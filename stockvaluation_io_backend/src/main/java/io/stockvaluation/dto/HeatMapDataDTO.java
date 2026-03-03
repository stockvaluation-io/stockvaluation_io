package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing 2-variable sensitivity heat map data.
 * 
 * Contains a grid of valuations showing how intrinsic value changes
 * with different combinations of growth rate and discount rate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeatMapDataDTO {
    
    /**
     * Revenue growth rates (X-axis values).
     * Example: [0.15, 0.20, 0.25, 0.30, 0.35] for 15-35% growth range
     */
    private List<Double> growthRates;
    
    /**
     * Discount rates / WACC (Y-axis values).
     * Example: [0.075, 0.080, 0.085, 0.090, 0.095] for 7.5-9.5% WACC range
     */
    private List<Double> discountRates;
    
    /**
     * 2D grid of calculated valuations.
     * Outer list = rows (one per discount rate)
     * Inner list = columns (one per growth rate)
     * 
     * Example: valuations.get(2).get(3) = valuation at discountRates[2] and growthRates[3]
     */
    private List<List<Double>> valuations;
    
    /**
     * Base case intrinsic value (center point of heat map).
     * Used as reference for percentage changes.
     */
    private Double baseValue;
    
    /**
     * Minimum valuation in the grid (for color scale).
     */
    private Double minValue;
    
    /**
     * Maximum valuation in the grid (for color scale).
     */
    private Double maxValue;
}

