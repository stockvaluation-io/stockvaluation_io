package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the numerical adjustments applied in a scenario.
 * 
 * Contains the specific parameter changes from base case.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioAdjustmentsDTO {
    
    /**
     * Revenue growth rate adjustment (as decimal).
     * Example: 0.25 represents 25% revenue growth
     */
    private Double revenueGrowthRate;
    
    /**
     * Operating margin adjustment (as decimal).
     * Example: 0.30 represents 30% operating margin
     */
    private Double operatingMargin;
    
    /**
     * Sales-to-capital ratio adjustment.
     * Example: 2.5 means $2.50 of revenue per $1 of invested capital
     */
    private Double salesToCapitalRatio;
    
    /**
     * Discount rate (WACC) adjustment (as decimal).
     * Example: 0.085 represents 8.5% discount rate
     */
    private Double discountRate;
}

