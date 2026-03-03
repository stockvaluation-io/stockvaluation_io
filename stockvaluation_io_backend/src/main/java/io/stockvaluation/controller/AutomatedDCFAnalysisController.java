package io.stockvaluation.controller;

import jakarta.servlet.http.HttpServletRequest;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationOutputDTO.CalibrationResultDTO;
import io.stockvaluation.dto.valuationOutputDTO.CompanyDTO;
import io.stockvaluation.dto.valuationOutputDTO.DistributionDTO;
import io.stockvaluation.dto.valuationOutputDTO.FinancialDTO;
import io.stockvaluation.dto.valuationOutputDTO.IntrinsicPricingDTO;
import io.stockvaluation.dto.valuationOutputDTO.SimulationResultsDTO;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.service.*;
import io.stockvaluation.utils.ResponseGenerator;
import io.stockvaluation.utils.SegmentParameterContext;
import io.stockvaluation.controller.BotDetector;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.stockvaluation.service.GrowthCalculatorService.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/automated-dcf-analysis")

public class AutomatedDCFAnalysisController {

        @Autowired
        CommonService commonService;

        @Autowired
        SyntheticRatingService syntheticRatingService;

        @Autowired
        CostOfCapitalService costOfCapitalService;

        @Autowired
        OptionValueService optionValueService;

        @Autowired
        ValuationOutputService valuationOutputService;

        @Autowired
        ValuationTemplateService valuationTemplateService;

        @Autowired
        DividendDiscountModelService dividendDiscountModelService;

        @Autowired
        ModelSelectionService modelSelectionService;

        @Autowired
        IntrinsicPricingService intrinsicPricingService;

        @Autowired
        ProbabilisticDCFService probabilisticDCFService;

        @Autowired
        MonteCarloValuationService monteCarloValuationService;

        @Autowired
        ValuationAnimationService valuationAnimationService;

        @Value("${intrinsic.pricing.enable.v1.v2.comparison:false}")
        private boolean enableV1V2Comparison;

        /*
         * @Operation(summary = "Calculate R&D convertor - Test controller", description
         * = "This API is used for R and D convertor calculations testing.")
         * 
         * @GetMapping("/RandD-convertor")
         * public ResponseEntity<ResponseDTO<Object>> calculateRD(@RequestParam String
         * industry, @RequestParam Double marginalTaxRate, @RequestParam boolean
         * requireRdConverter) {
         * 
         * if (!requireRdConverter) {
         * return ResponseGenerator.generateSuccessResponse(new RDResult(0.00, 0.00,
         * 0.00, 0.00));
         * }
         * try {
         * return ResponseGenerator.generateSuccessResponse(commonService.
         * calculateR_DConvertorValue(industry, marginalTaxRate, Map.of()));
         * } catch (RuntimeException e) {
         * return ResponseGenerator.generateNotFoundResponse(e.getMessage());
         * } catch (Exception e) {
         * return ResponseGenerator.generateExceptionResponseDTO(e);
         * }
         * }
         */

        @Operation(summary = "Calculate Operating Lease convertor - Test controller", description = "This API is used for Operating Lease convertor calculations testing.")
        @GetMapping("/operating-lease-converter")
        public ResponseEntity<?> calculateLease(@RequestParam boolean requiredLeaseConvertor) {

                if (!requiredLeaseConvertor) {
                        log.info("Lease convertor is not required , Skipping calculation");
                        return ResponseGenerator.generateSuccessResponse(new LeaseResultDTO(0.00, 0.00, 0.00, 0.00));
                }

                try {
                        return ResponseGenerator
                                        .generateSuccessResponse(commonService.calculateOperatingLeaseConvertor());
                } catch (RuntimeException e) {
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                }

        }

        // synthetic Rating controller
        @Operation(summary = "Calculate Synthetic Rating", description = "This API is used for Calculating Synthetic Rating Calculation Testing")
        @GetMapping("/synthetic-rating-convertor")
        public ResponseEntity<?> calculateSyntheticRating(String ticker, boolean requiredLeaseConvertor,
                        @RequestParam(required = false) Double leaseExpenseCurrentYear,
                        @RequestParam(required = false) Double[] commitments,
                        @RequestParam(required = false) Double futureCommitment) {
                if (Objects.isNull(ticker)) {
                        return ResponseGenerator.generateBadRequestResponse("Enter Company Ticker");
                }
                try {
                        return ResponseGenerator.generateSuccessResponse(
                                        syntheticRatingService.calculateSyntheticRating(ticker, requiredLeaseConvertor,
                                                        leaseExpenseCurrentYear, commitments, futureCommitment));
                } catch (RuntimeException e) {
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                }
        }

        // cost of capital

        @Operation(summary = "Calculate Cost Of Capital Based on Decile Grouping", description = "This API is used for Calculating Cost Of Capital Based on Decile Grouping Testing")
        @GetMapping("/cotOfCapital/basedOnDecileGroup")
        public ResponseEntity<?> calculateCostOfCapital(@RequestParam String region,
                        @RequestParam String riskGrouping) {

                try {
                        return ResponseGenerator.generateSuccessResponse(
                                        costOfCapitalService.costOfCapitalBasedOnDecile(region, riskGrouping));
                } catch (RuntimeException e) {
                        log.error("Error occurred while processing the request: {}", e.getMessage(), e); // Log the
                                                                                                         // error with
                                                                                                         // the stack
                                                                                                         // trace.
                        return ResponseGenerator.generateNotFoundResponse(e.getMessage());
                } catch (Exception e) {
                        log.error("Unexpected error: {}", e.getMessage(), e); // Log unexpected errors.
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                }

        }

        @Operation(summary = "Calculate Cost Of Capital Based on Industry", description = "This API is used for Calculating Cost Of Capital Based on Industry Testing")
        @GetMapping("/cotOfCapital/byIndustry")
        public ResponseEntity<?> calculateCostOfCapitalByIndustry(@RequestParam String ticker,
                        @RequestParam String industry) {

                if (!industry.equals("Single Business(US)") && !industry.equals("Single Business(Global)")) {
                        return ResponseGenerator.generateNotFoundResponse(
                                        "Please enter valid industry, valid industry: Single Business(US) OR Single Business(Global)");
                }
                try {
                        return ResponseGenerator.generateSuccessResponse(
                                        costOfCapitalService.costOfCapitalByIndustry(ticker, industry));
                } catch (RuntimeException e) {
                        log.error("Error occurred while processing the request: {}", e.getMessage(), e); // Log the
                                                                                                         // error with
                                                                                                         // the stack
                                                                                                         // trace.
                        return ResponseGenerator.generateNotFoundResponse(e.getMessage());
                } catch (Exception e) {
                        log.error("Unexpected error: {}", e.getMessage(), e); // Log unexpected errors.
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                }

        }

        // option value
        @GetMapping("/option-value")
        public ResponseEntity<?> calculateOptionValue(@RequestParam String ticker, @RequestParam Double strikePrice,
                        @RequestParam Double avgMaturity, @RequestParam Double optionStanding,
                        @RequestParam Double standardDeviation) {
                try {
                        return ResponseGenerator.generateSuccessResponse(optionValueService.calculateOptionValue(ticker,
                                        strikePrice, avgMaturity, optionStanding, standardDeviation));
                } catch (RuntimeException e) {
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                }
        }

