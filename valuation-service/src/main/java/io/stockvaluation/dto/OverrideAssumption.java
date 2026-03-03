package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OverrideAssumption {
    private Double overrideCost;
    private Boolean isOverride;
    private Double additionalInputValue;
    private String additionalRadioValue;
}
