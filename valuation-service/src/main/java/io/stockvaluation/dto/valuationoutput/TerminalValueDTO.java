package io.stockvaluation.dto.valuationoutput;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TerminalValueDTO {
    Double growthRate;
    Double costOfCapital;
    Double returnOnCapital;
    Double reinvestmentRate;
}
