package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.constant.YearlyCalculation;
import io.stockvaluation.domain.RDConverter;
import io.stockvaluation.domain.RiskFreeRate;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.BalanceSheetDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.DividendDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.IncomeStatementDTO;
import io.stockvaluation.dto.InfoDTO;
import io.stockvaluation.enums.InputDetails;
import io.stockvaluation.exception.BadRequestException;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.RDConverterRepository;
import io.stockvaluation.repository.RiskFreeRateRepository;
import io.stockvaluation.repository.SectorMappingRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommonServiceTest {

    @Mock
    private ValuationAssumptionProperties valuationAssumptionProperties;
    @Mock
    private RiskFreeRateRepository riskFreeRateRepository;
    @Mock
    private CountryEquityRepository countryEquityRepository;
    @Mock
    private SectorMappingRepository sectorMappingRepository;
    @Mock
    private RDConverterRepository rdConverterRepository;
    @Mock
    private CompanyDataAssemblyService companyDataAssemblyService;
    @Mock
    private SegmentWeightedParameterService segmentWeightedParameterService;

    @InjectMocks
    private CommonService commonService;

    @BeforeEach
    void setUp() throws Exception {
        // Clear the state map before each test if needed
        Field mapField = CommonService.class.getDeclaredField("basicAndFinancialMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) mapField.get(commonService);
        map.clear();
    }

    @Test
    void getCompanyDataFromProvider_Success() {
        String ticker = "AAPL";
        CompanyDataDTO mockData = new CompanyDataDTO();
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        FinancialDataDTO financialData = new FinancialDataDTO();
        mockData.setBasicInfoDataDTO(basicInfo);
        mockData.setFinancialDataDTO(financialData);

        when(companyDataAssemblyService.assembleCompanyData(ticker)).thenReturn(mockData);

        CompanyDataDTO result = commonService.getCompanyDataFromProvider(ticker);

        assertNotNull(result);
        assertEquals(mockData, result);
        verify(companyDataAssemblyService, times(1)).assembleCompanyData(ticker);
    }

    @Test
    void resolveBaselineRiskFreeRate_CurrencyCodeNull() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn(null);
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(0.04);

        double rate = commonService.resolveBaselineRiskFreeRate();

        assertEquals(0.04, rate);
    }

    @Test
    void resolveBaselineRiskFreeRate_CurrencyCodeExistsInRepo() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn("USD");
        when(riskFreeRateRepository.findRiskFreeRateByCurrency("USD")).thenReturn(Optional.of(0.05));

        double rate = commonService.resolveBaselineRiskFreeRate();

        assertEquals(0.05, rate);
    }

    @Test
    void resolveBaselineRiskFreeRate_CurrencyCodeNotInRepo() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn("USD");
        when(riskFreeRateRepository.findRiskFreeRateByCurrency("USD")).thenReturn(Optional.empty());
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(0.045);

        double rate = commonService.resolveBaselineRiskFreeRate();

        assertEquals(0.045, rate);
    }

    @Test
    void resolveMatureMarketPremium_usesConfiguredValueOnly() {
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);

        double premium = commonService.resolveMatureMarketPremium();

        assertEquals(4.77, premium);
        verifyNoInteractions(countryEquityRepository);
    }

    @Test
    void resolveEquityRiskPremiumForCountry_addsCountryRiskPremiumToConfiguredMatureErp() {
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);
        when(countryEquityRepository.findCountryRiskPremiumByCountry("India")).thenReturn(Optional.of(3.20914358987782));

        double premium = commonService.resolveEquityRiskPremiumForCountry("India");

        assertEquals(7.97914358987782, premium, 0.000001);
    }

    @Test
    void resolveEquityRiskPremiumForCountry_usIncludesAa1CountryRiskPremium() {
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);
        when(countryEquityRepository.findCountryRiskPremiumByCountry("United States"))
                .thenReturn(Optional.of(0.26131717269259455));

        double premium = commonService.resolveEquityRiskPremiumForCountry("United States");

        assertEquals(5.031317172692595, premium, 0.000001);
    }

    @Test
    void resolveEquityRiskPremiumForCountry_missingCountryUsesMatureErpOnly() {
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);

        double premium = commonService.resolveEquityRiskPremiumForCountry("   ");

        assertEquals(4.77, premium);
        verifyNoInteractions(countryEquityRepository);
    }

    @Test
    void resolveRiskFreeRateForCurrency_NullCurrencyCode() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn(null);
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(0.04);

        double rate = commonService.resolveRiskFreeRateForCurrency(null);

        assertEquals(0.04, rate);
    }

    @Test
    void resolveRiskFreeRateForCurrency_ValidCurrencyCode() {
        when(riskFreeRateRepository.findRiskFreeRateByCurrency("EUR")).thenReturn(Optional.of(0.06));

        double rate = commonService.resolveRiskFreeRateForCurrency("EUR");

        assertEquals(0.06, rate);
    }

    @Test
    void getCompanyDetails_MapEmpty_ThrowsException() {
        assertThrows(BadRequestException.class, () -> commonService.getCompanyDetails(InputDetails.INFO));
    }

    @Test
    void getCompanyDetails_IncomeStatement() throws Exception {
        setupCompanyDataInService();

        Object result = commonService.getCompanyDetails(InputDetails.INCOME_STATEMENT);

        assertTrue(result instanceof IncomeStatementDTO);
        IncomeStatementDTO dto = (IncomeStatementDTO) result;
        assertEquals("AAPL", dto.getTicker());
        assertEquals("Apple Inc", dto.getCompanyName());
    }

    @Test
    void getCompanyDetails_Info() throws Exception {
        setupCompanyDataInService();

        Object result = commonService.getCompanyDetails(InputDetails.INFO);

        assertTrue(result instanceof InfoDTO);
        InfoDTO dto = (InfoDTO) result;
        assertEquals("AAPL", dto.getTicker());
        assertEquals("Apple Inc", dto.getCompanyName());
    }

    @Test
    void getCompanyDetails_BalanceSheet() throws Exception {
        setupCompanyDataInService();

        Object result = commonService.getCompanyDetails(InputDetails.BALANCE_SHEET);

        assertTrue(result instanceof BalanceSheetDTO);
        BalanceSheetDTO dto = (BalanceSheetDTO) result;
        assertEquals("AAPL", dto.getTicker());
        assertEquals("Apple Inc", dto.getCompanyName());
    }

    private void setupCompanyDataInService() throws Exception {
        Field mapField = CommonService.class.getDeclaredField("basicAndFinancialMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) mapField.get(commonService);

        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setCompanyName("Apple Inc");

        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setStockPrice(150.0);
        financialData.setPreviousDayStockPrice(145.0);
        financialData.setHighestStockPrice(160.0);
        financialData.setLowestStockPrice(140.0);

        map.put("basicInfoDTO", basicInfo);
        map.put("financialDTO", financialData);
        map.put("ticker", "AAPL");
    }

    @Test
    void applySegmentWeightedParameters_CallsSegmentWeightedService() {
        FinancialDataInput input = new FinancialDataInput();
        CompanyDataDTO companyDataDTO = new CompanyDataDTO();
        List<String> adjustedParams = List.of("growth");

        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn(null);
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(0.04);

        commonService.applySegmentWeightedParameters(input, companyDataDTO, adjustedParams);

        verify(segmentWeightedParameterService, times(1)).applySegmentWeightedParameters(input, companyDataDTO,
                adjustedParams, 0.04);
    }

    @Test
    void calculateRDConverterValue_AllAvailable() {
        String industry = "Software";
        Double marginalTaxRate = 25.0;

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 100.0);
        rdMap.put("currentR&D-1", 90.0);
        rdMap.put("currentR&D-2", 80.0);
        rdMap.put("currentR&D-3", 70.0);
        rdMap.put("currentR&D-4", 60.0);

        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Software (System & Application)");
        when(sectorMappingRepository.findByIndustryName(industry)).thenReturn(mapping);

        RDConverter rdConverter = new RDConverter();
        rdConverter.setAmortizationPeriod(5);
        when(rdConverterRepository.findByIndustryName(mapping.getIndustryAsPerExcel())).thenReturn(rdConverter);

        RDResult result = commonService.calculateRDConverterValue(industry, marginalTaxRate, rdMap);

        assertNotNull(result);
        assertTrue(result.getAdjustmentToOperatingIncome() > 0);
        assertTrue(result.getTaxEffect() > 0);
        assertTrue(result.getTotalResearchAsset() > 0);
        assertTrue(result.getTotalAmortization() > 0);
    }

    @Test
    void calculateRDConverterValue_NoCurrentRD() {
        String industry = "Software";
        Double marginalTaxRate = 25.0;

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 0.0);

        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Software (System & Application)");
        when(sectorMappingRepository.findByIndustryName(industry)).thenReturn(mapping);

        RDConverter rdConverter = new RDConverter();
        rdConverter.setAmortizationPeriod(5);
        when(rdConverterRepository.findByIndustryName(mapping.getIndustryAsPerExcel())).thenReturn(rdConverter);

        RDResult result = commonService.calculateRDConverterValue(industry, marginalTaxRate, rdMap);

        assertNotNull(result);
        assertEquals(0.0, result.getAdjustmentToOperatingIncome());
        assertEquals(0.0, result.getTaxEffect());
        assertEquals(0.0, result.getTotalResearchAsset());
        assertEquals(0.0, result.getTotalAmortization());
    }
}
