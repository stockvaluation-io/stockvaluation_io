package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a single DCF scenario with causal reasoning.
 * 
 * Combines traditional scenario parameters (optimistic/base/pessimistic)
 * with causal dependency chains explaining how variables interact.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CausalScenarioDTO {
    
    /**
     * Scenario name: "optimistic", "base_case", or "pessimistic"
     */
    private String scenarioName;
    
    /**
     * Calculated intrinsic value per share for this scenario.
     */
    private Double intrinsicValue;
    
    /**
     * Narrative description of the scenario.
     * Example: "Strong growth with margin expansion driven by scale economies"
     */
    private String description;
    
    /**
     * List of key parameter changes from base case.
     * Example: ["Revenue growth: 30% (vs 22% base)", "Margins expand to 65%"]
     */
    private List<String> keyChanges;
    
    /**
     * Causal dependency chains explaining variable interactions.
     */
    private CausalChainDTO causalChain;
    
    /**
     * Numerical adjustments applied in this scenario.
     */
    private ScenarioAdjustmentsDTO adjustments;
    
    /**
     * Investment thesis narrative in Damodaran style.
     */
    private String investmentThesis;
    
    /**
     * Probability assigned to this scenario (0.0 to 1.0).
     * Example: 0.50 for base case, 0.30 for optimistic, 0.20 for pessimistic
     */
    private Double probability;
}

