package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO containing all scenarios and heat map data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CausalScenarioResponse {
    
    /**
     * Company ticker symbol.
     */
    private String ticker;
    
    /**
     * Company name.
     */
    private String companyName;
    
    /**
     * List of three scenarios: optimistic, base_case, pessimistic.
     */
    private List<CausalScenarioDTO> scenarios;
    
    /**
     * Heat map data for 2-variable sensitivity analysis.
     */
    private HeatMapDataDTO heatMapData;
    
    /**
     * Current market price per share.
     */
    private Double currentPrice;
    
    /**
     * Base case intrinsic value (for reference).
     */
    private Double baseIntrinsicValue;
    
    /**
     * Summary narrative of the scenario analysis.
     */
    private String summary;
    
    /**
     * Timestamp of calculation.
     */
    private String timestamp;
}

