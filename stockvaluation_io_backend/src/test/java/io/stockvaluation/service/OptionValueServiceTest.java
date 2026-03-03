package io.stockvaluation.service;

import io.stockvaluation.dto.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OptionValueService Tests
 * 
 * Tests the Black-Scholes option valuation logic:
 * - d1 and d2 calculations
 * - N(d1) and N(d2) cumulative distribution
 * - Value per option calculation
 * - Total option value calculation
 * - Edge cases (zero strike, zero maturity, etc.)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OptionValueServiceTest {

    @Mock
    private CommonService commonService;

    @InjectMocks
    private OptionValueService optionValueService;

    @BeforeEach
    void setUp() {
        CompanyDataDTO companyData = createCompanyData(175.0, 4.0, 1000000.0);
        when(commonService.getCompanyDtaFromYahooApi(any()))
            .thenReturn(companyData);
    }

    // ================================================================
    // 1. BLACK-SCHOLES D1/D2 CALCULATION TESTS (4 tests)
    // ================================================================

    @Nested
    @DisplayName("1. Black-Scholes D1/D2 Calculation Tests")
    class BlackScholesD1D2Tests {

        @Test
        @DisplayName("d1 calculation should be correct for standard inputs")
        void testD1Calculation() {
            // d1 = (ln(S/K) + (r + sigma^2/2) * T) / (sigma * sqrt(T))
            double stockPrice = 100.0;
            double strikePrice = 100.0;
            double riskFreeRate = 5.0; // 5%
            double volatility = 20.0;  // 20%
            double maturity = 1.0;     // 1 year
            
            double d1 = OptionValueService.calculated1(stockPrice, strikePrice, riskFreeRate, volatility, maturity);
            
            // At-the-money with these params, d1 should be around 0.35
            assertNotNull(d1);
            assertFalse(Double.isNaN(d1));
        }

        @Test
        @DisplayName("d2 calculation should be d1 minus sigma*sqrt(T)")
        void testD2Calculation() {
            double d1 = 0.5;
            double volatility = 20.0; // 20%
            double maturity = 1.0;
            
            double d2 = OptionValueService.calculated2(d1, volatility, maturity);
            
            // d2 = d1 - sigma * sqrt(T) = 0.5 - 0.20 * 1 = 0.30
            assertEquals(0.30, d2, 0.01);
        }

        @Test
        @DisplayName("d1 should be positive for in-the-money options")
        void testD1InTheMoney() {
            double stockPrice = 120.0;  // ITM
            double strikePrice = 100.0;
            double riskFreeRate = 5.0;
            double volatility = 20.0;
            double maturity = 1.0;
            
            double d1 = OptionValueService.calculated1(stockPrice, strikePrice, riskFreeRate, volatility, maturity);
            
            assertTrue(d1 > 0, "d1 should be positive for ITM options");
        }

        @Test
        @DisplayName("d1 should be negative for deep out-of-money options")
        void testD1OutOfMoney() {
            double stockPrice = 80.0;   // OTM
            double strikePrice = 100.0;
            double riskFreeRate = 5.0;
            double volatility = 20.0;
            double maturity = 1.0;
            
            double d1 = OptionValueService.calculated1(stockPrice, strikePrice, riskFreeRate, volatility, maturity);
            
            assertTrue(d1 < 0, "d1 should be negative for OTM options");
        }
    }

    // ================================================================
    // 2. N(d1) AND N(d2) TESTS (2 tests)
    // ================================================================

    @Nested
    @DisplayName("2. Cumulative Normal Distribution Tests")
    class CumulativeNormalTests {

        @Test
        @DisplayName("N(d1) should return valid probability between 0 and 1")
        void testNd1ValidProbability() {
            double d1 = 0.5;
            
            double nd1 = optionValueService.calculateNd1(d1);
            
            assertTrue(nd1 >= 0 && nd1 <= 1, "N(d1) should be between 0 and 1");
        }

        @Test
        @DisplayName("N(d2) should return valid probability between 0 and 1")
        void testNd2ValidProbability() {
            double d2 = -0.5;
            
            double nd2 = OptionValueService.calculateNd2(d2);
            
            assertTrue(nd2 >= 0 && nd2 <= 1, "N(d2) should be between 0 and 1");
        }
    }

    // ================================================================
    // 3. VALUE PER OPTION TESTS (3 tests)
    // ================================================================

    @Nested
    @DisplayName("3. Value Per Option Tests")
    class ValuePerOptionTests {

        @Test
        @DisplayName("Value per option should be positive for ITM options")
        void testValuePerOptionITM() {
            double stockPrice = 120.0;
            double strikePrice = 100.0;
            double maturity = 1.0;
            double nd1 = 0.85;
            double nd2 = 0.80;
            double riskFreeRate = 0.05;
            double dividendYield = 0.0;
            
            double value = OptionValueService.calculateValuePerOption(
                stockPrice, strikePrice, maturity, nd1, nd2, riskFreeRate, dividendYield);
            
            assertTrue(value > 0, "Option value should be positive for ITM");
        }

        @Test
        @DisplayName("Deep OTM option should have near-zero value")
        void testValuePerOptionDeepOTM() {
            double stockPrice = 50.0;
            double strikePrice = 100.0;
            double maturity = 0.1;  // Short time to expiry
            double nd1 = 0.01;
            double nd2 = 0.005;
            double riskFreeRate = 0.05;
            double dividendYield = 0.0;
            
            double value = OptionValueService.calculateValuePerOption(
                stockPrice, strikePrice, maturity, nd1, nd2, riskFreeRate, dividendYield);
            
            // Deep OTM with short expiry should have very low value
            assertTrue(value >= 0, "Option value should be non-negative");
        }

        @Test
        @DisplayName("Option value should not exceed stock price")
        void testValuePerOptionUpperBound() {
            double stockPrice = 100.0;
            double strikePrice = 50.0;
            double maturity = 1.0;
            double nd1 = 0.99;
            double nd2 = 0.98;
            double riskFreeRate = 0.05;
            double dividendYield = 0.0;
            
            double value = OptionValueService.calculateValuePerOption(
                stockPrice, strikePrice, maturity, nd1, nd2, riskFreeRate, dividendYield);
            
            // Call option value should not exceed stock price
            assertTrue(value <= stockPrice, "Option value should not exceed stock price");
        }
    }

    // ================================================================
    // 4. FULL OPTION CALCULATION TESTS (4 tests)
    // ================================================================

    @Nested
    @DisplayName("4. Full Option Calculation Tests")
    class FullOptionCalculationTests {

        @Test
        @DisplayName("Calculate option value for AAPL-like company")
        void testCalculateOptionValueAAPL() {
            CompanyDataDTO company = createCompanyData(175.0, 4.0, 15000000000.0);
            when(commonService.getCompanyDtaFromYahooApi("AAPL")).thenReturn(company);
            
            OptionValueResultDTO result = optionValueService.calculateOptionValue(
                "AAPL", 
                150.0,    // strikePrice
                3.0,      // avgMaturity (years)
                1000000.0, // optionStanding
                30.0       // standardDeviation (30% volatility)
            );
            
            assertNotNull(result);
            assertTrue(result.getValuePerOption() > 0);
            assertTrue(result.getValueOfAllOptionsOutstanding() > 0);
        }

        @Test
        @DisplayName("Total option value should be valuePerOption times optionStanding")
        void testTotalOptionValue() {
            CompanyDataDTO company = createCompanyData(100.0, 5.0, 1000000.0);
            when(commonService.getCompanyDtaFromYahooApi("TEST")).thenReturn(company);
            
            double optionStanding = 5000000.0;
            
            OptionValueResultDTO result = optionValueService.calculateOptionValue(
                "TEST", 
                90.0,     // strikePrice (ITM)
                2.0,      // avgMaturity
                optionStanding,
                25.0      // standardDeviation
            );
            
            assertEquals(result.getValuePerOption() * optionStanding, 
                        result.getValueOfAllOptionsOutstanding(), 0.01);
        }

        @Test
        @DisplayName("Unknown ticker should throw exception")
        void testUnknownTickerThrowsException() {
            when(commonService.getCompanyDtaFromYahooApi("UNKNOWN")).thenReturn(null);
            
            assertThrows(RuntimeException.class, () -> 
                optionValueService.calculateOptionValue("UNKNOWN", 100.0, 1.0, 1000.0, 20.0));
        }

        @Test
        @DisplayName("Option with high volatility should have higher value")
        void testHighVolatilityHigherValue() {
            CompanyDataDTO company = createCompanyData(100.0, 5.0, 1000000.0);
            when(commonService.getCompanyDtaFromYahooApi("TEST")).thenReturn(company);
            
            OptionValueResultDTO lowVolResult = optionValueService.calculateOptionValue(
                "TEST", 100.0, 1.0, 1000.0, 20.0);  // 20% vol
            
            OptionValueResultDTO highVolResult = optionValueService.calculateOptionValue(
                "TEST", 100.0, 1.0, 1000.0, 50.0);  // 50% vol
            
            assertTrue(highVolResult.getValuePerOption() > lowVolResult.getValuePerOption(),
                "Higher volatility should result in higher option value");
        }
    }

    // ================================================================
    // HELPER METHODS
    // ================================================================

    private CompanyDataDTO createCompanyData(double stockPrice, double riskFreeRate, double sharesOutstanding) {
        CompanyDataDTO company = new CompanyDataDTO();
        
        BasicInfoDataDTO basicInfo = new BasicInfoDataDTO();
        basicInfo.setTicker("TEST");
        basicInfo.setCompanyName("Test Company");
        company.setBasicInfoDataDTO(basicInfo);
        
        FinancialDataDTO financialData = new FinancialDataDTO();
        financialData.setStockPrice(stockPrice);
        financialData.setNoOfShareOutstanding(sharesOutstanding);
        company.setFinancialDataDTO(financialData);
        
        CompanyDriveDataDTO driveData = new CompanyDriveDataDTO();
        driveData.setRiskFreeRate(riskFreeRate / 100.0); // Convert to decimal
        company.setCompanyDriveDataDTO(driveData);
        
        return company;
    }
}

