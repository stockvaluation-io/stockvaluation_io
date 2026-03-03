package io.stockvaluation.form;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a sector-specific parameter override for multi-segment DCF analysis.
 * Allows users to override calculated values for specific business segments.
 * 
 * Example: Override "Electronic Components" revenue growth to 15% (absolute)
 * Example: Increase "Semiconductors" operating margin by 10% (relative multiplier)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SectorParameterOverride {
    
    /**
     * Name of the sector to override (e.g., "Electronic Components", "Semiconductors")
     * Must match the sector name from segment analysis
     */
    private String sectorName;
    
    /**
     * Type of parameter to override.
     * Valid values: "revenue_growth", "operating_margin", "sales_to_capital"
     */
    private String parameterType;
    
    /**
     * The override value.
     * Interpretation depends on adjustmentType:
     * - absolute: Direct value (e.g., 15.0 means 15%)
     * - relative_multiplier: Percentage change (e.g., 10.0 means +10%)
     * - relative_additive: Absolute points (e.g., 5.0 means +5 percentage points)
     */
    private Double value;
    
    /**
     * How to apply the override value.
     * Valid values:
     * - "absolute": Replace with value directly
     * - "relative_multiplier": Multiply current by (1 + value/100)
     * - "relative_additive": Add value to current
     */
    private String adjustmentType;
    
    /**
     * Time period for the override.
     * Valid values: "years_1_to_5", "years_6_to_10", "both"
     * Default: "both" if not specified
     */
    private String timeframe;
    
    /**
     * Validates the override parameters
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        if (sectorName == null || sectorName.trim().isEmpty()) {
            return false;
        }
        
        if (parameterType == null || !isValidParameterType(parameterType)) {
            return false;
        }
        
        if (value == null || !Double.isFinite(value)) {
            return false;
        }
        
        if (adjustmentType == null || !isValidAdjustmentType(adjustmentType)) {
            return false;
        }
        
        // Optional timeframe, default to "both"
        if (timeframe == null || timeframe.trim().isEmpty()) {
            timeframe = "both";
        }
        
        return true;
    }
    
    private boolean isValidParameterType(String type) {
        return "revenue_growth".equals(type) || 
               "operating_margin".equals(type) || 
               "sales_to_capital".equals(type);
    }
    
    private boolean isValidAdjustmentType(String type) {
        return "absolute".equals(type) || 
               "relative_multiplier".equals(type) || 
               "relative_additive".equals(type);
    }
    
    /**
     * Applies the override to a current value
     * @param currentValue The current calculated value
     * @return The overridden value
     */
    public Double applyOverride(Double currentValue) {
        if (currentValue == null) {
            currentValue = 0.0;
        }
        
        switch (adjustmentType) {
            case "absolute":
                return value;
                
            case "relative_multiplier":
                // value is percentage change, e.g., 10.0 means +10%
                return currentValue * (1.0 + value / 100.0);
                
            case "relative_additive":
                // value is absolute points to add
                return currentValue + value;
                
            default:
                return currentValue;
        }
    }
    
    @Override
    public String toString() {
        return String.format("SectorOverride[sector=%s, param=%s, value=%.2f, type=%s, timeframe=%s]",
            sectorName, parameterType, value, adjustmentType, timeframe);
    }
}

