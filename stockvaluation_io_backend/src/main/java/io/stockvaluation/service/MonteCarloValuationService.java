package io.stockvaluation.service;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationOutputDTO.CompanyDTO;
import io.stockvaluation.dto.valuationOutputDTO.FinancialDTO;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.repository.SectorMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Monte Carlo valuation service using ML-generated parameter distributions.
 * 
 * This service:
 * 1. Calls ML DCF Forecast API to get distributions (US companies only)
 * 2. Runs Monte Carlo simulation by sampling from distributions
 * 3. Reuses existing
 * ValuationOutputService.calculateFinancialData/calculateCompanyData
 * 4. Returns percentile-based valuation (p5, p50, p95)
 */
@Slf4j
@Service
public class MonteCarloValuationService {

    @Autowired
    private ValuationOutputService valuationOutputService;

    @Autowired
    private CommonService commonService;

    @Autowired
    private OptionValueService optionValueService;

    @Autowired
    private SectorMappingRepository sectorMappingRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ml.dcf.forecast.url:http://ml-dcf-forecast:8003}")
    private String mlForecastUrl;

    @Value("${ml.dcf.monte-carlo.paths:1000}")
    private int defaultPaths;

    /**
     * Run Monte Carlo DCF valuation using ML-generated distributions.
     *
     * @param ticker        Stock ticker
     * @param baselineInput Base financial data input
     * @param template      Valuation template
     * @param paths         Number of simulation paths (default 1000)
     * @return Monte Carlo result with percentiles, or null if unavailable
     */
    public MonteCarloResult runMonteCarlo(
            String ticker,
            FinancialDataInput baselineInput,
            ValuationTemplate template,
            Integer paths) {
        int numPaths = paths != null ? paths : defaultPaths;

        // 0. Check: US companies only
        // Use countryOfIncorporation if available, otherwise industryUs being set
        // implies US company
        String country = baselineInput.getBasicInfoDataDTO().getCountryOfIncorporation();
        String industryUs = baselineInput.getBasicInfoDataDTO().getIndustryUs();

        boolean isUsCompany = false;
        if (country != null && (country.equalsIgnoreCase("US") ||
                country.equalsIgnoreCase("USA") ||
                country.equalsIgnoreCase("United States"))) {
            isUsCompany = true;
        } else if (industryUs != null && !industryUs.trim().isEmpty()) {
            // Fall back to industryUs being set as proxy for US company
            isUsCompany = true;
            log.debug("Country is null for {}, but industryUs='{}' is set - treating as US company", ticker,
                    industryUs);
        }

        if (!isUsCompany) {
            log.info("Monte Carlo skipped for non-US company: {} (country: {}, industryUs: {})",
                    ticker, country, industryUs);
            return null;
        }

        // 1. Pre-compute shared values (reused for all paths)
        SectorMapping sectorMapping = null;
        try {
            sectorMapping = sectorMappingRepository
                    .findByIndustryName(baselineInput.getBasicInfoDataDTO().getIndustryUs());
        } catch (Exception e) {
            log.warn("Could not find sector mapping for {}: {}", ticker, e.getMessage());
        }

        RDResult rdResult = commonService.calculateR_DConvertorValue(
                baselineInput.getIndustry(),
                baselineInput.getFinancialDataDTO().getMarginalTaxRate(),
                baselineInput.getFinancialDataDTO().getResearchAndDevelopmentMap());

        OptionValueResultDTO optionResult = optionValueService.calculateOptionValue(
                ticker,
                baselineInput.getAverageStrikePrice(),
                baselineInput.getAverageMaturity(),
                baselineInput.getNumberOfOptions(),
                baselineInput.getStockPriceStdDev());

        LeaseResultDTO leaseResult = commonService.calculateOperatingLeaseConvertor();

        // 2. Get ML distributions
        MLForecastResponse forecast = callMLForecast(ticker, baselineInput);
        if (forecast == null || forecast.getPredictions() == null) {
            log.warn("ML forecast unavailable for {}, skipping Monte Carlo", ticker);
            return null;
        }

        // 3. Run simulations
        List<Double> valuations = new ArrayList<>();
        Random random = new Random(42); // Reproducible results

        log.info("Running {} Monte Carlo paths for {}", numPaths, ticker);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numPaths; i++) {
            try {
                // a. Deep copy using existing copy constructor
                FinancialDataInput sampledInput = new FinancialDataInput(baselineInput);

                // b. Sample from distributions
                applyDistributionSamples(sampledInput, forecast, random);

                // c. Reuse existing calculation methods
                FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                        sampledInput, rdResult, leaseResult, ticker, template);

                if (financialDTO == null) {
                    if (i < 3)
                        log.info("Path {} failed: financialDTO is null", i);
                    continue;
                }

                // d. Calculate company value
                CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                        financialDTO, sampledInput, optionResult, leaseResult);

