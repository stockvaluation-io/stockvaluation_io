package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.domain.CostOfCapital;
import io.stockvaluation.domain.IndustryAveragesGlobal;
import io.stockvaluation.domain.IndustryAveragesUS;
import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.provider.DataProvider;
import io.stockvaluation.repository.CostOfCapitalRepository;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.IndustryAveragesUSRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.RiskFreeRateRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CompanyDataAssemblyServiceTest {

    @Mock
    private CountryEquityRepository countryEquityRepository;
    @Mock
    private SectorMappingRepository sectorMappingRepository;
    @Mock
    private DataProvider dataProvider;
    @Mock
    private RiskFreeRateRepository riskFreeRateRepository;
    @Mock
    private IndustryAveragesUSRepository industryAvgUSRepository;
    @Mock
    private IndustryAveragesGlobalRepository industryAvgGloRepository;
    @Mock
    private InputStatRepository inputStatRepository;
    @Mock
    private CostOfCapitalRepository costOfCapitalRepository;
    @Mock
    private CurrencyRateService currencyRateService;
    @Mock
    private CompanyDataMapper companyDataMapper;
    @Mock
    private CompanyFinancialIngestionService companyFinancialIngestionService;
    @Mock
    private ValuationAssumptionProperties valuationAssumptionProperties;

    @InjectMocks
    private CompanyDataAssemblyService companyDataAssemblyService;

    @BeforeEach
    void setUp() {
        lenient().when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn("USD");
        lenient().when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(4.0);
        lenient().when(valuationAssumptionProperties.getConvergenceYearMargin()).thenReturn(5.0);

        lenient().when(riskFreeRateRepository.findRiskFreeRateByCurrency("USD")).thenReturn(Optional.of(4.2));
    }

    @Test
    void testResolveBaselineRiskFreeRate() {
        Double result = ReflectionTestUtils.invokeMethod(companyDataAssemblyService, "resolveBaselineRiskFreeRate");
        assertEquals(4.2, result);
    }

    @Test
    void testResolveBaselineRiskFreeRate_EmptyCurrencyCode() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn("");
        Double result = ReflectionTestUtils.invokeMethod(companyDataAssemblyService, "resolveBaselineRiskFreeRate");
        assertEquals(4.0, result); // Returns default 4.0 if empty
    }

    @Test
    void testResolveRiskFreeRateForCurrency() {
        lenient().when(riskFreeRateRepository.findRiskFreeRateByCurrency("EUR")).thenReturn(Optional.of(3.5));
        Double result = ReflectionTestUtils.invokeMethod(companyDataAssemblyService, "resolveRiskFreeRateForCurrency",
                "EUR");
        assertEquals(3.5, result);
    }

    @Test
    void testResolveRiskFreeRateForCurrency_NullCurrency() {
        Double result = ReflectionTestUtils.invokeMethod(companyDataAssemblyService, "resolveRiskFreeRateForCurrency",
                (String) null);
        assertEquals(4.2, result);
    }

    @Test
    void testConvertPercentage() {
        Double result = ReflectionTestUtils.invokeMethod(companyDataAssemblyService, "convertPercentage", 250.0);
        assertEquals(2.5, result);

        Double nullResult = ReflectionTestUtils.invokeMethod(companyDataAssemblyService, "convertPercentage",
                (Double) null);
        assertEquals(0.0, nullResult);
    }

    @Test
    void testReAdjustSalesToCapitalFirstPhases() {
        Double result1 = ReflectionTestUtils.invokeMethod(companyDataAssemblyService,
                "reAdjustSalesToCapitalFirstPhases", 4.0, 1.5);
        assertEquals(2.0, result1); // Math.max(4.0/2, 1.5) = 2.0

        Double result2 = ReflectionTestUtils.invokeMethod(companyDataAssemblyService,
                "reAdjustSalesToCapitalFirstPhases", null, 1.5);
        assertEquals(1.5, result2);
    }

    @Test
    void testAssembleCompanyData_US_Company() throws Exception {
        String ticker = "AAPL";

        Map<String, Object> basicInfoMap = new HashMap<>();
        basicInfoMap.put("currency", "USD");
        basicInfoMap.put("financialCurrency", "USD");
        when(dataProvider.getCompanyInfo(ticker)).thenReturn(basicInfoMap);

        BasicInfoDataDTO basicInfoDataDTO = new BasicInfoDataDTO();
        basicInfoDataDTO.setCountryOfIncorporation("United States");
        basicInfoDataDTO.setCurrency("USD");
        basicInfoDataDTO.setIndustryGlobal("Technology");
        basicInfoDataDTO.setTimeZoneFullName("America/New_York");
        basicInfoDataDTO.setMarketCap(1000000000L);
        basicInfoDataDTO.setFirstTradeDateEpochUtc(1600000000); // added to prevent NPE
        when(companyDataMapper.mapBasicInfo(ticker, basicInfoMap)).thenReturn(basicInfoDataDTO);

        FinancialDataDTO financialDataDTO = new FinancialDataDTO();
        financialDataDTO.setStockPrice(150.0);
        financialDataDTO.setRevenueTTM(380000.0);
        financialDataDTO.setRevenueLTM(360000.0);
        financialDataDTO.setOperatingIncomeTTM(110000.0);
        financialDataDTO.setOperatingIncomeLTM(100000.0);

        List<Double> historicalRevenue = List.of(380000.0, 360000.0, 340000.0);
        List<Double> historicalMargins = List.of(0.28, 0.27, 0.26);

        CompanyFinancialIngestionService.FinancialIngestionData ingestionData = new CompanyFinancialIngestionService.FinancialIngestionData(
                financialDataDTO, historicalRevenue, historicalMargins, 15000.0, 100000.0);
        when(companyFinancialIngestionService.ingest(ticker, basicInfoMap)).thenReturn(ingestionData);

        when(countryEquityRepository.findCorporateTaxRateByCountry("United States")).thenReturn(Optional.of(21.0));

        Map<String, Object> revenueEstimateMapData = new HashMap<>();
        Map<String, Object> growthMap = new HashMap<>();
        growthMap.put("+1y", 0.05); // 5% growth
        revenueEstimateMapData.put("growth", growthMap);
        when(dataProvider.getRevenueEstimate(ticker, "yearly")).thenReturn(revenueEstimateMapData);

        SectorMapping sectorMapping = new SectorMapping();
        sectorMapping.setIndustryAsPerExcel("Technology");
        when(sectorMappingRepository.findByIndustryName("Technology")).thenReturn(sectorMapping);

        when(industryAvgUSRepository.findSalesToCapitalByIndustryName("Technology")).thenReturn(Optional.of(1.5));

        IndustryAveragesUS avgUS = new IndustryAveragesUS();
        avgUS.setPreTaxOperatingMargin(25.0);
        avgUS.setAnnualAverageRevenueGrowth(8.0);
        when(industryAvgUSRepository.findByIndustryName("Technology")).thenReturn(avgUS);

        InputStatDistribution inputStat = new InputStatDistribution();
        inputStat.setPreTaxOperatingMarginFirstQuartile(10.0);
        inputStat.setPreTaxOperatingMarginMedian(20.0);
        inputStat.setPreTaxOperatingMarginThirdQuartile(30.0);
        inputStat.setSalesToInvestedCapitalThirdQuartile(2.0);
        when(inputStatRepository.findFirstByIndustryGroupOrderByIdAsc("Technology"))
                .thenReturn(Optional.of(inputStat));

        CostOfCapital costOfCapital = new CostOfCapital();
        costOfCapital.setMedian("0.08");
        costOfCapital.setThirdQuartile("0.10");
        when(costOfCapitalRepository.findCostOfCapitalByRegion("US")).thenReturn(Optional.of(costOfCapital));

        CompanyDataDTO result = companyDataAssemblyService.assembleCompanyData(ticker);

        assertNotNull(result);
        assertNotNull(result.getBasicInfoDataDTO());
        assertNotNull(result.getFinancialDataDTO());
        assertNotNull(result.getCompanyDriveDataDTO());

        assertEquals(0.15, result.getFinancialDataDTO().getEffectiveTaxRate(), 0.01); // 15000 / 100000
        assertEquals(21.0, result.getFinancialDataDTO().getMarginalTaxRate(), 0.01);
        assertEquals(0.05, result.getCompanyDriveDataDTO().getRevenueNextYear());
        assertEquals(2.0, result.getCompanyDriveDataDTO().getSalesToCapitalYears1To5(), 0.01);
        assertEquals(1.5, result.getCompanyDriveDataDTO().getSalesToCapitalYears6To10(), 0.01);
    }

    @Test
    void testAssembleCompanyData_CurrencyConversion() throws Exception {
        String ticker = "SOME_TICKER";

        Map<String, Object> basicInfoMap = new HashMap<>();
        basicInfoMap.put("currency", "EUR");
        basicInfoMap.put("financialCurrency", "USD");
        when(dataProvider.getCompanyInfo(ticker)).thenReturn(basicInfoMap);

        BasicInfoDataDTO basicInfoDataDTO = new BasicInfoDataDTO();
        basicInfoDataDTO.setCountryOfIncorporation("France");
        basicInfoDataDTO.setCurrency("EUR");
        basicInfoDataDTO.setFirstTradeDateEpochUtc(1500000000); // added to prevent NPE
        when(companyDataMapper.mapBasicInfo(ticker, basicInfoMap)).thenReturn(basicInfoDataDTO);

        FinancialDataDTO financialDataDTO = new FinancialDataDTO();
        financialDataDTO.setStockPrice(150.0);
        financialDataDTO.setRevenueTTM(100.0); // avoid zero division
        financialDataDTO.setRevenueLTM(90.0);
        financialDataDTO.setOperatingIncomeTTM(10.0); // avoid zero division
        financialDataDTO.setOperatingIncomeLTM(9.0);

        CompanyFinancialIngestionService.FinancialIngestionData ingestionData = new CompanyFinancialIngestionService.FinancialIngestionData(
                financialDataDTO, new ArrayList<>(), new ArrayList<>(), null, null);
        when(companyFinancialIngestionService.ingest(ticker, basicInfoMap)).thenReturn(ingestionData);

        when(currencyRateService.convertCurrency("EUR", "USD", 150.0)).thenReturn(165.0);
        when(countryEquityRepository.findCorporateTaxRateByCountry("France")).thenReturn(Optional.of(25.0));

        when(dataProvider.getRevenueEstimate(ticker, "yearly")).thenReturn(new HashMap<>());

        // Setup minimal valid data to avoid NPE
        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Unknown");
        when(sectorMappingRepository.findByIndustryName(any())).thenReturn(mapping);
        when(industryAvgGloRepository.findByIndustryName(any())).thenReturn(new IndustryAveragesGlobal());

        CompanyDataDTO result = companyDataAssemblyService.assembleCompanyData(ticker);

        assertEquals(165.0, result.getFinancialDataDTO().getStockPrice());
        assertEquals("USD", result.getBasicInfoDataDTO().getStockCurrency());
    }

    @Test
    void testAssembleCompanyData_CurrencyConversionFailureStopsValuation() {
        String ticker = "SOME_TICKER";

        Map<String, Object> basicInfoMap = new HashMap<>();
        basicInfoMap.put("currency", "EUR");
        basicInfoMap.put("financialCurrency", "USD");
        when(dataProvider.getCompanyInfo(ticker)).thenReturn(basicInfoMap);

        BasicInfoDataDTO basicInfoDataDTO = new BasicInfoDataDTO();
        basicInfoDataDTO.setCountryOfIncorporation("France");
        basicInfoDataDTO.setCurrency("EUR");
        when(companyDataMapper.mapBasicInfo(ticker, basicInfoMap)).thenReturn(basicInfoDataDTO);

        FinancialDataDTO financialDataDTO = new FinancialDataDTO();
        financialDataDTO.setStockPrice(150.0);
        CompanyFinancialIngestionService.FinancialIngestionData ingestionData =
                new CompanyFinancialIngestionService.FinancialIngestionData(
                        financialDataDTO, new ArrayList<>(), new ArrayList<>(), null, null);
        when(companyFinancialIngestionService.ingest(ticker, basicInfoMap)).thenReturn(ingestionData);

        when(currencyRateService.convertCurrency("EUR", "USD", 150.0))
                .thenThrow(new IllegalArgumentException("Currency not found: EUR or USD"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> companyDataAssemblyService.assembleCompanyData(ticker));

        assertTrue(error.getMessage().contains("Cannot safely value SOME_TICKER"));
    }

    @Test
    void testAssembleCompanyData_Global_Company() throws Exception {
        String ticker = "GLOBAL_CO";

        Map<String, Object> basicInfoMap = new HashMap<>();
        basicInfoMap.put("currency", "GBP");
        basicInfoMap.put("financialCurrency", "GBP");
        when(dataProvider.getCompanyInfo(ticker)).thenReturn(basicInfoMap);

        BasicInfoDataDTO basicInfoDataDTO = new BasicInfoDataDTO();
        basicInfoDataDTO.setCountryOfIncorporation("United Kingdom");
        basicInfoDataDTO.setCurrency("GBP");
        basicInfoDataDTO.setIndustryGlobal("Manufacturing");
        basicInfoDataDTO.setTimeZoneFullName("Europe/London");
        basicInfoDataDTO.setMarketCap(50000000L); // added to prevent NPE
        basicInfoDataDTO.setFirstTradeDateEpochUtc(1500000000); // added to prevent NPE
        when(companyDataMapper.mapBasicInfo(ticker, basicInfoMap)).thenReturn(basicInfoDataDTO);

        FinancialDataDTO financialDataDTO = new FinancialDataDTO();
        financialDataDTO.setRevenueTTM(100.0); // avoid zero division
        financialDataDTO.setRevenueLTM(90.0);
        financialDataDTO.setOperatingIncomeTTM(10.0); // avoid zero division
        financialDataDTO.setOperatingIncomeLTM(9.0);

        CompanyFinancialIngestionService.FinancialIngestionData ingestionData = new CompanyFinancialIngestionService.FinancialIngestionData(
                financialDataDTO, new ArrayList<>(), new ArrayList<>(), null, null);
        when(companyFinancialIngestionService.ingest(ticker, basicInfoMap)).thenReturn(ingestionData);

        when(countryEquityRepository.findCorporateTaxRateByCountry("United Kingdom")).thenReturn(Optional.of(19.0));
        when(dataProvider.getRevenueEstimate(ticker, "yearly")).thenReturn(new HashMap<>());

        SectorMapping sectorMapping = new SectorMapping();
        sectorMapping.setIndustryAsPerExcel("Manufacturing");
        when(sectorMappingRepository.findByIndustryName("Manufacturing")).thenReturn(sectorMapping);

        IndustryAveragesGlobal avgGlo = new IndustryAveragesGlobal();
        avgGlo.setPreTaxOperatingMargin(20.0);
        avgGlo.setAnnualAverageRevenueGrowth(5.0);
        when(industryAvgGloRepository.findByIndustryName("Manufacturing")).thenReturn(avgGlo);
        when(industryAvgGloRepository.findSalesToCapitalByIndustryName("Manufacturing")).thenReturn(Optional.of(1.2));

        CostOfCapital costOfCapital = new CostOfCapital();
        when(costOfCapitalRepository.findCostOfCapitalByRegion("Europe")).thenReturn(Optional.of(costOfCapital));

        CompanyDataDTO result = companyDataAssemblyService.assembleCompanyData(ticker);

        assertNotNull(result);
        assertEquals(19.0, result.getFinancialDataDTO().getMarginalTaxRate(), 0.01);
        assertEquals(1.2, result.getCompanyDriveDataDTO().getSalesToCapitalYears1To5(), 0.01);
        assertEquals(1.2, result.getCompanyDriveDataDTO().getSalesToCapitalYears6To10(), 0.01);
    }
}
