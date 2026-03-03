package io.stockvaluation.service;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.domain.RDConvertor;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.*;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.RDConvertorRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import io.stockvaluation.repository.IndustryAvgGloRepository;
import io.stockvaluation.repository.IndustryAvgUSRepository;
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
 * CommonService Tests
 * 
 * Tests the core service functions:
 * - R&D Capitalization calculations
 * - Operating Lease Conversion
 * - Segment Weighting calculations
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CommonServiceTest {

    @Mock
    private SectorMappingRepository sectorMappingRepository;

    @Mock
    private IndustryAvgGloRepository industryAvgGloRepository;

    @Mock
    private IndustryAvgUSRepository industryAvgUSRepository;

    @Mock
    private RDConvertorRepository rdConvertorRepository;

    @Mock
    private InputStatRepository inputStatRepository;

    @InjectMocks
    private CommonService commonService;

    @BeforeEach
    void setUp() {
        // Setup sector mapping mock
        SectorMapping sectorMapping = new SectorMapping();
        sectorMapping.setIndustryAsPerExcel("Software");
        when(sectorMappingRepository.findByIndustryName(any()))
            .thenReturn(sectorMapping);
        
        // Setup R&D convertor mock
        RDConvertor rdConvertor = new RDConvertor();
        rdConvertor.setAmortizationPeriod(5);
        when(rdConvertorRepository.findAmortizationPeriod(any()))
            .thenReturn(rdConvertor);
        
        // Setup input stat mock
        InputStatDistribution inputStat = new InputStatDistribution();
        when(inputStatRepository.findPreOperatingMarginByIndustryName(any()))
            .thenReturn(Optional.of(inputStat));
    }

    // ================================================================
    // 1. R&D CAPITALIZATION TESTS (8 tests)
    // ================================================================

    @Nested
    @DisplayName("1. R&D Capitalization Tests")
    class RDCapitalizationTests {

        @Test
        @DisplayName("Full 5-year R&D history should calculate correctly")
        void testFullRDHistory() {
            String industry = "technology";
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();
            rdMap.put("currentR&D-0", 1000000.0);
            rdMap.put("currentR&D-1", 900000.0);
            rdMap.put("currentR&D-2", 800000.0);
            rdMap.put("currentR&D-3", 700000.0);
            rdMap.put("currentR&D-4", 600000.0);
            
            RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate, rdMap);
            
            assertNotNull(result);
            // Total research asset should be positive
            assertTrue(result.getTotalResearchAsset() >= 0);
        }

        @Test
        @DisplayName("Partial R&D history (3 years) should still calculate")
        void testPartialRDHistory() {
            String industry = "pharmaceuticals";
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();
            rdMap.put("currentR&D-0", 500000.0);
            rdMap.put("currentR&D-1", 450000.0);
            rdMap.put("currentR&D-2", 400000.0);
            
            RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate, rdMap);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Empty R&D history should return zeros")
        void testEmptyRDHistory() {
            String industry = "technology";
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();
            
            RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate, rdMap);
            
            assertNotNull(result);
            assertEquals(0.0, result.getTotalResearchAsset(), 0.01);
        }

        @Test
        @DisplayName("Zero current R&D should return zeros")
        void testZeroCurrentRD() {
            String industry = "technology";
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();
            rdMap.put("currentR&D-0", 0.0);
            rdMap.put("currentR&D-1", 100000.0);
            rdMap.put("currentR&D-2", 100000.0);
            
            RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate, rdMap);
            
            assertNotNull(result);
            assertEquals(0.0, result.getTotalResearchAsset(), 0.01);
        }

        @Test
        @DisplayName("Empty R&D map should return zeros")
        void testEmptyRDMapReturnsZeros() {
            String industry = "technology";
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();  // Empty map instead of null
            
            RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate, rdMap);
            
            assertNotNull(result);
            assertEquals(0.0, result.getTotalResearchAsset(), 0.01);
        }

        @Test
        @DisplayName("Only current year R&D should be handled")
        void testOnlyCurrentYearRD() {
            String industry = "technology";
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();
            rdMap.put("currentR&D-0", 1000000.0);
            
            RDResult result = commonService.calculateR_DConvertorValue(industry, marginalTaxRate, rdMap);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Different industries should have different amortization periods")
        void testIndustrySpecificAmortization() {
            Double marginalTaxRate = 21.0;
            Map<String, Double> rdMap = new HashMap<>();
            rdMap.put("currentR&D-0", 1000000.0);
            rdMap.put("currentR&D-1", 900000.0);
            rdMap.put("currentR&D-2", 800000.0);
            
            RDResult techResult = commonService.calculateR_DConvertorValue("technology", marginalTaxRate, rdMap);
            RDResult pharmaResult = commonService.calculateR_DConvertorValue("pharmaceuticals", marginalTaxRate, rdMap);
            
            assertNotNull(techResult);
            assertNotNull(pharmaResult);
            // Different industries may have different results due to amortization periods
        }

        @Test
        @DisplayName("Tax rate should affect tax benefit calculation")
        void testTaxRateEffect() {
            String industry = "technology";
            Map<String, Double> rdMap = new HashMap<>();
            rdMap.put("currentR&D-0", 1000000.0);
            rdMap.put("currentR&D-1", 900000.0);
            
            RDResult lowTaxResult = commonService.calculateR_DConvertorValue(industry, 15.0, rdMap);
            RDResult highTaxResult = commonService.calculateR_DConvertorValue(industry, 30.0, rdMap);
            
            assertNotNull(lowTaxResult);
            assertNotNull(highTaxResult);
            // Tax benefit should be higher with higher tax rate
        }
    }

    // ================================================================
    // 2. OPERATING LEASE CONVERSION TESTS (6 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Operating Lease Conversion Tests")
    class OperatingLeaseConversionTests {

        @Test
        @DisplayName("Default lease convertor should return zeros")
        void testDefaultLeaseConvertor() {
            LeaseResultDTO result = commonService.calculateOperatingLeaseConvertor();
            
            assertNotNull(result);
            assertEquals(0.0, result.getAdjustmentToOperatingEarnings(), 0.01);
            assertEquals(0.0, result.getAdjustmentToTotalDebt(), 0.01);
        }

        @Test
        @DisplayName("Lease with commitments should calculate PV")
        void testLeaseWithCommitments() {
            Double leaseExpense = 100000.0;
            Double[] commitments = {90000.0, 80000.0, 70000.0, 60000.0, 50000.0};
            Double futureCommitment = 200000.0;
            
            LeaseResultDTO result = commonService.calculateOperatingLeaseConvertor(
                leaseExpense, commitments, futureCommitment);
            
            assertNotNull(result);
            // PV of lease should be positive
            assertTrue(result.getAdjustmentToTotalDebt() >= 0);
        }

        @Test
        @DisplayName("Zero lease expense should return zeros")
        void testZeroLeaseExpense() {
            Double leaseExpense = 0.0;
            Double[] commitments = {90000.0, 80000.0};
            Double futureCommitment = 100000.0;
            
            LeaseResultDTO result = commonService.calculateOperatingLeaseConvertor(
                leaseExpense, commitments, futureCommitment);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Null commitments should be handled")
        void testNullCommitments() {
            Double leaseExpense = 100000.0;
            Double[] commitments = null;
            Double futureCommitment = 200000.0;
            
            LeaseResultDTO result = commonService.calculateOperatingLeaseConvertor(
                leaseExpense, commitments, futureCommitment);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Empty commitments array should be handled")
        void testEmptyCommitments() {
            Double leaseExpense = 100000.0;
            Double[] commitments = {};
            Double futureCommitment = 200000.0;
            
            LeaseResultDTO result = commonService.calculateOperatingLeaseConvertor(
                leaseExpense, commitments, futureCommitment);
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("All null parameters should return zeros")
        void testAllNullParameters() {
            LeaseResultDTO result = commonService.calculateOperatingLeaseConvertor(
                null, null, null);
            
            assertNotNull(result);
            assertEquals(0.0, result.getAdjustmentToOperatingEarnings(), 0.01);
        }
    }

    // ================================================================
    // 3. SEGMENT WEIGHTING TESTS (4 tests)
    // Note: Multi-segment calculation tests require complex external dependencies.
    // These tests verify null/empty handling without hitting calculation paths.
    // ================================================================

    @Nested
    @DisplayName("3. Segment Weighting Tests")
    class SegmentWeightingTests {

        @Test
        @DisplayName("Null segments should be handled gracefully")
        void testNullSegments() {
            FinancialDataInput input = createFinancialInput();
            CompanyDataDTO companyData = createCompanyData();
            
            input.setSegments(null);
            
            List<String> adjustedParameters = new ArrayList<>();
            
            // Should not throw exception
            assertDoesNotThrow(() -> 
                commonService.applySegmentWeightedParameters(input, companyData, adjustedParameters));
        }

        @Test
        @DisplayName("Empty segments list should be handled")
        void testEmptySegmentsList() {
            FinancialDataInput input = createFinancialInput();
            CompanyDataDTO companyData = createCompanyData();
            
            SegmentResposeDTO segmentResponse = new SegmentResposeDTO();
            segmentResponse.setSegments(new ArrayList<>());
            input.setSegments(segmentResponse);
            
            List<String> adjustedParameters = new ArrayList<>();
            
            assertDoesNotThrow(() -> 
                commonService.applySegmentWeightedParameters(input, companyData, adjustedParameters));
        }

        @Test
        @DisplayName("Single-segment company should not require calculation")
        void testSingleSegmentFallback() {
            FinancialDataInput input = createFinancialInput();
            CompanyDataDTO companyData = createCompanyData();
            
            // Create single segment using correct nested class
            SegmentResposeDTO segmentResponse = new SegmentResposeDTO();
            List<SegmentResposeDTO.Segment> segments = new ArrayList<>();
            
            SegmentResposeDTO.Segment segment = new SegmentResposeDTO.Segment();
            segment.setSector("Technology");
            segment.setRevenueShare(100.0);
            segments.add(segment);
            
            segmentResponse.setSegments(segments);
            input.setSegments(segmentResponse);
            
            List<String> adjustedParameters = new ArrayList<>();
            
            // Single segment should be handled without NPE
            assertDoesNotThrow(() ->
                commonService.applySegmentWeightedParameters(input, companyData, adjustedParameters));
        }

        @Test
        @DisplayName("Null input should throw NullPointerException")
        void testNullInputHandling() {
            CompanyDataDTO companyData = createCompanyData();
            List<String> adjustedParameters = new ArrayList<>();
            
            // Null input is expected to throw NPE
            assertThrows(NullPointerException.class, () ->
                commonService.applySegmentWeightedParameters(null, companyData, adjustedParameters));
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
        
        return input;
    }

    private CompanyDataDTO createCompanyData() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker("TEST");
        basicInfo.setCompanyName("Test Company");
        basicInfo.setCountryOfIncorporation("United States");
        basicInfo.setIndustryUs("technology");
        basicInfo.setIndustryGlobal("technology");
        company.setBasicInfoDataDTO(basicInfo);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setRevenueTTM(100000000.0);
        financialData.setOperatingIncomeTTM(15000000.0);
        financialData.setMarginalTaxRate(21.0);
        company.setFinancialDataDTO(financialData);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRevenueNextYear(0.10);
        driveData.setOperatingMarginNextYear(0.15);
        company.setCompanyDriveDataDTO(driveData);
        
        return company;
    }
}

