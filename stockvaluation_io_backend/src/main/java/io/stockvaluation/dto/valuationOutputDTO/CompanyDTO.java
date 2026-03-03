package io.stockvaluation.dto.valuationOutputDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class CompanyDTO {

    private Double terminalCashFlow;
    private Double terminalCostOfCapital;
    private Double terminalValue;
    private Double pvTerminalValue;
    private Double pvCFOverNext10Years;
    private Double sumOfPV;
    private Double probabilityOfFailure;
    private Double proceedsIfFirmFails;
    private Double valueOfOperatingAssets;
    private Double debt;
    private Double minorityInterests;
    private Double cash;
    private Double nonOperatingAssets;
    private Double valueOfEquity;
    private Double valueOfOptions;
    private Double valueOfEquityInCommonStock;
    private Double numberOfShares;
    private Double estimatedValuePerShare;
    private Double price;
    private Double priceAsPercentageOfValue;
}
