package io.stockvaluation.service;

import io.stockvaluation.domain.IndustryAveragesGlobal;
import io.stockvaluation.domain.IndustryAveragesUS;
import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.SegmentResponseDTO;
import io.stockvaluation.dto.SegmentWeightedParameters;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.form.SectorParameterOverride;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.IndustryAveragesUSRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import io.stockvaluation.utils.SegmentParameterContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.stockvaluation.service.GrowthCalculatorService.adjustAnnualGrowth2_5years;
import static io.stockvaluation.utils.Helper.targetOperatingMargin;

@Service
@Slf4j
@RequiredArgsConstructor
public class SegmentWeightedParameterService {

    private final SectorMappingRepository sectorMappingRepository;
    private final IndustryAveragesUSRepository industryAvgUSRepository;
    private final IndustryAveragesGlobalRepository industryAvgGloRepository;
    private final InputStatRepository inputStatRepository;

    /**
     * Validates sector overrides against available segments
     * 
     * @param overrides List of sector overrides
     * @param segments  Segment data containing valid sector names
     * @return List of validated overrides (invalid ones are logged and excluded)
     */
    private List<SectorParameterOverride> validateSectorOverrides(
            List<SectorParameterOverride> overrides,
            SegmentResponseDTO segments) {

        if (overrides == null || overrides.isEmpty()) {
            return new ArrayList<>();
        }

        if (segments == null || segments.getSegments() == null) {
            log.warn("No segment data available for validating {} overrides", overrides.size());
            return new ArrayList<>();
        }

        Set<String> validSectors = segments.getSegments().stream()
                .map(SegmentResponseDTO.Segment::getSector)
                .collect(Collectors.toSet());

        List<SectorParameterOverride> validatedOverrides = new ArrayList<>();

        for (SectorParameterOverride override : overrides) {
            if (!override.isValid()) {
                log.warn("Invalid override structure: {}", override);
                continue;
            }

            // Validate sector name
            if (!validSectors.contains(override.getSectorName())) {
                log.warn("Invalid sector name in override: {} (valid sectors: {})",
                        override.getSectorName(), validSectors);
                continue;
            }

            // Validate value ranges based on parameter type
            switch (override.getParameterType()) {
                case "operating_margin":
                    if ("absolute".equals(override.getAdjustmentType()) &&
                            (Math.abs(override.getValue()) > 100)) {
                        log.warn("Operating margin override out of range: {} (should be between -100 and 100)",
                                override.getValue());
                        continue;
                    }
                    break;

                case "revenue_growth":
                    if ("absolute".equals(override.getAdjustmentType()) &&
                            (override.getValue() < -100 || override.getValue() > 1000)) {
                        log.warn("Revenue growth override seems unrealistic: {}%", override.getValue());
                    }
                    break;

                case "sales_to_capital":
                    if ("absolute".equals(override.getAdjustmentType()) &&
                            (override.getValue() < 0 || override.getValue() > 50)) {
                        log.warn("Sales to capital override seems unrealistic: {}", override.getValue());
                    }
                    break;
            }

            validatedOverrides.add(override);
            log.info("Validated override: {}", override);
        }

        return validatedOverrides;
    }

    /**
     * Applies sector-specific overrides to sector parameters
     * This is called AFTER sector parameters are calculated but BEFORE weighted
     * averaging
     * 
     * @param overrides    List of sector overrides to apply
     * @param sectorParams Sector parameters to modify
     * @param sectorName   Name of the sector being processed
     */
    private void applySectorOverrides(
            List<SectorParameterOverride> overrides,
            SegmentWeightedParameters.SectorParameters sectorParams,
            String sectorName) {

        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        int appliedCount = 0;

        for (SectorParameterOverride override : overrides) {
            if (!override.getSectorName().equalsIgnoreCase(sectorName)) {
                continue;
            }

            switch (override.getParameterType()) {
                case "revenue_growth":
                    applyRevenueGrowthOverride(override, sectorParams);
                    appliedCount++;
                    break;

                case "operating_margin":
                    applyOperatingMarginOverride(override, sectorParams);
                    appliedCount++;
                    break;

                case "sales_to_capital":
                    applySalesToCapitalOverride(override, sectorParams);
                    appliedCount++;
                    break;

                default:
                    log.warn("Unknown parameter type in override: {}", override.getParameterType());
            }
        }

        if (appliedCount > 0) {
            log.info("Applied {} override(s) to sector '{}'", appliedCount, sectorName);
        }
    }

