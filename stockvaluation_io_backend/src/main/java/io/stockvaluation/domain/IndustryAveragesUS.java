package io.stockvaluation.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "industry_averages_us")
public class IndustryAveragesUS {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String industryName;
    private int numberOfFirms;

    private double annualAverageRevenueGrowth;
    private double preTaxOperatingMargin;
    private double afterTaxRoc;
    private double averageEffectiveTaxRate;
    private double unleveredBeta;
    private double equityBeta;
    private double costOfEquity;
    private double stdDeviationInStockPrices;
    private double preTaxCostOfDebt;
    private double marketDebtToCapital;
    private double costOfCapital;
    private double salesToCapital;
    private double evToSales;
    private double evToEbitda;

    private double evToEbit;
    private double priceToBook;
    private double trailingPe;
    private double nonCashWcAsPercentOfRevenues;
    private double capExAsPercentOfRevenues;
    @JsonProperty("netcapExAsPercentOfRevenues") // Map JSON field to this Java field
    private double netCapExAsPercentOfRevenues;
    private double reinvestmentRate;
    private double roe;
    private double dividendPayoutRatio;
    private double equityReinvestmentRate;

    private double preTaxOperatingMarginAdjusted;
}
