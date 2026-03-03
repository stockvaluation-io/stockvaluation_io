package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing causal dependency chains in DCF scenario analysis.
 * 
 * Describes how changes in one variable causally impact others:
 * Revenue → Margin → ROIC → Valuation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CausalChainDTO {
    
    /**
     * How revenue growth affects other metrics.
     * Example: "Higher revenue creates scale economies, expanding margins by 2%"
     */
    private String revenueImpact;
    
    /**
     * How operating margins are affected by scale and competition.
     * Example: "Margin expansion from operating leverage and fixed cost dilution"
     */
    private String marginImpact;
    
    /**
     * How Return on Invested Capital responds to changes.
     * Example: "ROIC improves from 25% to 30% due to higher margins and capital efficiency"
     */
    private String roicImpact;
    
    /**
     * Net impact on company valuation.
     * Example: "Combined effects drive intrinsic value 35% above base case to $250/share"
     */
    private String valueImpact;
}

