package io.stockvaluation.service;

import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.DividendDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModelSelectionService Tests")
class ModelSelectionServiceTest {
    
    private ModelSelectionService modelSelectionService;
    
    @BeforeEach
    void setUp() {
        modelSelectionService = new ModelSelectionService();
    }
    
    @Nested
    @DisplayName("Primary Model Selection")
    class PrimaryModelSelectionTests {
        
        @Test
        @DisplayName("Should select FCFF for null company data")
        void testNullCompanyData() {
            CashflowType result = modelSelectionService.selectPrimaryModel(null);
            assertEquals(CashflowType.FCFF, result);
        }
        
        @Test
        @DisplayName("Should select FCFF for company without dividend data")
        void testNoDividendData() {
            CompanyDataDTO company = new CompanyDataDTO();
            company.setDividendDataDTO(null);
            
            CashflowType result = modelSelectionService.selectPrimaryModel(company);
            assertEquals(CashflowType.FCFF, result);
        }
        
        @Test
        @DisplayName("Should select DDM for high payout ratio company")
        void testHighPayoutSelectsDDM() {
            CompanyDataDTO company = createCompanyWithDividends(0.50, 0.02, 2.0);
            
            CashflowType result = modelSelectionService.selectPrimaryModel(company);
            assertEquals(CashflowType.DIVIDENDS, result);
        }
        
        @Test
        @DisplayName("Should select DDM for high dividend yield company")
        void testHighYieldSelectsDDM() {
            CompanyDataDTO company = createCompanyWithDividends(0.30, 0.025, 3.0);
            
            CashflowType result = modelSelectionService.selectPrimaryModel(company);
            assertEquals(CashflowType.DIVIDENDS, result);
        }
        
        @Test
        @DisplayName("Should select FCFE for highly leveraged company")
        void testHighLeverageSelectsFCFE() {
            CompanyDataDTO company = createHighLeverageCompany();
            
            CashflowType result = modelSelectionService.selectPrimaryModel(company);
            assertEquals(CashflowType.FCFE, result);
        }
        
        @Test
        @DisplayName("Should select FCFF for growth company without dividends")
        void testGrowthCompanySelectsFCFF() {
            CompanyDataDTO company = createGrowthCompany();
            
            CashflowType result = modelSelectionService.selectPrimaryModel(company);
            assertEquals(CashflowType.FCFF, result);
        }
    }
    
    @Nested
    @DisplayName("DDM Eligibility")
    class DDMEligibilityTests {
        
        @Test
        @DisplayName("Should be eligible with 45% payout ratio")
        void testEligibleHighPayout() {
            CompanyDataDTO company = createCompanyWithDividends(0.45, 0.015, 2.0);
            
            assertTrue(modelSelectionService.isDDMEligible(company));
        }
        
        @Test
        @DisplayName("Should not be eligible with 30% payout and low yield")
        void testNotEligibleLowPayoutLowYield() {
            CompanyDataDTO company = createCompanyWithDividends(0.30, 0.005, 1.0);
            
            assertFalse(modelSelectionService.isDDMEligible(company));
        }
        
        @Test
        @DisplayName("Should be eligible with moderate payout but high yield")
        void testEligibleModeratePayoutHighYield() {
            CompanyDataDTO company = createCompanyWithDividends(0.25, 0.03, 2.5);
            
            assertTrue(modelSelectionService.isDDMEligible(company));
        }
    }
    
    @Nested
    @DisplayName("Option Pricing Consideration")
    class OptionPricingTests {
        
        @Test
        @DisplayName("Should consider option pricing for distressed company")
        void testDistressedCompany() {
            CompanyDataDTO company = createDistressedCompany();
            
            assertTrue(modelSelectionService.shouldConsiderOptionPricing(company));
        }
        
        @Test
        @DisplayName("Should not consider option pricing for profitable company")
        void testProfitableCompany() {
            CompanyDataDTO company = createProfitableCompany();
            
            assertFalse(modelSelectionService.shouldConsiderOptionPricing(company));
        }
        
