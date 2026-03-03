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
}
