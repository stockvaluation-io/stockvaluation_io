package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Monte Carlo DCF valuation result with percentile distributions.
 * Used for probabilistic valuation based on ML-generated parameter
 * distributions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonteCarloResult {

    // Valuation percentiles (per share)
    private Double p5; // 5th percentile (downside)
    private Double p25; // 25th percentile
    private Double p50; // Median
    private Double p75; // 75th percentile
    private Double p95; // 95th percentile (upside)
    private Double mean;
    private Double std;

    // Simulation metadata
    private Integer paths;
    private Integer successfulPaths;

    // Input distributions (for frontend visualization)
    private List<YearDistribution> revenueGrowthDistributions;
    private List<YearDistribution> operatingMarginDistributions;
    private List<YearDistribution> salesToCapitalDistributions;
    private List<YearDistribution> costOfCapitalDistributions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearDistribution {
        private Integer year;
        private Double p5;
        private Double p50;
        private Double p95;
        private String explanation;
    }
}
