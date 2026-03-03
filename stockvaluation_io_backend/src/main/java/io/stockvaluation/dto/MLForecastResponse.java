package io.stockvaluation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response DTO from ML DCF Forecast service.
 * Contains year-on-year distributions for DCF parameters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MLForecastResponse {

    private String ticker;
    private Integer forecastYears;
    private Predictions predictions;

    // Additional fields returned by ML service (optional)
    private Map<String, Object> distribution;
    private Map<String, Object> simulationResultsDto;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Predictions {
        private List<YearPrediction> revenue_growth_rate;
        private List<YearPrediction> operating_margin;
        private List<YearPrediction> sales_to_capital_ratio;
        private List<YearPrediction> cost_of_capital;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class YearPrediction {
        private Integer year;
        private Double value;
        private Distribution distribution;
        private String explanation;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Distribution {
        private Double mean;
        private Double std;
        private Percentiles percentiles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Percentiles {
        private Double p5;
        private Double p50;
        private Double p95;
    }
}
