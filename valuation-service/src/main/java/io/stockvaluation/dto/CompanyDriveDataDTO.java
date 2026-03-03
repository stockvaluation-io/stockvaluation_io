package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CompanyDriveDataDTO {

    @Override
    public String toString() {
        return "CompanyDriveDataDTO [revenueNextYear=" + revenueNextYear + ", operatingMarginNextYear="
                + operatingMarginNextYear + ", compoundAnnualGrowth2_5=" + compoundAnnualGrowth2_5 + ", riskFreeRate="
                + riskFreeRate + ", initialCostCapital=" + initialCostCapital + ", convergenceYearMargin="
                + convergenceYearMargin + ", salesToCapitalYears1To5=" + salesToCapitalYears1To5
                + ", salesToCapitalYears6To10=" + salesToCapitalYears6To10 + ", targetPreTaxOperatingMargin="
                + targetPreTaxOperatingMargin + "]";
    }
    private Double revenueNextYear;

    private Double operatingMarginNextYear;

    private Double compoundAnnualGrowth2_5;

    private Double riskFreeRate;

    private Double initialCostCapital;

    private Double convergenceYearMargin;

    private Double salesToCapitalYears1To5;

    private Double salesToCapitalYears6To10;

    private Double targetPreTaxOperatingMargin;

}
