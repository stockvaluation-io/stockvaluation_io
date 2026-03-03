package io.stockvaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import io.stockvaluation.config.PasswordUtils;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.constant.YearlyCalculation;
import io.stockvaluation.domain.*;
import io.stockvaluation.dto.*;
import io.stockvaluation.enums.InputDetails;
import io.stockvaluation.exception.BadRequestException;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.form.LoginForm;
import io.stockvaluation.form.SignupForm;
import io.stockvaluation.form.SectorParameterOverride;
import io.stockvaluation.repository.*;
import io.stockvaluation.utils.ResponseGenerator;
import io.stockvaluation.utils.SegmentParameterContext;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.stockvaluation.service.GrowthCalculatorService.adjustAnnualGrowth2_5years;
import static io.stockvaluation.service.SpecialCompanies.reAdjustSalesToCapitalFirstPhases;
import static io.stockvaluation.utils.Helper.*;

@Service
@Slf4j
public class CommonService {

    @Value("${yahoo.base.url}")
    private String yahooApiUrl;

    private static final double PRE_TAX_COST_OF_DEBT = 0.05; // 5% cost of debt

    private static final double COST_OF_CAPITAL_ADJUSTMENT = 4.58; // Only Change this value when you update the data
                                                                   // sheet.

    private static final double CONVERGENCE = 0.05;

    private final DecimalFormat df = new DecimalFormat("0.00");

    Map<String, Object> basicAndFinancialMap = new ConcurrentHashMap<>();

    @Autowired
    CountryEquityRepository countryEquityRepository;

    @Autowired
    SectorMappingRepository sectorMappingRepository;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    RiskFreeRateRepository riskFreeRateRepository;

    @Autowired
    IndustryAvgUSRepository industryAvgUSRepository;

    @Autowired
    IndustryAvgGloRepository industryAvgGloRepository;

    @Autowired
    InputStatRepository inputStatRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RDConvertorRepository rdConvertorRepository;

    @Autowired
    RegionEquityRepository regionEquityRepository;

    @Autowired
    CostOfCapitalRepository costOfCapitalRepository;

    @Autowired
    LargeSpreadRepository largeSpreadRepository;

    @Autowired
    SmallSpreadRepository smallSpreadRepository;

    @Autowired
    FailureRateRepository failureRateRepository;

    @Autowired
    BondRatingRepository bondRatingRepository;

    @Autowired
    CurrencyRateService currencyRateService;

    @Autowired
    InputRepository inputRepository;

