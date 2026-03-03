package io.stockvaluation.dto.valuationoutput;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class BaseYearComparisonDTO {

    private Double revenueGrowthCompany;

    private Double revenueGrowthIndustry;

    private Double revenue;

    private Double operatingMarginCompany;

    private Double operatingMarginIndustry;

    private Double operatingIncome;

    private Double ebit;

}