        @PostMapping("/valuation-output")
        public ResponseEntity<?> getValuationOutput(@RequestParam String ticker,
                        @RequestBody FinancialDataInput financialDataInputOverrides) {
                try {
                        log.info("POST /valuation-output - ticker: {} (MINIMAL OVERRIDE PATTERN)", ticker);
                        log.info("   Received {} override parameter(s)",
                                        countNonNullFields(financialDataInputOverrides));

                        // Use shared valuation logic with overrides, no story generation for AI tool
                        // calls
                        boolean addStory = false;
                        boolean enableDCFAnalysis = false;
                        boolean addIntrinsicPricing = false; // Default false for API calls

                        ValuationOutputDTO valuationOutputDTO = calculateValuation(
                                        ticker, financialDataInputOverrides, addStory, enableDCFAnalysis,
                                        addIntrinsicPricing);

                        return ResponseGenerator.generateSuccessResponse(valuationOutputDTO);

                } catch (RuntimeException e) {
                        log.error("Error in POST /valuation-output for ticker {}", ticker, e);
                        e.printStackTrace();
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                } finally {
                        SegmentParameterContext.clear();
                }
        }

        @GetMapping("/{ticker}/story-valuation-output")
        public ResponseEntity<?> getValuationOutputNew(@PathVariable String ticker,
                        @RequestParam(value = "enableSegments", required = false, defaultValue = "true") Boolean enableSegments,
                        @RequestParam(value = "addIntrinsicPricing", required = false, defaultValue = "false") Boolean addIntrinsicPricing,
                        @RequestParam(value = "useIntrinsicPricingV2", required = false, defaultValue = "false") Boolean useIntrinsicPricingV2,
                        @RequestParam(value = "enableMonteCarlo", required = false, defaultValue = "false") Boolean enableMonteCarlo,
                        HttpServletRequest request,
                        Authentication auth) {

                try {
                        log.info("GET /{}/story-valuation-output (UI ENDPOINT - WITH STORY)", ticker);

                        // Bot detection only affects story generation, NOT valuation calculations
                        boolean addStory = !BotDetector.isBot(request);
                        boolean enableDCFAnalysis = false;

                        // Use shared valuation logic - no overrides for GET endpoint
                        ValuationOutputDTO valuationOutputDTO = calculateValuation(
                                        ticker, null, addStory, enableDCFAnalysis, addIntrinsicPricing,
                                        useIntrinsicPricingV2, enableMonteCarlo);

                        return ResponseGenerator.generateSuccessResponse(valuationOutputDTO);

                } catch (RuntimeException e) {
                        log.error("Error in GET /{}/story-valuation-output", ticker, e);
                        System.out.println(e.getMessage());
                        return ResponseGenerator.generateExceptionResponseDTO(e);
                } finally {
                        SegmentParameterContext.clear();
                }
        }

        /**
         * Core valuation calculation logic shared between POST and GET endpoints.
         * Ensures consistent step ordering and data processing for both endpoints.
         * 
         * Step Order (aligned for consistency):
         * 1. Fetch company data from Yahoo Finance
         * 2. Determine valuation template
         * 3. Initialize financial data with baseline values
         * 4. Apply user overrides (if any)
         * 5. Adjust sales-to-capital ratio
         * 6. Run initial valuation check
         * 7. Apply calibration and ML adjustments (includes segment analysis)
         * 8. Single calibration to market price
         * 9. Process scenario valuation
         * 10. Calculate DDM (if applicable)
         * 11. Add intrinsic pricing (if requested)
         * 12. Add story (if requested)
         * 
         * @param ticker              Stock ticker symbol
         * @param overrides           Optional user overrides (null for GET endpoint)
         * @param addStory            Whether to generate narrative story
         * @param enableDCFAnalysis   Whether ML-based DCF analysis is enabled
         * @param addIntrinsicPricing Whether to fetch peer-based intrinsic pricing
         *                            multiples
         * @return ValuationOutputDTO with consistent results
         */
        private ValuationOutputDTO calculateValuation(
                        String ticker,
                        FinancialDataInput overrides,
                        boolean addStory,
                        boolean enableDCFAnalysis,
                        boolean addIntrinsicPricing) {
                return calculateValuation(ticker, overrides, addStory, enableDCFAnalysis, addIntrinsicPricing, false,
                                false);
        }

        private ValuationOutputDTO calculateValuation(
                        String ticker,
                        FinancialDataInput overrides,
                        boolean addStory,
                        boolean enableDCFAnalysis,
                        boolean addIntrinsicPricing,
                        boolean useIntrinsicPricingV2) {
                return calculateValuation(ticker, overrides, addStory, enableDCFAnalysis, addIntrinsicPricing,
                                useIntrinsicPricingV2, false);
        }

        private ValuationOutputDTO calculateValuation(
                        String ticker,
                        FinancialDataInput overrides,
                        boolean addStory,
                        boolean enableDCFAnalysis,
                        boolean addIntrinsicPricing,
                        boolean useIntrinsicPricingV2,
                        boolean enableMonteCarlo) {

                // Step 1: Fetch company baseline data from Yahoo Finance
                CompanyDataDTO companyDataDTO = commonService.getCompanyDtaFromYahooApi(ticker);

                // Step 2: Determine valuation template based on company characteristics
                ValuationTemplate template = valuationTemplateService.determineTemplate(null, companyDataDTO);
                log.info("[TEMPLATE] Selected for {}: {} years, Growth: {}, Earnings: {}",
                                ticker, template.getProjectionYears(), template.getGrowthPattern(),
                                template.getEarningsLevel());

                // Step 3: Initialize financial data with baseline values
                FinancialDataInput financialDataInput = initializeFinancialDataInput(companyDataDTO, template);

                // Step 4: Apply user overrides (if any)
                if (overrides != null) {
                        applyUserOverrides(financialDataInput, overrides);
                        // Handle segments and sector overrides from user input
                        handleSegmentsAndOverrides(financialDataInput, companyDataDTO, ticker);
                }

                // Step 5: Adjust sales-to-capital ratio to be at least current ratio
                adjustSalesToCapitalRatio(financialDataInput);

                // Step 5.5: Start intrinsic pricing fetch in parallel (if requested)
                // This runs concurrently with Steps 6-10, saving significant time
                CompletableFuture<IntrinsicPricingDTO> intrinsicFutureV1 = null;
                CompletableFuture<IntrinsicPricingDTO> intrinsicFutureV2 = null;
                if (addIntrinsicPricing) {
                        String companyName = companyDataDTO.getBasicInfoDataDTO().getCompanyName();
                        String tickerFinal = ticker; // Final variable for lambda

                        if (enableV1V2Comparison) {
                                // V1V2 comparison enabled: Fetch both in parallel
                                log.info("V1/V2 comparison enabled - starting parallel fetch for {}", ticker);
                                intrinsicFutureV1 = CompletableFuture.supplyAsync(() -> {
                                        try {
                                                return intrinsicPricingService.getIntrinsicPricing(tickerFinal,
                                                                companyName, false);
                                        } catch (Exception e) {
                                                log.warn("V1 intrinsic pricing fetch failed: {}", e.getMessage());
                                                return null;
                                        }
                                });
                                intrinsicFutureV2 = CompletableFuture.supplyAsync(() -> {
                                        try {
                                                return intrinsicPricingService.getIntrinsicPricing(tickerFinal,
                                                                companyName, true);
                                        } catch (Exception e) {
                                                log.warn("V2 intrinsic pricing fetch failed: {}", e.getMessage());
                                                return null;
                                        }
                                });
                        } else {
                                // Single version fetch
                                intrinsicFutureV1 = CompletableFuture.supplyAsync(() -> {
                                        try {
                                                return intrinsicPricingService.getIntrinsicPricing(tickerFinal,
                                                                companyName, useIntrinsicPricingV2);
                                        } catch (Exception e) {
                                                log.warn("Intrinsic pricing fetch failed: {}", e.getMessage());
                                                return null;
                                        }
                                });
                        }
                }

                // Step 6: Run initial valuation check
                ValuationOutputDTO valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                ticker, financialDataInput, false, template);

