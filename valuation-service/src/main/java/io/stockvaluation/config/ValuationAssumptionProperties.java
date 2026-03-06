package io.stockvaluation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "valuation.assumptions")
public class ValuationAssumptionProperties {

    private double preTaxCostOfDebt = 0.05;
    private double convergenceYearMargin = 0.05;
    private double matureMarketPremium = 4.23;
    private double baselineRiskFreeRate = 4.58;
    private String matureMarketCountry = "United States";
    private String baselineRiskFreeCurrencyCode = "USD";
    private int simulationIterations = 10000;
    private int calibrationMaxIterations = 10000;
    private int impliedExpectationGridSteps = 24;
    private int impliedExpectationBisectionIterations = 28;
    private double impliedExpectationTolerance = 0.25;
    private boolean strictGrowthPolicy = false;
}
