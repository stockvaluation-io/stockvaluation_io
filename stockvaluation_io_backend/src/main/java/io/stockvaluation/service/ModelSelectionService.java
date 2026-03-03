package io.stockvaluation.service;

import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.DividendDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for automatic valuation model selection.
 * 
 * Implements Damodaran's decision framework for choosing between:
 * - FCFF (Free Cash Flow to Firm) - Default for most companies
 * - FCFE (Free Cash Flow to Equity) - For highly levered companies
 * - DDM (Dividend Discount Model) - For stable dividend payers
 * 
 * Also determines:
 * - Whether to use option pricing for distressed companies
 * - Appropriate growth pattern
 * - Earnings normalization requirements
 */
@Service
@Slf4j
public class ModelSelectionService {
    
    // Thresholds for model selection
    private static final double DDM_MIN_PAYOUT_RATIO = 0.40; // 40% payout for DDM
    private static final double DDM_MIN_DIVIDEND_YIELD = 0.01; // 1% yield minimum
    private static final double HIGH_LEVERAGE_THRESHOLD = 0.50; // 50% debt/capital for FCFE
    private static final double DISTRESS_THRESHOLD = -0.10; // -10% margin for option model consideration
    
    /**
     * Select the primary cashflow type for valuation.
     * 
     * Decision hierarchy:
     * 1. DDM if company is a stable dividend payer (payout > 40%, yield > 1%)
     * 2. FCFE if company is highly leveraged (debt/capital > 50%)
     * 3. FCFF otherwise (default - values entire firm)
     * 
     * @param companyDataDTO Company data including dividend information
     * @return CashflowType enum indicating the recommended model
     */
    public CashflowType selectPrimaryModel(CompanyDataDTO companyDataDTO) {
        log.info("[MODEL-SELECTION] Analyzing company for model selection");
        
        if (companyDataDTO == null) {
            log.warn("[MODEL-SELECTION] No company data, defaulting to FCFF");
            return CashflowType.FCFF;
        }
        
        // Check for DDM eligibility first
        if (isDDMEligible(companyDataDTO)) {
            log.info("[MODEL-SELECTION] Company eligible for DDM - stable dividend payer");
            return CashflowType.DIVIDENDS;
        }
        
        // Check for FCFE eligibility (high leverage)
        if (isFCFEPreferred(companyDataDTO)) {
            log.info("[MODEL-SELECTION] Company has high leverage - FCFE preferred");
            return CashflowType.FCFE;
        }
        
        // Default to FCFF
        log.info("[MODEL-SELECTION] Using default FCFF model");
        return CashflowType.FCFF;
    }
    
