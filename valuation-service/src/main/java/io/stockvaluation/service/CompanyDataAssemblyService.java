package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.domain.CostOfCapital;
import io.stockvaluation.domain.IndustryAveragesGlobal;
import io.stockvaluation.domain.IndustryAveragesUS;
import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.GrowthDto;
import io.stockvaluation.exception.InsufficientFinancialDataException;
import io.stockvaluation.provider.DataProvider;
import io.stockvaluation.provider.FinancialSnapshotProvider;
import io.stockvaluation.provider.PrimaryFilingAvailability;
import io.stockvaluation.provider.PrimaryFilingDataProvider;
import io.stockvaluation.provider.SourceProvenance;
import io.stockvaluation.provider.field.FinancialFieldDefinitionCatalog;
import io.stockvaluation.repository.CostOfCapitalRepository;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.IndustryAveragesUSRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.RiskFreeRateRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import io.stockvaluation.utils.MarketRegionResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.stockvaluation.service.GrowthCalculatorService.adjustAnnualGrowth2_5years;
import static io.stockvaluation.utils.Helper.calculateGrowthRate;
import static io.stockvaluation.utils.Helper.costOfCapital;
import static io.stockvaluation.utils.Helper.companyAnchoredTargetOperatingMargin;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyDataAssemblyService {
    private static final FinancialFieldDefinitionCatalog FIELD_DEFINITIONS =
            FinancialFieldDefinitionCatalog.loadDefault();

    private final CountryEquityRepository countryEquityRepository;
    private final SectorMappingRepository sectorMappingRepository;
    private final DataProvider dataProvider;
    private final RiskFreeRateRepository riskFreeRateRepository;
    private final IndustryAveragesUSRepository industryAvgUSRepository;
    private final IndustryAveragesGlobalRepository industryAvgGloRepository;
    private final InputStatRepository inputStatRepository;
    private final CostOfCapitalRepository costOfCapitalRepository;
    private final CurrencyRateService currencyRateService;
    private final CompanyDataMapper companyDataMapper;
    private final CompanyFinancialIngestionService companyFinancialIngestionService;
    private final ValuationAssumptionProperties valuationAssumptionProperties;
    private final PrimaryFilingDataProvider primaryFilingDataProvider;

    public CompanyDataDTO assembleCompanyData(String ticker) {
        return assembleCompanyData(ticker, false);
    }

    public CompanyDataDTO assembleCompanyData(String ticker, boolean researchedSourcePolicy) {
        Map<String, Object> basicInfoMap = dataProvider.getCompanyInfo(ticker);
        BasicInfoDataDTO basicInfoDataDTO = companyDataMapper.mapBasicInfo(ticker, basicInfoMap);
        SourceSelection sourceSelection = selectSource(ticker, basicInfoDataDTO, researchedSourcePolicy);
        CompanyFinancialIngestionService.FinancialIngestionData ingestionData =
                sourceSelection.useDefaultProviderCall()
                        ? companyFinancialIngestionService.ingest(ticker, basicInfoMap)
                        : companyFinancialIngestionService.ingest(ticker, basicInfoMap, sourceSelection.financialDataProvider());

        SourceProvenance sourceProvenance = ingestionData.sourceProvenance();
        if (sourceSelection.primaryProviderSelected()) {
            CompanyFinancialIngestionService.FinancialIngestionData normalizedIngestion =
                    companyFinancialIngestionService.ingest(ticker, basicInfoMap, dataProvider);
            PrimaryFilingAvailability primaryIngestionAvailability =
                    primaryIngestionAvailability(ingestionData, sourceProvenance);
            if (!primaryIngestionAvailability.available()) {
                ingestionData = normalizedIngestion;
                sourceProvenance = applyPrimarySourceMissingFallback(
                        normalizedIngestion.sourceProvenance(),
                        primaryIngestionAvailability);
            } else {
                sourceProvenance = applyPrimaryFilingReconciliation(
                        sourceProvenance,
                        normalizedIngestion.financialDataDTO(),
                        ingestionData.financialDataDTO(),
                        normalizedIngestion.sourceProvenance(),
                        basicInfoMap);
            }
        } else if (sourceSelection.primaryProviderUnavailable()) {
            sourceProvenance = applyPrimarySourceMissingFallback(sourceProvenance, sourceSelection.primaryAvailability());
        } else if (sourceSelection.nonUsYahooResearchPath()) {
            sourceProvenance = applyNonUsYahooCrossCheckStatus(sourceProvenance);
        }
        FinancialDataDTO financialDataDTO = ingestionData.financialDataDTO();
        financialDataDTO.setSourceProvenance(sourceProvenance);
        List<Double> historicalRevenue = ingestionData.historicalRevenue();
        List<Double> historicalMargins = ingestionData.historicalMargins();
        Double taxProvision = ingestionData.taxProvision();
        Double preTaxIncome = ingestionData.preTaxIncome();

        String country = basicInfoDataDTO.getCountryOfIncorporation();
        Optional<Double> corporateTaxRate = countryEquityRepository.findCorporateTaxRateByCountry(country);

        if (basicInfoMap.get("financialCurrency") != null
                && basicInfoMap.get("currency") != null
                && !basicInfoMap.get("currency").toString()
                .equalsIgnoreCase(basicInfoMap.get("financialCurrency").toString())) {
            try {
                Double convertedPrice = currencyRateService.convertCurrency(
                        basicInfoMap.get("currency").toString(),
                        basicInfoMap.get("financialCurrency").toString(),
                        financialDataDTO.getStockPrice());
                financialDataDTO.setStockPrice(convertedPrice);
                basicInfoDataDTO.setStockCurrency(basicInfoMap.get("financialCurrency").toString());
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Cannot safely value " + ticker + " because market price currency "
                                + basicInfoMap.get("currency")
                                + " differs from financial statement currency "
                                + basicInfoMap.get("financialCurrency")
                                + " and conversion failed.",
                        e);
            }
        }

        Double effectiveTaxRateCal = null;
        if (taxProvision != null && preTaxIncome != null && preTaxIncome != 0.0) {
            double effectiveTaxRateRaw = taxProvision / preTaxIncome;
            if (Double.isFinite(effectiveTaxRateRaw)) {
                effectiveTaxRateCal = new BigDecimal(effectiveTaxRateRaw)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            }
        }

        if (effectiveTaxRateCal == null || effectiveTaxRateCal < 0) {
            financialDataDTO.setEffectiveTaxRate(corporateTaxRate.map(aDouble -> (aDouble / 100)).orElse(0.0));
        } else {
            financialDataDTO.setEffectiveTaxRate(effectiveTaxRateCal);
        }

        double operatingMarginNextYear = financialDataDTO.getOperatingIncomeTTM() / financialDataDTO.getRevenueTTM();
        if (!Double.isFinite(operatingMarginNextYear)) {
            operatingMarginNextYear = financialDataDTO.getOperatingIncomeLTM() / financialDataDTO.getRevenueLTM();
        }
        if (!Double.isFinite(operatingMarginNextYear)) {
            operatingMarginNextYear = 0.0;
        }

        operatingMarginNextYear = new BigDecimal(operatingMarginNextYear)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        financialDataDTO.setMarginalTaxRate(corporateTaxRate.orElse(0.0));

        CompanyDriveDataDTO companyDriveDataDTO = new CompanyDriveDataDTO();
        Map<String, Object> revenueEstimateMapData = dataProvider.getRevenueEstimate(ticker, "yearly");

        Double revenueGrowthNext = null;
        if (revenueEstimateMapData.get("growth") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> growthMap = (Map<String, Object>) revenueEstimateMapData.get("growth");
            revenueGrowthNext = (Double) growthMap.get("+1y");
        }

        if (Objects.isNull(revenueGrowthNext)) {
            revenueGrowthNext = calculateGrowthRate(financialDataDTO.getRevenueTTM(), financialDataDTO.getRevenueLTM());
        } else {
            double revenueGrowthNextRatio = revenueGrowthNext
                    / calculateGrowthRate(financialDataDTO.getRevenueTTM(), financialDataDTO.getRevenueLTM());

            if (revenueGrowthNext > 0.6 && revenueGrowthNextRatio > 10) {
                revenueGrowthNext = calculateGrowthRate(financialDataDTO.getRevenueTTM(), financialDataDTO.getRevenueLTM());
            }
        }

        if (revenueGrowthNext < -50) {
            log.warn("Severe revenue decline detected ({}%). Capping at -50% for distressed company handling.",
                    String.format("%.2f", revenueGrowthNext * 100));
            revenueGrowthNext = -0.50;
        }

        companyDriveDataDTO.setRevenueNextYear(revenueGrowthNext);
        companyDriveDataDTO.setOperatingMarginNextYear(operatingMarginNextYear);
        companyDriveDataDTO.setCompoundAnnualGrowth2_5(
                adjustAnnualGrowth2_5years(revenueGrowthNext, revenueGrowthNext, Optional.empty()));
        companyDriveDataDTO.setRiskFreeRate(resolveRiskFreeRateForCurrency(basicInfoDataDTO.getCurrency()));
        companyDriveDataDTO.setConvergenceYearMargin(valuationAssumptionProperties.getConvergenceYearMargin());

        Double salesToCapital = null;
        Double salesToCapitalFirstPhase = null;
        Double avgPreTaxOperatingMargin;
        IndustryAveragesUS industryAveragesUS = null;
        IndustryAveragesGlobal industryAveragesGlobal = null;
        Optional<InputStatDistribution> optionalInputStatDistribution = Optional.empty();

        SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(basicInfoDataDTO.getIndustryGlobal());
        if (Objects.nonNull(sectorMapping)) {
            if (basicInfoDataDTO.getCountryOfIncorporation().equalsIgnoreCase("United States")) {
                Optional<Double> salesToCapitalOptional = industryAvgUSRepository
                        .findSalesToCapitalByIndustryName(sectorMapping.getIndustryAsPerExcel());
                industryAveragesUS = industryAvgUSRepository.findByIndustryName(sectorMapping.getIndustryAsPerExcel());
                if (industryAveragesUS == null) {
                    throw new InsufficientFinancialDataException(
                            "Industry averages not found for mapped industry: " + sectorMapping.getIndustryAsPerExcel());
                }
                salesToCapital = salesToCapitalOptional.orElse(0.0);
                avgPreTaxOperatingMargin = industryAveragesUS.getPreTaxOperatingMargin();
            } else {
                Optional<Double> salesToCapitalOptional = industryAvgGloRepository
                        .findSalesToCapitalByIndustryName(sectorMapping.getIndustryAsPerExcel());
                industryAveragesGlobal = industryAvgGloRepository
                        .findByIndustryName(sectorMapping.getIndustryAsPerExcel());
                if (industryAveragesGlobal == null) {
                    throw new InsufficientFinancialDataException(
                            "Industry averages not found for mapped industry: " + sectorMapping.getIndustryAsPerExcel());
                }
                salesToCapital = salesToCapitalOptional.orElse(0.0);
                avgPreTaxOperatingMargin = industryAveragesGlobal.getPreTaxOperatingMargin();
            }
            optionalInputStatDistribution = inputStatRepository
                    .findFirstByIndustryGroupOrderByIdAsc(sectorMapping.getIndustryAsPerExcel());
            if (optionalInputStatDistribution.isPresent()) {
                // Company-anchored target margin: anchor on the company's own normalized margin
                // (industry is a guardrail, not a ceiling). Normalize across the two most recent
                // actual periods so a temporary spike is not carried to the terminal year. A
                // multi-year (3-5y) normalized series is the planned upgrade.
                double ttmMarginPercent = operatingMarginNextYear * 100;
                double ltmMarginPercent = financialDataDTO.getOperatingIncomeLTM() / financialDataDTO.getRevenueLTM() * 100;
                double companyNormalizedMargin = (Double.isFinite(ltmMarginPercent) && ltmMarginPercent > 0)
                        ? (ttmMarginPercent + ltmMarginPercent) / 2.0
                        : ttmMarginPercent;
                companyDriveDataDTO.setTargetPreTaxOperatingMargin(
                        convertPercentage(
                                companyAnchoredTargetOperatingMargin(
                                        optionalInputStatDistribution.get().getPreTaxOperatingMarginFirstQuartile(),
                                        optionalInputStatDistribution.get().getPreTaxOperatingMarginMedian(),
                                        optionalInputStatDistribution.get().getPreTaxOperatingMarginThirdQuartile(),
                                        companyNormalizedMargin,
                                        avgPreTaxOperatingMargin)));
                salesToCapitalFirstPhase = optionalInputStatDistribution.get().getSalesToInvestedCapitalThirdQuartile();
            } else {
                log.info("industry dist data not found with sector mapping key :::{} ",
                        sectorMapping.getIndustryAsPerExcel());
                companyDriveDataDTO.setTargetPreTaxOperatingMargin(0.0);
            }
        } else {
            log.info("sector mapping did not found with industry name of yahoo");
        }

        if (Objects.nonNull(industryAveragesUS)) {
            companyDriveDataDTO.setCompoundAnnualGrowth2_5(
                    adjustAnnualGrowth2_5years(
                            revenueGrowthNext,
                            industryAveragesUS.getAnnualAverageRevenueGrowth() / 100,
                            optionalInputStatDistribution));
        }

        if (Objects.nonNull(industryAveragesGlobal)) {
            companyDriveDataDTO.setCompoundAnnualGrowth2_5(
                    adjustAnnualGrowth2_5years(
                            revenueGrowthNext,
                            industryAveragesGlobal.getAnnualAverageRevenueGrowth() / 100,
                            optionalInputStatDistribution));
        }

        double initialCostOfCapital = 0.0;
        IndustryAveragesUS finalIndustryAveragesUS = industryAveragesUS;
        IndustryAveragesGlobal finalIndustryAveragesGlobal = industryAveragesGlobal;
        Optional<InputStatDistribution> finalOptionalInputStatDistribution = optionalInputStatDistribution;
        Optional<CostOfCapital> costOfCapitalOption = costOfCapitalRepository.findCostOfCapitalByRegion(
                MarketRegionResolver.resolveCostOfCapitalRegion(basicInfoDataDTO));
        initialCostOfCapital = costOfCapitalOption.map(ofCapital -> costOfCapital(ofCapital, basicInfoDataDTO,
                financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                finalOptionalInputStatDistribution)).orElse(initialCostOfCapital);

        double costOfCapital = (initialCostOfCapital - resolveBaselineRiskFreeRate())
                + companyDriveDataDTO.getRiskFreeRate();
        companyDriveDataDTO.setInitialCostCapital(costOfCapital);

        salesToCapital = reAdjustSalesToCapitalFirstPhases(null, salesToCapital);

        if (Objects.nonNull(salesToCapitalFirstPhase)) {
            salesToCapitalFirstPhase = reAdjustSalesToCapitalFirstPhases(null, salesToCapitalFirstPhase);
            salesToCapital = reAdjustSalesToCapitalFirstPhases(salesToCapitalFirstPhase, salesToCapital);
            companyDriveDataDTO.setSalesToCapitalYears1To5(salesToCapitalFirstPhase);
        } else {
            salesToCapital = reAdjustSalesToCapitalFirstPhases(null, salesToCapital);
            companyDriveDataDTO.setSalesToCapitalYears1To5(salesToCapital);
        }

        GrowthDto growthDto = null;
        if (!historicalRevenue.isEmpty() && !historicalMargins.isEmpty() && finalOptionalInputStatDistribution.isPresent()) {
            growthDto = GrowthCalculatorService.calculateGrowth(
                    historicalRevenue,
                    historicalMargins,
                    finalOptionalInputStatDistribution.get().getPreTaxOperatingMarginFirstQuartile(),
                    finalOptionalInputStatDistribution.get().getPreTaxOperatingMarginThirdQuartile());
        }

        companyDriveDataDTO.setSalesToCapitalYears6To10(salesToCapital);

        CompanyDataDTO companyDataDTO = new CompanyDataDTO();
        companyDataDTO.setGrowthDto(growthDto);
        companyDataDTO.setBasicInfoDataDTO(basicInfoDataDTO);
        companyDataDTO.setFinancialDataDTO(financialDataDTO);
        companyDataDTO.setCompanyDriveDataDTO(companyDriveDataDTO);

        return companyDataDTO;
    }

    private PrimaryFilingAvailability primaryIngestionAvailability(
            CompanyFinancialIngestionService.FinancialIngestionData ingestionData,
            SourceProvenance sourceProvenance) {
        FinancialDataDTO financials = ingestionData != null ? ingestionData.financialDataDTO() : null;
        List<String> warnings = new ArrayList<>();
        if (financials == null) {
            warnings.add("Primary filing ingestion did not return financial data.");
        } else {
            if (!positive(financials.getRevenueTTM())) {
                warnings.add("Primary filing facts did not include usable current revenue.");
            }
            if (!positive(financials.getRevenueLTM())) {
                warnings.add("Primary filing facts did not include usable prior annual revenue for growth calculation.");
            }
            if (!positive(financials.getNoOfShareOutstanding())) {
                warnings.add("Primary filing facts did not include usable point-in-time shares outstanding.");
            }
        }
        if (warnings.isEmpty()) {
            return PrimaryFilingAvailability.available(primaryProviderName(sourceProvenance));
        }
        return PrimaryFilingAvailability.unavailable(
                "insufficient_facts",
                primaryProviderName(sourceProvenance),
                warnings);
    }

    private String primaryProviderName(SourceProvenance sourceProvenance) {
        if (sourceProvenance != null
                && sourceProvenance.getProvider() != null
                && !sourceProvenance.getProvider().isBlank()) {
            return sourceProvenance.getProvider();
        }
        return primaryFilingDataProvider.getProviderName();
    }

    private static boolean positive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }

    private SourceSelection selectSource(
            String ticker,
            BasicInfoDataDTO basicInfoDataDTO,
            boolean researchedSourcePolicy) {
        if (!researchedSourcePolicy || basicInfoDataDTO == null) {
            return SourceSelection.defaultProvider(dataProvider);
        }
        boolean usCompany = "United States".equalsIgnoreCase(basicInfoDataDTO.getCountryOfIncorporation());
        if (!usCompany) {
            return SourceSelection.nonUsYahoo(dataProvider);
        }
        if (primaryFilingDataProvider.hasPrimaryFinancials(ticker)) {
            return SourceSelection.primaryFiling(primaryFilingDataProvider);
        }
        PrimaryFilingAvailability availability = primaryFilingDataProvider.getPrimaryFinancialsAvailability(ticker);
        return SourceSelection.primaryUnavailable(dataProvider, availability);
    }

    private static SourceProvenance applyPrimarySourceMissingFallback(
            SourceProvenance sourceProvenance,
            PrimaryFilingAvailability primaryAvailability) {
        if (sourceProvenance == null) {
            sourceProvenance = SourceProvenance.yahooNormalized("unknown", null);
        } else {
            sourceProvenance = copySourceProvenance(sourceProvenance);
        }
        sourceProvenance.setSourcePolicyStatus(phase9FallbackStatus(primaryAvailability));
        sourceProvenance.setCrossCheckStatus(normalizeCrossCheckStatus(sourceProvenance.getCrossCheckStatus()));
        List<String> warnings = new ArrayList<>(safeWarnings(sourceProvenance));
        String primaryStatus = primaryAvailability == null || primaryAvailability.status() == null
                ? "unavailable"
                : primaryAvailability.status();
        warnings.add(
                "US researched valuation is using Yahoo-normalized financials because the primary filing provider returned unavailable ("
                        + primaryStatus + ").");
        if (primaryAvailability != null) {
            warnings.addAll(primaryAvailability.warnings());
        }
        sourceProvenance.setWarnings(dedupe(warnings));
        return sourceProvenance;
    }

    private static String phase9FallbackStatus(PrimaryFilingAvailability primaryAvailability) {
        String status = primaryAvailability == null || primaryAvailability.status() == null
                ? "unavailable"
                : primaryAvailability.status();
        return switch (status) {
            case "missing_user_agent" -> "sec_missing_user_agent_yahoo_fallback";
            case "http_error" -> "sec_http_error_yahoo_fallback";
            case "rate_limited" -> "sec_rate_limited_yahoo_fallback";
            case "cik_not_found" -> "sec_cik_not_found_yahoo_fallback";
            case "unsupported_filer" -> "sec_unsupported_filer_yahoo_fallback";
            case "unsupported_taxonomy" -> "sec_unsupported_taxonomy_yahoo_fallback";
            case "insufficient_facts" -> "sec_insufficient_facts_yahoo_fallback";
            case "parse_error" -> "sec_parse_error_yahoo_fallback";
            case "primary_not_applicable", "not_applicable" -> "sec_primary_not_applicable";
            default -> "sec_http_error_yahoo_fallback";
        };
    }

    private static SourceProvenance applyNonUsYahooCrossCheckStatus(SourceProvenance sourceProvenance) {
        if (sourceProvenance == null) {
            sourceProvenance = SourceProvenance.yahooNormalized("unknown", null);
        } else {
            sourceProvenance = copySourceProvenance(sourceProvenance);
        }
        sourceProvenance.setSourcePolicyStatus("primary_adapter_not_supported_yahoo_normalized");
        sourceProvenance.setCrossCheckStatus(normalizeCrossCheckStatus(sourceProvenance.getCrossCheckStatus()));
        List<String> warnings = new ArrayList<>(safeWarnings(sourceProvenance));
        warnings.add(
                "Non-US researched valuation may use Yahoo-normalized financials when company-report cross-check status is explicit.");
        sourceProvenance.setWarnings(dedupe(warnings));
        return sourceProvenance;
    }

    private static SourceProvenance applyPrimaryFilingReconciliation(
            SourceProvenance sourceProvenance,
            FinancialDataDTO normalizedFinancials,
            FinancialDataDTO filingFinancials,
            SourceProvenance normalizedSourceProvenance,
            Map<String, Object> basicInfoMap) {
        if (sourceProvenance == null) {
            sourceProvenance = SourceProvenance.primaryFiling("unknown", null);
        } else {
            sourceProvenance = copySourceProvenance(sourceProvenance);
        }
        sourceProvenance.setSourcePolicyStatus("primary_filing_used");
        sourceProvenance.setCrossCheckStatus("not_applicable");
        List<SourceProvenance.DataQualityWarning> warnings =
                new ArrayList<>(sourceProvenance.getDataQualityWarnings() == null
                        ? List.of()
                        : sourceProvenance.getDataQualityWarnings());
        addSourceMetadataWarnings(
                warnings,
                normalizedSourceProvenance,
                sourceProvenance,
                normalizedFinancials,
                filingFinancials,
                basicInfoMap);
        addMismatchWarning(warnings, "revenue", normalizedFinancials.getRevenueTTM(),
                filingFinancials.getRevenueTTM(), sourceProvenance);
        addMismatchWarning(warnings, "operating_income", normalizedFinancials.getOperatingIncomeTTM(),
                filingFinancials.getOperatingIncomeTTM(), sourceProvenance);
        addMismatchWarning(warnings, "cash_and_short_term_investments", normalizedFinancials.getCashAndMarkablTTM(),
                filingFinancials.getCashAndMarkablTTM(), sourceProvenance);
        addMismatchWarning(warnings, "total_debt", normalizedFinancials.getBookValueDebtTTM(),
                filingFinancials.getBookValueDebtTTM(), sourceProvenance);
        addMismatchWarning(warnings, "shares_outstanding", normalizedFinancials.getNoOfShareOutstanding(),
                filingFinancials.getNoOfShareOutstanding(), sourceProvenance);
        addMismatchWarning(warnings, "research_and_development", latestValue(normalizedFinancials.getResearchAndDevelopmentMap()),
                latestValue(filingFinancials.getResearchAndDevelopmentMap()), sourceProvenance);
        addMismatchWarning(warnings, "stock_based_compensation", normalizedFinancials.getStockBasedCompensationTTM(),
                filingFinancials.getStockBasedCompensationTTM(), sourceProvenance);
        sourceProvenance.setDataQualityWarnings(dedupeDataQualityWarnings(warnings));
        return sourceProvenance;
    }

    private static void addMismatchWarning(
            List<SourceProvenance.DataQualityWarning> warnings,
            String field,
            Double normalizedValue,
            Double filingValue,
            SourceProvenance sourceProvenance) {
        double threshold = reconciliationThreshold(field);
        List<String> warningRules = warningRules(field);
        if (normalizedValue == null || filingValue == null) {
            if (normalizedValue == null
                    && filingValue == null
                    && warningRules.contains("missing_required_field")) {
                warnings.add(new SourceProvenance.DataQualityWarning(
                        field,
                        "missing_required_field",
                        null,
                        null,
                        null,
                        threshold,
                        sourceProvenance.getSourceClass(),
                        sourceProvenance.getSourceDate()));
            }
            Double presentValue = normalizedValue != null ? normalizedValue : filingValue;
            if (presentValue != null
                    && Math.abs(presentValue) > 0.0
                    && warningRules.contains("missing_present_mismatch")) {
                warnings.add(new SourceProvenance.DataQualityWarning(
                        field,
                        "missing_present_mismatch",
                        normalizedValue,
                        filingValue,
                        null,
                        threshold,
                        sourceProvenance.getSourceClass(),
                        sourceProvenance.getSourceDate()));
            }
            return;
        }
        double denominator = Math.max(Math.abs(filingValue), 1.0);
        double differencePct = Math.abs(normalizedValue - filingValue) / denominator;
        if (warningRules.contains("sign_mismatch") && signsDiffer(normalizedValue, filingValue)) {
            warnings.add(new SourceProvenance.DataQualityWarning(
                    field,
                    "sign_mismatch",
                    normalizedValue,
                    filingValue,
                    round4(differencePct),
                    threshold,
                    sourceProvenance.getSourceClass(),
                    sourceProvenance.getSourceDate()));
            return;
        }
        if (differencePct <= threshold) {
            return;
        }
        String status = "material_mismatch";
        if ("total_debt".equals(field)
                && filingValue != null
                && normalizedValue != null
                && filingValue > normalizedValue
                && leaseInclusionExplainsDifference(normalizedValue, filingValue, sourceProvenance)) {
            // SEC debt includes recognized operating/finance lease liabilities while
            // normalized providers report interest-bearing debt only. This is a
            // definitional difference (leases treated as debt), not a data error.
            status = "definitional_difference";
        } else if ("total_debt".equals(field)
                && filingValue != null
                && normalizedValue != null
                && filingValue > normalizedValue
                && isPrimaryFiling(sourceProvenance)) {
            // Primary filing total debt commonly includes finance/operating lease
            // obligations that normalized rows exclude. The field definition records
            // this as a known provider difference; keep it flagged as a definitional
            // difference rather than a hard data error when the gap is material.
            double gapRatio = (filingValue - normalizedValue) / Math.max(Math.abs(normalizedValue), 1.0);
            if (gapRatio >= 0.20) {
                status = "definitional_difference";
            }
        }
        warnings.add(new SourceProvenance.DataQualityWarning(
                field,
                status,
                normalizedValue,
                filingValue,
                round4(differencePct),
                threshold,
                sourceProvenance.getSourceClass(),
                sourceProvenance.getSourceDate()));
    }

    /**
     * Returns true when the filing provenance indicates recognized lease liabilities
     * were included in debt, which explains a normalized-vs-filing gap for total_debt.
     */
    private static boolean leaseInclusionExplainsDifference(
            Double normalizedValue,
            Double filingValue,
            SourceProvenance sourceProvenance) {
        if (sourceProvenance == null || sourceProvenance.getWarnings() == null) {
            return false;
        }
        boolean leaseWarningPresent = sourceProvenance.getWarnings().stream()
                .anyMatch(warning -> warning != null
                        && warning.toLowerCase(java.util.Locale.ROOT).contains("lease liability"));
        if (!leaseWarningPresent) {
            return false;
        }
        // The gap must be at least 20% of normalized debt for lease inclusion to be
        // the plausible explanation; smaller gaps should stay flagged as mismatches.
        return filingValue - normalizedValue >= Math.max(Math.abs(normalizedValue), 1.0) * 0.20;
    }

    private static boolean isPrimaryFiling(SourceProvenance sourceProvenance) {
        return sourceProvenance != null
                && SourceProvenance.PRIMARY_FILING.equals(sourceProvenance.getSourceClass());
    }

    private static void addSourceMetadataWarnings(
            List<SourceProvenance.DataQualityWarning> warnings,
            SourceProvenance normalizedSource,
            SourceProvenance filingSource,
            FinancialDataDTO normalizedFinancials,
            FinancialDataDTO filingFinancials,
            Map<String, Object> basicInfoMap) {
        if (normalizedSource != null
                && filingSource != null
                && differentNonBlank(normalizedSource.getPeriodEnd(), filingSource.getPeriodEnd())) {
            warnings.add(metadataWarning("source_period", "period_mismatch", normalizedSource, filingSource));
        }
        if (isStaleSourceDate(normalizedSource) || isStaleSourceDate(filingSource)) {
            warnings.add(metadataWarning("source_date", "stale_source_date", normalizedSource, filingSource));
        }
        if (basicInfoMap != null
                && differentNonBlank(stringValue(basicInfoMap.get("currency")),
                stringValue(basicInfoMap.get("financialCurrency")))) {
            warnings.add(metadataWarning("currency", "currency_mismatch", normalizedSource, filingSource));
        }
        addAverageVsPointInTimeShareWarning(warnings, normalizedFinancials, filingFinancials, filingSource);
    }

    private static void addAverageVsPointInTimeShareWarning(
            List<SourceProvenance.DataQualityWarning> warnings,
            FinancialDataDTO normalizedFinancials,
            FinancialDataDTO filingFinancials,
            SourceProvenance filingSource) {
        Double averageShares = firstNonNull(
                normalizedFinancials.getDilutedSharesOutstanding(),
                normalizedFinancials.getBasicSharesOutstanding(),
                filingFinancials.getDilutedSharesOutstanding(),
                filingFinancials.getBasicSharesOutstanding());
        Double pointInTimeShares = firstNonNull(
                normalizedFinancials.getNoOfShareOutstanding(),
                filingFinancials.getNoOfShareOutstanding());
        if (averageShares == null || pointInTimeShares == null) {
            return;
        }
        double threshold = reconciliationThreshold("shares_outstanding");
        double differencePct = Math.abs(averageShares - pointInTimeShares) / Math.max(Math.abs(pointInTimeShares), 1.0);
        if (differencePct <= threshold) {
            return;
        }
        warnings.add(new SourceProvenance.DataQualityWarning(
                "shares_outstanding",
                "average_vs_point_in_time_mismatch",
                averageShares,
                pointInTimeShares,
                round4(differencePct),
                threshold,
                filingSource == null ? null : filingSource.getSourceClass(),
                filingSource == null ? null : filingSource.getSourceDate()));
    }

    private static SourceProvenance.DataQualityWarning metadataWarning(
            String field,
            String status,
            SourceProvenance normalizedSource,
            SourceProvenance filingSource) {
        SourceProvenance.DataQualityWarning warning = new SourceProvenance.DataQualityWarning(
                field,
                status,
                null,
                null,
                null,
                null,
                filingSource == null ? null : filingSource.getSourceClass(),
                filingSource == null ? null : filingSource.getSourceDate());
        if (normalizedSource != null) {
            warning.setNormalizedSourceClass(normalizedSource.getSourceClass());
            warning.setNormalizedSourceDate(normalizedSource.getSourceDate());
            warning.setNormalizedPeriodEnd(normalizedSource.getPeriodEnd());
        }
        if (filingSource != null) {
            warning.setFilingSourceClass(filingSource.getSourceClass());
            warning.setFilingSourceDate(filingSource.getSourceDate());
            warning.setFilingPeriodEnd(filingSource.getPeriodEnd());
        }
        return warning;
    }

    private static boolean isStaleSourceDate(SourceProvenance source) {
        if (source == null || source.getSourceDate() == null || source.getSourceDate().isBlank()) {
            return false;
        }
        try {
            return LocalDate.parse(source.getSourceDate()).isBefore(LocalDate.now().minusMonths(18));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean differentNonBlank(String left, String right) {
        return left != null
                && !left.isBlank()
                && right != null
                && !right.isBlank()
                && !left.equalsIgnoreCase(right);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Double firstNonNull(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static double reconciliationThreshold(String field) {
        try {
            return FIELD_DEFINITIONS.definition(field).reconciliationThreshold().relativeDifference();
        } catch (IllegalArgumentException e) {
            return 0.05;
        }
    }

    private static List<String> warningRules(String field) {
        try {
            return FIELD_DEFINITIONS.definition(field).warningRules();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    private static boolean signsDiffer(Double normalizedValue, Double filingValue) {
        return normalizedValue != null
                && filingValue != null
                && normalizedValue != 0.0
                && filingValue != 0.0
                && Math.signum(normalizedValue) != Math.signum(filingValue);
    }

    private static Double latestValue(Map<String, Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByKey().reversed())
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String normalizeCrossCheckStatus(String status) {
        if (status == null
                || status.isBlank()
                || "not_checked_by_service".equals(status)
                || "not_checked".equals(status)) {
            return "company_report_check_pending";
        }
        if ("company_report_checked".equals(status)) {
            return "company_report_cross_checked";
        }
        return status;
    }

    private static List<String> safeWarnings(SourceProvenance sourceProvenance) {
        return sourceProvenance.getWarnings() == null ? List.of() : sourceProvenance.getWarnings();
    }

    private static List<String> dedupe(List<String> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private static SourceProvenance copySourceProvenance(SourceProvenance source) {
        SourceProvenance copy = new SourceProvenance(
                source.getSourceClass(),
                source.getProvider(),
                source.getSourceDate(),
                source.getPeriodEnd(),
                source.getRetrievalStatus(),
                source.getCrossCheckStatus(),
                source.getSourcePolicyStatus(),
                source.getWarnings() == null ? new ArrayList<>() : new ArrayList<>(source.getWarnings()));
        copy.setDataQualityWarnings(source.getDataQualityWarnings() == null
                ? new ArrayList<>()
                : new ArrayList<>(source.getDataQualityWarnings()));
        return copy;
    }

    private static List<SourceProvenance.DataQualityWarning> dedupeDataQualityWarnings(
            List<SourceProvenance.DataQualityWarning> warnings) {
        List<SourceProvenance.DataQualityWarning> deduped = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (SourceProvenance.DataQualityWarning warning : warnings) {
            if (warning == null) {
                continue;
            }
            String key = warning.getField() + "|"
                    + warning.getStatus() + "|"
                    + warning.getNormalizedValue() + "|"
                    + warning.getFilingValue() + "|"
                    + warning.getSourceClass() + "|"
                    + warning.getSourceDate();
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            deduped.add(warning);
        }
        return deduped;
    }

    private static double round4(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private double resolveBaselineRiskFreeRate() {
        String baselineCurrencyCode = valuationAssumptionProperties.getBaselineRiskFreeCurrencyCode();
        if (baselineCurrencyCode == null || baselineCurrencyCode.isBlank()) {
            return valuationAssumptionProperties.getBaselineRiskFreeRate();
        }
        return riskFreeRateRepository.findRiskFreeRateByCurrency(baselineCurrencyCode.toUpperCase(Locale.ROOT))
                .orElse(valuationAssumptionProperties.getBaselineRiskFreeRate());
    }

    private double resolveRiskFreeRateForCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return resolveBaselineRiskFreeRate();
        }
        return riskFreeRateRepository.findRiskFreeRateByCurrency(currencyCode.toUpperCase(Locale.ROOT))
                .orElse(resolveBaselineRiskFreeRate());
    }

    private static Double convertPercentage(Double salesToCapital) {
        if (salesToCapital == null) {
            return 0.0;
        }
        return salesToCapital / 100;
    }

    private double reAdjustSalesToCapitalFirstPhases(Double salesToCapitalFirstPhase, Double salesToCapital) {
        if (Objects.nonNull(salesToCapitalFirstPhase)) {
            return Math.max(salesToCapitalFirstPhase / 2, salesToCapital);
        }
        return salesToCapital;
    }

    private record SourceSelection(
            FinancialSnapshotProvider financialDataProvider,
            boolean useDefaultProviderCall,
            boolean primaryProviderSelected,
            boolean primaryProviderUnavailable,
            boolean nonUsYahooResearchPath,
            PrimaryFilingAvailability primaryAvailability) {

        static SourceSelection defaultProvider(DataProvider provider) {
            return new SourceSelection(provider, true, false, false, false, null);
        }

        static SourceSelection primaryFiling(FinancialSnapshotProvider provider) {
            return new SourceSelection(provider, false, true, false, false,
                    PrimaryFilingAvailability.available(provider.getProviderName()));
        }

        static SourceSelection primaryUnavailable(DataProvider provider, PrimaryFilingAvailability availability) {
            return new SourceSelection(provider, false, false, true, false, availability);
        }

        static SourceSelection nonUsYahoo(DataProvider provider) {
            return new SourceSelection(provider, false, false, false, true, null);
        }
    }
}
