package io.stockvaluation.service;

import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;

/**
 * ValuationModel.java
 *
 * Implements the logic provided in the Excel formulas the user supplied.
 *
 * Notes:
 *  - All percentages are represented as decimal fractions (e.g. 3% -> 0.03).
 *  - The code follows the IF(...) logic from the spreadsheet exactly.
 *  - The class computes:
 *      - modelType: OPTION_PRICING | DISCOUNTED_CF
 *      - earningsLevel: CURRENT | NORMALIZED
 *      - cashflowToDiscount: FCFF | FCFE | DIVIDENDS
 *      - growthPeriodLength: "No high growth period" | "10 or more years" | "5 to 10 years" | "5 years of less"
 *      - growthPattern: STABLE | TWO_STAGE | THREE_STAGE | N_STAGE
 *
 *  - FCFE is computed as:
 *      FCFE = NI - (CapitalSpending - Depreciation) * (1 - DebtRatio) - DeltaWC * (1 - DebtRatio)
 *
 * Example main() uses the sample inputs from the sheet to reproduce the example outputs.
 */
public class ValuationModel {

    // Inputs (naming corresponds loosely to spreadsheet cell names)
    // Booleans that in the sheet were "Yes"/"No"
    private boolean earningsPositive;      // corresponds to D8 = "Yes"/"No"
    private boolean useOptionPricing;      // corresponds to F25 = "Yes"/"No"
    private boolean negativeIsCyclical;    // corresponds to F22
    private boolean negativeIsOneTime;     // corresponds to F23

    private boolean canEstimateCapexAndWC; // corresponds to F29 (can we estimate capital spending & WC)
    private boolean hasDividendInfoOrIsPayingDividends; // corresponds to F33 (used in nested IF)
    private double f32Value;               // corresponds to F32 (numeric)
    private double g40Value;               // corresponds to G40 (numeric)

    // Growth / advantage inputs
    private String f48GrowthLabel;         // corresponds to F48, string (e.g. "Stable Growth" or other)
    private boolean hasSustainableAdvantage; // corresponds to F15 ("Yes"/"No")
    private double firmGrowthRate;         // corresponds to F14 (e.g. 15% -> 0.15)
    private double expectedInflation;      // E11 (decimal)
    private double expectedRealGrowth;     // E12 (decimal)

    // Inputs used in growth pattern rule
    private String f45EarningsLevelOverride; // corresponds to F45 (e.g. "Normalized Earnings" or other)

    // Inputs for FCFE computation
    private double netIncome;              // NI
    private double depreciation;           // Depreciation & amortization
    private double capitalSpending;        // Capital spending (including acquisitions)
    private double deltaWorkingCapital;    // Δ Non-cash working capital
    private double debtRatio;              // debt ratio in market value terms (decimal, e.g. 0.04)

    // Computed outputs (using enums)
    private ModelType modelType;
    private EarningsLevel earningsLevel;
    private CashflowType cashflowToDiscount;
    private String growthPeriodLength;
    private GrowthPattern growthPattern;
    private double computedFCFE;

    public ValuationModel(
            boolean earningsPositive,
            boolean useOptionPricing,
            boolean negativeIsCyclical,
            boolean negativeIsOneTime,
            boolean canEstimateCapexAndWC,
            boolean hasDividendInfoOrIsPayingDividends,
            double f32Value,
            double g40Value,
            String f48GrowthLabel,
            boolean hasSustainableAdvantage,
            double firmGrowthRate,
            double expectedInflation,
            double expectedRealGrowth,
            String f45EarningsLevelOverride,
            double netIncome,
            double depreciation,
            double capitalSpending,
            double deltaWorkingCapital,
            double debtRatio
    ) {
        this.earningsPositive = earningsPositive;
        this.useOptionPricing = useOptionPricing;
        this.negativeIsCyclical = negativeIsCyclical;
        this.negativeIsOneTime = negativeIsOneTime;
        this.canEstimateCapexAndWC = canEstimateCapexAndWC;
        this.hasDividendInfoOrIsPayingDividends = hasDividendInfoOrIsPayingDividends;
        this.f32Value = f32Value;
        this.g40Value = g40Value;
        this.f48GrowthLabel = f48GrowthLabel;
        this.hasSustainableAdvantage = hasSustainableAdvantage;
        this.firmGrowthRate = firmGrowthRate;
        this.expectedInflation = expectedInflation;
        this.expectedRealGrowth = expectedRealGrowth;
        this.f45EarningsLevelOverride = f45EarningsLevelOverride;
        this.netIncome = netIncome;
        this.depreciation = depreciation;
        this.capitalSpending = capitalSpending;
        this.deltaWorkingCapital = deltaWorkingCapital;
        this.debtRatio = debtRatio;

        // compute everything now
        computeAll();
    }

