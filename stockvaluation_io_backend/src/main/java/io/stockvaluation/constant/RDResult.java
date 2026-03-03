package io.stockvaluation.constant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class RDResult {

    private Double totalResearchAsset;
    private Double totalAmortization;
    private Double adjustmentToOperatingIncome;
    private Double taxEffect;

    @JsonIgnore
    private List<YearlyCalculation> yearlyCalculations;

    public RDResult(Double totalResearchAsset, Double totalAmortization, Double adjustmentToOperatingIncome, Double taxEffect) {
        this.totalResearchAsset = totalResearchAsset;
        this.totalAmortization = totalAmortization;
        this.adjustmentToOperatingIncome = adjustmentToOperatingIncome;
        this.taxEffect = taxEffect;
    }
}
