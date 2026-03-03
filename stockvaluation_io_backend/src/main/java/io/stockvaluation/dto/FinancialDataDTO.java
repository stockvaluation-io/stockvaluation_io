package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class FinancialDataDTO {
    
    public String toString() {
        return "FinancialDataDTO [revenueTTM=" + revenueTTM + ", revenueLTM=" + revenueLTM + ", operatingIncomeTTM="
                + operatingIncomeTTM + ", operatingIncomeLTM=" + operatingIncomeLTM + ", interestExpenseTTM="
                + interestExpenseTTM + ", interestExpenseLTM=" + interestExpenseLTM + ", bookValueEqualityTTM="
                + bookValueEqualityTTM + ", bookValueEqualityLTM=" + bookValueEqualityLTM + ", bookValueDebtTTM="
                + bookValueDebtTTM + ", bookValueDebtLTM=" + bookValueDebtLTM + ", cashAndMarkablTTM=" + cashAndMarkablTTM
                + ", cashAndMarkablLTM=" + cashAndMarkablLTM + ", nonOperatingAssetTTM=" + nonOperatingAssetTTM
                + ", nonOperatingAssetLTM=" + nonOperatingAssetLTM + ", minorityInterestTTM=" + minorityInterestTTM
                + ", minorityInterestLTM=" + minorityInterestLTM + ", noOfShareOutstanding=" + noOfShareOutstanding
                + ", stockPrice=" + stockPrice + ", lowestStockPrice=" + lowestStockPrice + ", highestStockPrice="
                + highestStockPrice + ", previousDayStockPrice=" + previousDayStockPrice + ", effectiveTaxRate="
                + effectiveTaxRate + ", marginalTaxRate=" + marginalTaxRate + ", researchAndDevelopmentMap="
                + researchAndDevelopmentMap + "]";
    }
    private Double revenueTTM;

    private Double revenueLTM;

    private Double operatingIncomeTTM;

    private Double operatingIncomeLTM;

    private Double interestExpenseTTM;

    private Double interestExpenseLTM;

    private Double bookValueEqualityTTM;

    private Double bookValueEqualityLTM;

    private Double bookValueDebtTTM;

    private Double bookValueDebtLTM;

    private Double cashAndMarkablTTM;

    private Double cashAndMarkablLTM;

    private Double nonOperatingAssetTTM;

    private Double nonOperatingAssetLTM;

    private Double minorityInterestTTM;

    private Double minorityInterestLTM;

    private Double noOfShareOutstanding;

    private Double stockPrice;

    private Double lowestStockPrice;

    private Double highestStockPrice;

    private Double previousDayStockPrice;

    private Double effectiveTaxRate;

    private Double marginalTaxRate;

    private Map<String, Double> researchAndDevelopmentMap;

}
