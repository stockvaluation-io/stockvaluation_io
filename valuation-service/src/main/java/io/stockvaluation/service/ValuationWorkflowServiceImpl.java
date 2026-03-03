package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationoutput.CalibrationResultDTO;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.dto.valuationoutput.FinancialDTO;
import io.stockvaluation.dto.valuationoutput.SimulationResultsDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.utils.SegmentParameterContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.stockvaluation.service.GrowthCalculatorService.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationWorkflowServiceImpl implements ValuationWorkflowService {

        private final CommonService commonService;
        private final OptionValueService optionValueService;
        private final ValuationOutputService valuationOutputService;
        private final ValuationTemplateService valuationTemplateService;
        private final ValuationAssumptionProperties valuationAssumptionProperties;

        @Override
        public ValuationOutputDTO getValuation(String ticker, FinancialDataInput financialDataInputOverrides,
                        boolean addStory) {
                try {
                        if (addStory) {
                                log.info("GET /{}/valuation (UI ENDPOINT - WITH STORY)", ticker);
                        } else {
                                log.info("POST /{}/valuation (MINIMAL OVERRIDE PATTERN)", ticker);
                                log.info("   Received {} override parameter(s)",
                                                countNonNullFields(financialDataInputOverrides));
                        }

                        boolean enableDCFAnalysis = false;

                        return calculateValuation(
                                        ticker, financialDataInputOverrides, addStory, enableDCFAnalysis);

                } catch (RuntimeException e) {
                        log.error("Error in valuation output for ticker {} (addStory={})", ticker, addStory, e);
                        throw e;
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
         * 2. Determine valuation template and primary model
         * 3. Initialize financial data with baseline values
         * 4. Apply user overrides (if any)
         * 5. Adjust sales-to-capital ratio
         * 6. Run initial valuation check
         * 7. Apply calibration and ML adjustments (includes segment analysis)
         * 8. Single calibration to market price
         * 9. Process scenario valuation
         * 10. Copy selected model metadata to output
         * 11. Add story (if requested)
         * 
         * @param ticker            Stock ticker symbol
         * @param overrides         Optional user overrides (null for GET endpoint)
         * @param addStory          Whether to generate narrative story
         * @param enableDCFAnalysis Whether ML-based DCF analysis is enabled
         * @return ValuationOutputDTO with consistent results
         */
        private ValuationOutputDTO calculateValuation(
                        String ticker,
                        FinancialDataInput overrides,
                        boolean addStory,
                        boolean enableDCFAnalysis) {

                // Step 1: Fetch company baseline data from Yahoo Finance
                CompanyDataDTO companyDataDTO = commonService.getCompanyDataFromProvider(ticker);

                // Step 2: Determine valuation template based on company characteristics
                ValuationTemplate template = valuationTemplateService.determineTemplate(null, companyDataDTO);
                log.info("[TEMPLATE] Selected for {}: {} years, Growth: {}, Earnings: {}",
                                ticker, template.getProjectionYears(), template.getGrowthPattern(),
                                template.getEarningsLevel());
                ModelSelectionDecision modelDecision = resolveModelSelection(template);

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

                // Step 6: Run initial valuation check
                ValuationOutputDTO valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                ticker, financialDataInput, false, template);

                // Step 7: Apply calibration fallback if needed
                // Note: Segment analysis is performed INSIDE applyCalibrationAndMLAdjustments
                // after any calibration adjustments, ensuring consistent parameter processing
                ValuationOutputDTO valuationOutputDTO = applyCalibrationAndMLAdjustments(
                                ticker, financialDataInput, companyDataDTO, valuationOutputDTOCheck, enableDCFAnalysis,
                                addStory, template, true);

                // Step 8: Single calibration to market price
                valuationOutputDTO.setCalibrationResultDTO(
                                calibrateToMarketPrice(ticker, new FinancialDataInput(financialDataInput),
                                                valuationOutputDTO.getCompanyDTO().getPrice()));

                // Step 9: Process scenario valuation
                processScenarioValuation(valuationOutputDTO, new FinancialDataInput(financialDataInput),
                                new CompanyDataDTO(companyDataDTO), template);

                // Step 10: Set model metadata from the model resolved in Step 2.
                assignModelSelectionMetadata(valuationOutputDTO, ticker, modelDecision);
                if (companyDataDTO.getBasicInfoDataDTO() != null) {
                        valuationOutputDTO.setIndustryUs(companyDataDTO.getBasicInfoDataDTO().getIndustryUs());
                        valuationOutputDTO.setIndustryGlobal(companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal());
                }

                // Step 11: Add story (if requested)
                if (addStory) {
                        valuationOutputDTO = valuationOutputService.addStory(valuationOutputDTO);
                }

                return valuationOutputDTO;
        }

        /**
         * Sets model selection metadata on output DTO.
         * Valuation pipeline is FCFF-only in local-first mode.
         */
        private void assignModelSelectionMetadata(ValuationOutputDTO valuationOutputDTO, String ticker,
                        ModelSelectionDecision modelDecision) {
                valuationOutputDTO.setPrimaryModel(modelDecision.primaryModel());
                valuationOutputDTO.setModelSelectionRationale(modelDecision.rationale());
                log.info("[MODEL] Primary model for {}: {} (template requested: {})",
                                ticker, modelDecision.primaryModel(), modelDecision.requestedModel());
        }

        private ModelSelectionDecision resolveModelSelection(ValuationTemplate template) {
                CashflowType requestedModel = template != null ? template.getCashflowToDiscount() : CashflowType.FCFF;
                if (requestedModel != CashflowType.FCFF) {
                        throw new IllegalStateException("Only FCFF is supported in the valuation workflow");
                }
                return new ModelSelectionDecision(
                                CashflowType.FCFF,
                                requestedModel,
                                "FCFF selected from valuation template and used for valuation.");
        }

        private record ModelSelectionDecision(
                        CashflowType primaryModel,
                        CashflowType requestedModel,
                        String rationale) {
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
                RDResult rdResult = commonService.calculateRDConverterValue(
                                financialDataInput.getIndustry(),
                                financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                financialDataInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(
                                ticker, financialDataInput.getAverageStrikePrice(),
                                financialDataInput.getAverageMaturity(),
                                financialDataInput.getNumberOfOptions(),
                                financialDataInput.getStockPriceStdDev());
                LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConverter();

                int simulationIterations = Math.max(1, valuationAssumptionProperties.getSimulationIterations());
                List<Double> results = IntStream.range(0, simulationIterations)
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

                RDResult rdResult = commonService.calculateRDConverterValue(
                                financialDataInput.getIndustry(),
                                financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                financialDataInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(
                                companyDataDto.getBasicInfoDataDTO().getTicker(),
                                financialDataInput.getAverageStrikePrice(),
                                financialDataInput.getAverageMaturity(),
                                financialDataInput.getNumberOfOptions(),
                                financialDataInput.getStockPriceStdDev());
                LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConverter();

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

        @Override
        public CalibrationResultDTO calibrateToMarketPrice(String ticker, FinancialDataInput financialDataInput,
                        Double currentPrice) {
                // Initial calculations for RD, Option Value, and Lease
                RDResult rdResult = commonService.calculateRDConverterValue(
                                financialDataInput.getIndustry(),
                                financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                financialDataInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(
                                ticker, financialDataInput.getAverageStrikePrice(),
                                financialDataInput.getAverageMaturity(),
                                financialDataInput.getNumberOfOptions(),
                                financialDataInput.getStockPriceStdDev());
                LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConverter();

                // Hyperparameters
                double epsilon = 0.01; // Tolerance for convergence
                double learningRate = 0.1; // Initial learning rate
                int maxIterations = Math.max(1, valuationAssumptionProperties.getCalibrationMaxIterations());

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
                        if (financialDataInput.getSegments() != null
                                        && financialDataInput.getSegments().getSegments() != null
                                        && !financialDataInput.getSegments().getSegments().isEmpty()) {
                                log.info("✅ Loaded {} segments for sector override processing",
                                                financialDataInput.getSegments().getSegments().size());

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
                        boolean enableSegments) {

                if (!enableSegments) {
                        log.info("Multi-segment analysis disabled for {}", ticker);
                        return;
                }

                if (financialDataInput.getSegments() != null && financialDataInput.getSegments().getSegments() != null
                                && financialDataInput.getSegments().getSegments().size() > 1) {
                        List<String> adjustedParameters = new ArrayList<>();
                        commonService.applySegmentWeightedParameters(financialDataInput, companyDataDTO,
                                        adjustedParameters);
                        log.info("Multi-segment analysis applied for {} with {} segments",
                                        ticker, financialDataInput.getSegments().getSegments().size());
                } else {
                        log.warn("Multi-segment analysis enabled but no segment data present in input for {}", ticker);
                }
        }

        private ValuationOutputDTO applyCalibrationAndMLAdjustments(
                        String ticker,
                        FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDTO,
                        ValuationOutputDTO valuationOutputDTOCheck,
                        boolean enableDCFAnalysis,
                        boolean addStory,
                        ValuationTemplate template,
                        boolean enableSegments) {

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

                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments);

                        return valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, addStory, template);
                } else {
                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments);

                        return valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, addStory, template);
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
                                        commonService.calculateRDConverterValue(
                                                        financialDataInput.getIndustry(),
                                                        financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                                        financialDataInput.getFinancialDataDTO()
                                                                        .getResearchAndDevelopmentMap()),
                                        commonService.calculateOperatingLeaseConverter());

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
         * getValuationOutputWithStory.
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
                        if (overrides.getTargetPreTaxOperatingMargin() == null) {
                                baseline.setTargetPreTaxOperatingMargin(overrides.getOperatingMarginNextYear());
                                log.info("   Derived override: targetPreTaxOperatingMargin = {} (from operatingMarginNextYear)",
                                                overrides.getOperatingMarginNextYear());
                        }
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

                // Copy segments provided by caller (valuation-agent) for multi-segment DCF
                // breakdown and weighting.
                if (overrides.getSegments() != null
                                && overrides.getSegments().getSegments() != null
                                && !overrides.getSegments().getSegments().isEmpty()) {
                        baseline.setSegments(overrides.getSegments());
                        log.info("   Override: segments = {} segment(s)",
                                        overrides.getSegments().getSegments().size());
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
