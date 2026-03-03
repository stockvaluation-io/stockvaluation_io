package io.stockvaluation.enums;

import lombok.Getter;

/**
 * Type of cash flow to discount in DCF valuation.
 * Determines whether we value the firm (FCFF) or equity directly (FCFE/Dividends).
 */
@Getter
public enum CashflowType {
    
    FCFF("FCFF (Value firm)", "Free Cash Flow to Firm - values entire enterprise"),
    FCFE("FCFE (Value equity)", "Free Cash Flow to Equity - values equity directly"),
    DIVIDENDS("Dividends (Value equity)", "Dividend Discount Model - values equity via dividends");
    
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
        if (value == null) {
            return FCFF;
        }
        for (CashflowType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        // Handle partial matches
        String lower = value.toLowerCase();
        if (lower.contains("fcff") || lower.contains("firm")) {
            return FCFF;
        }
        if (lower.contains("fcfe")) {
            return FCFE;
        }
        if (lower.contains("dividend")) {
            return DIVIDENDS;
        }
        return FCFF;
    }
    
    /**
     * Check if this is an equity valuation approach (vs firm valuation).
     */
    public boolean isEquityValuation() {
        return this == FCFE || this == DIVIDENDS;
    }
}

