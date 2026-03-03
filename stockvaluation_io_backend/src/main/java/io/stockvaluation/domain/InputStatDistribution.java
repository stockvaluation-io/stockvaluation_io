package io.stockvaluation.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class InputStatDistribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String industryGroup;
    private int count;
    // Revenue Growth Rate (Last 3 years) Percentiles
    private double revenueGrowthRateFirstQuartile;
    private double revenueGrowthRateMedian;
    private double revenueGrowthRateThirdQuartile;

    // Pre-tax Operating Margin Percentiles
    private double preTaxOperatingMarginFirstQuartile;
    private double preTaxOperatingMarginMedian;
    private double preTaxOperatingMarginThirdQuartile;

    // Sales to Invested Capital Percentiles
    private double salesToInvestedCapitalFirstQuartile;
    private double salesToInvestedCapitalMedian;
    private double salesToInvestedCapitalThirdQuartile;

    // Cost of Capital Percentiles
    private double costOfCapitalFirstQuartile;
    private double costOfCapitalMedian;
    private double costOfCapitalThirdQuartile;

    // Beta Percentiles
    private double betaFirstQuartile;
    private double betaMedian;
    private double betaThirdQuartile;

    // Debt to Capital Ratio Percentiles
    private double debtToCapitalRatioFirstQuartile;
    private double debtToCapitalRatioMedian;
    private double debtToCapitalRatioThirdQuartile;
}
