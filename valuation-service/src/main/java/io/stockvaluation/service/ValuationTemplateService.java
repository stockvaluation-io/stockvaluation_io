package io.stockvaluation.service;

import io.stockvaluation.config.ValuationTemplateProperties;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.ValuationTemplate;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;
import io.stockvaluation.form.FinancialDataInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to determine the appropriate DCF valuation template based on company characteristics.
 * 
 * Uses ValuationModel logic to select:
 * - Projection period (5, 10, or 15 years)
 * - Growth pattern (Stable, Two-stage, Three-stage)
 * - Earnings level (Current vs Normalized)
 * - Cash flow approach (FCFF only)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ValuationTemplateService {

    private final ValuationTemplateProperties valuationTemplateProperties;
    
    /**
     * Determine the appropriate valuation template for a company.
     * 
     * @param financialDataInput Initialized financial data (can be partially initialized)
     * @param companyDataDTO Company data from Yahoo Finance
     * @return ValuationTemplate with all characteristics determined
     */
    public ValuationTemplate determineTemplate(
        FinancialDataInput financialDataInput,
        CompanyDataDTO companyDataDTO
    ) {
        log.info("[TEMPLATE] Determining valuation template for company");
        
        // Extract inputs for ValuationModel
        boolean earningsPositive = determineEarningsPositivity(companyDataDTO);
        double firmGrowthRate = extractFirmGrowthRate(companyDataDTO, financialDataInput);
        double expectedInflation = valuationTemplateProperties.getExpectedInflation();
        double expectedRealGrowth = valuationTemplateProperties.getExpectedRealGrowth();
        
        // Additional parameters for ValuationModel
        boolean useOptionPricing = false; // We focus on DCF models
        boolean negativeIsCyclical = false; // Could be enhanced later
        boolean negativeIsOneTime = false;
        boolean canEstimateCapexAndWC = true; // Assume we can estimate
        // Legacy input retained for ValuationModel constructor compatibility.
        boolean legacyDividendSignal = false;
        boolean hasSustainableAdvantage = determineSustainableAdvantage(firmGrowthRate, expectedInflation, expectedRealGrowth);
        
        // Legacy inputs retained for ValuationModel constructor compatibility.
        double netIncome = companyDataDTO.getFinancialDataDTO() != null && 
            companyDataDTO.getFinancialDataDTO().getOperatingIncomeTTM() != null ? 
            companyDataDTO.getFinancialDataDTO().getOperatingIncomeTTM() : 0.0;
        double depreciation = 0.0; // Would need to extract from data
        double capitalSpending = 0.0;
        double deltaWorkingCapital = 0.0;
        double debtRatio = valuationTemplateProperties.getDefaultDebtRatio();
        
        // Dummy values for f32Value and g40Value (used in cashflow determination)
        double f32Value = 0.0;
        double g40Value = 0.0;
        
        log.info("   Earnings Positive: {}", earningsPositive);
        log.info("   Firm Growth Rate: {}%", firmGrowthRate * 100);
        log.info("   Has Sustainable Advantage: {}", hasSustainableAdvantage);
        
        // Instantiate ValuationModel
        ValuationModel model = new ValuationModel(
            earningsPositive,
            useOptionPricing,
            negativeIsCyclical,
            negativeIsOneTime,
            canEstimateCapexAndWC,
            legacyDividendSignal,
            f32Value,
            g40Value,
            "", // f48GrowthLabel - empty means not stable growth
            hasSustainableAdvantage,
            firmGrowthRate,
            expectedInflation,
            expectedRealGrowth,
            "", // f45EarningsLevelOverride
            valuationTemplateProperties.getStableGrowthSpread(),
            valuationTemplateProperties.getHighGrowthSpread(),
            netIncome,
            depreciation,
            capitalSpending,
            deltaWorkingCapital,
            debtRatio
        );
        
        // Build template from model outputs (now using enums directly)
        ValuationTemplate template = new ValuationTemplate();
        template.setModelType(model.getModelType());
        template.setGrowthPattern(model.getGrowthPattern());
        template.setEarningsLevel(model.getEarningsLevel());
        template.setCashflowToDiscount(CashflowType.FCFF);
        template.getMetadata().put("legacyCashflowSuggestion", model.getCashflowToDiscount());
        template.setGrowthPeriodLength(model.getGrowthPeriodLength());
        
        // Map growth pattern to projection years
        int projectionYears = determineProjectionYears(model.getGrowthPattern());
        template.setProjectionYears(projectionYears);
        template.setArrayLength(projectionYears + 2); // base + projection + terminal
        
        log.info("[TEMPLATE] Selected: {} ({} years)", 
            template.getGrowthPattern().getDisplayName(), template.getProjectionYears());
        log.info("   Model Type: {}", template.getModelType().getDisplayName());
        log.info("   Earnings Level: {}", template.getEarningsLevel().getDisplayName());
        log.info("   Cashflow: {}", template.getCashflowToDiscount().getDisplayName());
        log.info("   Array Length: {}", template.getArrayLength());
        
        // Calculate normalized margin if needed
        if (template.useNormalizedEarnings()) {
            Double normalizedMargin = calculateNormalizedMargin(financialDataInput, companyDataDTO);
            template.setNormalizedOperatingMargin(normalizedMargin);
            log.info("   Normalized Margin: {}%", normalizedMargin);
        }
        
        return template;
    }
    
    /**
     * Determine projection years based on growth pattern.
     * 
     * @param growthPattern Growth pattern enum from ValuationModel
     * @return Number of projection years (5, 10, or 15)
     */
    private int determineProjectionYears(GrowthPattern growthPattern) {
        if (growthPattern == null) {
            return valuationTemplateProperties.getDefaultProjectionYears();
        }
        
        switch (growthPattern) {
            case STABLE:
                return valuationTemplateProperties.getDefaultProjectionYears();
            case TWO_STAGE:
                return valuationTemplateProperties.getDefaultProjectionYears();
            case THREE_STAGE:
                return valuationTemplateProperties.getThreeStageProjectionYears();
            case N_STAGE:
                return valuationTemplateProperties.getDefaultProjectionYears();
            default:
                log.warn("Unknown growth pattern: {}, defaulting to configured projection years", growthPattern);
                return valuationTemplateProperties.getDefaultProjectionYears();
        }
    }
    
    /**
     * Determine if earnings are positive based on recent financial data.
     */
    private boolean determineEarningsPositivity(CompanyDataDTO companyDataDTO) {
        if (companyDataDTO.getFinancialDataDTO() == null) {
            return true; // Default to positive
        }
        
        FinancialDataDTO financialData = companyDataDTO.getFinancialDataDTO();
        
        // Check operating income
        if (financialData.getOperatingIncomeTTM() != null && financialData.getOperatingIncomeTTM() > 0) {
            return true;
        }
        
        // If negative or null, consider negative
        return false;
    }
    
    /**
     * Extract firm growth rate from company data.
     */
    private double extractFirmGrowthRate(CompanyDataDTO companyDataDTO, FinancialDataInput financialDataInput) {
        // Try to get from financialDataInput first
        if (financialDataInput != null && financialDataInput.getRevenueNextYear() != null) {
            return financialDataInput.getRevenueNextYear() / 100.0;
        }
        
        // Try from company drive data
        if (companyDataDTO.getCompanyDriveDataDTO() != null && 
            companyDataDTO.getCompanyDriveDataDTO().getRevenueNextYear() != null) {
            return companyDataDTO.getCompanyDriveDataDTO().getRevenueNextYear();
        }
        
        // Try from growth DTO
        if (companyDataDTO.getGrowthDto() != null && 
            companyDataDTO.getGrowthDto().getRevenueMu() != null) {
            return companyDataDTO.getGrowthDto().getRevenueMu();
        }
        
        // Default to 10% if no data available
        return valuationTemplateProperties.getDefaultGrowthRate();
    }
    
    /**
     * Determine if company has sustainable competitive advantage.
     * Based on whether growth rate significantly exceeds inflation + real growth + 6%
     */
    private boolean determineSustainableAdvantage(double firmGrowthRate, double inflation, double realGrowth) {
        double threshold = inflation + realGrowth + valuationTemplateProperties.getSustainableAdvantageSpread();
        return firmGrowthRate > threshold;
    }
    
    /**
     * Calculate normalized operating margin by averaging 3-5 years of historical margins.
     * 
     * @param input Financial data input
     * @param companyDataDTO Company data
     * @return Normalized margin as percentage (e.g., 25.5 for 25.5%)
     */
    private Double calculateNormalizedMargin(FinancialDataInput input, CompanyDataDTO companyDataDTO) {
        List<Double> historicalMargins = new ArrayList<>();
        
        // Try to extract from GrowthDto
        if (companyDataDTO.getGrowthDto() != null && 
            companyDataDTO.getGrowthDto().getMarginChanges() != null) {
            // marginChanges might contain historical operating margins
            historicalMargins = companyDataDTO.getGrowthDto().getMarginChanges();
        }
        
        // If we have marginMu, use it as a fallback
        if (historicalMargins.isEmpty() && 
            companyDataDTO.getGrowthDto() != null && 
            companyDataDTO.getGrowthDto().getMarginMu() != null) {
            // MarginMu is the mean of historical margins
            return companyDataDTO.getGrowthDto().getMarginMu() * 100; // Convert to percentage
        }
        
        // Calculate average of 3-5 most recent years
        if (!historicalMargins.isEmpty()) {
            int yearsToAverage = Math.min(5, historicalMargins.size());
            
            if (yearsToAverage >= 3) {
                double sum = 0;
                for (int i = 0; i < yearsToAverage; i++) {
                    sum += historicalMargins.get(i);
                }
                double avgMargin = sum / yearsToAverage;
                
                log.info("   Calculated normalized margin from {} years: {}%", yearsToAverage, avgMargin * 100);
                return avgMargin * 100; // Convert to percentage
            }
        }
        
        // Fall back to current operating margin
        if (input != null && input.getOperatingMarginNextYear() != null) {
            log.info("   Insufficient historical data, using current margin: {}%", input.getOperatingMarginNextYear());
            return input.getOperatingMarginNextYear();
        }
        
        // Final fallback from company drive data
        if (companyDataDTO.getCompanyDriveDataDTO() != null && 
            companyDataDTO.getCompanyDriveDataDTO().getOperatingMarginNextYear() != null) {
            double margin = companyDataDTO.getCompanyDriveDataDTO().getOperatingMarginNextYear() * 100;
            log.info("   Using margin from company drive data: {}%", margin);
            return margin;
        }
        
        // Last resort: use a conservative default
        log.warn("   No margin data available, using configured default margin: {}%",
                valuationTemplateProperties.getDefaultNormalizedMargin());
        return valuationTemplateProperties.getDefaultNormalizedMargin();
    }
}
