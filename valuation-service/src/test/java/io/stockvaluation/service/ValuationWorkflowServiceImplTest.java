package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.GrowthDto;
import io.stockvaluation.dto.GrowthAnchorDTO;
import io.stockvaluation.dto.OptionValueResultDTO;
import io.stockvaluation.dto.SegmentResponseDTO;
import io.stockvaluation.dto.SegmentWeightedParameters;
import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.dto.ValuationTemplate;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.dto.valuationoutput.FinancialDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.EarningsLevel;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.enums.ModelType;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.form.SectorParameterOverride;
import io.stockvaluation.utils.SegmentParameterContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValuationWorkflowServiceImplTest {

    @Mock
    private CommonService commonService;
    @Mock
    private OptionValueService optionValueService;
    @Mock
    private ValuationOutputService valuationOutputService;
    @Mock
    private ValuationTemplateService valuationTemplateService;
    @Mock
    private GrowthAnchorService growthAnchorService;

    @AfterEach
    void tearDown() {
        SegmentParameterContext.clear();
    }

    @Test
    void getValuation_postWithSegments_callsSegmentWeightingAndReturnsFcffOutput() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        ValuationOutputDTO initial = valuationOutput(100.0, 100.0);
        ValuationOutputDTO refined = valuationOutput(100.0, 100.0);

        FinancialDataInput overrides = new FinancialDataInput();
        overrides.setCompoundAnnualGrowth2_5(7.2);
        overrides.setSegments(new SegmentResponseDTO(List.of(
                new SegmentResponseDTO.Segment("software", "technology", List.of("Cloud"), 0.9, 0.7, 0.3),
                new SegmentResponseDTO.Segment("hardware", "technology", List.of("Devices"), 0.8, 0.3, 0.2)
        )));

        stubHappyPath(companyData, template, initial, refined);

        ValuationOutputDTO result = workflow.getValuation("AAPL", overrides, false);

        assertSame(refined, result);
        assertEquals(CashflowType.FCFF, result.getPrimaryModel());
        assertEquals("FCFF selected from valuation template and used for valuation.", result.getModelSelectionRationale());
        assertNotNull(result.getAssumptionTransparency());
        assertNotNull(result.getAssumptionTransparency().getMarketImpliedExpectations());
        assertEquals(4, result.getAssumptionTransparency().getMarketImpliedExpectations().getMetrics().size());

        verify(commonService, times(1)).applySegmentWeightedParameters(any(FinancialDataInput.class), eq(companyData), anyList());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> adjustedCaptor = ArgumentCaptor.forClass(List.class);
        verify(commonService, atLeastOnce()).applySegmentWeightedParameters(
                any(FinancialDataInput.class),
                eq(companyData),
                adjustedCaptor.capture());
        assertTrue(adjustedCaptor.getAllValues().stream().anyMatch(list -> list.contains("compoundAnnualGrowth2_5")));

        ArgumentCaptor<FinancialDataInput> captor = ArgumentCaptor.forClass(FinancialDataInput.class);
        verify(valuationOutputService, times(2))
                .getValuationOutput(eq("AAPL"), captor.capture(), eq(false), eq(template));
        List<FinancialDataInput> requests = captor.getAllValues();
        assertEquals(2, requests.size());
        assertNotNull(requests.get(1).getSegments());
        assertEquals(2, requests.get(1).getSegments().getSegments().size());
    }

    @Test
    void getValuation_sectorOverridesWithoutSegments_doesNotApplySegmentWeighting() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        FinancialDataInput overrides = new FinancialDataInput();
        overrides.setSectorOverrides(List.of(
                new SectorParameterOverride("software", "operating_margin", 2.0, "relative_additive", "both")
        ));

        stubHappyPath(companyData, template, valuationOutput(100.0, 100.0), valuationOutput(100.0, 100.0));

        workflow.getValuation("AAPL", overrides, false);

        verify(commonService, never()).applySegmentWeightedParameters(any(FinancialDataInput.class), any(CompanyDataDTO.class), anyList());
    }

    @Test
    void getValuation_invalidTemplateModel_throwsAndClearsContext() {
        ValuationWorkflowServiceImpl workflow = workflow();
        SegmentWeightedParameters marker = new SegmentWeightedParameters();
        marker.setSegmentWeighted(true);
        SegmentParameterContext.setParameters(marker);

        CompanyDataDTO companyData = companyData();
        ValuationTemplate invalidTemplate = fcffTemplate();
        invalidTemplate.setCashflowToDiscount(null);

        when(commonService.getCompanyDataFromProvider("AAPL")).thenReturn(companyData);
        when(valuationTemplateService.determineTemplate(isNull(), eq(companyData))).thenReturn(invalidTemplate);

        assertThrows(IllegalStateException.class, () -> workflow.getValuation("AAPL", null, true));
        assertNull(SegmentParameterContext.getParameters());
    }

    @Test
    void getValuation_withGrowthAnchor_populatesTransparencyGrowthAnchor() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        ValuationOutputDTO initial = valuationOutput(100.0, 100.0);
        ValuationOutputDTO refined = valuationOutput(100.0, 100.0);
        stubHappyPath(companyData, template, initial, refined);

        GrowthAnchorDTO anchor = GrowthAnchorDTO.builder()
                .entity("softwareinternet")
                .entityDisplay("Software (Internet)")
                .region("United States")
                .year(2026)
                .p50(0.12)
                .build();
        when(growthAnchorService.getAnchorByYahooIndustry(anyString(), anyString())).thenReturn(Optional.of(anchor));

        ValuationOutputDTO result = workflow.getValuation("AAPL", new FinancialDataInput(), false);

        assertNotNull(result.getGrowthSkillContext());
        assertNotNull(result.getAssumptionTransparency());
        assertNotNull(result.getAssumptionTransparency().getGrowthAnchor());
        assertEquals("softwareinternet", result.getAssumptionTransparency().getGrowthAnchor().getEntity());
    }

    @Test
    void getValuation_withNullOverrides_doesNotThrow() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();
        stubHappyPath(companyData, template, valuationOutput(100.0, 100.0), valuationOutput(100.0, 100.0));

        ValuationOutputDTO result = workflow.getValuation("AAPL", null, false);

        assertNotNull(result);
        assertNotNull(result.getAssumptionTransparency());
    }

    @Test
    void getValuation_withoutMarketPrice_returnsEmptyImpliedMetrics() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        ValuationOutputDTO initial = valuationOutput(100.0, 95.0);
        ValuationOutputDTO refined = valuationOutput(null, 95.0);
        stubHappyPath(companyData, template, initial, refined);

        ValuationOutputDTO result = workflow.getValuation("AAPL", new FinancialDataInput(), false);

        assertNotNull(result.getAssumptionTransparency());
        assertNotNull(result.getAssumptionTransparency().getMarketImpliedExpectations());
        assertEquals(0, result.getAssumptionTransparency().getMarketImpliedExpectations().getMetrics().size());
    }

    @Test
    void getValuation_marketImpliedMetrics_canSolveWithMonotonicPricingFunction() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        ValuationOutputDTO initial = valuationOutput(90.0, 88.8);
        ValuationOutputDTO refined = valuationOutput(90.0, 88.8);
        stubMonotonicImpliedPath(companyData, template, initial, refined);

        ValuationOutputDTO result = workflow.getValuation("AAPL", new FinancialDataInput(), false);

        List<Boolean> solvedFlags = result.getAssumptionTransparency()
                .getMarketImpliedExpectations()
                .getMetrics()
                .stream()
                .map(metric -> Boolean.TRUE.equals(metric.getSolved()))
                .collect(Collectors.toList());
        assertEquals(4, solvedFlags.size());
        assertTrue(solvedFlags.stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void getValuation_marketImpliedMetrics_marksBoundedWhenTargetUnreachable() {
        ValuationWorkflowServiceImpl workflow = workflow();
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        ValuationOutputDTO initial = valuationOutput(10_000.0, 88.8);
        ValuationOutputDTO refined = valuationOutput(10_000.0, 88.8);
        stubMonotonicImpliedPath(companyData, template, initial, refined);

        ValuationOutputDTO result = workflow.getValuation("AAPL", new FinancialDataInput(), false);

        List<Boolean> solvedFlags = result.getAssumptionTransparency()
                .getMarketImpliedExpectations()
                .getMetrics()
                .stream()
                .map(metric -> Boolean.TRUE.equals(metric.getSolved()))
                .collect(Collectors.toList());
        assertEquals(4, solvedFlags.size());
        assertTrue(solvedFlags.stream().noneMatch(Boolean::booleanValue));
    }

    @Test
    void getValuation_strictGrowthPolicyViolation_throws422() {
        ValuationWorkflowServiceImpl workflow = workflow(properties -> properties.setStrictGrowthPolicy(true));
        CompanyDataDTO companyData = companyData();
        ValuationTemplate template = fcffTemplate();

        when(commonService.getCompanyDataFromProvider("AAPL")).thenReturn(companyData);
        when(valuationTemplateService.determineTemplate(isNull(), eq(companyData))).thenReturn(template);

        GrowthAnchorDTO anchor = GrowthAnchorDTO.builder()
                .entity("software")
                .region("United States")
                .confidenceScore(0.90)
                .p10(0.04)
                .p90(0.18)
                .build();
        when(growthAnchorService.getAnchorByYahooIndustry(anyString(), anyString())).thenReturn(Optional.of(anchor));

        FinancialDataInput overrides = new FinancialDataInput();
        overrides.setCompoundAnnualGrowth2_5(40.0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> workflow.getValuation("AAPL", overrides, false));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
    }

    private ValuationWorkflowServiceImpl workflow() {
        return workflow(properties -> {
        });
    }

    private ValuationWorkflowServiceImpl workflow(java.util.function.Consumer<ValuationAssumptionProperties> customizer) {
        ValuationAssumptionProperties props = new ValuationAssumptionProperties();
        props.setCalibrationMaxIterations(1);
        props.setSimulationIterations(1);
        customizer.accept(props);
        return new ValuationWorkflowServiceImpl(
                commonService,
                optionValueService,
                valuationOutputService,
                valuationTemplateService,
                props,
                growthAnchorService
        );
    }

    private void stubHappyPath(
            CompanyDataDTO companyData,
            ValuationTemplate template,
            ValuationOutputDTO initial,
            ValuationOutputDTO refined
    ) {
        when(commonService.getCompanyDataFromProvider("AAPL")).thenReturn(companyData);
        when(valuationTemplateService.determineTemplate(isNull(), eq(companyData))).thenReturn(template);
        when(growthAnchorService.getAnchorByYahooIndustry(anyString(), anyString())).thenReturn(Optional.empty());

        when(valuationOutputService.calculateCurrentSalesToCapitalRatio(any(FinancialDataInput.class), any(RDResult.class), any()))
                .thenReturn(1.0);
        when(valuationOutputService.getValuationOutput(eq("AAPL"), any(FinancialDataInput.class), eq(false), eq(template)))
                .thenReturn(initial, refined);

        when(commonService.calculateRDConverterValue(anyString(), anyDouble(), anyMap())).thenReturn(new RDResult(0.0, 0.0, 0.0, 0.0));
        when(commonService.calculateOperatingLeaseConverter()).thenReturn(new io.stockvaluation.dto.LeaseResultDTO(0.0, 0.0, 0.0, 0.0));
        when(optionValueService.calculateOptionValue(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OptionValueResultDTO(0.0, 0.0));

        CompanyDTO calibrationCompany = new CompanyDTO();
        calibrationCompany.setEstimatedValuePerShare(100.0);
        lenient().when(valuationOutputService.calculateFinancialData(any(FinancialDataInput.class), any(RDResult.class), any(), anyString(), isNull()))
                .thenReturn(new FinancialDTO());
        lenient().when(valuationOutputService.calculateCompanyData(any(FinancialDTO.class), any(FinancialDataInput.class), any(OptionValueResultDTO.class), any()))
                .thenReturn(calibrationCompany);
    }

    private void stubMonotonicImpliedPath(
            CompanyDataDTO companyData,
            ValuationTemplate template,
            ValuationOutputDTO initial,
            ValuationOutputDTO refined
    ) {
        when(commonService.getCompanyDataFromProvider("AAPL")).thenReturn(companyData);
        when(valuationTemplateService.determineTemplate(isNull(), eq(companyData))).thenReturn(template);
        when(growthAnchorService.getAnchorByYahooIndustry(anyString(), anyString())).thenReturn(Optional.empty());

        when(valuationOutputService.calculateCurrentSalesToCapitalRatio(any(FinancialDataInput.class), any(RDResult.class), any()))
                .thenReturn(1.0);
        when(valuationOutputService.getValuationOutput(eq("AAPL"), any(FinancialDataInput.class), eq(false), eq(template)))
                .thenReturn(initial, refined);

        when(commonService.calculateRDConverterValue(anyString(), anyDouble(), anyMap())).thenReturn(new RDResult(0.0, 0.0, 0.0, 0.0));
        when(commonService.calculateOperatingLeaseConverter()).thenReturn(new io.stockvaluation.dto.LeaseResultDTO(0.0, 0.0, 0.0, 0.0));
        when(optionValueService.calculateOptionValue(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OptionValueResultDTO(0.0, 0.0));

        when(valuationOutputService.calculateFinancialData(any(FinancialDataInput.class), any(RDResult.class), any(), anyString(), any()))
                .thenReturn(new FinancialDTO());

        when(valuationOutputService.calculateCompanyData(any(FinancialDTO.class), any(FinancialDataInput.class), any(OptionValueResultDTO.class), any()))
                .thenAnswer(invocation -> {
                    FinancialDataInput input = invocation.getArgument(1);
                    double cagr = nonNull(input.getCompoundAnnualGrowth2_5());
                    double margin = nonNull(input.getTargetPreTaxOperatingMargin());
                    double salesToCapital = nonNull(input.getSalesToCapitalYears1To5());
                    double wacc = nonNull(input.getInitialCostCapital());
                    double estimate = 50.0 + (2.0 * cagr) + (1.5 * margin) + (0.04 * salesToCapital) - (2.0 * wacc);
                    CompanyDTO company = new CompanyDTO();
                    company.setEstimatedValuePerShare(estimate);
                    return company;
                });
    }

    private static double nonNull(Double value) {
        return value == null ? 0.0 : value;
    }

    private static CompanyDataDTO companyData() {
        BasicInfoDataDTO basic = new BasicInfoDataDTO();
        basic.setTicker("AAPL");
        basic.setCompanyName("Apple Inc");
        basic.setCountryOfIncorporation("United States");
        basic.setIndustryUs("software");
        basic.setIndustryGlobal("technology");
        basic.setCurrency("USD");
        basic.setStockCurrency("USD");

        FinancialDataDTO financial = new FinancialDataDTO();
        financial.setMarginalTaxRate(0.25);
        financial.setResearchAndDevelopmentMap(Map.of("2024", 1_000.0));

        CompanyDriveDataDTO drive = new CompanyDriveDataDTO();
        drive.setRevenueNextYear(0.10);
        drive.setOperatingMarginNextYear(0.20);
        drive.setCompoundAnnualGrowth2_5(0.08);
        drive.setConvergenceYearMargin(0.18);
        drive.setSalesToCapitalYears1To5(2.2);
        drive.setSalesToCapitalYears6To10(2.1);
        drive.setRiskFreeRate(0.04);
        drive.setInitialCostCapital(0.08);
        drive.setTargetPreTaxOperatingMargin(0.22);

        GrowthDto growth = new GrowthDto();

        CompanyDataDTO dto = new CompanyDataDTO();
        dto.setBasicInfoDataDTO(basic);
        dto.setFinancialDataDTO(financial);
        dto.setCompanyDriveDataDTO(drive);
        dto.setGrowthDto(growth);
        return dto;
    }

    private static ValuationTemplate fcffTemplate() {
        ValuationTemplate template = new ValuationTemplate();
        template.setProjectionYears(10);
        template.setArrayLength(12);
        template.setGrowthPattern(GrowthPattern.TWO_STAGE);
        template.setEarningsLevel(EarningsLevel.CURRENT);
        template.setCashflowToDiscount(CashflowType.FCFF);
        template.setModelType(ModelType.DISCOUNTED_CF);
        return template;
    }

    private static ValuationOutputDTO valuationOutput(double marketPrice, double estimatedValue) {
        CompanyDTO company = new CompanyDTO();
        company.setPrice(marketPrice);
        company.setEstimatedValuePerShare(estimatedValue);

        ValuationOutputDTO out = new ValuationOutputDTO();
        out.setCompanyDTO(company);
        return out;
    }

    private static ValuationOutputDTO valuationOutput(Double marketPrice, double estimatedValue) {
        CompanyDTO company = new CompanyDTO();
        company.setPrice(marketPrice);
        company.setEstimatedValuePerShare(estimatedValue);

        ValuationOutputDTO out = new ValuationOutputDTO();
        out.setCompanyDTO(company);
        return out;
    }
}
