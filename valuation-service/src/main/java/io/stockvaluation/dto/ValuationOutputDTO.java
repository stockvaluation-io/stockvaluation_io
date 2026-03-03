package io.stockvaluation.dto;

import io.stockvaluation.dto.valuationoutput.*;
import io.stockvaluation.enums.CashflowType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ValuationOutputDTO {

    public ValuationOutputDTO(ValuationOutputDTO other) {
        if (other != null) {
            this.companyName = other.companyName;
            this.profitabilityStory = other.profitabilityStory;
            this.riskStory = other.riskStory;
            this.currency = other.currency;
            this.stockCurrency = other.stockCurrency;
            this.industryUs = other.industryUs;
            this.industryGlobal = other.industryGlobal;

            this.financialDTO = other.financialDTO; // shallow copy
            this.companyDTO = other.companyDTO;
            this.terminalValueDTO = other.terminalValueDTO;
            this.baseYearComparison = other.baseYearComparison;
            this.story = other.story;
            this.simulationResultsDto = other.simulationResultsDto;
            this.calibrationResultDTO = other.calibrationResultDTO;
            this.narrativeDTO = other.narrativeDTO;
            this.assumptionTransparency = other.assumptionTransparency;

            this.primaryModel = other.primaryModel;
            this.modelSelectionRationale = other.modelSelectionRationale;

            // Valuation IDs
            this.valuationId = other.valuationId;
            this.userValuationId = other.userValuationId;
        }
    }

    private String companyName;

    private String profitabilityStory;
    private String riskStory;
    private String currency;
    private String stockCurrency;
    private String industryUs;
    private String industryGlobal;

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
    private AssumptionTransparencyDTO assumptionTransparency;

    /**
     * The primary valuation model selected for this company.
     * FCFF (current supported model)
     */
    private CashflowType primaryModel;

    /**
     * Explanation for why the primary model was selected.
     */
    private String modelSelectionRationale;

    /**
     * Get the recommended intrinsic value from deterministic DCF output.
     */
    public Double getRecommendedIntrinsicValue() {
        return companyDTO != null ? companyDTO.getEstimatedValuePerShare() : null;
    }
}
