package io.stockvaluation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor

public class LeaseResultDTO {
    private Double depreciationOnOperatingLease;
    private Double adjustmentToOperatingEarnings;
    private Double adjustmentToTotalDebt;
    private Double AdjustmentToDepreciation;

    public LeaseResultDTO(Double depreciationOnOperatingLease, Double adjustmentToOperatingEarnings, Double adjustmentToTotalDebt, Double adjustmentToDepreciation) {
        this.depreciationOnOperatingLease = depreciationOnOperatingLease;
        this.adjustmentToOperatingEarnings = adjustmentToOperatingEarnings;
        this.adjustmentToTotalDebt = adjustmentToTotalDebt;
        AdjustmentToDepreciation = adjustmentToDepreciation;
    }
}