    /**
     * Applies revenue growth override to sector parameters
     * Handles both years 1-5 (compoundAnnualGrowth2_5) and next year growth
     */
    private void applyRevenueGrowthOverride(
            SectorParameterOverride override,
            SegmentWeightedParameters.SectorParameters sectorParams) {

        String timeframe = override.getTimeframe() != null ? override.getTimeframe() : "both";

        // Apply to years 2-5 CAGR
        if ("years_1_to_5".equals(timeframe) || "both".equals(timeframe)) {
            Double currentValue = sectorParams.getCompoundAnnualGrowth2_5();
            Double newValue = override.applyOverride(currentValue);

            log.info("Revenue Growth Override (Years 2-5) for {}: {} → {} ({})",
                    sectorParams.getSectorName(),
                    String.format("%.2f%%", currentValue),
                    String.format("%.2f%%", newValue),
                    override.getAdjustmentType());

            sectorParams.setCompoundAnnualGrowth2_5(newValue);
        }

        // Apply to next year growth (optional - usually we keep this aligned with year
        // 1)
        if ("years_1_to_5".equals(timeframe) || "both".equals(timeframe)) {
            // Also update revenue next year if applying to early years
            Double currentRevenueNext = sectorParams.getRevenueNextYear();
            Double newRevenueNext = override.applyOverride(currentRevenueNext);

            log.info("Revenue Growth Override (Next Year) for {}: {} → {} ({})",
                    sectorParams.getSectorName(),
                    String.format("%.2f%%", currentRevenueNext),
                    String.format("%.2f%%", newRevenueNext),
                    override.getAdjustmentType());

            sectorParams.setRevenueNextYear(newRevenueNext);
        }
    }

    /**
     * Applies operating margin override to sector parameters
     * Handles both target margin and next year margin
     */
    private void applyOperatingMarginOverride(
            SectorParameterOverride override,
            SegmentWeightedParameters.SectorParameters sectorParams) {

        // Apply to target operating margin
        Double currentTargetMargin = sectorParams.getTargetPreTaxOperatingMargin();
        Double newTargetMargin = override.applyOverride(currentTargetMargin);

        log.info("Operating Margin Override (Target) for {}: {} → {} ({})",
                sectorParams.getSectorName(),
                String.format("%.2f%%", currentTargetMargin),
                String.format("%.2f%%", newTargetMargin),
                override.getAdjustmentType());

        sectorParams.setTargetPreTaxOperatingMargin(newTargetMargin);

        // Also apply to next year margin to maintain consistency
        Double currentNextMargin = sectorParams.getOperatingMarginNextYear();
        Double newNextMargin = override.applyOverride(currentNextMargin);

        log.info("Operating Margin Override (Next Year) for {}: {} → {} ({})",
                sectorParams.getSectorName(),
                String.format("%.2f%%", currentNextMargin),
                String.format("%.2f%%", newNextMargin),
                override.getAdjustmentType());

        sectorParams.setOperatingMarginNextYear(newNextMargin);
    }

    /**
     * Applies sales-to-capital override to sector parameters
     * Handles both years 1-5 and years 6-10 timeframes
     */
    private void applySalesToCapitalOverride(
            SectorParameterOverride override,
            SegmentWeightedParameters.SectorParameters sectorParams) {

        String timeframe = override.getTimeframe() != null ? override.getTimeframe() : "both";

        // Apply to years 1-5
        if ("years_1_to_5".equals(timeframe) || "both".equals(timeframe)) {
            Double currentValue1To5 = sectorParams.getSalesToCapitalYears1To5();
            Double newValue1To5 = override.applyOverride(currentValue1To5);

            log.info("Sales-to-Capital Override (Years 1-5) for {}: {} → {} ({})",
                    sectorParams.getSectorName(),
                    String.format("%.2f", currentValue1To5),
                    String.format("%.2f", newValue1To5),
                    override.getAdjustmentType());

            sectorParams.setSalesToCapitalYears1To5(newValue1To5);
        }

        // Apply to years 6-10
        if ("years_6_to_10".equals(timeframe) || "both".equals(timeframe)) {
            Double currentValue6To10 = sectorParams.getSalesToCapitalYears6To10();
            Double newValue6To10 = override.applyOverride(currentValue6To10);

            log.info("Sales-to-Capital Override (Years 6-10) for {}: {} → {} ({})",
                    sectorParams.getSectorName(),
                    String.format("%.2f", currentValue6To10),
                    String.format("%.2f", newValue6To10),
                    override.getAdjustmentType());

            sectorParams.setSalesToCapitalYears6To10(newValue6To10);
        }
    }

    /**
     * Apply segment-based weighted parameters to FinancialDataInput
     * This method calculates weighted averages for all key valuation parameters
     * based on segment revenue shares
     * Following the EXACT same logic as lines 443-585 for consistency
     */
    public void applySegmentWeightedParameters(FinancialDataInput financialDataInput, CompanyDataDTO companyDataDTO,
            List<String> adjustedParameters,
            double baselineRiskFreeRate) {
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            log.info("No multi-segment data, using company-level parameters");
            return;
        }

        List<SegmentResponseDTO.Segment> segments = financialDataInput.getSegments().getSegments();
        log.info("Calculating segment-weighted parameters for {} segments", segments.size());
        Set<String> adjustedParameterSet = adjustedParameters == null ? Set.of()
                : adjustedParameters.stream()
                        .filter(param -> param != null && !param.isBlank())
                        .collect(Collectors.toSet());

