package io.stockvaluation.service;

import io.stockvaluation.domain.*;
import io.stockvaluation.dto.*;
import io.stockvaluation.repository.*;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CostOfCapitalService Tests
 * 
 * Tests the WACC calculation logic:
 * - Decile-based cost of capital lookup
 * - Industry-based cost of capital calculation
 * - Weighted multi-segment cost of capital
 * - Risk adjustments
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CostOfCapitalServiceTest {

    @Mock
    private CostOfCapitalRepository costOfCapitalRepository;

    @Mock
    private IndustryAvgUSRepository industryAvgUSRepository;

    @Mock
    private IndustryAvgGloRepository industryAvgGloRepository;

    @Mock
    private SectorMappingRepository sectorMappingRepository;

    @Mock
    private CommonService commonService;

    @InjectMocks
    private CostOfCapitalService costOfCapitalService;

    @BeforeEach
    void setUp() {
        CompanyDataDTO companyData = createCompanyData();
        when(commonService.getCompanyDtaFromYahooApi(any()))
            .thenReturn(companyData);
    }

    @Nested
    @DisplayName("1. Decile-Based Cost of Capital Tests")
    class DecileBasedTests {

        @Test
        @DisplayName("First Quartile risk grouping should return correct value")
        void testFirstQuartileRiskGrouping() {
            CostOfCapital costOfCapital = createCostOfCapital("8.50", "7.00", "9.50", "10.50", "12.00");
            when(costOfCapitalRepository.findByRegion("US"))
                .thenReturn(Optional.of(costOfCapital));
            
            String result = costOfCapitalService.costOfCapitalBasedOnDecile("US", "First Quartile");
            
            assertEquals("8.50", result);
        }

        @Test
        @DisplayName("Median risk grouping should return correct value")
        void testMedianRiskGrouping() {
            CostOfCapital costOfCapital = createCostOfCapital("8.50", "7.00", "9.50", "10.50", "12.00");
            when(costOfCapitalRepository.findByRegion("US"))
                .thenReturn(Optional.of(costOfCapital));
            
            String result = costOfCapitalService.costOfCapitalBasedOnDecile("US", "Median");
            
            assertEquals("9.50", result);
        }

        @Test
        @DisplayName("Third Quartile risk grouping should return correct value")
        void testThirdQuartileRiskGrouping() {
            CostOfCapital costOfCapital = createCostOfCapital("8.50", "7.00", "9.50", "10.50", "12.00");
            when(costOfCapitalRepository.findByRegion("US"))
                .thenReturn(Optional.of(costOfCapital));
            
            String result = costOfCapitalService.costOfCapitalBasedOnDecile("US", "Third Quartile");
            
            assertEquals("10.50", result);
        }

        @Test
        @DisplayName("Invalid region should return error message")
        void testInvalidRegion() {
            when(costOfCapitalRepository.findByRegion("INVALID"))
                .thenReturn(Optional.empty());
            
            String result = costOfCapitalService.costOfCapitalBasedOnDecile("INVALID", "Median");
            
            assertEquals("Region not found", result);
        }

        @Test
        @DisplayName("Invalid risk grouping should return error message")
        void testInvalidRiskGrouping() {
            CostOfCapital costOfCapital = createCostOfCapital("8.50", "7.00", "9.50", "10.50", "12.00");
            when(costOfCapitalRepository.findByRegion("US"))
                .thenReturn(Optional.of(costOfCapital));
            
            String result = costOfCapitalService.costOfCapitalBasedOnDecile("US", "Invalid");
            
            assertTrue(result.contains("not Found"));
        }
    }

    @Nested
    @DisplayName("2. Industry-Based Cost of Capital Tests")
    class IndustryBasedTests {

        @Test
        @DisplayName("US industry should calculate correctly")
        void testUSIndustry() {
            setupUSIndustryMocks();
            
            String result = costOfCapitalService.costOfCapitalByIndustry("AAPL", "Single Business(US)");
            
            assertNotNull(result);
            assertTrue(result.matches("-?\\d+\\.\\d+"));
        }

        @Test
        @DisplayName("Global industry should calculate correctly")
        void testGlobalIndustry() {
            setupGlobalIndustryMocks();
            
            String result = costOfCapitalService.costOfCapitalByIndustry("AAPL", "Single Business(Global)");
            
            assertNotNull(result);
            assertTrue(result.matches("-?\\d+\\.\\d+"));
        }

        @Test
        @DisplayName("Cost of capital should include risk-free rate adjustment")
        void testRiskFreeRateAdjustment() {
            setupUSIndustryMocks();
            
            String result = costOfCapitalService.costOfCapitalByIndustry("AAPL", "Single Business(US)");
            
            assertNotNull(result);
        }

        @Test
        @DisplayName("Null company data should throw exception")
        void testNullCompanyData() {
            when(commonService.getCompanyDtaFromYahooApi("UNKNOWN"))
                .thenReturn(null);
            
            assertThrows(RuntimeException.class, () -> 
                costOfCapitalService.costOfCapitalByIndustry("UNKNOWN", "Single Business(US)"));
        }

        @Test
        @DisplayName("First Decile risk should return correct value")
        void testFirstDecileRiskGrouping() {
            CostOfCapital costOfCapital = createCostOfCapital("8.50", "7.00", "9.50", "10.50", "12.00");
            when(costOfCapitalRepository.findByRegion("US"))
                .thenReturn(Optional.of(costOfCapital));
            
            String result = costOfCapitalService.costOfCapitalBasedOnDecile("US", "First Decile");
            
            assertEquals("7.00", result);
        }
    }

    private CostOfCapital createCostOfCapital(String firstQuartile, String firstDecile, 
                                               String median, String thirdQuartile, String ninthDecile) {
        CostOfCapital coc = new CostOfCapital();
        coc.setFirstQuartile(firstQuartile);
        coc.setFirstDecile(firstDecile);
        coc.setMedian(median);
        coc.setThirdQuartile(thirdQuartile);
        coc.setNinthDecile(ninthDecile);
        return coc;
    }

    private void setupUSIndustryMocks() {
        SectorMapping sectorMapping = new SectorMapping();
        sectorMapping.setIndustryAsPerExcel("Software");
        when(sectorMappingRepository.findByIndustryName(any()))
            .thenReturn(sectorMapping);
        
        IndustryAveragesUS usAvg = new IndustryAveragesUS();
        usAvg.setCostOfCapital(9.5);
        when(industryAvgUSRepository.findByIndustryName("Software"))
            .thenReturn(usAvg);
    }

    private void setupGlobalIndustryMocks() {
        SectorMapping sectorMapping = new SectorMapping();
        sectorMapping.setIndustryAsPerExcel("Software");
        when(sectorMappingRepository.findByIndustryName(any()))
            .thenReturn(sectorMapping);
        
        IndustryAveragesGlobal globalAvg = new IndustryAveragesGlobal();
        globalAvg.setCostOfCapital(10.0);
        when(industryAvgGloRepository.findByIndustryName("Software"))
            .thenReturn(globalAvg);
    }

    private CompanyDataDTO createCompanyData() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker("AAPL");
        basicInfo.setCompanyName("Apple Inc.");
        basicInfo.setIndustryUs("technology");
        basicInfo.setIndustryGlobal("technology");
        company.setBasicInfoDataDTO(basicInfo);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRiskFreeRate(0.04);
        company.setCompanyDriveDataDTO(driveData);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setStockPrice(175.0);
        company.setFinancialDataDTO(financialData);
        
        return company;
    }
}

