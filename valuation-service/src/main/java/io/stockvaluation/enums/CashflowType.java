package io.stockvaluation.enums;

import lombok.Getter;

/**
 * Type of cash flow to discount in DCF valuation.
 * Current valuation pipeline supports FCFF only.
 */
@Getter
public enum CashflowType {

    FCFF("FCFF (Value firm)", "Free Cash Flow to Firm - values entire enterprise");
    
    private final String displayName;
    private final String description;
    
    CashflowType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Parse from legacy string value for backward compatibility.
     */
    public static CashflowType fromString(String value) {
        return FCFF;
    }
    
    /**
     * Check if this is an equity valuation approach (vs firm valuation).
     */
    public boolean isEquityValuation() {
        return false;
    }
}
