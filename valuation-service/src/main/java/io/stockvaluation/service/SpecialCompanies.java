package io.stockvaluation.service;

import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.form.FinancialDataInput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SpecialCompanies {
    private static final Map<String, List<String>> companies;

    static {
        companies = new HashMap<>();
        companies.put(
                "USD",
                List.of("MSFT", "GOOGL", "AMZN", "NVDA", "GOOG", "META", "TSLA", "AVGO", "TSMC", "TSM", "AAPL", "INFY", "KO", "BRK-B", "LLY", "ORCL")
        );

        companies.put(
                "INR",
                List.of("TCS.NS", "INFY.NS", "ITC.BO", "RELIANCE.NS", "BHARTIARTL.NS", "HCLTECH.NS", "SUNPHARMA.NS", "WIT")
        );

        companies.put(
                "SEK",
                List.of("ATCO-B.ST", "EVO-B.ST")
        );
    }

    public static double reAdjustSalesToCapitalFirstPhases(BasicInfoDataDTO basicInfoDataDTO, Double salesToCapitalFirstPhase, Double salesToCapital) {
        if (isSpecialCompanies(basicInfoDataDTO.getCurrency(), basicInfoDataDTO.getCurrency())) {
            return 2 * salesToCapitalFirstPhase;
        } else {
            if(Objects.nonNull(salesToCapitalFirstPhase))
                return Math.max(salesToCapitalFirstPhase / 2, salesToCapital);
            return salesToCapital;
        }
    }

    public static double reAdjustROIC(FinancialDataInput financialDataInput, double roic) {
        if (isSpecialCompanies(financialDataInput.getBasicInfoDataDTO().getCurrency(), financialDataInput.getBasicInfoDataDTO().getTicker())) {
            return 2 * roic;
        } else {
            return roic;
        }
    }

    public static boolean isSpecialCompanies(BasicInfoDataDTO infoDataDTO) {
        return companies.containsKey(infoDataDTO.getCurrency().toUpperCase())
                && companies.get(
                infoDataDTO.getCurrency().toUpperCase()
        ).contains(infoDataDTO.getTicker().toUpperCase());
    }

    private static boolean isSpecialCompanies(String currency, String ticker) {
        return companies.containsKey(currency.toUpperCase())
                && companies.get(currency.toUpperCase()).contains(ticker.toUpperCase());
    }
}
