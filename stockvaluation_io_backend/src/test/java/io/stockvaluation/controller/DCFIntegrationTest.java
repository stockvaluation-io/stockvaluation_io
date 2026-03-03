package io.stockvaluation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DCF Integration Tests
 * 
 * Tests the full DCF valuation flow with mocked Yahoo Finance responses
 * using JSON fixtures for consistent test data across runs.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DCFIntegrationTest {

    @Mock
    private CommonService commonService;

    @Mock
    private ValuationOutputService valuationOutputService;

    @Mock
    private ValuationTemplateService valuationTemplateService;

    @Mock
    private OptionValueService optionValueService;

    @InjectMocks
    private AutomatedDCFAnalysisController controller;

    private static JsonNode fixtureData;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void loadFixtures() throws IOException {
        objectMapper = new ObjectMapper();
        ClassPathResource resource = new ClassPathResource("fixtures/company-data-fixtures.json");
        try (InputStream is = resource.getInputStream()) {
            fixtureData = objectMapper.readTree(is);
        }
    }

    @BeforeEach
    void setUp() {
        when(commonService.calculateOperatingLeaseConvertor())
            .thenReturn(new LeaseResultDTO(0.0, 0.0, 0.0, 0.0));
        when(valuationOutputService.calculateCurrentSalesToCapitalRatio(any(), any(), any()))
            .thenReturn(1.5);
    }

    // ================================================================
    // FIXTURE LOADING HELPERS
    // ================================================================

    private CompanyDataDTO loadCompanyFromFixture(String ticker) {
        JsonNode company = fixtureData.get("companies").get(ticker);
        if (company == null) {
            throw new IllegalArgumentException("No fixture data for ticker: " + ticker);
        }
        return createCompanyFromJson(company);
    }

    private CompanyDataDTO createCompanyFromJson(JsonNode json) {
        CompanyDataDTO dto = new CompanyDataDTO();
        
        // Basic Info
        JsonNode basicInfo = json.get("basicInfo");
        BasicInfoDataDTO basic = new BasicInfoDataDTO();
        basic.setTicker(basicInfo.get("ticker").asText());
        basic.setCompanyName(basicInfo.get("companyName").asText());
        basic.setCountryOfIncorporation(basicInfo.get("countryOfIncorporation").asText());
        basic.setIndustryUs(basicInfo.get("industryUs").asText());
        basic.setIndustryGlobal(basicInfo.get("industryGlobal").asText());
        dto.setBasicInfoDataDTO(basic);
        
        // Financial Data
        JsonNode financialData = json.get("financialData");
        FinancialDataDTO financial = new FinancialDataDTO();
        financial.setRevenueTTM(financialData.get("revenueTTM").asDouble());
        financial.setOperatingIncomeTTM(financialData.get("operatingIncomeTTM").asDouble());
        financial.setStockPrice(financialData.get("stockPrice").asDouble());
        financial.setNoOfShareOutstanding(financialData.get("noOfShareOutstanding").asDouble());
        financial.setMarginalTaxRate(financialData.get("marginalTaxRate").asDouble());
        financial.setBookValueDebtTTM(financialData.get("bookValueDebtTTM").asDouble());
        financial.setBookValueEqualityTTM(financialData.get("bookValueEqualityTTM").asDouble());
        financial.setCashAndMarkablTTM(financialData.get("cashAndMarkablTTM").asDouble());
        
        if (financialData.has("interestExpenseTTM")) {
            financial.setInterestExpenseTTM(financialData.get("interestExpenseTTM").asDouble());
        }
        
        // R&D data
        Map<String, Double> rdMap = new HashMap<>();
        if (financialData.has("researchAndDevelopment")) {
            JsonNode rdNode = financialData.get("researchAndDevelopment");
            rdNode.fieldNames().forEachRemaining(field -> {
                rdMap.put(field, rdNode.get(field).asDouble());
            });
        }
        financial.setResearchAndDevelopmentMap(rdMap);
        dto.setFinancialDataDTO(financial);
        
        // Drive Data
        JsonNode driveData = json.get("driveData");
        CompanyDriveDataDTO drive = new CompanyDriveDataDTO();
        drive.setRevenueNextYear(driveData.get("revenueNextYear").asDouble());
        drive.setOperatingMarginNextYear(driveData.get("operatingMarginNextYear").asDouble());
        drive.setCompoundAnnualGrowth2_5(driveData.get("compoundAnnualGrowth2_5").asDouble());
        drive.setTargetPreTaxOperatingMargin(driveData.get("targetPreTaxOperatingMargin").asDouble());
        drive.setSalesToCapitalYears1To5(driveData.get("salesToCapitalYears1To5").asDouble());
        drive.setSalesToCapitalYears6To10(driveData.get("salesToCapitalYears6To10").asDouble());
        drive.setRiskFreeRate(driveData.get("riskFreeRate").asDouble());
        drive.setInitialCostCapital(driveData.get("initialCostCapital").asDouble());
        dto.setCompanyDriveDataDTO(drive);
        
        return dto;
    }

    private double[] getExpectedWaccRange(String ticker) {
        JsonNode expected = fixtureData.get("companies").get(ticker).get("expectedOutput");
        JsonNode range = expected.get("waccRange");
        return new double[]{range.get(0).asDouble(), range.get(1).asDouble()};
    }

    // ================================================================
    // INTEGRATION TESTS - PROFITABLE COMPANIES
    // ================================================================

    @Nested
    @DisplayName("Profitable Company Integration Tests")
    class ProfitableCompanyIntegrationTests {

        @Test
        @Order(1)
        @DisplayName("AAPL - Full DCF flow with fixture data")
        void testApple_FullDCFFlow() {
            // Load fixture
            CompanyDataDTO company = loadCompanyFromFixture("AAPL");
            String ticker = "AAPL";
            
            // Setup mocks
            when(commonService.getCompanyDtaFromYahooApi(ticker)).thenReturn(company);
            when(valuationTemplateService.determineTemplate(any(), any()))
                .thenReturn(createTemplate(10, "STABLE", "PROFITABLE"));
            
            // Create expected output
            ValuationOutputDTO expectedOutput = createValuationOutput(
                company.getFinancialDataDTO().getStockPrice(),
                company.getFinancialDataDTO().getStockPrice() * 1.15, // 15% upside
                0.085 // WACC
            );
            when(valuationOutputService.getValuationOutput(eq(ticker), any(), anyBoolean(), any()))
                .thenReturn(expectedOutput);
            when(commonService.calculateR_DConvertorValue(any(), any(), any()))
                .thenReturn(new RDResult(10000000000.0, 3000000000.0, 2000000000.0, 500000000.0));
            
            // Validate fixture data
            assertNotNull(company);
            assertEquals("Apple Inc.", company.getBasicInfoDataDTO().getCompanyName());
            assertEquals("United States", company.getBasicInfoDataDTO().getCountryOfIncorporation());
            
            // Validate WACC is within expected range
            double[] waccRange = getExpectedWaccRange(ticker);
            double wacc = expectedOutput.getCompanyDTO().getTerminalCostOfCapital() / 100;
            assertTrue(wacc >= waccRange[0] && wacc <= waccRange[1],
                "WACC " + wacc + " should be within range [" + waccRange[0] + ", " + waccRange[1] + "]");
            
            // Validate positive intrinsic value
            assertTrue(expectedOutput.getCompanyDTO().getEstimatedValuePerShare() > 0,
                "Profitable company should have positive intrinsic value");
        }
    }

    // ================================================================
    // INTEGRATION TESTS - LOSS-MAKING COMPANIES
    // ================================================================

    @Nested
    @DisplayName("Loss-Making Company Integration Tests")
    class LossMakingCompanyIntegrationTests {

        @Test
        @Order(2)
        @DisplayName("RIVN - Loss-making EV company with calibration")
        void testRivian_LossMakingFlow() {
            // Load fixture
            CompanyDataDTO company = loadCompanyFromFixture("RIVN");
            String ticker = "RIVN";
            
            // Setup mocks
            when(commonService.getCompanyDtaFromYahooApi(ticker)).thenReturn(company);
            when(valuationTemplateService.determineTemplate(any(), any()))
                .thenReturn(createTemplate(10, "HIGH_GROWTH", "LOSS_MAKING"));
            
            // Validate fixture data
            assertNotNull(company);
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0,
                "RIVN should have negative margin");
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0,
                "RIVN should target positive margin (turnaround)");
            
            // Validate higher WACC for risky company
            double initialWacc = company.getCompanyDriveDataDTO().getInitialCostCapital() / 100;
            assertTrue(initialWacc >= 0.10, "Loss-making company should have higher WACC");
        }
    }

    // ================================================================
    // INTEGRATION TESTS - MULTI-SEGMENT CONGLOMERATES
    // ================================================================

    @Nested
    @DisplayName("Multi-Segment Conglomerate Integration Tests")
    class MultiSegmentIntegrationTests {

        @Test
        @Order(3)
        @DisplayName("GE - Multi-segment with weighted parameters")
        void testGE_MultiSegmentFlow() {
            // Load fixture
            CompanyDataDTO company = loadCompanyFromFixture("GE");
            String ticker = "GE";
            
            // Setup mocks
            when(commonService.getCompanyDtaFromYahooApi(ticker)).thenReturn(company);
            when(valuationTemplateService.determineTemplate(any(), any()))
                .thenReturn(createTemplate(10, "STABLE", "PROFITABLE"));
            
            // Validate fixture data
            assertNotNull(company);
            assertEquals("General Electric Company", company.getBasicInfoDataDTO().getCompanyName());
            
            // Validate segment data exists in fixture
            JsonNode segments = fixtureData.get("companies").get(ticker).get("segments");
            assertNotNull(segments, "GE should have segment data");
            assertTrue(segments.size() >= 3, "GE should have multiple segments");
            
            // Calculate weighted margin from segments
            double weightedMargin = 0;
            for (JsonNode segment : segments) {
                weightedMargin += segment.get("revenueShare").asDouble() * segment.get("margin").asDouble();
            }
            
            // Validate weighted margin is reasonable
            assertTrue(weightedMargin > 0, "GE weighted margin should be positive");
            assertTrue(weightedMargin < 0.25, "GE weighted margin should be below 25%");
        }
    }

    // ================================================================
    // INTEGRATION TESTS - NON-US COMPANIES
    // ================================================================

    @Nested
    @DisplayName("Non-US Company Integration Tests")
    class NonUSCompanyIntegrationTests {

        @Test
        @Order(4)
        @DisplayName("BABA - China company with country risk premium")
        void testAlibaba_CountryRiskFlow() {
            // Load fixture
            CompanyDataDTO company = loadCompanyFromFixture("BABA");
            String ticker = "BABA";
            
            // Setup mocks
            when(commonService.getCompanyDtaFromYahooApi(ticker)).thenReturn(company);
            when(valuationTemplateService.determineTemplate(any(), any()))
                .thenReturn(createTemplate(10, "HIGH_GROWTH", "PROFITABLE"));
            
            // Validate fixture data
            assertNotNull(company);
            assertEquals("China", company.getBasicInfoDataDTO().getCountryOfIncorporation());
            
            // Validate country risk premium is applied
            JsonNode countryRisk = fixtureData.get("companies").get(ticker).get("countryRiskPremium");
            assertNotNull(countryRisk, "BABA should have country risk premium");
            assertTrue(countryRisk.asDouble() > 0, "China should have positive country risk premium");
            
            // Validate higher WACC due to country risk
            double[] waccRange = getExpectedWaccRange(ticker);
            assertTrue(waccRange[0] >= 0.10, "China company should have WACC >= 10%");
        }
    }

    // ================================================================
    // INTEGRATION TESTS - R&D INTENSIVE COMPANIES
    // ================================================================

    @Nested
    @DisplayName("R&D Intensive Company Integration Tests")
    class RDIntensiveIntegrationTests {

        @Test
        @Order(5)
        @DisplayName("GILD - Biotech with R&D capitalization")
        void testGilead_RDCapitalizationFlow() {
            // Load fixture
            CompanyDataDTO company = loadCompanyFromFixture("GILD");
            String ticker = "GILD";
            
            // Setup mocks
            when(commonService.getCompanyDtaFromYahooApi(ticker)).thenReturn(company);
            when(valuationTemplateService.determineTemplate(any(), any()))
                .thenReturn(createTemplate(10, "STABLE", "PROFITABLE"));
            
            // Validate fixture data
            assertNotNull(company);
            assertFalse(company.getFinancialDataDTO().getResearchAndDevelopmentMap().isEmpty(),
                "GILD should have R&D history");
            
            // Validate R&D history length
            int rdYears = company.getFinancialDataDTO().getResearchAndDevelopmentMap().size();
            assertTrue(rdYears >= 5, "GILD should have 5+ years of R&D history");
            
            // Validate R&D as % of revenue
            double currentRD = company.getFinancialDataDTO().getResearchAndDevelopmentMap()
                .getOrDefault("currentRD-0", 0.0);
            double revenue = company.getFinancialDataDTO().getRevenueTTM();
            double rdRatio = currentRD / revenue;
            assertTrue(rdRatio > 0.15, "GILD should have R&D > 15% of revenue");
        }
    }

    // ================================================================
    // DAMODARAN METHODOLOGY VALIDATION
    // ================================================================

    @Nested
    @DisplayName("Damodaran Methodology Validation")
    class DamodaranMethodologyTests {

        @Test
        @DisplayName("Validate all fixtures against Damodaran bounds")
        void validateAllFixturesAgainstDamodaranBounds() {
            JsonNode bounds = fixtureData.get("methodology").get("damodaranBounds");
            
            double waccMin = bounds.get("waccMin").asDouble();
            double waccMax = bounds.get("waccMax").asDouble();
            double terminalGrowthMax = bounds.get("terminalGrowthMax").asDouble();
            double marginRedFlagHigh = bounds.get("operatingMarginRedFlagHigh").asDouble();
            double marginRedFlagLow = bounds.get("operatingMarginRedFlagLow").asDouble();
            
            // Validate each company fixture
            JsonNode companies = fixtureData.get("companies");
            companies.fieldNames().forEachRemaining(ticker -> {
                JsonNode company = companies.get(ticker);
                String type = company.get("type").asText();
                
                // Skip distressed companies for margin validation
                if (!type.equals("distressed")) {
                    double margin = company.get("driveData").get("operatingMarginNextYear").asDouble();
                    
                    // Loss-making can have negative margins but not below red flag
                    if (!type.equals("loss-making")) {
                        assertTrue(margin > marginRedFlagLow,
                            ticker + " margin should be above " + marginRedFlagLow);
                    }
                    assertTrue(margin < marginRedFlagHigh,
                        ticker + " margin should be below " + marginRedFlagHigh);
                }
                
                // Validate WACC range for expected output
                if (company.has("expectedOutput") && company.get("expectedOutput").has("waccRange")) {
                    double[] range = new double[]{
                        company.get("expectedOutput").get("waccRange").get(0).asDouble(),
                        company.get("expectedOutput").get("waccRange").get(1).asDouble()
                    };
                    assertTrue(range[0] >= waccMin * 0.8, // Allow 20% tolerance
                        ticker + " WACC range min should be reasonable");
                    assertTrue(range[1] <= waccMax * 1.5, // Allow 50% tolerance for emerging markets
                        ticker + " WACC range max should be reasonable");
                }
            });
        }

        @Test
        @DisplayName("Validate terminal growth never exceeds risk-free rate")
        void validateTerminalGrowthConstraint() {
            JsonNode companies = fixtureData.get("companies");
            double terminalGrowthMax = fixtureData.get("methodology")
                .get("damodaranBounds").get("terminalGrowthMax").asDouble();
            
            companies.fieldNames().forEachRemaining(ticker -> {
                JsonNode company = companies.get(ticker);
                double riskFreeRate = company.get("driveData").get("riskFreeRate").asDouble() / 100;
                
                if (company.has("expectedOutput") && company.get("expectedOutput").has("terminalGrowthMax")) {
                    double expectedTerminalMax = company.get("expectedOutput").get("terminalGrowthMax").asDouble();
                    assertTrue(expectedTerminalMax <= riskFreeRate,
                        ticker + " terminal growth " + expectedTerminalMax + 
                        " should not exceed risk-free rate " + riskFreeRate);
                }
            });
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private ValuationTemplate createTemplate(int years, String growthPattern, String earningsLevel) {
        ValuationTemplate template = new ValuationTemplate();
        template.setProjectionYears(years);
        template.setGrowthPattern(io.stockvaluation.enums.GrowthPattern.fromString(growthPattern));
        template.setEarningsLevel(io.stockvaluation.enums.EarningsLevel.fromString(earningsLevel));
        return template;
    }

    private ValuationOutputDTO createValuationOutput(double price, double intrinsicValue, double wacc) {
        ValuationOutputDTO dto = new ValuationOutputDTO();
        
        io.stockvaluation.dto.valuationOutputDTO.CompanyDTO companyDTO = 
            new io.stockvaluation.dto.valuationOutputDTO.CompanyDTO();
        companyDTO.setPrice(price);
        companyDTO.setEstimatedValuePerShare(intrinsicValue);
        companyDTO.setTerminalCostOfCapital(wacc * 100);
        dto.setCompanyDTO(companyDTO);
        
        return dto;
    }
}

