package io.stockvaluation;

import io.stockvaluation.config.CurrencyProviderProperties;
import io.stockvaluation.config.SyntheticRatingProperties;
import io.stockvaluation.config.ValuationTemplateProperties;
import io.stockvaluation.config.YFinanceProviderProperties;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.DividendDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.FieldErrorDTO;
import io.stockvaluation.dto.InfoDTO;
import io.stockvaluation.dto.ResponseDTO;
import io.stockvaluation.dto.SyntheticResultDTO;
import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.dto.ValuationTemplate;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;
import io.stockvaluation.provider.BalanceSheetSnapshot;
import io.stockvaluation.provider.DataProviderException;
import io.stockvaluation.provider.IncomeStatementSnapshot;
import io.stockvaluation.service.SpecialCompanies;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageSmokeTest {

    @Test
    void applicationRestTemplateUsesBufferingFactoryAndAllowsNaN() throws Exception {
        RestTemplate restTemplate = new StockValuationBackendApplication().restTemplate();

        assertTrue(restTemplate.getRequestFactory() instanceof BufferingClientHttpRequestFactory);
        MappingJackson2HttpMessageConverter converter = restTemplate.getMessageConverters().stream()
                .filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .findFirst()
                .orElseThrow();
        Map<?, ?> parsed = converter.getObjectMapper().readValue("{\"value\":NaN}", Map.class);
        assertTrue(Double.isNaN((Double) parsed.get("value")));
    }

    @Test
    void enumsReturnExpectedDefaultsAndLegacyMappings() {
        assertEquals(GrowthPattern.STABLE, GrowthPattern.fromString("stable growth"));
        assertEquals(GrowthPattern.THREE_STAGE, GrowthPattern.fromString("three stage"));
        assertEquals(GrowthPattern.N_STAGE, GrowthPattern.fromString("n-stage model"));
        assertEquals(17, GrowthPattern.THREE_STAGE.getArrayLength());
        assertEquals(EarningsLevel.NORMALIZED, EarningsLevel.fromString("Normalized"));
        assertEquals(EarningsLevel.CURRENT, EarningsLevel.fromString(null));
        assertEquals(ModelType.OPTION_PRICING, ModelType.fromString("Option Pricing Model"));
        assertEquals(ModelType.DISCOUNTED_CF, ModelType.fromString("unknown"));
        assertEquals(CashflowType.FCFF, CashflowType.fromString("anything"));
        assertFalse(CashflowType.FCFF.isEquityValuation());
    }

    @Test
    void valuationTemplateAndDividendDtoConvenienceMethodsBehaveAsExpected() {
        ValuationTemplate template = new ValuationTemplate();
        template.setProjectionYears(10);
        template.setArrayLength(12);
        template.setGrowthPattern(GrowthPattern.TWO_STAGE);
        template.setEarningsLevel(EarningsLevel.NORMALIZED);
        template.setCashflowToDiscount(CashflowType.FCFF);
        template.setModelType(ModelType.DISCOUNTED_CF);
        template.setNormalizedOperatingMargin(18.5);
        template.getMetadata().put("legacyCashflowSuggestion", "FCFF");

        assertEquals(11, template.getTerminalYearIndex());
        assertEquals(10, template.getLastProjectionYearIndex());
        assertTrue(template.isFCFFModel());
        assertTrue(template.useNormalizedEarnings());
        assertTrue(template.toString().contains("Two-stage Growth"));

        DividendDataDTO dividendData = DividendDataDTO.builder()
                .dividendRate(2.5)
                .trailingAnnualDividendRate(2.2)
                .payoutRatio(0.5)
                .dividendYield(0.025)
                .dividendHistory(Map.of("2024", 2.4, "2023", 2.2, "2022", 2.0, "2021", 1.8))
                .dividendGrowthRate(0.20)
                .build();

        assertTrue(dividendData.isDividendPaying());
        assertTrue(dividendData.hasSufficientHistory());
        assertTrue(dividendData.isSuitableForDDM());
        assertEquals(2.5, dividendData.getCurrentDividend());
        assertEquals(0.15, dividendData.getEstimatedGrowthRate(0.20));

        dividendData.setDividendRate(null);
        dividendData.setDividendGrowthRate(null);
        assertEquals(2.2, dividendData.getCurrentDividend());
        assertEquals(0.10, dividendData.getEstimatedGrowthRate(0.20));
    }

    @Test
    void dtoCopiesPropertiesAndDelegateToDividendData() {
        DividendDataDTO dividendData = DividendDataDTO.builder().dividendRate(1.0).dividendYield(0.02).payoutRatio(0.5).build();

        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRevenueNextYear(0.12);
        driveData.setRiskFreeRate(4.0);

        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setRevenueTTM(100.0);

        CompanyDataDTO companyData = new CompanyDataDTO();
        companyData.setCompanyDriveDataDTO(driveData);
        companyData.setFinancialDataDTO(financialData);
        companyData.setDividendDataDTO(dividendData);

        CompanyDataDTO copy = new CompanyDataDTO(companyData);
        assertSame(driveData, copy.getCompanyDriveDataDTO());
        assertTrue(copy.isDividendPaying());
        assertTrue(copy.isSuitableForDDM());
        assertTrue(companyData.toString().contains("dividendDataDTO"));
        assertTrue(driveData.toString().contains("riskFreeRate"));
        assertTrue(financialData.toString().contains("revenueTTM"));

        CompanyDTO companyDTO = new CompanyDTO();
        companyDTO.setEstimatedValuePerShare(123.45);

        ValuationOutputDTO output = new ValuationOutputDTO();
        output.setCompanyName("Example");
        output.setCompanyDTO(companyDTO);
        output.setPrimaryModel(CashflowType.FCFF);
        output.setValuationId("valuation-1");
        output.setUserValuationId("user-1");

        ValuationOutputDTO outputCopy = new ValuationOutputDTO(output);
        assertEquals(123.45, output.getRecommendedIntrinsicValue());
        assertSame(companyDTO, outputCopy.getCompanyDTO());
        assertEquals("valuation-1", outputCopy.getValuationId());
        assertEquals("user-1", outputCopy.getUserValuationId());

        SyntheticResultDTO syntheticResult = new SyntheticResultDTO("10.0", "A", "1.5", "1.2", "6.7");
        assertEquals("A", syntheticResult.getEstimatedBondRating());

        InfoDTO info = new InfoDTO();
        info.setCompanyName("Example");
        info.setTicker("EXMP");
        info.setWebsite("https://example.com");
        info.setDateOfValuation(java.time.LocalDate.of(2026, 3, 10));
        info.setCountryOfIncorporation("Sweden");
        info.setIndustryUs("software");
        info.setIndustryGlobal("technology");
        info.setNoOfShareOutstanding(10.0);
        info.setStockPrice(100.0);
        info.setLowestStockPrice(90.0);
        info.setHighestStockPrice(110.0);
        info.setPriceChangeFromLastStock(5.0);
        info.setPercentageChangeFromLastStock(5.0);
        info.setPriceChangeCurrentStock(10.0);
        info.setPercentageChangeCurrentStock(9.09);
        assertEquals("Example", info.getCompanyName());
        assertEquals(9.09, info.getPercentageChangeCurrentStock());

        ResponseDTO<String> ok = new ResponseDTO<>("payload");
        ResponseDTO<String> withStatus = new ResponseDTO<>("payload", 202);
        ResponseDTO<String> full = new ResponseDTO<>("payload", "done", true, 200, "OK");
        ResponseDTO<String> custom = new ResponseDTO<>("payload", "message", true, 201);
        ResponseDTO<String> errors = new ResponseDTO<>(java.util.List.of(new FieldErrorDTO("field", "message", null)));
        assertTrue(ok.isSuccess());
        assertEquals(202, withStatus.getHttpStatus());
        assertEquals("OK", full.getErrorCode());
        assertEquals("message", custom.getMessage());
        assertFalse(errors.isSuccess());
    }

    @Test
    void configPropertiesSnapshotsExceptionsAndSpecialCompaniesBehaveAsExpected() {
        ValuationTemplateProperties templateProperties = new ValuationTemplateProperties();
        assertEquals(0.03, templateProperties.getExpectedInflation());
        templateProperties.setDefaultProjectionYears(12);
        assertEquals(12, templateProperties.getDefaultProjectionYears());

        SyntheticRatingProperties syntheticRatingProperties = new SyntheticRatingProperties();
        syntheticRatingProperties.setDefaultCountry("Sweden");
        assertEquals("Sweden", syntheticRatingProperties.getDefaultCountry());

        CurrencyProviderProperties currencyProviderProperties = new CurrencyProviderProperties();
        currencyProviderProperties.setBaseUrl("https://example.com");
        assertEquals("https://example.com", currencyProviderProperties.getBaseUrl());

        YFinanceProviderProperties providerProperties = new YFinanceProviderProperties();
        providerProperties.setBaseUrl("https://yfinance.example.com");
        assertEquals("https://yfinance.example.com", providerProperties.getBaseUrl());

        DataProviderException exception = new DataProviderException("stub", "AAPL", "failed");
        assertEquals("[stub] Failed for ticker 'AAPL': failed", exception.getMessage());

        assertNull(BalanceSheetSnapshot.empty().bookValueEquity());
        assertNull(IncomeStatementSnapshot.empty().totalRevenue());

        io.stockvaluation.dto.BasicInfoDataDTO special = new io.stockvaluation.dto.BasicInfoDataDTO();
        special.setCurrency("USD");
        special.setTicker("AAPL");
        assertTrue(SpecialCompanies.isSpecialCompanies(special));

        io.stockvaluation.form.FinancialDataInput input = new io.stockvaluation.form.FinancialDataInput();
        input.setBasicInfoDataDTO(special);
        assertEquals(20.0, SpecialCompanies.reAdjustROIC(input, 10.0), 1e-9);
        assertEquals(5.0, SpecialCompanies.reAdjustSalesToCapitalFirstPhases(special, 10.0, 5.0), 1e-9);
    }
}
