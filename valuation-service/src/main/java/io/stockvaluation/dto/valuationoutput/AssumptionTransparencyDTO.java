package io.stockvaluation.dto.valuationoutput;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AssumptionTransparencyDTO {

    private String valuationModel;
    private String industryUs;
    private String industryGlobal;
    private String currency;
    private Integer segmentCount;
    private boolean segmentAware;
    private DiscountRate discountRate;
    private OperatingAssumptions operatingAssumptions;
    private List<String> notes = new ArrayList<>();
    private GrowthAnchor growthAnchor;
    private MarketImpliedExpectations marketImpliedExpectations;

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class GrowthAnchor {
        /** Damodaran entity key used for matching. */
        private String entity;
        /** Human-readable display name. */
        private String entityDisplay;
        /** Region the data is sourced from. */
        private String region;
        /** Dataset year. */
        private Integer year;
        /** Number of firms in the industry. */
        private Double numberOfFirms;
        /** Fundamental growth = ROE x Reinvestment Rate. */
        private Double fundamentalGrowth;
        /** Historical growth proxy (median of historical measures). */
        private Double historicalGrowthProxy;
        /** Expected growth proxy (median of forward measures). */
        private Double expectedGrowthProxy;
        /** Heuristic confidence in [0,1]. */
        private Double confidenceScore;
        /** Growth distribution percentiles. */
        private Double p25;
        private Double p50;
        private Double p75;
        /** Data provenance. */
        private String source;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class DiscountRate {
        private Double riskFreeRate;
        private Double equityRiskPremium;
        private Double initialCostOfCapital;
        private Double terminalCostOfCapital;
        private String costOfCapitalFormula;
        private String riskFreeRateSource;
        private String equityRiskPremiumSource;
        private String initialCostOfCapitalSource;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class OperatingAssumptions {
        private Double revenueGrowthRateYears2To5;
        private Double targetOperatingMargin;
        private Double salesToCapitalYears1To5;
        private Double salesToCapitalYears6To10;
        private String revenueGrowthSource;
        private String operatingMarginSource;
        private String salesToCapitalSource;
        private String revenueGrowthRationale;
        private String operatingMarginRationale;
        private String salesToCapitalRationale;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class MarketImpliedExpectations {
        private Double marketPrice;
        private Double modelIntrinsicValue;
        private String method;
        private List<ImpliedMetric> metrics = new ArrayList<>();
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ImpliedMetric {
        private String key;
        private String label;
        private String unit;
        private Double modelValue;
        private Double impliedValue;
        private Double gap;
        private Boolean solved;
        private String note;
    }
}