    /**
     * Determine if company should use DDM.
     * 
     * Criteria:
     * - Pays dividends
     * - Payout ratio > 40%
     * - Dividend yield > 1%
     * - Has consistent dividend history
     */
    public boolean isDDMEligible(CompanyDataDTO companyDataDTO) {
        DividendDataDTO dividendData = companyDataDTO.getDividendDataDTO();
        
        if (dividendData == null) {
            return false;
        }
        
        // Check if company pays dividends
        if (!dividendData.isDividendPaying()) {
            return false;
        }
        
        // Check payout ratio
        Double payoutRatio = dividendData.getPayoutRatio();
        if (payoutRatio != null && payoutRatio >= DDM_MIN_PAYOUT_RATIO) {
            log.debug("[MODEL-SELECTION] Payout ratio {} >= threshold {}", payoutRatio, DDM_MIN_PAYOUT_RATIO);
            return true;
        }
        
        // Alternative: check dividend yield
        Double dividendYield = dividendData.getDividendYield();
        if (dividendYield != null && dividendYield >= DDM_MIN_DIVIDEND_YIELD) {
            // Also need reasonable payout
            if (payoutRatio != null && payoutRatio > 0.20) {
                log.debug("[MODEL-SELECTION] Dividend yield {} >= threshold {}", dividendYield, DDM_MIN_DIVIDEND_YIELD);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Determine if FCFE is preferred over FCFF.
     * 
     * FCFE is preferred when:
     * - Company has high leverage (debt/capital > 50%)
     * - Debt structure is stable
     */
    private boolean isFCFEPreferred(CompanyDataDTO companyDataDTO) {
        FinancialDataDTO financialData = companyDataDTO.getFinancialDataDTO();
        
        if (financialData == null) {
            return false;
        }
        
        // Calculate debt to capital ratio
        Double bookValueDebt = financialData.getBookValueDebtTTM();
        Double bookValueEquity = financialData.getBookValueEqualityTTM();
        
        if (bookValueDebt != null && bookValueEquity != null && bookValueEquity > 0) {
            double totalCapital = bookValueDebt + bookValueEquity;
            double debtRatio = bookValueDebt / totalCapital;
            
            if (debtRatio >= HIGH_LEVERAGE_THRESHOLD) {
                log.debug("[MODEL-SELECTION] Debt ratio {} >= threshold {}", debtRatio, HIGH_LEVERAGE_THRESHOLD);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Determine if option pricing model should be considered.
     * 
     * Option pricing is relevant for:
     * - Distressed companies (equity as call option on firm value)
     * - Companies with significant real options (patents, R&D)
     */
    public boolean shouldConsiderOptionPricing(CompanyDataDTO companyDataDTO) {
        if (companyDataDTO == null || companyDataDTO.getFinancialDataDTO() == null) {
            return false;
        }
        
        FinancialDataDTO financialData = companyDataDTO.getFinancialDataDTO();
        
        // Check for distress: negative operating margin
        Double operatingIncome = financialData.getOperatingIncomeTTM();
        Double revenue = financialData.getRevenueTTM();
        
        if (operatingIncome != null && revenue != null && revenue > 0) {
            double operatingMargin = operatingIncome / revenue;
            if (operatingMargin < DISTRESS_THRESHOLD) {
                log.info("[MODEL-SELECTION] Company shows distress (margin: {}), consider option model", operatingMargin);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Determine the appropriate model type (DCF vs Option)
     */
    public ModelType selectModelType(CompanyDataDTO companyDataDTO) {
        if (shouldConsiderOptionPricing(companyDataDTO)) {
            // For now, still use DCF but flag that option model could be considered
            log.info("[MODEL-SELECTION] Option pricing could be considered, but using DCF");
        }
        return ModelType.DISCOUNTED_CF;
    }
    
    /**
     * Select appropriate growth pattern based on company characteristics.
     */
    public GrowthPattern selectGrowthPattern(CompanyDataDTO companyDataDTO, double firmGrowthRate) {
        double inflationPlusRealGrowth = 0.05; // ~5% = 3% inflation + 2% real
        
        // Thresholds per Damodaran
        double stableThreshold = inflationPlusRealGrowth + 0.0101; // ~6%
        double twoStageThreshold = inflationPlusRealGrowth + 0.06; // ~11%
        
        if (firmGrowthRate < stableThreshold) {
            return GrowthPattern.STABLE;
        } else if (firmGrowthRate < twoStageThreshold) {
            return GrowthPattern.TWO_STAGE;
        } else {
            return GrowthPattern.THREE_STAGE;
        }
    }
    
    /**
     * Determine if earnings should be normalized.
     */
    public EarningsLevel selectEarningsLevel(CompanyDataDTO companyDataDTO) {
        if (companyDataDTO == null || companyDataDTO.getFinancialDataDTO() == null) {
            return EarningsLevel.CURRENT;
        }
        
        FinancialDataDTO financialData = companyDataDTO.getFinancialDataDTO();
        
        // Check for negative earnings
        Double operatingIncome = financialData.getOperatingIncomeTTM();
        if (operatingIncome != null && operatingIncome < 0) {
            // Negative earnings - might need normalization
            // Check if it's cyclical or one-time
            // For now, use heuristic based on margin
            Double revenue = financialData.getRevenueTTM();
            if (revenue != null && revenue > 0) {
                double margin = operatingIncome / revenue;
                // Deeply negative suggests structural issue, use current
                // Slightly negative might be cyclical, normalize
                if (margin > -0.10) {
                    return EarningsLevel.NORMALIZED;
                }
            }
        }
        
        return EarningsLevel.CURRENT;
    }
    
    /**
     * Get a summary of model selection rationale.
     */
    public String getSelectionRationale(CompanyDataDTO companyDataDTO, CashflowType selectedModel) {
        StringBuilder rationale = new StringBuilder();
        
        switch (selectedModel) {
            case DIVIDENDS:
                DividendDataDTO div = companyDataDTO.getDividendDataDTO();
                rationale.append("DDM selected: ");
                if (div != null) {
                    rationale.append(String.format("Payout ratio: %.1f%%, ", 
                        div.getPayoutRatio() != null ? div.getPayoutRatio() * 100 : 0));
                    rationale.append(String.format("Dividend yield: %.1f%%", 
                        div.getDividendYield() != null ? div.getDividendYield() * 100 : 0));
                }
                break;
                
            case FCFE:
                rationale.append("FCFE selected: Company has high leverage (>50% debt/capital)");
                break;
                
            case FCFF:
            default:
                rationale.append("FCFF selected: Standard firm valuation approach");
                break;
        }
        
        return rationale.toString();
    }
}

