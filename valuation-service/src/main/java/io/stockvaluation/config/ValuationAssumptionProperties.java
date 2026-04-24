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
    private DamodaranAssumptions damodaran = new DamodaranAssumptions();
    private double baselineRiskFreeRate = 4.58;
    private String baselineRiskFreeCurrencyCode = "USD";
    private int simulationIterations = 10000;
    private int calibrationMaxIterations = 10000;
    private int impliedExpectationGridSteps = 24;
    private int impliedExpectationBisectionIterations = 28;
    private double impliedExpectationTolerance = 0.25;
    private boolean strictGrowthPolicy = false;

    /**
     * Compatibility getter for existing valuation code. Configuration writes go
     * through valuation.assumptions.damodaran.mature-market-erp only.
     */
    public double getMatureMarketPremium() {
        return damodaran.getMatureMarketErp();
    }

    @Getter
    @Setter
    public static class DamodaranAssumptions {
        private double matureMarketErp = 4.77;
        private String dataDate = "2026-04-01";
        private String countryRiskSource = "ctrypremApr26.xlsx";
    }
}
