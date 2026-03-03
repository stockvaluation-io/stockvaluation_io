package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DcfAdjustmentRationaleDTO {
    private String revenueGrowthRationale;
    private String operatingMarginRationale;
    private String waccRationale;
    private String taxRateRationale;
}

