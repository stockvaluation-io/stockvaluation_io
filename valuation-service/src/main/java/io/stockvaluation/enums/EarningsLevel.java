package io.stockvaluation.enums;

import lombok.Getter;

/**
 * Earnings level to use as the base for DCF projections.
 * Normalized earnings are used for cyclical or temporarily impaired companies.
 */
@Getter
public enum EarningsLevel {
    
    CURRENT("Current Earnings", "Use current/TTM earnings as base"),
    NORMALIZED("Normalized Earnings", "Use normalized (3-5 year average) earnings as base");
    
    private final String displayName;
    private final String description;
    
    EarningsLevel(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    /**
     * Parse from legacy string value for backward compatibility.
     */
    public static EarningsLevel fromString(String value) {
        if (value == null) {
            return CURRENT;
        }
        for (EarningsLevel level : values()) {
            if (level.displayName.equalsIgnoreCase(value) || level.name().equalsIgnoreCase(value)) {
                return level;
            }
        }
        // Handle partial matches
        if (value.toLowerCase().contains("normalized")) {
            return NORMALIZED;
        }
        return CURRENT;
    }
}

