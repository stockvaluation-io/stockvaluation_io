package io.stockvaluation.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValuationModel Tests
 * 
 * Tests the core valuation model logic based on Damodaran's framework:
 * - Model Type Selection (Option Pricing vs DCF)
 * - Earnings Level (Current vs Normalized)
 * - Cashflow Selection (FCFF vs FCFE vs Dividends)
 * - Growth Period Length
 * - Growth Pattern (Stable, Two-stage, Three-stage, n-stage)
 * - FCFE Calculation
 */
public class ValuationModelTest {

    // Default test parameters
    private static final double DEFAULT_NET_INCOME = 1000000.0;
    private static final double DEFAULT_DEPRECIATION = 100000.0;
    private static final double DEFAULT_CAPITAL_SPENDING = 150000.0;
    private static final double DEFAULT_DELTA_WC = 50000.0;
    private static final double DEFAULT_DEBT_RATIO = 0.04;
    private static final double DEFAULT_INFLATION = 0.03;
    private static final double DEFAULT_REAL_GROWTH = 0.02;

    // ================================================================
    // 1. MODEL TYPE SELECTION TESTS (4 tests)
    // ================================================================

    @Nested
    @DisplayName("1. Model Type Selection Tests")
    class ModelTypeSelectionTests {

        @Test
        @DisplayName("useOptionPricing=true should return Option Pricing Model")
        void testOptionPricingModelSelected() {
            ValuationModel model = createModel(
                true,   // earningsPositive
                true,   // useOptionPricing
                false,  // negativeIsCyclical
                false,  // negativeIsOneTime
                true,   // canEstimateCapexAndWC
                true,   // hasDividendInfo
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.ModelType.OPTION_PRICING, model.getModelType());
        }

        @Test
        @DisplayName("useOptionPricing=false should return Discounted CF Model")
        void testDCFModelSelected() {
            ValuationModel model = createModel(
                true,   // earningsPositive
                false,  // useOptionPricing
                false,  // negativeIsCyclical
                false,  // negativeIsOneTime
                true,   // canEstimateCapexAndWC
                true,   // hasDividendInfo
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.ModelType.DISCOUNTED_CF, model.getModelType());
        }

        @Test
        @DisplayName("Default model type should be DCF for standard company")
        void testDefaultModelType() {
            ValuationModel model = createDefaultModel(true, 0.10);
            assertEquals(io.stockvaluation.enums.ModelType.DISCOUNTED_CF, model.getModelType());
        }

        @Test
        @DisplayName("Loss-making company should still use DCF if no option pricing")
        void testLossMakingCompanyDCF() {
            ValuationModel model = createModel(
                false,  // earningsPositive (loss-making)
                false,  // useOptionPricing
                false, false, true, true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.ModelType.DISCOUNTED_CF, model.getModelType());
        }
    }

    // ================================================================
    // 2. EARNINGS LEVEL TESTS (6 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Earnings Level Tests")
    class EarningsLevelTests {

        @Test
        @DisplayName("Positive earnings should use Current Earnings")
        void testPositiveEarningsCurrentEarnings() {
            ValuationModel model = createModel(
                true,   // earningsPositive
                false, false, false, true, true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, model.getEarningsLevel());
        }

        @Test
        @DisplayName("Negative earnings + cyclical should use Normalized Earnings")
        void testNegativeCyclicalNormalizedEarnings() {
            ValuationModel model = createModel(
                false,  // earningsPositive
                false,
                true,   // negativeIsCyclical
                false,  // negativeIsOneTime
                true, true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.NORMALIZED, model.getEarningsLevel());
        }

        @Test
        @DisplayName("Negative earnings + one-time should use Normalized Earnings")
        void testNegativeOneTimeNormalizedEarnings() {
            ValuationModel model = createModel(
                false,  // earningsPositive
                false,
                false,  // negativeIsCyclical
                true,   // negativeIsOneTime
                true, true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.NORMALIZED, model.getEarningsLevel());
        }