                // Step 7: Apply calibration fallback if needed
                // Note: Segment analysis is performed INSIDE applyCalibrationAndMLAdjustments
                // after any calibration adjustments, ensuring consistent parameter processing
                ValuationOutputDTO valuationOutputDTO = applyCalibrationAndMLAdjustments(
                                ticker, financialDataInput, companyDataDTO, valuationOutputDTOCheck, enableDCFAnalysis,
                                addStory, template);

                // Step 8: Single calibration to market price
                valuationOutputDTO.setCalibrationResultDTO(
                                calibrateToMarketPrice(ticker, new FinancialDataInput(financialDataInput),
                                                valuationOutputDTO.getCompanyDTO().getPrice()));

                // Step 9: Process scenario valuation
                processScenarioValuation(valuationOutputDTO, new FinancialDataInput(financialDataInput),
                                new CompanyDataDTO(companyDataDTO), template);

                // Step 10: Calculate DDM (if company pays dividends)
                calculateAndSetDDM(valuationOutputDTO, companyDataDTO, ticker);

                // Step 11: Collect intrinsic pricing results (if requested)
                // Results were fetched in parallel since Step 5.5
                if (addIntrinsicPricing) {
                        try {
                                if (intrinsicFutureV1 != null) {
                                        IntrinsicPricingDTO intrinsicPricingV1 = intrinsicFutureV1.join();
                                        if (enableV1V2Comparison) {
                                                valuationOutputDTO.setIntrinsicPricingDTO(intrinsicPricingV1);
                                                if (intrinsicPricingV1 != null) {
                                                        log.info("Intrinsic pricing V1 added for {}: {} peers found, recommended multiple: {}",
                                                                        ticker, intrinsicPricingV1.getPeersFound(),
                                                                        intrinsicPricingV1.getRecommendedMultiple());
                                                }
                                        } else {
                                                valuationOutputDTO.setIntrinsicPricingDTO(intrinsicPricingV1);
                                                if (intrinsicPricingV1 != null) {
                                                        String version = useIntrinsicPricingV2 ? "V2" : "V1";
                                                        log.info("Intrinsic pricing {} added for {}: {} peers found, recommended multiple: {}",
                                                                        version, ticker,
                                                                        intrinsicPricingV1.getPeersFound(),
                                                                        intrinsicPricingV1.getRecommendedMultiple());
                                                }
                                        }
                                }
                                if (intrinsicFutureV2 != null) {
                                        IntrinsicPricingDTO intrinsicPricingV2 = intrinsicFutureV2.join();
                                        valuationOutputDTO.setIntrinsicPricingV2DTO(intrinsicPricingV2);
                                        if (intrinsicPricingV2 != null) {
                                                log.info("Intrinsic pricing V2 added for {}: {} peers found, recommended multiple: {}",
                                                                ticker, intrinsicPricingV2.getPeersFound(),
                                                                intrinsicPricingV2.getRecommendedMultiple());
                                        }
                                }
                        } catch (Exception e) {
                                log.warn("Failed to collect intrinsic pricing results for {}: {}", ticker,
                                                e.getMessage());
                        }
                }

                // Step 11.5: Run Monte Carlo valuation (if enabled)
                if (enableMonteCarlo) {
                        try {
                                log.info("Running Monte Carlo valuation for {}", ticker);
                                MonteCarloResult mcResult = monteCarloValuationService.runMonteCarlo(
                                                ticker, financialDataInput, template, null);
                                if (mcResult != null) {
                                        valuationOutputDTO.setMonteCarloResult(mcResult);
                                        log.info("Monte Carlo for {}: p5=${}, p50=${}, p95=${}",
                                                        ticker, mcResult.getP5(), mcResult.getP50(), mcResult.getP95());
                                }
                        } catch (Exception e) {
                                log.warn("Monte Carlo valuation failed for {}: {}", ticker, e.getMessage());
                                // Continue without Monte Carlo - don't fail the request
                        }
                }

                // Step 11.6: Generate DCF animation (if service available)
                try {
                        log.info("Generating DCF animation for {}", ticker);
                        String animationBase64 = null;
                        //animationBase64 = valuationAnimationService.generateAnimation(valuationOutputDTO);
                        if (animationBase64 != null) {
                                valuationOutputDTO.setValuationAnimationBase64(animationBase64);
                                log.info("Animation generated for {}: {} bytes",
                                                ticker, animationBase64.length());
                        }
                } catch (Exception e) {
                        log.warn("Animation generation failed for {}: {}", ticker, e.getMessage());
                        // Continue without animation - don't fail the request
                }

                // Step 12: Add story (if requested)
                if (addStory) {
                        valuationOutputDTO = valuationOutputService.addStory(valuationOutputDTO);
                }

