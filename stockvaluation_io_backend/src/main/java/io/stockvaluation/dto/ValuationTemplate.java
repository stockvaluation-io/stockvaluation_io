package io.stockvaluation.dto;

import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * ValuationTemplate defines the DCF model structure based on company characteristics.
 * 
 * This template determines:
 * - Projection period length (5, 10, or 15 years)
 * - Growth pattern (Stable, Two-stage, Three-stage)
 * - Earnings level (Current vs Normalized)
 * - Cash flow approach (FCFF, FCFE, Dividends)
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ValuationTemplate {
    
    /**
     * Number of projection years (5, 10, or 15)
     */
    private int projectionYears;
    
    /**
     * Total array length = base year (0) + projection years + terminal year
     * Example: 5 year model = 0 + 5 + 1 = 7 elements [0,1,2,3,4,5,6]
     */
    private int arrayLength;
    
    /**
     * Growth pattern: STABLE, TWO_STAGE, THREE_STAGE, N_STAGE
     */
    private GrowthPattern growthPattern;
    
    /**
     * Earnings level: CURRENT or NORMALIZED
     */
    private EarningsLevel earningsLevel;
    
    /**
     * Cash flow to discount: FCFF, FCFE, or DIVIDENDS
     */
    private CashflowType cashflowToDiscount;
    
    /**
     * Growth period length description
     */
    private String growthPeriodLength;
    
    /**
     * Model type: OPTION_PRICING or DISCOUNTED_CF
     */
    private ModelType modelType;
    
    /**
     * Normalized operating margin (3-5 year average) if earningsLevel is NORMALIZED
     * Null if using current earnings
     */
    private Double normalizedOperatingMargin;
    
    /**
     * Additional metadata about the valuation model
     */
    private Map<String, Object> metadata = new HashMap<>();
    
    /**
     * Convenience method to get terminal year index
     */
    public int getTerminalYearIndex() {
        return arrayLength - 1;
    }
    
    /**
     * Convenience method to get last projection year index (before terminal)
     */
    public int getLastProjectionYearIndex() {
        return arrayLength - 2;
    }
    
    /**
     * Check if this template uses dividend discount model
     */
    public boolean isDividendModel() {
        return cashflowToDiscount == CashflowType.DIVIDENDS;
    }
    
    /**
     * Check if this template uses FCFE (equity valuation)
     */
    public boolean isFCFEModel() {
        return cashflowToDiscount == CashflowType.FCFE;
    }
    
    /**
     * Check if this template uses FCFF (firm valuation)
     */
    public boolean isFCFFModel() {
        return cashflowToDiscount == CashflowType.FCFF;
    }
    
    /**
     * Check if earnings should be normalized
     */
    public boolean useNormalizedEarnings() {
        return earningsLevel == EarningsLevel.NORMALIZED;
    }
    
    @Override
    public String toString() {
        return String.format(
            "ValuationTemplate[projectionYears=%d, arrayLength=%d, growthPattern=%s, earningsLevel=%s, " +
            "cashflow=%s, modelType=%s, normalizedMargin=%s]",
            projectionYears, arrayLength, 
            growthPattern != null ? growthPattern.getDisplayName() : null, 
            earningsLevel != null ? earningsLevel.getDisplayName() : null, 
            cashflowToDiscount != null ? cashflowToDiscount.getDisplayName() : null, 
            modelType != null ? modelType.getDisplayName() : null, 
            normalizedOperatingMargin
        );
    }
}
