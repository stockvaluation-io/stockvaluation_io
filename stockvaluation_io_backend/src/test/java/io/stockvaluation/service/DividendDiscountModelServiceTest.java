package io.stockvaluation.service;

import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.DDMResultDTO;
import io.stockvaluation.dto.DividendDataDTO;
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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DividendDiscountModelService Tests")
class DividendDiscountModelServiceTest {

    @Mock
    private CostOfCapitalService costOfCapitalService;
    
    @Mock
    private CommonService commonService;
    
    @InjectMocks
    private DividendDiscountModelService ddmService;
    
    @Nested
    @DisplayName("Gordon Growth Model Tests")
    class GordonGrowthTests {
        
        @Test
        @DisplayName("Should calculate Gordon Growth DDM correctly")
        void testGordonGrowthBasicCalculation() {
            // Given: D0 = $2.00, g = 3%, r = 8%
            double currentDividend = 2.00;
            double growthRate = 0.03;
            double costOfEquity = 0.08;
            
            // When
            DDMResultDTO result = ddmService.calculateGordonGrowthDDM(currentDividend, growthRate, costOfEquity);
            
            // Then: P = D0 * (1 + g) / (r - g) = 2 * 1.03 / 0.05 = $41.20
            assertNotNull(result);
            assertEquals("Gordon Growth", result.getModelUsed());
            assertTrue(result.getApplicable());
            assertEquals(41.20, result.getIntrinsicValue(), 0.01);
            assertEquals(currentDividend, result.getCurrentDividend());
            assertEquals(growthRate, result.getDividendGrowthRate());
            assertEquals(costOfEquity, result.getCostOfEquity());
        }
        
        @Test
        @DisplayName("Should cap growth rate when g >= r")
        void testGordonGrowthCapsGrowthRate() {
            // Given: g = 10% >= r = 8%
            double currentDividend = 2.00;
            double growthRate = 0.10; // Higher than cost of equity
            double costOfEquity = 0.08;
            
            // When
            DDMResultDTO result = ddmService.calculateGordonGrowthDDM(currentDividend, growthRate, costOfEquity);
            
            // Then: Growth should be capped to r - 1% = 7%
            assertNotNull(result);
            assertTrue(result.getApplicable());
            assertTrue(result.getDividendGrowthRate() < costOfEquity);
            assertTrue(result.getIntrinsicValue() > 0);
        }
        
        @Test
        @DisplayName("Should handle zero growth rate (preferred stock like)")
        void testGordonGrowthZeroGrowth() {
            // Given: g = 0%, D0 = $5.00, r = 10%
            double currentDividend = 5.00;
            double growthRate = 0.0;
            double costOfEquity = 0.10;
            
            // When
            DDMResultDTO result = ddmService.calculateGordonGrowthDDM(currentDividend, growthRate, costOfEquity);
            
            // Then: P = D1 / r = 5.00 / 0.10 = $50.00
            assertNotNull(result);
            assertEquals(50.00, result.getIntrinsicValue(), 0.01);
        }
        
        @Test
        @DisplayName("High confidence when spread is large")
        void testGordonGrowthHighConfidence() {
            // Given: g = 3%, r = 10% (spread = 7%)
            DDMResultDTO result = ddmService.calculateGordonGrowthDDM(2.00, 0.03, 0.10);
            
            assertEquals("HIGH", result.getConfidence());
        }
        
        @Test
        @DisplayName("Low confidence when spread is small")
        void testGordonGrowthLowConfidence() {
            // Given: g = 6%, r = 7% (spread = 1%)
            DDMResultDTO result = ddmService.calculateGordonGrowthDDM(2.00, 0.06, 0.07);
            
            assertEquals("LOW", result.getConfidence());
        }
    }
    
    @Nested
    @DisplayName("Two-Stage DDM Tests")
    class TwoStageTests {
        
