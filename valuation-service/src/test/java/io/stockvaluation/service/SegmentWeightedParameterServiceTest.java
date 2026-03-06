package io.stockvaluation.service;

import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.domain.IndustryAveragesUS;
import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.SegmentWeightedParameters;
import io.stockvaluation.dto.SegmentResponseDTO;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.form.SectorParameterOverride;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.IndustryAveragesUSRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import io.stockvaluation.utils.SegmentParameterContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SegmentWeightedParameterServiceTest {

    @Mock
    private SectorMappingRepository sectorMappingRepository;
    @Mock
    private IndustryAveragesUSRepository industryAvgUSRepository;
    @Mock
    private IndustryAveragesGlobalRepository industryAvgGloRepository;
    @Mock
    private InputStatRepository inputStatRepository;

    @InjectMocks
    private SegmentWeightedParameterService service;

    @AfterEach
    void tearDown() {
        SegmentParameterContext.clear();
    }

    @Test
    void applySegmentWeightedParameters_singleSegment_returnsWithoutChanges() {
        FinancialDataInput input = baselineInput();
        input.setSegments(new SegmentResponseDTO(List.of(
                new SegmentResponseDTO.Segment("software", "tech", List.of("core"), 1.0, 1.0, 0.2)
        )));

        Double revenueBefore = input.getRevenueNextYear();
        Double marginBefore = input.getTargetPreTaxOperatingMargin();
        Double stcBefore = input.getSalesToCapitalYears1To5();

        service.applySegmentWeightedParameters(input, companyData("United States"), List.of(), 0.04);

        assertEquals(revenueBefore, input.getRevenueNextYear());
        assertEquals(marginBefore, input.getTargetPreTaxOperatingMargin());
        assertEquals(stcBefore, input.getSalesToCapitalYears1To5());
        assertNull(SegmentParameterContext.getParameters());
        verifyNoInteractions(sectorMappingRepository, industryAvgUSRepository, industryAvgGloRepository, inputStatRepository);
    }

    @Test
    void applySegmentWeightedParameters_twoSegments_withValidOverride_setsSectorContext() {
        FinancialDataInput input = baselineInput();
        input.setSegments(new SegmentResponseDTO(List.of(
                new SegmentResponseDTO.Segment("sector-a", "tech", List.of("A"), 0.9, 0.6, 0.2),
                new SegmentResponseDTO.Segment("sector-b", "tech", List.of("B"), 0.9, 0.4, 0.2)
        )));
        input.setSectorOverrides(List.of(
                new SectorParameterOverride("sector-a", "operating_margin", 5.0, "relative_additive", "both"),
                new SectorParameterOverride("unknown-sector", "operating_margin", 5.0, "relative_additive", "both")
        ));

        when(sectorMappingRepository.findByIndustryName("sector-a"))
                .thenReturn(new SectorMapping(1L, "yahoo-a", "sector-a", "Industry A"));
        when(sectorMappingRepository.findByIndustryName("sector-b"))
                .thenReturn(new SectorMapping(2L, "yahoo-b", "sector-b", "Industry B"));

        when(inputStatRepository.findFirstByIndustryGroupOrderByIdAsc(anyString())).thenReturn(Optional.empty());
        when(industryAvgUSRepository.findByIndustryName(anyString())).thenReturn(null);

        service.applySegmentWeightedParameters(input, companyData("United States"), List.of(), 0.04);

        SegmentWeightedParameters context = SegmentParameterContext.getParameters();
        assertNotNull(context);
        assertTrue(context.hasSectorParameters());
        assertEquals(2, context.getSectorNames().size());

        SegmentWeightedParameters.SectorParameters a = context.getSectorParameters("sector-a");
        SegmentWeightedParameters.SectorParameters b = context.getSectorParameters("sector-b");

        assertNotNull(a);
        assertNotNull(b);
        assertEquals(23.0, a.getTargetPreTaxOperatingMargin(), 0.0001);
        assertEquals(18.0, b.getTargetPreTaxOperatingMargin(), 0.0001);

        assertEquals(3.0, input.getRevenueNextYear(), 0.0001);
        assertNotNull(input.getInitialCostCapital());
    }

    @Test
    void applySegmentWeightedParameters_missingSectorMapping_redistributesToMappedSegments() {
        FinancialDataInput input = baselineInput();
        input.setSegments(new SegmentResponseDTO(List.of(
                new SegmentResponseDTO.Segment("missing-sector", "tech", List.of("A"), 0.9, 0.7, 0.2),
                new SegmentResponseDTO.Segment("mapped-sector", "tech", List.of("B"), 0.9, 0.3, 0.2)
        )));

        when(sectorMappingRepository.findByIndustryName("missing-sector")).thenReturn(null);
        when(sectorMappingRepository.findByIndustryName("mapped-sector"))
                .thenReturn(new SectorMapping(2L, "yahoo-b", "mapped-sector", "Industry B"));

        service.applySegmentWeightedParameters(input, companyData("United States"), List.of(), 0.04);

        SegmentWeightedParameters context = SegmentParameterContext.getParameters();
        assertNotNull(context);
        assertTrue(context.hasSectorParameters());
        assertNotNull(context.getSectorParameters("mapped-sector"));
        assertNull(context.getSectorParameters("missing-sector"));

        // With 70% missing-share redistributed to the only mapped segment,
        // weighted revenue next-year should reflect full 100% mapped weight.
        assertEquals(3.0, input.getRevenueNextYear(), 0.0001);
    }

    @Test
    void applySegmentWeightedParameters_explicitTopLevelOverrides_remainAuthoritativeInSegmentMode() {
        FinancialDataInput input = baselineInput();
        input.setRevenueNextYear(12.5);
        input.setCompoundAnnualGrowth2_5(12.5);
        input.setOperatingMarginNextYear(36.0);
        input.setTargetPreTaxOperatingMargin(36.0);
        input.setSalesToCapitalYears1To5(3.6);
        input.setSalesToCapitalYears6To10(3.6);
        input.setInitialCostCapital(9.2);
        input.setSegments(new SegmentResponseDTO(List.of(
                new SegmentResponseDTO.Segment("sector-a", "tech", List.of("A"), 0.9, 0.6, 0.2),
                new SegmentResponseDTO.Segment("sector-b", "tech", List.of("B"), 0.9, 0.4, 0.2)
        )));

        when(sectorMappingRepository.findByIndustryName("sector-a"))
                .thenReturn(new SectorMapping(1L, "yahoo-a", "sector-a", "Industry A"));
        when(sectorMappingRepository.findByIndustryName("sector-b"))
                .thenReturn(new SectorMapping(2L, "yahoo-b", "sector-b", "Industry B"));

        IndustryAveragesUS highIndustryAverages = new IndustryAveragesUS();
        highIndustryAverages.setAnnualAverageRevenueGrowth(40.0);
        highIndustryAverages.setPreTaxOperatingMargin(45.0);
        highIndustryAverages.setSalesToCapital(6.0);
        highIndustryAverages.setCostOfCapital(0.11);
        when(industryAvgUSRepository.findByIndustryName(anyString())).thenReturn(highIndustryAverages);
        when(inputStatRepository.findFirstByIndustryGroupOrderByIdAsc(anyString())).thenReturn(Optional.empty());

        service.applySegmentWeightedParameters(
                input,
                companyData("United States"),
                List.of(
                        "revenueNextYear",
                        "compoundAnnualGrowth2_5",
                        "operatingMarginNextYear",
                        "targetPreTaxOperatingMargin",
                        "salesToCapitalYears1To5",
                        "salesToCapitalYears6To10",
                        "initialCostCapital"),
                0.04);

        SegmentWeightedParameters context = SegmentParameterContext.getParameters();
        assertNotNull(context);
        assertEquals(12.5, input.getRevenueNextYear(), 0.0001);
        assertEquals(12.5, input.getCompoundAnnualGrowth2_5(), 0.0001);
        assertEquals(36.0, input.getTargetPreTaxOperatingMargin(), 0.0001);
        assertEquals(3.6, input.getSalesToCapitalYears1To5(), 0.0001);
        assertEquals(3.6, input.getSalesToCapitalYears6To10(), 0.0001);
        assertEquals(9.2, input.getInitialCostCapital(), 0.0001);

        SegmentWeightedParameters.SectorParameters sectorA = context.getSectorParameters("sector-a");
        SegmentWeightedParameters.SectorParameters sectorB = context.getSectorParameters("sector-b");
        assertNotNull(sectorA);
        assertNotNull(sectorB);
        assertEquals(12.5, sectorA.getCompoundAnnualGrowth2_5(), 0.0001);
        assertEquals(12.5, sectorB.getCompoundAnnualGrowth2_5(), 0.0001);
        assertEquals(36.0, sectorA.getTargetPreTaxOperatingMargin(), 0.0001);
        assertEquals(36.0, sectorB.getTargetPreTaxOperatingMargin(), 0.0001);
    }

    private static FinancialDataInput baselineInput() {
        FinancialDataInput input = new FinancialDataInput();
        input.setRevenueNextYear(3.0);
        input.setCompoundAnnualGrowth2_5(4.0);
        input.setOperatingMarginNextYear(20.0);
        input.setTargetPreTaxOperatingMargin(18.0);
        input.setSalesToCapitalYears1To5(2.0);
        input.setSalesToCapitalYears6To10(2.5);
        input.setInitialCostCapital(8.0);
        input.setIndustry("technology");
        return input;
    }

    private static CompanyDataDTO companyData(String country) {
        BasicInfoDataDTO basic = new BasicInfoDataDTO();
        basic.setTicker("AAPL");
        basic.setCountryOfIncorporation(country);
        basic.setIndustryUs("technology");

        CompanyDriveDataDTO drive = new CompanyDriveDataDTO();
        drive.setRevenueNextYear(0.10);
        drive.setOperatingMarginNextYear(0.20);
        drive.setTargetPreTaxOperatingMargin(20.0);
        drive.setCompoundAnnualGrowth2_5(5.0);
        drive.setSalesToCapitalYears1To5(2.5);
        drive.setSalesToCapitalYears6To10(2.5);
        drive.setRiskFreeRate(0.04);
        drive.setInitialCostCapital(0.08);
        drive.setConvergenceYearMargin(0.15);

        CompanyDataDTO dto = new CompanyDataDTO();
        dto.setBasicInfoDataDTO(basic);
        dto.setCompanyDriveDataDTO(drive);
        return dto;
    }
}
