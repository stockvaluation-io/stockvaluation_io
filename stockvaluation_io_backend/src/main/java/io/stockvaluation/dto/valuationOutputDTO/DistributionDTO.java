package io.stockvaluation.dto.valuationOutputDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class DistributionDTO {
    /**
     * Histogram data for valuation distribution
     */
    private HistogramDTO histogram;
    
    /**
     * Confidence intervals (90% and 95%)
     */
    private Map<String, ConfidenceIntervalDTO> confidenceIntervals;
    
    /**
     * Probability that Value Per Share > Current Price
     */
    private Double probabilityUndervalued;
    
    /**
     * Probability that Value Per Share < Current Price
     */
    private Double probabilityOvervalued;
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class HistogramDTO {
        /**
         * Bin centers (midpoints of each bin)
         */
        private List<Double> bins;
        
        /**
         * Frequencies (probabilities) for each bin
         */
        private List<Double> frequencies;
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ConfidenceIntervalDTO {
        private Double lower;
        private Double upper;
    }
}