    public CompanyDataDTO getCompanyDtaFromYahooApi(String ticker) {

        if (Objects.nonNull(basicAndFinancialMap) && !basicAndFinancialMap.isEmpty()) {
            basicAndFinancialMap.clear();
        }
        Map<String, Object> basicInfoMap = basicInfoYearly(yahooApiUrl + "/info?ticker=" + ticker);
        BasicInfoDataDTO basicInfoDataDTO = new BasicInfoDataDTO();

        if (Objects.nonNull(basicInfoMap) && basicInfoMap.containsKey("trailingPegRatio")) {
            basicInfoMap.remove("trailingPegRatio");
        }

        if (Objects.isNull(basicInfoMap) || basicInfoMap.isEmpty()) {
            throw new BadRequestException("We are Not able to find the company using ticker " + ticker
                    + ". Make sure that you verify the company ticker name on the Yahoo Finance.");
        }

        if (basicInfoMap.get("sectorKey") != null
                && basicInfoMap.get("sectorKey").toString().toLowerCase().contains("financial")) {
            throw new BadRequestException(
                    "We currently do not provide valuations for companies in the financial services sector. However, we are actively developing a valuation module for this sector..");
        }

        basicInfoDataDTO.setTicker(ticker);

        if (basicInfoMap.get("financialCurrency") != null) {
            basicInfoDataDTO.setCurrency(basicInfoMap.get("financialCurrency").toString());
        }
        if (basicInfoMap.get("currency") != null) {
            basicInfoDataDTO.setStockCurrency(basicInfoMap.get("currency").toString());
        }
        if (basicInfoMap.get("longName") != null) {
            basicInfoDataDTO.setCompanyName(basicInfoMap.get("longName").toString());
        }
        if (basicInfoMap.get("country") != null) {
            basicInfoDataDTO.setCountryOfIncorporation(basicInfoMap.get("country").toString());
        }
        if (basicInfoMap.get("industryKey") != null) {
            basicInfoDataDTO.setIndustryUs(basicInfoMap.get("industryKey").toString());
            basicInfoDataDTO.setIndustryGlobal(basicInfoMap.get("industryKey").toString());
        }
        if (basicInfoMap.get("longBusinessSummary") != null) {
            basicInfoDataDTO.setSummary(basicInfoMap.get("longBusinessSummary").toString());
        }
        if (basicInfoMap.get("website") != null) {
            basicInfoDataDTO.setWebsite(basicInfoMap.get("website").toString());
        }
        if (basicInfoMap.get("compensationRisk") != null) {
            basicInfoDataDTO.setCompensationRisk((Integer) basicInfoMap.get("compensationRisk"));
        }
        if (basicInfoMap.get("marketCap") != null) {
            basicInfoDataDTO.setMarketCap(Long.parseLong(basicInfoMap.get("marketCap").toString()));
        }
        if (basicInfoMap.get("heldPercentInstitutions") != null) {
            basicInfoDataDTO.setHeldPercentInstitutions(
                    Double.parseDouble(basicInfoMap.get("heldPercentInstitutions").toString()));
        }
        // if (basicInfoMap.get("heldPercentInsiders") != null) {
        // basicInfoDataDTO.setHeldPercentInsiders((Double)
        // basicInfoMap.get("heldPercentInsiders"));
        // }setFirstTradeDateEpochUtc
        if (basicInfoMap.get("firstTradeDateEpochUtc") != null) {
            basicInfoDataDTO.setFirstTradeDateEpochUtc((Integer) basicInfoMap.get("firstTradeDateEpochUtc"));
        }

        if (basicInfoMap.get("firstTradeDateMilliseconds") != null) {
            Object firstTradeDateObj = basicInfoMap.get("firstTradeDateMilliseconds");

            if (firstTradeDateObj instanceof Long) {
                Long firstTradeDateMillis = (Long) firstTradeDateObj;
                int firstTradeDateEpochUtc = (int) (firstTradeDateMillis / 1000);
                basicInfoDataDTO.setFirstTradeDateEpochUtc(firstTradeDateEpochUtc);
            } else {
                basicInfoDataDTO.setFirstTradeDateEpochUtc(0);
            }
        }

        if (basicInfoMap.get("timeZoneFullName") != null) {
            basicInfoDataDTO.setTimeZoneFullName(basicInfoMap.get("timeZoneFullName").toString());
        }

        if (basicInfoMap.get("beta") != null) {
            basicInfoDataDTO.setBeta((Double) basicInfoMap.get("beta"));
        }

        if (basicInfoMap.get("debtToEquity") != null) {
            basicInfoDataDTO.setDebtToEquity((Double) basicInfoMap.get("debtToEquity"));
        }

        basicInfoDataDTO.setDateOfValuation(LocalDate.now());

        Map<String, Object> incomeStatementQuarterlyMapData = incomeStatementQuarterlyData(
                yahooApiUrl + "/income-stmt?ticker=" + ticker + "&freq=quarterly");

        FinancialDataDTO financialDataDTO = new FinancialDataDTO();
        double totalRevenueTTM = calculateStreamTotal(getDecendingSortedQuaterlyMap(incomeStatementQuarterlyMapData),
                "TotalRevenue");

        double operatingIncomeTTM = calculateStreamTotal(getDecendingSortedQuaterlyMap(incomeStatementQuarterlyMapData),
                "EBIT");

        double specialIncomeCharges = 0.0;

        specialIncomeCharges = calculateStreamTotal(getDecendingSortedQuaterlyMap(incomeStatementQuarterlyMapData),
                "SpecialIncomeCharges");

        operatingIncomeTTM = operatingIncomeTTM + Math.abs(specialIncomeCharges);

        double interestExpenseTTM = calculateStreamTotal(getDecendingSortedQuaterlyMap(incomeStatementQuarterlyMapData),
                "InterestExpense");

        Map<String, Object> incomeStatementYearlyMapData = incomeStatementYearlyData(
                yahooApiUrl + "/income-stmt?ticker=" + ticker + "&freq=yearly");

        financialDataDTO.setResearchAndDevelopmentMap(
                setResearchAndDevelopmentMap(incomeStatementYearlyMapData, incomeStatementQuarterlyMapData));

        List<Double> historicalRevenue = new ArrayList<>();
        List<Double> historicalMargins = new ArrayList<>();

        // Calculate GrowthRateDto
        if (incomeStatementYearlyMapData.size() > 3) {
            Map<String, Object> sortedMap = new TreeMap<>(incomeStatementYearlyMapData);

            for (Map.Entry<String, Object> entry : sortedMap.entrySet()) {
                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                if (data != null) {
                    if (data.get("TotalRevenue") != null && data.get("EBIT") != null) {
                        double rev = (Double) data.get("TotalRevenue");
                        double ebit = (Double) data.get("EBIT");
                        historicalRevenue.add(rev);
                        historicalMargins.add(ebit / rev);
                    }
                }
            }
        }

        Map<String, Object> previousYearIncomeData = findDataByYear(incomeStatementYearlyMapData, "income");
        Double revenueLTM = (Double) previousYearIncomeData.get("TotalRevenue");
        if (revenueLTM == totalRevenueTTM)
            revenueLTM = (Double) findDataByYearAttempt(incomeStatementYearlyMapData, "income").get("TotalRevenue");

        Double operatingIncomeLTM = (Double) previousYearIncomeData.get("EBIT");

        if (previousYearIncomeData.containsKey("SpecialIncomeCharges")
                && previousYearIncomeData.get("SpecialIncomeCharges") != null) {
            Double specialIncomeChargesY = (Double) previousYearIncomeData.get("SpecialIncomeCharges");
            operatingIncomeLTM = operatingIncomeLTM + Math.abs(specialIncomeChargesY);
        }

        Double interestExpenseLTM = (Double) previousYearIncomeData.get("InterestExpense");

        Double taxProvision = 0.0D;

        if (previousYearIncomeData.get("TaxProvision") != null) {
            taxProvision = (Double) previousYearIncomeData.get("TaxProvision");
        }

        Double preTaxIncome = (Double) previousYearIncomeData.get("PretaxIncome");
        Map<String, Object> balanceSheetQuarterly = balanceSheetQuaterlyData(
                yahooApiUrl + "/balance-sheet?ticker=" + ticker + "&freq=quarterly");
        Map<String, Object> quarterlyRecentBalanceSheet = getMostRecentTimeStampMap(balanceSheetQuarterly);
        Double bookValueEquityTTM = (Double) quarterlyRecentBalanceSheet.get("CommonStockEquity");

        Double bookValueOfDebtTTM = 0.0d;

        if (quarterlyRecentBalanceSheet.get("TotalDebt") != null) {
            bookValueOfDebtTTM = (Double) quarterlyRecentBalanceSheet.get("TotalDebt");
        } else if (quarterlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation") != null) {
            bookValueOfDebtTTM = (Double) quarterlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation");
        } else {
            bookValueOfDebtTTM = (Double) quarterlyRecentBalanceSheet
                    .get("TotalNonCurrentLiabilitiesNetMinorityInterest");
        }

        // if (Objects.isNull(bookValueOfDebtTTM))
        // bookValueOfDebtTTM = (Double)
        // quarterlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation");

        Double cashAndMarketableTTM = 0.0D;
        if (quarterlyRecentBalanceSheet.get("CashCashEquivalentsAndShortTermInvestments") != null)
            cashAndMarketableTTM = (Double) quarterlyRecentBalanceSheet
                    .get("CashCashEquivalentsAndShortTermInvestments");

        Double numberOfShareOutStanding = null;
        if (quarterlyRecentBalanceSheet.get("OrdinarySharesNumber") != null)
            numberOfShareOutStanding = (Double) quarterlyRecentBalanceSheet.get("OrdinarySharesNumber");

        Map<String, Object> balanceSheetYearly = balanceSheetYearlyData(
                yahooApiUrl + "/balance-sheet?ticker=" + ticker + "&freq=yearly");
        Map<String, Object> yearlyRecentBalanceSheet = findDataByYear(balanceSheetYearly, null);
        Double bookValueEquityLTM = (Double) yearlyRecentBalanceSheet.get("CommonStockEquity");
        Double bookValueOfDebtLTM = 0.0d;

        if (yearlyRecentBalanceSheet.get("TotalDebt") != null) {
            bookValueOfDebtLTM = (Double) yearlyRecentBalanceSheet.get("TotalDebt");
        } else if (yearlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation") != null) {
            bookValueOfDebtLTM = (Double) yearlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation");
        } else {
            bookValueOfDebtLTM = (Double) yearlyRecentBalanceSheet.get("TotalNonCurrentLiabilitiesNetMinorityInterest");
        }

        // if (Objects.isNull(bookValueOfDebtTTM))
        // bookValueOfDebtTTM = (Double)
        // quarterlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation");

        if (Objects.isNull(numberOfShareOutStanding) && yearlyRecentBalanceSheet.get("OrdinarySharesNumber") != null)
            numberOfShareOutStanding = (Double) yearlyRecentBalanceSheet.get("OrdinarySharesNumber");

        if (Objects.isNull(bookValueOfDebtLTM))
            bookValueOfDebtLTM = (Double) yearlyRecentBalanceSheet.get("LongTermDebtAndCapitalLeaseObligation");

        Double cashAndMarketableLTM = (Double) yearlyRecentBalanceSheet
                .get("CashCashEquivalentsAndShortTermInvestments");

        financialDataDTO.setRevenueTTM(totalRevenueTTM);
        if (totalRevenueTTM == 0) {
            financialDataDTO.setRevenueTTM(revenueLTM);
        }
        financialDataDTO.setRevenueLTM(revenueLTM);
        financialDataDTO.setOperatingIncomeTTM(operatingIncomeTTM);
        if (operatingIncomeTTM == 0) {
            financialDataDTO.setOperatingIncomeTTM(operatingIncomeLTM);
        }
        financialDataDTO.setOperatingIncomeLTM(operatingIncomeLTM);
        financialDataDTO.setInterestExpenseTTM(interestExpenseTTM);
        if (interestExpenseTTM == 0) {
            financialDataDTO.setInterestExpenseTTM(interestExpenseLTM);
        }
        financialDataDTO.setInterestExpenseLTM(interestExpenseLTM);
        financialDataDTO.setBookValueEqualityTTM(bookValueEquityTTM);
        if (bookValueEquityTTM == 0) {
            financialDataDTO.setBookValueEqualityTTM(bookValueEquityLTM);
        }
        financialDataDTO.setBookValueEqualityLTM(bookValueEquityLTM);
        financialDataDTO.setBookValueDebtTTM(bookValueOfDebtTTM);
        if (bookValueOfDebtTTM == 0) {
            financialDataDTO.setBookValueDebtTTM(bookValueOfDebtLTM);
        }
        financialDataDTO.setBookValueDebtLTM(bookValueOfDebtLTM);
        financialDataDTO.setCashAndMarkablTTM(cashAndMarketableTTM);
        if (cashAndMarketableTTM == 0) {
            financialDataDTO.setCashAndMarkablTTM(cashAndMarketableLTM);
        }
        financialDataDTO.setCashAndMarkablLTM(cashAndMarketableLTM);
        financialDataDTO.setNonOperatingAssetTTM(0.0);
        financialDataDTO.setNonOperatingAssetLTM(0.0);

        if (yearlyRecentBalanceSheet.get("MinorityInterest") != null) {
            financialDataDTO.setMinorityInterestTTM((Double) yearlyRecentBalanceSheet.get("MinorityInterest"));
        } else {
            financialDataDTO.setMinorityInterestTTM(0.0);
        }
        financialDataDTO.setMinorityInterestLTM(0.0);
        financialDataDTO.setNoOfShareOutstanding(numberOfShareOutStanding);

        financialDataDTO.setHighestStockPrice((Double) basicInfoMap.get("dayHigh"));
        financialDataDTO.setPreviousDayStockPrice((Double) basicInfoMap.get("previousClose"));
        financialDataDTO.setLowestStockPrice((Double) basicInfoMap.get("dayLow"));
        String country = basicInfoDataDTO.getCountryOfIncorporation();
        financialDataDTO.setStockPrice((Double) basicInfoMap.get("currentPrice"));
        Optional<Double> corporateTaxRate = countryEquityRepository.findCorporateTaxRateByCountry(country);

        if (basicInfoMap.get("financialCurrency") != null && basicInfoMap.get("currency") != null &&
                !basicInfoMap.get("currency").toString()
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

        double effectiveTaxRateCal = new BigDecimal((taxProvision / preTaxIncome))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        if (effectiveTaxRateCal < 0) {
            financialDataDTO.setEffectiveTaxRate(corporateTaxRate.map(aDouble -> (aDouble / 100)).orElse(0.0));
        } else {
            financialDataDTO.setEffectiveTaxRate(effectiveTaxRateCal);
        }

        double operatingMarginNextYear = operatingIncomeTTM / totalRevenueTTM;

        if (Double.isNaN(operatingMarginNextYear)) {
            operatingMarginNextYear = operatingIncomeLTM / revenueLTM;
        }

        operatingMarginNextYear = new BigDecimal(operatingMarginNextYear)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        financialDataDTO.setMarginalTaxRate(corporateTaxRate.orElse(0.0));
        CompanyDriveDataDTO companyDriveDataDTO = new CompanyDriveDataDTO();
        Map<String, Object> revenueEstimateMapData = revenueYearlyData(
                yahooApiUrl + "/revenue-estimate?ticker=" + ticker + "&freq=yearly");

        Double revenueGrowthNext = null;

        if (revenueEstimateMapData.get("growth") != null) {
            Map<String, Object> growthMap = (Map<String, Object>) revenueEstimateMapData.get("growth");
            revenueGrowthNext = (Double) growthMap.get("+1y");
        }

        if (Objects.isNull(revenueGrowthNext)) {
            revenueGrowthNext = calculateGrowthRate(totalRevenueTTM, revenueLTM);
        } else {
            double revenueGrowthNextRatio = revenueGrowthNext / calculateGrowthRate(totalRevenueTTM, revenueLTM);

            // Some issue with yahoo estimates
            if (revenueGrowthNext > 0.6 && revenueGrowthNextRatio > 10) {
                revenueGrowthNext = calculateGrowthRate(totalRevenueTTM, revenueLTM);
            }
        }

        // FIXED: Handle distressed companies with severe revenue decline per
        // Damodaran's methodology
        // Instead of resetting to 0%, cap at -50% to reflect distressed company
        // scenarios
        // Companies declining more than 50% annually need proper distress valuation
        // treatment
        if (revenueGrowthNext < -50) {
            log.warn("Severe revenue decline detected ({}%). Capping at -50% for distressed company handling.",
                    String.format("%.2f", revenueGrowthNext * 100));
            revenueGrowthNext = -0.50; // -50% cap for distressed companies
        }

        companyDriveDataDTO.setRevenueNextYear(revenueGrowthNext);
        companyDriveDataDTO.setOperatingMarginNextYear(operatingMarginNextYear);
        companyDriveDataDTO.setCompoundAnnualGrowth2_5(adjustAnnualGrowth2_5(revenueGrowthNext));

        Optional<Double> riskFreeRateOptional = riskFreeRateRepository
                .findRiskFreeRateByCurrency(basicInfoDataDTO.getCurrency());
        companyDriveDataDTO.setRiskFreeRate(riskFreeRateOptional.orElse(0.0));
        companyDriveDataDTO.setConvergenceYearMargin(CONVERGENCE);

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
                salesToCapital = salesToCapitalOptional.orElse(0.0);
                avgPreTaxOperatingMargin = industryAveragesUS.getPreTaxOperatingMargin();
            } else {
                Optional<Double> salesToCapitalOptional = industryAvgGloRepository
                        .findSalesToCapitalByIndustryName(sectorMapping.getIndustryAsPerExcel());
                industryAveragesGlobal = industryAvgGloRepository
                        .findByIndustryName(sectorMapping.getIndustryAsPerExcel());
                salesToCapital = salesToCapitalOptional.orElse(0.0);
                avgPreTaxOperatingMargin = industryAveragesGlobal.getPreTaxOperatingMargin();
            }
            optionalInputStatDistribution = inputStatRepository
                    .findPreOperatingMarginByIndustryName(sectorMapping.getIndustryAsPerExcel());
            if (optionalInputStatDistribution.isPresent()) {
                if (SpecialCompanies.isSpecialCompanies(basicInfoDataDTO)) {
                    companyDriveDataDTO
                            .setTargetPreTaxOperatingMargin(adjustTargetPreTaxOperatingMargin(operatingMarginNextYear));
                } else {

                    companyDriveDataDTO.setTargetPreTaxOperatingMargin(
                            convertPercentage(
                                    targetOperatingMargin(
                                            optionalInputStatDistribution.get().getPreTaxOperatingMarginFirstQuartile(),
                                            optionalInputStatDistribution.get().getPreTaxOperatingMarginMedian(),
                                            optionalInputStatDistribution.get().getPreTaxOperatingMarginThirdQuartile(),
                                            operatingMarginNextYear * 100,
                                            avgPreTaxOperatingMargin)));
                    /*
                     * if(optionalInputStatDistribution.get().getPreTaxOperatingMarginMedian() < 0)
                     * {
                     * companyDriveDataDTO.setTargetPreTaxOperatingMargin(
                     * convertPercentage(avgPreTaxOperatingMargin)
                     * );
                     * }
                     * else {
                     * companyDriveDataDTO.setTargetPreTaxOperatingMargin(
                     * Math.min(
                     * Math.max(
                     * convertPercentage(
                     * optionalInputStatDistribution.get().getPreTaxOperatingMarginMedian()
                     * ),
                     * convertPercentage(avgPreTaxOperatingMargin)
                     * ),
                     * convertPercentage(
                     * optionalInputStatDistribution.get().getPreTaxOperatingMarginThirdQuartile()
                     * )
                     * )
                     * 
                     * );
                     * }
                     */
                }

                salesToCapitalFirstPhase = optionalInputStatDistribution.get().getSalesToInvestedCapitalThirdQuartile();
            } else {
                log.info("industry dist data not found with sector mapping key :::{} ",
                        sectorMapping.getIndustryAsPerExcel());

                companyDriveDataDTO.setTargetPreTaxOperatingMargin(0.0);
            }
        } else {
            log.info("sector mapping  did not found with industry name of yahoo");
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
        double costOfCapital = (initialCostOfCapital - COST_OF_CAPITAL_ADJUSTMENT)
                + companyDriveDataDTO.getRiskFreeRate();
        companyDriveDataDTO.setInitialCostCapital(costOfCapital);

        salesToCapital = reAdjustSalesToCapitalFirstPhases(basicInfoDataDTO, null, salesToCapital);

        if (Objects.nonNull(salesToCapitalFirstPhase)) {
            salesToCapitalFirstPhase = reAdjustSalesToCapitalFirstPhases(basicInfoDataDTO, null,
                    salesToCapitalFirstPhase);
            salesToCapital = reAdjustSalesToCapitalFirstPhases(basicInfoDataDTO, salesToCapitalFirstPhase,
                    salesToCapital);
            companyDriveDataDTO.setSalesToCapitalYears1To5(convertPercentage(salesToCapitalFirstPhase));
        } else {
            salesToCapital = reAdjustSalesToCapitalFirstPhases(basicInfoDataDTO, null, salesToCapital);
            companyDriveDataDTO.setSalesToCapitalYears1To5(convertPercentage(salesToCapital));
        }

        GrowthDto growthDto = null;
        if (!historicalRevenue.isEmpty() && !historicalMargins.isEmpty()
                && finalOptionalInputStatDistribution.isPresent()) {
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

        // Fetch dividend data for DDM calculations
        try {
            DividendDataDTO dividendDataDTO = fetchDividendData(ticker);
            companyDataDTO.setDividendDataDTO(dividendDataDTO);
            if (dividendDataDTO != null && dividendDataDTO.isDividendPaying()) {
                log.info("Dividend data loaded for {}: yield={}, payout={}",
                        ticker, dividendDataDTO.getDividendYield(), dividendDataDTO.getPayoutRatio());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch dividend data for {}: {}", ticker, e.getMessage());
            // Continue without dividend data - FCFF will still work
        }

        basicAndFinancialMap.put("basicInfoDTO", basicInfoDataDTO);
        basicAndFinancialMap.put("financialDTO", financialDataDTO);
        basicAndFinancialMap.put("ticker", ticker);
        return companyDataDTO;
    }

    public ValuationOutputDTO getStory(ValuationOutputDTO valuationOutputDTO, FinancialDataInput financialDataInput,
            String ticker, boolean addStory) {
        if (addStory) {
            try {
                System.out.println("Fetching story for ------------------------" + ticker);
                System.out.println("Story URL: " + yahooApiUrl + "/analyze?ticker=" + ticker + "&name="
                        + valuationOutputDTO.getCompanyName());
                // story() method will automatically extract auth token from request context
                Map<String, Object> basicInfoMap = story(
                        yahooApiUrl + "/analyze?ticker=" + ticker + "&name=" + valuationOutputDTO.getCompanyName(),
                        new MLInputDTO(financialDataInput, valuationOutputDTO));
                System.out.println("Story data: " + basicInfoMap);

                // Extract valuation IDs from yfinance response (used by frontend for chat
                // context)
                if (basicInfoMap.containsKey("valuation_id")) {
                    Object valId = basicInfoMap.get("valuation_id");
                    if (valId != null) {
                        valuationOutputDTO.setValuationId(valId.toString());
                        log.info("Extracted valuation_id from yfinance: {}", valId);
                    }
                }
                if (basicInfoMap.containsKey("user_valuation_id")) {
                    Object userValId = basicInfoMap.get("user_valuation_id");
                    if (userValId != null) {
                        valuationOutputDTO.setUserValuationId(userValId.toString());
                        log.info("Extracted user_valuation_id from yfinance: {}", userValId);
                    }
                }

                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                NarrativeDTO narrativeDTO = objectMapper.convertValue(basicInfoMap, NarrativeDTO.class);
                valuationOutputDTO.setNarrativeDTO(narrativeDTO);
            } catch (Exception e) {
                System.out.println("Failed to map story data to setNarrativeDTO: " + e.getMessage());
                log.error("Failed to map story data to setNarrativeDTO: {}", e.getMessage());
            }
        }
        return valuationOutputDTO;
    }

    /**
     * Inner class to hold DCF adjustment results including parameters and
     * rationales
     */
    public static class DcfAdjustmentResult {
        private List<String> adjustedParameters;
        private Map<String, String> rationales;

        public DcfAdjustmentResult() {
            this.adjustedParameters = new ArrayList<>();
            this.rationales = new HashMap<>();
        }

        public DcfAdjustmentResult(List<String> adjustedParameters, Map<String, String> rationales) {
            this.adjustedParameters = adjustedParameters;
            this.rationales = rationales;
        }

        public List<String> getAdjustedParameters() {
            return adjustedParameters;
        }

        public void setAdjustedParameters(List<String> adjustedParameters) {
            this.adjustedParameters = adjustedParameters;
        }

        public Map<String, String> getRationales() {
            return rationales;
        }

        public void setRationales(Map<String, String> rationales) {
            this.rationales = rationales;
        }
    }

    public DcfAdjustmentResult analyzeBaseDCFAndApplyAdjustments(ValuationOutputDTO valuationOutputDTO,
            FinancialDataInput financialDataInput, String ticker) {
        List<String> adjustedParameters = new ArrayList<>();
        Map<String, String> rationales = new HashMap<>();
        try {
            log.info("🔍 [DCF_ADJUSTMENTS] Calling Python backend /analyze_default_dcf for ticker: {}", ticker);
            String url = yahooApiUrl + "/analyze_default_dcf?ticker=" + ticker + "&name="
                    + valuationOutputDTO.getCompanyName();
            log.debug("🔍 [DCF_ADJUSTMENTS] Request URL: {}", url);

            // story() method will automatically extract auth token from request context
            Map<String, Object> responseMap = story(
                    url,
                    new MLInputDTO(financialDataInput, valuationOutputDTO));

            if (responseMap == null) {
                log.warn("⚠️ [DCF_ADJUSTMENTS] Python backend returned null response for ticker: {}", ticker);
                return new DcfAdjustmentResult(adjustedParameters, rationales);
            }

            log.info("✅ [DCF_ADJUSTMENTS] Python backend response received. Response keys: {}", responseMap.keySet());

            // Log raw response structure for debugging
            if (responseMap.containsKey("dcf_analysis")) {
                Object dcfAnalysis = responseMap.get("dcf_analysis");
                log.debug("🔍 [DCF_ADJUSTMENTS] dcf_analysis structure: {}", dcfAnalysis);
                if (dcfAnalysis instanceof Map) {
                    Map<?, ?> dcfAnalysisMap = (Map<?, ?>) dcfAnalysis;
                    if (dcfAnalysisMap.containsKey("dcf_adjustment_instructions")) {
                        Object instructions = dcfAnalysisMap.get("dcf_adjustment_instructions");
                        log.info("🔍 [DCF_ADJUSTMENTS] Found dcf_adjustment_instructions: {} (type: {})",
                                instructions, instructions != null ? instructions.getClass().getSimpleName() : "null");
                        if (instructions instanceof List) {
                            log.info("🔍 [DCF_ADJUSTMENTS] Number of instructions: {}",
                                    ((List<?>) instructions).size());
                        }
                    }
                }
            }

            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            BaseDcfAnalysisDTO baseDcfAnalysisDTO = objectMapper.convertValue(responseMap, BaseDcfAnalysisDTO.class);

            log.info("✅ [DCF_ADJUSTMENTS] Mapped to BaseDcfAnalysisDTO. DcfAnalysis: {}",
                    baseDcfAnalysisDTO != null && baseDcfAnalysisDTO.getDcfAnalysis() != null ? "present" : "null");

            if (baseDcfAnalysisDTO == null || baseDcfAnalysisDTO.getDcfAnalysis() == null
                    || baseDcfAnalysisDTO.getDcfAnalysis().getDcfAdjustmentInstructions() == null) {
                log.warn("⚠️ [DCF_ADJUSTMENTS] No adjustment instructions found. " +
                        "baseDcfAnalysisDTO={}, dcfAnalysis={}, instructions={}",
                        baseDcfAnalysisDTO != null,
                        baseDcfAnalysisDTO != null && baseDcfAnalysisDTO.getDcfAnalysis() != null,
                        baseDcfAnalysisDTO != null && baseDcfAnalysisDTO.getDcfAnalysis() != null
                                ? baseDcfAnalysisDTO.getDcfAnalysis().getDcfAdjustmentInstructions() != null
                                : false);
                return new DcfAdjustmentResult(adjustedParameters, rationales);
            }

            List<BaseDcfAnalysisDTO.DcfAdjustmentInstruction> instructions = baseDcfAnalysisDTO.getDcfAnalysis()
                    .getDcfAdjustmentInstructions();
            log.info("📊 [DCF_ADJUSTMENTS] Processing {} adjustment instruction(s)", instructions.size());

            for (BaseDcfAnalysisDTO.DcfAdjustmentInstruction instruction : instructions) {
                if (instruction == null) {
                    log.warn("⚠️ [DCF_ADJUSTMENTS] Skipping null instruction");
                    continue;
                }

                if (instruction.getParameter() == null || instruction.getNewValue() == null) {
                    log.warn("⚠️ [DCF_ADJUSTMENTS] Skipping instruction with missing parameter or newValue. " +
                            "parameter={}, newValue={}", instruction.getParameter(), instruction.getNewValue());
                    continue;
                }

                String parameter = instruction.getParameter().toLowerCase();
                Double newValue = instruction.getNewValue();
                String rationale = instruction.getRationale();

                log.info("🔍 [DCF_ADJUSTMENTS] Processing instruction: parameter={}, newValue={}, rationale={}",
                        parameter, newValue,
                        rationale != null ? rationale.substring(0, Math.min(100, rationale.length())) + "..." : "null");

                switch (parameter) {
                    case "revenue_cagr":
                        // Set Compound Annual Growth (Years 2-5)
                        financialDataInput.setCompoundAnnualGrowth2_5(newValue);
                        log.info("✅ [DCF_ADJUSTMENTS] Applied revenue_cagr adjustment: {} (rationale: {})",
                                newValue,
                                rationale != null ? rationale.substring(0, Math.min(100, rationale.length())) + "..."
                                        : "null");
                        adjustedParameters.add("revenue_cagr");
                        rationales.put("revenue_cagr", rationale);
                        break;
                    case "operating_margin":
                        // Set target pre-tax operating margin
                        financialDataInput.setTargetPreTaxOperatingMargin(newValue);
                        log.info("✅ [DCF_ADJUSTMENTS] Applied operating_margin adjustment: {} (rationale: {})",
                                newValue,
                                rationale != null ? rationale.substring(0, Math.min(100, rationale.length())) + "..."
                                        : "null");
                        adjustedParameters.add("operating_margin");
                        rationales.put("operating_margin", rationale);
                        break;
                    case "wacc":
                        // Set initial cost of capital (expects percent units consistently with existing
                        // code)
                        financialDataInput.setInitialCostCapital(newValue * 100);
                        log.info("✅ [DCF_ADJUSTMENTS] Applied wacc adjustment: {} (rationale: {})",
                                newValue,
                                rationale != null ? rationale.substring(0, Math.min(100, rationale.length())) + "..."
                                        : "null");
                        adjustedParameters.add("wacc");
                        rationales.put("wacc", rationale);
                        break;
                    case "tax_rate":
                        // Apply as an override assumption to tax rate
                        financialDataInput
                                .setOverrideAssumptionTaxRate(new OverrideAssumption(newValue, true, 0D, null));
                        log.info("✅ [DCF_ADJUSTMENTS] Applied tax_rate adjustment: {} (rationale: {})",
                                newValue,
                                rationale != null ? rationale.substring(0, Math.min(100, rationale.length())) + "..."
                                        : "null");
                        adjustedParameters.add("tax_rate");
                        rationales.put("tax_rate", rationale);
                        break;
                    case "terminal_growth":
                        // Apply as an override assumption to perpetual growth
                        financialDataInput
                                .setOverrideAssumptionGrowthRate(new OverrideAssumption(newValue, true, 0D, null));
                        log.info("✅ [DCF_ADJUSTMENTS] Applied terminal_growth adjustment: {} (rationale: {})",
                                newValue,
                                rationale != null ? rationale.substring(0, Math.min(100, rationale.length())) + "..."
                                        : "null");
                        adjustedParameters.add("terminal_growth");
                        rationales.put("terminal_growth", rationale);
                        break;
                    default:
                        // For unmapped parameters (e.g., capex_percent_sales,
                        // working_capital_percent_sales), skip for now
                        log.debug("⚠️ [DCF_ADJUSTMENTS] Skipping unmapped parameter: {} (rationale: {})",
                                parameter,
                                rationale != null ? rationale.substring(0, Math.min(50, rationale.length())) + "..."
                                        : "null");
                        break;
                }
            }

            log.info("📊 [DCF_ADJUSTMENTS] Final result: {} parameter(s) adjusted, {} rationale(s) stored",
                    adjustedParameters.size(), rationales.size());
            log.debug("📊 [DCF_ADJUSTMENTS] Adjusted parameters: {}", adjustedParameters);
            log.debug("📊 [DCF_ADJUSTMENTS] Rationales map: {}", rationales);

        } catch (Exception e) {
            log.error("❌ [DCF_ADJUSTMENTS] Failed to analyze base DCF or apply adjustments for ticker {}: {}",
                    ticker, e.getMessage(), e);
        }
        return new DcfAdjustmentResult(adjustedParameters, rationales);
    }

    public SegmentResposeDTO getSegment(String ticker, String companyName, String industry, String description) {
        try {
            Map<String, Object> basicInfoMap = story(
                    yahooApiUrl + "/segment",
                    new SegmentDTO(ticker, companyName, industry, description));
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            return objectMapper.convertValue(basicInfoMap, SegmentResposeDTO.class);
        } catch (Exception e) {
            log.error("Failed to map story data to getSegment: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Validates sector overrides against available segments
     * 
     * @param overrides List of sector overrides
     * @param segments  Segment data containing valid sector names
     * @return List of validated overrides (invalid ones are logged and excluded)
     */
    private List<SectorParameterOverride> validateSectorOverrides(
            List<SectorParameterOverride> overrides,
            SegmentResposeDTO segments) {

        if (overrides == null || overrides.isEmpty()) {
            return new ArrayList<>();
        }

        if (segments == null || segments.getSegments() == null) {
            log.warn("No segment data available for validating {} overrides", overrides.size());
            return new ArrayList<>();
        }

        Set<String> validSectors = segments.getSegments().stream()
                .map(SegmentResposeDTO.Segment::getSector)
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
            List<String> adjustedParameters) {
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            log.info("No multi-segment data, using company-level parameters");
            return;
        }

        List<SegmentResposeDTO.Segment> segments = financialDataInput.getSegments().getSegments();
        log.info("Calculating segment-weighted parameters for {} segments", segments.size());

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

        for (SegmentResposeDTO.Segment segment : segments) {
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

        } else {
            double weightedRevGrowthNext = 0.0;
            double weightedRevGrowth2_5 = 0.0;
            double weightedTargetMargin = 0.0;
            double weightedSalesToCapital1To5 = 0.0;
            double weightedSalesToCapital6To10 = 0.0;
            double weightedCostOfCapital = 0.0;

            String country = companyDataDTO.getBasicInfoDataDTO().getCountryOfIncorporation();
            boolean isUS = country != null && country.equalsIgnoreCase("United States");

            Double revenueGrowthNext = companyDataDTO.getCompanyDriveDataDTO().getRevenueNextYear();
            Double operatingMarginNextYear = companyDataDTO.getCompanyDriveDataDTO().getOperatingMarginNextYear();
            Double targetPreTaxOperatingMargin = companyDataDTO.getCompanyDriveDataDTO()
                    .getTargetPreTaxOperatingMargin();
            Double companyRevGrowth2_5 = companyDataDTO.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5();
            Double salesToCapitalYears1To5 = companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears1To5();
            Double salesToCapitalYears6To10 = companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears6To10();

            if (!adjustedParameters.isEmpty()) {
                if (adjustedParameters.contains("revenue_cagr")) {
                    companyRevGrowth2_5 = financialDataInput.getCompoundAnnualGrowth2_5();
                }
                if (adjustedParameters.contains("operating_margin")) {
                    targetPreTaxOperatingMargin = financialDataInput.getOperatingMarginNextYear();
                }
            }
            /*
             * Double revenueGrowthNext = financialDataInput.getRevenueNextYear() / 100;
             * Double operatingMarginNextYear =
             * financialDataInput.getOperatingMarginNextYear() / 100;
             * Double targetPreTaxOperatingMargin =
             * financialDataInput.getTargetPreTaxOperatingMargin() / 100;
             * Double salesToCapitalYears1To5 =
             * financialDataInput.getSalesToCapitalYears1To5() / 100;
             * Double salesToCapitalYears6To10 =
             * financialDataInput.getSalesToCapitalYears6To10();
             * Double companyRevGrowth2_5 = financialDataInput.getCompoundAnnualGrowth2_5()
             * / 100 ;
             */

            for (SegmentResposeDTO.Segment segment : segments) {
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
                        .findPreOperatingMarginByIndustryName(industryName);

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
                    segmentSalesToCapital1To5 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    inputStatDist.get().getSalesToInvestedCapitalThirdQuartile(),
                                    salesToCapitalYears1To5)); // Convert back to percentage for storage
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryUS != null) {
                    segmentSalesToCapital1To5 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    null,
                                    industryUS.getSalesToCapital())); // Convert back to percentage for storage
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryGlobal != null) {
                    segmentSalesToCapital1To5 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    null,
                                    industryGlobal.getSalesToCapital())); // Convert back to percentage for storage
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else {
                    segmentSalesToCapital1To5 = salesToCapitalYears1To5;
                }

                // Second phase sales to capital (matching line 587)
                if (inputStatDist.isPresent() && inputStatDist.get().getSalesToInvestedCapitalThirdQuartile() > 0) {
                    // Use third quartile from InputStatDistribution (first phase)
                    segmentSalesToCapital6To10 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    salesToCapitalYears1To5,
                                    inputStatDist.get().getSalesToInvestedCapitalThirdQuartile())); // Convert back to
                                                                                                    // percentage for
                                                                                                    // storage
                } else if (industryUS != null) {
                    segmentSalesToCapital6To10 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    segmentSalesToCapital1To5,
                                    industryUS.getSalesToCapital()));
                } else if (industryGlobal != null) {
                    segmentSalesToCapital6To10 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    segmentSalesToCapital1To5,
                                    industryGlobal.getSalesToCapital()));
                } else {
                    segmentSalesToCapital6To10 = salesToCapitalYears6To10 / 100;
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
                weightedSalesToCapital1To5 += segmentSalesToCapital1To5 * 100 * revenueShare; // Convert back to
                                                                                              // percentage for storage
                weightedSalesToCapital6To10 += segmentSalesToCapital6To10 * 100 * revenueShare; // Convert back to
                                                                                                // percentage for
                                                                                                // storage
                weightedCostOfCapital += segmentCostOfCapital * revenueShare;

                log.error(
                        "Segment {}: industry={}, revGrowth2_5={} (calculated={}, company={}, using max), targetMargin={}, sales1-5={}, sales6-10={}, costOfCap={}, adjustedRevenueShare={}",
                        segment.getSector(), industryName, segmentRevGrowth2_5, segmentRevGrowth2_5Calculated,
                        companyRevGrowth2_5,
                        segmentTargetMargin * 100, segmentSalesToCapital1To5 * 100, segmentSalesToCapital6To10 * 100,
                        segmentCostOfCapital, revenueShare);
            }

            // Apply weighted cost of capital adjustment (matching line 563-564)
            double riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate();
            weightedCostOfCapital = (weightedCostOfCapital - COST_OF_CAPITAL_ADJUSTMENT) + riskFreeRate;

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
            for (SegmentResposeDTO.Segment segment : segments) {
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
                        .findPreOperatingMarginByIndustryName(industryName);

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
                    segmentSalesToCapital1To5 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    inputStatDist.get().getSalesToInvestedCapitalThirdQuartile(),
                                    salesToCapitalYears1To5)); // Convert back to percentage for storage
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryUS != null) {
                    segmentSalesToCapital1To5 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    null,
                                    industryUS.getSalesToCapital())); // Convert back to percentage for storage
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else if (industryGlobal != null) {
                    segmentSalesToCapital1To5 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    null,
                                    industryGlobal.getSalesToCapital())); // Convert back to percentage for storage
                    segmentSalesToCapital1To5 = Math.max(segmentSalesToCapital1To5, salesToCapitalYears1To5);
                } else {
                    segmentSalesToCapital1To5 = salesToCapitalYears1To5;
                }

                // Second phase sales to capital
                if (inputStatDist.isPresent() && inputStatDist.get().getSalesToInvestedCapitalThirdQuartile() > 0) {
                    // Use third quartile from InputStatDistribution (first phase)
                    segmentSalesToCapital6To10 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    salesToCapitalYears1To5,
                                    inputStatDist.get().getSalesToInvestedCapitalThirdQuartile())); // Convert back to
                                                                                                    // percentage for
                                                                                                    // storage
                } else if (industryUS != null) {
                    segmentSalesToCapital6To10 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    segmentSalesToCapital1To5,
                                    industryUS.getSalesToCapital()));
                } else if (industryGlobal != null) {
                    segmentSalesToCapital6To10 = convertPercentage(
                            reAdjustSalesToCapitalFirstPhases(
                                    companyDataDTO.getBasicInfoDataDTO(),
                                    segmentSalesToCapital1To5,
                                    industryGlobal.getSalesToCapital()));
                } else {
                    segmentSalesToCapital6To10 = salesToCapitalYears6To10 / 100;
                }

                // Set sector sales to capital parameters
                sectorParams.setSalesToCapitalYears1To5(segmentSalesToCapital1To5 * 100); // Convert back to percentage
                sectorParams.setSalesToCapitalYears6To10(segmentSalesToCapital6To10 * 100); // Convert back to
                                                                                            // percentage

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
                segmentCostOfCapital = (segmentCostOfCapital - COST_OF_CAPITAL_ADJUSTMENT) + riskFreeRate;
                sectorParams.setInitialCostCapital(segmentCostOfCapital * 100); // Convert to percentage

                // Apply sector-specific overrides BEFORE storing parameters
                // This is critical - overrides must be applied AFTER calculation but BEFORE
                // weighted averaging
                applySectorOverrides(validatedOverrides, sectorParams, segment.getSector());

                // Store sector parameters
                segmentParams.setSectorParameters(segment.getSector(), sectorParams);

                log.debug("Created sector parameters for {}: {}", segment.getSector(), sectorParams);
            }

            // Store in thread-safe context for use in ValuationOutputService
            SegmentParameterContext.setParameters(segmentParams);

            // Apply weighted parameters to FinancialDataInput (for backward compatibility)
            financialDataInput.setRevenueNextYear(weightedRevGrowthNext);
            financialDataInput.setCompoundAnnualGrowth2_5(weightedRevGrowth2_5);
            financialDataInput.setTargetPreTaxOperatingMargin(weightedTargetMargin);
            financialDataInput.setSalesToCapitalYears1To5(weightedSalesToCapital1To5);
            financialDataInput.setSalesToCapitalYears6To10(weightedSalesToCapital6To10);
            financialDataInput.setInitialCostCapital(weightedCostOfCapital);
        }

    }

    private Map<String, Double> setResearchAndDevelopmentMap(Map<String, Object> incomeStatementYearlyMapData,
            Map<String, Object> incomeStatementQuarterlyMapData) {
        Map<String, Double> researchAndDevelopmentMap = new TreeMap<>();
        Map<String, Object> decendingMap = getDecendingSortedQuaterlyMap(incomeStatementYearlyMapData);
        int i = 1;
        for (String key : decendingMap.keySet()) {
            Map<String, Object> objectMap = (Map<String, Object>) decendingMap.get(key);
            Double researchAndDevelopment = (Double) objectMap.get("ResearchAndDevelopment");
            researchAndDevelopmentMap.put("currentR&D" + (-i), researchAndDevelopment);
            i++;
        }
        Map<String, Object> map = getDecendingSortedQuaterlyMap(incomeStatementQuarterlyMapData);
        double current = 0.0;
        for (String key : map.keySet()) {
            Map<String, Object> map1 = (Map<String, Object>) map.get(key);
            current += map1.get("ResearchAndDevelopment") == null ? 0.0 : (Double) map1.get("ResearchAndDevelopment");
        }
        researchAndDevelopmentMap.put("currentR&D-0", current);
        return researchAndDevelopmentMap;
    }

    private Map<String, Object> getMostRecentTimeStampMap(Map<String, Object> map) {
        return map.keySet().stream()
                .map(Long::parseLong)
                .max(Long::compareTo)
                .map(recentTimeStamp -> {
                    Map<String, Object> associatedMap = (Map<String, Object>) map.get(String.valueOf(recentTimeStamp));
                    return Optional.ofNullable(associatedMap)
                            .orElseGet(() -> {
                                log.error("No Map found ::::{} ", recentTimeStamp);
                                return Collections.emptyMap();
                            });
                })
                .orElse(Collections.emptyMap());
    }

    private double calculateStreamTotal(Map<String, Object> map, String key) {
        return map.values().stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .mapToDouble(f -> Optional.ofNullable(f.get(key))
                        .filter(Double.class::isInstance).map(Double.class::cast).orElse(0.0))
                .sum();

    }

    public static Map<String, Object> getDecendingSortedQuaterlyMap(Map<String, Object> map) {
        return map.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Object>comparingByKey().reversed()) // Sort in descending order
                .limit(4) // Take the first 4 entries after sorting
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new // Collect into LinkedHashMap to maintain order
                ));
    }

    public Object getCompanyDetails(InputDetails inputDetails) {
        if (Objects.isNull(basicAndFinancialMap) || basicAndFinancialMap.isEmpty()) {
            throw new BadRequestException("Enter the ticker name in the company search bar.");
        }
        if (InputDetails.INCOME_STATEMENT.equals(inputDetails)) {
            IncomeStatementDTO incomeStatementDTO = new IncomeStatementDTO();
            BasicInfoDataDTO basicInfoDTO = (BasicInfoDataDTO) basicAndFinancialMap.get("basicInfoDTO");
            incomeStatementDTO.setTicker((String) basicAndFinancialMap.get("ticker"));
            incomeStatementDTO.setCompanyName(basicInfoDTO.getCompanyName());
            FinancialDataDTO financialDataDTO = (FinancialDataDTO) basicAndFinancialMap.get("financialDTO");
            incomeStatementDTO.setRevenueTTM(financialDataDTO.getRevenueTTM());
            incomeStatementDTO.setRevenueLTM(financialDataDTO.getRevenueLTM());
            incomeStatementDTO.setStockPrice(financialDataDTO.getStockPrice());
            incomeStatementDTO.setOperatingIncomeTTM(financialDataDTO.getOperatingIncomeTTM());
            incomeStatementDTO.setOperatingIncomeLTM(financialDataDTO.getOperatingIncomeLTM());
            incomeStatementDTO.setEffectiveTaxRate(financialDataDTO.getEffectiveTaxRate());
            incomeStatementDTO.setHighestStockPrice(financialDataDTO.getHighestStockPrice());
            incomeStatementDTO.setLowestStockPrice(financialDataDTO.getLowestStockPrice());
            incomeStatementDTO.setPriceChangeFromLastStock(
                    financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice());
            incomeStatementDTO.setPercentageChangeFromLastStock(
                    (financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice()) * 100
                            / financialDataDTO.getStockPrice());
            incomeStatementDTO.setPriceChangeCurrentStock(
                    financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice());
            incomeStatementDTO.setPercentageChangeCurrentStock(
                    (financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice()) * 100
                            / financialDataDTO.getHighestStockPrice());
            return incomeStatementDTO;
        } else if (InputDetails.INFO.equals(inputDetails)) {
            InfoDTO infoDTO = new InfoDTO();
            FinancialDataDTO financialDataDTO = (FinancialDataDTO) basicAndFinancialMap.get("financialDTO");
            BasicInfoDataDTO basicInfoDTO = (BasicInfoDataDTO) basicAndFinancialMap.get("basicInfoDTO");
            infoDTO.setTicker((String) basicAndFinancialMap.get("ticker"));
            infoDTO.setCompanyName(basicInfoDTO.getCompanyName());
            infoDTO.setDateOfValuation(basicInfoDTO.getDateOfValuation());
            infoDTO.setWebsite(basicInfoDTO.getWebsite());
            infoDTO.setCountryOfIncorporation(basicInfoDTO.getCountryOfIncorporation());
            infoDTO.setIndustryUs(basicInfoDTO.getIndustryUs());
            infoDTO.setIndustryGlobal(basicInfoDTO.getIndustryGlobal());
            infoDTO.setNoOfShareOutstanding(financialDataDTO.getNoOfShareOutstanding());
            infoDTO.setStockPrice(financialDataDTO.getStockPrice());
            infoDTO.setHighestStockPrice(financialDataDTO.getHighestStockPrice());
            infoDTO.setLowestStockPrice(financialDataDTO.getLowestStockPrice());
            infoDTO.setPriceChangeFromLastStock(
                    financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice());
            infoDTO.setPercentageChangeFromLastStock(
                    (financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice()) * 100
                            / financialDataDTO.getStockPrice());
            infoDTO.setPriceChangeCurrentStock(
                    financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice());
            infoDTO.setPercentageChangeCurrentStock(
                    (financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice()) * 100
                            / financialDataDTO.getHighestStockPrice());
            return infoDTO;
        } else {
            BalanceSheetDTO balanceSheetDTO = new BalanceSheetDTO();

            FinancialDataDTO financialDataDTO = (FinancialDataDTO) basicAndFinancialMap.get("financialDTO");
            BasicInfoDataDTO basicInfoDTO = (BasicInfoDataDTO) basicAndFinancialMap.get("basicInfoDTO");
            balanceSheetDTO.setTicker((String) basicAndFinancialMap.get("ticker"));
            balanceSheetDTO.setCompanyName(basicInfoDTO.getCompanyName());
            balanceSheetDTO.setCashAndMarkablTTM(financialDataDTO.getCashAndMarkablTTM());
            balanceSheetDTO.setCashAndMarkablLTM(financialDataDTO.getCashAndMarkablLTM());
            balanceSheetDTO.setBookValueEqualityTTM(financialDataDTO.getBookValueEqualityTTM());
            balanceSheetDTO.setBookValueEqualityLTM(financialDataDTO.getBookValueEqualityLTM());
            balanceSheetDTO.setStockPrice(financialDataDTO.getStockPrice());
            balanceSheetDTO.setBookValueDebtTTM(financialDataDTO.getBookValueDebtTTM());
            balanceSheetDTO.setBookValueDebtLTM(financialDataDTO.getBookValueDebtLTM());
            balanceSheetDTO.setHighestStockPrice(financialDataDTO.getHighestStockPrice());
            balanceSheetDTO.setLowestStockPrice(financialDataDTO.getLowestStockPrice());
            balanceSheetDTO.setPriceChangeFromLastStock(
                    financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice());
            balanceSheetDTO.setPercentageChangeFromLastStock(
                    (financialDataDTO.getStockPrice() - financialDataDTO.getPreviousDayStockPrice()) * 100
                            / financialDataDTO.getStockPrice());
            balanceSheetDTO.setPriceChangeCurrentStock(
                    financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice());
            balanceSheetDTO.setPercentageChangeCurrentStock(
                    (financialDataDTO.getHighestStockPrice() - financialDataDTO.getStockPrice()) * 100
                            / financialDataDTO.getHighestStockPrice());
            return balanceSheetDTO;
        }
    }

    public Object verifyLoginDetailsUser(LoginForm loginForm) {
        Optional<User> userOptional = userRepository.findByUsername(loginForm.getEmail());
        if (!userOptional.isPresent()) {
            throw new RuntimeException("user not found with email " + loginForm.getEmail());
        }
        User user = userOptional.get();
        if (!PasswordUtils.matchPassword(loginForm.getPassword(), user.getPassword())) {
            throw new RuntimeException("user password or email is not correct");
        }

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setFirstName(user.getFirstName());
        loginDTO.setLastName(user.getLastName());
        loginDTO.setUsername(user.getUsername());
        loginDTO.setMsg("user login successfully !");
        return loginDTO;
    }

    public Object createNewUser(SignupForm signupForm) {
        Optional<User> userOptional = userRepository.findByUsername(signupForm.getEmail());
        if (userOptional.isPresent()) {
            return ResponseGenerator.generateBadRequestResponse("email id is already present " + signupForm.getEmail());
        }
        User user = new User();
        user.setFirstName(signupForm.getFirstName());
        user.setLastName(signupForm.getLastName());
        user.setUsername(signupForm.getEmail());
        user.setPassword(PasswordUtils.encodePassword(signupForm.getPassword()));
        user.setRole(signupForm.getRole());
        userRepository.save(user);
        return "user created with email id " + signupForm.getEmail();
    }

    public RDResult calculateR_DConvertorValue(String industry, Double marginalTaxRate,
            Map<String, Double> researchAndDevelopmentMap) {
        int defaultAmortization = 4;
        SectorMapping sectorMapping = sectorMappingRepository.findByIndustryName(industry);
        if (Objects.isNull(sectorMapping)) {
            log.info("Sector mapping not found for industry: {}", industry);
            // throw new RuntimeException("Sector mapping not found for industry: " +
            // input.getIndustryUS());
        }
        // TODO: handle case when sector mapping is not found and
        // also handle the duplicated data while calculations

        RDConvertor rdConvertor = rdConvertorRepository.findAmortizationPeriod(sectorMapping.getIndustryAsPerExcel());

        int amortizationPeriod;

        if (Objects.isNull(rdConvertor)) {
            // throw new RuntimeException("Amortization period not found for industry: " +
            // input.getIndustryUS());
            log.info("Amortization period not found for industry: {} ", industry);
            amortizationPeriod = defaultAmortization;
        } else {
            amortizationPeriod = rdConvertor.getAmortizationPeriod();
            log.info("Amortization Period of {} is {} ", "entertainment", rdConvertor.getAmortizationPeriod());
            if (amortizationPeriod > 4) {
                log.info("Amortization period for {} is greater than 5, setting to default: {}", "entertainment",
                        defaultAmortization);
                amortizationPeriod = defaultAmortization;
            }
        }

        Double currentYearExpense = researchAndDevelopmentMap.get("currentR&D-0");

        List<Double> pastRdExpenses = new ArrayList<>();
        if (researchAndDevelopmentMap.get("currentR&D-1") != null
                && researchAndDevelopmentMap.get("currentR&D-1") != 0.0
                && !Objects.equals(currentYearExpense, researchAndDevelopmentMap.get("currentR&D-1"))) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-1"));
        }
        if (researchAndDevelopmentMap.get("currentR&D-2") != null
                && researchAndDevelopmentMap.get("currentR&D-2") != 0.0) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-2"));
        }
        if (researchAndDevelopmentMap.get("currentR&D-3") != null
                && researchAndDevelopmentMap.get("currentR&D-3") != 0.0) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-3"));
        }
        if (researchAndDevelopmentMap.get("currentR&D-4") != null
                && researchAndDevelopmentMap.get("currentR&D-4") != 0.0) {
            pastRdExpenses.add(researchAndDevelopmentMap.get("currentR&D-4"));
        }

        // FIXED: Use available data instead of returning zeros when insufficient
        // history
        // Only return zeros if there is NO R&D data at all (no current year expense)
        if (currentYearExpense == null || currentYearExpense == 0) {
            log.info("No current R&D expense, returning zero R&D adjustments");
            return new RDResult(0.00, 0.00, 0.00, 0.00);
        }

        // Adjust amortization period based on available data
        // Per Damodaran: Use the best available data rather than ignoring R&D entirely
        int effectiveAmortizationPeriod;
        if (pastRdExpenses.isEmpty()) {
            // If no historical data, use current year only (1-year amortization)
            effectiveAmortizationPeriod = 1;
            log.warn("No historical R&D data available. Using current year expense only for R&D capitalization.");
        } else if (pastRdExpenses.size() < amortizationPeriod - 1) {
            // Use available historical data with adjusted amortization period
            effectiveAmortizationPeriod = pastRdExpenses.size() + 1;
            log.info("Limited R&D history available ({} years). Adjusting amortization period from {} to {}.",
                    pastRdExpenses.size(), amortizationPeriod, effectiveAmortizationPeriod);
        } else {
            effectiveAmortizationPeriod = amortizationPeriod;
        }

        log.info("Current Year expense: {} , tax rate: {} , effective amortization period: {}, past year expenses: {}",
                currentYearExpense, marginalTaxRate, effectiveAmortizationPeriod, pastRdExpenses);
        RDResult result = new RDResult();
        List<YearlyCalculation> calculations = new ArrayList<>();
        log.info("R&D calculation started...");
        // Current year calculation
        calculations.add(createYearlyCalculation("current Year", currentYearExpense, effectiveAmortizationPeriod, 0));
        // Past years calculations
        for (int i = 0; i < effectiveAmortizationPeriod - 1; i++) {
            double pastRdExpense = i < pastRdExpenses.size() ? pastRdExpenses.get(i) : 0;
            if (i >= pastRdExpenses.size()) {
                log.warn("No R&D expense data available for year {}. setting default value to 0.", (i + 1));
            }
            calculations
                    .add(createYearlyCalculation("Year - " + (i + 1), pastRdExpense, effectiveAmortizationPeriod, i));
        }

        // Set results
        result.setYearlyCalculations(calculations);
        double totalResearchAsset = 0;
        double totalAmortization = 0;
        for (YearlyCalculation calc : calculations) {
            totalResearchAsset += calc.getUnamortizedPortion();
            if (!calc.getYear().contains("Current")) {
                totalAmortization += calc.getAmortizationThisYear();
            }
        }
        result.setTotalResearchAsset(totalResearchAsset);
        result.setTotalAmortization(totalAmortization);
        log.info("Total Amortization calculated: {} and Total research asset value is: {} ",
                result.getTotalAmortization(), result.getTotalResearchAsset());

        // Adjusted amount to operating income
        double adjustmentToOperatingIncome = currentYearExpense - totalAmortization;
        result.setAdjustmentToOperatingIncome(adjustmentToOperatingIncome);
        log.info("Adjustment to Operating Income calculated: {}", result.getAdjustmentToOperatingIncome());

        // Tax Effect: calculating tax amount
        double taxEffect = adjustmentToOperatingIncome * (marginalTaxRate / 100);
        result.setTaxEffect(taxEffect);
        log.info("Tax Effect calculated: {}", result.getTaxEffect());
        log.info("R&D convertor completed...");
        return new RDResult(totalResearchAsset, totalAmortization, adjustmentToOperatingIncome, taxEffect);
    }

    private YearlyCalculation createYearlyCalculation(String yearLabel, double rdExpense, int amortizationPeriod,
            int yearIndex) {
        YearlyCalculation yearlyCalculation = new YearlyCalculation();
        yearlyCalculation.setYear(yearLabel);
        yearlyCalculation.setRdExpense(rdExpense);

        if (yearLabel.contains("current")) {
            // For current year, unamortized portion is equal to the entire expense and
            // amortization is 0
            yearlyCalculation.setUnamortizedPortion(rdExpense);
            yearlyCalculation.setAmortizationThisYear(0.00);
        } else {
            // For past years, calculate the amortization and unamortized portion
            double unamortizedPortion = rdExpense * (double) (amortizationPeriod - (yearIndex + 1))
                    / amortizationPeriod;
            yearlyCalculation.setUnamortizedPortion(Math.max(unamortizedPortion, 0));
            yearlyCalculation.setAmortizationThisYear(rdExpense / amortizationPeriod);
        }

        log.info(
                "Added {} calculations , amortization: {}, unamortization portion this year: {} , RD expenses this year: {}",
                yearlyCalculation.getYear(), yearlyCalculation.getAmortizationThisYear(),
                yearlyCalculation.getUnamortizedPortion(), yearlyCalculation.getRdExpense());

        return yearlyCalculation;
    }

    private String formatDouble(double value) {
        return df.format(value);
    }

    public String convertExcelToJson(MultipartFile file) throws IOException {
        Workbook workbook = new XSSFWorkbook(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(11); // Assuming the relevant data is on the 12th sheet

        List<Map<String, Object>> excelData = new ArrayList<>(); // Changed to Object to accommodate numeric values
        Row headerRow = sheet.getRow(3); // Assuming the actual header row starts at row 4 (index 3)
        if (headerRow == null) {
            throw new IllegalArgumentException("Header row is missing in the Excel file");
        }

        // Create a FormulaEvaluator for evaluating formulas
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        // Iterate over rows starting from row 4 (index 3)
        for (int i = 4; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row))
                continue; // Skip empty or null rows

            Map<String, Object> rowData = new LinkedHashMap<>();
            // Mapping financial and country data
            rowData.put("country", getCellValueAsString(row.getCell(0))); // Country name
            rowData.put("moodysRating", getCellValueAsString(row.getCell(1))); // Moody's credit rating
            rowData.put("adjustedDefaultSpread", getNumericCellValue(row.getCell(2))); // Adjusted default spread
            rowData.put("equityRiskPremium", getNumericCellValue(row.getCell(3))); // Equity risk premium
            rowData.put("countryRiskPremium", getNumericCellValue(row.getCell(4))); // Country risk premium
            rowData.put("corporateTaxRate", getNumericCellValue(row.getCell(5))); // Corporate tax rate
            rowData.put("gdpInMillions", getNumericCellValue(row.getCell(6))); // GDP in millions

            excelData.add(rowData);
        }

        workbook.close();

        // Convert to JSON using Jackson ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(excelData);
    }

    // method to get values numerically instead of string
    private Object getNumericCellValue(Cell cell) {
        if (cell == null)
            return null; // Return null if the cell is empty
        switch (cell.getCellType()) {
            case NUMERIC:
                // Check if the cell is formatted as a percentage
                // Check if the cell is formatted as a percentage
                if (cell.getCellStyle().getDataFormatString().contains("%")) {
                    return cell.getNumericCellValue() * 100; // Multiply by 100 for percentage values
                }
                return cell.getNumericCellValue(); // Return numeric value as is
            case FORMULA:
                // Evaluate the formula and return numeric value if applicable
                FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
                CellValue evaluatedValue = evaluator.evaluate(cell);
                if (evaluatedValue.getCellType() == CellType.NUMERIC) {
                    // Check if the evaluated value is formatted as a percentage
                    if (cell.getCellStyle().getDataFormatString().contains("%")) {
                        return evaluatedValue.getNumberValue() * 100; // Return the evaluated numeric value directly
                    }
                    return evaluatedValue.getNumberValue(); // Return the evaluated numeric value as is
                }
                return null; // Return null if the evaluated cell is not numeric
            default:
                return null; // Return null for non-numeric types
        }
    }

    private boolean isRowEmpty(Row row) {
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }

    public String convertExcelToJsonn(MultipartFile file) throws IOException {
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

            // Use a list to wrap the single object
            List<Map<String, Object>> excelData = new ArrayList<>();
            Map<String, Object> rowData = new LinkedHashMap<>();

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row))
                    continue;

                Cell keyCell = row.getCell(0);
                Cell valueCell = row.getCell(1);
                if (row.getCell(3) != null) {
                }

                if (keyCell != null && keyCell.getCellType() == CellType.STRING
                        && !keyCell.getStringCellValue().trim().isEmpty()) {
                    String key = keyCell.getStringCellValue().trim();
                    Object value = getCellValue(evaluator, valueCell);

                    if (!isUnwantedKey(key)) { // if value is not present with value- continue ( instructions)
                        rowData.put(toCamelCase(key), value); // Convert key to camelCase
                    }
                }
            }

            // Add the populated rowData map to the excelData list
            excelData.add(rowData);

            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(excelData);
        } finally {
            if (workbook != null)
                workbook.close();
        }
    }

    private String toCamelCase(String key) {
        if (key == null || key.trim().isEmpty()) {
            return key;
        }

        key = key.replaceAll("[=]", ""); // Remove trailing '=' sign if any
        StringBuilder camelCaseKey = new StringBuilder();
        String[] parts = key.split("[-_ ]");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty())
                continue;

            if (i == 0) {
                camelCaseKey.append(part.toLowerCase());
            } else {
                camelCaseKey.append(part.substring(0, 1).toUpperCase());
                camelCaseKey.append(part.substring(1).toLowerCase());
            }
        }
        return camelCaseKey.toString();
    }

    private Object getCellValue(FormulaEvaluator evaluator, Cell cell) {
        if (cell == null)
            return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    // Format the date as a string
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
                    return dateFormat.format(cell.getDateCellValue()); // Convert to formatted string
                }

                return cell.getNumericCellValue(); // Return as a number

            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return evaluator.evaluate(cell).getNumberValue();
            default:
                return "";
        }
    }

    private boolean isUnwantedKey(String key) {
        return key.contains("If you don't understand") || key.contains("Numbers from your base year");
    }

    public String convertIndustryAverageExcelToJson(MultipartFile file) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Ensure the sheet exists
            if (workbook.getNumberOfSheets() <= 0) {
                throw new IllegalArgumentException("Sheet index out of bounds");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rowIterator = sheet.iterator();
            List<String> keys = new ArrayList<>();

            // Read header row for keys
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                for (Cell cell : headerRow) {
                    keys.add(toCamelCase(cell.getStringCellValue()));
                }
            }

            // Read data rows
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < keys.size(); cellIndex++) {
                    Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    Object cellValue = getCellValueAsString(cell); // Extracting value

                    // Handle percentage values
                    if (cell.getCellType() == CellType.NUMERIC) {
                        if (cell.getCellStyle().getDataFormatString().contains("%")) {
                            // It's a percentage, convert it to the appropriate format (multiply by 100)
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue() * 100); // Return as percentage
                        } else {
                            // Return as normal numeric value
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue());
                        }
                    } else if (cell.getCellType() == CellType.STRING) {
                        // If the cell is a string, handle it accordingly
                        rowData.put(keys.get(cellIndex), cellValue);
                    } else {
                        // Handle other types as needed (e.g., boolean, formula, etc.)
                        rowData.put(keys.get(cellIndex), cellValue);
                    }
                }
                data.add(rowData);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        // Convert to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(data);
    }

    public String convertCountryEquityExcelToJson(MultipartFile file) throws IOException {
        List<Map<String, Object>> data = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // Ensure the sheet exists
            if (workbook.getNumberOfSheets() <= 11) {
                throw new IllegalArgumentException("Sheet index out of bounds");
            }

            Sheet sheet = workbook.getSheetAt(11);
            Iterator<Row> rowIterator = sheet.iterator();
            List<String> keys = new ArrayList<>();

            // Read header row for keys
            if (rowIterator.hasNext()) {
                Row headerRow = rowIterator.next();
                for (Cell cell : headerRow) {
                    keys.add(toCamelCase(cell.getStringCellValue()));
                }
            }

            // Read data rows
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Map<String, Object> rowData = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < keys.size(); cellIndex++) {
                    Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    Object cellValue = getCellValueAsString(cell); // Extracting value

                    // Handle percentage values
                    if (cell.getCellType() == CellType.NUMERIC) {
                        if (cell.getCellStyle().getDataFormatString().contains("%")) {
                            // It's a percentage, convert it to the appropriate format (multiply by 100)
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue() * 100); // Return as percentage
                        } else {
                            // Return as normal numeric value
                            rowData.put(keys.get(cellIndex), cell.getNumericCellValue());
                        }
                    } else if (cell.getCellType() == CellType.STRING) {
                        // If the cell is a string, handle it accordingly
                        rowData.put(keys.get(cellIndex), cellValue);
                    } else {
                        // Handle other types as needed (e.g., boolean, formula, etc.)
                        rowData.put(keys.get(cellIndex), cellValue);
                    }
                }
                data.add(rowData);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error", e);
        }

        // Convert to JSON
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(data);
    }

    public String convertInputExcelDataToJson(MultipartFile file) throws IOException {
        Workbook workbook = null;
        try {
            workbook = new XSSFWorkbook(file.getInputStream());
            Sheet sheet = workbook.getSheetAt(0);
            List<Map<String, List<Object>>> data = new ArrayList<>();
            Map<String, List<Object>> rowData = new LinkedHashMap<>();

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row))
                    continue; // if row is empty skip that row

                Cell keyCell = row.getCell(0); // for key
                List<Object> colData = new ArrayList<>();

                for (int j = 1; j < row.getLastCellNum(); j++) {
                    Cell valueCell = row.getCell(j);
                    if (valueCell == null || valueCell.getCellType() == CellType.BLANK)
                        break; // Stop if the cell is blank

                    // Get cell value as a String, Double, or other types as necessary
                    switch (valueCell.getCellType()) {
                        case STRING:
                            colData.add(valueCell.getStringCellValue());
                            break;
                        case NUMERIC:
                            colData.add(valueCell.getNumericCellValue());
                            break;
                        case BOOLEAN:
                            colData.add(valueCell.getBooleanCellValue());
                            break;
                        case BLANK:
                            colData.add(null); // or handle as needed
                            break;
                        default:
                            colData.add(valueCell.toString()); // Fallback for other types
                    }
                }

                if (keyCell != null && keyCell.getCellType() == CellType.STRING
                        && !keyCell.getStringCellValue().trim().isEmpty()) {
                    String key = keyCell.getStringCellValue().trim();

                    if (!isUnwantedKey(key)) {
                        rowData.put(toCamelCase(key), colData);
                    }
                }
            }

            data.add(rowData);
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    public List<IndustryAveragesUS> getAllIndustryUS() {
        return industryAvgUSRepository.findAll();
    }

    public void loadIndustryUSData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<IndustryAveragesUS> industryAveragesList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, IndustryAveragesUS.class));

        // Save the list to the database
        industryAvgUSRepository.saveAll(industryAveragesList);

        log.info("Data successfully loaded into the database!");
    }

    public void loadIndustryGloData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<IndustryAveragesGlobal> industryAveragesList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, IndustryAveragesGlobal.class));

        // Save the list to the database
        industryAvgGloRepository.saveAll(industryAveragesList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<IndustryAveragesGlobal> getAllIndustryGlo() {
        return industryAvgGloRepository.findAll();
    }

    public void loadCountryEquityData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<CountryEquity> countryEquityList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CountryEquity.class));

        // Save the list to the database
        countryEquityRepository.saveAll(countryEquityList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<CountryEquity> getAllCountryEquity() {
        return countryEquityRepository.findAll();
    }

    public void loadRegionEquityData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<RegionEquity> regionEquityList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RegionEquity.class));

        // Save the list to the database
        regionEquityRepository.saveAll(regionEquityList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<RegionEquity> getAllRegionEquity() {
        return regionEquityRepository.findAll();
    }

    public void loadRDConvertorData(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<RDConvertor> rdConvertorList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RDConvertor.class));

        // Save the list to the database
        rdConvertorRepository.saveAll(rdConvertorList);
    }

    public List<RDConvertor> getAllRDConvertor() {
        return rdConvertorRepository.findAll();
    }

    public List<InputStatDistribution> getAllInputStat() {
        return inputStatRepository.findAll();
    }

    public void loadRegionSectorMapping(MultipartFile file) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<SectorMapping> sectorMappingList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SectorMapping.class));

        // Save the list to the database
        sectorMappingRepository.saveAll(sectorMappingList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<SectorMapping> getAllSectorMapping() {
        return sectorMappingRepository.findAll();
    }

    public void loadRiskFreeRate(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<RiskFreeRate> riskFreeRateList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, RiskFreeRate.class));

        // Save the list to the database
        riskFreeRateRepository.saveAll(riskFreeRateList);

        System.out.println("Data successfully loaded into the database!");

    }

    public List<RiskFreeRate> getAllRiskFreeRate() {
        return riskFreeRateRepository.findAll();
    }

    public void loadCostOfCapital(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<CostOfCapital> costOfCapitalList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, CostOfCapital.class));

        // Save the list to the database
        costOfCapitalRepository.saveAll(costOfCapitalList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<CostOfCapital> getAllCostOfCapital() {
        return costOfCapitalRepository.findAll();
    }

    public void loadLargeSpread(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<LargeBondSpread> largeBondSpreadList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, LargeBondSpread.class));

        // Save the list to the database
        largeSpreadRepository.saveAll(largeBondSpreadList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<LargeBondSpread> getAllLargeSpread() {
        return largeSpreadRepository.findAll();
    }

    public void loadSmallSpread(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<SmallBondSpread> smallBondSpreadList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SmallBondSpread.class));

        // Save the list to the database
        smallSpreadRepository.saveAll(smallBondSpreadList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<SmallBondSpread> getAllSmallSpread() {
        return smallSpreadRepository.findAll();
    }

    public void loadBondRating(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<BondRating> bondRatingList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, BondRating.class));

        // Save the list to the database
        bondRatingRepository.saveAll(bondRatingList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<BondRating> getAllBondRating() {
        return bondRatingRepository.findAll();
    }

    public void loadFailureRate(MultipartFile file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<FailureRate> failureRateList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, FailureRate.class));

        // Save the list to the database
        failureRateRepository.saveAll(failureRateList);

        System.out.println("Data successfully loaded into the database!");
    }

    public List<FailureRate> getAllFailureRate() {
        return failureRateRepository.findAll();
    }

    public void loadInputStat(MultipartFile file) throws IOException {

        ObjectMapper objectMapper = new ObjectMapper();

        // Read the content of the uploaded file into a list of objects
        List<InputStatDistribution> inputStatList = objectMapper.readValue(file.getInputStream(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, InputStatDistribution.class));

        // Save the list to the database
        inputStatRepository.saveAll(inputStatList);

        System.out.println("Data successfully loaded into the database!");
    }

    /**
     * Calculate operating lease conversion with default zero values.
     * This is a backward-compatible method that returns zero adjustments when no
     * lease data is available.
     * For companies with actual operating lease commitments, use the overloaded
     * method with parameters.
     * 
     * @return LeaseResultDTO with zero adjustments (no operating lease impact)
     */
    public LeaseResultDTO calculateOperatingLeaseConvertor() {
        // Return zero adjustments when no lease data is provided
        // This prevents applying arbitrary default values to companies without
        // operating leases
        return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
    }

    /**
     * Calculate operating lease conversion using Damodaran's methodology.
     * Converts operating lease commitments to debt equivalent for proper DCF
     * valuation.
     * 
     * Per Damodaran: Operating leases are effectively debt obligations that should
     * be
     * capitalized to get a true picture of the company's debt and operating income.
     * 
     * @param leaseExpenseCurrentYear Current year's operating lease expense
     * @param commitments             Array of lease commitments for Years 1-5
     * @param futureCommitment        Total commitment beyond Year 5
     * @return LeaseResultDTO with calculated adjustments
     */
    public LeaseResultDTO calculateOperatingLeaseConvertor(
            Double leaseExpenseCurrentYear,
            Double[] commitments,
            Double futureCommitment) {

        // Return zero adjustments if no valid lease data provided
        if (leaseExpenseCurrentYear == null || leaseExpenseCurrentYear <= 0) {
            log.debug("No operating lease expense provided, returning zero adjustments");
            return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        }

        if (commitments == null || commitments.length == 0) {
            log.debug("No lease commitments provided, returning zero adjustments");
            return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        }

        // Default future commitment to 0 if not provided
        if (futureCommitment == null) {
            futureCommitment = 0.0;
        }

        double costOfDebt = PRE_TAX_COST_OF_DEBT;
        int numberOfYearsInCommits;

        // Array to store present values of commitments
        double[] presentValues = new double[commitments.length];
        double totalCommits = 0;
        double avgCommits;
        for (Double commitment : commitments) {
            if (commitment != null) {
                totalCommits += commitment;
            }
        }

        // Handle edge case where all commitments are zero
        if (totalCommits == 0) {
            log.debug("All lease commitments are zero, returning zero adjustments");
            return new LeaseResultDTO(0.0, 0.0, 0.0, 0.0);
        }

        avgCommits = totalCommits / commitments.length;
        numberOfYearsInCommits = avgCommits > 0 ? (int) Math.round(futureCommitment / avgCommits) : 0;
        log.info("Number of years embedded in yr 6 estimate: {} ", numberOfYearsInCommits);

        // Variable to accumulate total debt
        double totalDebt = 0;

        log.info("Starting the lease conversion calculation.");

        // Convert each commitment into Present Value (PV) using the given commitments
        log.info("Calculating Present Values for Year 1 to Year 5 commitments.");
        for (int i = 0; i < commitments.length; i++) {
            double commitment = commitments[i] != null ? commitments[i] : 0.0;
            presentValues[i] = calculatePresentValue(commitment, i + 1);
            totalDebt += presentValues[i];
            log.info("Year {}: Commitment = {}, Present Value = {}", i + 1, commitment, presentValues[i]);
        }

        // Calculate Present Value for Year 6 and beyond as an annuity
        log.info("Calculating Present Value for Year 6 and beyond using an annuity method.");
        double annuityCommitments = calculateAnnuityCommitments(numberOfYearsInCommits, futureCommitment);
        double annuityPV = calculateValue(numberOfYearsInCommits, annuityCommitments, costOfDebt);

        totalDebt += annuityPV;
        log.info("Year 6 and beyond annuity PV: {} and total Debt value: {}", annuityPV, totalDebt);

        // Calculate depreciationOnLeaseAsset on operating lease asset (straight-line)
        int totalYears = numberOfYearsInCommits + commitments.length;
        double depreciationOnLeaseAsset = totalYears > 0 ? totalDebt / totalYears : 0;
        log.info("Calculating depreciationOnLeaseAsset: Total Debt = {}, Depreciation = {}", totalDebt,
                depreciationOnLeaseAsset);

        // Calculate adjustment to operating earnings
        double adjustmentToOperatingEarnings = leaseExpenseCurrentYear - depreciationOnLeaseAsset;
        log.info("Calculating adjustment to operating earnings: Lease Expense = {}, Depreciation = {}, Adjustment = {}",
                leaseExpenseCurrentYear, depreciationOnLeaseAsset, adjustmentToOperatingEarnings);

        double adjustmentToTotalDebt = totalDebt;

        // Return all calculated adjustments in the DTO
        LeaseResultDTO resultDTO = new LeaseResultDTO(depreciationOnLeaseAsset, adjustmentToOperatingEarnings,
                adjustmentToTotalDebt, depreciationOnLeaseAsset);
        log.info("Lease Conversion calculation completed successfully.");

        return resultDTO;
    }

    private double calculatePresentValue(double commitment, int year) {
        log.debug("Calculating Present Value for commitment {} at year {}", commitment, year);
        double pv = commitment / Math.pow(1 + PRE_TAX_COST_OF_DEBT, year);
        log.debug("Present Value for commitment {} in year {}: {}", commitment, year, pv);
        return pv;
    }

    private double calculateAnnuityCommitments(int numberOfYearsInCommits, double futureCommitment) {
        if (numberOfYearsInCommits > 0) {
            return futureCommitment > 0 ? futureCommitment / numberOfYearsInCommits : futureCommitment;
        } else {
            return 0;
        }
    }

    public double calculateValue(double numberOfYearsInCommits, double annuityCommitments, double costOfDebt) {
        double result;

        // Calculate the formula when D18 > 0
        if (numberOfYearsInCommits > 0) {
            double part1 = Math.pow(1 + costOfDebt, -numberOfYearsInCommits);
            double part2 = 1 - part1;
            double part3 = annuityCommitments * part2 / costOfDebt;
            double part4 = Math.pow(1 + costOfDebt, 5);
            result = part3 / part4;
        } else {
            // Calculate the formula when D18 <= 0
            result = annuityCommitments / Math.pow(1 + costOfDebt, 6);
        }

        return result;
    }

    private Map<String, Object> balanceSheetYearlyData(String url) {
        Map<String, Object> responseMap = restTemplate.getForObject(url, Map.class);
        return responseMap;

    }

    private Map<String, Object> balanceSheetQuaterlyData(String url) {
        Map<String, Object> responseMap = restTemplate.getForObject(url, Map.class);
        return responseMap;
    }

    private Map<String, Object> incomeStatementYearlyData(String endPoint) {
        Map<String, Object> responseMap = restTemplate.getForObject(endPoint, Map.class);
        return responseMap;

    }

    private Map<String, Object> incomeStatementQuarterlyData(String url) {
        Map<String, Object> responseMap = restTemplate.getForObject(url, Map.class);
        return responseMap;

    }

    private Map<String, Object> basicInfoYearly(String endPoint) {
        Map<String, Object> responseMap = restTemplate.getForObject(endPoint, Map.class);
        return responseMap;

    }

    private Map<String, Object> revenueYearlyData(String url) {
        Map<String, Object> responseMap = restTemplate.getForObject(url, Map.class);
        return responseMap;

    }

    /**
     * Extract Authorization header from current HTTP request context
     */
    private String getAuthTokenFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && !authHeader.isEmpty()) {
                    return authHeader;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract Authorization header from request context: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> story(String url, Object json) {
        // Try to extract auth token from current request context
        String authToken = getAuthTokenFromRequest();
        return story(url, json, authToken);
    }

    private Map<String, Object> story(String url, Object json, String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward Authorization header if provided (required for credit-gated
        // endpoints)
        if (authToken != null && !authToken.isEmpty()) {
            headers.set("Authorization", authToken);
            log.debug("Forwarding Authorization header to Python backend for story generation");
        } else {
            log.warn("No Authorization header found - Python backend may reject request for credit-gated endpoint");
        }

        try {
            // Manually serialize with JavaTimeModule to handle LocalDate fields
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

            String jsonString = objectMapper.writeValueAsString(json);
            HttpEntity<String> entity = new HttpEntity<>(jsonString, headers);

            return restTemplate.postForObject(url, entity, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to serialize request body for story endpoint: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize request", e);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Python backend returned error for story endpoint: {} - {}", e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw e;
        }
    }

    public static Double convertPercentage(Double salesToCapital) {
        if (salesToCapital == null) {
            return 0.0;
        }
        return (salesToCapital / 100);
    }

    public Map<String, Object> findDataByYear(Map<String, Object> map, String type) {
        int currentYear = LocalDate.now().getYear();
        int[] targetYears = { currentYear - 1, currentYear - 2, currentYear - 3 };

        for (int targetYear : targetYears) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                long timestamp = Long.parseLong(entry.getKey());
                LocalDate timeStampToDate = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                if (timeStampToDate.getYear() == targetYear) {
                    Map<String, Object> data = (Map<String, Object>) entry.getValue();
                    if (data != null) {
                        if (type != null && data.get("TotalRevenue") != null)
                            return data;
                        if (type == null)
                            return data;
                    }
                }
            }
        }
        return new HashMap<>();
    }

    public Map<String, Object> findDataByYearAttempt(Map<String, Object> map, String type) {
        int currentYear = LocalDate.now().getYear();
        int[] targetYears = { currentYear - 2, currentYear - 3 };

        for (int targetYear : targetYears) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                long timestamp = Long.parseLong(entry.getKey());
                LocalDate timeStampToDate = Instant.ofEpochMilli(timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();

                if (timeStampToDate.getYear() == targetYear) {
                    Map<String, Object> data = (Map<String, Object>) entry.getValue();
                    if (data != null) {
                        if (type != null && data.get("TotalRevenue") != null)
                            return data;
                        if (type == null)
                            return data;
                    }
                }
            }
        }
        return new HashMap<>();
    }

    @Transactional
    public void saveInputData(List<InputRequestDTO> inputRequestDTOList) {
        List<Input> inputs = inputRequestDTOList.stream().map(inputDTO -> {
            Input input = new Input();
            input.setDateOfValuation(inputDTO.getDateOfValuation());
            input.setTicker(inputDTO.getTicker());
            input.setCompanyName(inputDTO.getCompanyName());
            input.setCurrency(inputDTO.getCurrency());
            input.setIndustryUS(inputDTO.getIndustryUS());
            input.setIndustryGlo(inputDTO.getIndustryGlo());
            input.setCurrentYearExpense(inputDTO.getCurrentYearExpense());
            input.setTotalRevenue(inputDTO.getTotalRevenue());
            input.setOperatingIncome(inputDTO.getOperatingIncome());
            input.setHasRAndDExpensesToCapitalize(inputDTO.getHasRAndDExpensesToCapitalize());
            input.setMarginalTaxRate(inputDTO.getMarginalTaxRate());
            // Handle PastExpense as well
            List<PastExpense> pastExpenses = inputDTO.getPastExpense().stream().map(pastExpenseDTO -> {
                PastExpense pastExpense = new PastExpense();
                pastExpense.setExpense(pastExpenseDTO.getExpense());
                pastExpense.setInput(input); // Set the input reference
                return pastExpense;
            }).collect(Collectors.toList());

            input.setPastExpense(pastExpenses); // Set the list of past expenses

            return input;
        }).collect(Collectors.toList());

        // Save all inputs in one batch operation
        inputRepository.saveAll(inputs);
    }

    public List<Input> getAllInputData() {
        return inputRepository.findAll();
    }

    @Transactional
    public void deleteInput(Long inputId) {
        // Check if the input exists
        if (inputRepository.existsById(inputId)) {
            inputRepository.deleteById(inputId);
        } else {
            throw new RuntimeException("Input not found with id: " + inputId);
        }
    }

    public String saveSingleInputData(InputRequestDTO inputDTO) {
        Input input = new Input();
        input.setDateOfValuation(inputDTO.getDateOfValuation());
        input.setTicker(inputDTO.getTicker());
        input.setCompanyName(inputDTO.getCompanyName());
        input.setCurrency(inputDTO.getCurrency());
        input.setIndustryUS(inputDTO.getIndustryUS());
        input.setIndustryGlo(inputDTO.getIndustryGlo());
        input.setCurrentYearExpense(inputDTO.getCurrentYearExpense());
        input.setTotalRevenue(inputDTO.getTotalRevenue());
        input.setOperatingIncome(inputDTO.getOperatingIncome());
        input.setHasRAndDExpensesToCapitalize(inputDTO.getHasRAndDExpensesToCapitalize());
        input.setMarginalTaxRate(inputDTO.getMarginalTaxRate());

        List<PastExpense> pastExpense = inputDTO.getPastExpense().stream().map(pastExpenseRequestDTO -> {
            PastExpense pastExpense1 = new PastExpense();
            pastExpense1.setInput(input);
            pastExpense1.setExpense(pastExpenseRequestDTO.getExpense());
            return pastExpense1;
        }).collect(Collectors.toList());
        input.setPastExpense(pastExpense);
        inputRepository.save(input);
        return "data saved successfully";
    }

    public Map<String, Double> getR_DValues(String companyTicker, boolean requireRdConverter) {
        Map<String, Double> result = new HashMap<>();
        if (!requireRdConverter) {
            result.put("totalResearchAsset", 0.0);
            result.put("totalAmortization", 0.0);
            result.put("adjustmentToOperatingIncome", 0.0);
            return result;
        }
        return result;
    }

    /**
     * Calculate causal scenarios with dependency chain reasoning and heat map data.
     * 
     * Implements simple causal chains:
     * Revenue → Margin → ROIC → Value
     * 
     * @param ticker                 Stock ticker symbol
     * @param request                Causal scenario request with variances
     * @param valuationOutputService Service for calculating valuations
     * @return CausalScenarioResponse with 3 scenarios and heat map
     */

    /**
     * Fetch dividend data from Yahoo Finance API for DDM calculations.
     * 
     * @param ticker Stock ticker symbol
     * @return DividendDataDTO with dividend information, or null if not available
     */
    public DividendDataDTO fetchDividendData(String ticker) {
        try {
            String url = yahooApiUrl + "/dividends?ticker=" + ticker;
            log.info("Fetching dividend data for {} from {}", ticker, url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || response.isEmpty()) {
                log.info("No dividend data available for {}", ticker);
                return null;
            }

            DividendDataDTO dividendDataDTO = DividendDataDTO.builder()
                    .dividendRate(parseDouble(response.get("dividendRate")))
                    .dividendYield(parseDouble(response.get("dividendYield")))
                    .payoutRatio(parseDouble(response.get("payoutRatio")))
                    .trailingAnnualDividendRate(parseDouble(response.get("trailingAnnualDividendRate")))
                    .trailingAnnualDividendYield(parseDouble(response.get("trailingAnnualDividendYield")))
                    .exDividendDate(parseLong(response.get("exDividendDate")))
                    .lastDividendValue(parseDouble(response.get("lastDividendValue")))
                    .lastDividendDate(parseLong(response.get("lastDividendDate")))
                    .fiveYearAvgDividendYield(parseDouble(response.get("fiveYearAvgDividendYield")))
                    .dividendGrowthRate(parseDouble(response.get("dividendGrowthRate")))
                    .build();

            // Parse dividend history if available
            if (response.get("dividendHistory") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> historyMap = (Map<String, Object>) response.get("dividendHistory");
                Map<String, Double> dividendHistory = new HashMap<>();
                for (Map.Entry<String, Object> entry : historyMap.entrySet()) {
                    dividendHistory.put(entry.getKey(), parseDouble(entry.getValue()));
                }
                dividendDataDTO.setDividendHistory(dividendHistory);
            }

            return dividendDataDTO;

        } catch (Exception e) {
            log.warn("Error fetching dividend data for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    /**
     * Safely parse a Double from an Object
     */
    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Safely parse a Long from an Object
     */
    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