        @Test
        @DisplayName("Model type should always be DCF for now")
        void testModelTypeAlwaysDCF() {
            CompanyDataDTO distressed = createDistressedCompany();
            CompanyDataDTO profitable = createProfitableCompany();
            
            // Even for distressed companies, we use DCF (option model not yet implemented)
            assertEquals(ModelType.DISCOUNTED_CF, modelSelectionService.selectModelType(distressed));
            assertEquals(ModelType.DISCOUNTED_CF, modelSelectionService.selectModelType(profitable));
        }
    }
    
    @Nested
    @DisplayName("Growth Pattern Selection")
    class GrowthPatternTests {
        
        @Test
        @DisplayName("Should select STABLE for low growth")
        void testLowGrowthStable() {
            GrowthPattern result = modelSelectionService.selectGrowthPattern(null, 0.04);
            assertEquals(GrowthPattern.STABLE, result);
        }
        
        @Test
        @DisplayName("Should select TWO_STAGE for moderate growth")
        void testModerateGrowthTwoStage() {
            GrowthPattern result = modelSelectionService.selectGrowthPattern(null, 0.08);
            assertEquals(GrowthPattern.TWO_STAGE, result);
        }
        
        @Test
        @DisplayName("Should select THREE_STAGE for high growth")
        void testHighGrowthThreeStage() {
            GrowthPattern result = modelSelectionService.selectGrowthPattern(null, 0.15);
            assertEquals(GrowthPattern.THREE_STAGE, result);
        }
    }
    
    @Nested
    @DisplayName("Earnings Level Selection")
    class EarningsLevelTests {
        
        @Test
        @DisplayName("Should use CURRENT for positive earnings")
        void testPositiveEarningsCurrent() {
            CompanyDataDTO company = createProfitableCompany();
            
            EarningsLevel result = modelSelectionService.selectEarningsLevel(company);
            assertEquals(EarningsLevel.CURRENT, result);
        }
        
        @Test
        @DisplayName("Should consider NORMALIZED for slightly negative earnings")
        void testSlightlyNegativeNormalized() {
            CompanyDataDTO company = createSlightlyLossCompany();
            
            EarningsLevel result = modelSelectionService.selectEarningsLevel(company);
            assertEquals(EarningsLevel.NORMALIZED, result);
        }
        
        @Test
        @DisplayName("Should use CURRENT for deeply negative (structural)")
        void testDeeplyNegativeCurrent() {
            CompanyDataDTO company = createDistressedCompany();
            
            EarningsLevel result = modelSelectionService.selectEarningsLevel(company);
            assertEquals(EarningsLevel.CURRENT, result);
        }
    }
    
    @Nested
    @DisplayName("Selection Rationale")
    class RationaleTests {
        
        @Test
        @DisplayName("Should provide DDM rationale")
        void testDDMRationale() {
            CompanyDataDTO company = createCompanyWithDividends(0.50, 0.03, 3.0);
            
            String rationale = modelSelectionService.getSelectionRationale(company, CashflowType.DIVIDENDS);
            
            assertTrue(rationale.contains("DDM"));
            assertTrue(rationale.contains("Payout"));
        }
        
        @Test
        @DisplayName("Should provide FCFE rationale")
        void testFCFERationale() {
            CompanyDataDTO company = createHighLeverageCompany();
            
            String rationale = modelSelectionService.getSelectionRationale(company, CashflowType.FCFE);
            
            assertTrue(rationale.contains("FCFE"));
            assertTrue(rationale.contains("leverage"));
        }
        
        @Test
        @DisplayName("Should provide FCFF rationale")
        void testFCFFRationale() {
            CompanyDataDTO company = createGrowthCompany();
            
            String rationale = modelSelectionService.getSelectionRationale(company, CashflowType.FCFF);
            
            assertTrue(rationale.contains("FCFF"));
        }
    }
    
    @Nested
    @DisplayName("Real Company Scenarios")
    class RealScenarios {
        
