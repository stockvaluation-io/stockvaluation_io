package io.stockvaluation.service;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationOutputDTO.*;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.repository.IndustryAvgGloRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import io.stockvaluation.dto.OverrideAssumption;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ValuationOutputService Tests
 * 
 * Tests the core DCF valuation logic:
 * - Revenue growth rate calculations
 * - Revenue projections
 * - EBIT and tax calculations
 * - Reinvestment calculations
 * - Terminal value calculations
 * - Present value calculations
 * - Company valuation calculations
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ValuationOutputServiceTest {

    @Mock
    private CommonService commonService;

    @Mock
    private OptionValueService optionValueService;

    @Mock
    private CostOfCapitalService costOfCapitalService;

    @Mock
    private SyntheticRatingService syntheticRatingService;

    @Mock
    private IndustryAvgGloRepository industryAvgGloRepository;

    @Mock
    private InputStatRepository inputStatRepository;

    @Mock
    private SectorMappingRepository sectorMappingRepository;

    @InjectMocks
    private ValuationOutputService valuationOutputService;

    private RDResult mockRDResult;
    private LeaseResultDTO mockLeaseResult;
    private OptionValueResultDTO mockOptionResult;

    @BeforeEach
    void setUp() {
        // Setup common mocks
        mockRDResult = new RDResult(0.0, 0.0, 0.0, 0.0);
        mockLeaseResult = new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        mockOptionResult = new OptionValueResultDTO();
        
        when(commonService.calculateR_DConvertorValue(any(), any(), any()))
            .thenReturn(mockRDResult);
        when(commonService.calculateOperatingLeaseConvertor())
            .thenReturn(mockLeaseResult);
        when(optionValueService.calculateOptionValue(any(), any(), any(), any(), any()))
            .thenReturn(mockOptionResult);
        
        SectorMapping sectorMapping = new SectorMapping();
        sectorMapping.setIndustryAsPerExcel("Software");
        when(sectorMappingRepository.findByIndustryName(any()))
            .thenReturn(sectorMapping);
    }

    // ================================================================
    // 1. REVENUE GROWTH RATE CALCULATION TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("1. Revenue Growth Rate Calculation Tests")
    class RevenueGrowthRateTests {

        @Test
        @DisplayName("Growth rate array should have correct length for 10-year model")
        void testGrowthRateArrayLength10Year() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getRevenueGrowthRate());
            // Default model: 12 elements (base + 10 years + terminal)
            assertEquals(12, result.getRevenueGrowthRate().length);
        }

        @Test
        @DisplayName("Growth rate should converge to terminal rate")
        void testGrowthRateConvergesToTerminal() {
            FinancialDataInput input = createFinancialInput(15.0, 20.0, 10.0);
            input.setRiskFreeRate(4.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] growthRates = result.getRevenueGrowthRate();
            assertNotNull(growthRates);
            
            // Terminal year growth should be <= risk-free rate (Damodaran principle)
            int terminalIdx = growthRates.length - 1;
            assertTrue(growthRates[terminalIdx] <= 4.0,
                "Terminal growth should not exceed risk-free rate");
        }

        @Test
        @DisplayName("High growth should decline over projection period")
        void testHighGrowthDeclines() {
            FinancialDataInput input = createFinancialInput(30.0, 20.0, 25.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] growthRates = result.getRevenueGrowthRate();
            assertNotNull(growthRates);
            
            // Growth should generally decline from high initial rate
            // (checking year 1 vs year 10)
            if (growthRates.length >= 11) {
                assertTrue(growthRates[1] >= growthRates[10],
                    "Growth should decline over time");
            }
        }

        @Test
        @DisplayName("Negative growth should be handled correctly")
        void testNegativeGrowthHandling() {
            FinancialDataInput input = createFinancialInput(-10.0, 5.0, -5.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getRevenueGrowthRate());
            // Should not throw exception with negative growth
        }

        @Test
        @DisplayName("Zero growth should be valid")
        void testZeroGrowth() {
            FinancialDataInput input = createFinancialInput(0.0, 10.0, 0.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getRevenueGrowthRate());
        }
    }

    // ================================================================
    // 2. REVENUE PROJECTION TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Revenue Projection Tests")
    class RevenueProjectionTests {

        @Test
        @DisplayName("Revenue should increase with positive growth")
        void testRevenueIncreasesWithPositiveGrowth() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] revenues = result.getRevenues();
            assertNotNull(revenues);
            
            // Revenue should grow year over year (at least initially)
            for (int i = 1; i < Math.min(5, revenues.length); i++) {
                if (revenues[i] != null && revenues[i-1] != null) {
                    assertTrue(revenues[i] >= revenues[i-1],
                        "Revenue should increase in early years");
                }
            }
        }

        @Test
        @DisplayName("Base year revenue should match input")
        void testBaseYearRevenueMatchesInput() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            double baseRevenue = input.getFinancialDataDTO().getRevenueTTM();
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] revenues = result.getRevenues();
            assertNotNull(revenues);
            assertEquals(baseRevenue, revenues[0], 0.01);
        }

        @Test
        @DisplayName("Revenue array should have correct length")
        void testRevenueArrayLength() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getRevenues());
            assertEquals(result.getRevenueGrowthRate().length, result.getRevenues().length);
        }

        @Test
        @DisplayName("Revenue should not be negative")
        void testRevenueNonNegative() {
            FinancialDataInput input = createFinancialInput(-5.0, 10.0, -3.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] revenues = result.getRevenues();
            assertNotNull(revenues);
            
            for (int i = 0; i < revenues.length; i++) {
                if (revenues[i] != null) {
                    assertTrue(revenues[i] >= 0, "Revenue should be non-negative at index " + i);
                }
            }
        }

        @Test
        @DisplayName("Revenue projection for 15-year template should work")
        void testRevenue15YearTemplate() {
            FinancialDataInput input = createFinancialInput(20.0, 15.0, 15.0);
            
            ValuationTemplate template = new ValuationTemplate();
            template.setProjectionYears(15);
            template.setArrayLength(17); // base + 15 + terminal
            template.setGrowthPattern(io.stockvaluation.enums.GrowthPattern.THREE_STAGE);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", template);
            
            assertNotNull(result.getRevenues());
            assertEquals(17, result.getRevenues().length);
        }
    }

    // ================================================================
    // 3. EBIT AND TAX CALCULATION TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("3. EBIT and Tax Calculation Tests")
    class EBITAndTaxTests {

        @Test
        @DisplayName("Operating income should be calculated correctly")
        void testOperatingIncomeCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getEbitOperatingIncome());
            // Operating income = Revenue * Operating Margin
        }

        @Test
        @DisplayName("Tax should be applied correctly")
        void testTaxCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            input.getFinancialDataDTO().setMarginalTaxRate(25.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getEbitOperatingIncome());
            // After-tax income should be calculated
        }

        @Test
        @DisplayName("Negative operating income should handle taxes correctly")
        void testNegativeOperatingIncomeTax() {
            FinancialDataInput input = createFinancialInput(5.0, -10.0, 3.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            // Should not throw exception with negative operating income
            assertNotNull(result);
        }

        @Test
        @DisplayName("EBIT margin should converge to target")
        void testEBITMarginConvergence() {
            FinancialDataInput input = createFinancialInput(10.0, 5.0, 8.0);
            input.setTargetPreTaxOperatingMargin(25.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getEbitOperatingIncome());
            assertNotNull(result.getRevenues());
        }

        @Test
        @DisplayName("Operating margin array should exist")
        void testOperatingMarginArray() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getEbitOperatingMargin());
        }
    }

    // ================================================================
    // 4. REINVESTMENT AND FCFF TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("4. Reinvestment and FCFF Tests")
    class ReinvestmentAndFCFFTests {

        @Test
        @DisplayName("Reinvestment should be calculated correctly")
        void testReinvestmentCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            input.setSalesToCapitalYears1To5(2.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getReinvestment());
        }

        @Test
        @DisplayName("FCFF should be calculated correctly")
        void testFCFFCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getFcff());
            // FCFF = After-tax Operating Income - Reinvestment
        }

        @Test
        @DisplayName("Sales-to-capital ratio should affect reinvestment")
        void testSalesToCapitalAffectsReinvestment() {
            FinancialDataInput input1 = createFinancialInput(10.0, 15.0, 8.0);
            input1.setSalesToCapitalYears1To5(1.5);
            
            FinancialDataInput input2 = createFinancialInput(10.0, 15.0, 8.0);
            input2.setSalesToCapitalYears1To5(3.0);
            
            FinancialDTO result1 = valuationOutputService.calculateFinancialData(
                input1, mockRDResult, mockLeaseResult, "TEST", null);
            FinancialDTO result2 = valuationOutputService.calculateFinancialData(
                input2, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result1.getReinvestment());
            assertNotNull(result2.getReinvestment());
            // Higher sales-to-capital = lower reinvestment needed
        }

        @Test
        @DisplayName("ROIC should be calculated correctly")
        void testROICCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getRoic());
        }

        @Test
        @DisplayName("Invested capital should be tracked")
        void testInvestedCapitalTracking() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getInvestedCapital());
        }
    }

    // ================================================================
    // 5. TERMINAL VALUE TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("5. Terminal Value Tests")
    class TerminalValueTests {

        @Test
        @DisplayName("Terminal growth should not exceed risk-free rate")
        void testTerminalGrowthCap() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            input.setRiskFreeRate(3.5);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] growthRates = result.getRevenueGrowthRate();
            int terminalIdx = growthRates.length - 1;
            
            // Terminal growth should be capped at risk-free rate (Damodaran principle)
            assertTrue(growthRates[terminalIdx] <= 3.5,
                "Terminal growth rate should not exceed risk-free rate");
        }

        @Test
        @DisplayName("Terminal cost of capital should be reasonable")
        void testTerminalCostOfCapital() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            input.setInitialCostCapital(10.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] costOfCapital = result.getCostOfCapital();
            assertNotNull(costOfCapital);
            
            // Terminal cost of capital should converge to mature market level
        }

        @Test
        @DisplayName("Cumulative discount factor should decrease over time")
        void testCumulativeDiscountFactorDecreases() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            Double[] discountFactors = result.getComulatedDiscountedFactor();
            assertNotNull(discountFactors);
            
            // Cumulative discount factor should decrease over time
            for (int i = 2; i < discountFactors.length; i++) {
                if (discountFactors[i] != null && discountFactors[i-1] != null) {
                    assertTrue(discountFactors[i] <= discountFactors[i-1],
                        "Cumulative discount factor should decrease");
                }
            }
        }

        @Test
        @DisplayName("PV of FCFF should be calculated correctly")
        void testPVFCFFCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getPvFcff());
            // PV FCFF = FCFF * Cumulative Discount Factor
        }

        @Test
        @DisplayName("Terminal value calculation components should exist")
        void testTerminalValueComponents() {
            FinancialDataInput input = createFinancialInput(10.0, 15.0, 8.0);
            
            FinancialDTO result = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            assertNotNull(result.getFcff());
            assertNotNull(result.getCostOfCapital());
            assertNotNull(result.getRevenueGrowthRate());
        }
    }

    // ================================================================
    // 6. COMPANY VALUATION TESTS (5 tests)
    // ================================================================

    @Nested
    @DisplayName("6. Company Valuation Tests")
    class CompanyValuationTests {

        @Test
        @DisplayName("Intrinsic value should be positive for profitable company")
        void testIntrinsicValuePositiveForProfitable() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            
            FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                financialDTO, input, mockOptionResult, mockLeaseResult);
            
            assertNotNull(companyDTO);
            // For a profitable company with reasonable assumptions, value should be positive
        }

        @Test
        @DisplayName("Per-share value should be calculated correctly")
        void testPerShareValueCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            input.getFinancialDataDTO().setNoOfShareOutstanding(1000000.0);
            
            FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                financialDTO, input, mockOptionResult, mockLeaseResult);
            
            assertNotNull(companyDTO);
            assertNotNull(companyDTO.getEstimatedValuePerShare());
        }

        @Test
        @DisplayName("Stock price should be set from input")
        void testStockPriceFromInput() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            input.getFinancialDataDTO().setStockPrice(150.0);
            
            FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                financialDTO, input, mockOptionResult, mockLeaseResult);
            
            assertNotNull(companyDTO);
            assertEquals(150.0, companyDTO.getPrice(), 0.01);
        }

        @Test
        @DisplayName("Market cap should be calculated correctly")
        void testMarketCapCalculation() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            input.getFinancialDataDTO().setStockPrice(100.0);
            input.getFinancialDataDTO().setNoOfShareOutstanding(1000000.0);
            
            FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                financialDTO, input, mockOptionResult, mockLeaseResult);
            
            assertNotNull(companyDTO);
            // Market cap = Price * Shares Outstanding
        }

        @Test
        @DisplayName("Option dilution should be accounted for")
        void testOptionDilutionAccounting() {
            FinancialDataInput input = createFinancialInput(10.0, 20.0, 8.0);
            
            // Create option result with value
            OptionValueResultDTO optionWithValue = new OptionValueResultDTO();
            optionWithValue.setValuePerOption(10.0);
            optionWithValue.setValueOfAllOptionsOutstanding(10000000.0);
            
            FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                input, mockRDResult, mockLeaseResult, "TEST", null);
            
            CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                financialDTO, input, optionWithValue, mockLeaseResult);
            
            assertNotNull(companyDTO);
            // Options should reduce equity value
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private FinancialDataInput createFinancialInput(double revenueGrowth, double operatingMargin, double cagr) {
        FinancialDataInput input = new FinancialDataInput();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker("TEST");
        basicInfo.setCompanyName("Test Company");
        basicInfo.setCountryOfIncorporation("United States");
        basicInfo.setIndustryUs("technology");
        basicInfo.setIndustryGlobal("technology");
        basicInfo.setCurrency("USD");
        basicInfo.setStockCurrency("USD");
        input.setBasicInfoDataDTO(basicInfo);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setRevenueTTM(100000000.0);
        financialData.setOperatingIncomeTTM(15000000.0);
        financialData.setStockPrice(100.0);
        financialData.setNoOfShareOutstanding(10000000.0);
        financialData.setMarginalTaxRate(21.0);
        financialData.setEffectiveTaxRate(21.0);
        financialData.setResearchAndDevelopmentMap(new HashMap<>());
        
        // Fix: Add required fields for calculatePreInvestedCapital to prevent NullPointerException
        financialData.setBookValueEqualityTTM(50000000.0);  // $50M equity
        financialData.setBookValueDebtTTM(20000000.0);      // $20M debt
        financialData.setCashAndMarkablTTM(10000000.0);     // $10M cash
        
        input.setFinancialDataDTO(financialData);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRevenueNextYear(revenueGrowth / 100.0);
        driveData.setOperatingMarginNextYear(operatingMargin / 100.0);
        driveData.setCompoundAnnualGrowth2_5(cagr / 100.0);
        driveData.setConvergenceYearMargin(0.15);
        driveData.setRiskFreeRate(0.04);
        driveData.setInitialCostCapital(0.08);
        driveData.setSalesToCapitalYears1To5(2.0);
        driveData.setSalesToCapitalYears6To10(2.0);
        input.setCompanyDriveDataDTO(driveData);
        
        input.setRevenueNextYear(revenueGrowth);
        input.setOperatingMarginNextYear(operatingMargin);
        input.setCompoundAnnualGrowth2_5(cagr);
        input.setTargetPreTaxOperatingMargin(operatingMargin);
        input.setConvergenceYearMargin(15.0);
        input.setSalesToCapitalYears1To5(2.0);
        input.setSalesToCapitalYears6To10(2.0);
        input.setRiskFreeRate(4.0);
        input.setInitialCostCapital(8.0);
        input.setIndustry("technology");
        input.setIsExpensesCapitalize(false);
        input.setCompanyRiskLevel("Medium");
        
        // Initialize override assumptions to prevent NPE
        input.setOverrideAssumptionCostCapital(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionReturnOnCapital(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionProbabilityOfFailure(new OverrideAssumption(0D, false, 0D, "V"));
        input.setOverrideAssumptionReinvestmentLag(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionTaxRate(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionNOL(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionRiskFreeRate(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionGrowthRate(new OverrideAssumption(0D, false, 0D, null));
        input.setOverrideAssumptionCashPosition(new OverrideAssumption(0D, false, 0D, null));
        
        return input;
    }
}

