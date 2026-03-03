package io.stockvaluation.enums;

import lombok.Getter;

/**
 * Growth pattern for DCF projections.
 * Determines the structure and length of the projection period.
 */
@Getter
public enum GrowthPattern {
    
    STABLE("Stable Growth", 10, "Mature company with stable growth at or below economy"),
    TWO_STAGE("Two-stage Growth", 10, "High growth transitioning to stable growth"),
    THREE_STAGE("Three-stage Growth", 15, "High growth -> transition -> stable growth"),
    N_STAGE("n-stage model", 10, "Complex multi-stage model for special situations");
    
    private final String displayName;
    private final int defaultProjectionYears;
    private final String description;
    
    GrowthPattern(String displayName, int defaultProjectionYears, String description) {
        this.displayName = displayName;
        this.defaultProjectionYears = defaultProjectionYears;
        this.description = description;
    }
    
    /**
     * Parse from legacy string value for backward compatibility.
     */
    public static GrowthPattern fromString(String value) {
        if (value == null) {
            return TWO_STAGE;
        }
        for (GrowthPattern pattern : values()) {
            if (pattern.displayName.equalsIgnoreCase(value) || pattern.name().equalsIgnoreCase(value)) {
                return pattern;
            }
        }
        // Handle partial matches
        String lower = value.toLowerCase();
        if (lower.contains("stable")) {
            return STABLE;
        }
        if (lower.contains("three")) {
            return THREE_STAGE;
        }
        if (lower.contains("two")) {
            return TWO_STAGE;
        }
        if (lower.contains("n-stage")) {
            return N_STAGE;
        }
        return TWO_STAGE;
    }
    
    /**
     * Get the array length needed for this growth pattern.
     * Array = base year (index 0) + projection years + terminal year
     */
    public int getArrayLength() {
        return defaultProjectionYears + 2;
    }
}

