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
public class IntrinsicPricingDTO {
    private String company;
    private String ticker;
    private String sector;
    private Integer peersFound;
    private Boolean llmEnhanced;
    private Map<String, MultipleResultDTO> multiples;
    private List<String> peerList;
    private String recommendedMultiple;
    private String recommendationReason;
    private SectorRecommendationDTO sectorRecommendation;
    private String timestamp;
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class MultipleResultDTO {
        private Double intrinsicValue;
        private Double marketValue;
        private Double mispricingPct;
        private String conclusion;
        private ConfidenceIntervalDTO confidenceInterval;
        private DistributionStatsDTO distribution;
        private RegressionDetailsDTO regression;
        private List<FeatureImportanceDTO> featureImportance;
        private Map<String, CoefficientValidationDTO> coefficientValidation;
        private List<PeerComparisonDTO> peerComparison;
        private Integer nPeersUsed;
        private String error; // For error cases
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ConfidenceIntervalDTO {
        private Double lowerBound;
        private Double upperBound;
        private Double confidence;
        private Double intervalWidth;
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class DistributionStatsDTO {
        private Double median;
        private Double mean;
        private Double std;
        private Double min;
        private Double max;
        private Double percentile25;
        private Double percentile75;
        private Double percentile10;
        private Double percentile90;
        private Double targetPercentile;
        private String targetPosition;
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class RegressionDetailsDTO {
        private String modelType;
        private Double rSquared;
        private Integer nSamples;
        private List<String> features;
        private Map<String, Double> coefficients;
        private Boolean marketCapWeighted;
        private Boolean similarityWeighted;
        private Map<String, Double> vifScores; // VIF scores for multicollinearity
        private Map<String, Double> highVifWarning; // Features with VIF > 10
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class FeatureImportanceDTO {
        private String feature;
        private Double coefficient;
        private Double absCoefficient;
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class CoefficientValidationDTO {
        private Double coefficient;
        private String expectedSign;
        private String actualSign;
        private Boolean matchesTheory;
        private Double vif; // VIF score if available
        private Boolean highMulticollinearity; // VIF > 10
        private Boolean violationLikelyDueToMulticollinearity;
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class PeerComparisonDTO {
        private String ticker;
        private String companyName;
        private Double multipleValue; // The actual multiple value for this peer
        private Map<String, Double> features; // Feature values used in regression
    }
    
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class SectorRecommendationDTO {
        private String multiple;
        private String sector;
        private String rationale;
        private Double rSquared;
    }
}

