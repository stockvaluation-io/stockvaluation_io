package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for calculating causal scenarios with heat map.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CausalScenarioRequest {
    
    /**
     * Base assumptions for the DCF calculation.
     * Can be empty map to use defaults from Yahoo Finance.
     */
    private Map<String, Object> baseAssumptions;
    
    /**
     * Whether to enable causal reasoning analysis.
     * Default: true
     */
    private Boolean enableCausalReasoning;
    
    /**
     * Whether to generate heat map data.
     * Default: true
     */
    private Boolean generateHeatmap;
    
    /**
     * Growth rate variance for scenarios (as decimal).
     * Example: 0.05 means ±5% from base case
     * Default: 0.05
     */
    private Double growthVariance;
    
    /**
     * Operating margin variance for scenarios (as decimal).
     * Example: 0.03 means ±3 percentage points
     * Default: 0.03
     */
    private Double marginVariance;
    
    /**
     * WACC variance for scenarios (as decimal).
     * Example: 0.01 means ±1 percentage point
     * Default: 0.01
     */
    private Double waccVariance;
    
    /**
     * Number of points for heat map grid (N x N).
     * Default: 5 (creates 5x5 = 25 valuations)
     */
    private Integer heatmapGridSize;
}

