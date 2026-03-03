package io.stockvaluation.enums;

import lombok.Getter;

/**
 * Type of valuation model to use.
 * Based on Damodaran's valuation framework decision tree.
 */
@Getter
public enum ModelType {
    
    OPTION_PRICING("Option Pricing Model"),
    DISCOUNTED_CF("Discounted CF Model");
    
    private final String displayName;
    
    ModelType(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Parse from legacy string value for backward compatibility.
     */
    public static ModelType fromString(String value) {
        if (value == null) {
            return DISCOUNTED_CF;
        }
        for (ModelType type : values()) {
            if (type.displayName.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return DISCOUNTED_CF;
    }
}

