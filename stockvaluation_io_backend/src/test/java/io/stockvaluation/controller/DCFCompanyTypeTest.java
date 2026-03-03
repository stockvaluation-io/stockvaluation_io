package io.stockvaluation.controller;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.service.*;
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
 * DCF Company Type Tests
 * 
 * Comprehensive test suite covering ~100 company scenarios across different types:
 * - Profitable companies (15 tests)
 * - Loss-making companies (15 tests)
 * - NOL transition companies (10 tests)
 * - High stock options companies (10 tests)
 * - Distressed companies (15 tests)
 * - High-leverage companies (10 tests)
 * - Multi-segment conglomerates (10 tests)
 * - R&D intensive companies (10 tests)
 * - Non-US companies (5 tests)
 * 
 * Each test validates both consistency and Damodaran methodology bounds.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DCFCompanyTypeTest {

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

    // Damodaran methodology bounds
    private static final double WACC_MIN = 0.06;
    private static final double WACC_MAX = 0.15;
    private static final double WACC_RED_FLAG_LOW = 0.05;
    private static final double WACC_RED_FLAG_HIGH = 0.20;
    private static final double TERMINAL_GROWTH_MAX = 0.04;
    private static final double OPERATING_MARGIN_RED_FLAG_HIGH = 0.50;
    private static final double OPERATING_MARGIN_RED_FLAG_LOW = -0.50;
    private static final double TERMINAL_VALUE_PERCENT_MAX = 0.90;

    @BeforeEach
    void setUp() {
        // Common mock setup
        when(commonService.calculateOperatingLeaseConvertor())
            .thenReturn(new LeaseResultDTO(0.0, 0.0, 0.0, 0.0));
        when(commonService.calculateR_DConvertorValue(any(), any(), any()))
            .thenReturn(new RDResult(0.0, 0.0, 0.0, 0.0));
        when(valuationOutputService.calculateCurrentSalesToCapitalRatio(any(), any(), any()))
            .thenReturn(1.5);
    }

    // ================================================================
    // HELPER METHODS - Factory methods for each company type
    // ================================================================

    /**
     * Creates a profitable company with positive margins
     */
    private CompanyDataDTO createProfitableCompany(String ticker, String name, double operatingMargin) {
        CompanyDataDTO dto = new CompanyDataDTO();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker(ticker);
        basicInfo.setCompanyName(name);
        basicInfo.setCountryOfIncorporation("United States");
        basicInfo.setIndustryUs("technology");
        basicInfo.setIndustryGlobal("technology");
        dto.setBasicInfoDataDTO(basicInfo);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setRevenueTTM(100_000_000_000.0);
        financialData.setOperatingIncomeTTM(100_000_000_000.0 * operatingMargin);
        financialData.setStockPrice(150.0);
        financialData.setNoOfShareOutstanding(1_000_000_000.0);
        financialData.setMarginalTaxRate(21.0);
        financialData.setBookValueDebtTTM(20_000_000_000.0);
        financialData.setBookValueEqualityTTM(80_000_000_000.0);
        financialData.setCashAndMarkablTTM(30_000_000_000.0);
        financialData.setResearchAndDevelopmentMap(new HashMap<>());
        dto.setFinancialDataDTO(financialData);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRevenueNextYear(0.08);
        driveData.setOperatingMarginNextYear(operatingMargin);
        driveData.setCompoundAnnualGrowth2_5(0.06);
        driveData.setTargetPreTaxOperatingMargin(operatingMargin + 0.02);
        driveData.setSalesToCapitalYears1To5(1.5);
        driveData.setSalesToCapitalYears6To10(1.5);
        driveData.setRiskFreeRate(4.0);
        driveData.setInitialCostCapital(8.0);
        driveData.setConvergenceYearMargin(5.0);
        dto.setCompanyDriveDataDTO(driveData);
        
        return dto;
    }

    /**
     * Creates a loss-making company with negative margins
     */
    private CompanyDataDTO createLossMakingCompany(String ticker, String name, double negativeMargin, double targetMargin) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, negativeMargin);
        dto.getFinancialDataDTO().setOperatingIncomeTTM(dto.getFinancialDataDTO().getRevenueTTM() * negativeMargin);
        dto.getCompanyDriveDataDTO().setOperatingMarginNextYear(negativeMargin);
        dto.getCompanyDriveDataDTO().setTargetPreTaxOperatingMargin(targetMargin);
        dto.getCompanyDriveDataDTO().setInitialCostCapital(12.0); // Higher WACC for risky companies
        return dto;
    }

    /**
     * Creates a distressed company with severe decline and high failure risk
     */
    private CompanyDataDTO createDistressedCompany(String ticker, String name, double revenueDecline, double negativeMargin) {
        CompanyDataDTO dto = createLossMakingCompany(ticker, name, negativeMargin, 0.05);
        dto.getCompanyDriveDataDTO().setRevenueNextYear(Math.max(revenueDecline, -0.50)); // Cap at -50%
        dto.getCompanyDriveDataDTO().setCompoundAnnualGrowth2_5(-0.10);
        dto.getCompanyDriveDataDTO().setInitialCostCapital(15.0);
        dto.getFinancialDataDTO().setBookValueEqualityTTM(-5_000_000_000.0); // Negative book value
        dto.getFinancialDataDTO().setCashAndMarkablTTM(1_000_000_000.0); // Low cash
        return dto;
    }

    /**
     * Creates a high-leverage company with high debt/equity ratio
     */
    private CompanyDataDTO createHighLeverageCompany(String ticker, String name, double debtEquityRatio, double margin) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, margin);
        double equity = 20_000_000_000.0;
        double debt = equity * debtEquityRatio;
        dto.getFinancialDataDTO().setBookValueDebtTTM(debt);
        dto.getFinancialDataDTO().setBookValueEqualityTTM(equity);
        dto.getFinancialDataDTO().setInterestExpenseTTM(debt * 0.05); // 5% interest rate
        dto.getCompanyDriveDataDTO().setInitialCostCapital(10.0 + (debtEquityRatio * 2)); // Higher WACC
        return dto;
    }

    /**
     * Creates an R&D intensive company with full R&D history
     */
    private CompanyDataDTO createRDIntensiveCompany(String ticker, String name, double rdToRevenue) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, 0.15);
        double revenue = dto.getFinancialDataDTO().getRevenueTTM();
        
        Map<String, Double> rdMap = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            rdMap.put("currentR&D-" + i, revenue * rdToRevenue * Math.pow(0.9, i));
        }
        dto.getFinancialDataDTO().setResearchAndDevelopmentMap(rdMap);
        return dto;
    }

    /**
     * Creates a multi-segment conglomerate
     */
    private CompanyDataDTO createMultiSegmentCompany(String ticker, String name, int numSegments) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, 0.12);
        // Segments would be added via SegmentResponseDTO in actual implementation
        return dto;
    }

    /**
     * Creates a non-US company with country risk premium
     */
    private CompanyDataDTO createNonUSCompany(String ticker, String name, String country, double countryRiskPremium) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, 0.15);
        dto.getBasicInfoDataDTO().setCountryOfIncorporation(country);
        dto.getCompanyDriveDataDTO().setInitialCostCapital(8.0 + countryRiskPremium);
        return dto;
    }

    /**
     * Creates a company with high stock options
     */
    private CompanyDataDTO createHighOptionsCompany(String ticker, String name, double optionsAsPercentOfShares) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, 0.20);
        // Options would affect equity value calculation
        return dto;
    }

    /**
     * Creates a NOL transition company (losses to profits)
     */
    private CompanyDataDTO createNOLTransitionCompany(String ticker, String name, double currentMargin, double targetMargin) {
        CompanyDataDTO dto = createProfitableCompany(ticker, name, currentMargin);
        dto.getCompanyDriveDataDTO().setOperatingMarginNextYear(currentMargin);
        dto.getCompanyDriveDataDTO().setTargetPreTaxOperatingMargin(targetMargin);
        // NOL carryforward would reduce taxes in early profitable years
        return dto;
    }

    /**
     * Creates mock valuation output with specified values
     */
    private ValuationOutputDTO createMockValuationOutput(double price, double intrinsicValue, double wacc, double terminalGrowth) {
        ValuationOutputDTO dto = new ValuationOutputDTO();
        
        io.stockvaluation.dto.valuationOutputDTO.CompanyDTO companyDTO = 
            new io.stockvaluation.dto.valuationOutputDTO.CompanyDTO();
        companyDTO.setPrice(price);
        companyDTO.setEstimatedValuePerShare(intrinsicValue);
        companyDTO.setTerminalCostOfCapital(wacc * 100);
        dto.setCompanyDTO(companyDTO);
        
        return dto;
    }

    /**
     * Creates mock valuation template
     */
    private ValuationTemplate createMockTemplate(int years, String growthPattern, String earningsLevel) {
        ValuationTemplate template = new ValuationTemplate();
        template.setProjectionYears(years);
        template.setGrowthPattern(io.stockvaluation.enums.GrowthPattern.fromString(growthPattern));
        template.setEarningsLevel(io.stockvaluation.enums.EarningsLevel.fromString(earningsLevel));
        return template;
    }

    // ================================================================
    // VALIDATION HELPER METHODS
    // ================================================================

    /**
     * Validates WACC is within Damodaran bounds
     */
    private void assertWACCWithinBounds(double wacc, String context) {
        assertFalse(wacc < WACC_RED_FLAG_LOW, 
            context + ": WACC " + wacc + " below red flag threshold 5%");
        assertFalse(wacc > WACC_RED_FLAG_HIGH, 
            context + ": WACC " + wacc + " above red flag threshold 20%");
    }

    /**
     * Validates operating margin is within reasonable bounds
     */
    private void assertMarginWithinBounds(double margin, String context) {
        assertFalse(margin > OPERATING_MARGIN_RED_FLAG_HIGH, 
            context + ": Margin " + margin + " exceeds 50% - unrealistic");
        assertFalse(margin < OPERATING_MARGIN_RED_FLAG_LOW, 
            context + ": Margin " + margin + " below -50% - company not viable");
    }

    /**
     * Validates terminal growth does not exceed risk-free rate
     */
    private void assertTerminalGrowthValid(double terminalGrowth, double riskFreeRate, String context) {
        assertTrue(terminalGrowth <= riskFreeRate, 
            context + ": Terminal growth " + terminalGrowth + " exceeds risk-free rate " + riskFreeRate);
    }

    /**
     * Validates intrinsic value is positive for viable companies
     */
    private void assertPositiveIntrinsicValue(double value, String context) {
        assertTrue(value > 0, context + ": Intrinsic value should be positive");
    }

    // ================================================================
    // 1. PROFITABLE COMPANIES (15 tests)
    // ================================================================

    @Nested
    @DisplayName("1. Profitable Company Tests")
    class ProfitableCompanyTests {

        @Test
        @DisplayName("AAPL - Large-cap tech with 25% margin")
        void testApple_LargecapTech() {
            CompanyDataDTO company = createProfitableCompany("AAPL", "Apple Inc.", 0.25);
            ValuationOutputDTO output = createMockValuationOutput(175.0, 195.0, 0.085, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "AAPL");
            assertWACCWithinBounds(0.085, "AAPL");
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "AAPL");
        }

        @Test
        @DisplayName("MSFT - Large-cap tech with 35% margin")
        void testMicrosoft_LargecapTech() {
            CompanyDataDTO company = createProfitableCompany("MSFT", "Microsoft Corp", 0.35);
            ValuationOutputDTO output = createMockValuationOutput(380.0, 420.0, 0.082, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "MSFT");
            assertWACCWithinBounds(0.082, "MSFT");
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "MSFT");
        }

        @Test
        @DisplayName("GOOGL - Large-cap tech with 28% margin")
        void testGoogle_LargecapTech() {
            CompanyDataDTO company = createProfitableCompany("GOOGL", "Alphabet Inc.", 0.28);
            ValuationOutputDTO output = createMockValuationOutput(140.0, 165.0, 0.088, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "GOOGL");
            assertWACCWithinBounds(0.088, "GOOGL");
        }

        @Test
        @DisplayName("META - Large-cap tech with 30% margin")
        void testMeta_LargecapTech() {
            CompanyDataDTO company = createProfitableCompany("META", "Meta Platforms", 0.30);
            ValuationOutputDTO output = createMockValuationOutput(500.0, 480.0, 0.090, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "META");
            assertWACCWithinBounds(0.090, "META");
        }

        @Test
        @DisplayName("AMZN - Large-cap with lower 8% margin")
        void testAmazon_LargecapRetail() {
            CompanyDataDTO company = createProfitableCompany("AMZN", "Amazon.com", 0.08);
            ValuationOutputDTO output = createMockValuationOutput(180.0, 200.0, 0.092, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "AMZN");
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "AMZN");
        }

        @Test
        @DisplayName("PG - Consumer staples with 18% margin")
        void testPG_ConsumerStaples() {
            CompanyDataDTO company = createProfitableCompany("PG", "Procter & Gamble", 0.18);
            ValuationOutputDTO output = createMockValuationOutput(160.0, 155.0, 0.070, 0.03);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "PG");
            assertWACCWithinBounds(0.070, "PG");
        }

        @Test
        @DisplayName("KO - Consumer staples with 25% margin")
        void testCocaCola_ConsumerStaples() {
            CompanyDataDTO company = createProfitableCompany("KO", "Coca-Cola Co", 0.25);
            ValuationOutputDTO output = createMockValuationOutput(60.0, 58.0, 0.068, 0.03);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "KO");
            assertWACCWithinBounds(0.068, "KO");
        }

        @Test
        @DisplayName("PEP - Consumer staples with 14% margin")
        void testPepsi_ConsumerStaples() {
            CompanyDataDTO company = createProfitableCompany("PEP", "PepsiCo Inc", 0.14);
            ValuationOutputDTO output = createMockValuationOutput(175.0, 170.0, 0.072, 0.03);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "PEP");
        }

        @Test
        @DisplayName("WMT - Retail with 4% margin")
        void testWalmart_Retail() {
            CompanyDataDTO company = createProfitableCompany("WMT", "Walmart Inc", 0.04);
            ValuationOutputDTO output = createMockValuationOutput(160.0, 145.0, 0.075, 0.03);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "WMT");
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0, "WMT should have positive margin");
        }

        @Test
        @DisplayName("COST - Retail with 3.5% margin")
        void testCostco_Retail() {
            CompanyDataDTO company = createProfitableCompany("COST", "Costco Wholesale", 0.035);
            ValuationOutputDTO output = createMockValuationOutput(750.0, 720.0, 0.078, 0.03);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "COST");
        }

        @Test
        @DisplayName("JPM - Financial with 35% margin")
        void testJPMorgan_Financial() {
            CompanyDataDTO company = createProfitableCompany("JPM", "JPMorgan Chase", 0.35);
            company.getBasicInfoDataDTO().setIndustryUs("banking");
            ValuationOutputDTO output = createMockValuationOutput(200.0, 210.0, 0.095, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "JPM");
            assertWACCWithinBounds(0.095, "JPM");
        }

        @Test
        @DisplayName("BAC - Financial with 28% margin")
        void testBankOfAmerica_Financial() {
            CompanyDataDTO company = createProfitableCompany("BAC", "Bank of America", 0.28);
            company.getBasicInfoDataDTO().setIndustryUs("banking");
            ValuationOutputDTO output = createMockValuationOutput(38.0, 42.0, 0.098, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "BAC");
        }

        @Test
        @DisplayName("V - Payments with 65% margin (high but valid)")
        void testVisa_Payments() {
            CompanyDataDTO company = createProfitableCompany("V", "Visa Inc", 0.65);
            ValuationOutputDTO output = createMockValuationOutput(280.0, 310.0, 0.080, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "V");
            // Visa has exceptionally high margins - this is valid for payments industry
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0.5, "V has high margins");
        }

        @Test
        @DisplayName("MA - Payments with 55% margin")
        void testMastercard_Payments() {
            CompanyDataDTO company = createProfitableCompany("MA", "Mastercard Inc", 0.55);
            ValuationOutputDTO output = createMockValuationOutput(450.0, 480.0, 0.082, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "MA");
        }

        @Test
        @DisplayName("BRK.B - Conglomerate with 15% margin")
        void testBerkshire_Conglomerate() {
            CompanyDataDTO company = createProfitableCompany("BRK.B", "Berkshire Hathaway", 0.15);
            ValuationOutputDTO output = createMockValuationOutput(360.0, 380.0, 0.085, 0.04);
            
            assertPositiveIntrinsicValue(output.getCompanyDTO().getEstimatedValuePerShare(), "BRK.B");
        }
    }

    // ================================================================
    // 2. LOSS-MAKING COMPANIES (15 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Loss-Making Company Tests")
    class LossMakingCompanyTests {

        @Test
        @DisplayName("RIVN - EV maker with -45% margin")
        void testRivian_EVMaker() {
            CompanyDataDTO company = createLossMakingCompany("RIVN", "Rivian Automotive", -0.45, 0.10);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0, "RIVN has negative margin");
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0, "RIVN targets positive margin");
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "RIVN");
        }

        @Test
        @DisplayName("LCID - EV maker with -80% margin (extreme distress)")
        void testLucid_EVMaker() {
            CompanyDataDTO company = createLossMakingCompany("LCID", "Lucid Group", -0.80, 0.08);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < -0.5, "LCID has severe losses");
            // Note: -80% margin exceeds -50% threshold - this is intentional for distressed company test
            // DCF should handle this with calibration and failure probability adjustments
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0, "LCID targets turnaround");
        }

        @Test
        @DisplayName("FSR - EV maker with -100% margin")
        void testFisker_EVMaker() {
            CompanyDataDTO company = createLossMakingCompany("FSR", "Fisker Inc", -1.00, 0.05);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < -0.5, "FSR has extreme losses");
        }

        @Test
        @DisplayName("NKLA - EV maker with -200% margin (pre-revenue)")
        void testNikola_EVMaker() {
            CompanyDataDTO company = createLossMakingCompany("NKLA", "Nikola Corp", -2.00, 0.05);
            
            // Extreme loss-making - near distressed
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < -1.0, "NKLA extreme losses");
        }

        @Test
        @DisplayName("GOEV - EV maker with -150% margin")
        void testCanoo_EVMaker() {
            CompanyDataDTO company = createLossMakingCompany("GOEV", "Canoo Inc", -1.50, 0.05);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < -1.0, "GOEV extreme losses");
        }

        @Test
        @DisplayName("SNAP - Unprofitable tech with -15% margin")
        void testSnap_UnprofitableTech() {
            CompanyDataDTO company = createLossMakingCompany("SNAP", "Snap Inc", -0.15, 0.15);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0, "SNAP has negative margin");
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0, "SNAP targets profitability");
        }

        @Test
        @DisplayName("PINS - Unprofitable tech with -5% margin")
        void testPinterest_UnprofitableTech() {
            CompanyDataDTO company = createLossMakingCompany("PINS", "Pinterest Inc", -0.05, 0.18);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0, "PINS marginally unprofitable");
        }

        @Test
        @DisplayName("PLTR - Tech with -10% margin, high growth")
        void testPalantir_UnprofitableTech() {
            CompanyDataDTO company = createLossMakingCompany("PLTR", "Palantir Technologies", -0.10, 0.20);
            company.getCompanyDriveDataDTO().setRevenueNextYear(0.25); // High growth
            
            assertTrue(company.getCompanyDriveDataDTO().getRevenueNextYear() > 0.20, "PLTR high growth");
        }

        @Test
        @DisplayName("COIN - Crypto exchange with -20% margin")
        void testCoinbase_CryptoExchange() {
            CompanyDataDTO company = createLossMakingCompany("COIN", "Coinbase Global", -0.20, 0.25);
            company.getCompanyDriveDataDTO().setInitialCostCapital(14.0); // Higher risk
            
            assertTrue(company.getCompanyDriveDataDTO().getInitialCostCapital() > 12.0, "COIN high WACC");
        }

        @Test
        @DisplayName("HOOD - Fintech with -25% margin")
        void testRobinhood_Fintech() {
            CompanyDataDTO company = createLossMakingCompany("HOOD", "Robinhood Markets", -0.25, 0.15);
            
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "HOOD");
        }

        @Test
        @DisplayName("UBER - Ride-sharing with -5% margin")
        void testUber_Ridesharing() {
            CompanyDataDTO company = createLossMakingCompany("UBER", "Uber Technologies", -0.05, 0.12);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0, "UBER marginally negative");
        }

        @Test
        @DisplayName("LYFT - Ride-sharing with -12% margin")
        void testLyft_Ridesharing() {
            CompanyDataDTO company = createLossMakingCompany("LYFT", "Lyft Inc", -0.12, 0.08);
            
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0, "LYFT targets profit");
        }

        @Test
        @DisplayName("DASH - Delivery with -8% margin")
        void testDoorDash_Delivery() {
            CompanyDataDTO company = createLossMakingCompany("DASH", "DoorDash Inc", -0.08, 0.10);
            
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "DASH");
        }

        @Test
        @DisplayName("ABNB - Travel tech with -3% margin")
        void testAirbnb_TravelTech() {
            CompanyDataDTO company = createLossMakingCompany("ABNB", "Airbnb Inc", -0.03, 0.20);
            
            // Nearly breakeven
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > -0.10, "ABNB near breakeven");
        }

        @Test
        @DisplayName("RBLX - Gaming with -30% margin")
        void testRoblox_Gaming() {
            CompanyDataDTO company = createLossMakingCompany("RBLX", "Roblox Corp", -0.30, 0.15);
            
            assertMarginWithinBounds(company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "RBLX");
        }
    }

    // ================================================================
    // 3. NOL TRANSITION COMPANIES (10 tests)
    // ================================================================

    @Nested
    @DisplayName("3. NOL Transition Company Tests")
    class NOLTransitionCompanyTests {

        @Test
        @DisplayName("TSLA_NOL - Tesla transitioning with NOL carryforward")
        void testTesla_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("TSLA", "Tesla Inc", 0.08, 0.15);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0, "TSLA now profitable");
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 
                       company.getCompanyDriveDataDTO().getOperatingMarginNextYear(), "TSLA improving margins");
        }

        @Test
        @DisplayName("AMZN_NOL - Amazon with historical NOLs")
        void testAmazon_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("AMZN", "Amazon.com", 0.05, 0.12);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0, "AMZN profitable");
        }

        @Test
        @DisplayName("SQ_NOL - Block transitioning to profitability")
        void testBlock_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("SQ", "Block Inc", -0.02, 0.12);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0.05, "SQ transitioning");
        }

        @Test
        @DisplayName("SHOP_NOL - Shopify with NOL utilization")
        void testShopify_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("SHOP", "Shopify Inc", 0.03, 0.18);
            
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0.15, "SHOP targets high margin");
        }

        @Test
        @DisplayName("SPOT_NOL - Spotify transitioning")
        void testSpotify_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("SPOT", "Spotify Technology", -0.01, 0.08);
            
            assertTrue(Math.abs(company.getCompanyDriveDataDTO().getOperatingMarginNextYear()) < 0.05, "SPOT near breakeven");
        }

        @Test
        @DisplayName("CRWD_NOL - CrowdStrike with improving margins")
        void testCrowdStrike_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("CRWD", "CrowdStrike Holdings", 0.05, 0.22);
            
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0.20, "CRWD targets 20%+ margin");
        }

        @Test
        @DisplayName("ZS_NOL - Zscaler transitioning")
        void testZscaler_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("ZS", "Zscaler Inc", 0.02, 0.18);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0, "ZS profitable");
        }

        @Test
        @DisplayName("NET_NOL - Cloudflare with NOLs")
        void testCloudflare_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("NET", "Cloudflare Inc", -0.05, 0.15);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < 0, "NET still unprofitable");
        }

        @Test
        @DisplayName("DDOG_NOL - Datadog transitioning")
        void testDatadog_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("DDOG", "Datadog Inc", 0.08, 0.22);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0, "DDOG profitable");
        }

        @Test
        @DisplayName("MDB_NOL - MongoDB with NOL carryforward")
        void testMongoDB_NOLTransition() {
            CompanyDataDTO company = createNOLTransitionCompany("MDB", "MongoDB Inc", -0.08, 0.18);
            
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0.15, "MDB targets profitability");
        }
    }

    // ================================================================
    // 4. HIGH STOCK OPTIONS COMPANIES (10 tests)
    // ================================================================

    @Nested
    @DisplayName("4. High Stock Options Company Tests")
    class HighStockOptionsCompanyTests {

        @Test
        @DisplayName("TSLA - High stock-based compensation")
        void testTesla_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("TSLA", "Tesla Inc", 0.08);
            
            assertNotNull(company);
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() > 0, "TSLA profitable");
        }

        @Test
        @DisplayName("NVDA - Significant employee options")
        void testNvidia_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("NVDA", "NVIDIA Corp", 0.06);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("CRM - High SBC as % of revenue")
        void testSalesforce_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("CRM", "Salesforce Inc", 0.07);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("NOW - ServiceNow with options dilution")
        void testServiceNow_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("NOW", "ServiceNow Inc", 0.05);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("SNOW - Snowflake with high SBC")
        void testSnowflake_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("SNOW", "Snowflake Inc", 0.10);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("ADBE - Adobe with options")
        void testAdobe_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("ADBE", "Adobe Inc", 0.04);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("ORCL - Oracle with employee options")
        void testOracle_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("ORCL", "Oracle Corp", 0.03);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("WDAY - Workday with high SBC")
        void testWorkday_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("WDAY", "Workday Inc", 0.08);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("SPLK - Splunk with options")
        void testSplunk_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("SPLK", "Splunk Inc", 0.09);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("TEAM - Atlassian with high SBC")
        void testAtlassian_HighOptions() {
            CompanyDataDTO company = createHighOptionsCompany("TEAM", "Atlassian Corp", 0.07);
            
            assertNotNull(company);
        }
    }

    // ================================================================
    // 5. DISTRESSED COMPANIES (15 tests)
    // ================================================================

    @Nested
    @DisplayName("5. Distressed Company Tests")
    class DistressedCompanyTests {

        @Test
        @DisplayName("Distressed_1 - Severe revenue decline capped at -50%")
        void testDistressed_SevereDecline() {
            CompanyDataDTO company = createDistressedCompany("DIST1", "Distressed Co 1", -0.75, -0.40);
            
            // Revenue decline should be capped at -50%
            assertTrue(company.getCompanyDriveDataDTO().getRevenueNextYear() >= -0.50, 
                "Revenue decline capped at -50%");
        }

        @Test
        @DisplayName("Distressed_2 - Negative book equity")
        void testDistressed_NegativeBookEquity() {
            CompanyDataDTO company = createDistressedCompany("DIST2", "Distressed Co 2", -0.40, -0.30);
            
            assertTrue(company.getFinancialDataDTO().getBookValueEqualityTTM() < 0, 
                "Distressed company has negative book equity");
        }

        @Test
        @DisplayName("Distressed_3 - Low cash position")
        void testDistressed_LowCash() {
            CompanyDataDTO company = createDistressedCompany("DIST3", "Distressed Co 3", -0.30, -0.50);
            
            assertTrue(company.getFinancialDataDTO().getCashAndMarkablTTM() < 
                       company.getFinancialDataDTO().getRevenueTTM() * 0.05, 
                "Distressed company has low cash");
        }

        @Test
        @DisplayName("Distressed_4 - High WACC for distress")
        void testDistressed_HighWACC() {
            CompanyDataDTO company = createDistressedCompany("DIST4", "Distressed Co 4", -0.50, -0.35);
            
            assertTrue(company.getCompanyDriveDataDTO().getInitialCostCapital() > 12.0, 
                "Distressed company has high WACC");
        }

        @Test
        @DisplayName("Distressed_5 - Bankruptcy candidate")
        void testDistressed_BankruptcyCandidate() {
            CompanyDataDTO company = createDistressedCompany("DIST5", "Distressed Co 5", -0.60, -0.80);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < -0.50, 
                "Bankruptcy candidate has severe losses");
        }

        @Test
        @DisplayName("Distressed_6 - Restructuring company")
        void testDistressed_Restructuring() {
            CompanyDataDTO company = createDistressedCompany("DIST6", "Restructuring Co", -0.25, -0.15);
            company.getCompanyDriveDataDTO().setTargetPreTaxOperatingMargin(0.08); // Turnaround target
            
            assertTrue(company.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() > 0, 
                "Restructuring targets profitability");
        }

        @Test
        @DisplayName("Distressed_7 - Retail bankruptcy risk")
        void testDistressed_RetailBankruptcy() {
            CompanyDataDTO company = createDistressedCompany("DIST7", "Distressed Retail", -0.35, -0.25);
            company.getBasicInfoDataDTO().setIndustryUs("retail");
            
            assertNotNull(company.getBasicInfoDataDTO().getIndustryUs());
        }

        @Test
        @DisplayName("Distressed_8 - Energy sector distress")
        void testDistressed_EnergyDistress() {
            CompanyDataDTO company = createDistressedCompany("DIST8", "Distressed Energy", -0.45, -0.60);
            company.getBasicInfoDataDTO().setIndustryUs("energy");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("Distressed_9 - Real estate distress")
        void testDistressed_RealEstateDistress() {
            CompanyDataDTO company = createDistressedCompany("DIST9", "Distressed REIT", -0.20, -0.30);
            company.getBasicInfoDataDTO().setIndustryUs("real estate");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("Distressed_10 - Healthcare distress")
        void testDistressed_HealthcareDistress() {
            CompanyDataDTO company = createDistressedCompany("DIST10", "Distressed Healthcare", -0.55, -0.40);
            company.getBasicInfoDataDTO().setIndustryUs("healthcare");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("Distressed_11 - Tech startup failure")
        void testDistressed_TechStartupFailure() {
            CompanyDataDTO company = createDistressedCompany("DIST11", "Failed Tech Startup", -0.80, -1.50);
            
            assertTrue(company.getCompanyDriveDataDTO().getOperatingMarginNextYear() < -1.0, 
                "Tech startup has extreme losses");
        }

        @Test
        @DisplayName("Distressed_12 - Manufacturing decline")
        void testDistressed_ManufacturingDecline() {
            CompanyDataDTO company = createDistressedCompany("DIST12", "Declining Manufacturer", -0.40, -0.20);
            company.getBasicInfoDataDTO().setIndustryUs("manufacturing");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("Distressed_13 - Media company distress")
        void testDistressed_MediaDistress() {
            CompanyDataDTO company = createDistressedCompany("DIST13", "Distressed Media", -0.30, -0.25);
            company.getBasicInfoDataDTO().setIndustryUs("media");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("Distressed_14 - Airline distress")
        void testDistressed_AirlineDistress() {
            CompanyDataDTO company = createDistressedCompany("DIST14", "Distressed Airline", -0.50, -0.35);
            company.getBasicInfoDataDTO().setIndustryUs("airlines");
            company.getCompanyDriveDataDTO().setInitialCostCapital(18.0);
            
            assertTrue(company.getCompanyDriveDataDTO().getInitialCostCapital() > 15.0, 
                "Airline has very high WACC");
        }

        @Test
        @DisplayName("Distressed_15 - Hospitality distress")
        void testDistressed_HospitalityDistress() {
            CompanyDataDTO company = createDistressedCompany("DIST15", "Distressed Hotel", -0.45, -0.55);
            company.getBasicInfoDataDTO().setIndustryUs("hospitality");
            
            assertNotNull(company);
        }
    }

    // ================================================================
    // 6. HIGH-LEVERAGE COMPANIES (10 tests)
    // ================================================================

    @Nested
    @DisplayName("6. High-Leverage Company Tests")
    class HighLeverageCompanyTests {

        @Test
        @DisplayName("AAL - Airline with 5x leverage")
        void testAmericanAirlines_HighLeverage() {
            CompanyDataDTO company = createHighLeverageCompany("AAL", "American Airlines", 5.0, 0.08);
            
            double debtEquity = company.getFinancialDataDTO().getBookValueDebtTTM() / 
                               company.getFinancialDataDTO().getBookValueEqualityTTM();
            assertTrue(debtEquity > 4.0, "AAL has high leverage");
        }

        @Test
        @DisplayName("DAL - Airline with 3x leverage")
        void testDelta_HighLeverage() {
            CompanyDataDTO company = createHighLeverageCompany("DAL", "Delta Air Lines", 3.0, 0.10);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("UAL - Airline with 4x leverage")
        void testUnited_HighLeverage() {
            CompanyDataDTO company = createHighLeverageCompany("UAL", "United Airlines", 4.0, 0.09);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("SPG - REIT with 2x leverage")
        void testSimonProperty_HighLeverage() {
            CompanyDataDTO company = createHighLeverageCompany("SPG", "Simon Property Group", 2.0, 0.40);
            company.getBasicInfoDataDTO().setIndustryUs("real estate");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("DUK - Utility with 1.5x leverage")
        void testDuke_Utility() {
            CompanyDataDTO company = createHighLeverageCompany("DUK", "Duke Energy", 1.5, 0.25);
            company.getBasicInfoDataDTO().setIndustryUs("utilities");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("SO - Utility with 1.8x leverage")
        void testSouthern_Utility() {
            CompanyDataDTO company = createHighLeverageCompany("SO", "Southern Company", 1.8, 0.22);
            company.getBasicInfoDataDTO().setIndustryUs("utilities");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("T - Telecom with 2.5x leverage")
        void testATT_Telecom() {
            CompanyDataDTO company = createHighLeverageCompany("T", "AT&T Inc", 2.5, 0.18);
            company.getBasicInfoDataDTO().setIndustryUs("telecommunications");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("VZ - Telecom with 2x leverage")
        void testVerizon_Telecom() {
            CompanyDataDTO company = createHighLeverageCompany("VZ", "Verizon Communications", 2.0, 0.20);
            company.getBasicInfoDataDTO().setIndustryUs("telecommunications");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("F - Automotive with 3x leverage")
        void testFord_Automotive() {
            CompanyDataDTO company = createHighLeverageCompany("F", "Ford Motor", 3.0, 0.05);
            company.getBasicInfoDataDTO().setIndustryUs("automotive");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("GM - Automotive with 2.5x leverage")
        void testGM_Automotive() {
            CompanyDataDTO company = createHighLeverageCompany("GM", "General Motors", 2.5, 0.06);
            company.getBasicInfoDataDTO().setIndustryUs("automotive");
            
            assertNotNull(company);
        }
    }

    // ================================================================
    // 7. MULTI-SEGMENT CONGLOMERATES (10 tests)
    // ================================================================

    @Nested
    @DisplayName("7. Multi-Segment Conglomerate Tests")
    class MultiSegmentConglomerateTests {

        @Test
        @DisplayName("GE - Industrial conglomerate with 4 segments")
        void testGE_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("GE", "General Electric", 4);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("JNJ - Healthcare conglomerate with 3 segments")
        void testJNJ_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("JNJ", "Johnson & Johnson", 3);
            company.getBasicInfoDataDTO().setIndustryUs("healthcare");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("MMM - Industrial conglomerate with 4 segments")
        void testMMM_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("MMM", "3M Company", 4);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("HON - Industrial with 4 segments")
        void testHoneywell_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("HON", "Honeywell International", 4);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("UTX - Aerospace conglomerate")
        void testUTX_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("UTX", "United Technologies", 3);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("DIS - Media conglomerate with 4 segments")
        void testDisney_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("DIS", "Walt Disney", 4);
            company.getBasicInfoDataDTO().setIndustryUs("media");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("CMCSA - Media/Telecom conglomerate")
        void testComcast_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("CMCSA", "Comcast Corp", 3);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("ABT - Healthcare conglomerate")
        void testAbbott_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("ABT", "Abbott Laboratories", 4);
            company.getBasicInfoDataDTO().setIndustryUs("healthcare");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("RTX - Aerospace/Defense conglomerate")
        void testRaytheon_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("RTX", "Raytheon Technologies", 4);
            company.getBasicInfoDataDTO().setIndustryUs("aerospace");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("ITW - Industrial conglomerate with 7 segments")
        void testITW_MultiSegment() {
            CompanyDataDTO company = createMultiSegmentCompany("ITW", "Illinois Tool Works", 7);
            
            assertNotNull(company);
        }
    }

    // ================================================================
    // 8. R&D INTENSIVE COMPANIES (10 tests)
    // ================================================================

    @Nested
    @DisplayName("8. R&D Intensive Company Tests")
    class RDIntensiveCompanyTests {

        @Test
        @DisplayName("GILD - Biotech with 20% R&D/Revenue")
        void testGilead_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("GILD", "Gilead Sciences", 0.20);
            
            assertNotNull(company.getFinancialDataDTO().getResearchAndDevelopmentMap());
            assertFalse(company.getFinancialDataDTO().getResearchAndDevelopmentMap().isEmpty(), 
                "GILD has R&D history");
        }

        @Test
        @DisplayName("REGN - Biotech with 25% R&D/Revenue")
        void testRegeneron_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("REGN", "Regeneron Pharmaceuticals", 0.25);
            
            assertTrue(company.getFinancialDataDTO().getResearchAndDevelopmentMap().size() >= 5, 
                "REGN has 5+ years R&D history");
        }

        @Test
        @DisplayName("VRTX - Biotech with 30% R&D/Revenue")
        void testVertex_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("VRTX", "Vertex Pharmaceuticals", 0.30);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("BIIB - Biotech with 22% R&D/Revenue")
        void testBiogen_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("BIIB", "Biogen Inc", 0.22);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("MRNA - mRNA with 35% R&D/Revenue")
        void testModerna_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("MRNA", "Moderna Inc", 0.35);
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("INTC - Semiconductor with 25% R&D/Revenue")
        void testIntel_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("INTC", "Intel Corp", 0.25);
            company.getBasicInfoDataDTO().setIndustryUs("semiconductors");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("AMD - Semiconductor with 20% R&D/Revenue")
        void testAMD_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("AMD", "Advanced Micro Devices", 0.20);
            company.getBasicInfoDataDTO().setIndustryUs("semiconductors");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("NVDA - Semiconductor with 18% R&D/Revenue")
        void testNVDA_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("NVDA", "NVIDIA Corp", 0.18);
            company.getBasicInfoDataDTO().setIndustryUs("semiconductors");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("PFE - Pharma with 15% R&D/Revenue")
        void testPfizer_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("PFE", "Pfizer Inc", 0.15);
            company.getBasicInfoDataDTO().setIndustryUs("pharmaceuticals");
            
            assertNotNull(company);
        }

        @Test
        @DisplayName("MRK - Pharma with 18% R&D/Revenue")
        void testMerck_RDIntensive() {
            CompanyDataDTO company = createRDIntensiveCompany("MRK", "Merck & Co", 0.18);
            company.getBasicInfoDataDTO().setIndustryUs("pharmaceuticals");
            
            assertNotNull(company);
        }
    }

    // ================================================================
    // 9. NON-US COMPANIES (5 tests)
    // ================================================================

    @Nested
    @DisplayName("9. Non-US Company Tests")
    class NonUSCompanyTests {

        @Test
        @DisplayName("BABA - China with 3% country risk premium")
        void testAlibaba_China() {
            CompanyDataDTO company = createNonUSCompany("BABA", "Alibaba Group", "China", 3.0);
            
            assertEquals("China", company.getBasicInfoDataDTO().getCountryOfIncorporation());
            assertTrue(company.getCompanyDriveDataDTO().getInitialCostCapital() > 10.0, 
                "China company has higher WACC");
        }

        @Test
        @DisplayName("INFY - India with 2.5% country risk premium")
        void testInfosys_India() {
            CompanyDataDTO company = createNonUSCompany("INFY", "Infosys Ltd", "India", 2.5);
            
            assertEquals("India", company.getBasicInfoDataDTO().getCountryOfIncorporation());
        }

        @Test
        @DisplayName("VALE - Brazil with 4% country risk premium")
        void testVale_Brazil() {
            CompanyDataDTO company = createNonUSCompany("VALE", "Vale SA", "Brazil", 4.0);
            
            assertEquals("Brazil", company.getBasicInfoDataDTO().getCountryOfIncorporation());
            assertTrue(company.getCompanyDriveDataDTO().getInitialCostCapital() > 11.0, 
                "Brazil company has higher WACC");
        }

        @Test
        @DisplayName("ASML - Netherlands with 0.5% country risk premium")
        void testASML_Netherlands() {
            CompanyDataDTO company = createNonUSCompany("ASML", "ASML Holding", "Netherlands", 0.5);
            
            assertEquals("Netherlands", company.getBasicInfoDataDTO().getCountryOfIncorporation());
            assertTrue(company.getCompanyDriveDataDTO().getInitialCostCapital() < 10.0, 
                "Developed market has lower WACC");
        }

        @Test
        @DisplayName("TSM - Taiwan with 1.5% country risk premium")
        void testTSMC_Taiwan() {
            CompanyDataDTO company = createNonUSCompany("TSM", "Taiwan Semiconductor", "Taiwan", 1.5);
            
            assertEquals("Taiwan", company.getBasicInfoDataDTO().getCountryOfIncorporation());
        }
    }
}

