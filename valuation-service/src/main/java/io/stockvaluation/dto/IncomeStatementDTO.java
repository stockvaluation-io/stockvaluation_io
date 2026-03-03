package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class IncomeStatementDTO {

    private String ticker;

    private String companyName;

    private Double revenueTTM;

    private Double revenueLTM;

    private Double operatingIncomeTTM;

    private Double operatingIncomeLTM;

    private Double effectiveTaxRate;

    private Double lowestStockPrice;

    private Double highestStockPrice;

    private Double priceChangeFromLastStock;

    private Double percentageChangeFromLastStock;

    private Double priceChangeCurrentStock;

    private Double percentageChangeCurrentStock;

    private Double stockPrice;

}
