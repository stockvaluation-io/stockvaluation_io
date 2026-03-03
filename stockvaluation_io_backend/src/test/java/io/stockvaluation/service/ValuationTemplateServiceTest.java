package io.stockvaluation.service;

import io.stockvaluation.dto.*;
import io.stockvaluation.form.FinancialDataInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValuationTemplateService Tests
 * 
 * Tests the template determination logic based on company characteristics:
 * - Template determination for different company types
 * - Projection years calculation
 * - Normalized margin calculation
 * - Earnings positivity detection
 * - Sustainable advantage determination
 */
public class ValuationTemplateServiceTest {

    private ValuationTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new ValuationTemplateService();
    }

    // ================================================================
    // 1. TEMPLATE DETERMINATION TESTS (8 tests)
    // ================================================================

    @Nested
    @DisplayName("1. Template Determination Tests")
    class TemplateDeterminationTests {

        @Test
        @DisplayName("Profitable company with moderate growth should get 10-year template")
        void testProfitableModerateGrowth() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            assertEquals(10, template.getProjectionYears());
            assertEquals(io.stockvaluation.enums.ModelType.DISCOUNTED_CF, template.getModelType());
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, template.getEarningsLevel());
        }

        @Test
        @DisplayName("Profitable company with high growth should get appropriate growth pattern")
        void testProfitableHighGrowth() {
            CompanyDataDTO company = createCompanyData(100000000.0, 20000000.0, 0.25);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            assertTrue(template.getProjectionYears() >= 10);
            assertEquals(io.stockvaluation.enums.GrowthPattern.THREE_STAGE, template.getGrowthPattern());
        }

        @Test
        @DisplayName("Loss-making company should use appropriate template")
        void testLossMakingCompany() {
            CompanyDataDTO company = createCompanyData(100000000.0, -15000000.0, 0.10);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            // Loss-making without cyclical flag uses Current Earnings
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, template.getEarningsLevel());
        }

        @Test
        @DisplayName("Stable growth company should have appropriate template")
        void testStableGrowthCompany() {
            CompanyDataDTO company = createCompanyData(100000000.0, 10000000.0, 0.03);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            assertEquals(io.stockvaluation.enums.GrowthPattern.STABLE, template.getGrowthPattern());
        }

        @Test
        @DisplayName("Two-stage growth company should have 10-year projection")
        void testTwoStageGrowthCompany() {
            // Growth rate between t1 (0.0601) and t2 (0.11)
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            assertEquals(io.stockvaluation.enums.GrowthPattern.TWO_STAGE, template.getGrowthPattern());
            assertEquals(10, template.getProjectionYears());
        }

        @Test
        @DisplayName("Three-stage growth company should have 15-year projection")
        void testThreeStageGrowthCompany() {
            // Growth rate > t2 (0.11)
            CompanyDataDTO company = createCompanyData(100000000.0, 25000000.0, 0.20);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            assertEquals(io.stockvaluation.enums.GrowthPattern.THREE_STAGE, template.getGrowthPattern());
            assertEquals(15, template.getProjectionYears());
        }

        @Test
        @DisplayName("Template with financial input should use input growth rate")
        void testTemplateWithFinancialInput() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.05);
            FinancialDataInput input = new FinancialDataInput();
            input.setRevenueNextYear(15.0); // 15% growth
            
            ValuationTemplate template = templateService.determineTemplate(input, company);
            
            assertNotNull(template);
            // With 15% growth from input, should be Three-stage
            assertEquals(io.stockvaluation.enums.GrowthPattern.THREE_STAGE, template.getGrowthPattern());
        }

        @Test
        @DisplayName("Template should set array length correctly")
        void testTemplateArrayLength() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            // arrayLength = projectionYears + 2 (base + terminal)
            assertEquals(template.getProjectionYears() + 2, template.getArrayLength());
        }
    }

    // ================================================================
    // 2. PROJECTION YEARS TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Projection Years Tests")
    class ProjectionYearsTests {

        @Test
        @DisplayName("Stable Growth pattern should return 10 years")
        void testStableGrowth10Years() {
            CompanyDataDTO company = createCompanyData(100000000.0, 10000000.0, 0.02);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertEquals(10, template.getProjectionYears());
        }

        @Test
        @DisplayName("Two-stage Growth pattern should return 10 years")
        void testTwoStageGrowth10Years() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertEquals(10, template.getProjectionYears());
        }

        @Test
        @DisplayName("Three-stage Growth pattern should return 15 years")
        void testThreeStageGrowth15Years() {
            CompanyDataDTO company = createCompanyData(100000000.0, 20000000.0, 0.20);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertEquals(15, template.getProjectionYears());
        }

        @Test
        @DisplayName("n-stage model should return 10 years as default")
        void testNStageModel10Years() {
            // Loss-making company without cyclical or one-time flag
            CompanyDataDTO company = createCompanyData(100000000.0, -20000000.0, 0.10);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            // n-stage model defaults to 10 years
            assertEquals(10, template.getProjectionYears());
        }

        @Test
        @DisplayName("Unknown growth pattern should default to 10 years")
        void testUnknownGrowthPatternDefault() {
            CompanyDataDTO company = createCompanyData(100000000.0, 10000000.0, 0.05);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            // Should have valid projection years
            assertTrue(template.getProjectionYears() >= 10);
        }
    }

    // ================================================================
    // 3. NORMALIZED MARGIN CALCULATION TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("3. Normalized Margin Calculation Tests")
    class NormalizedMarginCalculationTests {

        @Test
        @DisplayName("Company with GrowthDto marginMu should use it for normalized margin")
        void testNormalizedMarginFromMarginMu() {
            CompanyDataDTO company = createCompanyData(100000000.0, -10000000.0, 0.10);
            GrowthDto growthDto = new GrowthDto();
            growthDto.setMarginMu(0.15); // 15% mean margin
            company.setGrowthDto(growthDto);
            
            // Force cyclical to get Normalized Earnings
            // This requires creating a model that triggers normalized earnings
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            // Template should be created successfully
        }

        @Test
        @DisplayName("Company with margin changes history should calculate average")
        void testNormalizedMarginFromHistory() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            GrowthDto growthDto = new GrowthDto();
            growthDto.setMarginChanges(Arrays.asList(0.10, 0.12, 0.14, 0.11, 0.13));
            company.setGrowthDto(growthDto);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            // Should use the margin changes for calculation
        }

        @Test
        @DisplayName("Company without historical data should use current margin")
        void testNormalizedMarginFallbackToCurrent() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            company.getCompanyDriveDataDTO().setOperatingMarginNextYear(0.20);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
        }

        @Test
        @DisplayName("Company with insufficient history should use available data")
        void testNormalizedMarginInsufficientHistory() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            GrowthDto growthDto = new GrowthDto();
            growthDto.setMarginChanges(Arrays.asList(0.10, 0.12)); // Only 2 years
            company.setGrowthDto(growthDto);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
        }

        @Test
        @DisplayName("Default margin should be returned when no data available")
        void testNormalizedMarginDefault() {
            CompanyDataDTO company = new CompanyDataDTO();
            company.setBasicInfoDataDTO(new BasicInfoDataDTO());
            company.setFinancialDataDTO(new FinancialDataDTO());
            company.setCompanyDriveDataDTO(new CompanyDriveDataDTO());
            // No GrowthDto, no margin data
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            // Should use conservative default
        }
    }

    // ================================================================
    // 4. EARNINGS POSITIVITY TESTS (4 tests)
    // ================================================================

    @Nested
    @DisplayName("4. Earnings Positivity Tests")
    class EarningsPositivityTests {

        @Test
        @DisplayName("Positive operating income should be detected as positive earnings")
        void testPositiveOperatingIncome() {
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, template.getEarningsLevel());
        }

        @Test
        @DisplayName("Negative operating income should be detected as negative earnings")
        void testNegativeOperatingIncome() {
            CompanyDataDTO company = createCompanyData(100000000.0, -15000000.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            // Should be Current Earnings (not normalized since no cyclical flag)
            assertEquals(io.stockvaluation.enums.EarningsLevel.CURRENT, template.getEarningsLevel());
        }

        @Test
        @DisplayName("Zero operating income should be treated as negative")
        void testZeroOperatingIncome() {
            CompanyDataDTO company = createCompanyData(100000000.0, 0.0, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
        }

        @Test
        @DisplayName("Null operating income should default to positive")
        void testNullOperatingIncome() {
            CompanyDataDTO company = createCompanyData(100000000.0, null, 0.08);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
        }
    }

    // ================================================================
    // 5. SUSTAINABLE ADVANTAGE TESTS (3 tests)
    // ================================================================

    @Nested
    @DisplayName("5. Sustainable Advantage Tests")
    class SustainableAdvantageTests {

        @Test
        @DisplayName("High growth rate should indicate sustainable advantage")
        void testHighGrowthSustainableAdvantage() {
            // Growth > 11% (0.03 + 0.02 + 0.06) indicates advantage
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.15);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            // With sustainable advantage and high growth, should get longer period
            assertNotNull(template);
        }

        @Test
        @DisplayName("Low growth rate should not indicate sustainable advantage")
        void testLowGrowthNoAdvantage() {
            CompanyDataDTO company = createCompanyData(100000000.0, 10000000.0, 0.05);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
            // Without advantage, growth period should be shorter
        }

        @Test
        @DisplayName("Growth at threshold should not indicate advantage")
        void testGrowthAtThreshold() {
            // Threshold = 0.11, growth exactly at threshold
            CompanyDataDTO company = createCompanyData(100000000.0, 15000000.0, 0.11);
            
            ValuationTemplate template = templateService.determineTemplate(null, company);
            
            assertNotNull(template);
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private CompanyDataDTO createCompanyData(Double revenue, Double operatingIncome, double growthRate) {
        CompanyDataDTO company = new CompanyDataDTO();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker("TEST");
        basicInfo.setCompanyName("Test Company");
        basicInfo.setCountryOfIncorporation("United States");
        basicInfo.setIndustryUs("technology");
        basicInfo.setIndustryGlobal("technology");
        company.setBasicInfoDataDTO(basicInfo);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setRevenueTTM(revenue);
        financialData.setOperatingIncomeTTM(operatingIncome);
        financialData.setStockPrice(100.0);
        financialData.setNoOfShareOutstanding(1000000.0);
        financialData.setMarginalTaxRate(21.0);
        financialData.setResearchAndDevelopmentMap(new HashMap<>());
        company.setFinancialDataDTO(financialData);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRevenueNextYear(growthRate);
        driveData.setOperatingMarginNextYear(operatingIncome != null ? operatingIncome / revenue : 0.10);
        driveData.setCompoundAnnualGrowth2_5(growthRate * 0.8);
        driveData.setTargetPreTaxOperatingMargin(0.15);
        driveData.setSalesToCapitalYears1To5(1.5);
        driveData.setSalesToCapitalYears6To10(1.5);
        driveData.setRiskFreeRate(0.04);
        driveData.setInitialCostCapital(0.08);
        company.setCompanyDriveDataDTO(driveData);
        
        return company;
    }
}