                return valuationOutputDTO;
        }

        /**
         * Calculate DDM valuation and set on output DTO.
         * Also determines primary model selection.
         */
        private void calculateAndSetDDM(ValuationOutputDTO valuationOutputDTO, CompanyDataDTO companyDataDTO,
                        String ticker) {
                try {
                        // Determine primary model
                        io.stockvaluation.enums.CashflowType primaryModel = modelSelectionService
                                        .selectPrimaryModel(companyDataDTO);
                        valuationOutputDTO.setPrimaryModel(primaryModel);
                        valuationOutputDTO.setModelSelectionRationale(
                                        modelSelectionService.getSelectionRationale(companyDataDTO, primaryModel));

                        log.info("[DDM] Primary model for {}: {} - {}",
                                        ticker, primaryModel, valuationOutputDTO.getModelSelectionRationale());

                        // Calculate DDM if company pays dividends
                        if (companyDataDTO.isDividendPaying()) {
                                DDMResultDTO ddmResult = dividendDiscountModelService.calculateDDM(companyDataDTO,
                                                ticker);
                                valuationOutputDTO.setDdmResultDTO(ddmResult);

                                if (ddmResult != null && ddmResult.getApplicable() != null
                                                && ddmResult.getApplicable()) {
                                        log.info("[DDM] DDM valuation for {}: ${} ({}) - FCFF: ${}",
                                                        ticker,
                                                        String.format("%.2f", ddmResult.getIntrinsicValue()),
                                                        ddmResult.getModelUsed(),
                                                        String.format("%.2f", valuationOutputDTO.getCompanyDTO()
                                                                        .getEstimatedValuePerShare()));
                                } else if (ddmResult != null) {
                                        log.info("[DDM] DDM not applicable for {}: {}",
                                                        ticker, ddmResult.getNotApplicableReason());
                                }
                        } else {
                                log.info("[DDM] Company {} does not pay dividends, skipping DDM", ticker);
                                valuationOutputDTO.setDdmResultDTO(
                                                DDMResultDTO.notApplicable("Company does not pay dividends"));
                        }
                } catch (Exception e) {
                        log.warn("[DDM] Error calculating DDM for {}: {}", ticker, e.getMessage());
                        valuationOutputDTO.setDdmResultDTO(DDMResultDTO.notApplicable("Error: " + e.getMessage()));
                }
        }

        private double[] calculatePercentiles(List<Double> values, double[] percentiles) {
                if (values == null || values.isEmpty()) {
                        throw new IllegalArgumentException("Values list cannot be null or empty");
                }

                // Sort the values
                List<Double> sortedValues = new ArrayList<>(values);
                Collections.sort(sortedValues);

                // Compute percentiles
                double[] results = new double[percentiles.length];
                int n = sortedValues.size();

                for (int i = 0; i < percentiles.length; i++) {
                        double rank = (percentiles[i] / 100.0) * (n - 1); // Rank position
                        int lowerIndex = (int) Math.floor(rank);
                        int upperIndex = (int) Math.ceil(rank);

                        if (lowerIndex == upperIndex) {
                                results[i] = sortedValues.get(lowerIndex);
                        } else {
                                double lowerValue = sortedValues.get(lowerIndex);
                                double upperValue = sortedValues.get(upperIndex);
                                results[i] = lowerValue + (rank - lowerIndex) * (upperValue - lowerValue); // Linear
                                                                                                           // interpolation
                        }
                }

                return results;
        }

        public SimulationResultsDTO runSimulations(String ticker, FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDT) {
                RDResult rdResult = commonService.calculateR_DConvertorValue(
                                financialDataInput.getIndustry(),
                                financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                financialDataInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(
                                ticker, financialDataInput.getAverageStrikePrice(),
                                financialDataInput.getAverageMaturity(),
                                financialDataInput.getNumberOfOptions(),
                                financialDataInput.getStockPriceStdDev());
                LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConvertor();

                List<Double> results = IntStream.range(0, 10000)
                                .parallel()
                                .mapToObj(i -> runSingleSimulation(ticker, new FinancialDataInput(financialDataInput),
                                                companyDataDT, rdResult, optionValueResultDTO, leaseResultDTO))
                                .collect(Collectors.toList());

                DoubleSummaryStatistics stats = results.stream()
                                .mapToDouble(Double::doubleValue)
                                .summaryStatistics();

                double[] percentiles = calculatePercentiles(results,
                                new double[] { 5, 25, 50, 75, 95 });

                return new SimulationResultsDTO(
                                stats.getAverage(),
                                stats.getMin(),
                                stats.getMax(),
                                percentiles[0], // 5th
                                percentiles[2], // 50th
                                percentiles[4] // 95th
                );
        }

        private Map<String, Double> processScenarioValuation(ValuationOutputDTO valuationOutputDTO,
                        FinancialDataInput financialDataInput, CompanyDataDTO companyDataDto,
                        ValuationTemplate template) {

                Map<String, Double> scenarioValuations = new HashMap<>();

                RDResult rdResult = commonService.calculateR_DConvertorValue(
                                financialDataInput.getIndustry(),
                                financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                financialDataInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(
                                companyDataDto.getBasicInfoDataDTO().getTicker(),
                                financialDataInput.getAverageStrikePrice(),
                                financialDataInput.getAverageMaturity(),
                                financialDataInput.getNumberOfOptions(),
                                financialDataInput.getStockPriceStdDev());
                LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConvertor();

                // Extract scenario analysis safely
                NarrativeDTO.ScenarioAnalysis scenarioAnalysis = valuationOutputDTO != null
                                && valuationOutputDTO.getNarrativeDTO() != null
                                                ? valuationOutputDTO.getNarrativeDTO().getScenarioAnalysis()
                                                : null;
                if (scenarioAnalysis != null && valuationOutputDTO != null) {
                        // Process all scenarios
                        processScenario("optimistic", scenarioAnalysis.getOptimistic(), scenarioValuations,
                                        financialDataInput, companyDataDto, rdResult, optionValueResultDTO,
                                        leaseResultDTO, template);

                        processScenario("base_case", scenarioAnalysis.getBase_case(), scenarioValuations,
                                        financialDataInput, companyDataDto, rdResult, optionValueResultDTO,
                                        leaseResultDTO, template);

                        processScenario("pessimistic", scenarioAnalysis.getPessimistic(), scenarioValuations,
                                        financialDataInput, companyDataDto, rdResult, optionValueResultDTO,
                                        leaseResultDTO, template);

                        scenarioAnalysis.getBase_case().setIntrinsicValue(
                                        valuationOutputDTO.getCompanyDTO().getEstimatedValuePerShare());

                        // Generate heat map
                        /*
                         * Map<String, Object> heatMapData = generateSensitivityHeatMap(
                         * companyDataDto.getBasicInfoDataDTO().getTicker(),
                         * financialDataInput,
                         * companyDataDto,
                         * rdResult,
                         * optionValueResultDTO,
                         * leaseResultDTO
                         * );
                         * 
                         * // Store heat map in ValuationOutputDTO
                         * valuationOutputDTO.setHeatMapData(heatMapData);
                         */
                }

                return scenarioValuations;
        }

        private void processScenario(
                        String scenarioName,
                        NarrativeDTO.ScenarioAnalysis.Scenario scenario,
                        Map<String, Double> scenarioValuations,
                        FinancialDataInput baseFinancialDataInput,
                        CompanyDataDTO companyDataDto,
                        RDResult rdResult,
                        OptionValueResultDTO optionValueResultDTO,
                        LeaseResultDTO leaseResultDTO,
                        ValuationTemplate template) {

                if (scenario == null || scenario.getAdjustments() == null) {
                        log.warn("Scenario {} has null adjustments, skipping", scenarioName);
                        return;
                }

                // Clone FinancialDataInput to avoid mutating base input for other scenarios
                FinancialDataInput financialDataInput = new FinancialDataInput(baseFinancialDataInput);

                NarrativeDTO.ScenarioAnalysis.Scenario.Adjustments adjustments = scenario.getAdjustments();

                log.info("🎯 Processing scenario: {}", scenarioName);
                log.info("  Base values: growth={}, margin={}, stc={}, discount={}",
                                baseFinancialDataInput.getCompoundAnnualGrowth2_5(),
                                baseFinancialDataInput.getTargetPreTaxOperatingMargin(),
                                baseFinancialDataInput.getSalesToCapitalYears1To5(),
                                baseFinancialDataInput.getInitialCostCapital());

                // Set compoundAnnualGrowth2_5 → revenueGrowthRate(1)
                if (adjustments.getRevenueGrowthRate() != null
                                && adjustments.getRevenueGrowthRate().size() > 1
                                && adjustments.getRevenueGrowthRate().get(1) != null) {
                        double growth = adjustments.getRevenueGrowthRate().stream()
                                        .mapToDouble(Double::doubleValue)
                                        .average()
                                        .orElse(0.0) * 100;
                        financialDataInput.setCompoundAnnualGrowth2_5(growth);
                }

                // Set targetPreTaxOperatingMargin → operatingMargin(1)
                // NOTE: LLM returns decimals (0.30 = 30%), must convert to percentage for
                // FinancialDataInput
                if (adjustments.getOperatingMargin() != null
                                && adjustments.getOperatingMargin().size() > 1
                                && adjustments.getOperatingMargin().get(1) != null) {
                        double margin = adjustments.getOperatingMargin().stream().mapToDouble(Double::doubleValue)
                                        .average().orElse(0.0) * 100;
                        financialDataInput.setTargetPreTaxOperatingMargin(margin);
                }

                // Set SalesToCapitalYears1To5 → salesToCapitalRatio(1)
                // NOTE: Sales-to-capital is a ratio (not percentage), no conversion needed
                if (adjustments.getSalesToCapitalRatio() != null
                                && adjustments.getSalesToCapitalRatio().size() > 1
                                && adjustments.getSalesToCapitalRatio().get(1) != null) {
                        double stc = adjustments.getSalesToCapitalRatio().stream().mapToDouble(Double::doubleValue)
                                        .average().orElse(0.0);
                        financialDataInput.setSalesToCapitalYears1To5(stc);
                }

                // Set InitialCostCapital → discountRate(1)
                // NOTE: LLM returns decimals (0.085 = 8.5%), must convert to percentage for
                // FinancialDataInput
                if (adjustments.getDiscountRate() != null
                                && adjustments.getDiscountRate().size() > 1
                                && adjustments.getDiscountRate().get(1) != null) {
                        double discount = adjustments.getDiscountRate().stream().mapToDouble(Double::doubleValue)
                                        .average().orElse(0.0) * 100;
                        financialDataInput.setInitialCostCapital(discount);
                }

                // Run valuation
                FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                                financialDataInput,
                                rdResult,
                                leaseResultDTO,
                                companyDataDto.getBasicInfoDataDTO().getTicker(),
                                template);

                CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                                financialDTO,
                                financialDataInput,
                                optionValueResultDTO,
                                leaseResultDTO);

                Double intrinsicValue = companyDTO.getEstimatedValuePerShare();
                scenarioValuations.put(scenarioName, intrinsicValue);
                scenario.setIntrinsicValue(intrinsicValue);

                log.info("  💰 {} scenario result: ${} per share", scenarioName, String.format("%.2f", intrinsicValue));
        }

        private Double runSingleSimulation(String ticker, FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDto, RDResult rdResult, OptionValueResultDTO optionValueResultDTO,
                        LeaseResultDTO leaseResultDTO) {

                /*
                 * Map<String, Double> logParams = calculateLogNormalParams(
                 * companyDataDto.getGrowthDto().getRevenueMu(),
                 * companyDataDto.getGrowthDto().getRevenueStdDev(),
                 * -1.0 / 100
                 * );
                 * double[] simulated = generateCorrelatedVariables(
                 * logParams.get("muLog") / 100,
                 * logParams.get("sigmaLog") / 100,
                 * companyDataDto.getGrowthDto().getMarginMu(),
                 * companyDataDto.getGrowthDto().getMarginStdDev(),
                 * companyDataDto.getGrowthDto().getRevenueMarginCorrelation()
                 * );
                 * 
                 * 
                 * double[] simulated = generateCorrelatedVariables(
                 * companyDataDto.getGrowthDto().getRevenueMu() / 100.0,
                 * companyDataDto.getGrowthDto().getRevenueStdDev() / 100.0,
                 * companyDataDto.getGrowthDto().getMarginMin() / 100.0,
                 * companyDataDto.getGrowthDto().getMarginMu() / 100.0,
                 * companyDataDto.getGrowthDto().getMarginMax() / 100.0,
                 * companyDataDto.getGrowthDto().getRevenueMarginCorrelation()
                 * );
                 * 
                 * financialDataInput.setCompoundAnnualGrowth2_5(simulated[0] * 100);
                 * financialDataInput.setOperatingMarginNextYear(simulated[1] * 100);
                 */

                financialDataInput.setCompoundAnnualGrowth2_5(
                                generateRevenueGrowth(
                                                financialDataInput.getRevenueNextYear(),
                                                companyDataDto.getGrowthDto().getRevenueStdDev()));

                financialDataInput.setTargetPreTaxOperatingMargin(
                                /*
                                 * generateOperatingMargin(
                                 * financialDataInput.getOperatingMarginNextYear(),
                                 * companyDataDto.getGrowthDto().getMarginStdDev()
                                 * )
                                 */
                                generateOperatingMargin(
                                                companyDataDto.getGrowthDto().getMarginMin(),
                                                companyDataDto.getGrowthDto().getMarginMu(),
                                                companyDataDto.getGrowthDto().getMarginMax()));

                FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(
                                financialDataInput,
                                rdResult,
                                leaseResultDTO,
                                ticker,
                                null);
                CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(
                                financialDTO,
                                financialDataInput,
                                optionValueResultDTO,
                                leaseResultDTO);

                return companyDTO.getEstimatedValuePerShare();
        }

        public CalibrationResultDTO calibrateToMarketPrice(String ticker, FinancialDataInput financialDataInput,
                        Double currentPrice) {
                // Initial calculations for RD, Option Value, and Lease
                RDResult rdResult = commonService.calculateR_DConvertorValue(
                                financialDataInput.getIndustry(),
                                financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                financialDataInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(
                                ticker, financialDataInput.getAverageStrikePrice(),
                                financialDataInput.getAverageMaturity(),
                                financialDataInput.getNumberOfOptions(),
                                financialDataInput.getStockPriceStdDev());
                LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConvertor();

                // Hyperparameters
                double epsilon = 0.01; // Tolerance for convergence
                double learningRate = 0.1; // Initial learning rate
                int maxIterations = 10000; // Maximum iterations to prevent infinite loops

                // Initialize variables
                double currentRevenueGrowth = financialDataInput.getCompoundAnnualGrowth2_5();
                double currentMargin = financialDataInput.getTargetPreTaxOperatingMargin();

                for (int i = 0; i < maxIterations; i++) {
                        // Create a copy of the input to avoid modifying the original
                        FinancialDataInput tempInput = new FinancialDataInput(financialDataInput);
                        tempInput.setCompoundAnnualGrowth2_5(currentRevenueGrowth);
                        tempInput.setTargetPreTaxOperatingMargin(currentMargin);

                        // Calculate the estimated value per share
                        double estimatedValuePerShare = getEstimatedValuePerShare(
                                        tempInput, rdResult, optionValueResultDTO, leaseResultDTO, ticker);

                        // Check for NaN or invalid values
                        if (Double.isNaN(estimatedValuePerShare)) {
                                break;
                        }

                        // Calculate the price error
                        double priceError = estimatedValuePerShare - currentPrice;

                        // Check for convergence
                        if (Math.abs(priceError) < epsilon) {
                                break;
                        }

                        // Calculate partial derivatives (gradient approximation)
                        double dRev = calculatePartialDerivative(tempInput, currentRevenueGrowth, currentMargin, true,
                                        rdResult, optionValueResultDTO, leaseResultDTO, ticker);
                        double dMargin = calculatePartialDerivative(tempInput, currentRevenueGrowth, currentMargin,
                                        false, rdResult, optionValueResultDTO, leaseResultDTO, ticker);

                        // Handle NaN in gradients
                        if (Double.isNaN(dRev))
                                dRev = 0.0;
                        if (Double.isNaN(dMargin))
                                dMargin = 0.0;

                        // Clamp gradient updates to avoid large jumps
                        double revenueUpdate = learningRate * priceError * dRev;
                        double marginUpdate = learningRate * priceError * dMargin;

                        revenueUpdate = Math.min(Math.max(revenueUpdate, -1.0), 1.0); // Clamp between -1 and 1
                        marginUpdate = Math.min(Math.max(marginUpdate, -1.0), 1.0); // Clamp between -1 and 1

                        // Update revenue growth and margin
                        currentRevenueGrowth -= revenueUpdate;
                        currentMargin -= marginUpdate;

                        // Adjust learning rate dynamically for faster convergence
                        if (i % 100 == 0) {
                                learningRate *= 0.9; // Reduce learning rate every 100 iterations
                        }
                }

                // Return the calibrated result
                return new CalibrationResultDTO(currentRevenueGrowth, currentMargin);
        }

        private double calculatePartialDerivative(FinancialDataInput financialDataInput, double revenueGrowth,
                        double margin,
                        boolean withRespectToRevenue, RDResult rdResult,
                        OptionValueResultDTO optionValueResultDTO, LeaseResultDTO leaseResultDTO,
                        String ticker) {
                double h = 0.0001; // Small step for numerical differentiation

                // Create a copy of the input to avoid modifying the original
                FinancialDataInput tempInput = new FinancialDataInput(financialDataInput);

                // Perturb the input based on the variable of interest
                if (withRespectToRevenue) {
                        tempInput.setCompoundAnnualGrowth2_5(revenueGrowth + h);
                        tempInput.setTargetPreTaxOperatingMargin(margin);
                } else {
                        tempInput.setCompoundAnnualGrowth2_5(revenueGrowth);
                        tempInput.setTargetPreTaxOperatingMargin(margin + h);
                }

                // Calculate the perturbed value
                double perturbedPrice = getEstimatedValuePerShare(tempInput, rdResult, optionValueResultDTO,
                                leaseResultDTO, ticker);

                // Calculate the base value
                FinancialDataInput baseInput = new FinancialDataInput(financialDataInput);
                baseInput.setCompoundAnnualGrowth2_5(revenueGrowth);
                baseInput.setTargetPreTaxOperatingMargin(margin);
                double basePrice = getEstimatedValuePerShare(baseInput, rdResult, optionValueResultDTO, leaseResultDTO,
                                ticker);

                // Handle NaN values
                if (Double.isNaN(perturbedPrice) || Double.isNaN(basePrice)) {
                        return 0.0; // Prevent NaN propagation
                }

                // Return the partial derivative
                return (perturbedPrice - basePrice) / h;
        }

        private double getEstimatedValuePerShare(FinancialDataInput input, RDResult rdResult,
                        OptionValueResultDTO optionValueResultDTO, LeaseResultDTO leaseResultDTO,
                        String ticker) {
                FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(input, rdResult,
                                leaseResultDTO, ticker, null);
                CompanyDTO companyDTO = valuationOutputService.calculateCompanyData(financialDTO, input,
                                optionValueResultDTO, leaseResultDTO);

                if (companyDTO == null || Double.isNaN(companyDTO.getEstimatedValuePerShare())) {
                        return Double.NaN;
                }

                return companyDTO.getEstimatedValuePerShare();
        }

        /**
         * Initializes override assumptions to default values.
         * Common logic shared between POST and GET endpoints.
         */
        private void initializeOverrideAssumptions(FinancialDataInput financialDataInput) {
                financialDataInput.setOverrideAssumptionCostCapital(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput.setOverrideAssumptionReturnOnCapital(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput
                                .setOverrideAssumptionProbabilityOfFailure(new OverrideAssumption(0D, false, 0D, "V"));
                financialDataInput.setOverrideAssumptionReinvestmentLag(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput.setOverrideAssumptionTaxRate(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput.setOverrideAssumptionNOL(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput.setOverrideAssumptionRiskFreeRate(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput.setOverrideAssumptionGrowthRate(new OverrideAssumption(0D, false, 0D, null));
                financialDataInput.setOverrideAssumptionCashPosition(new OverrideAssumption(0D, false, 0D, null));
        }

        /**
         * Handles segment fetching and sector override application.
         * Common logic for processing sector-specific parameter overrides.
         */
        private void handleSegmentsAndOverrides(FinancialDataInput financialDataInput, CompanyDataDTO companyDataDTO,
                        String ticker) {
                if (financialDataInput.getSectorOverrides() != null
                                && !financialDataInput.getSectorOverrides().isEmpty()) {
                        log.info("Sector overrides detected ({} overrides) - fetching segment data for {}",
                                        financialDataInput.getSectorOverrides().size(), ticker);

                        // Fetch segments from company data
                        SegmentResposeDTO segmentResposeDTO = commonService.getSegment(
                                        ticker,
                                        companyDataDTO.getBasicInfoDataDTO().getCompanyName(),
                                        companyDataDTO.getBasicInfoDataDTO().getIndustryUs(),
                                        companyDataDTO.getBasicInfoDataDTO().getSummary());

                        if (segmentResposeDTO != null && segmentResposeDTO.getSegments() != null
                                        && !segmentResposeDTO.getSegments().isEmpty()) {
                                // Attach segments to financial input
                                financialDataInput.setSegments(segmentResposeDTO);
                                log.info("✅ Loaded {} segments for sector override processing",
                                                segmentResposeDTO.getSegments().size());

                                // Apply segment-weighted parameters with overrides
                                List<String> adjustedParameters = new ArrayList<>();
                                commonService.applySegmentWeightedParameters(financialDataInput, companyDataDTO,
                                                adjustedParameters);
                                log.info("✅ Applied sector overrides successfully for {}", ticker);
                        } else {
                                log.warn("⚠️ Sector overrides requested but no segment data available for {}. Overrides will be ignored.",
                                                ticker);
                        }
                } else if (financialDataInput.getSegments() != null
                                && financialDataInput.getSegments().getSegments() != null
                                && financialDataInput.getSegments().getSegments().size() > 1) {
                        // Segments provided in input (existing behavior)
                        List<String> adjustedParameters = new ArrayList<>();
                        commonService.applySegmentWeightedParameters(financialDataInput, companyDataDTO,
                                        adjustedParameters);

                }
        }

        /**
         * Initializes FinancialDataInput with template support for normalized margins
         */
        private FinancialDataInput initializeFinancialDataInput(CompanyDataDTO companyDataDTO,
                        ValuationTemplate template) {
                FinancialDataInput financialDataInput = new FinancialDataInput();

                // Basic info
                financialDataInput.setBasicInfoDataDTO(companyDataDTO.getBasicInfoDataDTO());
                financialDataInput.setFinancialDataDTO(companyDataDTO.getFinancialDataDTO());
                financialDataInput.setCompanyDriveDataDTO(companyDataDTO.getCompanyDriveDataDTO());
                financialDataInput.setGrowthDto(companyDataDTO.getGrowthDto());
                financialDataInput.setIndustry(companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal());
                financialDataInput.setIsExpensesCapitalize(false);
                financialDataInput.setCompanyRiskLevel("Medium");

                // Company drive parameters (Yahoo Finance baseline)
                double operatingMarginNextYear = companyDataDTO.getCompanyDriveDataDTO().getOperatingMarginNextYear()
                                * 100;

                // Apply template adjustments if provided
                if (template != null && template.getNormalizedOperatingMargin() != null) {
                        operatingMarginNextYear = template.getNormalizedOperatingMargin();
                }

                financialDataInput.setRevenueNextYear(
                                companyDataDTO.getCompanyDriveDataDTO().getRevenueNextYear() * 100);
                financialDataInput.setOperatingMarginNextYear(operatingMarginNextYear);
                financialDataInput.setCompoundAnnualGrowth2_5(
                                companyDataDTO.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5() * 100);
                financialDataInput.setTargetPreTaxOperatingMargin(operatingMarginNextYear);
                financialDataInput.setConvergenceYearMargin(
                                companyDataDTO.getCompanyDriveDataDTO().getConvergenceYearMargin() * 100);
                financialDataInput.setSalesToCapitalYears1To5(
                                companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears1To5() * 100);
                financialDataInput.setSalesToCapitalYears6To10(
                                companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears6To10() * 100);
                financialDataInput.setRiskFreeRate(
                                companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate() * 100);
                financialDataInput.setInitialCostCapital(
                                companyDataDTO.getCompanyDriveDataDTO().getInitialCostCapital() * 100);

                // Initialize override assumptions
                initializeOverrideAssumptions(financialDataInput);

                return financialDataInput;
        }

        /**
         * Processes multi-segment analysis by fetching segment data and applying
         * weighted parameters.
         * Common logic for segment-based DCF adjustments.
         * 
         * @param financialDataInput Financial input to apply segment parameters to
         * @param companyDataDTO     Company data for segment fetching
         * @param ticker             Stock ticker symbol
         * @param enableDCFAnalysis  Whether ML-based DCF analysis is enabled
         * @param adjustedParameters List of parameters adjusted by ML (if any)
         */
        private void processSegmentAnalysis(
                        FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDTO,
                        String ticker,
                        boolean enableDCFAnalysis,
                        CommonService.DcfAdjustmentResult adjustmentResult) {

                SegmentResposeDTO segmentResposeDTO = commonService.getSegment(
                                financialDataInput.getBasicInfoDataDTO().getTicker(),
                                financialDataInput.getBasicInfoDataDTO().getCompanyName(),
                                financialDataInput.getBasicInfoDataDTO().getIndustryUs(),
                                companyDataDTO.getBasicInfoDataDTO().getSummary());

                if (segmentResposeDTO != null && segmentResposeDTO.getSegments() != null
                                && segmentResposeDTO.getSegments().size() > 1) {
                        financialDataInput.setSegments(segmentResposeDTO);
                        List<String> adjustedParameters = adjustmentResult != null
                                        ? adjustmentResult.getAdjustedParameters()
                                        : new ArrayList<>();
                        commonService.applySegmentWeightedParameters(financialDataInput, companyDataDTO,
                                        adjustedParameters);
                        log.info("Multi-segment analysis enabled for {} with {} segments",
                                        ticker, segmentResposeDTO.getSegments().size());
                } else {
                        log.warn("Multi-segment analysis enabled but no segment data returned for {}", ticker);
                }
        }

        private ValuationOutputDTO applyCalibrationAndMLAdjustments(
                        String ticker,
                        FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDTO,
                        ValuationOutputDTO valuationOutputDTOCheck,
                        boolean enableDCFAnalysis,
                        boolean addStory,
                        ValuationTemplate template) {

                CommonService.DcfAdjustmentResult adjustmentResult = new CommonService.DcfAdjustmentResult(
                                new ArrayList<>(), new HashMap<>());

                // If intrinsic value is negative, apply calibration
                if (valuationOutputDTOCheck.getCompanyDTO().getEstimatedValuePerShare() < 0) {
                        log.info("Negative intrinsic value detected for {}, applying calibration", ticker);

                        CalibrationResultDTO calibrationResultDTO = calibrateToMarketPrice(
                                        ticker,
                                        new FinancialDataInput(financialDataInput),
                                        valuationOutputDTOCheck.getCompanyDTO().getPrice());

                        // Apply 80% of calibrated values
                        financialDataInput.setCompoundAnnualGrowth2_5(calibrationResultDTO.getRevenueGrowth() * 0.80);
                        financialDataInput.setTargetPreTaxOperatingMargin(
                                        calibrationResultDTO.getOperatingMargin() * 0.80);

                        // Apply ML adjustments if enabled
                        if (enableDCFAnalysis) {
                                ValuationOutputDTO valuationOutputDTOAdjusted = valuationOutputService
                                                .getValuationOutput(
                                                                ticker, financialDataInput, false, template);
                                adjustmentResult = commonService.analyzeBaseDCFAndApplyAdjustments(
                                                valuationOutputDTOAdjusted, financialDataInput, ticker);
                        }

                        // Re-process segments with adjusted parameters
                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableDCFAnalysis,
                                        adjustmentResult);

                        // Recalculate valuation with adjusted parameters
                        ValuationOutputDTO finalValuation = valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, addStory, template);

                        // Populate rationale DTO
                        finalValuation.setAdjustmentRationales(buildRationaleDTO(adjustmentResult));

                        // Calculate probabilistic DCF distribution if ML analysis is enabled
                        if (enableDCFAnalysis) {
                                calculateAndSetProbabilisticDCF(finalValuation, ticker, financialDataInput,
                                                companyDataDTO);
                        }

                        return finalValuation;
                } else {
                        // Apply ML adjustments if enabled
                        if (enableDCFAnalysis) {
                                ValuationOutputDTO valuationOutputDTOAdjusted = valuationOutputService
                                                .getValuationOutput(
                                                                ticker, financialDataInput, false, template);
                                adjustmentResult = commonService.analyzeBaseDCFAndApplyAdjustments(
                                                valuationOutputDTOAdjusted, financialDataInput, ticker);
                        }

                        // Re-process segments with adjusted parameters
                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableDCFAnalysis,
                                        adjustmentResult);

                        // Return original valuation with story if positive
                        ValuationOutputDTO finalValuation = valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, addStory, template);

                        // Populate rationale DTO
                        finalValuation.setAdjustmentRationales(buildRationaleDTO(adjustmentResult));

                        // Calculate probabilistic DCF distribution if ML analysis is enabled
                        if (enableDCFAnalysis) {
                                calculateAndSetProbabilisticDCF(finalValuation, ticker, financialDataInput,
                                                companyDataDTO);
                        }

                        return finalValuation;
                }
        }

        /**
         * Builds DcfAdjustmentRationaleDTO from adjustment result.
         * Maps parameter names to rationale fields.
         */
        private DcfAdjustmentRationaleDTO buildRationaleDTO(CommonService.DcfAdjustmentResult adjustmentResult) {
                log.info("🔍 [RATIONALES] Building rationale DTO from adjustment result");

                if (adjustmentResult == null) {
                        log.warn("⚠️ [RATIONALES] Adjustment result is null");
                        return new DcfAdjustmentRationaleDTO();
                }

                if (adjustmentResult.getRationales() == null) {
                        log.warn("⚠️ [RATIONALES] Rationales map is null");
                        return new DcfAdjustmentRationaleDTO();
                }

                Map<String, String> rationales = adjustmentResult.getRationales();
                log.info("📊 [RATIONALES] Rationales map contains {} entries: {}",
                                rationales.size(), rationales.keySet());

                DcfAdjustmentRationaleDTO rationaleDTO = new DcfAdjustmentRationaleDTO();

                String revenueGrowthRationale = rationales.get("revenue_cagr");
                String operatingMarginRationale = rationales.get("operating_margin");
                String waccRationale = rationales.get("wacc");
                String taxRateRationale = rationales.get("tax_rate");

                log.info("📊 [RATIONALES] Mapping rationales: revenue_cagr={}, operating_margin={}, wacc={}, tax_rate={}",
                                revenueGrowthRationale != null ? "present" : "null",
                                operatingMarginRationale != null ? "present" : "null",
                                waccRationale != null ? "present" : "null",
                                taxRateRationale != null ? "present" : "null");

                rationaleDTO.setRevenueGrowthRationale(revenueGrowthRationale);
                rationaleDTO.setOperatingMarginRationale(operatingMarginRationale);
                rationaleDTO.setWaccRationale(waccRationale);
                rationaleDTO.setTaxRateRationale(taxRateRationale);

                log.info("✅ [RATIONALES] Rationale DTO built successfully");
                return rationaleDTO;
        }

        /**
         * Calculate probabilistic DCF distribution and set it in the valuation output.
         * Calls ML service to get parameter distributions, then runs Monte Carlo
         * simulation.
         */
        private void calculateAndSetProbabilisticDCF(
                        ValuationOutputDTO valuationOutputDTO,
                        String ticker,
                        FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDTO) {
                try {
                        log.info("🎲 [PROBABILISTIC_DCF] Starting probabilistic DCF calculation for {}", ticker);

                        // Get current stock price
                        Double currentStockPrice = null;
                        if (financialDataInput.getFinancialDataDTO() != null) {
                                currentStockPrice = financialDataInput.getFinancialDataDTO().getStockPrice();
                        }

                        // Calculate probabilistic DCF distribution
                        ProbabilisticDCFService.ProbabilisticDCFResult result = probabilisticDCFService
                                        .calculateProbabilisticDCF(
                                                        ticker,
                                                        financialDataInput,
                                                        currentStockPrice,
                                                        1000, // Default 1000 Monte Carlo paths
                                                        companyDataDTO);

                        if (result != null && result.getDistribution() != null) {
                                valuationOutputDTO.setDistribution(result.getDistribution());

                                // Update simulationResultsDto with actual statistics
                                if (result.getSimulationResults() != null) {
                                        valuationOutputDTO.setSimulationResultsDto(result.getSimulationResults());
                                }

                                log.info("✅ [PROBABILISTIC_DCF] Probabilistic DCF distribution calculated successfully. "
                                                +
                                                "Mean: {}, P50: {}, P5: {}, P95: {}",
                                                result.getSimulationResults() != null
                                                                ? result.getSimulationResults().getAverage()
                                                                : "N/A",
                                                result.getSimulationResults() != null
                                                                ? result.getSimulationResults().getFiftyPercentile()
                                                                : "N/A",
                                                result.getSimulationResults() != null
                                                                ? result.getSimulationResults().getFifthPercentile()
                                                                : "N/A",
                                                result.getSimulationResults() != null
                                                                ? result.getSimulationResults().getNinthPercentile()
                                                                : "N/A");
                        } else {
                                log.warn("⚠️ [PROBABILISTIC_DCF] Probabilistic DCF calculation returned null");
                        }
                } catch (Exception e) {
                        log.error("❌ [PROBABILISTIC_DCF] Error calculating probabilistic DCF for {}: {}",
                                        ticker, e.getMessage(), e);
                        // Don't fail the entire request if probabilistic DCF fails
                }
        }

        /**
         * **CRITICAL FIX**: Adjusts sales-to-capital ratio for both Years 1-5 and Years
         * 6-10.
         * Ensures ratios are at least as high as the current ratio to prevent
         * unrealistic DCF calculations.
         * 
         * Per Damodaran: Sales-to-capital ratio should be consistent with the company's
         * current
         * reinvestment efficiency and should not decline below current levels without
         * justification.
         * 
         * Formula: salesToCapital = max(inputValue, calculatedCurrentRatio)
         */
        private void adjustSalesToCapitalRatio(FinancialDataInput financialDataInput) {
                try {
                        // Calculate current sales-to-capital ratio using R&D and operating lease
                        // adjustments
                        double currentSalesToCapital = valuationOutputService.calculateCurrentSalesToCapitalRatio(
                                        financialDataInput,
                                        commonService.calculateR_DConvertorValue(
                                                        financialDataInput.getIndustry(),
                                                        financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                                        financialDataInput.getFinancialDataDTO()
                                                                        .getResearchAndDevelopmentMap()),
                                        commonService.calculateOperatingLeaseConvertor());

                        // Adjust Years 1-5 sales-to-capital ratio
                        double inputSalesToCapital1To5 = financialDataInput.getSalesToCapitalYears1To5();
                        double adjustedSalesToCapital1To5 = Math.max(inputSalesToCapital1To5, currentSalesToCapital);
                        financialDataInput.setSalesToCapitalYears1To5(adjustedSalesToCapital1To5);

                        // Adjust Years 6-10 sales-to-capital ratio
                        double inputSalesToCapital6To10 = financialDataInput.getSalesToCapitalYears6To10();
                        double adjustedSalesToCapital6To10 = Math.max(inputSalesToCapital6To10, currentSalesToCapital);
                        financialDataInput.setSalesToCapitalYears6To10(adjustedSalesToCapital6To10);

                        log.info("Sales-to-capital adjustment for {}: Years 1-5: input={}, adjusted={} | Years 6-10: input={}, adjusted={} | current={}",
                                        financialDataInput.getBasicInfoDataDTO().getTicker(),
                                        inputSalesToCapital1To5, adjustedSalesToCapital1To5,
                                        inputSalesToCapital6To10, adjustedSalesToCapital6To10,
                                        currentSalesToCapital);
                } catch (Exception e) {
                        log.error("Failed to adjust sales-to-capital ratio, using input values", e);
                }
        }

        /**
         * Apply ONLY user overrides from the minimal payload to the baseline
         * financialDataInput.
         * This method implements the "minimal override pattern" similar to
         * getValuationOutputNew.
         * 
         * @param baseline  The baseline FinancialDataInput populated from Yahoo Finance
         * @param overrides The minimal FinancialDataInput containing ONLY user
         *                  overrides
         */
        private void applyUserOverrides(FinancialDataInput baseline, FinancialDataInput overrides) {
                log.info("Applying user overrides to baseline parameters...");
                int overrideCount = 0;

                // Apply each override if present (non-null)
                if (overrides.getRevenueNextYear() != null) {
                        baseline.setRevenueNextYear(overrides.getRevenueNextYear());
                        log.info("   Override: revenueNextYear = {}", overrides.getRevenueNextYear());
                        overrideCount++;
                }

                if (overrides.getOperatingMarginNextYear() != null) {
                        baseline.setOperatingMarginNextYear(overrides.getOperatingMarginNextYear());
                        log.info("   Override: operatingMarginNextYear = {}", overrides.getOperatingMarginNextYear());
                        overrideCount++;
                }

                if (overrides.getCompoundAnnualGrowth2_5() != null) {
                        baseline.setCompoundAnnualGrowth2_5(overrides.getCompoundAnnualGrowth2_5());
                        log.info("   Override: compoundAnnualGrowth2_5 = {}", overrides.getCompoundAnnualGrowth2_5());
                        overrideCount++;
                }

                if (overrides.getTargetPreTaxOperatingMargin() != null) {
                        baseline.setTargetPreTaxOperatingMargin(overrides.getTargetPreTaxOperatingMargin());
                        log.info("   Override: targetPreTaxOperatingMargin = {}",
                                        overrides.getTargetPreTaxOperatingMargin());
                        overrideCount++;
                }

                if (overrides.getConvergenceYearMargin() != null) {
                        baseline.setConvergenceYearMargin(overrides.getConvergenceYearMargin());
                        log.info("   Override: convergenceYearMargin = {}", overrides.getConvergenceYearMargin());
                        overrideCount++;
                }

                if (overrides.getSalesToCapitalYears1To5() != null) {
                        baseline.setSalesToCapitalYears1To5(overrides.getSalesToCapitalYears1To5());
                        log.info("   Override: salesToCapitalYears1To5 = {}", overrides.getSalesToCapitalYears1To5());
                        overrideCount++;
                }

                if (overrides.getSalesToCapitalYears6To10() != null) {
                        baseline.setSalesToCapitalYears6To10(overrides.getSalesToCapitalYears6To10());
                        log.info("   Override: salesToCapitalYears6To10 = {}", overrides.getSalesToCapitalYears6To10());
                        overrideCount++;
                }

                if (overrides.getRiskFreeRate() != null) {
                        baseline.setRiskFreeRate(overrides.getRiskFreeRate());
                        log.info("   Override: riskFreeRate = {}", overrides.getRiskFreeRate());
                        overrideCount++;
                }

                if (overrides.getInitialCostCapital() != null) {
                        baseline.setInitialCostCapital(overrides.getInitialCostCapital());
                        log.info("   Override: initialCostCapital = {}", overrides.getInitialCostCapital());
                        overrideCount++;
                }

                // Terminal growth rate override (for dcf_recalculator tool)
                if (overrides.getTerminalGrowthRate() != null) {
                        baseline.setTerminalGrowthRate(overrides.getTerminalGrowthRate());
                        log.info("   Override: terminalGrowthRate = {}%", overrides.getTerminalGrowthRate());
                        overrideCount++;
                }

                // Copy sector overrides if present
                if (overrides.getSectorOverrides() != null && !overrides.getSectorOverrides().isEmpty()) {
                        baseline.setSectorOverrides(overrides.getSectorOverrides());
                        log.info("   Override: sectorOverrides = {} override(s)",
                                        overrides.getSectorOverrides().size());
                        overrideCount++;
                }

                log.info("Applied {} user override(s) to baseline", overrideCount);
        }

        /**
         * Count non-null fields in FinancialDataInput for logging purposes.
         */
        private int countNonNullFields(FinancialDataInput input) {
                int count = 0;
                if (input.getRevenueNextYear() != null)
                        count++;
                if (input.getOperatingMarginNextYear() != null)
                        count++;
                if (input.getCompoundAnnualGrowth2_5() != null)
                        count++;
                if (input.getTargetPreTaxOperatingMargin() != null)
                        count++;
                if (input.getConvergenceYearMargin() != null)
                        count++;
                if (input.getSalesToCapitalYears1To5() != null)
                        count++;
                if (input.getSalesToCapitalYears6To10() != null)
                        count++;
                if (input.getRiskFreeRate() != null)
                        count++;
                if (input.getInitialCostCapital() != null)
                        count++;
                if (input.getTerminalGrowthRate() != null)
                        count++;
                if (input.getSectorOverrides() != null && !input.getSectorOverrides().isEmpty())
                        count++;
                return count;
        }

}
