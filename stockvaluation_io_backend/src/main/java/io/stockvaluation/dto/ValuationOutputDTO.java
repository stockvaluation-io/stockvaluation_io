package io.stockvaluation.dto;

import io.stockvaluation.dto.valuationOutputDTO.*;
import io.stockvaluation.enums.CashflowType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ValuationOutputDTO {

    public ValuationOutputDTO(ValuationOutputDTO other) {
        if (other != null) {
            this.companyName = other.companyName;
            this.growthStory = other.growthStory;
            this.profitabilityStory = other.profitabilityStory;
            this.growthEfficiencyStory = other.growthEfficiencyStory;
            this.competitiveAdvantages = other.competitiveAdvantages;
            this.riskStory = other.riskStory;
            this.currency = other.currency;
            this.stockCurrency = other.stockCurrency;

            this.financialDTO = other.financialDTO; // shallow copy
            this.companyDTO = other.companyDTO;
            this.terminalValueDTO = other.terminalValueDTO;
            this.baseYearComparison = other.baseYearComparison;
            this.story = other.story;
            this.simulationResultsDto = other.simulationResultsDto;
            this.calibrationResultDTO = other.calibrationResultDTO;
            this.narrativeDTO = other.narrativeDTO;
            this.adjustmentRationales = other.adjustmentRationales;
            this.heatMapData = other.heatMapData;
            this.distribution = other.distribution;

            // DDM fields
            this.ddmResultDTO = other.ddmResultDTO;
            this.primaryModel = other.primaryModel;
            this.modelSelectionRationale = other.modelSelectionRationale;

            // Intrinsic Pricing fields
            this.intrinsicPricingDTO = other.intrinsicPricingDTO;
            this.intrinsicPricingV2DTO = other.intrinsicPricingV2DTO;

            // Monte Carlo valuation
            this.monteCarloResult = other.monteCarloResult;

            // Animation
            this.valuationAnimationBase64 = other.valuationAnimationBase64;

            // Valuation IDs
            this.valuationId = other.valuationId;
            this.userValuationId = other.userValuationId;
        }
    }

    private String companyName;

    // static values for now as per discussion 25-Nov-2024
    private String growthStory;
    private String profitabilityStory;
    private String growthEfficiencyStory;
    private String competitiveAdvantages;
    private String riskStory;
    private String currency;
    private String stockCurrency;

    /**
     * UUID of the valuation record in Supabase.
     * Set by yfinance after saving to valuations table.
     * Used by frontend to pass to chat/notebook for context loading.
     */
    @JsonProperty("valuation_id")
    private String valuationId;

    /**
     * UUID of the user_valuations link record.
     * Links the user to this specific valuation.
     */
    @JsonProperty("user_valuation_id")
    private String userValuationId;

    private FinancialDTO financialDTO;
    private CompanyDTO companyDTO;
    private TerminalValueDTO terminalValueDTO;
    private BaseYearComparisonDTO baseYearComparison;
    private Story story;
    private SimulationResultsDTO simulationResultsDto;
    private CalibrationResultDTO calibrationResultDTO;
    private NarrativeDTO narrativeDTO;
    private DcfAdjustmentRationaleDTO adjustmentRationales;
    private Map<String, Object> heatMapData;

    /**
     * Probabilistic DCF distribution data.
     * Contains histogram, confidence intervals, and probability metrics.
     * Only populated when ML-based DCF analysis is enabled.
     */
    private DistributionDTO distribution;

    /**
     * DDM (Dividend Discount Model) valuation result.
     * Calculated in parallel with FCFF when company pays dividends.
     * May be null if company doesn't pay dividends.
     */
    private DDMResultDTO ddmResultDTO;

    /**
     * The primary valuation model selected for this company.
     * FCFF (default), FCFE (high leverage), or DIVIDENDS (stable dividend payers)
     */
    private CashflowType primaryModel;

    /**
     * Explanation for why the primary model was selected.
     */
    private String modelSelectionRationale;

    /**
     * Intrinsic pricing (peer-based valuation multiples) result - V1.
     * Calculated using regression analysis against peer companies.
     * May be null if service is unavailable or company has no peers.
     */
    private IntrinsicPricingDTO intrinsicPricingDTO;

    /**
     * Intrinsic pricing (peer-based valuation multiples) result - V2 (Enhanced R²).
     * Only populated when feature flag intrinsic.pricing.enable.v1.v2.comparison is
     * enabled.
     * May be null if service is unavailable, company has no peers, or feature flag
     * is disabled.
     */
    private IntrinsicPricingDTO intrinsicPricingV2DTO;

    /**
     * Monte Carlo DCF valuation result.
     * Contains probabilistic valuation percentiles (p5, p50, p95) based on
     * ML-generated parameter distributions.
     * Only populated when enableMonteCarlo=true and ML service is available.
     * US companies only.
     */
    private MonteCarloResult monteCarloResult;

    /**
     * Base64-encoded Manim animation video explaining DCF valuation.
     * Generated by the valuation-animation service.
     * Only populated when animation service is available and enabled.
     */
    @JsonProperty("valuation_animation_base64")
    private String valuationAnimationBase64;

    /**
     * Check if DDM valuation is available
     */
    public boolean hasDDMValuation() {
        return ddmResultDTO != null && ddmResultDTO.getApplicable() != null && ddmResultDTO.getApplicable();
    }

    /**
     * Get the recommended intrinsic value based on primary model.
     * Returns DDM value if primary is DIVIDENDS and DDM is applicable,
     * otherwise returns FCFF value.
     */
    public Double getRecommendedIntrinsicValue() {
        if (primaryModel == CashflowType.DIVIDENDS && hasDDMValuation()) {
            return ddmResultDTO.getIntrinsicValue();
        }
        return companyDTO != null ? companyDTO.getEstimatedValuePerShare() : null;
    }
}
