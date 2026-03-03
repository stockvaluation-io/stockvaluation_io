package io.stockvaluation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class NarrativeDTO {

    public Growth growth;
    public Margins margins;
    public InvestmentEfficiency investmentEfficiency;
    public Risks risks;
    public KeyTakeaways keyTakeaways;
    public List<BullBearDebate> bullBearDebate;
    public ScenarioAnalysis scenarioAnalysis;
    public RealOptionAnalysis realOptionAnalysis;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Growth {
        private String title;
        public String narrative;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Margins {
        public String title;
        public String narrative;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class InvestmentEfficiency {
        public String title;
        public String narrative;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Risks {
        public String title;
        public String narrative;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class KeyTakeaways {
        public String title;
        public String narrative;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class BullBearDebate {
        public String bear;
        public String bull;
        public Integer round;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ScenarioAnalysis {
        public Scenario optimistic;
        public Scenario base_case;
        public Scenario pessimistic;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        @Getter
        @Setter
        public static class Scenario {
            public String description;
            public List<String> keyChanges;
            public String valuationImpact;
            public Adjustments adjustments;
            public String investmentThesis;
            public Double intrinsicValue;
            
            @JsonProperty("causal_reasoning")
            public CausalReasoning causalReasoning;

            @JsonIgnoreProperties(ignoreUnknown = true)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @NoArgsConstructor
            @AllArgsConstructor
            @Getter
            @Setter
            public static class Adjustments {
                @JsonSetter(nulls = Nulls.AS_EMPTY) // ensures nulls are handled
                @JsonProperty("revenueGrowthRate")
                public List<Double> revenueGrowthRate;

                @JsonSetter(nulls = Nulls.AS_EMPTY)
                @JsonProperty("operatingMargin")
                public List<Double> operatingMargin;

                @JsonSetter(nulls = Nulls.AS_EMPTY)
                @JsonProperty("salesToCapitalRatio")
                public List<Double> salesToCapitalRatio;

                @JsonSetter(nulls = Nulls.AS_EMPTY)
                @JsonProperty("discountRate")
                public List<Double> discountRate;
            }
            
            @JsonIgnoreProperties(ignoreUnknown = true)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @NoArgsConstructor
            @AllArgsConstructor
            @Getter
            @Setter
            public static class CausalReasoning {
                public CausalNarrative growth;
                public CausalNarrative margins;
                public CausalNarrative risk;
                
                @JsonProperty("investment_efficiency")
                public CausalNarrative investmentEfficiency;
                
                @JsonIgnoreProperties(ignoreUnknown = true)
                @JsonInclude(JsonInclude.Include.NON_NULL)
                @NoArgsConstructor
                @AllArgsConstructor
                @Getter
                @Setter
                public static class CausalNarrative {
                    public String title;
                    public String narrative;
                    
                    @JsonProperty("parameter_impact")
                    public String parameterImpact;
                }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class RealOptionAnalysis {
        public String company;
        public String valuationDate;
        public Double baseCompanyValue;
        public Double totalRealOptionsValue;
        public Double enhancedCompanyValue;
        public Double optionPremiumPercentage;
        public Double valuePerShare;
        public List<Option> options;

        @JsonIgnoreProperties(ignoreUnknown = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @NoArgsConstructor
        @AllArgsConstructor
        @Getter
        @Setter
        public static class Option {
            public String optionType;
            public Double confidence;
            public String description;
            public Double underlyingValue;
            public Double exercisePrice;
            public Double timeToExpiryYears;
            public Double volatility;
            public PricingMethods pricingMethods;
            public Double finalOptionValue;

            @JsonIgnoreProperties(ignoreUnknown = true)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @NoArgsConstructor
            @AllArgsConstructor
            @Getter
            @Setter
            public static class PricingMethods {
                public Double blackScholes;
                public Double americanBinomial;
                public Double monteCarloLsm;
            }
        }
    }
}