    private void computeAll() {
        computeModelType();
        computeEarningsLevel();
        computeCashflowToDiscount();
        computeGrowthPeriodLength();
        computeGrowthPattern();
        computeFCFE();
    }

    /**
     * =IF(F25="Yes","Option Pricing Model","Discounted CF Model")
     */
    private void computeModelType() {
        this.modelType = useOptionPricing ? ModelType.OPTION_PRICING : ModelType.DISCOUNTED_CF;
    }

    /**
     * =IF(D8="No",IF(F22="Yes","Normalized Earnings",IF(F23="Yes","Normalized Earnings","Current Earnings")),"Current Earnings")
     *
     * If earningsPositive == false (D8="No") then check negativeIsCyclical or negativeIsOneTime to pick Normalized.
     * Otherwise Current Earnings.
     */
    private void computeEarningsLevel() {
        if (!earningsPositive) {
            if (negativeIsCyclical || negativeIsOneTime) {
                this.earningsLevel = EarningsLevel.NORMALIZED;
            } else {
                this.earningsLevel = EarningsLevel.CURRENT;
            }
        } else {
            this.earningsLevel = EarningsLevel.CURRENT;
        }
    }

    /**
     * =IF(F29="Yes",IF(F33="Yes","FCFF (Value firm)","Dividends (Value equity)"),
     *     IF(D8="No","FCFF(Value firm)",
     *        (IF(F32>1.25*G40,"FCFE (Value equity)",(IF(F32<0.9*G40,"FCFE (Value equity)","Dividends (Value equity)"))))))
     *
     * Transliteration:
     *  - if canEstimateCapexAndWC (F29) true:
     *       if hasDividendInfoOrIsPayingDividends (F33) true -> FCFF
     *       else -> Dividends
     *  - else (can't estimate):
     *       if earningsPositive == false -> FCFF (Value firm)
     *       else compare f32Value to 1.25 * g40Value and 0.9 * g40Value
     */
    private void computeCashflowToDiscount() {
        if (canEstimateCapexAndWC) {
            if (hasDividendInfoOrIsPayingDividends) {
                this.cashflowToDiscount = CashflowType.FCFF;
            } else {
                this.cashflowToDiscount = CashflowType.DIVIDENDS;
            }
        } else {
            if (!earningsPositive) {
                this.cashflowToDiscount = CashflowType.FCFF;
            } else {
                if (f32Value > 1.25 * g40Value) {
                    this.cashflowToDiscount = CashflowType.FCFE;
                } else if (f32Value < 0.9 * g40Value) {
                    this.cashflowToDiscount = CashflowType.FCFE;
                } else {
                    this.cashflowToDiscount = CashflowType.DIVIDENDS;
                }
            }
        }
    }

    /**
     * =IF(F48="Stable Growth","No high growth period",
     *     IF(F15="Yes",IF(F14>(E11+E12+0.06),"10 or more years","5 to 10 years"),
     *        IF(F14>(E11+E12+0.06),"5 to 10 years","5 years of less")))
     *
     * Notes: E11 and E12 are expectedInflation and expectedRealGrowth (decimal).
     */
    private void computeGrowthPeriodLength() {
        if ("Stable Growth".equalsIgnoreCase(f48GrowthLabel)) {
            this.growthPeriodLength = "No high growth period";
            return;
        }

        double threshold = expectedInflation + expectedRealGrowth + 0.06; // E11 + E12 + 0.06

        if (hasSustainableAdvantage) {
            // IF(F15="Yes", IF(F14 > threshold, "10 or more years", "5 to 10 years"))
            if (firmGrowthRate > threshold) {
                this.growthPeriodLength = "10 or more years";
            } else {
                this.growthPeriodLength = "5 to 10 years";
            }
        } else {
            // ELSE branch: IF(F14 > threshold, "5 to 10 years", "5 years of less")
            if (firmGrowthRate > threshold) {
                this.growthPeriodLength = "5 to 10 years";
            } else {
                this.growthPeriodLength = "5 years of less";
            }
        }
    }