        // Validate sector overrides
        List<SectorParameterOverride> validatedOverrides = validateSectorOverrides(
                financialDataInput.getSectorOverrides(),
                financialDataInput.getSegments());

        if (!validatedOverrides.isEmpty()) {
            log.info("Will apply {} validated sector overrides", validatedOverrides.size());
        }

        // First pass: identify segments with missing sector mappings and redistribute
        // their revenue share
        double missingMappingRevenueShare = 0.0;
        int validSegmentCount = 0;

        for (SegmentResponseDTO.Segment segment : segments) {
            SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(segment.getSector());
            if (sectorMapping == null) {
                log.warn("Sector mapping not found for {}, will redistribute its revenue share of {}",
                        segment.getSector(), segment.getRevenueShare());
                missingMappingRevenueShare += segment.getRevenueShare();
            } else {
                validSegmentCount++;
            }
        }

        // Calculate redistribution amount per valid segment
        double redistributionPerSegment = validSegmentCount > 0 ? missingMappingRevenueShare / validSegmentCount : 0.0;

        if (missingMappingRevenueShare > 0) {
            log.info("Redistributing {} revenue share equally among {} valid segments ({}% each)",
                    missingMappingRevenueShare, validSegmentCount, redistributionPerSegment);
        }

        double weightedRevGrowthNext = 0.0;
        double weightedRevGrowth2_5 = 0.0;
        double weightedTargetMargin = 0.0;
        double weightedSalesToCapital1To5 = 0.0;
        double weightedSalesToCapital6To10 = 0.0;
        double weightedCostOfCapital = 0.0;

        String country = companyDataDTO.getBasicInfoDataDTO().getCountryOfIncorporation();
        boolean isUS = country != null && country.equalsIgnoreCase("United States");
        double riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate();
        if (financialDataInput.getRiskFreeRate() != null) {
            riskFreeRate = financialDataInput.getRiskFreeRate() / 100.0;
        }

        // IMPORTANT:
        // Use FinancialDataInput as the source of truth so caller overrides (valuation-agent
        // or UI) remain effective even in segment-aware runs.
        // CompanyDataDTO is used only as fallback.
        Double revenueGrowthNextPct = coalesce(
                financialDataInput.getRevenueNextYear(),
                asPercent(companyDataDTO.getCompanyDriveDataDTO().getRevenueNextYear()));
        Double operatingMarginNextYearPct = coalesce(
                financialDataInput.getOperatingMarginNextYear(),
                asPercent(companyDataDTO.getCompanyDriveDataDTO().getOperatingMarginNextYear()));
        Double targetPreTaxOperatingMargin = coalesce(
                financialDataInput.getTargetPreTaxOperatingMargin(),
                asPercent(companyDataDTO.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin()));
        Double companyRevGrowth2_5 = coalesce(
                financialDataInput.getCompoundAnnualGrowth2_5(),
                asPercent(companyDataDTO.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5()));
        Double salesToCapitalYears1To5 = coalesce(
                financialDataInput.getSalesToCapitalYears1To5(),
                asSalesToCapitalRatio(companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears1To5()));
        Double salesToCapitalYears6To10 = coalesce(
                financialDataInput.getSalesToCapitalYears6To10(),
                asSalesToCapitalRatio(companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears6To10()));

        // Growth helper uses decimal form for next-year growth/margin (e.g., 0.10 not 10.0).
        Double revenueGrowthNext = revenueGrowthNextPct / 100.0;
        Double operatingMarginNextYear = operatingMarginNextYearPct / 100.0;

        if (!adjustedParameterSet.isEmpty()) {
            log.debug("Segment-weighted run received adjusted parameter hints: {}", adjustedParameterSet);
        }

