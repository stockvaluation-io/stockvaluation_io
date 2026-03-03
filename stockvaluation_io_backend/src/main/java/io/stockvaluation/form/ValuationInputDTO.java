package io.stockvaluation.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValuationInputDTO {

    private boolean hasRAndDExpensesToCapitalize = false;
    private boolean hasOperatingLeaseCommitments = false;
    private boolean hasEmployeeOptionsOutstanding = false;
    private Double numberOfOptionsOutstanding = 0.00;
    private boolean hasOverrideCostOfCapitalAfterYear10 = false;
    private Double costOfCapitalAfterYear10 = 0.0;
    private boolean hasOverrideReturnOnCapitalAfterYear10 = false;
    private Double returnOnCapitalAfterYear10 = 0.0;
    private boolean hasAssumeNoFailure = false;
    private Double probabilityOfFailure = 0.0;
    private String proceedsInFailureTieTo = "";
    private Double distressProceedsAsPercentage = 0.0;
    private boolean overrideReinvestmentLag = false;
    private Double reinvestmentLag = 0.00;
    private Double nolCarriedOver = 0.0;
    private boolean overrideRiskFreeRateAfterYear10 = false;
    private boolean hasEffectiveTaxRateAdjustToMarginalRate = false;
    private Double riskFreeRateAfterYear10 = 0.0;
    private boolean overrideGrowthRateInPerpetuity = false;
    private Double growthRateInPerpetuity = 0.0;
    private boolean overrideTrappedCash = false;
    private Double trappedCash = 0.0;
    private Double averageForeignTaxRate = 0.0;

}
