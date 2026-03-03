package io.stockvaluation.service;

import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

@Component
public class CompanyDataMapper {

    public BasicInfoDataDTO mapBasicInfo(String ticker, Map<String, Object> basicInfoMap) {
        if (Objects.nonNull(basicInfoMap) && basicInfoMap.containsKey("trailingPegRatio")) {
            basicInfoMap.remove("trailingPegRatio");
        }

        validateBasicInfoPayload(ticker, basicInfoMap);

        BasicInfoDataDTO basicInfoDataDTO = new BasicInfoDataDTO();
        basicInfoDataDTO.setTicker(ticker);

        if (basicInfoMap.get("financialCurrency") != null) {
            basicInfoDataDTO.setCurrency(basicInfoMap.get("financialCurrency").toString());
        }
        if (basicInfoMap.get("currency") != null) {
            basicInfoDataDTO.setStockCurrency(basicInfoMap.get("currency").toString());
        }
        if (basicInfoMap.get("longName") != null) {
            basicInfoDataDTO.setCompanyName(basicInfoMap.get("longName").toString());
        }
        if (basicInfoMap.get("country") != null) {
            basicInfoDataDTO.setCountryOfIncorporation(basicInfoMap.get("country").toString());
        }
        if (basicInfoMap.get("industryKey") != null) {
            basicInfoDataDTO.setIndustryUs(basicInfoMap.get("industryKey").toString());
            basicInfoDataDTO.setIndustryGlobal(basicInfoMap.get("industryKey").toString());
        }
        if (basicInfoMap.get("longBusinessSummary") != null) {
            basicInfoDataDTO.setSummary(basicInfoMap.get("longBusinessSummary").toString());
        }
        if (basicInfoMap.get("website") != null) {
            basicInfoDataDTO.setWebsite(basicInfoMap.get("website").toString());
        }
        if (basicInfoMap.get("compensationRisk") != null) {
            basicInfoDataDTO.setCompensationRisk((Integer) basicInfoMap.get("compensationRisk"));
        }
        if (basicInfoMap.get("marketCap") != null) {
            basicInfoDataDTO.setMarketCap(Long.parseLong(basicInfoMap.get("marketCap").toString()));
        }
        if (basicInfoMap.get("heldPercentInstitutions") != null) {
            basicInfoDataDTO.setHeldPercentInstitutions(
                    Double.parseDouble(basicInfoMap.get("heldPercentInstitutions").toString()));
        }
        if (basicInfoMap.get("firstTradeDateEpochUtc") != null) {
            basicInfoDataDTO.setFirstTradeDateEpochUtc((Integer) basicInfoMap.get("firstTradeDateEpochUtc"));
        }

        if (basicInfoMap.get("firstTradeDateMilliseconds") != null) {
            Object firstTradeDateObj = basicInfoMap.get("firstTradeDateMilliseconds");

            if (firstTradeDateObj instanceof Long firstTradeDateMillis) {
                int firstTradeDateEpochUtc = (int) (firstTradeDateMillis / 1000);
                basicInfoDataDTO.setFirstTradeDateEpochUtc(firstTradeDateEpochUtc);
            } else {
                basicInfoDataDTO.setFirstTradeDateEpochUtc(0);
            }
        }

        if (basicInfoMap.get("timeZoneFullName") != null) {
            basicInfoDataDTO.setTimeZoneFullName(basicInfoMap.get("timeZoneFullName").toString());
        }

        if (basicInfoMap.get("beta") != null) {
            basicInfoDataDTO.setBeta((Double) basicInfoMap.get("beta"));
        }

        if (basicInfoMap.get("debtToEquity") != null) {
            basicInfoDataDTO.setDebtToEquity((Double) basicInfoMap.get("debtToEquity"));
        }

        basicInfoDataDTO.setDateOfValuation(LocalDate.now());
        return basicInfoDataDTO;
    }

    private void validateBasicInfoPayload(String ticker, Map<String, Object> basicInfoMap) {
        if (Objects.isNull(basicInfoMap) || basicInfoMap.isEmpty()) {
            throw new BadRequestException("We are Not able to find the company using ticker " + ticker
                    + ". Make sure that you verify the company ticker name on the Yahoo Finance.");
        }

        if (basicInfoMap.get("sectorKey") != null
                && basicInfoMap.get("sectorKey").toString().toLowerCase().contains("financial")) {
            throw new BadRequestException(
                    "We currently do not provide valuations for companies in the financial services sector. However, we are actively developing a valuation module for this sector..");
        }
    }
}
