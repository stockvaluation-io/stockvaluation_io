package io.stockvaluation.service;

import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.DDMResultDTO;
import io.stockvaluation.dto.DividendDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for Dividend Discount Model (DDM) calculations.
 * 
 * Implements two models based on Damodaran's framework:
 * 1. Gordon Growth Model - for stable dividend payers
 * 2. Two-Stage DDM - for companies transitioning from high to stable growth
 * 
 * Model selection is automatic based on company characteristics.
 */
@Service
@Slf4j
public class DividendDiscountModelService {
    
    @Autowired
    private CostOfCapitalService costOfCapitalService;
    
    @Autowired
    private CommonService commonService;
    
    // Constants for DDM calculations
    private static final double MIN_GROWTH_COST_SPREAD = 0.01; // 1% minimum spread
    private static final double MAX_GROWTH_RATE = 0.15; // 15% max growth rate
    private static final double DEFAULT_STABLE_GROWTH = 0.03; // 3% stable growth (economy)
    private static final double DEFAULT_HIGH_GROWTH_YEARS = 5; // 5 years of high growth
    
    /**
     * Calculate DDM valuation for a company.
     * Automatically selects between Gordon Growth and Two-Stage models.
     * 
     * @param companyDataDTO Company data with dividend information
     * @param costOfEquity Cost of equity for discounting (as decimal)
     * @return DDMResultDTO with valuation and model details
     */
    public DDMResultDTO calculateDDM(CompanyDataDTO companyDataDTO, double costOfEquity) {
        log.info("[DDM] Calculating DDM for {}", 
            companyDataDTO.getBasicInfoDataDTO() != null ? 
            companyDataDTO.getBasicInfoDataDTO().getTicker() : "unknown");
        
        // Check if DDM is applicable
        DividendDataDTO dividendData = companyDataDTO.getDividendDataDTO();
        if (dividendData == null) {
            return DDMResultDTO.notApplicable("No dividend data available");
        }
        
        if (!dividendData.isDividendPaying()) {
            return DDMResultDTO.notApplicable("Company does not pay dividends");
        }
        
        // Get current dividend
        Double currentDividend = dividendData.getCurrentDividend();
        if (currentDividend == null || currentDividend <= 0) {
            return DDMResultDTO.notApplicable("Invalid dividend amount");
        }
        
        // Get dividend growth rate
        Double dividendGrowthRate = dividendData.getEstimatedGrowthRate(null);
        
        // Validate cost of equity
        if (costOfEquity <= 0) {
            return DDMResultDTO.notApplicable("Invalid cost of equity");
        }
        
        // Select and calculate appropriate model
        if (shouldUseTwoStageModel(dividendData, dividendGrowthRate)) {
            return calculateTwoStageDDM(currentDividend, dividendGrowthRate, costOfEquity);
        } else {
            return calculateGordonGrowthDDM(currentDividend, dividendGrowthRate, costOfEquity);
        }
    }
    
    /**
     * Calculate DDM with auto-derived cost of equity.
     * 
     * @param companyDataDTO Company data
     * @param ticker Stock ticker for cost of equity lookup
     * @return DDMResultDTO
     */
    public DDMResultDTO calculateDDM(CompanyDataDTO companyDataDTO, String ticker) {
        try {
            // Get cost of equity from cost of capital service
            // Cost of equity ≈ WACC for all-equity firms, or derive from CAPM
            double costOfEquity = deriveCostOfEquity(companyDataDTO, ticker);
            return calculateDDM(companyDataDTO, costOfEquity);
        } catch (Exception e) {
            log.error("[DDM] Error calculating DDM for {}: {}", ticker, e.getMessage());
            return DDMResultDTO.notApplicable("Error calculating cost of equity: " + e.getMessage());
        }
    }
    
    /**
     * Gordon Growth Model (Stable Growth DDM)
     * 
     * Formula: P = D1 / (r - g) = D0 * (1 + g) / (r - g)
     * 
     * Where:
     * - D0 = Current dividend
     * - D1 = Next year's dividend = D0 * (1 + g)
     * - r = Cost of equity
     * - g = Dividend growth rate (must be < r)
     * 
     * @param currentDividend D0 - Current annual dividend per share
     * @param growthRate g - Expected perpetual growth rate
     * @param costOfEquity r - Cost of equity for discounting
     * @return DDMResultDTO with Gordon Growth valuation
     */
    public DDMResultDTO calculateGordonGrowthDDM(
            double currentDividend, 
            double growthRate, 
            double costOfEquity) {
        
        log.info("[DDM-Gordon] D0={}, g={}, r={}", currentDividend, growthRate, costOfEquity);
        
        // Validate: g must be less than r
        if (growthRate >= costOfEquity) {
            // Cap growth rate to be below cost of equity
            double cappedGrowth = costOfEquity - MIN_GROWTH_COST_SPREAD;
            log.warn("[DDM-Gordon] Growth rate {} >= cost of equity {}. Capping to {}", 
                growthRate, costOfEquity, cappedGrowth);
            growthRate = cappedGrowth;
        }
        
        // Ensure minimum spread
        if (costOfEquity - growthRate < MIN_GROWTH_COST_SPREAD) {
            growthRate = costOfEquity - MIN_GROWTH_COST_SPREAD;
        }
        
        // Calculate D1 = D0 * (1 + g)
        double nextYearDividend = currentDividend * (1 + growthRate);
        
        // Calculate P = D1 / (r - g)
        double intrinsicValue = nextYearDividend / (costOfEquity - growthRate);
        
        log.info("[DDM-Gordon] D1={}, P={}", nextYearDividend, intrinsicValue);
        
        return DDMResultDTO.gordonGrowth(intrinsicValue, currentDividend, growthRate, costOfEquity);
    }
    