    /**
     * =IF(D8="No",IF(F45="Normalized Earnings",IF(F14<(E11+E12+0.0101),"Stable Growth",IF(F14<(E11+E12+0.06),"Two-stage Growth","Three-stage Growth")),"n-stage model"),
     *    IF(F14<(E11+E12+0.0101),"Stable Growth",IF(F14<(E11+E12+0.06),"Two-stage Growth","Three-stage Growth")))
     *
     * Transliteration:
     *  - If earningsPositive == false:
     *       If f45EarningsLevelOverride == "Normalized Earnings" then follow numeric thresholds to pick Stable/Two-stage/Three-stage
     *       Else -> "n-stage model"
     *  - Else (earningsPositive true) -> use thresholds to pick Stable/Two-stage/Three-stage
     */
    private void computeGrowthPattern() {
        double t1 = expectedInflation + expectedRealGrowth + 0.0101; // E11 + E12 + 0.0101
        double t2 = expectedInflation + expectedRealGrowth + 0.06;   // E11 + E12 + 0.06

        if (!earningsPositive) {
            if ("Normalized Earnings".equalsIgnoreCase(f45EarningsLevelOverride)) {
                if (firmGrowthRate < t1) {
                    this.growthPattern = GrowthPattern.STABLE;
                } else if (firmGrowthRate < t2) {
                    this.growthPattern = GrowthPattern.TWO_STAGE;
                } else {
                    this.growthPattern = GrowthPattern.THREE_STAGE;
                }
            } else {
                this.growthPattern = GrowthPattern.N_STAGE;
            }
        } else {
            if (firmGrowthRate < t1) {
                this.growthPattern = GrowthPattern.STABLE;
            } else if (firmGrowthRate < t2) {
                this.growthPattern = GrowthPattern.TWO_STAGE;
            } else {
                this.growthPattern = GrowthPattern.THREE_STAGE;
            }
        }
    }

    /**
     * FCFE = NI - (Capital Spending - Depreciation) *(1 - DebtRatio) - ΔWC *(1 - DebtRatio)
     *
     * This matches the formula you provided.
     */
    private void computeFCFE() {
        double capexNet = capitalSpending - depreciation;
        double multiplier = (1.0 - debtRatio);
        this.computedFCFE = netIncome - capexNet * multiplier - deltaWorkingCapital * multiplier;
    }

    // ----- Getters for outputs -----
    public ModelType getModelType() {
        return modelType;
    }

    public EarningsLevel getEarningsLevel() {
        return earningsLevel;
    }

    public CashflowType getCashflowToDiscount() {
        return cashflowToDiscount;
    }

    public String getGrowthPeriodLength() {
        return growthPeriodLength;
    }

    public GrowthPattern getGrowthPattern() {
        return growthPattern;
    }

    public double getComputedFCFE() {
        return computedFCFE;
    }
    
    // ----- Legacy string getters for backward compatibility -----
    
    /**
     * @deprecated Use getModelType() instead
     */
    @Deprecated
    public String getModelTypeString() {
        return modelType != null ? modelType.getDisplayName() : null;
    }
    
    /**
     * @deprecated Use getEarningsLevel() instead
     */
    @Deprecated
    public String getEarningsLevelString() {
        return earningsLevel != null ? earningsLevel.getDisplayName() : null;
    }
    
    /**
     * @deprecated Use getCashflowToDiscount() instead
     */
    @Deprecated
    public String getCashflowToDiscountString() {
        return cashflowToDiscount != null ? cashflowToDiscount.getDisplayName() : null;
    }
    
    /**
     * @deprecated Use getGrowthPattern() instead
     */
    @Deprecated
    public String getGrowthPatternString() {
        return growthPattern != null ? growthPattern.getDisplayName() : null;
    }
}
