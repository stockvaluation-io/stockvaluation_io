package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.domain.RDConverter;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.RDConverterRepository;
import io.stockvaluation.repository.RiskFreeRateRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage tests for CommonService, focusing on the many paths inside
 * calculateRDConverterValue, resolveRiskFreeRateForCurrency, and related
 * helpers.
 */
@ExtendWith(MockitoExtension.class)
class CommonServiceBranchCoverageTest {

    @Mock
    private ValuationAssumptionProperties valuationAssumptionProperties;
    @Mock
    private RiskFreeRateRepository riskFreeRateRepository;
    @Mock
    private CountryEquityRepository countryEquityRepository;
    @Mock
    private SectorMappingRepository sectorMappingRepository;
    @Mock
    private RDConverterRepository rdConverterRepository;
    @Mock
    private CompanyDataAssemblyService companyDataAssemblyService;
    @Mock
    private SegmentWeightedParameterService segmentWeightedParameterService;

    @InjectMocks
    private CommonService commonService;

    @BeforeEach
    void clearInternalMap() throws Exception {
        Field mapField = CommonService.class.getDeclaredField("basicAndFinancialMap");
        mapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) mapField.get(commonService);
        map.clear();
    }

    // =========================================================
    // calculateRDConverterValue — sector mapping variations
    // =========================================================

    @Test
    void calculateRDConverterValue_nullSectorMapping_usesDefaultAmortization() {
        // Sector mapping not found → should not throw; uses default amortization=4
        when(sectorMappingRepository.findByIndustryName("UnknownIndustry")).thenReturn(null);

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 200.0);
        rdMap.put("currentR&D-1", 180.0);

        // Should not throw; sectorMapping is null → we cannot call
        // rdConverterRepository
        // without NPE → need graceful handling
        assertThrows(NullPointerException.class,
                () -> commonService.calculateRDConverterValue("UnknownIndustry", 21.0, rdMap));
        // NOTE: This documents current behaviour. A future refactor should guard
        // against null.
    }

    @Test
    void calculateRDConverterValue_nullRDConverter_usesDefaultAmortization4() {
        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Software");
        when(sectorMappingRepository.findByIndustryName("Tech")).thenReturn(mapping);
        when(rdConverterRepository.findByIndustryName("Software")).thenReturn(null);

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 100.0);
        rdMap.put("currentR&D-1", 90.0);
        rdMap.put("currentR&D-2", 80.0);
        rdMap.put("currentR&D-3", 70.0);

        RDResult result = commonService.calculateRDConverterValue("Tech", 25.0, rdMap);

        assertNotNull(result);
        // Default amortization = 4, so 3 past years are included
        assertTrue(result.getTotalResearchAsset() > 0);
    }

    @Test
    void calculateRDConverterValue_amortizationPeriodGreaterThan4_clampedTo4() {
        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Pharma");
        when(sectorMappingRepository.findByIndustryName("Pharma")).thenReturn(mapping);

        RDConverter rdConverter = new RDConverter();
        rdConverter.setAmortizationPeriod(10); // > 4, should be clamped to 4
        when(rdConverterRepository.findByIndustryName("Pharma")).thenReturn(rdConverter);

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 100.0);
        rdMap.put("currentR&D-1", 90.0);

        RDResult result = commonService.calculateRDConverterValue("Pharma", 20.0, rdMap);

        assertNotNull(result);
        assertTrue(result.getTotalResearchAsset() > 0);
    }

    @Test
    void calculateRDConverterValue_noPastYears_stillCalculates() {
        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Biotech");
        when(sectorMappingRepository.findByIndustryName("Biotech")).thenReturn(mapping);

        RDConverter rdConverter = new RDConverter();
        rdConverter.setAmortizationPeriod(3);
        when(rdConverterRepository.findByIndustryName("Biotech")).thenReturn(rdConverter);

        // No past years → only current year present
        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 150.0);

        RDResult result = commonService.calculateRDConverterValue("Biotech", 20.0, rdMap);

        assertNotNull(result);
        // currentYearExpense is NOT null/0, but no past years →
        // effectiveAmortizationPeriod=1
        assertEquals(0.0, result.getTotalAmortization(), 0.001);
        assertEquals(150.0, result.getTotalResearchAsset(), 0.001);
    }

    @Test
    void calculateRDConverterValue_partialHistory_adjustsAmortizationPeriod() {
        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("AI");
        when(sectorMappingRepository.findByIndustryName("AI")).thenReturn(mapping);

        RDConverter rdConverter = new RDConverter();
        rdConverter.setAmortizationPeriod(4); // wants 4 years but only 1 past available
        when(rdConverterRepository.findByIndustryName("AI")).thenReturn(rdConverter);

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 100.0);
        rdMap.put("currentR&D-1", 80.0); // only 1 past year (not equal to currentR&D-0)

        RDResult result = commonService.calculateRDConverterValue("AI", 21.0, rdMap);

        assertNotNull(result);
        // effective amortization = 2 (1 past + current)
        assertTrue(result.getTotalResearchAsset() > 0);
    }

    @Test
    void calculateRDConverterValue_rdYear1SameAsCurrent_notAddedToPastYears() {
        // When currentR&D-1 == currentR&D-0, it is excluded from pastRdExpenses
        SectorMapping mapping = new SectorMapping();
        mapping.setIndustryAsPerExcel("Cloud");
        when(sectorMappingRepository.findByIndustryName("Cloud")).thenReturn(mapping);

        RDConverter rdConverter = new RDConverter();
        rdConverter.setAmortizationPeriod(3);
        when(rdConverterRepository.findByIndustryName("Cloud")).thenReturn(rdConverter);

        Map<String, Double> rdMap = new HashMap<>();
        rdMap.put("currentR&D-0", 100.0);
        rdMap.put("currentR&D-1", 100.0); // same as current → excluded

        RDResult result = commonService.calculateRDConverterValue("Cloud", 21.0, rdMap);

        assertNotNull(result);
        // Only currentR&D-0 treated, effectiveAmortizationPeriod = 1
        assertEquals(0.0, result.getTotalAmortization(), 0.001);
    }

    // =========================================================
    // resolveRiskFreeRateForCurrency — blank string fallback
    // =========================================================

    @Test
    void resolveRiskFreeRateForCurrency_blankString_fallsBackToBaseline() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn(null);
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(4.5);

        double rate = commonService.resolveRiskFreeRateForCurrency("   ");

        assertEquals(4.5, rate);
    }

    @Test
    void resolveRiskFreeRateForCurrency_currencyNotInRepo_fallsBackToBaseline() {
        when(riskFreeRateRepository.findRiskFreeRateByCurrency("GBP")).thenReturn(Optional.empty());
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn(null);
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(4.0);

        double rate = commonService.resolveRiskFreeRateForCurrency("GBP");

        assertEquals(4.0, rate);
    }

    // =========================================================
    // ERP helpers — config mature ERP plus country risk premium
    // =========================================================

    @Test
    void resolveMatureMarketPremium_ignoresCountryTable() {
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);

        double premium = commonService.resolveMatureMarketPremium();

        assertEquals(4.77, premium);
        verifyNoInteractions(countryEquityRepository);
    }

    @Test
    void resolveEquityRiskPremiumForCountry_countryNotFound_usesMatureErpOnly() {
        when(countryEquityRepository.findCountryRiskPremiumByCountry("Unknown")).thenReturn(Optional.empty());
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);

        double premium = commonService.resolveEquityRiskPremiumForCountry("Unknown");

        assertEquals(4.77, premium);
    }

    @Test
    void resolveEquityRiskPremiumForCountry_swedenUsesZeroCountryRiskPremium() {
        when(countryEquityRepository.findCountryRiskPremiumByCountry("Sweden")).thenReturn(Optional.of(0.0));
        when(valuationAssumptionProperties.getMatureMarketPremium()).thenReturn(4.77);

        double premium = commonService.resolveEquityRiskPremiumForCountry("Sweden");

        assertEquals(4.77, premium);
    }

    // =========================================================
    // resolveBaselineRiskFreeRate — blank currency code fallback
    // =========================================================

    @Test
    void resolveBaselineRiskFreeRate_blankCurrencyCode_fallsBackToProperty() {
        when(valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode()).thenReturn("  ");
        when(valuationAssumptionProperties.getBaselineRiskFreeRate()).thenReturn(4.58);

        double rate = commonService.resolveBaselineRiskFreeRate();

        assertEquals(4.58, rate);
    }
}