    /**
     * Two-Stage Dividend Discount Model
     * 
     * Stage 1: High growth period (n years)
     * Stage 2: Stable growth perpetuity
     * 
     * Formula:
     * P = Σ(t=1 to n) [D0 * (1+g1)^t / (1+r)^t] + [Dn * (1+g2) / (r-g2)] / (1+r)^n
     * 
     * Where:
     * - g1 = High growth rate
     * - g2 = Stable growth rate
     * - n = High growth period years
     * 
     * @param currentDividend D0
     * @param highGrowthRate g1
     * @param costOfEquity r
     * @return DDMResultDTO with Two-Stage valuation
     */
    public DDMResultDTO calculateTwoStageDDM(
            double currentDividend,
            double highGrowthRate,
            double costOfEquity) {
        
        return calculateTwoStageDDM(
            currentDividend, 
            highGrowthRate, 
            (int) DEFAULT_HIGH_GROWTH_YEARS, 
            DEFAULT_STABLE_GROWTH, 
            costOfEquity
        );
    }
    
    /**
     * Two-Stage DDM with configurable parameters
     */
    public DDMResultDTO calculateTwoStageDDM(
            double currentDividend,
            double highGrowthRate,
            int highGrowthYears,
            double stableGrowthRate,
            double costOfEquity) {
        
        log.info("[DDM-TwoStage] D0={}, g1={}, n={}, g2={}, r={}", 
            currentDividend, highGrowthRate, highGrowthYears, stableGrowthRate, costOfEquity);
        
        // Cap high growth rate
        if (highGrowthRate > MAX_GROWTH_RATE) {
            log.warn("[DDM-TwoStage] Capping high growth from {} to {}", highGrowthRate, MAX_GROWTH_RATE);
            highGrowthRate = MAX_GROWTH_RATE;
        }
        
        // Ensure stable growth < cost of equity
        if (stableGrowthRate >= costOfEquity) {
            stableGrowthRate = costOfEquity - MIN_GROWTH_COST_SPREAD;
        }
        
        double pvHighGrowth = 0;
        double dividend = currentDividend;
        
        // Stage 1: High growth period - calculate PV of dividends
        for (int t = 1; t <= highGrowthYears; t++) {
            dividend = dividend * (1 + highGrowthRate);
            double discountFactor = Math.pow(1 + costOfEquity, t);
            pvHighGrowth += dividend / discountFactor;
        }
        
        // Stage 2: Terminal value with stable growth (Gordon Growth at end of high growth)
        // Terminal dividend = Dn * (1 + g2)
        double terminalDividend = dividend * (1 + stableGrowthRate);
        double terminalValue = terminalDividend / (costOfEquity - stableGrowthRate);
        
        // Discount terminal value back to present
        double pvTerminal = terminalValue / Math.pow(1 + costOfEquity, highGrowthYears);
        
        double intrinsicValue = pvHighGrowth + pvTerminal;
        
        log.info("[DDM-TwoStage] PV(high growth)={}, PV(terminal)={}, Total={}", 
            pvHighGrowth, pvTerminal, intrinsicValue);
        
        return DDMResultDTO.twoStage(
            intrinsicValue, 
            currentDividend, 
            highGrowthRate, 
            highGrowthYears, 
            stableGrowthRate, 
            costOfEquity
        );
    }
    
    /**
     * Determine if Two-Stage model is more appropriate than Gordon Growth
     */
    private boolean shouldUseTwoStageModel(DividendDataDTO dividendData, double growthRate) {
        // Use Two-Stage if:
        // 1. High dividend growth rate (> 6%)
        // 2. Company has history of growing dividends
        
        if (growthRate > 0.06) {
            return true;
        }
        
        // Check if there's high variability in dividend growth
        if (dividendData.getDividendHistory() != null && dividendData.getDividendHistory().size() > 5) {
            // With sufficient history, could analyze growth pattern
            // For now, use simple threshold
            return growthRate > DEFAULT_STABLE_GROWTH + 0.02;
        }
        
        return false;
    }
    
    /**
     * Derive cost of equity from company data
     * Uses CAPM: r_e = r_f + β * ERP
     */
    private double deriveCostOfEquity(CompanyDataDTO companyDataDTO, String ticker) {
        // Get risk-free rate
        double riskFreeRate = 0.04; // Default 4%
        if (companyDataDTO.getCompanyDriveDataDTO() != null && 
            companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate() != null) {
            riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate();
        }
        
        // Get beta
        double beta = 1.0; // Default market beta
        if (companyDataDTO.getBasicInfoDataDTO() != null && 
            companyDataDTO.getBasicInfoDataDTO().getBeta() != null) {
            beta = companyDataDTO.getBasicInfoDataDTO().getBeta();
        }
        
        // Equity risk premium (typical US market)
        double equityRiskPremium = 0.05; // 5%
        
        // CAPM: r_e = r_f + β * ERP
        double costOfEquity = riskFreeRate + beta * equityRiskPremium;
        
        // Ensure reasonable bounds
        costOfEquity = Math.max(0.06, Math.min(costOfEquity, 0.25)); // 6% to 25%
        
        log.info("[DDM] Derived cost of equity for {}: r_f={}, β={}, ERP={}, r_e={}", 
            ticker, riskFreeRate, beta, equityRiskPremium, costOfEquity);
        
        return costOfEquity;
    }
    
    /**
     * Check if DDM is applicable for a company
     */
    public boolean isDDMApplicable(CompanyDataDTO companyDataDTO) {
        return companyDataDTO != null && companyDataDTO.isSuitableForDDM();
    }
}

