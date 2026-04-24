package io.stockvaluation.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ValuationAssumptionProperties – covers the config package that was
 * at 47%.
 * Tests default values and that setters/getters work correctly.
 */
class ValuationAssumptionPropertiesTest {

    @Test
    void defaultValues_areCorrect() {
        ValuationAssumptionProperties props = new ValuationAssumptionProperties();

        assertEquals(0.05, props.getPreTaxCostOfDebt(), 0.0001);
        assertEquals(0.05, props.getConvergenceYearMargin(), 0.0001);
        assertEquals(4.77, props.getMatureMarketPremium(), 0.0001);
        assertEquals(4.77, props.getDamodaran().getMatureMarketErp(), 0.0001);
        assertEquals("2026-04-01", props.getDamodaran().getDataDate());
        assertEquals("ctrypremApr26.xlsx", props.getDamodaran().getCountryRiskSource());
        assertEquals(4.58, props.getBaselineRiskFreeRate(), 0.0001);
        assertEquals("USD", props.getBaselineRiskFreeCurrencyCode());
        assertEquals(10_000, props.getSimulationIterations());
        assertEquals(10_000, props.getCalibrationMaxIterations());
        assertEquals(24, props.getImpliedExpectationGridSteps());
        assertEquals(28, props.getImpliedExpectationBisectionIterations());
        assertEquals(0.25, props.getImpliedExpectationTolerance(), 0.0001);
        assertFalse(props.isStrictGrowthPolicy());
    }

    @Test
    void setters_mutateValues() {
        ValuationAssumptionProperties props = new ValuationAssumptionProperties();

        props.setPreTaxCostOfDebt(0.06);
        props.setConvergenceYearMargin(0.10);
        props.getDamodaran().setMatureMarketErp(5.0);
        props.getDamodaran().setDataDate("2026-01-01");
        props.getDamodaran().setCountryRiskSource("ctryprem.xlsx");
        props.setBaselineRiskFreeRate(3.5);
        props.setBaselineRiskFreeCurrencyCode("EUR");
        props.setSimulationIterations(500);
        props.setCalibrationMaxIterations(200);
        props.setImpliedExpectationGridSteps(10);
        props.setImpliedExpectationBisectionIterations(12);
        props.setImpliedExpectationTolerance(0.10);
        props.setStrictGrowthPolicy(true);

        assertEquals(0.06, props.getPreTaxCostOfDebt(), 0.0001);
        assertEquals(0.10, props.getConvergenceYearMargin(), 0.0001);
        assertEquals(5.0, props.getMatureMarketPremium(), 0.0001);
        assertEquals(5.0, props.getDamodaran().getMatureMarketErp(), 0.0001);
        assertEquals("2026-01-01", props.getDamodaran().getDataDate());
        assertEquals("ctryprem.xlsx", props.getDamodaran().getCountryRiskSource());
        assertEquals(3.5, props.getBaselineRiskFreeRate(), 0.0001);
        assertEquals("EUR", props.getBaselineRiskFreeCurrencyCode());
        assertEquals(500, props.getSimulationIterations());
        assertEquals(200, props.getCalibrationMaxIterations());
        assertEquals(10, props.getImpliedExpectationGridSteps());
        assertEquals(12, props.getImpliedExpectationBisectionIterations());
        assertEquals(0.10, props.getImpliedExpectationTolerance(), 0.0001);
        assertTrue(props.isStrictGrowthPolicy());
    }

    @Test
    void strictGrowthPolicy_defaultIsFalse_canBeEnabled() {
        ValuationAssumptionProperties props = new ValuationAssumptionProperties();
        assertFalse(props.isStrictGrowthPolicy());

        props.setStrictGrowthPolicy(true);
        assertTrue(props.isStrictGrowthPolicy());

        props.setStrictGrowthPolicy(false);
        assertFalse(props.isStrictGrowthPolicy());
    }

    @Test
    void simulationIterations_zeroAndNegative_canBeSet() {
        ValuationAssumptionProperties props = new ValuationAssumptionProperties();
        props.setSimulationIterations(0);
        assertEquals(0, props.getSimulationIterations());

        props.setSimulationIterations(-1);
        assertEquals(-1, props.getSimulationIterations());
    }
}
