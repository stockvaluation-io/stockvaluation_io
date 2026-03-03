package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Dividend Discount Model (DDM) valuation results.
 * Contains both Gordon Growth Model and Two-Stage DDM outputs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DDMResultDTO {
    
    /**
     * Which DDM model was used: "Gordon Growth" or "Two-Stage"
     */
    private String modelUsed;
    
    /**
     * Intrinsic value per share from DDM
     */
    private Double intrinsicValue;
    
    /**
     * Current annual dividend per share used in calculation
     */
    private Double currentDividend;
    
    /**
     * Dividend growth rate used in calculation (as decimal)
     */
    private Double dividendGrowthRate;
    
    /**
     * Cost of equity used for discounting (as decimal)
     */
    private Double costOfEquity;
    
    /**
     * Terminal/stable growth rate (as decimal)
     */
    private Double terminalGrowthRate;
    
    /**
     * Number of years in high growth phase (Two-Stage only)
     */
    private Integer highGrowthYears;
    
    /**
     * High growth rate for initial phase (Two-Stage only, as decimal)
     */
    private Double highGrowthRate;
    
    /**
     * Payout ratio in high growth phase (Two-Stage only)
     */
    private Double highGrowthPayoutRatio;
    
    /**
     * Payout ratio in stable phase
     */
    private Double stablePayoutRatio;
    
    /**
     * Whether DDM was applicable for this company
     */
    private Boolean applicable;
    
    /**
     * Reason if DDM was not applicable
     */
    private String notApplicableReason;
    
    /**
     * Confidence level in DDM result: HIGH, MEDIUM, LOW
     */
    private String confidence;
    
    /**
     * Brief explanation of the DDM calculation
     */
    private String explanation;
    
    /**
     * Create a result for non-applicable companies
     */
    public static DDMResultDTO notApplicable(String reason) {
        return DDMResultDTO.builder()
                .applicable(false)
                .notApplicableReason(reason)
                .build();
    }
    
    /**
     * Create a Gordon Growth Model result
     */
    public static DDMResultDTO gordonGrowth(
            Double intrinsicValue,
            Double currentDividend,
            Double dividendGrowthRate,
            Double costOfEquity) {
        
        return DDMResultDTO.builder()
                .modelUsed("Gordon Growth")
                .intrinsicValue(intrinsicValue)
                .currentDividend(currentDividend)
                .dividendGrowthRate(dividendGrowthRate)
                .terminalGrowthRate(dividendGrowthRate)
                .costOfEquity(costOfEquity)
                .applicable(true)
                .confidence(determineConfidence(dividendGrowthRate, costOfEquity))
                .explanation(String.format(
                    "Gordon Growth Model: D1 / (r - g) = %.2f * (1 + %.2f%%) / (%.2f%% - %.2f%%) = $%.2f",
                    currentDividend,
                    dividendGrowthRate * 100,
                    costOfEquity * 100,
                    dividendGrowthRate * 100,
                    intrinsicValue
                ))
                .build();
    }
    
    /**
     * Create a Two-Stage DDM result
     */
    public static DDMResultDTO twoStage(
            Double intrinsicValue,
            Double currentDividend,
            Double highGrowthRate,
            Integer highGrowthYears,
            Double stableGrowthRate,
            Double costOfEquity) {
        
        return DDMResultDTO.builder()
                .modelUsed("Two-Stage")
                .intrinsicValue(intrinsicValue)
                .currentDividend(currentDividend)
                .highGrowthRate(highGrowthRate)
                .highGrowthYears(highGrowthYears)
                .dividendGrowthRate(highGrowthRate)
                .terminalGrowthRate(stableGrowthRate)
                .costOfEquity(costOfEquity)
                .applicable(true)
                .confidence(determineConfidence(stableGrowthRate, costOfEquity))
                .explanation(String.format(
                    "Two-Stage DDM: %d years at %.1f%% growth, then stable at %.1f%% = $%.2f",
                    highGrowthYears,
                    highGrowthRate * 100,
                    stableGrowthRate * 100,
                    intrinsicValue
                ))
                .build();
    }
    
    /**
     * Determine confidence level based on model inputs
     */
    private static String determineConfidence(Double growthRate, Double costOfEquity) {
        if (growthRate == null || costOfEquity == null) {
            return "LOW";
        }
        
        double spread = costOfEquity - growthRate;
        
        // Higher spread = more reliable
        if (spread > 0.04) {
            return "HIGH";
        } else if (spread > 0.02) {
            return "MEDIUM";
        } else {
            return "LOW"; // Small spread makes model sensitive to small changes
        }
    }
}

