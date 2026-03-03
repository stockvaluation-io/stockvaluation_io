package io.stockvaluation.dto.valuationoutput;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SimulationResultsDTO {
    private Double average;
    private Double min;
    private Double max;
    private Double fifthPercentile;
    private Double fiftyPercentile;
    private Double ninthPercentile;
}
