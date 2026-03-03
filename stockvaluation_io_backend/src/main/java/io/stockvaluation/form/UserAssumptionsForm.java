package io.stockvaluation.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
// Default Assumptions
public class UserAssumptionsForm {

    private Boolean overrideCostOfCapitalAfterYear10;

    private Double costOfCapitalAfterYear10;

    private Boolean overrideReturnOnCapitalAfterYear10;

    private Double returnOnCapitalAfterYear10;

    private Boolean assumeNoFailure;

    private Double probabilityOfFailure;

    private String proceedsInFailureTieTo;

    private Double distressProceedsAsPercentage;

    private Boolean overrideReinvestmentLag;

    private Integer reinvestmentLag;

    private Boolean overrideTaxRateToMarginalByTerminalYear;

    private Boolean overrideNOL;

    private Double NOLCarriedOver;

    private Boolean overrideRiskFreeRateAfterYear10;

    private Double riskFreeRateAfterYear10;

    private Boolean overrideGrowthRateInPerpetuity;

    private Double growthRateInPerpetuity;

    private Boolean overrideTrappedCash;

    private Double trappedCash;

    private Double averageForeignTaxRate;
}