        @Test
        @DisplayName("Utility company should use DDM")
        void testUtilityCompany() {
            // Duke Energy like: high payout, stable dividends
            CompanyDataDTO company = createCompanyWithDividends(0.75, 0.04, 4.0);
            
            assertEquals(CashflowType.DIVIDENDS, modelSelectionService.selectPrimaryModel(company));
            assertTrue(modelSelectionService.isDDMEligible(company));
        }
        
        @Test
        @DisplayName("Tech growth company should use FCFF")
        void testTechGrowthCompany() {
            // NVDA like: no/low dividends, high growth
            CompanyDataDTO company = createGrowthCompany();
            
            assertEquals(CashflowType.FCFF, modelSelectionService.selectPrimaryModel(company));
            assertFalse(modelSelectionService.isDDMEligible(company));
        }
        
        @Test
        @DisplayName("REIT should use DDM")
        void testREIT() {
            // REIT: required high payout
            CompanyDataDTO company = createCompanyWithDividends(0.90, 0.05, 5.0);
            
            assertEquals(CashflowType.DIVIDENDS, modelSelectionService.selectPrimaryModel(company));
        }
        
        @Test
        @DisplayName("Startup should use FCFF")
        void testStartup() {
            // Startup: no dividends, negative earnings
            CompanyDataDTO company = createDistressedCompany();
            // No dividend data
            company.setDividendDataDTO(null);
            
            assertEquals(CashflowType.FCFF, modelSelectionService.selectPrimaryModel(company));
        }
    }
    
    // Helper methods
    
    private CompanyDataDTO createCompanyWithDividends(double payoutRatio, double yield, double dividendRate) {
        CompanyDataDTO company = new CompanyDataDTO();
        
        DividendDataDTO dividendData = DividendDataDTO.builder()
            .dividendRate(dividendRate)
            .dividendYield(yield)
            .payoutRatio(payoutRatio)
            .build();
        company.setDividendDataDTO(dividendData);
        
        return company;
    }
    
    private CompanyDataDTO createHighLeverageCompany() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setBookValueDebtTTM(600000000.0); // 600M debt
        financialData.setBookValueEqualityTTM(400000000.0); // 400M equity
        // Debt/Capital = 60% > 50% threshold
        company.setFinancialDataDTO(financialData);
        
        // No meaningful dividends
        DividendDataDTO dividendData = DividendDataDTO.builder()
            .dividendRate(0.0)
            .dividendYield(0.0)
            .payoutRatio(0.0)
            .build();
        company.setDividendDataDTO(dividendData);
        
        return company;
    }
    
    private CompanyDataDTO createGrowthCompany() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setBookValueDebtTTM(100000000.0);
        financialData.setBookValueEqualityTTM(400000000.0);
        financialData.setOperatingIncomeTTM(50000000.0);
        financialData.setRevenueTTM(500000000.0);
        company.setFinancialDataDTO(financialData);
        
        // Low/no dividends
        DividendDataDTO dividendData = DividendDataDTO.builder()
            .dividendRate(0.5)
            .dividendYield(0.002)
            .payoutRatio(0.10)
            .build();
        company.setDividendDataDTO(dividendData);
        
        return company;
    }
    
    private CompanyDataDTO createDistressedCompany() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setOperatingIncomeTTM(-50000000.0); // -50M loss
        financialData.setRevenueTTM(200000000.0); // 200M revenue
        // Margin = -25% < -10% threshold
        company.setFinancialDataDTO(financialData);
        
        return company;
    }
    
    private CompanyDataDTO createProfitableCompany() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setOperatingIncomeTTM(100000000.0);
        financialData.setRevenueTTM(500000000.0);
        // Margin = 20%
        company.setFinancialDataDTO(financialData);
        
        return company;
    }
    
    private CompanyDataDTO createSlightlyLossCompany() {
        CompanyDataDTO company = new CompanyDataDTO();
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setOperatingIncomeTTM(-10000000.0); // -10M loss
        financialData.setRevenueTTM(200000000.0); // 200M revenue
        // Margin = -5% (between 0 and -10%)
        company.setFinancialDataDTO(financialData);
        
        return company;
    }
}