            for (SegmentResponseDTO.Segment segment : segments) {
                Double revenueShare = segment.getRevenueShare();
                if (revenueShare == null || revenueShare == 0) {
                    continue;
                }

                // Get sector mapping for this segment
                SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(segment.getSector());
                if (sectorMapping == null) {
                    // Skip segments with missing mappings (their revenue was redistributed)
                    log.info("Skipping segment {} (no mapping)", segment.getSector());
                    continue;
                }

                // Add redistributed revenue share to valid segments
                revenueShare += redistributionPerSegment;

                String industryName = sectorMapping.getIndustryAsPerExcel();

                // Get industry averages for this sector
                IndustryAveragesUS industryUS = null;
                IndustryAveragesGlobal industryGlobal = null;
                Double avgPreTaxOperatingMargin = 0.0;
                Optional<InputStatDistribution> inputStatDist = inputStatRepository
                        .findFirstByIndustryGroupOrderByIdAsc(industryName);

                if (isUS) {
                    industryUS = industryAvgUSRepository.findByIndustryName(industryName);
                    if (industryUS != null) {
                        avgPreTaxOperatingMargin = industryUS.getPreTaxOperatingMargin();
                    }
                } else {
                    industryGlobal = industryAvgGloRepository.findByIndustryName(industryName);
                    if (industryGlobal != null) {
                        avgPreTaxOperatingMargin = industryGlobal.getPreTaxOperatingMargin();
                    }
                }

                // Calculate segment-specific revenue growth (Years 2-5)
                // Use revenueGrowthRateThirdQuartile if available, matching the logic from
                // lines 508-523
                // Take MAX of calculated vs company growth to avoid underestimating
                Double segmentRevGrowth2_5Calculated;
                if (industryUS != null) {
                    segmentRevGrowth2_5Calculated = adjustAnnualGrowth2_5years(
                            revenueGrowthNext,
                            industryUS.getAnnualAverageRevenueGrowth() / 100,
                            inputStatDist) * 100; // Convert decimal to percentage
                } else if (industryGlobal != null) {
                    segmentRevGrowth2_5Calculated = adjustAnnualGrowth2_5years(
                            revenueGrowthNext,
                            industryGlobal.getAnnualAverageRevenueGrowth() / 100,
                            inputStatDist) * 100; // Convert decimal to percentage
                } else {
                    segmentRevGrowth2_5Calculated = companyRevGrowth2_5;
                }

                // Take maximum of calculated sector growth vs company-level growth
                Double segmentRevGrowth2_5 = Math.max(segmentRevGrowth2_5Calculated, companyRevGrowth2_5);

                // Calculate target operating margin for sector (matching lines 462-473)
                Double segmentTargetMargin;
                if (inputStatDist.isPresent()) {
                    segmentTargetMargin = convertPercentage(
                            targetOperatingMargin(
                                    inputStatDist.get().getPreTaxOperatingMarginFirstQuartile(),
                                    inputStatDist.get().getPreTaxOperatingMarginMedian(),
                                    inputStatDist.get().getPreTaxOperatingMarginThirdQuartile(),
                                    operatingMarginNextYear * 100,
                                    avgPreTaxOperatingMargin));
                    segmentTargetMargin = Math.max(segmentTargetMargin, targetPreTaxOperatingMargin / 100);
                } else {
                    segmentTargetMargin = targetPreTaxOperatingMargin / 100;
                }

                // Get sales to capital ratio (matching lines 497, 571-574)
                Double segmentSalesToCapital1To5;
                Double segmentSalesToCapital6To10;

                if (inputStatDist.isPresent() && inputStatDist.get().getSalesToInvestedCapitalThirdQuartile() > 0) {
                    // Use third quartile from InputStatDistribution (first phase)
                    segmentSalesToCapital1To5 = reAdjustSalesToCapitalFirstPhases(
                            inputStatDist.get().getSalesToInvestedCapitalThirdQuartile(),
                            salesToCapitalYears1To5);
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryUS != null) {
                    segmentSalesToCapital1To5 = reAdjustSalesToCapitalFirstPhases(
                            null,
                            industryUS.getSalesToCapital());
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryGlobal != null) {
                    segmentSalesToCapital1To5 = reAdjustSalesToCapitalFirstPhases(
                            null,
                            industryGlobal.getSalesToCapital());
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else {
                    segmentSalesToCapital1To5 = salesToCapitalYears1To5;
                }

                // Second phase sales to capital (matching line 587)
                if (inputStatDist.isPresent() && inputStatDist.get().getSalesToInvestedCapitalThirdQuartile() > 0) {
                    // Use third quartile from InputStatDistribution (first phase)
                    segmentSalesToCapital6To10 = reAdjustSalesToCapitalFirstPhases(
                            salesToCapitalYears1To5,
                            inputStatDist.get().getSalesToInvestedCapitalThirdQuartile());
                } else if (industryUS != null) {
                    segmentSalesToCapital6To10 = reAdjustSalesToCapitalFirstPhases(
                            segmentSalesToCapital1To5,
                            industryUS.getSalesToCapital());
                } else if (industryGlobal != null) {
                    segmentSalesToCapital6To10 = reAdjustSalesToCapitalFirstPhases(
                            segmentSalesToCapital1To5,
                            industryGlobal.getSalesToCapital());
                } else {
                    segmentSalesToCapital6To10 = salesToCapitalYears6To10;
                }

                // Calculate cost of capital for this sector
                // Following the pattern from
                // CostOfCapitalService.calculateWeightedCostOfCapitalUS/Global
                Double segmentCostOfCapital;
                if (industryUS != null) {
                    segmentCostOfCapital = industryUS.getCostOfCapital();
                } else if (industryGlobal != null) {
                    segmentCostOfCapital = industryGlobal.getCostOfCapital();
                } else {
                    segmentCostOfCapital = companyDataDTO.getCompanyDriveDataDTO().getInitialCostCapital();
                }

                // Add weighted contributions
                weightedRevGrowthNext += revenueGrowthNext * revenueShare;
                weightedRevGrowth2_5 += segmentRevGrowth2_5 * revenueShare;
                weightedTargetMargin += segmentTargetMargin * 100 * revenueShare; // Convert back to percentage for
                                                                                  // storage
                weightedSalesToCapital1To5 += segmentSalesToCapital1To5 * revenueShare;
                weightedSalesToCapital6To10 += segmentSalesToCapital6To10 * revenueShare;
                weightedCostOfCapital += segmentCostOfCapital * revenueShare;

                log.error(
                        "Segment {}: industry={}, revGrowth2_5={} (calculated={}, company={}, using max), targetMargin={}, sales1-5={}, sales6-10={}, costOfCap={}, adjustedRevenueShare={}",
                        segment.getSector(), industryName, segmentRevGrowth2_5, segmentRevGrowth2_5Calculated,
                        companyRevGrowth2_5,
                        segmentTargetMargin * 100, segmentSalesToCapital1To5, segmentSalesToCapital6To10,
                        segmentCostOfCapital, revenueShare);
            }

            // Apply weighted cost of capital adjustment (matching line 563-564)
            weightedCostOfCapital = (weightedCostOfCapital - baselineRiskFreeRate)
                    + riskFreeRate;

            // Store old values for comparison logging
            Double oldRevNext = financialDataInput.getRevenueNextYear();
            Double oldRevGrowth2_5 = financialDataInput.getCompoundAnnualGrowth2_5();
            Double oldTargetMargin = financialDataInput.getTargetPreTaxOperatingMargin();
            Double oldSales1To5 = financialDataInput.getSalesToCapitalYears1To5();
            Double oldSales6To10 = financialDataInput.getSalesToCapitalYears6To10();
            Double oldCostOfCap = financialDataInput.getInitialCostCapital();

            weightedRevGrowthNext = weightedRevGrowthNext * 100;
            weightedCostOfCapital = weightedCostOfCapital * 100;

            // Create thread-safe segment-weighted parameters container
            SegmentWeightedParameters segmentParams = new SegmentWeightedParameters();
            segmentParams.setWeightedRevenueNextYear(weightedRevGrowthNext);
            segmentParams.setWeightedCompoundAnnualGrowth2_5(weightedRevGrowth2_5);
            // CRITICAL FIX: Convert operating margin from decimal to percentage
            segmentParams.setWeightedOperatingMarginNextYear(
                    companyDataDTO.getCompanyDriveDataDTO().getOperatingMarginNextYear() * 100);
            segmentParams.setWeightedTargetPreTaxOperatingMargin(weightedTargetMargin);
            segmentParams.setConvergenceYearMargin(companyDataDTO.getCompanyDriveDataDTO().getConvergenceYearMargin());
            segmentParams.setWeightedSalesToCapitalYears1To5(weightedSalesToCapital1To5);
            segmentParams.setWeightedSalesToCapitalYears6To10(weightedSalesToCapital6To10);
            segmentParams.setWeightedInitialCostCapital(weightedCostOfCapital);
            segmentParams.setRiskFreeRate(riskFreeRate);
            segmentParams.setIndustry(companyDataDTO.getBasicInfoDataDTO().getIndustryUs());
            segmentParams.setSegmentWeighted(true);
            segmentParams.setSegmentCount(segments.size());

            // Calculate and store sector-specific parameters
            for (SegmentResponseDTO.Segment segment : segments) {
                Double revenueShare = segment.getRevenueShare();
                if (revenueShare == null || revenueShare == 0) {
                    continue;
                }

                // Get sector mapping for this segment
                SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(segment.getSector());
                if (sectorMapping == null) {
                    continue;
                }

                // Add redistributed revenue share to valid segments
                revenueShare += redistributionPerSegment;

                String industryName = sectorMapping.getIndustryAsPerExcel();

                // Get industry averages for this sector
                IndustryAveragesUS industryUS = null;
                IndustryAveragesGlobal industryGlobal = null;
                Double avgPreTaxOperatingMargin = 0.0;
                Optional<InputStatDistribution> inputStatDist = inputStatRepository
                        .findFirstByIndustryGroupOrderByIdAsc(industryName);

                if (isUS) {
                    industryUS = industryAvgUSRepository.findByIndustryName(industryName);
                    if (industryUS != null) {
                        avgPreTaxOperatingMargin = industryUS.getPreTaxOperatingMargin();
                    }
                } else {
                    industryGlobal = industryAvgGloRepository.findByIndustryName(industryName);
                    if (industryGlobal != null) {
                        avgPreTaxOperatingMargin = industryGlobal.getPreTaxOperatingMargin();
                    }
                }

                // Create sector-specific parameters
                SegmentWeightedParameters.SectorParameters sectorParams = new SegmentWeightedParameters.SectorParameters();
                sectorParams.setSectorName(segment.getSector());
                sectorParams.setRevenueShare(revenueShare);
                sectorParams.setIndustryAsPerExcel(industryName);

                // Calculate sector-specific revenue growth (Years 2-5)
                Double segmentRevGrowth2_5Calculated;
                if (industryUS != null) {
                    segmentRevGrowth2_5Calculated = adjustAnnualGrowth2_5years(
                            revenueGrowthNext,
                            industryUS.getAnnualAverageRevenueGrowth() / 100,
                            inputStatDist) * 100; // Convert decimal to percentage
                } else if (industryGlobal != null) {
                    segmentRevGrowth2_5Calculated = adjustAnnualGrowth2_5years(
                            revenueGrowthNext,
                            industryGlobal.getAnnualAverageRevenueGrowth() / 100,
                            inputStatDist) * 100; // Convert decimal to percentage
                } else {
                    segmentRevGrowth2_5Calculated = companyRevGrowth2_5;
                }

                // Take maximum of calculated sector growth vs company-level growth
                Double segmentRevGrowth2_5 = Math.max(segmentRevGrowth2_5Calculated, companyRevGrowth2_5);

                // Set sector revenue growth parameters
                // CRITICAL FIX: Convert revenueGrowthNext from decimal to percentage (0.1019 ->
                // 10.19)
                sectorParams.setRevenueNextYear(revenueGrowthNext * 100);
                sectorParams.setCompoundAnnualGrowth2_5(segmentRevGrowth2_5);
                // CRITICAL FIX: Terminal growth rate should converge to risk-free rate for each
                // sector
                sectorParams.setTerminalGrowthRate(riskFreeRate / 100); // Convert to decimal for consistency

                // Calculate target operating margin for sector
                Double segmentTargetMargin;
                if (inputStatDist.isPresent()) {
                    segmentTargetMargin = convertPercentage(
                            targetOperatingMargin(
                                    inputStatDist.get().getPreTaxOperatingMarginFirstQuartile(),
                                    inputStatDist.get().getPreTaxOperatingMarginMedian(),
                                    inputStatDist.get().getPreTaxOperatingMarginThirdQuartile(),
                                    operatingMarginNextYear * 100,
                                    avgPreTaxOperatingMargin));
                    segmentTargetMargin = Math.max(segmentTargetMargin, targetPreTaxOperatingMargin / 100);
                } else {
                    segmentTargetMargin = targetPreTaxOperatingMargin / 100;
                }

                // Set sector operating margin parameters
                // CRITICAL FIX: Convert operatingMarginNextYear from decimal to percentage if
                // needed
                sectorParams.setOperatingMarginNextYear(operatingMarginNextYear * 100); // Convert to percentage
                sectorParams.setTargetPreTaxOperatingMargin(segmentTargetMargin * 100); // Convert back to percentage
                sectorParams
                        .setConvergenceYearMargin(companyDataDTO.getCompanyDriveDataDTO().getConvergenceYearMargin());

                // Get sales to capital ratio for this sector
                Double segmentSalesToCapital1To5;
                Double segmentSalesToCapital6To10;

                if (inputStatDist.isPresent() && inputStatDist.get().getSalesToInvestedCapitalThirdQuartile() > 0) {
                    // Use third quartile from InputStatDistribution (first phase)
                    segmentSalesToCapital1To5 = reAdjustSalesToCapitalFirstPhases(
                            inputStatDist.get().getSalesToInvestedCapitalThirdQuartile(),
                            salesToCapitalYears1To5);
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryUS != null) {
                    segmentSalesToCapital1To5 = reAdjustSalesToCapitalFirstPhases(
                            null,
                            industryUS.getSalesToCapital());
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryGlobal != null) {
                    segmentSalesToCapital1To5 = reAdjustSalesToCapitalFirstPhases(
                            null,
                            industryGlobal.getSalesToCapital());
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else {
                    segmentSalesToCapital1To5 = salesToCapitalYears1To5;
                }

                // Second phase sales to capital
                if (inputStatDist.isPresent() && inputStatDist.get().getSalesToInvestedCapitalThirdQuartile() > 0) {
                    // Use third quartile from InputStatDistribution (first phase)
                    segmentSalesToCapital6To10 = reAdjustSalesToCapitalFirstPhases(
                            salesToCapitalYears1To5,
                            inputStatDist.get().getSalesToInvestedCapitalThirdQuartile());
                } else if (industryUS != null) {
                    segmentSalesToCapital6To10 = reAdjustSalesToCapitalFirstPhases(
                            segmentSalesToCapital1To5,
                            industryUS.getSalesToCapital());
                } else if (industryGlobal != null) {
                    segmentSalesToCapital6To10 = reAdjustSalesToCapitalFirstPhases(
                            segmentSalesToCapital1To5,
                            industryGlobal.getSalesToCapital());
                } else {
                    segmentSalesToCapital6To10 = salesToCapitalYears6To10;
                }

                // Set sector sales to capital parameters
                sectorParams.setSalesToCapitalYears1To5(segmentSalesToCapital1To5);
                sectorParams.setSalesToCapitalYears6To10(segmentSalesToCapital6To10);

                // Calculate cost of capital for this sector
                Double segmentCostOfCapital;
                if (industryUS != null) {
                    segmentCostOfCapital = industryUS.getCostOfCapital();
                } else if (industryGlobal != null) {
                    segmentCostOfCapital = industryGlobal.getCostOfCapital();
                } else {
                    segmentCostOfCapital = companyDataDTO.getCompanyDriveDataDTO().getInitialCostCapital();
                }

                // Apply cost of capital adjustment
                segmentCostOfCapital = (segmentCostOfCapital - baselineRiskFreeRate) + riskFreeRate;
                sectorParams.setInitialCostCapital(segmentCostOfCapital * 100); // Convert to percentage

                // Apply sector-specific overrides BEFORE storing parameters
                // This is critical - overrides must be applied AFTER calculation but BEFORE
                // weighted averaging
                applySectorOverrides(validatedOverrides, sectorParams, segment.getSector());

                // Explicit top-level overrides should remain authoritative in segment-aware runs.
                applyTopLevelOverridesToSector(sectorParams, financialDataInput, adjustedParameterSet, validatedOverrides);

                // Store sector parameters
                segmentParams.setSectorParameters(segment.getSector(), sectorParams);

                log.debug("Created sector parameters for {}: {}", segment.getSector(), sectorParams);
            }

        // Recalculate weighted values from final per-sector parameters (after sector + top-level overrides)
        recomputeWeightedFromSectorParameters(segmentParams);

        // Store in thread-safe context for use in ValuationOutputService
        SegmentParameterContext.setParameters(segmentParams);

        // Apply weighted parameters to FinancialDataInput (for backward compatibility)
        financialDataInput.setRevenueNextYear(segmentParams.getWeightedRevenueNextYear());
        financialDataInput.setCompoundAnnualGrowth2_5(segmentParams.getWeightedCompoundAnnualGrowth2_5());
        financialDataInput.setTargetPreTaxOperatingMargin(segmentParams.getWeightedTargetPreTaxOperatingMargin());
        financialDataInput.setSalesToCapitalYears1To5(segmentParams.getWeightedSalesToCapitalYears1To5());
        financialDataInput.setSalesToCapitalYears6To10(segmentParams.getWeightedSalesToCapitalYears6To10());
        financialDataInput.setInitialCostCapital(segmentParams.getWeightedInitialCostCapital());

    }

    private static Double convertPercentage(Double salesToCapital) {
        if (salesToCapital == null) {
            return 0.0;
        }
        return salesToCapital / 100;
    }

    private static Double coalesce(Double primary, Double fallback) {
        return primary != null ? primary : fallback;
    }

    private static double asPercent(Double value) {
        if (value == null) {
            return 0.0;
        }
        return Math.abs(value) <= 1.0 ? value * 100.0 : value;
    }

    private static double asSalesToCapitalRatio(Double value) {
        if (value == null) {
            return 0.0;
        }
        // Backward compatibility for legacy callers that still send 200 == 2.0x
        if (Math.abs(value) > 50.0) {
            return value / 100.0;
        }
        return value;
    }

    private double reAdjustSalesToCapitalFirstPhases(Double salesToCapitalFirstPhase, Double salesToCapital) {
        if (salesToCapitalFirstPhase != null) {
            return Math.max(salesToCapitalFirstPhase / 2, salesToCapital);
        }
        return salesToCapital;
    }

    private static boolean hasSectorOverrideForParameter(List<SectorParameterOverride> overrides, String sectorName,
            String parameterType) {
        if (overrides == null || overrides.isEmpty()) {
            return false;
        }
        return overrides.stream()
                .anyMatch(override -> sectorName.equalsIgnoreCase(override.getSectorName())
                        && parameterType.equalsIgnoreCase(override.getParameterType()));
    }

    private static void recomputeWeightedFromSectorParameters(SegmentWeightedParameters segmentParams) {
        if (segmentParams == null || !segmentParams.hasSectorParameters()) {
            return;
        }

        double totalShare = 0.0;
        double weightedRevenueNextYear = 0.0;
        double weightedRevenueGrowth2To5 = 0.0;
        double weightedOperatingMarginNextYear = 0.0;
        double weightedTargetMargin = 0.0;
        double weightedSalesToCapital1To5 = 0.0;
        double weightedSalesToCapital6To10 = 0.0;
        double weightedInitialCostCapital = 0.0;

        for (SegmentWeightedParameters.SectorParameters sector : segmentParams.getSectorParameters().values()) {
            if (sector == null || sector.getRevenueShare() == null || sector.getRevenueShare() <= 0) {
                continue;
            }
            double share = sector.getRevenueShare();
            totalShare += share;
            weightedRevenueNextYear += (sector.getRevenueNextYear() == null ? 0.0 : sector.getRevenueNextYear()) * share;
            weightedRevenueGrowth2To5 += (sector.getCompoundAnnualGrowth2_5() == null ? 0.0
                    : sector.getCompoundAnnualGrowth2_5()) * share;
            weightedOperatingMarginNextYear += (sector.getOperatingMarginNextYear() == null ? 0.0
                    : sector.getOperatingMarginNextYear()) * share;
            weightedTargetMargin += (sector.getTargetPreTaxOperatingMargin() == null ? 0.0
                    : sector.getTargetPreTaxOperatingMargin()) * share;
            weightedSalesToCapital1To5 += (sector.getSalesToCapitalYears1To5() == null ? 0.0
                    : sector.getSalesToCapitalYears1To5()) * share;
            weightedSalesToCapital6To10 += (sector.getSalesToCapitalYears6To10() == null ? 0.0
                    : sector.getSalesToCapitalYears6To10()) * share;
            weightedInitialCostCapital += (sector.getInitialCostCapital() == null ? 0.0
                    : sector.getInitialCostCapital()) * share;
        }

        if (totalShare <= 0) {
            return;
        }

        segmentParams.setWeightedRevenueNextYear(weightedRevenueNextYear / totalShare);
        segmentParams.setWeightedCompoundAnnualGrowth2_5(weightedRevenueGrowth2To5 / totalShare);
        segmentParams.setWeightedOperatingMarginNextYear(weightedOperatingMarginNextYear / totalShare);
        segmentParams.setWeightedTargetPreTaxOperatingMargin(weightedTargetMargin / totalShare);
        segmentParams.setWeightedSalesToCapitalYears1To5(weightedSalesToCapital1To5 / totalShare);
        segmentParams.setWeightedSalesToCapitalYears6To10(weightedSalesToCapital6To10 / totalShare);
        segmentParams.setWeightedInitialCostCapital(weightedInitialCostCapital / totalShare);
    }

    private static void applyTopLevelOverridesToSector(
            SegmentWeightedParameters.SectorParameters sectorParams,
            FinancialDataInput financialDataInput,
            Set<String> adjustedParameterSet,
            List<SectorParameterOverride> validatedOverrides) {
        if (sectorParams == null || financialDataInput == null || adjustedParameterSet == null || adjustedParameterSet.isEmpty()) {
            return;
        }

        boolean hasRevenueOverride = hasSectorOverrideForParameter(validatedOverrides, sectorParams.getSectorName(),
                "revenue_growth");
        boolean hasOperatingMarginOverride = hasSectorOverrideForParameter(validatedOverrides,
                sectorParams.getSectorName(), "operating_margin");
        boolean hasSalesToCapitalOverride = hasSectorOverrideForParameter(validatedOverrides, sectorParams.getSectorName(),
                "sales_to_capital");

        if (!hasRevenueOverride) {
            if (adjustedParameterSet.contains("revenueNextYear") && financialDataInput.getRevenueNextYear() != null) {
                sectorParams.setRevenueNextYear(financialDataInput.getRevenueNextYear());
            }
            if (adjustedParameterSet.contains("compoundAnnualGrowth2_5")
                    && financialDataInput.getCompoundAnnualGrowth2_5() != null) {
                sectorParams.setCompoundAnnualGrowth2_5(financialDataInput.getCompoundAnnualGrowth2_5());
            }
        }

        if (!hasOperatingMarginOverride) {
            if (adjustedParameterSet.contains("operatingMarginNextYear")
                    && financialDataInput.getOperatingMarginNextYear() != null) {
                sectorParams.setOperatingMarginNextYear(financialDataInput.getOperatingMarginNextYear());
            }
            if (adjustedParameterSet.contains("targetPreTaxOperatingMargin")
                    && financialDataInput.getTargetPreTaxOperatingMargin() != null) {
                sectorParams.setTargetPreTaxOperatingMargin(financialDataInput.getTargetPreTaxOperatingMargin());
            }
        }

        if (!hasSalesToCapitalOverride) {
            if (adjustedParameterSet.contains("salesToCapitalYears1To5")
                    && financialDataInput.getSalesToCapitalYears1To5() != null) {
                sectorParams.setSalesToCapitalYears1To5(financialDataInput.getSalesToCapitalYears1To5());
            }
            if (adjustedParameterSet.contains("salesToCapitalYears6To10")
                    && financialDataInput.getSalesToCapitalYears6To10() != null) {
                sectorParams.setSalesToCapitalYears6To10(financialDataInput.getSalesToCapitalYears6To10());
            }
        }

        if (adjustedParameterSet.contains("initialCostCapital") && financialDataInput.getInitialCostCapital() != null) {
            sectorParams.setInitialCostCapital(financialDataInput.getInitialCostCapital());
        }
    }
}
