package io.stockvaluation.dto.valuationOutputDTO;


import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FinancialDTO {


    //     0 for base year , 1 - for year 1 and last 11 is for terminal year
    private Double[] revenueGrowthRate = new Double[12];
    private Double[] revenues = new Double[12];
    private Double[] ebitOperatingMargin = new Double[12];
    private Double[] ebitOperatingIncome = new Double[12];

    private Map<String, Double[]> revenueGrowthRateBySector = new HashMap<>();
    private Map<String, Double[]> revenuesBySector = new HashMap<>();
    private Map<String, Double[]> ebitOperatingMarginBySector = new HashMap<>();
    private Map<String, Double[]> ebitOperatingIncomeSector = new HashMap<>();

    private Double[] taxRate = new Double[12];
    private Double[] ebit1MinusTax = new Double[12];
    private Double[] reinvestment = new Double[12];
    private Double[] fcff = new Double[12];
    private Double[] nol = new Double[12];
    private Double[] costOfCapital = new Double[12];
    private Double[] comulatedDiscountedFactor = new Double[12];
    private Double[] pvFcff = new Double[12];
    private Double[] salesToCapitalRatio = new Double[12];
    private Double[] investedCapital = new Double[12];
    private Double[] roic = new Double[12];

//    Implied variable DTO Merged into financial DTO
//    private Double[] salesToCapitalRatio = new Double[12];
//    private Double[] investedCapital = new Double[12];
//    private Double[] roic = new Double[12];

}
