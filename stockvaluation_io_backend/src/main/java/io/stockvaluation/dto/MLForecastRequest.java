package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for ML DCF Forecast service.
 * Maps Java data to the format expected by /api/v1/forecast endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLForecastRequest {

    private String ticker;
    private Integer forecastHorizon; // default: 10
    private FinancialData financialData;
    private CompanyInfo companyInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialData {
        private List<YearData> historicalYears;
        private YearData currentYear;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class YearData {
        private Integer year;
        private Double revenue;
        private Double operatingIncome;
        private Double investedCapital;
        private Double costOfCapital;
        private Double revenueGrowthRate;
        private Double operatingMargin;
        private Double salesToCapitalRatio;
        private Double incomeBeforeTax;
        private Double incomeTax;
        private Double netIncome;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyInfo {
        private Double debt;
        private Double cash;
        private Double shares;
        private Double riskFreeRate;
        private Double beta;
        private Double debtToCapital;
        private String industry;
        private String lifecycleStage;
    }
}
