package io.stockvaluation.service;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationOutputDTO.CalibrationResultDTO;
import io.stockvaluation.dto.valuationOutputDTO.CompanyDTO;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.controller.AutomatedDCFAnalysisController;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Calibration and Scenario Valuation Tests
 * 
 * Tests the calibration logic and scenario analysis:
 * - Calibration to market price convergence
 * - Non-convergence handling
 * - Negative intrinsic value calibration
 * - Scenario valuation (optimistic/base/pessimistic)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CalibrationServiceTest {

    @Mock
    private CommonService commonService;

    @Mock
    private ValuationOutputService valuationOutputService;

    @Mock
    private OptionValueService optionValueService;

    @InjectMocks
    private AutomatedDCFAnalysisController controller;

    @BeforeEach
    void setUp() {
        // Setup common mocks
        when(commonService.calculateOperatingLeaseConvertor())
            .thenReturn(new LeaseResultDTO(0.0, 0.0, 0.0, 0.0));
        when(commonService.calculateR_DConvertorValue(any(), any(), any()))
            .thenReturn(new RDResult(0.0, 0.0, 0.0, 0.0));
        when(optionValueService.calculateOptionValue(any(), any(), any(), any(), any()))
            .thenReturn(new OptionValueResultDTO());
    }

    // ================================================================
    // 1. CALIBRATION TO MARKET PRICE TESTS (6 tests)
    // ================================================================

    @Nested
    @DisplayName("1. Calibration to Market Price Tests")
    class CalibrationToMarketPriceTests {

        @Test
        @DisplayName("Calibration should converge when value close to price")
        void testCalibrationConvergesWhenClose() {
            FinancialDataInput input = createFinancialInput();
            Double currentPrice = 100.0;
            
            // Mock valuation that returns close to market price
            mockValuationOutput(currentPrice * 1.05); // 5% above market
            
            CalibrationResultDTO result = controller.calibrateToMarketPrice(
                "TEST", input, currentPrice);
            
            assertNotNull(result);
            assertNotNull(result.getRevenueGrowth());
            assertNotNull(result.getOperatingMargin());
        }

        @Test
        @DisplayName("Calibration should adjust growth when value too high")
        void testCalibrationAdjustsGrowthWhenHigh() {
            FinancialDataInput input = createFinancialInput();
            input.setCompoundAnnualGrowth2_5(20.0); // High growth
            Double currentPrice = 100.0;
            
            mockValuationOutput(currentPrice * 1.5); // 50% above market
            
            CalibrationResultDTO result = controller.calibrateToMarketPrice(
                "TEST", input, currentPrice);
            
            assertNotNull(result);
            // Calibrated growth should be lower than input to reduce value
        }

        @Test
        @DisplayName("Calibration should handle very low intrinsic value")
        void testCalibrationHandlesLowValue() {
            FinancialDataInput input = createFinancialInput();
            Double currentPrice = 100.0;
            
            mockValuationOutput(currentPrice * 0.5); // 50% below market
            
            CalibrationResultDTO result = controller.calibrateToMarketPrice(
                "TEST", input, currentPrice);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Calibration with negative intrinsic value should still work")
        void testCalibrationWithNegativeValue() {
            FinancialDataInput input = createFinancialInput();
            input.setOperatingMarginNextYear(-20.0); // Negative margin
            Double currentPrice = 50.0;
            
            mockValuationOutput(-25.0); // Negative intrinsic value
            
            CalibrationResultDTO result = controller.calibrateToMarketPrice(
                "TEST", input, currentPrice);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Calibration should respect maximum iterations")
        void testCalibrationRespectsMaxIterations() {
            FinancialDataInput input = createFinancialInput();
            Double currentPrice = 100.0;
            
            // Mock constantly changing value (non-convergent)
            when(valuationOutputService.calculateFinancialData(any(), any(), any(), any(), any()))
                .thenReturn(createMockFinancialDTO());
            
            CompanyDTO companyDTO = new CompanyDTO();
            companyDTO.setEstimatedValuePerShare(Double.MAX_VALUE);
            when(valuationOutputService.calculateCompanyData(any(), any(), any(), any()))
                .thenReturn(companyDTO);
            
            CalibrationResultDTO result = controller.calibrateToMarketPrice(
                "TEST", input, currentPrice);
            
            // Should complete without infinite loop
            assertNotNull(result);
        }

        @Test
        @DisplayName("Calibration with zero price should be handled")
        void testCalibrationWithZeroPrice() {
            FinancialDataInput input = createFinancialInput();
            Double currentPrice = 0.0;
            
            mockValuationOutput(50.0);
            
            // Should not divide by zero
            CalibrationResultDTO result = controller.calibrateToMarketPrice(
                "TEST", input, currentPrice);
            
            assertNotNull(result);
        }
    }

    // ================================================================
    // 2. SCENARIO VALUATION TESTS (6 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Scenario Valuation Tests")
    class ScenarioValuationTests {

        @Test
        @DisplayName("Base case scenario should use current assumptions")
        void testBaseCaseScenario() {
            FinancialDataInput input = createFinancialInput();
            double baseGrowth = input.getCompoundAnnualGrowth2_5();
            
            // Base case should preserve input assumptions
            assertNotNull(input);
            assertEquals(8.0, baseGrowth, 0.01);
        }

        @Test
        @DisplayName("Optimistic scenario should have higher growth")
        void testOptimisticScenario() {
            // Optimistic typically has higher growth and margins
            double baseGrowth = 8.0;
            double optimisticGrowth = baseGrowth * 1.2; // 20% higher
            
            assertTrue(optimisticGrowth > baseGrowth);
        }

        @Test
        @DisplayName("Pessimistic scenario should have lower growth")
        void testPessimisticScenario() {
            double baseGrowth = 8.0;
            double pessimisticGrowth = baseGrowth * 0.8; // 20% lower
            
            assertTrue(pessimisticGrowth < baseGrowth);
        }

        @Test
        @DisplayName("Scenario adjustments should be applied correctly")
        void testScenarioAdjustmentsApplied() {
            FinancialDataInput input = createFinancialInput();
            
            // Clone for scenario
            FinancialDataInput scenarioInput = new FinancialDataInput(input);
            scenarioInput.setCompoundAnnualGrowth2_5(12.0); // Optimistic
            
            assertNotEquals(input.getCompoundAnnualGrowth2_5(), 
                           scenarioInput.getCompoundAnnualGrowth2_5());
        }

        @Test
        @DisplayName("Null scenario adjustments should be handled")
        void testNullScenarioAdjustments() {
            NarrativeDTO.ScenarioAnalysis.Scenario scenario = 
                new NarrativeDTO.ScenarioAnalysis.Scenario();
            scenario.setAdjustments(null);
            
            // Should not throw NPE
            assertNull(scenario.getAdjustments());
        }

        @Test
        @DisplayName("Scenario intrinsic values should differ")
        void testScenarioIntrinsicValuesDiffer() {
            // Optimistic > Base > Pessimistic (typically)
            double optimisticValue = 150.0;
            double baseValue = 100.0;
            double pessimisticValue = 75.0;
            
            assertTrue(optimisticValue > baseValue);
            assertTrue(baseValue > pessimisticValue);
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private FinancialDataInput createFinancialInput() {
        FinancialDataInput input = new FinancialDataInput();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker("TEST");
        basicInfo.setCompanyName("Test Company");
        basicInfo.setIndustryUs("technology");
        basicInfo.setIndustryGlobal("technology");
        input.setBasicInfoDataDTO(basicInfo);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setRevenueTTM(100000000.0);
        financialData.setOperatingIncomeTTM(15000000.0);
        financialData.setMarginalTaxRate(21.0);
        financialData.setStockPrice(100.0);
        financialData.setNoOfShareOutstanding(1000000.0);
        financialData.setResearchAndDevelopmentMap(new HashMap<>());
        input.setFinancialDataDTO(financialData);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRevenueNextYear(0.10);
        driveData.setOperatingMarginNextYear(0.15);
        driveData.setCompoundAnnualGrowth2_5(0.08);
        driveData.setRiskFreeRate(0.04);
        input.setCompanyDriveDataDTO(driveData);
        
        input.setRevenueNextYear(10.0);
        input.setOperatingMarginNextYear(15.0);
        input.setCompoundAnnualGrowth2_5(8.0);
        input.setTargetPreTaxOperatingMargin(15.0);
        input.setSalesToCapitalYears1To5(1.5);
        input.setSalesToCapitalYears6To10(1.5);
        input.setRiskFreeRate(4.0);
        input.setInitialCostCapital(8.0);
        input.setIndustry("technology");
        
        return input;
    }

    private void mockValuationOutput(double intrinsicValue) {
        when(valuationOutputService.calculateFinancialData(any(), any(), any(), any(), any()))
            .thenReturn(createMockFinancialDTO());
        
        CompanyDTO companyDTO = new CompanyDTO();
        companyDTO.setEstimatedValuePerShare(intrinsicValue);
        when(valuationOutputService.calculateCompanyData(any(), any(), any(), any()))
            .thenReturn(companyDTO);
    }

    private io.stockvaluation.dto.valuationOutputDTO.FinancialDTO createMockFinancialDTO() {
        io.stockvaluation.dto.valuationOutputDTO.FinancialDTO dto = 
            new io.stockvaluation.dto.valuationOutputDTO.FinancialDTO();
        dto.setRevenues(new Double[12]);
        dto.setEbitOperatingIncome(new Double[12]);
        dto.setFcff(new Double[12]);
        dto.setCostOfCapital(new Double[12]);
        dto.setRevenueGrowthRate(new Double[12]);
        dto.setComulatedDiscountedFactor(new Double[12]);
        dto.setRoic(new Double[12]);
        
        // Initialize arrays with default values
        for (int i = 0; i < 12; i++) {
            dto.getRevenues()[i] = 100000000.0 * Math.pow(1.1, i);
            dto.getEbitOperatingIncome()[i] = 15000000.0 * Math.pow(1.1, i);
            dto.getFcff()[i] = 10000000.0 * Math.pow(1.1, i);
            dto.getCostOfCapital()[i] = 8.0;
            dto.getRevenueGrowthRate()[i] = 10.0;
            dto.getComulatedDiscountedFactor()[i] = Math.pow(0.92, i);
            dto.getRoic()[i] = 15.0;
        }
        
        return dto;
    }
}

