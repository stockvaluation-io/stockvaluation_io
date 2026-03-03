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
import io.stockvaluation.repository.CostOfCapitalRepository;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.IndustryAveragesUSRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.RiskFreeRateRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.stockvaluation.service.GrowthCalculatorService.adjustAnnualGrowth2_5years;
import static io.stockvaluation.utils.Helper.calculateGrowthRate;
import static io.stockvaluation.utils.Helper.costOfCapital;
import static io.stockvaluation.utils.Helper.targetOperatingMargin;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyDataAssemblyService {

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

    public CompanyDataDTO assembleCompanyData(String ticker) {
        Map<String, Object> basicInfoMap = dataProvider.getCompanyInfo(ticker);
        BasicInfoDataDTO basicInfoDataDTO = companyDataMapper.mapBasicInfo(ticker, basicInfoMap);
        CompanyFinancialIngestionService.FinancialIngestionData ingestionData =
                companyFinancialIngestionService.ingest(ticker, basicInfoMap);

        FinancialDataDTO financialDataDTO = ingestionData.financialDataDTO();
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
                log.error("Skip currency fixes: {}", e.getMessage());
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
                companyDriveDataDTO.setTargetPreTaxOperatingMargin(
                        convertPercentage(
                                targetOperatingMargin(
                                        optionalInputStatDistribution.get().getPreTaxOperatingMarginFirstQuartile(),
                                        optionalInputStatDistribution.get().getPreTaxOperatingMarginMedian(),
                                        optionalInputStatDistribution.get().getPreTaxOperatingMarginThirdQuartile(),
                                        operatingMarginNextYear * 100,
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
        String timeZoneFullName = basicInfoDataDTO.getTimeZoneFullName();
        IndustryAveragesUS finalIndustryAveragesUS = industryAveragesUS;
        IndustryAveragesGlobal finalIndustryAveragesGlobal = industryAveragesGlobal;
        Optional<InputStatDistribution> finalOptionalInputStatDistribution = optionalInputStatDistribution;

        if (Objects.nonNull(timeZoneFullName)) {
            if (timeZoneFullName.toLowerCase().contains("europe")) {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("Europe");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            } else if (timeZoneFullName.toLowerCase().contains("tokyo")) {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("Japan");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            } else if (timeZoneFullName.toLowerCase().contains("asia")) {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("Emerging");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            } else if (timeZoneFullName.toLowerCase().contains("america")) {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("US");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            } else {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("Global");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            }
        } else {
            if (basicInfoDataDTO.getCountryOfIncorporation().equalsIgnoreCase("Japan")) {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("Japan");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            } else if (basicInfoDataDTO.getCountryOfIncorporation().equalsIgnoreCase("United States")) {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("US");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            } else {
                Optional<CostOfCapital> costOfCapital = costOfCapitalRepository.findCostOfCapitalByRegion("Global");
                initialCostOfCapital = costOfCapital.map(ofCapital -> (costOfCapital(ofCapital, basicInfoDataDTO,
                        financialDataDTO, finalIndustryAveragesUS, finalIndustryAveragesGlobal,
                        finalOptionalInputStatDistribution))).orElse(initialCostOfCapital);
            }
        }

        double costOfCapital = (initialCostOfCapital - resolveBaselineRiskFreeRate())
                + companyDriveDataDTO.getRiskFreeRate();
        companyDriveDataDTO.setInitialCostCapital(costOfCapital);

        salesToCapital = reAdjustSalesToCapitalFirstPhases(null, salesToCapital);

        if (Objects.nonNull(salesToCapitalFirstPhase)) {
            salesToCapitalFirstPhase = reAdjustSalesToCapitalFirstPhases(null, salesToCapitalFirstPhase);
            salesToCapital = reAdjustSalesToCapitalFirstPhases(salesToCapitalFirstPhase, salesToCapital);
            companyDriveDataDTO.setSalesToCapitalYears1To5(convertPercentage(salesToCapitalFirstPhase));
        } else {
            salesToCapital = reAdjustSalesToCapitalFirstPhases(null, salesToCapital);
            companyDriveDataDTO.setSalesToCapitalYears1To5(convertPercentage(salesToCapital));
        }

        GrowthDto growthDto = null;
        if (!historicalRevenue.isEmpty() && !historicalMargins.isEmpty() && finalOptionalInputStatDistribution.isPresent()) {
            growthDto = GrowthCalculatorService.calculateGrowth(
                    historicalRevenue,
                    historicalMargins,
                    finalOptionalInputStatDistribution.get().getPreTaxOperatingMarginFirstQuartile(),
                    finalOptionalInputStatDistribution.get().getPreTaxOperatingMarginThirdQuartile());
        }

        companyDriveDataDTO.setSalesToCapitalYears6To10(convertPercentage(salesToCapital));

        CompanyDataDTO companyDataDTO = new CompanyDataDTO();
        companyDataDTO.setGrowthDto(growthDto);
        companyDataDTO.setBasicInfoDataDTO(basicInfoDataDTO);
        companyDataDTO.setFinancialDataDTO(financialDataDTO);
        companyDataDTO.setCompanyDriveDataDTO(companyDriveDataDTO);

        return companyDataDTO;
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
}