        @Test
        @DisplayName("Should calculate Two-Stage DDM correctly")
        void testTwoStageBasicCalculation() {
            // Given: D0 = $1.00, g1 = 15%, n = 5, g2 = 3%, r = 10%
            double currentDividend = 1.00;
            double highGrowthRate = 0.15;
            int highGrowthYears = 5;
            double stableGrowthRate = 0.03;
            double costOfEquity = 0.10;
            
            // When
            DDMResultDTO result = ddmService.calculateTwoStageDDM(
                currentDividend, highGrowthRate, highGrowthYears, stableGrowthRate, costOfEquity);
            
            // Then
            assertNotNull(result);
            assertEquals("Two-Stage", result.getModelUsed());
            assertTrue(result.getApplicable());
            assertTrue(result.getIntrinsicValue() > 0);
            assertEquals(highGrowthYears, result.getHighGrowthYears());
            assertEquals(stableGrowthRate, result.getTerminalGrowthRate());
        }
        
        @Test
        @DisplayName("Should cap high growth rate at 15%")
        void testTwoStageCapsHighGrowth() {
            // Given: high growth = 25% (should be capped)
            DDMResultDTO result = ddmService.calculateTwoStageDDM(
                1.00, 0.25, 5, 0.03, 0.10);
            
            // Then
            assertNotNull(result);
            assertEquals(0.15, result.getHighGrowthRate(), 0.001); // Capped at 15%
        }
        
        @Test
        @DisplayName("Two-Stage should be higher than Gordon for high growth companies")
        void testTwoStageVsGordonForHighGrowth() {
            // Given: high growth company
            double currentDividend = 1.00;
            double costOfEquity = 0.10;
            
            // Calculate Gordon with average growth
            DDMResultDTO gordonResult = ddmService.calculateGordonGrowthDDM(
                currentDividend, 0.05, costOfEquity);
            
            // Calculate Two-Stage with high initial growth
            DDMResultDTO twoStageResult = ddmService.calculateTwoStageDDM(
                currentDividend, 0.12, 5, 0.03, costOfEquity);
            
            // Then: Two-stage should capture more value from high growth period
            assertTrue(twoStageResult.getIntrinsicValue() > gordonResult.getIntrinsicValue());
        }
    }
    
    @Nested
    @DisplayName("DDM Applicability Tests")
    class ApplicabilityTests {
        
        @Test
        @DisplayName("Should return not applicable for null dividend data")
        void testNotApplicableNullDividendData() {
            // Given
            CompanyDataDTO company = new CompanyDataDTO();
            company.setDividendDataDTO(null);
            
            // When
            DDMResultDTO result = ddmService.calculateDDM(company, 0.10);
            
            // Then
            assertNotNull(result);
            assertFalse(result.getApplicable());
            assertNotNull(result.getNotApplicableReason());
        }
        
        @Test
        @DisplayName("Should return not applicable for non-dividend payer")
        void testNotApplicableNonDividendPayer() {
            // Given
            CompanyDataDTO company = createNonDividendCompany();
            
            // When
            DDMResultDTO result = ddmService.calculateDDM(company, 0.10);
            
            // Then
            assertFalse(result.getApplicable());
            assertTrue(result.getNotApplicableReason().contains("not pay"));
        }
        
        @Test
        @DisplayName("Should calculate DDM for dividend payer")
        void testApplicableForDividendPayer() {
            // Given
            CompanyDataDTO company = createDividendPayingCompany(
                2.50,  // dividendRate
                0.025, // dividendYield 2.5%
                0.50,  // payoutRatio 50%
                0.05   // growth rate 5%
            );
            
            // When
            DDMResultDTO result = ddmService.calculateDDM(company, 0.10);
            
            // Then
            assertTrue(result.getApplicable());
            assertNotNull(result.getIntrinsicValue());
            assertTrue(result.getIntrinsicValue() > 0);
        }
        
        @Test
        @DisplayName("Should check DDM applicability correctly")
        void testIsDDMApplicable() {
            CompanyDataDTO dividendPayer = createDividendPayingCompany(2.0, 0.02, 0.45, 0.03);
            CompanyDataDTO nonPayer = createNonDividendCompany();
            
            assertTrue(ddmService.isDDMApplicable(dividendPayer));
            assertFalse(ddmService.isDDMApplicable(nonPayer));
            assertFalse(ddmService.isDDMApplicable(null));
        }
    }
    