                if (companyDTO == null) {
                    if (i < 3)
                        log.info("Path {} failed: companyDTO is null", i);
                    continue;
                }

                // e. Collect per-share value
                if (companyDTO.getEstimatedValuePerShare() != null) {
                    Double value = companyDTO.getEstimatedValuePerShare();
                    // Filter out extreme outliers (negative or absurdly high values)
                    if (value > 0 && value < 1e9) {
                        valuations.add(value);
                        if (i < 3)
                            log.info("Path {} succeeded: value=${}", i, value);
                    } else {
                        if (i < 3)
                            log.info("Path {} value filtered: ${}", i, value);
                    }
                } else {
                    if (i < 3)
                        log.info("Path {} failed: estimatedValuePerShare is null", i);
                }
            } catch (Exception e) {
                // Log first 3 failures at INFO level for debugging
                if (i < 3) {
                    log.info("Path {} exception: {}", i, e.getMessage());
                    if (e.getCause() != null) {
                        log.info("Cause: {}", e.getCause().getMessage());
                    }
                }
                // Continue with other paths
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Monte Carlo for {} completed: {}/{} paths successful in {}ms",
                ticker, valuations.size(), numPaths, elapsed);

        if (valuations.size() < 10) {
            log.warn("Too few successful paths ({}) for {}, skipping Monte Carlo",
                    valuations.size(), ticker);
            return null;
        }

        // 4. Calculate percentiles and build result
        return buildResult(valuations, numPaths, forecast);
    }

    /**
     * Build ML forecast request from company data.
     * 
     * Note: ML service requires 3+ years of historical data. Since we only have TTM
     * data,
     * we generate synthetic historical years by estimating backwards from current
     * values.
     * This uses industry-average assumptions for growth decay.
     */
    private MLForecastRequest buildMLForecastRequest(String ticker, FinancialDataInput input) {
        // Build current year data
        Double revenue = input.getFinancialDataDTO().getRevenueTTM();
        Double operatingIncome = input.getFinancialDataDTO().getOperatingIncomeTTM();
        Double operatingMargin = (revenue != null && revenue > 0 && operatingIncome != null)
                ? (operatingIncome / revenue) * 100 // as percentage
                : null;
        Double costOfCapital = input.getInitialCostCapital();

        // Get actual sales-to-capital ratio from input (already calculated in main
        // valuation)
        Double salesToCapitalRatio = input.getSalesToCapitalYears1To5();
        if (salesToCapitalRatio == null || salesToCapitalRatio <= 0) {
            // Fall back to industry average (~2.0)
            salesToCapitalRatio = 2.0;
            log.debug("Using fallback sales-to-capital ratio of 2.0 for {}", ticker);
        }

        // Calculate invested capital from revenue / sales-to-capital ratio
        // This is more reliable than using book values which may be null
        Double investedCapital = (revenue != null && salesToCapitalRatio > 0)
                ? revenue / salesToCapitalRatio
                : calculateInvestedCapital(input); // fall back to book value calculation

        int currentYearNum = java.time.Year.now().getValue();

        MLForecastRequest.YearData currentYear = MLForecastRequest.YearData.builder()
                .year(currentYearNum)
                .revenue(revenue)
                .operatingIncome(operatingIncome)
                .investedCapital(investedCapital)
                .costOfCapital(costOfCapital)
                .operatingMargin(operatingMargin)
                .salesToCapitalRatio(salesToCapitalRatio) // Explicitly set
                .build();

        // Generate 3 years of synthetic historical data by extrapolating backwards
        // Assumption: company grew ~10% YoY (conservative estimate)
        List<MLForecastRequest.YearData> historicalYears = new ArrayList<>();
        double assumedGrowthRate = 0.10; // 10% YoY growth assumption

        for (int i = 2; i >= 0; i--) { // Years: current-3, current-2, current-1
            double reverseFactor = Math.pow(1 + assumedGrowthRate, i + 1); // Divide by this to get historical

            Double histRevenue = revenue != null ? revenue / reverseFactor : null;
            Double histOperatingIncome = operatingIncome != null ? operatingIncome / reverseFactor : null;
            Double histInvestedCapital = investedCapital != null ? investedCapital / reverseFactor : null;

            MLForecastRequest.YearData histYear = MLForecastRequest.YearData.builder()
                    .year(currentYearNum - 3 + i)
                    .revenue(histRevenue)
                    .operatingIncome(histOperatingIncome)
                    .investedCapital(histInvestedCapital)
                    .costOfCapital(costOfCapital) // Cost of capital stays roughly same
                    .operatingMargin(operatingMargin) // Margin stays roughly same
                    .salesToCapitalRatio(salesToCapitalRatio) // Same ratio historically (approximately)
                    .build();

            historicalYears.add(histYear);
        }

        MLForecastRequest.FinancialData financialData = MLForecastRequest.FinancialData.builder()
                .currentYear(currentYear)
                .historicalYears(historicalYears)
                .build();

        MLForecastRequest.CompanyInfo companyInfo = MLForecastRequest.CompanyInfo.builder()
                .debt(input.getFinancialDataDTO().getBookValueDebtTTM())
                .cash(input.getFinancialDataDTO().getCashAndMarkablTTM())
                .shares(input.getFinancialDataDTO().getNoOfShareOutstanding())
                .riskFreeRate(input.getRiskFreeRate())
                .beta(input.getBasicInfoDataDTO().getBeta())
                .debtToCapital(calculateDebtToCapital(input))
                .industry(normalizeIndustry(input.getBasicInfoDataDTO().getIndustryUs()))
                .lifecycleStage(determineLifecycleStage(input))
                .build();

        return MLForecastRequest.builder()
                .ticker(ticker)
                .forecastHorizon(10)
                .financialData(financialData)
                .companyInfo(companyInfo)
                .build();
    }

    /**
     * Call ML forecast service.
     */
    private MLForecastResponse callMLForecast(String ticker, FinancialDataInput input) {
        String url = mlForecastUrl + "/api/v1/forecast";

        try {
            MLForecastRequest request = buildMLForecastRequest(ticker, input);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<MLForecastRequest> entity = new HttpEntity<>(request, headers);

            log.debug("Calling ML forecast for {} at {}", ticker, url);

            ResponseEntity<MLForecastResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MLForecastResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("ML forecast received for {}: {} years",
                        ticker, response.getBody().getForecastYears());
                return response.getBody();
            }

            log.warn("ML forecast returned non-2xx for {}: {}", ticker, response.getStatusCode());
            return null;

        } catch (RestClientException e) {
            log.warn("Failed to call ML forecast for {}: {}", ticker, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling ML forecast for {}: {}", ticker, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Apply sampled values from distributions to the input.
     */
    private void applyDistributionSamples(
            FinancialDataInput input,
            MLForecastResponse forecast,
            Random random) {
        var predictions = forecast.getPredictions();

        // Sample revenue growth rate (use year 1)
        if (predictions.getRevenue_growth_rate() != null &&
                !predictions.getRevenue_growth_rate().isEmpty()) {
            var yearPred = predictions.getRevenue_growth_rate().get(0);
            if (yearPred.getDistribution() != null && yearPred.getDistribution().getPercentiles() != null) {
                double sampled = sampleTriangular(yearPred.getDistribution().getPercentiles(), random);
                // OverrideAssumption(overrideCost, isOverride, additionalInputValue,
                // additionalRadioValue)
                input.setOverrideAssumptionGrowthRate(new OverrideAssumption(sampled, true, null, null));
            }
        }

        // Sample operating margin
        if (predictions.getOperating_margin() != null &&
                !predictions.getOperating_margin().isEmpty()) {
            var yearPred = predictions.getOperating_margin().get(0);
            if (yearPred.getDistribution() != null && yearPred.getDistribution().getPercentiles() != null) {
                double sampled = sampleTriangular(yearPred.getDistribution().getPercentiles(), random);
                input.setTargetPreTaxOperatingMargin(sampled);
            }
        }

        // Sample sales to capital ratio
        // Now enabled - ML request includes proper salesToCapitalRatio from input data
        if (predictions.getSales_to_capital_ratio() != null &&
                !predictions.getSales_to_capital_ratio().isEmpty()) {
            var yearPred = predictions.getSales_to_capital_ratio().get(0);
            if (yearPred.getDistribution() != null && yearPred.getDistribution().getPercentiles() != null) {
                double sampled = sampleTriangular(yearPred.getDistribution().getPercentiles(), random);
                input.setSalesToCapitalYears1To5(sampled);
                input.setSalesToCapitalYears6To10(sampled);
            }
        }

        // Sample cost of capital
        if (predictions.getCost_of_capital() != null &&
                !predictions.getCost_of_capital().isEmpty()) {
            var yearPred = predictions.getCost_of_capital().get(0);
            if (yearPred.getDistribution() != null && yearPred.getDistribution().getPercentiles() != null) {
                double sampled = sampleTriangular(yearPred.getDistribution().getPercentiles(), random);
                input.setInitialCostCapital(sampled);
            }
        }
    }

    /**
     * Sample from triangular distribution using p5, p50, p95 percentiles.
     */
    private double sampleTriangular(MLForecastResponse.Percentiles percentiles, Random random) {
        double a = percentiles.getP5(); // min
        double b = percentiles.getP95(); // max
        double c = percentiles.getP50(); // mode

        // Handle edge cases
        if (a >= b)
            return c;
        if (c < a)
            c = a;
        if (c > b)
            c = b;

        double u = random.nextDouble();
        double fc = (c - a) / (b - a);

        if (u < fc) {
            return a + Math.sqrt(u * (b - a) * (c - a));
        } else {
            return b - Math.sqrt((1 - u) * (b - a) * (b - c));
        }
    }

    /**
     * Build result with percentiles from simulation values.
     */
    private MonteCarloResult buildResult(
            List<Double> valuations,
            int totalPaths,
            MLForecastResponse forecast) {
        // Sort for percentile calculation
        Collections.sort(valuations);
        int n = valuations.size();

        MonteCarloResult result = new MonteCarloResult();
        result.setPaths(totalPaths);
        result.setSuccessfulPaths(n);

        // Calculate percentiles
        result.setP5(getPercentile(valuations, 5));
        result.setP25(getPercentile(valuations, 25));
        result.setP50(getPercentile(valuations, 50));
        result.setP75(getPercentile(valuations, 75));
        result.setP95(getPercentile(valuations, 95));

        // Calculate mean and std
        double sum = valuations.stream().mapToDouble(Double::doubleValue).sum();
        double mean = sum / n;
        result.setMean(mean);

        double variance = valuations.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum() / n;
        result.setStd(Math.sqrt(variance));

        // Copy distribution data for frontend visualization
        if (forecast.getPredictions() != null) {
            result.setRevenueGrowthDistributions(
                    mapToYearDistributions(forecast.getPredictions().getRevenue_growth_rate()));
            result.setOperatingMarginDistributions(
                    mapToYearDistributions(forecast.getPredictions().getOperating_margin()));
            result.setSalesToCapitalDistributions(
                    mapToYearDistributions(forecast.getPredictions().getSales_to_capital_ratio()));
            result.setCostOfCapitalDistributions(
                    mapToYearDistributions(forecast.getPredictions().getCost_of_capital()));
        }

        return result;
    }

    private Double getPercentile(List<Double> sortedValues, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private List<MonteCarloResult.YearDistribution> mapToYearDistributions(
            List<MLForecastResponse.YearPrediction> predictions) {
        if (predictions == null)
            return null;

        return predictions.stream()
                .map(p -> {
                    MonteCarloResult.YearDistribution dist = new MonteCarloResult.YearDistribution();
                    dist.setYear(p.getYear());
                    dist.setExplanation(p.getExplanation());
                    if (p.getDistribution() != null && p.getDistribution().getPercentiles() != null) {
                        dist.setP5(p.getDistribution().getPercentiles().getP5());
                        dist.setP50(p.getDistribution().getPercentiles().getP50());
                        dist.setP95(p.getDistribution().getPercentiles().getP95());
                    }
                    return dist;
                })
                .collect(Collectors.toList());
    }

    // Helper methods

    private Double calculateInvestedCapital(FinancialDataInput input) {
        Double equity = input.getFinancialDataDTO().getBookValueEqualityTTM();
        Double debt = input.getFinancialDataDTO().getBookValueDebtTTM();
        Double cash = input.getFinancialDataDTO().getCashAndMarkablTTM();

        equity = equity != null ? equity : 0.0;
        debt = debt != null ? debt : 0.0;
        cash = cash != null ? cash : 0.0;

        return equity + debt - cash;
    }

    private Double calculateDebtToCapital(FinancialDataInput input) {
        Double debt = input.getFinancialDataDTO().getBookValueDebtTTM();
        Double investedCapital = calculateInvestedCapital(input);

        if (investedCapital == null || investedCapital == 0)
            return 0.0;
        return (debt != null ? debt : 0.0) / investedCapital * 100;
    }

    private String normalizeIndustry(String industry) {
        if (industry == null)
            return "general";
        return industry.toLowerCase().replaceAll("\\s+", "_");
    }

    private String determineLifecycleStage(FinancialDataInput input) {
        // Simple heuristic based on growth (using revenueMu from GrowthDto)
        Double growthRate = input.getGrowthDto() != null
                ? input.getGrowthDto().getRevenueMu()
                : null;

        if (growthRate == null)
            return "mature";
        // Convert from decimal to percentage if needed
        double growthPct = Math.abs(growthRate) < 1 ? growthRate * 100 : growthRate;
        if (growthPct > 20)
            return "growth";
        if (growthPct > 5)
            return "mature";
        return "decline";
    }
}
