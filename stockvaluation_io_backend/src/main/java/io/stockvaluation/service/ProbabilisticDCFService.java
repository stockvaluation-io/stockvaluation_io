package io.stockvaluation.service;

import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.dto.valuationOutputDTO.DistributionDTO;
import io.stockvaluation.dto.valuationOutputDTO.SimulationResultsDTO;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.dto.ValuationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class ProbabilisticDCFService {

    @Autowired
    private ValuationOutputService valuationOutputService;

    @Autowired
    private ValuationTemplateService valuationTemplateService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${ml.dcf.forecast.url:http://ml-dcf-forecast:8003}")
    private String mlDcfForecastUrl;

    private static final int DEFAULT_MONTE_CARLO_PATHS = 1000;
    private static final int DEFAULT_HISTOGRAM_BINS = 50;
    private static final double Z_SCORE_90 = 1.645; // For 90% confidence interval

    /**
     * Main method to calculate probabilistic DCF valuation
     * 
     * @param ticker Stock ticker symbol
     * @param financialDataInput Original financial data input
     * @param currentStockPrice Current stock price
     * @param monteCarloPaths Number of Monte Carlo paths (default: 1000)
     * @param companyDataDTO Company data DTO (required for template determination)
     * @return ProbabilisticDCFResult containing distribution and simulation results
     */
    public ProbabilisticDCFResult calculateProbabilisticDCF(
            String ticker,
            FinancialDataInput financialDataInput,
            Double currentStockPrice,
            Integer monteCarloPaths,
            io.stockvaluation.dto.CompanyDataDTO companyDataDTO) {
        
        if (monteCarloPaths == null || monteCarloPaths <= 0) {
            monteCarloPaths = DEFAULT_MONTE_CARLO_PATHS;
        }

        log.info("🎲 [PROBABILISTIC_DCF] Starting probabilistic DCF calculation for {} with {} paths", 
                ticker, monteCarloPaths);

        try {
            // Step 1: Call ML service to get parameter distributions
            Map<String, Object> mlResponse = callMLService(ticker, financialDataInput);
            if (mlResponse == null || !mlResponse.containsKey("predictions")) {
                log.warn("⚠️ [PROBABILISTIC_DCF] ML service returned null or invalid response");
                return null;
            }

            System.out.println("mlResponse: " + mlResponse);
            System.out.println("mlResponse keys: " + mlResponse.keySet());
            Object predictionsObj = mlResponse.get("predictions");
            if (predictionsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> predictions = (Map<String, Object>) predictionsObj;
                System.out.println("mlResponse predictions: " + predictions);
                System.out.println("mlResponse predictions keys: " + predictions.keySet());
                Object predictionsInnerObj = predictions.get("predictions");
                if (predictionsInnerObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> predictionsInner = (Map<String, Object>) predictionsInnerObj;
                    System.out.println("mlResponse predictions predictions: " + predictionsInner);
                    System.out.println("mlResponse predictions predictions keys: " + predictionsInner.keySet());
                    System.out.println("mlResponse predictions predictions values: " + predictionsInner.values());
                }
            }
            // Step 2: Extract distributions from ML response
            Map<String, Map<Integer, DistributionInfo>> distributions = extractDistributions(mlResponse);
            if (distributions.isEmpty()) {
                log.warn("⚠️ [PROBABILISTIC_DCF] No distributions found in ML response");
                return null;
            }

            // Step 3: Get valuation template
            ValuationTemplate template = valuationTemplateService.determineTemplate(financialDataInput, companyDataDTO);

            // Step 4: Run Monte Carlo simulation
            List<Double> valuationPaths = runMonteCarloSimulation(
                    ticker, financialDataInput, distributions, template, monteCarloPaths);
            System.out.println("valuationPaths: " + valuationPaths);
            if (valuationPaths.isEmpty()) {
                log.warn("⚠️ [PROBABILISTIC_DCF] No valuation paths generated");
                return null;
            }

            // Step 5: Aggregate results
            log.info("📊 [PROBABILISTIC_DCF] Aggregating {} valuation paths", valuationPaths.size());
            DistributionDTO distribution = aggregateResults(valuationPaths, currentStockPrice);
            SimulationResultsDTO simulationResults = createSimulationResultsDTO(valuationPaths);
            System.out.println("simulationResults: " + simulationResults);  
            System.out.println("distribution: " + distribution);
            System.out.println("distribution histogram: " + distribution.getHistogram());
            System.out.println("distribution confidenceIntervals: " + distribution.getConfidenceIntervals());
            if (distribution.getConfidenceIntervals() != null) {
                System.out.println("distribution confidenceIntervals keys: " + distribution.getConfidenceIntervals().keySet());
                System.out.println("distribution confidenceIntervals values: " + distribution.getConfidenceIntervals().values());
            }
            System.out.println("distribution probabilityUndervalued: " + distribution.getProbabilityUndervalued());
            System.out.println("distribution probabilityOvervalued: " + distribution.getProbabilityOvervalued());
            log.info("✅ [PROBABILISTIC_DCF] Probabilistic DCF calculation completed successfully. " +
                    "Distribution: histogram={}, CI90={}, CI95={}, probUndervalued={}, probOvervalued={}",
                    distribution.getHistogram() != null ? "present" : "null",
                    distribution.getConfidenceIntervals() != null && distribution.getConfidenceIntervals().containsKey("90") ? "present" : "null",
                    distribution.getConfidenceIntervals() != null && distribution.getConfidenceIntervals().containsKey("95") ? "present" : "null",
                    distribution.getProbabilityUndervalued(),
                    distribution.getProbabilityOvervalued());

            return new ProbabilisticDCFResult(distribution, simulationResults, valuationPaths);

        } catch (Exception e) {
            log.error("❌ [PROBABILISTIC_DCF] Error calculating probabilistic DCF for {}: {}", 
                    ticker, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Inner class to hold probabilistic DCF calculation results
     */
    public static class ProbabilisticDCFResult {
        private final DistributionDTO distribution;
        private final SimulationResultsDTO simulationResults;
        private final List<Double> valuationPaths;
        
        public ProbabilisticDCFResult(DistributionDTO distribution, SimulationResultsDTO simulationResults, List<Double> valuationPaths) {
            this.distribution = distribution;
            this.simulationResults = simulationResults;
            this.valuationPaths = valuationPaths;
        }
        
        public DistributionDTO getDistribution() {
            return distribution;
        }
        
        public SimulationResultsDTO getSimulationResults() {
            return simulationResults;
        }
        
        public List<Double> getValuationPaths() {
            return valuationPaths;
        }
    }

    /**
     * Call ML DCF Forecast service to get parameter distributions
     */
    private Map<String, Object> callMLService(String ticker, FinancialDataInput financialDataInput) {
        try {
            String url = mlDcfForecastUrl + "/api/v1/forecast";
            log.info("📡 [PROBABILISTIC_DCF] Calling ML service: {}", url);

            // Build request payload
            Map<String, Object> request = buildMLServiceRequest(ticker, financialDataInput);
            log.debug("📡 [PROBABILISTIC_DCF] Request payload: {}", request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            // Use exchange() for better error handling and response parsing
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    (Class<Map<String, Object>>) (Class<?>) Map.class
            );

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                Map<String, Object> response = responseEntity.getBody();
                log.info("✅ [PROBABILISTIC_DCF] ML service response received. Status: {}, Response keys: {}", 
                        responseEntity.getStatusCode(), response != null ? response.keySet() : "null");
                return response;
            } else {
                log.warn("⚠️ [PROBABILISTIC_DCF] ML service returned non-2xx status: {}", responseEntity.getStatusCode());
                return null;
            }

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("❌ [PROBABILISTIC_DCF] HTTP error calling ML service: {} - Response body: {}", 
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            return null;
        } catch (org.springframework.web.client.RestClientException e) {
            log.error("❌ [PROBABILISTIC_DCF] RestClientException calling ML service: {}", e.getMessage(), e);
            return null;
        } catch (Exception e) {
            log.error("❌ [PROBABILISTIC_DCF] Unexpected error calling ML service: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build request payload for ML service
     * 
     * Note: ML service requires at least 3 years of historical data.
     * We construct historical years from available data (TTM, LTM, and estimated previous years).
     */
    private Map<String, Object> buildMLServiceRequest(String ticker, FinancialDataInput financialDataInput) {
        Map<String, Object> request = new HashMap<>();
        request.put("ticker", ticker);
        request.put("forecastHorizon", 10); // Default 10 years

        // Build financialData structure
        Map<String, Object> financialData = new HashMap<>();
        
        // Build historicalYears - ML service requires at least 3 years
        List<Map<String, Object>> historicalYears = new ArrayList<>();
        
        if (financialDataInput.getFinancialDataDTO() != null) {
            FinancialDataDTO fd = financialDataInput.getFinancialDataDTO();
            int currentYear = java.time.LocalDate.now().getYear();
            
            // Get current year data (TTM)
            double currentRevenue = fd.getRevenueTTM() != null ? fd.getRevenueTTM() : 0.0;
            double currentOperatingIncome = fd.getOperatingIncomeTTM() != null ? fd.getOperatingIncomeTTM() : 0.0;
            double currentInvestedCapital = 0.0;
            if (fd.getBookValueEqualityTTM() != null && fd.getBookValueDebtTTM() != null) {
                currentInvestedCapital = fd.getBookValueEqualityTTM() + fd.getBookValueDebtTTM();
            }
            
            // Get previous year data (LTM if available, otherwise estimate)
            double prevRevenue = fd.getRevenueLTM() != null ? fd.getRevenueLTM() : currentRevenue;
            double prevOperatingIncome = fd.getOperatingIncomeLTM() != null ? fd.getOperatingIncomeLTM() : currentOperatingIncome;
            double prevInvestedCapital = 0.0;
            if (fd.getBookValueEqualityLTM() != null && fd.getBookValueDebtLTM() != null) {
                prevInvestedCapital = fd.getBookValueEqualityLTM() + fd.getBookValueDebtLTM();
            } else {
                prevInvestedCapital = currentInvestedCapital;
            }
            
            // Estimate growth rate for back-calculating year N-2
            double revenueGrowthRate = 0.0;
            if (prevRevenue > 0 && currentRevenue > prevRevenue) {
                revenueGrowthRate = (currentRevenue - prevRevenue) / prevRevenue;
            } else if (financialDataInput.getCompanyDriveDataDTO() != null && 
                      financialDataInput.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5() != null) {
                revenueGrowthRate = financialDataInput.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5() / 100.0;
            }
            
            // Estimate year N-2 (two years ago)
            double yearN2Revenue = prevRevenue > 0 ? prevRevenue / (1 + revenueGrowthRate) : prevRevenue;
            double yearN2OperatingIncome = prevOperatingIncome;
            if (currentOperatingIncome > 0 && prevOperatingIncome > 0) {
                double marginChange = (currentOperatingIncome / currentRevenue) - (prevOperatingIncome / prevRevenue);
                double estimatedMargin = (prevOperatingIncome / prevRevenue) - marginChange;
                yearN2OperatingIncome = yearN2Revenue * estimatedMargin;
            }
            double yearN2InvestedCapital = prevInvestedCapital;
            
            // Year N-2 (two years ago) - required for minimum 3 years
            Map<String, Object> yearN2 = new HashMap<>();
            yearN2.put("year", currentYear - 2);
            yearN2.put("revenue", Math.max(0, yearN2Revenue));
            yearN2.put("operatingIncome", Math.max(0, yearN2OperatingIncome));
            yearN2.put("investedCapital", Math.max(0, yearN2InvestedCapital));
            historicalYears.add(yearN2);
            
            // Year N-1 (previous year)
            Map<String, Object> yearN1 = new HashMap<>();
            yearN1.put("year", currentYear - 1);
            yearN1.put("revenue", Math.max(0, prevRevenue));
            yearN1.put("operatingIncome", Math.max(0, prevOperatingIncome));
            yearN1.put("investedCapital", Math.max(0, prevInvestedCapital));
            historicalYears.add(yearN1);
        }
        
        financialData.put("historicalYears", historicalYears);

        // Build currentYear
        Map<String, Object> currentYear = new HashMap<>();
        if (financialDataInput.getFinancialDataDTO() != null) {
            FinancialDataDTO fd = financialDataInput.getFinancialDataDTO();
            currentYear.put("year", java.time.LocalDate.now().getYear());
            currentYear.put("revenue", fd.getRevenueTTM());
            currentYear.put("operatingIncome", fd.getOperatingIncomeTTM());
            
            // Calculate invested capital
            double investedCapital = 0.0;
            if (fd.getBookValueEqualityTTM() != null && fd.getBookValueDebtTTM() != null) {
                investedCapital = fd.getBookValueEqualityTTM() + fd.getBookValueDebtTTM();
            }
            currentYear.put("investedCapital", investedCapital);
            
            // Add metrics if available from company drive data
            if (financialDataInput.getCompanyDriveDataDTO() != null) {
                if (financialDataInput.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5() != null) {
                    currentYear.put("revenueGrowthRate", financialDataInput.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5());
                }
                if (financialDataInput.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() != null) {
                    currentYear.put("operatingMargin", financialDataInput.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin());
                }
                if (financialDataInput.getCompanyDriveDataDTO().getSalesToCapitalYears1To5() != null) {
                    currentYear.put("salesToCapitalRatio", financialDataInput.getCompanyDriveDataDTO().getSalesToCapitalYears1To5());
                }
                if (financialDataInput.getCompanyDriveDataDTO().getInitialCostCapital() != null) {
                    currentYear.put("costOfCapital", financialDataInput.getCompanyDriveDataDTO().getInitialCostCapital() / 100.0); // Convert from percentage
                }
            }
        }
        financialData.put("currentYear", currentYear);
        request.put("financialData", financialData);

        // Build companyInfo
        Map<String, Object> companyInfo = new HashMap<>();
        if (financialDataInput.getFinancialDataDTO() != null) {
            FinancialDataDTO fd = financialDataInput.getFinancialDataDTO();
            companyInfo.put("debt", fd.getBookValueDebtTTM());
            companyInfo.put("cash", fd.getCashAndMarkablTTM());
            companyInfo.put("shares", fd.getNoOfShareOutstanding());
        }
        if (financialDataInput.getCompanyDriveDataDTO() != null) {
            companyInfo.put("riskFreeRate", financialDataInput.getCompanyDriveDataDTO().getRiskFreeRate());
        }
        if (financialDataInput.getBasicInfoDataDTO() != null && financialDataInput.getBasicInfoDataDTO().getBeta() != null) {
            companyInfo.put("beta", financialDataInput.getBasicInfoDataDTO().getBeta());
        }
        request.put("companyInfo", companyInfo);

        return request;
    }

    /**
     * Extract distributions from ML service response
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<Integer, DistributionInfo>> extractDistributions(Map<String, Object> mlResponse) {
        Map<String, Map<Integer, DistributionInfo>> distributions = new HashMap<>();
        
        Map<String, Object> predictions = (Map<String, Object>) mlResponse.get("predictions");
        if (predictions == null) {
            return distributions;
        }

        // Extract distributions for each metric
        String[] metrics = {"revenue_growth_rate", "operating_margin", "sales_to_capital_ratio", "cost_of_capital"};
        
        int totalExtracted = 0;
        int totalSkipped = 0;
        
        for (String metric : metrics) {
            List<Map<String, Object>> metricPredictions = (List<Map<String, Object>>) predictions.get(metric);
            if (metricPredictions == null) {
                log.warn("⚠️ [PROBABILISTIC_DCF] No predictions found for metric: {}", metric);
                totalSkipped++;
                continue;
            }

            Map<Integer, DistributionInfo> metricDistributions = new HashMap<>();
            int extractedForMetric = 0;
            int skippedForMetric = 0;
            
            for (Map<String, Object> prediction : metricPredictions) {
                Integer year = (Integer) prediction.get("year");
                if (year == null) {
                    skippedForMetric++;
                    continue;
                }

                Map<String, Object> distributionMap = (Map<String, Object>) prediction.get("distribution");
                if (distributionMap == null) {
                    log.debug("⚠️ [PROBABILISTIC_DCF] No distribution map for {} year {}", metric, year);
                    skippedForMetric++;
                    continue;
                }

                Double mean = getDoubleValue(distributionMap, "mean");
                Double std = getDoubleValue(distributionMap, "std");
                
                // Validate mean is not NaN or infinite
                if (mean != null && (Double.isNaN(mean) || Double.isInfinite(mean))) {
                    log.warn("⚠️ [PROBABILISTIC_DCF] Invalid mean (NaN/Inf) for {} year {}: {}", metric, year, mean);
                    mean = null;
                }
                
                // Validate std is not NaN or infinite
                if (std != null && (Double.isNaN(std) || Double.isInfinite(std))) {
                    log.warn("⚠️ [PROBABILISTIC_DCF] Invalid std (NaN/Inf) for {} year {}: {}", metric, year, std);
                    std = null;
                }
                
                // If std not available, estimate from percentiles
                if (std == null || std <= 0) {
                    Map<String, Object> percentiles = (Map<String, Object>) distributionMap.get("percentiles");
                    if (percentiles != null) {
                        Double p5 = getDoubleValue(percentiles, "p5");
                        Double p95 = getDoubleValue(percentiles, "p95");
                        
                        // Validate percentiles
                        if (p5 != null && (Double.isNaN(p5) || Double.isInfinite(p5))) {
                            log.warn("⚠️ [PROBABILISTIC_DCF] Invalid p5 (NaN/Inf) for {} year {}", metric, year);
                            p5 = null;
                        }
                        if (p95 != null && (Double.isNaN(p95) || Double.isInfinite(p95))) {
                            log.warn("⚠️ [PROBABILISTIC_DCF] Invalid p95 (NaN/Inf) for {} year {}", metric, year);
                            p95 = null;
                        }
                        
                        if (p5 != null && p95 != null && p95 > p5) {
                            std = (p95 - p5) / (2 * Z_SCORE_90);
                            log.debug("📊 [PROBABILISTIC_DCF] Estimated std from percentiles for {} year {}: {}", metric, year, std);
                        }
                    }
                }

                if (mean != null && std != null && std > 0 && !Double.isNaN(mean) && !Double.isInfinite(mean) 
                    && !Double.isNaN(std) && !Double.isInfinite(std)) {
                    metricDistributions.put(year, new DistributionInfo(mean, std));
                    extractedForMetric++;
                    log.debug("✅ [PROBABILISTIC_DCF] Extracted distribution for {} year {}: mean={}, std={}", 
                            metric, year, mean, std);
                } else {
                    skippedForMetric++;
                    String reason = "";
                    if (mean == null) reason += "mean=null ";
                    else if (Double.isNaN(mean) || Double.isInfinite(mean)) reason += "mean=invalid ";
                    if (std == null) reason += "std=null ";
                    else if (std <= 0) reason += "std<=0 ";
                    else if (Double.isNaN(std) || Double.isInfinite(std)) reason += "std=invalid ";
                    log.warn("⚠️ [PROBABILISTIC_DCF] Skipped distribution for {} year {}: {}", metric, year, reason.trim());
                }
            }

            if (!metricDistributions.isEmpty()) {
                distributions.put(metric, metricDistributions);
                totalExtracted++;
                log.info("✅ [PROBABILISTIC_DCF] Extracted {} distributions for {} ({} years)", 
                        extractedForMetric, metric, metricDistributions.size());
            } else {
                totalSkipped++;
                log.warn("⚠️ [PROBABILISTIC_DCF] No valid distributions extracted for {} (skipped {} years)", 
                        metric, skippedForMetric);
            }
        }

        log.info("📊 [PROBABILISTIC_DCF] Extracted distributions: {} metrics successful, {} metrics skipped. " +
                "Total distributions: {}", totalExtracted, totalSkipped, 
                distributions.values().stream().mapToInt(Map::size).sum());
        return distributions;
    }

    /**
     * Run Monte Carlo simulation
     */
    private List<Double> runMonteCarloSimulation(
            String ticker,
            FinancialDataInput baseFinancialDataInput,
            Map<String, Map<Integer, DistributionInfo>> distributions,
            ValuationTemplate template,
            int numPaths) {

        log.info("🎲 [PROBABILISTIC_DCF] Running {} Monte Carlo paths", numPaths);

        List<Double> paths = IntStream.range(0, numPaths)
                .parallel()
                .mapToObj(pathIndex -> {
                    try {
                        // Sample parameters from distributions
                        FinancialDataInput sampledInput = sampleParameters(
                                new FinancialDataInput(baseFinancialDataInput), 
                                distributions);

                        // Run DCF calculation with sampled parameters
                        ValuationOutputDTO result = valuationOutputService.getValuationOutput(
                                ticker, sampledInput, false, template);

                        if (result != null && result.getCompanyDTO() != null) {
                            Double valuePerShare = result.getCompanyDTO().getEstimatedValuePerShare();
                            if (valuePerShare != null && valuePerShare > 0) {
                                return valuePerShare;
                            }
                        }
                        return null;
                    } catch (Exception e) {
                        log.debug("⚠️ [PROBABILISTIC_DCF] Error in path {}: {}", pathIndex, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        log.info("📈 [PROBABILISTIC_DCF] Completed {} successful paths out of {} total paths", paths.size(), numPaths);
        
        // Validate that paths show variation (not all identical)
        if (paths.size() > 1) {
            double firstValue = paths.get(0);
            boolean allIdentical = paths.stream().allMatch(v -> Math.abs(v - firstValue) < 1e-6);
            
            if (allIdentical) {
                log.warn("⚠️ [PROBABILISTIC_DCF] WARNING: All {} valuation paths are identical (value={}). " +
                        "This suggests parameters are not being sampled properly. Check distributions and fallback logic.",
                        paths.size(), firstValue);
            } else {
                // Calculate variation statistics
                double min = paths.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                double max = paths.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                double mean = paths.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                double std = Math.sqrt(paths.stream()
                        .mapToDouble(v -> Math.pow(v - mean, 2))
                        .average()
                        .orElse(0.0));
                double coefficientOfVariation = (std / mean) * 100.0;
                
                log.info("📊 [PROBABILISTIC_DCF] Path variation: min={}, max={}, mean={}, std={}, CV={}%", 
                        min, max, mean, std, coefficientOfVariation);
                
                if (coefficientOfVariation < 0.1) {
                    log.warn("⚠️ [PROBABILISTIC_DCF] Low variation detected (CV={}%). " +
                            "Monte Carlo simulation may not be sampling parameters effectively.",
                            coefficientOfVariation);
                }
            }
        } else if (paths.size() == 1) {
            log.warn("⚠️ [PROBABILISTIC_DCF] Only 1 successful path generated. Check parameter sampling and DCF calculation.");
        }
        
        return paths;
    }

    /**
     * Sample parameters from distributions and update FinancialDataInput
     * 
     * Note: Java backend uses simplified parameterization:
     * - Year 1: revenueNextYear
     * - Years 2-5: compoundAnnualGrowth2_5
     * - Years 6+: Converge to terminal growth (risk-free rate)
     * 
     * We sample from ML distributions and map to this structure:
     * - Year 1 growth -> revenueNextYear
     * - Average of years 2-5 -> compoundAnnualGrowth2_5
     * - Year 1 operating margin -> targetPreTaxOperatingMargin
     * - Year 1 sales-to-capital -> salesToCapitalYears1To5
     * - Year 1 cost of capital -> initialCostCapital
     */
    private FinancialDataInput sampleParameters(
            FinancialDataInput financialDataInput,
            Map<String, Map<Integer, DistributionInfo>> distributions) {

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Sample revenue growth rates
        Map<Integer, DistributionInfo> revenueGrowthDist = distributions.get("revenue_growth_rate");
        if (revenueGrowthDist != null && financialDataInput.getCompanyDriveDataDTO() != null) {
            // Sample year 1 growth for revenueNextYear
            DistributionInfo year1Dist = revenueGrowthDist.get(1);
            if (year1Dist != null) {
                double sampledYear1Growth = sampleFromNormal(year1Dist.mean, year1Dist.std, random);
                // revenueNextYear format in ValuationOutputService (line 575-579):
                // - If segments exist: revenueNextYear * 100 (expects decimal like 0.05 for 5%)
                // - If no segments: revenueNextYear used directly (expects percentage like 5.0 for 5%)
                // ML service returns percentage (e.g., 37.67 for 37.67%)
                // We need to set it as a growth rate, not absolute revenue!
                if (financialDataInput.getSegments() != null && 
                    financialDataInput.getSegments().getSegments() != null && 
                    financialDataInput.getSegments().getSegments().size() > 1) {
                    // With segments: set as decimal (0.3767 for 37.67%)
                    financialDataInput.setRevenueNextYear(sampledYear1Growth / 100.0);
                } else {
                    // Without segments: set as percentage (37.67 for 37.67%)
                    financialDataInput.setRevenueNextYear(sampledYear1Growth);
                }
                log.debug("🎲 [PROBABILISTIC_DCF] Sampled revenue growth year 1: {}% (set as revenueNextYear={})", 
                        sampledYear1Growth, financialDataInput.getRevenueNextYear());
            }
            
            // Sample average growth for years 2-5 (compoundAnnualGrowth2_5)
            // Average the distributions for years 2, 3, 4, 5
            double sumMean = 0.0;
            double sumVar = 0.0;
            int count = 0;
            for (int year = 2; year <= 5; year++) {
                DistributionInfo dist = revenueGrowthDist.get(year);
                if (dist != null) {
                    sumMean += dist.mean;
                    sumVar += dist.std * dist.std; // Variance
                    count++;
                }
            }
            if (count > 0) {
                double avgMean = sumMean / count;
                double avgStd = Math.sqrt(sumVar / count); // Standard deviation of average
                double sampledAvgGrowth = sampleFromNormal(avgMean, avgStd, random);
                financialDataInput.setCompoundAnnualGrowth2_5(sampledAvgGrowth);
            } else if (year1Dist != null) {
                // Fallback: use year 1 distribution
                double sampledGrowth = sampleFromNormal(year1Dist.mean, year1Dist.std, random);
                financialDataInput.setCompoundAnnualGrowth2_5(sampledGrowth);
            }
        }

        // Sample operating margin (use year 1 as target)
        Map<Integer, DistributionInfo> operatingMarginDist = distributions.get("operating_margin");
        if (operatingMarginDist != null && !operatingMarginDist.isEmpty()) {
            DistributionInfo dist = operatingMarginDist.get(1);
            if (dist != null) {
                double sampledMargin = sampleFromNormal(dist.mean, dist.std, random);
                // Ensure margin is reasonable (0-100%)
                sampledMargin = Math.max(0, Math.min(100, sampledMargin));
                financialDataInput.setTargetPreTaxOperatingMargin(sampledMargin);
                log.debug("🎲 [PROBABILISTIC_DCF] Sampled operating margin: {}% (from distribution mean={}, std={})", 
                        sampledMargin, dist.mean, dist.std);
            } else {
                log.warn("⚠️ [PROBABILISTIC_DCF] Operating margin distribution exists but year 1 is missing, using fallback");
                // Fallback: use historical operating margin if available
                applyOperatingMarginFallback(financialDataInput, random);
            }
        } else {
            log.warn("⚠️ [PROBABILISTIC_DCF] No operating margin distribution available, using fallback");
            // Fallback: use historical operating margin if available
            applyOperatingMarginFallback(financialDataInput, random);
        }

        // Sample sales-to-capital ratio (use year 1)
        Map<Integer, DistributionInfo> salesToCapitalDist = distributions.get("sales_to_capital_ratio");
        if (salesToCapitalDist != null) {
            DistributionInfo dist = salesToCapitalDist.get(1);
            if (dist != null) {
                double sampledRatio = sampleFromNormal(dist.mean, dist.std, random);
                // Ensure ratio is positive
                sampledRatio = Math.max(0.1, sampledRatio);
                financialDataInput.setSalesToCapitalYears1To5(sampledRatio);
                // Also set years 6-10 (can use same value or sample separately)
                financialDataInput.setSalesToCapitalYears6To10(sampledRatio);
            }
        }

        // Sample cost of capital (use year 1)
        Map<Integer, DistributionInfo> costOfCapitalDist = distributions.get("cost_of_capital");
        if (costOfCapitalDist != null && financialDataInput.getCompanyDriveDataDTO() != null) {
            DistributionInfo dist = costOfCapitalDist.get(1);
            if (dist != null) {
                double sampledCost = sampleFromNormal(dist.mean, dist.std, random);
                // ML service returns percentage (e.g., 10.0 for 10%)
                // Java backend expects percentage (e.g., 10.0 for 10%)
                sampledCost = Math.max(3.0, Math.min(25.0, sampledCost)); // Reasonable bounds
                financialDataInput.setInitialCostCapital(sampledCost);
                log.debug("🎲 [PROBABILISTIC_DCF] Sampled cost of capital: {}% (from distribution mean={}, std={})", 
                        sampledCost, dist.mean, dist.std);
            } else {
                log.warn("⚠️ [PROBABILISTIC_DCF] Cost of capital distribution exists but year 1 is missing");
            }
        } else {
            log.debug("🎲 [PROBABILISTIC_DCF] No cost of capital distribution available, using original value");
        }

        // Log summary of sampled parameters for debugging
        if (log.isDebugEnabled()) {
            StringBuilder sampledParams = new StringBuilder("🎲 [PROBABILISTIC_DCF] Sampled parameters: ");
            if (financialDataInput.getRevenueNextYear() != null) {
                sampledParams.append(String.format("revenueNextYear=%.2f, ", financialDataInput.getRevenueNextYear()));
            }
            if (financialDataInput.getCompoundAnnualGrowth2_5() != null) {
                sampledParams.append(String.format("CAGR2_5=%.2f%%, ", financialDataInput.getCompoundAnnualGrowth2_5()));
            }
            if (financialDataInput.getTargetPreTaxOperatingMargin() != null) {
                sampledParams.append(String.format("opMargin=%.2f%%, ", financialDataInput.getTargetPreTaxOperatingMargin()));
            }
            if (financialDataInput.getSalesToCapitalYears1To5() != null) {
                sampledParams.append(String.format("salesToCapital=%.2f, ", financialDataInput.getSalesToCapitalYears1To5()));
            }
            if (financialDataInput.getInitialCostCapital() != null) {
                sampledParams.append(String.format("costOfCapital=%.2f%%", financialDataInput.getInitialCostCapital()));
            }
            log.debug(sampledParams.toString());
        }

        return financialDataInput;
    }

    /**
     * Apply fallback logic for operating margin when distribution is missing
     * Uses historical operating margin from financial data if available,
     * otherwise uses a reasonable default with variation
     */
    private void applyOperatingMarginFallback(
            FinancialDataInput financialDataInput, 
            ThreadLocalRandom random) {
        
        double fallbackMean = 20.0; // Default 20% operating margin
        double fallbackStd = 5.0;   // Default 5% standard deviation
        
        // Try to get historical operating margin from financial data
        if (financialDataInput.getFinancialDataDTO() != null) {
            FinancialDataDTO fd = financialDataInput.getFinancialDataDTO();
            
            // Calculate operating margin from TTM data if available
            if (fd.getRevenueTTM() != null && fd.getOperatingIncomeTTM() != null 
                && fd.getRevenueTTM() > 0) {
                double ttmMargin = (fd.getOperatingIncomeTTM() / fd.getRevenueTTM()) * 100.0;
                if (!Double.isNaN(ttmMargin) && !Double.isInfinite(ttmMargin) && ttmMargin >= 0 && ttmMargin <= 100) {
                    fallbackMean = ttmMargin;
                    log.debug("📊 [PROBABILISTIC_DCF] Using TTM operating margin as fallback: {}%", fallbackMean);
                }
            }
            
            // Also check LTM if available and use average
            if (fd.getRevenueLTM() != null && fd.getOperatingIncomeLTM() != null 
                && fd.getRevenueLTM() > 0) {
                double ltmMargin = (fd.getOperatingIncomeLTM() / fd.getRevenueLTM()) * 100.0;
                if (!Double.isNaN(ltmMargin) && !Double.isInfinite(ltmMargin) && ltmMargin >= 0 && ltmMargin <= 100) {
                    // Average TTM and LTM if both are valid
                    if (fallbackMean != 20.0) {
                        fallbackMean = (fallbackMean + ltmMargin) / 2.0;
                    } else {
                        fallbackMean = ltmMargin;
                    }
                    log.debug("📊 [PROBABILISTIC_DCF] Using average TTM/LTM operating margin as fallback: {}%", fallbackMean);
                }
            }
            
            // Calculate std from historical variation if we have both TTM and LTM
            if (fd.getRevenueTTM() != null && fd.getOperatingIncomeTTM() != null 
                && fd.getRevenueTTM() > 0
                && fd.getRevenueLTM() != null && fd.getOperatingIncomeLTM() != null 
                && fd.getRevenueLTM() > 0) {
                double ttmMargin = (fd.getOperatingIncomeTTM() / fd.getRevenueTTM()) * 100.0;
                double ltmMargin = (fd.getOperatingIncomeLTM() / fd.getRevenueLTM()) * 100.0;
                if (!Double.isNaN(ttmMargin) && !Double.isNaN(ltmMargin) 
                    && !Double.isInfinite(ttmMargin) && !Double.isInfinite(ltmMargin)) {
                    fallbackStd = Math.abs(ttmMargin - ltmMargin);
                    if (fallbackStd == 0 || fallbackStd < 1.0) {
                        fallbackStd = Math.max(1.0, fallbackMean * 0.1); // At least 1% or 10% of mean
                    }
                }
            } else {
                // Use 10% of mean as default std
                fallbackStd = Math.max(1.0, fallbackMean * 0.1);
            }
        }
        
        // Check if company drive data has a target margin
        if (financialDataInput.getCompanyDriveDataDTO() != null 
            && financialDataInput.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin() != null) {
            Double targetMargin = financialDataInput.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin();
            if (targetMargin >= 0 && targetMargin <= 100) {
                // Blend historical with target (70% historical, 30% target)
                fallbackMean = 0.7 * fallbackMean + 0.3 * targetMargin;
                log.debug("📊 [PROBABILISTIC_DCF] Blended historical and target operating margin: {}%", fallbackMean);
            }
        }
        
        // Sample from fallback distribution
        double sampledMargin = sampleFromNormal(fallbackMean, fallbackStd, random);
        sampledMargin = Math.max(0, Math.min(100, sampledMargin)); // Ensure reasonable bounds
        
        financialDataInput.setTargetPreTaxOperatingMargin(sampledMargin);
        log.info("🔄 [PROBABILISTIC_DCF] Applied operating margin fallback: sampled={}% (mean={}%, std={}%)", 
                sampledMargin, fallbackMean, fallbackStd);
    }

    /**
     * Sample from normal distribution
     */
    private double sampleFromNormal(double mean, double std, ThreadLocalRandom random) {
        // Box-Muller transform for normal distribution
        double u1 = random.nextDouble();
        double u2 = random.nextDouble();
        double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return mean + std * z;
    }

    /**
     * Aggregate Monte Carlo results into distribution statistics
     */
    private DistributionDTO aggregateResults(List<Double> valuationPaths, Double currentStockPrice) {
        if (valuationPaths.isEmpty()) {
            return null;
        }

        Collections.sort(valuationPaths);
        int n = valuationPaths.size();

        // Calculate statistics
        double mean = valuationPaths.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = valuationPaths.get(0);
        double max = valuationPaths.get(n - 1);
        double p5 = getPercentile(valuationPaths, 5);
        double p50 = getPercentile(valuationPaths, 50);
        double p95 = getPercentile(valuationPaths, 95);

        // Generate histogram
        DistributionDTO.HistogramDTO histogram = generateHistogram(valuationPaths);

        // Calculate confidence intervals
        Map<String, DistributionDTO.ConfidenceIntervalDTO> confidenceIntervals = new HashMap<>();
        double p2_5 = getPercentile(valuationPaths, 2.5);
        double p97_5 = getPercentile(valuationPaths, 97.5);
        
        confidenceIntervals.put("90", new DistributionDTO.ConfidenceIntervalDTO(p5, p95));
        confidenceIntervals.put("95", new DistributionDTO.ConfidenceIntervalDTO(p2_5, p97_5));

        // Calculate probabilities
        Double probabilityUndervalued = null;
        Double probabilityOvervalued = null;
        if (currentStockPrice != null && currentStockPrice > 0) {
            long undervaluedCount = valuationPaths.stream()
                    .filter(v -> v > currentStockPrice)
                    .count();
            long overvaluedCount = valuationPaths.stream()
                    .filter(v -> v < currentStockPrice)
                    .count();
            
            probabilityUndervalued = (double) undervaluedCount / n;
            probabilityOvervalued = (double) overvaluedCount / n;
        }

        DistributionDTO distribution = new DistributionDTO();
        distribution.setHistogram(histogram);
        distribution.setConfidenceIntervals(confidenceIntervals);
        distribution.setProbabilityUndervalued(probabilityUndervalued);
        distribution.setProbabilityOvervalued(probabilityOvervalued);

        log.info("✅ [PROBABILISTIC_DCF] Aggregated results: mean={}, min={}, max={}, p50={}", 
                mean, min, max, p50);

        return distribution;
    }

    /**
     * Generate histogram from valuation paths
     */
    private DistributionDTO.HistogramDTO generateHistogram(List<Double> valuationPaths) {
        double min = valuationPaths.get(0);
        double max = valuationPaths.get(valuationPaths.size() - 1);
        int numBins = DEFAULT_HISTOGRAM_BINS;
        double binWidth = (max - min) / numBins;

        List<Double> bins = new ArrayList<>();
        List<Double> frequencies = new ArrayList<>();

        for (int i = 0; i < numBins; i++) {
            final int binIndex = i;
            double binStart = min + binIndex * binWidth;
            double binEnd = binStart + binWidth;
            double binCenter = (binStart + binEnd) / 2.0;

            long count = valuationPaths.stream()
                    .filter(v -> v >= binStart && (binIndex == numBins - 1 ? v <= binEnd : v < binEnd))
                    .count();

            bins.add(binCenter);
            frequencies.add((double) count / valuationPaths.size());
        }

        DistributionDTO.HistogramDTO histogram = new DistributionDTO.HistogramDTO();
        histogram.setBins(bins);
        histogram.setFrequencies(frequencies);

        return histogram;
    }

    /**
     * Calculate percentile from sorted list
     */
    private double getPercentile(List<Double> sortedList, double percentile) {
        if (sortedList.isEmpty()) {
            return 0.0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        return sortedList.get(index);
    }

    /**
     * Helper to safely get double value from map
     */
    @SuppressWarnings("unchecked")
    private Double getDoubleValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Inner class to hold distribution information
     */
    private static class DistributionInfo {
        final double mean;
        final double std;

        DistributionInfo(double mean, double std) {
            this.mean = mean;
            this.std = std;
        }
    }

    /**
     * Create SimulationResultsDTO from distribution
     */
    public SimulationResultsDTO createSimulationResultsDTO(List<Double> valuationPaths) {
        if (valuationPaths == null || valuationPaths.isEmpty()) {
            return new SimulationResultsDTO(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        Collections.sort(valuationPaths);
        int n = valuationPaths.size();

        double average = valuationPaths.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = valuationPaths.get(0);
        double max = valuationPaths.get(n - 1);
        double fifthPercentile = getPercentile(valuationPaths, 5);
        double fiftyPercentile = getPercentile(valuationPaths, 50);
        double ninetyFifthPercentile = getPercentile(valuationPaths, 95);

        return new SimulationResultsDTO(
                average, min, max, fifthPercentile, fiftyPercentile, ninetyFifthPercentile);
    }
}

