package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class BalanceSheetDTO {

    private String companyName;

    private String ticker;

    private Double cashAndMarkablTTM;

    private Double cashAndMarkablLTM;

    private Double bookValueEqualityTTM;

    private Double bookValueEqualityLTM;

    private Double bookValueDebtTTM;

    private Double bookValueDebtLTM;

    private Double lowestStockPrice;

    private Double stockPrice;

    private Double highestStockPrice;

    private Double priceChangeFromLastStock;

    private Double percentageChangeFromLastStock;

    private Double priceChangeCurrentStock;

    private Double percentageChangeCurrentStock;


}
