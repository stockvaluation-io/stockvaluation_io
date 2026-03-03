package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BaseDcfAnalysisDTO {
    private String valuationId;        // UUID of the valuation record in Supabase
    private String userValuationId;    // UUID of the user_valuations link record
    private InvestmentThesis investmentThesis;
    private DcfAnalysis dcfAnalysis;
    private RiskAssessment riskAssessment;
    private Recommendations recommendations;
    private SynthesisMetadata synthesisMetadata;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class InvestmentThesis {
        private String summary;
        private List<String> keyDrivers;
        private String thesisStrength; // strong|moderate|weak
        private String timeHorizon;    // short|medium|long term
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class DcfAnalysis {
        private String currentValuation;
        private String adjustedValuation;
        private String valuationRange;
        private KeyAssumptions keyAssumptions;
        private List<DcfAdjustmentInstruction> dcfAdjustmentInstructions;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class KeyAssumptions {
        private String growthRate;
        private String margins;
        private String wacc;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class DcfAdjustmentInstruction {
        private String parameter;            // e.g., revenue_cagr, operating_margin, wacc, etc.
        private String unit;                 // percent
        private Double baselineValue;
        private Double newValue;
        private Double deltaAbs;
        private Double deltaPct;
        private Integer deltaBps;            // nullable, only for wacc/terminal_growth
        private String rationale;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class RiskAssessment {
        private List<String> keyRisks;
        private String riskLevel;            // low|medium|high
        private List<String> mitigationStrategies;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Recommendations {
        private String investmentRating;     // buy|hold|sell
        private String confidenceLevel;      // high|medium|low
        private List<String> keyActions;
        private List<String> monitoringPoints;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class SynthesisMetadata {
        private String analysisCompleteness;     // complete|partial
        private String dataQuality;              // high|medium|low
        private String methodologyConfidence;    // high|medium|low
        private String lastUpdated;              // timestamp string
    }
}