        @Test
        @DisplayName("Negative earnings + both cyclical and one-time should use Normalized")
        void testNegativeBothNormalizedEarnings() {
            ValuationModel model = createModel(
                false,  // earningsPositive
                false,
                true,   // negativeIsCyclical
                true,   // negativeIsOneTime
                true, true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.NORMALIZED, model.getEarningsLevel());
        }

        @Test
        @DisplayName("Negative earnings + neither cyclical nor one-time should use Current")
        void testNegativeNeitherCurrentEarnings() {
            ValuationModel model = createModel(
                false,  // earningsPositive
                false,
                false,  // negativeIsCyclical
                false,  // negativeIsOneTime
                true, true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, model.getEarningsLevel());
        }

        @Test
        @DisplayName("Distressed company with structural losses uses Current Earnings")
        void testDistressedCompanyCurrentEarnings() {
            ValuationModel model = createModel(
                false, false, false, false,
                true, true,
                0.0, 0.0, "", false, -0.30 // Severe decline
            );
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, model.getEarningsLevel());
        }
    }

    // ================================================================
    // 3. CASHFLOW SELECTION TESTS (8 tests)
    // ================================================================

    @Nested
    @DisplayName("3. Cashflow Selection Tests")
    class CashflowSelectionTests {

        @Test
        @DisplayName("Can estimate capex + has dividend info should use FCFF")
        void testFCFFWithDividendInfo() {
            ValuationModel model = createModel(
                true, false, false, false,
                true,   // canEstimateCapexAndWC
                true,   // hasDividendInfo
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.FCFF, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("Can estimate capex + no dividend info should use Dividends")
        void testDividendsNoDividendInfo() {
            ValuationModel model = createModel(
                true, false, false, false,
                true,   // canEstimateCapexAndWC
                false,  // hasDividendInfo
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.DIVIDENDS, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("Cannot estimate capex + negative earnings should use FCFF")
        void testFCFFNegativeEarnings() {
            ValuationModel model = createModel(
                false,  // earningsPositive
                false, false, false,
                false,  // canEstimateCapexAndWC
                true,
                0.0, 0.0, "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.FCFF, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("f32 > 1.25 * g40 should use FCFE")
        void testFCFEHighF32() {
            ValuationModel model = createModel(
                true, false, false, false,
                false,  // canEstimateCapexAndWC
                true,
                150.0,  // f32Value
                100.0,  // g40Value (1.25 * 100 = 125, 150 > 125)
                "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.FCFE, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("f32 < 0.9 * g40 should use FCFE")
        void testFCFELowF32() {
            ValuationModel model = createModel(
                true, false, false, false,
                false,  // canEstimateCapexAndWC
                true,
                80.0,   // f32Value
                100.0,  // g40Value (0.9 * 100 = 90, 80 < 90)
                "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.FCFE, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("f32 between 0.9 and 1.25 * g40 should use Dividends")
        void testDividendsMiddleRange() {
            ValuationModel model = createModel(
                true, false, false, false,
                false,  // canEstimateCapexAndWC
                true,
                100.0,  // f32Value
                100.0,  // g40Value (90 <= 100 <= 125)
                "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.DIVIDENDS, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("f32 exactly at 1.25 * g40 should use Dividends")
        void testDividendsAtUpperBound() {
            ValuationModel model = createModel(
                true, false, false, false,
                false, true,
                125.0,  // f32Value = 1.25 * g40
                100.0,  // g40Value
                "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.DIVIDENDS, model.getCashflowToDiscount());
        }

        @Test
        @DisplayName("f32 exactly at 0.9 * g40 should use Dividends")
        void testDividendsAtLowerBound() {
            ValuationModel model = createModel(
                true, false, false, false,
                false, true,
                90.0,   // f32Value = 0.9 * g40
                100.0,  // g40Value
                "", false, 0.10
            );
            
            assertEquals(io.stockvaluation.enums.CashflowType.DIVIDENDS, model.getCashflowToDiscount());
        }
    }

    // ================================================================
    // 4. GROWTH PERIOD LENGTH TESTS (6 tests)
    // ================================================================

    @Nested
    @DisplayName("4. Growth Period Length Tests")
    class GrowthPeriodLengthTests {

        @Test
        @DisplayName("Stable Growth label should return No high growth period")
        void testStableGrowthNoHighGrowthPeriod() {
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0,
                "Stable Growth",  // f48GrowthLabel
                false, 0.10
            );
            
            assertEquals("No high growth period", model.getGrowthPeriodLength());
        }

        @Test
        @DisplayName("Sustainable advantage + high growth should return 10+ years")
        void testSustainableAdvantageHighGrowth10Years() {
            // threshold = 0.03 + 0.02 + 0.06 = 0.11
            // firmGrowthRate = 0.15 > 0.11
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "",
                true,   // hasSustainableAdvantage
                0.15    // firmGrowthRate > threshold
            );
            
            assertEquals("10 or more years", model.getGrowthPeriodLength());
        }

        @Test
        @DisplayName("Sustainable advantage + moderate growth should return 5-10 years")
        void testSustainableAdvantageModerateGrowth5To10Years() {
            // threshold = 0.11, firmGrowthRate = 0.08 < 0.11
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "",
                true,   // hasSustainableAdvantage
                0.08    // firmGrowthRate < threshold
            );
            
            assertEquals("5 to 10 years", model.getGrowthPeriodLength());
        }

        @Test
        @DisplayName("No sustainable advantage + high growth should return 5-10 years")
        void testNoAdvantageHighGrowth5To10Years() {
            // threshold = 0.11, firmGrowthRate = 0.15 > 0.11
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "",
                false,  // hasSustainableAdvantage
                0.15    // firmGrowthRate > threshold
            );
            
            assertEquals("5 to 10 years", model.getGrowthPeriodLength());
        }

        @Test
        @DisplayName("No sustainable advantage + low growth should return 5 years or less")
        void testNoAdvantageLowGrowth5YearsOrLess() {
            // threshold = 0.11, firmGrowthRate = 0.05 < 0.11
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "",
                false,  // hasSustainableAdvantage
                0.05    // firmGrowthRate < threshold
            );
            
            assertEquals("5 years of less", model.getGrowthPeriodLength());
        }

        @Test
        @DisplayName("Growth rate at exactly threshold should return lower tier")
        void testGrowthRateAtThreshold() {
            // threshold = 0.03 + 0.02 + 0.06 = 0.11
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "",
                false,
                0.11    // firmGrowthRate = threshold
            );
            
            // At threshold, not greater, so should be 5 years or less
            assertEquals("5 years of less", model.getGrowthPeriodLength());
        }
    }

    // ================================================================
    // 5. GROWTH PATTERN TESTS (8 tests)
    // ================================================================

    @Nested
    @DisplayName("5. Growth Pattern Tests")
    class GrowthPatternTests {

        // t1 = 0.03 + 0.02 + 0.0101 = 0.0601
        // t2 = 0.03 + 0.02 + 0.06 = 0.11

        @Test
        @DisplayName("Positive earnings + growth < t1 should be Stable Growth")
        void testPositiveEarningsStableGrowth() {
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false,
                0.04    // firmGrowthRate < 0.0601 (t1)
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.STABLE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Positive earnings + t1 < growth < t2 should be Two-stage")
        void testPositiveEarningsTwoStageGrowth() {
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false,
                0.08    // 0.0601 < 0.08 < 0.11
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.TWO_STAGE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Positive earnings + growth > t2 should be Three-stage")
        void testPositiveEarningsThreeStageGrowth() {
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false,
                0.15    // 0.15 > 0.11 (t2)
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.THREE_STAGE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Negative earnings + Normalized + growth < t1 should be Stable")
        void testNegativeNormalizedStableGrowth() {
            ValuationModel model = new ValuationModel(
                false,  // earningsPositive
                false, true, false,  // cyclical = true for normalized
                true, true,
                0.0, 0.0, "",
                false,
                0.04,   // firmGrowthRate
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH,
                "Normalized Earnings",  // f45EarningsLevelOverride
                DEFAULT_NET_INCOME, DEFAULT_DEPRECIATION,
                DEFAULT_CAPITAL_SPENDING, DEFAULT_DELTA_WC, DEFAULT_DEBT_RATIO
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.STABLE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Negative earnings + Normalized + t1 < growth < t2 should be Two-stage")
        void testNegativeNormalizedTwoStageGrowth() {
            ValuationModel model = new ValuationModel(
                false, false, true, false,
                true, true,
                0.0, 0.0, "",
                false,
                0.08,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH,
                "Normalized Earnings",
                DEFAULT_NET_INCOME, DEFAULT_DEPRECIATION,
                DEFAULT_CAPITAL_SPENDING, DEFAULT_DELTA_WC, DEFAULT_DEBT_RATIO
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.TWO_STAGE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Negative earnings + Normalized + growth > t2 should be Three-stage")
        void testNegativeNormalizedThreeStageGrowth() {
            ValuationModel model = new ValuationModel(
                false, false, true, false,
                true, true,
                0.0, 0.0, "",
                false,
                0.15,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH,
                "Normalized Earnings",
                DEFAULT_NET_INCOME, DEFAULT_DEPRECIATION,
                DEFAULT_CAPITAL_SPENDING, DEFAULT_DELTA_WC, DEFAULT_DEBT_RATIO
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.THREE_STAGE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Negative earnings + NOT Normalized should be n-stage model")
        void testNegativeNotNormalizedNStage() {
            ValuationModel model = new ValuationModel(
                false,  // earningsPositive
                false, false, false,  // neither cyclical nor one-time
                true, true,
                0.0, 0.0, "",
                false,
                0.10,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH,
                "Current Earnings",  // NOT normalized
                DEFAULT_NET_INCOME, DEFAULT_DEPRECIATION,
                DEFAULT_CAPITAL_SPENDING, DEFAULT_DELTA_WC, DEFAULT_DEBT_RATIO
            );
            
            assertEquals(io.stockvaluation.enums.GrowthPattern.N_STAGE, model.getGrowthPattern());
        }

        @Test
        @DisplayName("Growth rate at exactly t1 boundary should be Two-stage")
        void testGrowthRateAtT1Boundary() {
            // t1 = 0.0601, at exactly t1 it should NOT be stable (< not <=)
            ValuationModel model = createModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false,
                0.0601  // exactly t1
            );
            
            // Since firmGrowthRate is NOT < t1, it should be Two-stage
            assertEquals(io.stockvaluation.enums.GrowthPattern.TWO_STAGE, model.getGrowthPattern());
        }
    }

    // ================================================================
    // 6. FCFE CALCULATION TESTS (4 tests)
    // ================================================================

    @Nested
    @DisplayName("6. FCFE Calculation Tests")
    class FCFECalculationTests {

        @Test
        @DisplayName("Standard FCFE calculation")
        void testStandardFCFECalculation() {
            // FCFE = NI - (CapSpending - Depreciation) * (1 - DebtRatio) - DeltaWC * (1 - DebtRatio)
            // FCFE = 1000000 - (150000 - 100000) * 0.96 - 50000 * 0.96
            // FCFE = 1000000 - 50000 * 0.96 - 50000 * 0.96
            // FCFE = 1000000 - 48000 - 48000 = 904000
            ValuationModel model = new ValuationModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false, 0.10,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH, "",
                1000000.0,  // netIncome
                100000.0,   // depreciation
                150000.0,   // capitalSpending
                50000.0,    // deltaWorkingCapital
                0.04        // debtRatio
            );
            
            double expectedFCFE = 1000000 - (150000 - 100000) * 0.96 - 50000 * 0.96;
            assertEquals(expectedFCFE, model.getComputedFCFE(), 0.01);
        }

        @Test
        @DisplayName("FCFE with zero debt ratio")
        void testFCFEZeroDebtRatio() {
            // With zero debt ratio, multiplier = 1.0
            // FCFE = NI - (CapSpending - Depreciation) - DeltaWC
            ValuationModel model = new ValuationModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false, 0.10,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH, "",
                1000000.0,  // netIncome
                100000.0,   // depreciation
                150000.0,   // capitalSpending
                50000.0,    // deltaWorkingCapital
                0.0         // debtRatio = 0
            );
            
            double expectedFCFE = 1000000 - (150000 - 100000) - 50000;
            assertEquals(expectedFCFE, model.getComputedFCFE(), 0.01);
        }

        @Test
        @DisplayName("FCFE with high debt ratio")
        void testFCFEHighDebtRatio() {
            // With 50% debt ratio, multiplier = 0.5
            ValuationModel model = new ValuationModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false, 0.10,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH, "",
                1000000.0,  // netIncome
                100000.0,   // depreciation
                150000.0,   // capitalSpending
                50000.0,    // deltaWorkingCapital
                0.50        // debtRatio = 50%
            );
            
            double expectedFCFE = 1000000 - (150000 - 100000) * 0.5 - 50000 * 0.5;
            assertEquals(expectedFCFE, model.getComputedFCFE(), 0.01);
        }

        @Test
        @DisplayName("FCFE with negative capex net (depreciation > capital spending)")
        void testFCFENegativeCapexNet() {
            // When depreciation > capital spending, capexNet is negative
            // This adds to FCFE
            ValuationModel model = new ValuationModel(
                true, false, false, false, true, true,
                0.0, 0.0, "", false, 0.10,
                DEFAULT_INFLATION, DEFAULT_REAL_GROWTH, "",
                1000000.0,  // netIncome
                200000.0,   // depreciation > capitalSpending
                100000.0,   // capitalSpending
                50000.0,    // deltaWorkingCapital
                0.04        // debtRatio
            );
            
            // capexNet = 100000 - 200000 = -100000
            // FCFE = 1000000 - (-100000) * 0.96 - 50000 * 0.96
            // FCFE = 1000000 + 96000 - 48000 = 1048000
            double expectedFCFE = 1000000 - (100000 - 200000) * 0.96 - 50000 * 0.96;
            assertEquals(expectedFCFE, model.getComputedFCFE(), 0.01);
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private ValuationModel createModel(
            boolean earningsPositive,
            boolean useOptionPricing,
            boolean negativeIsCyclical,
            boolean negativeIsOneTime,
            boolean canEstimateCapexAndWC,
            boolean hasDividendInfo,
            double f32Value,
            double g40Value,
            String f48GrowthLabel,
            boolean hasSustainableAdvantage,
            double firmGrowthRate) {
        
        return new ValuationModel(
            earningsPositive,
            useOptionPricing,
            negativeIsCyclical,
            negativeIsOneTime,
            canEstimateCapexAndWC,
            hasDividendInfo,
            f32Value,
            g40Value,
            f48GrowthLabel,
            hasSustainableAdvantage,
            firmGrowthRate,
            DEFAULT_INFLATION,
            DEFAULT_REAL_GROWTH,
            "",  // f45EarningsLevelOverride
            DEFAULT_NET_INCOME,
            DEFAULT_DEPRECIATION,
            DEFAULT_CAPITAL_SPENDING,
            DEFAULT_DELTA_WC,
            DEFAULT_DEBT_RATIO
        );
    }

    private ValuationModel createDefaultModel(boolean earningsPositive, double firmGrowthRate) {
        return createModel(
            earningsPositive,
            false,  // useOptionPricing
            false,  // negativeIsCyclical
            false,  // negativeIsOneTime
            true,   // canEstimateCapexAndWC
            true,   // hasDividendInfo
            0.0, 0.0, "",
            false,  // hasSustainableAdvantage
            firmGrowthRate
        );
    }
}