    @Nested
    @DisplayName("Real Company Scenarios")
    class RealCompanyScenarios {
        
        @Test
        @DisplayName("Stable dividend payer like KO (Coca-Cola)")
        void testStableDividendPayer() {
            // KO-like: $1.76 dividend, 3% yield, 75% payout, 3% growth
            CompanyDataDTO company = createDividendPayingCompany(1.76, 0.03, 0.75, 0.03);
            setCompanyInfo(company, "KO", 1.0); // Beta = 1.0
            setRiskFreeRate(company, 0.04);
            
            DDMResultDTO result = ddmService.calculateDDM(company, "KO");
            
            assertTrue(result.getApplicable());
            assertEquals("Gordon Growth", result.getModelUsed());
            // Value should be reasonable (around 30-50 for KO-like)
            assertTrue(result.getIntrinsicValue() > 20);
            assertTrue(result.getIntrinsicValue() < 80);
        }
        
        @Test
        @DisplayName("High growth dividend payer like MSFT")
        void testHighGrowthDividendPayer() {
            // MSFT-like: $2.48 dividend, 0.8% yield, 25% payout, 10% growth
            CompanyDataDTO company = createDividendPayingCompany(2.48, 0.008, 0.25, 0.10);
            setCompanyInfo(company, "MSFT", 1.1);
            setRiskFreeRate(company, 0.04);
            
            DDMResultDTO result = ddmService.calculateDDM(company, "MSFT");
            
            assertTrue(result.getApplicable());
            // Should use Two-Stage for high growth
            assertEquals("Two-Stage", result.getModelUsed());
        }
        
        @Test
        @DisplayName("Utility company like NEE")
        void testUtilityCompany() {
            // NEE-like: $1.70 dividend, 2.5% yield, 55% payout, 4% growth
            CompanyDataDTO company = createDividendPayingCompany(1.70, 0.025, 0.55, 0.04);
            setCompanyInfo(company, "NEE", 0.7); // Low beta utility
            setRiskFreeRate(company, 0.04);
            
            DDMResultDTO result = ddmService.calculateDDM(company, "NEE");
            
            assertTrue(result.getApplicable());
            assertEquals("Gordon Growth", result.getModelUsed());
            // Utilities should have HIGH confidence due to stable dividends
        }
    }
    
    // Helper methods
    
    private CompanyDataDTO createNonDividendCompany() {
        CompanyDataDTO company = new CompanyDataDTO();
        DividendDataDTO dividendData = DividendDataDTO.builder()
            .dividendRate(0.0)
            .dividendYield(0.0)
            .payoutRatio(0.0)
            .build();
        company.setDividendDataDTO(dividendData);
        return company;
    }
    
    private CompanyDataDTO createDividendPayingCompany(
            double dividendRate, double dividendYield, double payoutRatio, double growthRate) {
        CompanyDataDTO company = new CompanyDataDTO();
        
        Map<String, Double> history = new HashMap<>();
        history.put("2023-01-15", dividendRate / 4);
        history.put("2023-04-15", dividendRate / 4);
        history.put("2023-07-15", dividendRate / 4);
        history.put("2023-10-15", dividendRate / 4);
        
        DividendDataDTO dividendData = DividendDataDTO.builder()
            .dividendRate(dividendRate)
            .dividendYield(dividendYield)
            .payoutRatio(payoutRatio)
            .trailingAnnualDividendRate(dividendRate)
            .dividendGrowthRate(growthRate)
            .dividendHistory(history)
            .build();
        company.setDividendDataDTO(dividendData);
        
        return company;
    }
    
    private void setCompanyInfo(CompanyDataDTO company, String ticker, double beta) {
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker(ticker);
        basicInfo.setBeta(beta);
        company.setBasicInfoDataDTO(basicInfo);
    }
    
    private void setRiskFreeRate(CompanyDataDTO company, double riskFreeRate) {
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRiskFreeRate(riskFreeRate);
        company.setCompanyDriveDataDTO(driveData);
    }
}

