package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationoutput.AssumptionTransparencyDTO;
import io.stockvaluation.dto.valuationoutput.CalibrationResultDTO;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.dto.valuationoutput.FinancialDTO;
import io.stockvaluation.dto.valuationoutput.SimulationResultsDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.utils.MarketRegionResolver;
import io.stockvaluation.utils.SegmentParameterContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.stockvaluation.service.GrowthCalculatorService.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ValuationWorkflowServiceImpl implements ValuationWorkflowService {
        private static final double FORCE_THREE_STAGE_PREMIUM_THRESHOLD = 150.0;
        private static final double FORCE_THREE_STAGE_DISCOUNT_THRESHOLD = 67.0;

        private final CommonService commonService;
        private final OptionValueService optionValueService;
        private final ValuationOutputService valuationOutputService;
        private final ValuationTemplateService valuationTemplateService;
        private final ValuationAssumptionProperties valuationAssumptionProperties;
        private final GrowthAnchorService growthAnchorService;

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
                String growthAnchorRegion = MarketRegionResolver
                                .resolveGrowthAnchorRegion(companyDataDTO.getBasicInfoDataDTO());

                // Step 2: Determine valuation template based on company characteristics
                ValuationTemplate template = valuationTemplateService.determineTemplate(overrides, companyDataDTO);
                log.info("[TEMPLATE] Selected for {}: {} years, Growth: {}, Earnings: {}",
                                ticker, template.getProjectionYears(), template.getGrowthPattern(),
                                template.getEarningsLevel());
                String templateSelectionReason = resolveTemplateSelectionReason(template);
                ModelSelectionDecision modelDecision = resolveModelSelection(template);

                // Step 3: Initialize financial data with baseline values
                FinancialDataInput financialDataInput = initializeFinancialDataInput(companyDataDTO, template);

                // Step 4: Apply user overrides (if any)
                List<String> adjustedParameters = new ArrayList<>();
                if (overrides != null) {
                        adjustedParameters = applyUserOverrides(financialDataInput, overrides);
                }

                // VAL-3: Strict Growth Policy Guard (Optional)
                if (valuationAssumptionProperties.isStrictGrowthPolicy()) {
                        final FinancialDataInput strictGrowthInput = financialDataInput;
                        growthAnchorService.getAnchorByYahooIndustry(
                                        companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal(),
                                        growthAnchorRegion)
                                        .ifPresent(anchor -> {
                                                // Only enforce rigorously if we have high heuristic confidence
                                                if (anchor.getConfidenceScore() != null
                                                                && anchor.getConfidenceScore() > 0.8) {
                                                        double inputCagr = strictGrowthInput
                                                                        .getCompoundAnnualGrowth2_5() / 100.0;
                                                        Double p10 = anchor.getP10();
                                                        Double p90 = anchor.getP90();

                                                        if (p10 != null && p90 != null
                                                                        && (inputCagr < p10 || inputCagr > p90)) {
                                                                log.warn("VAL-3 Guard tripped: Intolerable growth assumption {} for {}, anchor p10={}, p90={}",
                                                                                inputCagr, ticker, p10, p90);
                                                                String msg = String.format(
                                                                                "{\"error\": \"GROWTH_ASSUMPTION_INCOHERENT\", \"band\": {\"p10\": %.3f, \"p90\": %.3f}, \"provided\": %.3f}",
                                                                                p10, p90, inputCagr);
                                                                throw new ResponseStatusException(
                                                                                HttpStatus.UNPROCESSABLE_ENTITY, msg);
                                                        }
                                                }
                                        });
                }

                // Step 5: Adjust sales-to-capital ratio to be at least current ratio
                adjustSalesToCapitalRatio(financialDataInput);

                // Step 5.5: Start intrinsic pricing fetch in parallel (if requested)
                // This runs concurrently with Steps 6-10, saving significant time

                // Step 6: Run initial valuation check
                ValuationOutputDTO valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                ticker, financialDataInput, false, template);

                if (shouldForceThreeStageTemplate(template, overrides, valuationOutputDTOCheck)) {
                        Double priceToValuePct = calculatePriceToValuePct(valuationOutputDTOCheck);
                        templateSelectionReason = buildForcedThreeStageReason(priceToValuePct);
                        template = valuationTemplateService.withGrowthPattern(
                                        template,
                                        GrowthPattern.THREE_STAGE,
                                        templateSelectionReason);
                        log.info("[TEMPLATE] Forcing THREE_STAGE for {} after baseline check (price/value={}%)",
                                        ticker, round2(priceToValuePct));

                        financialDataInput = initializeFinancialDataInput(companyDataDTO, template);
                        adjustedParameters = new ArrayList<>();
                        if (overrides != null) {
                                adjustedParameters = applyUserOverrides(financialDataInput, overrides);
                        }
                        adjustSalesToCapitalRatio(financialDataInput);
                        valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                        ticker, financialDataInput, false, template);
                }

                // Step 7: Apply calibration fallback if needed
                // Note: Segment analysis is performed INSIDE applyCalibrationAndMLAdjustments
                // after any calibration adjustments, ensuring consistent parameter processing
                ValuationOutputDTO valuationOutputDTO = applyCalibrationAndMLAdjustments(
                                ticker, financialDataInput, companyDataDTO, valuationOutputDTOCheck, enableDCFAnalysis,
                                addStory, template, true, adjustedParameters);

                if (shouldForceThreeStageTemplate(template, overrides, valuationOutputDTO)) {
                        Double priceToValuePct = calculatePriceToValuePct(valuationOutputDTO);
                        templateSelectionReason = buildForcedThreeStageReason(priceToValuePct);
                        template = valuationTemplateService.withGrowthPattern(
                                        template,
                                        GrowthPattern.THREE_STAGE,
                                        templateSelectionReason);
                        log.info("[TEMPLATE] Forcing THREE_STAGE for {} after segment-aware valuation (price/value={}%)",
                                        ticker, round2(priceToValuePct));

                        financialDataInput = initializeFinancialDataInput(companyDataDTO, template);
                        adjustedParameters = new ArrayList<>();
                        if (overrides != null) {
                                adjustedParameters = applyUserOverrides(financialDataInput, overrides);
                        }
                        adjustSalesToCapitalRatio(financialDataInput);
                        valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                        ticker, financialDataInput, false, template);
                        valuationOutputDTO = applyCalibrationAndMLAdjustments(
                                        ticker, financialDataInput, companyDataDTO, valuationOutputDTOCheck,
                                        enableDCFAnalysis, addStory, template, true, adjustedParameters);
                }

                // Step 8: Single calibration to market price
                Double currentMarketPrice = valuationOutputDTO.getCompanyDTO() != null
                                ? valuationOutputDTO.getCompanyDTO().getPrice()
                                : null;
                if (currentMarketPrice != null && currentMarketPrice > 0) {
                        valuationOutputDTO.setCalibrationResultDTO(
                                        calibrateToMarketPrice(ticker, new FinancialDataInput(financialDataInput),
                                                        currentMarketPrice));
                } else {
                        log.warn("Skipping calibration for {} because current market price is missing or non-positive: {}",
                                        ticker, currentMarketPrice);
                }

                // Step 9: Process scenario valuation
                processScenarioValuation(valuationOutputDTO, new FinancialDataInput(financialDataInput),
                                new CompanyDataDTO(companyDataDTO), template);

                // Step 10: Set model metadata from the model resolved in Step 2.
                assignModelSelectionMetadata(valuationOutputDTO, ticker, modelDecision);
                assignTemplateMetadata(valuationOutputDTO, template, templateSelectionReason);
                if (companyDataDTO.getBasicInfoDataDTO() != null) {
                        valuationOutputDTO.setIndustryUs(companyDataDTO.getBasicInfoDataDTO().getIndustryUs());
                        valuationOutputDTO.setIndustryGlobal(companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal());
                }

                // Step 11: Add assumption transparency (including market-implied expectations)
                valuationOutputDTO.setAssumptionTransparency(buildAssumptionTransparency(
                                ticker,
                                financialDataInput,
                                valuationOutputDTO,
                                template,
                                templateSelectionReason));

                // Step 12: Add story (if requested)
                if (addStory) {
                        valuationOutputDTO = valuationOutputService.addStory(valuationOutputDTO);
                }

                // Step 13: Add Growth Anchor Diagnostics
                Optional<io.stockvaluation.dto.GrowthAnchorDTO> anchorDtoOpt = growthAnchorService
                                .getAnchorByYahooIndustry(
                                                companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal(),
                                                growthAnchorRegion);
                if (anchorDtoOpt.isPresent()) {
                        GrowthAnchorDTO anchor = anchorDtoOpt.get();
                        valuationOutputDTO.setGrowthSkillContext(anchor);
                        if (valuationOutputDTO.getAssumptionTransparency() != null) {
                                valuationOutputDTO.getAssumptionTransparency().setGrowthAnchor(toGrowthAnchor(anchor));
                        }
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

        private void assignTemplateMetadata(
                        ValuationOutputDTO valuationOutputDTO,
                        ValuationTemplate template,
                        String templateSelectionReason) {
                if (valuationOutputDTO == null || template == null) {
                        return;
                }
                valuationOutputDTO.setGrowthPattern(template.getGrowthPattern());
                valuationOutputDTO.setProjectionYears(template.getProjectionYears());
                valuationOutputDTO.setTemplateSelectionReason(templateSelectionReason);
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

        private AssumptionTransparencyDTO buildAssumptionTransparency(
                        String ticker,
                        FinancialDataInput financialDataInput,
                        ValuationOutputDTO valuationOutputDTO,
                        ValuationTemplate template,
                        String templateSelectionReason) {
                AssumptionTransparencyDTO dto = new AssumptionTransparencyDTO();
                dto.setValuationModel(valuationOutputDTO.getPrimaryModel() != null
                                ? valuationOutputDTO.getPrimaryModel().name()
                                : CashflowType.FCFF.name());
                dto.setIndustryUs(valuationOutputDTO.getIndustryUs());
                dto.setIndustryGlobal(valuationOutputDTO.getIndustryGlobal());
                dto.setCurrency(valuationOutputDTO.getCurrency());
                dto.setGrowthPattern(template != null && template.getGrowthPattern() != null
                                ? template.getGrowthPattern().name()
                                : null);
                dto.setProjectionYears(template != null ? template.getProjectionYears() : null);
                dto.setTemplateSelectionReason(templateSelectionReason);
                dto.setSegmentCount(financialDataInput.getSegments() != null
                                && financialDataInput.getSegments().getSegments() != null
                                                ? financialDataInput.getSegments().getSegments().size()
                                                : 0);
                dto.setSegmentAware(dto.getSegmentCount() != null && dto.getSegmentCount() > 1);

                FinancialDTO financialDTO = valuationOutputDTO.getFinancialDTO();
                Double riskFreeRate = normalizePercent(financialDataInput.getRiskFreeRate());
                Double initialCostOfCapital = normalizePercent(firstNonNull(
                                financialDataInput.getInitialCostCapital(),
                                firstFinite(financialDTO != null ? financialDTO.getCostOfCapital() : null)));
                Double terminalCostOfCapital = normalizePercent(firstNonNull(
                                valuationOutputDTO.getTerminalValueDTO() != null
                                                ? valuationOutputDTO.getTerminalValueDTO().getCostOfCapital()
                                                : null,
                                lastFinite(financialDTO != null ? financialDTO.getCostOfCapital() : null)));

                Double equityRiskPremium = null;
                if (riskFreeRate != null && terminalCostOfCapital != null) {
                        equityRiskPremium = round2(terminalCostOfCapital - riskFreeRate);
                }

                dto.setDiscountRate(new AssumptionTransparencyDTO.DiscountRate(
                                riskFreeRate,
                                equityRiskPremium,
                                initialCostOfCapital,
                                terminalCostOfCapital,
                                "Terminal WACC = risk-free rate + country equity risk premium; country ERP is configured Damodaran mature market ERP plus country risk premium.",
                                riskFreeRate != null ? "Valuation input baseline/override" : "Not available",
                                equityRiskPremium != null
                                                ? "Configured Damodaran mature-market ERP plus country risk premium from "
                                                                + valuationAssumptionProperties.getDamodaran()
                                                                                .getCountryRiskSource()
                                                                + " ("
                                                                + valuationAssumptionProperties.getDamodaran()
                                                                                .getDataDate()
                                                                + ")."
                                                : "Not available",
                                "Final FCFF output"));

                Double effectiveRevenueGrowth = SegmentParameterContext.getParameterOrDefault(
                                SegmentWeightedParameters::getWeightedCompoundAnnualGrowth2_5,
                                financialDataInput.getCompoundAnnualGrowth2_5());
                Double effectiveOperatingMarginNextYear = SegmentParameterContext.getParameterOrDefault(
                                SegmentWeightedParameters::getWeightedOperatingMarginNextYear,
                                financialDataInput.getOperatingMarginNextYear());
                Double effectiveTargetOperatingMargin = SegmentParameterContext.getParameterOrDefault(
                                SegmentWeightedParameters::getWeightedTargetPreTaxOperatingMargin,
                                financialDataInput.getTargetPreTaxOperatingMargin());
                Double effectiveConvergenceYear = SegmentParameterContext.getParameterOrDefault(
                                SegmentWeightedParameters::getConvergenceYearMargin,
                                financialDataInput.getConvergenceYearMargin());
                Double effectiveSalesToCapitalYears1To5 = SegmentParameterContext.getParameterOrDefault(
                                SegmentWeightedParameters::getWeightedSalesToCapitalYears1To5,
                                financialDataInput.getSalesToCapitalYears1To5());
                Double effectiveSalesToCapitalYears6To10 = SegmentParameterContext.getParameterOrDefault(
                                SegmentWeightedParameters::getWeightedSalesToCapitalYears6To10,
                                financialDataInput.getSalesToCapitalYears6To10());

                dto.setOperatingAssumptions(new AssumptionTransparencyDTO.OperatingAssumptions(
                                normalizePercent(effectiveRevenueGrowth),
                                normalizePercent(effectiveOperatingMarginNextYear),
                                normalizePercent(effectiveTargetOperatingMargin),
                                round2(effectiveConvergenceYear),
                                normalizeMultiple(effectiveSalesToCapitalYears1To5),
                                normalizeMultiple(effectiveSalesToCapitalYears6To10),
                                "Valuation input baseline/override",
                                "Valuation input baseline/override",
                                "Valuation input baseline/override",
                                null,
                                null,
                                null));

                List<String> notes = new ArrayList<>();
                notes.add("Rates are shown in percent.");
                notes.add("Sales-to-capital is shown as x multiple.");
                notes.add("Single-lever market expectation checks solve one variable at a time while others stay fixed.");
                if (isForcedThreeStageReason(templateSelectionReason)) {
                        notes.add("Projection was upgraded to THREE_STAGE because market price and intrinsic value diverged materially in the first-pass baseline.");
                }
                dto.setNotes(notes);

                dto.setMarketImpliedExpectations(buildMarketImpliedExpectations(
                                ticker,
                                financialDataInput,
                                valuationOutputDTO,
                                template));
                dto.setPricedInExpectations(buildPricedInExpectations(
                                ticker,
                                financialDataInput,
                                valuationOutputDTO,
                                template));
                return dto;
        }

        private AssumptionTransparencyDTO.MarketImpliedExpectations buildMarketImpliedExpectations(
                        String ticker,
                        FinancialDataInput baseInput,
                        ValuationOutputDTO valuationOutputDTO,
                        ValuationTemplate template) {
                CompanyDTO company = valuationOutputDTO.getCompanyDTO();
                AssumptionTransparencyDTO.MarketImpliedExpectations expectations = new AssumptionTransparencyDTO.MarketImpliedExpectations();
                expectations.setMethod(
                                "Single-variable market expectation checks; each lever is solved independently to match the current market price.");

                if (company == null || company.getPrice() == null || company.getPrice() <= 0) {
                        expectations.setMetrics(new ArrayList<>());
                        return expectations;
                }

                double marketPrice = company.getPrice();
                expectations.setMarketPrice(round2(marketPrice));
                expectations.setModelIntrinsicValue(round2(company.getEstimatedValuePerShare()));
                double baseGrowth = getEffectiveRevenueGrowth(baseInput);
                double baseMargin = getEffectiveTargetOperatingMargin(baseInput);
                double baseSalesToCapital = getEffectiveSalesToCapitalYears1To5(baseInput);

                RDResult rdResult = commonService.calculateRDConverterValue(
                                baseInput.getIndustry(),
                                baseInput.getFinancialDataDTO().getMarginalTaxRate(),
                                baseInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValue = optionValueService.calculateOptionValue(
                                ticker,
                                baseInput.getAverageStrikePrice(),
                                baseInput.getAverageMaturity(),
                                baseInput.getNumberOfOptions(),
                                baseInput.getStockPriceStdDev());
                LeaseResultDTO leaseResult = commonService.calculateOperatingLeaseConverter();

                List<AssumptionTransparencyDTO.ImpliedMetric> metrics = new ArrayList<>();

                SolveResult impliedGrowth = solveImpliedVariable(
                                baseInput,
                                marketPrice,
                                (input, value) -> input.setCompoundAnnualGrowth2_5(value),
                                -30.0,
                                50.0,
                                ticker,
                                rdResult,
                                optionValue,
                                leaseResult,
                                template);
                metrics.add(toImpliedMetric(
                                "revenue_cagr",
                                "Revenue Growth (Years 2-5)",
                                "percent",
                                normalizePercent(baseGrowth),
                                normalizePercent(impliedGrowth.value()),
                                impliedGrowth.solved()));

                SolveResult impliedMargin = solveImpliedVariable(
                                baseInput,
                                marketPrice,
                                (input, value) -> input.setTargetPreTaxOperatingMargin(value),
                                0.5,
                                85.0,
                                ticker,
                                rdResult,
                                optionValue,
                                leaseResult,
                                template);
                metrics.add(toImpliedMetric(
                                "operating_margin",
                                "Operating Margin (Years 2-5)",
                                "percent",
                                normalizePercent(baseMargin),
                                normalizePercent(impliedMargin.value()),
                                impliedMargin.solved()));

                double stcBaseRaw = firstNonNull(baseInput.getSalesToCapitalYears1To5(), 2.0);
                double stcLower = stcBaseRaw > 20.0 ? 25.0 : 0.25;
                double stcUpper = stcBaseRaw > 20.0 ? 2000.0 : 20.0;
                SolveResult impliedSalesToCapital = solveImpliedVariable(
                                baseInput,
                                marketPrice,
                                (input, value) -> {
                                        input.setSalesToCapitalYears1To5(value);
                                        input.setSalesToCapitalYears6To10(value);
                                },
                                stcLower,
                                stcUpper,
                                ticker,
                                rdResult,
                                optionValue,
                                leaseResult,
                                template);
                metrics.add(toImpliedMetric(
                                "sales_to_capital",
                                "Sales/Capital (Years 2-5)",
                                "multiple",
                                normalizeMultiple(baseSalesToCapital),
                                normalizeMultiple(impliedSalesToCapital.value()),
                                impliedSalesToCapital.solved()));

                expectations.setMetrics(metrics);
                return expectations;
        }

        private AssumptionTransparencyDTO.PricedInExpectations buildPricedInExpectations(
                        String ticker,
                        FinancialDataInput baseInput,
                        ValuationOutputDTO valuationOutputDTO,
                        ValuationTemplate template) {
                CompanyDTO company = valuationOutputDTO.getCompanyDTO();
                AssumptionTransparencyDTO.PricedInExpectations expectations =
                                new AssumptionTransparencyDTO.PricedInExpectations();
                expectations.setMethod(
                                "Deterministic market expectations grid; growth and margin vary together while risk and capital efficiency are scenario toggles.");

                if (company == null || company.getPrice() == null || company.getPrice() <= 0) {
                        expectations.setScenarios(new ArrayList<>());
                        expectations.setGrid(new ArrayList<>());
                        expectations.setFrontier(new ArrayList<>());
                        return expectations;
                }

                double marketPrice = company.getPrice();
                Double modelIntrinsicValue = company.getEstimatedValuePerShare();
                expectations.setMarketPrice(round2(marketPrice));
                expectations.setModelIntrinsicValue(round2(modelIntrinsicValue));

                double baseGrowth = getEffectiveRevenueGrowth(baseInput);
                double baseMargin = getEffectiveTargetOperatingMargin(baseInput);
                double baseRiskRaw = getEffectiveInitialCostOfCapital(baseInput);
                double baseRisk = firstNonNull(normalizePercent(baseRiskRaw), 0.0);
                double baseSalesToCapital = getEffectiveSalesToCapitalYears1To5(baseInput);

                expectations.setBaseCase(new AssumptionTransparencyDTO.BaseCase(
                                round2(baseGrowth),
                                round2(baseMargin),
                                round2(baseRisk),
                                round2(baseSalesToCapital),
                                round2(modelIntrinsicValue),
                                calculateGapToMarket(modelIntrinsicValue, marketPrice),
                                calculateGapToMarketPct(modelIntrinsicValue, marketPrice)));

                RDResult rdResult = commonService.calculateRDConverterValue(
                                baseInput.getIndustry(),
                                baseInput.getFinancialDataDTO().getMarginalTaxRate(),
                                baseInput.getFinancialDataDTO().getResearchAndDevelopmentMap());
                OptionValueResultDTO optionValue = optionValueService.calculateOptionValue(
                                ticker,
                                baseInput.getAverageStrikePrice(),
                                baseInput.getAverageMaturity(),
                                baseInput.getNumberOfOptions(),
                                baseInput.getStockPriceStdDev());
                LeaseResultDTO leaseResult = commonService.calculateOperatingLeaseConverter();

                List<Double> growthAxis = buildAxis(baseGrowth, 10.0, -30.0, 50.0);
                List<Double> marginAxis = buildAxis(baseMargin, 10.0, 0.5, 85.0);
                List<PricedInScenarioDefinition> definitions = buildPricedInScenarioDefinitions(baseRisk,
                                baseSalesToCapital);

                List<AssumptionTransparencyDTO.PricedInScenario> scenarios = new ArrayList<>();
                AssumptionTransparencyDTO.PricedInScenario baseScenario = null;
                for (PricedInScenarioDefinition definition : definitions) {
                        List<AssumptionTransparencyDTO.PricedInGridPoint> grid = buildPricedInGrid(
                                        baseInput,
                                        growthAxis,
                                        marginAxis,
                                        definition.initialCostOfCapital(),
                                        baseRiskRaw,
                                        definition.salesToCapital(),
                                        marketPrice,
                                        ticker,
                                        rdResult,
                                        optionValue,
                                        leaseResult,
                                        template);
                        List<AssumptionTransparencyDTO.PricedInFrontierPoint> frontier =
                                        buildPricedInFrontier(grid, marginAxis, marketPrice);
                        AssumptionTransparencyDTO.PricedInScenario scenario =
                                        new AssumptionTransparencyDTO.PricedInScenario(
                                                        definition.key(),
                                                        definition.label(),
                                                        definition.riskKey(),
                                                        definition.riskLabel(),
                                                        definition.capitalEfficiencyKey(),
                                                        definition.capitalEfficiencyLabel(),
                                                        round2(definition.initialCostOfCapital()),
                                                        round2(definition.salesToCapital()),
                                                        buildPricedInHeadline(frontier, definition),
                                                        grid,
                                                        frontier);
                        scenarios.add(scenario);
                        if ("base_risk__base_efficiency".equals(definition.key())) {
                                baseScenario = scenario;
                        }
                }

                expectations.setScenarios(scenarios);
                if (baseScenario != null) {
                        expectations.setGrid(baseScenario.getGrid());
                        expectations.setFrontier(baseScenario.getFrontier());
                } else {
                        expectations.setGrid(scenarios.isEmpty() ? new ArrayList<>() : scenarios.get(0).getGrid());
                        expectations.setFrontier(scenarios.isEmpty() ? new ArrayList<>() : scenarios.get(0).getFrontier());
                }
                return expectations;
        }

        private List<AssumptionTransparencyDTO.PricedInGridPoint> buildPricedInGrid(
                        FinancialDataInput baseInput,
                        List<Double> growthAxis,
                        List<Double> marginAxis,
                        double initialCostOfCapital,
                        double initialCostOfCapitalScaleReference,
                        double salesToCapital,
                        double marketPrice,
                        String ticker,
                        RDResult rdResult,
                        OptionValueResultDTO optionValue,
                        LeaseResultDTO leaseResult,
                        ValuationTemplate template) {
                List<AssumptionTransparencyDTO.PricedInGridPoint> grid = new ArrayList<>();
                for (Double margin : marginAxis) {
                        for (Double growth : growthAxis) {
                                Double intrinsicValue = evaluatePricedInValue(
                                                baseInput,
                                                growth,
                                                margin,
                                                initialCostOfCapital,
                                                initialCostOfCapitalScaleReference,
                                                salesToCapital,
                                                rdResult,
                                                optionValue,
                                                leaseResult,
                                                ticker,
                                                template);
                                grid.add(new AssumptionTransparencyDTO.PricedInGridPoint(
                                                round2(growth),
                                                round2(margin),
                                                round2(initialCostOfCapital),
                                                round2(salesToCapital),
                                                round2(intrinsicValue),
                                                calculateGapToMarket(intrinsicValue, marketPrice),
                                                calculateGapToMarketPct(intrinsicValue, marketPrice),
                                                intrinsicValue != null && intrinsicValue >= marketPrice));
                        }
                }
                return grid;
        }

        private Double evaluatePricedInValue(
                        FinancialDataInput baseInput,
                        double growth,
                        double margin,
                        double initialCostOfCapital,
                        double initialCostOfCapitalScaleReference,
                        double salesToCapital,
                        RDResult rdResult,
                        OptionValueResultDTO optionValue,
                        LeaseResultDTO leaseResult,
                        String ticker,
                        ValuationTemplate template) {
                try {
                        FinancialDataInput scenario = new FinancialDataInput(baseInput);
                        scenario.setCompoundAnnualGrowth2_5(growth);
                        scenario.setTargetPreTaxOperatingMargin(margin);
                        scenario.setInitialCostCapital(restorePercentScale(
                                        initialCostOfCapitalScaleReference,
                                        initialCostOfCapital));
                        scenario.setSalesToCapitalYears1To5(salesToCapital);
                        scenario.setSalesToCapitalYears6To10(salesToCapital);
                        double estimate = getEstimatedValuePerShareWithScenarioContext(scenario, rdResult, optionValue,
                                        leaseResult, ticker, template);
                        if (!Double.isFinite(estimate)) {
                                return null;
                        }
                        return estimate;
                } catch (RuntimeException ex) {
                        log.debug("Skipping priced-in grid point for {} due to evaluation error: {}", ticker,
                                        ex.getMessage());
                        return null;
                }
        }

        private double getEffectiveRevenueGrowth(FinancialDataInput input) {
                return firstNonNull(
                                SegmentParameterContext.getParameterOrDefault(
                                                SegmentWeightedParameters::getWeightedCompoundAnnualGrowth2_5,
                                                input.getCompoundAnnualGrowth2_5()),
                                0.0);
        }

        private double getEffectiveTargetOperatingMargin(FinancialDataInput input) {
                return firstNonNull(
                                SegmentParameterContext.getParameterOrDefault(
                                                SegmentWeightedParameters::getWeightedTargetPreTaxOperatingMargin,
                                                input.getTargetPreTaxOperatingMargin()),
                                0.0);
        }

        private double getEffectiveInitialCostOfCapital(FinancialDataInput input) {
                return firstNonNull(
                                SegmentParameterContext.getParameterOrDefault(
                                                SegmentWeightedParameters::getWeightedInitialCostCapital,
                                                input.getInitialCostCapital()),
                                0.0);
        }

        private double getEffectiveSalesToCapitalYears1To5(FinancialDataInput input) {
                return firstNonNull(
                                SegmentParameterContext.getParameterOrDefault(
                                                SegmentWeightedParameters::getWeightedSalesToCapitalYears1To5,
                                                input.getSalesToCapitalYears1To5()),
                                2.0);
        }

        private double getEstimatedValuePerShareWithScenarioContext(
                        FinancialDataInput scenario,
                        RDResult rdResult,
                        OptionValueResultDTO optionValue,
                        LeaseResultDTO leaseResult,
                        String ticker,
                        ValuationTemplate template) {
                SegmentWeightedParameters originalSegmentParameters = SegmentParameterContext.getParameters();
                boolean shouldOverrideSegmentContext = originalSegmentParameters != null
                                && originalSegmentParameters.hasValidParameters();

                if (shouldOverrideSegmentContext) {
                        SegmentParameterContext.setParameters(
                                        buildScenarioSegmentParameters(originalSegmentParameters, scenario));
                }

                try {
                        return getEstimatedValuePerShare(scenario, rdResult, optionValue, leaseResult, ticker,
                                        template);
                } finally {
                        if (originalSegmentParameters != null) {
                                SegmentParameterContext.setParameters(originalSegmentParameters);
                        } else {
                                SegmentParameterContext.clear();
                        }
                }
        }

        private SegmentWeightedParameters buildScenarioSegmentParameters(
                        SegmentWeightedParameters original,
                        FinancialDataInput scenario) {
                SegmentWeightedParameters adjusted = original.copy();

                Double targetGrowth = firstNonNull(
                                scenario.getCompoundAnnualGrowth2_5(),
                                original.getWeightedCompoundAnnualGrowth2_5());
                Double targetMargin = firstNonNull(
                                scenario.getTargetPreTaxOperatingMargin(),
                                original.getWeightedTargetPreTaxOperatingMargin());
                Double targetInitialCost = firstNonNull(
                                scenario.getInitialCostCapital(),
                                original.getWeightedInitialCostCapital());
                Double targetSalesToCapital1To5 = firstNonNull(
                                scenario.getSalesToCapitalYears1To5(),
                                original.getWeightedSalesToCapitalYears1To5());
                Double targetSalesToCapital6To10 = firstNonNull(
                                scenario.getSalesToCapitalYears6To10(),
                                original.getWeightedSalesToCapitalYears6To10());

                adjusted.setWeightedCompoundAnnualGrowth2_5(targetGrowth);
                adjusted.setWeightedTargetPreTaxOperatingMargin(targetMargin);
                adjusted.setWeightedInitialCostCapital(targetInitialCost);
                adjusted.setWeightedSalesToCapitalYears1To5(targetSalesToCapital1To5);
                adjusted.setWeightedSalesToCapitalYears6To10(targetSalesToCapital6To10);

                for (SegmentWeightedParameters.SectorParameters sector : adjusted.getSectorParameters().values()) {
                        if (sector == null) {
                                continue;
                        }
                        sector.setCompoundAnnualGrowth2_5(shiftByWeightedDelta(
                                        sector.getCompoundAnnualGrowth2_5(),
                                        targetGrowth,
                                        original.getWeightedCompoundAnnualGrowth2_5()));
                        sector.setTargetPreTaxOperatingMargin(clamp(
                                        shiftByWeightedDelta(
                                                        sector.getTargetPreTaxOperatingMargin(),
                                                        targetMargin,
                                                        original.getWeightedTargetPreTaxOperatingMargin()),
                                        -100.0,
                                        100.0));
                        sector.setInitialCostCapital(Math.max(0.1,
                                        shiftByWeightedDelta(
                                                        sector.getInitialCostCapital(),
                                                        targetInitialCost,
                                                        original.getWeightedInitialCostCapital())));
                        sector.setSalesToCapitalYears1To5(scaleByWeightedRatio(
                                        sector.getSalesToCapitalYears1To5(),
                                        targetSalesToCapital1To5,
                                        original.getWeightedSalesToCapitalYears1To5()));
                        sector.setSalesToCapitalYears6To10(scaleByWeightedRatio(
                                        sector.getSalesToCapitalYears6To10(),
                                        targetSalesToCapital6To10,
                                        original.getWeightedSalesToCapitalYears6To10()));
                }

                return adjusted;
        }

        private Double shiftByWeightedDelta(Double sectorValue, Double targetWeightedValue, Double originalWeightedValue) {
                if (targetWeightedValue == null) {
                        return sectorValue;
                }
                if (sectorValue == null || originalWeightedValue == null) {
                        return targetWeightedValue;
                }
                return sectorValue + (targetWeightedValue - originalWeightedValue);
        }

        private Double scaleByWeightedRatio(Double sectorValue, Double targetWeightedValue, Double originalWeightedValue) {
                if (targetWeightedValue == null) {
                        return sectorValue;
                }
                if (sectorValue == null || originalWeightedValue == null || Math.abs(originalWeightedValue) < 0.000001) {
                        return Math.max(0.0001, targetWeightedValue);
                }
                return Math.max(0.0001, sectorValue * (targetWeightedValue / originalWeightedValue));
        }

        private Double clamp(Double value, double lower, double upper) {
                if (value == null) {
                        return null;
                }
                return Math.max(lower, Math.min(upper, value));
        }

        private List<AssumptionTransparencyDTO.PricedInFrontierPoint> buildPricedInFrontier(
                        List<AssumptionTransparencyDTO.PricedInGridPoint> grid,
                        List<Double> marginAxis,
                        double marketPrice) {
                List<AssumptionTransparencyDTO.PricedInFrontierPoint> frontier = new ArrayList<>();
                for (Double margin : marginAxis) {
                        List<AssumptionTransparencyDTO.PricedInGridPoint> row = grid.stream()
                                        .filter(point -> point.getOperatingMargin() != null
                                                        && Math.abs(point.getOperatingMargin() - round2(margin)) < 0.001)
                                        .sorted(Comparator.comparing(AssumptionTransparencyDTO.PricedInGridPoint::getRevenueGrowth,
                                                        Comparator.nullsLast(Double::compareTo)))
                                        .collect(Collectors.toList());
                        frontier.add(interpolateFrontierPoint(row, margin, marketPrice));
                }
                return frontier;
        }

        private AssumptionTransparencyDTO.PricedInFrontierPoint interpolateFrontierPoint(
                        List<AssumptionTransparencyDTO.PricedInGridPoint> row,
                        Double margin,
                        double marketPrice) {
                AssumptionTransparencyDTO.PricedInGridPoint nearest = null;
                Double nearestAbsGap = null;

                for (int i = 0; i < row.size(); i++) {
                        AssumptionTransparencyDTO.PricedInGridPoint current = row.get(i);
                        if (current.getIntrinsicValue() == null || current.getRevenueGrowth() == null) {
                                continue;
                        }
                        double currentGap = current.getIntrinsicValue() - marketPrice;
                        double currentAbsGap = Math.abs(currentGap);
                        if (nearestAbsGap == null || currentAbsGap < nearestAbsGap) {
                                nearest = current;
                                nearestAbsGap = currentAbsGap;
                        }
                        if (Math.abs(currentGap) <= Math.max(0.01,
                                        valuationAssumptionProperties.getImpliedExpectationTolerance())) {
                                return new AssumptionTransparencyDTO.PricedInFrontierPoint(
                                                round2(margin),
                                                round2(current.getRevenueGrowth()),
                                                round2(current.getIntrinsicValue()),
                                                calculateGapToMarket(current.getIntrinsicValue(), marketPrice),
                                                calculateGapToMarketPct(current.getIntrinsicValue(), marketPrice),
                                                true,
                                                "Grid point is within tolerance of current market price.");
                        }
                        if (i == 0) {
                                continue;
                        }
                        AssumptionTransparencyDTO.PricedInGridPoint previous = row.get(i - 1);
                        if (previous.getIntrinsicValue() == null || previous.getRevenueGrowth() == null) {
                                continue;
                        }
                        double previousGap = previous.getIntrinsicValue() - marketPrice;
                        if ((previousGap <= 0 && currentGap >= 0) || (previousGap >= 0 && currentGap <= 0)) {
                                double denominator = current.getIntrinsicValue() - previous.getIntrinsicValue();
                                double t = Math.abs(denominator) < 0.000001
                                                ? 0.0
                                                : (marketPrice - previous.getIntrinsicValue()) / denominator;
                                double impliedGrowth = previous.getRevenueGrowth()
                                                + t * (current.getRevenueGrowth() - previous.getRevenueGrowth());
                                return new AssumptionTransparencyDTO.PricedInFrontierPoint(
                                                round2(margin),
                                                round2(impliedGrowth),
                                                round2(marketPrice),
                                                0.0,
                                                0.0,
                                                true,
                                                "Interpolated between adjacent grid points.");
                        }
                }

                if (nearest == null) {
                        return new AssumptionTransparencyDTO.PricedInFrontierPoint(
                                        round2(margin),
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        "No valid grid point was available for this margin.");
                }

                return new AssumptionTransparencyDTO.PricedInFrontierPoint(
                                round2(margin),
                                round2(nearest.getRevenueGrowth()),
                                round2(nearest.getIntrinsicValue()),
                                calculateGapToMarket(nearest.getIntrinsicValue(), marketPrice),
                                calculateGapToMarketPct(nearest.getIntrinsicValue(), marketPrice),
                                false,
                                "Market price is outside the sampled growth range; nearest bounded point shown.");
        }

        private String buildPricedInHeadline(
                        List<AssumptionTransparencyDTO.PricedInFrontierPoint> frontier,
                        PricedInScenarioDefinition definition) {
                Optional<AssumptionTransparencyDTO.PricedInFrontierPoint> solved = frontier.stream()
                                .filter(point -> Boolean.TRUE.equals(point.getSolved())
                                                && point.getImpliedRevenueGrowth() != null
                                                && point.getOperatingMargin() != null)
                                .min(Comparator.comparing(point -> Math.abs(point.getImpliedRevenueGrowth())));
                if (solved.isPresent()) {
                        AssumptionTransparencyDTO.PricedInFrontierPoint point = solved.get();
                        return String.format(
                                        "At %.2f%% margin, today's market price needs about %.2f%% revenue growth under %s and %s.",
                                        point.getOperatingMargin(),
                                        point.getImpliedRevenueGrowth(),
                                        definition.riskLabel().toLowerCase(Locale.ROOT),
                                        definition.capitalEfficiencyLabel().toLowerCase(Locale.ROOT));
                }
                return "Current market price is outside the sampled market expectations range for this scenario.";
        }

        private List<Double> buildAxis(double center, double spread, double lower, double upper) {
                double[] offsets = new double[] { -spread, -spread / 2.0, 0.0, spread / 2.0, spread };
                List<Double> values = new ArrayList<>();
                for (double offset : offsets) {
                        double candidate = Math.max(lower, Math.min(upper, center + offset));
                        double rounded = round2(candidate);
                        if (values.stream().noneMatch(value -> Math.abs(value - rounded) < 0.001)) {
                                values.add(rounded);
                        }
                }
                Collections.sort(values);
                return values;
        }

        private List<PricedInScenarioDefinition> buildPricedInScenarioDefinitions(
                        double baseRisk,
                        double baseSalesToCapital) {
                List<PricedInScenarioDefinition> definitions = new ArrayList<>();
                Map<String, Double> riskValues = new LinkedHashMap<>();
                riskValues.put("low_risk", Math.max(1.0, baseRisk - 1.5));
                riskValues.put("base_risk", Math.max(1.0, baseRisk));
                riskValues.put("high_risk", Math.max(1.0, baseRisk + 1.5));

                double stcLower = baseSalesToCapital > 20.0 ? 25.0 : 0.25;
                double stcUpper = baseSalesToCapital > 20.0 ? 2000.0 : 20.0;
                Map<String, Double> efficiencyValues = new LinkedHashMap<>();
                efficiencyValues.put("efficient", Math.max(stcLower, Math.min(stcUpper, baseSalesToCapital * 1.25)));
                efficiencyValues.put("base_efficiency", Math.max(stcLower, Math.min(stcUpper, baseSalesToCapital)));
                efficiencyValues.put("inefficient", Math.max(stcLower, Math.min(stcUpper, baseSalesToCapital * 0.75)));

                for (Map.Entry<String, Double> risk : riskValues.entrySet()) {
                        for (Map.Entry<String, Double> efficiency : efficiencyValues.entrySet()) {
                                definitions.add(new PricedInScenarioDefinition(
                                                risk.getKey() + "__" + efficiency.getKey(),
                                                labelForRisk(risk.getKey()) + " / "
                                                                + labelForEfficiency(efficiency.getKey()),
                                                risk.getKey(),
                                                labelForRisk(risk.getKey()),
                                                efficiency.getKey(),
                                                labelForEfficiency(efficiency.getKey()),
                                                risk.getValue(),
                                                efficiency.getValue()));
                        }
                }
                return definitions;
        }

        private String labelForRisk(String key) {
                return switch (key) {
                        case "low_risk" -> "Low Risk";
                        case "high_risk" -> "High Risk";
                        default -> "Base Risk";
                };
        }

        private String labelForEfficiency(String key) {
                return switch (key) {
                        case "efficient" -> "Efficient Capital";
                        case "inefficient" -> "Inefficient Capital";
                        default -> "Base Efficiency";
                };
        }

        private Double calculateGapToMarket(Double intrinsicValue, double marketPrice) {
                if (intrinsicValue == null || !Double.isFinite(intrinsicValue)) {
                        return null;
                }
                return round2(intrinsicValue - marketPrice);
        }

        private Double calculateGapToMarketPct(Double intrinsicValue, double marketPrice) {
                if (intrinsicValue == null || !Double.isFinite(intrinsicValue) || marketPrice == 0.0) {
                        return null;
                }
                return round2(((intrinsicValue - marketPrice) / marketPrice) * 100.0);
        }

        private record PricedInScenarioDefinition(
                        String key,
                        String label,
                        String riskKey,
                        String riskLabel,
                        String capitalEfficiencyKey,
                        String capitalEfficiencyLabel,
                        double initialCostOfCapital,
                        double salesToCapital) {
        }

        private AssumptionTransparencyDTO.ImpliedMetric toImpliedMetric(
                        String key,
                        String label,
                        String unit,
                        Double modelValue,
                        Double impliedValue,
                        boolean solved) {
                Double gap = null;
                if (modelValue != null && impliedValue != null) {
                        gap = round2(impliedValue - modelValue);
                }
                return new AssumptionTransparencyDTO.ImpliedMetric(
                                key,
                                label,
                                unit,
                                modelValue,
                                impliedValue,
                                gap,
                                solved,
                                solved ? "Solved to current market price."
                                                : "Nearest bounded estimate in configured range.");
        }

        private SolveResult solveImpliedVariable(
                        FinancialDataInput baseInput,
                        double targetPrice,
                        InputMutator mutator,
                        double lower,
                        double upper,
                        String ticker,
                        RDResult rdResult,
                        OptionValueResultDTO optionValue,
                        LeaseResultDTO leaseResult,
                        ValuationTemplate template) {
                int steps = Math.max(8, valuationAssumptionProperties.getImpliedExpectationGridSteps());
                double tolerance = Math.max(0.01, valuationAssumptionProperties.getImpliedExpectationTolerance());
                int bisectionIterations = Math.max(8,
                                valuationAssumptionProperties.getImpliedExpectationBisectionIterations());

                double bestX = lower;
                double bestAbsDiff = Double.POSITIVE_INFINITY;
                Double prevX = null;
                Double prevDiff = null;
                Double bracketLow = null;
                Double bracketHigh = null;

                for (int i = 0; i <= steps; i++) {
                        double x = lower + (upper - lower) * (i / (double) steps);
                        Double estimate = evaluateImpliedPrice(baseInput, mutator, x, rdResult, optionValue,
                                        leaseResult, ticker,
                                        template);
                        if (estimate == null || estimate.isNaN() || estimate.isInfinite()) {
                                continue;
                        }
                        double diff = estimate - targetPrice;
                        double absDiff = Math.abs(diff);
                        if (absDiff < bestAbsDiff) {
                                bestAbsDiff = absDiff;
                                bestX = x;
                        }

                        if (prevDiff != null && prevX != null
                                        && (diff == 0.0 || (prevDiff > 0 && diff < 0) || (prevDiff < 0 && diff > 0))) {
                                bracketLow = prevX;
                                bracketHigh = x;
                                break;
                        }

                        prevX = x;
                        prevDiff = diff;
                }

                if (bestAbsDiff <= tolerance) {
                        return new SolveResult(bestX, true);
                }
                if (bracketLow == null || bracketHigh == null) {
                        return new SolveResult(bestX, false);
                }

                double lo = bracketLow;
                double hi = bracketHigh;
                double midpoint = bestX;
                for (int i = 0; i < bisectionIterations; i++) {
                        midpoint = (lo + hi) / 2.0;
                        Double estimate = evaluateImpliedPrice(baseInput, mutator, midpoint, rdResult, optionValue,
                                        leaseResult,
                                        ticker, template);
                        if (estimate == null || estimate.isNaN() || estimate.isInfinite()) {
                                break;
                        }
                        double diffMid = estimate - targetPrice;
                        double absDiff = Math.abs(diffMid);
                        if (absDiff < bestAbsDiff) {
                                bestAbsDiff = absDiff;
                                bestX = midpoint;
                        }
                        if (absDiff <= tolerance) {
                                return new SolveResult(midpoint, true);
                        }
                        Double loEstimate = evaluateImpliedPrice(baseInput, mutator, lo, rdResult, optionValue,
                                        leaseResult, ticker,
                                        template);
                        if (loEstimate == null || loEstimate.isNaN() || loEstimate.isInfinite()) {
                                break;
                        }
                        double diffLo = loEstimate - targetPrice;
                        if ((diffLo > 0 && diffMid < 0) || (diffLo < 0 && diffMid > 0)) {
                                hi = midpoint;
                        } else {
                                lo = midpoint;
                        }
                }
                return new SolveResult(bestX, bestAbsDiff <= tolerance);
        }

        private Double evaluateImpliedPrice(
                        FinancialDataInput baseInput,
                        InputMutator mutator,
                        double value,
                        RDResult rdResult,
                        OptionValueResultDTO optionValue,
                        LeaseResultDTO leaseResult,
                        String ticker,
                        ValuationTemplate template) {
                try {
                        FinancialDataInput scenario = new FinancialDataInput(baseInput);
                        mutator.apply(scenario, value);
                        return getEstimatedValuePerShareWithScenarioContext(scenario, rdResult, optionValue,
                                        leaseResult, ticker, template);
                } catch (RuntimeException ex) {
                        log.debug("Skipping implied valuation point for {} due to evaluation error: {}", ticker,
                                        ex.getMessage());
                        return null;
                }
        }

        private AssumptionTransparencyDTO.GrowthAnchor toGrowthAnchor(GrowthAnchorDTO anchor) {
                return new AssumptionTransparencyDTO.GrowthAnchor(
                                anchor.getEntity(),
                                anchor.getEntityDisplay(),
                                anchor.getRegion(),
                                anchor.getYear(),
                                anchor.getNumberOfFirms(),
                                anchor.getFundamentalGrowth(),
                                anchor.getHistoricalGrowthProxy(),
                                anchor.getExpectedGrowthProxy(),
                                anchor.getConfidenceScore(),
                                anchor.getP25(),
                                anchor.getP50(),
                                anchor.getP75(),
                                "Damodaran Historical Growth Rate in Earnings");
        }

        private Double normalizePercent(Double rawValue) {
                if (rawValue == null) {
                        return null;
                }
                double normalized = rawValue;
                if (Math.abs(normalized) <= 1.0) {
                        normalized *= 100.0;
                } else if (Math.abs(normalized) > 100.0) {
                        normalized /= 100.0;
                }
                return round2(normalized);
        }

        private Double normalizeMultiple(Double rawValue) {
                if (rawValue == null) {
                        return null;
                }
                return round2(rawValue);
        }

        private double restorePercentScale(Double referenceValue, double normalizedPercentValue) {
                if (referenceValue != null && Math.abs(referenceValue) > 100.0) {
                        return normalizedPercentValue * 100.0;
                }
                if (referenceValue != null && Math.abs(referenceValue) <= 1.0) {
                        return normalizedPercentValue / 100.0;
                }
                return normalizedPercentValue;
        }

        private Double firstFinite(Double[] values) {
                if (values == null) {
                        return null;
                }
                for (Double value : values) {
                        if (value != null && Double.isFinite(value)) {
                                return value;
                        }
                }
                return null;
        }

        private Double lastFinite(Double[] values) {
                if (values == null) {
                        return null;
                }
                for (int i = values.length - 1; i >= 0; i--) {
                        Double value = values[i];
                        if (value != null && Double.isFinite(value)) {
                                return value;
                        }
                }
                return null;
        }

        private Double firstNonNull(Double primary, Double fallback) {
                return primary != null ? primary : fallback;
        }

        private Double round2(Double value) {
                if (value == null) {
                        return null;
                }
                return Math.round(value * 100.0) / 100.0;
        }

        private String resolveTemplateSelectionReason(ValuationTemplate template) {
                if (template == null || template.getMetadata() == null) {
                        return "ValuationModel heuristic";
                }
                Object reason = template.getMetadata().get("templateSelectionReason");
                return reason instanceof String && !((String) reason).isBlank()
                                ? (String) reason
                                : "ValuationModel heuristic";
        }

        private boolean shouldForceThreeStageTemplate(
                        ValuationTemplate template,
                        FinancialDataInput overrides,
                        ValuationOutputDTO valuationOutputDTOCheck) {
                if (template == null || template.getGrowthPattern() == null) {
                        return false;
                }
                if (overrides != null && overrides.getGrowthPatternOverride() != null) {
                        return false;
                }
                if (template.getGrowthPattern() != GrowthPattern.STABLE
                                && template.getGrowthPattern() != GrowthPattern.TWO_STAGE) {
                        return false;
                }
                Double priceToValuePct = calculatePriceToValuePct(valuationOutputDTOCheck);
                return priceToValuePct != null
                                && (priceToValuePct > FORCE_THREE_STAGE_PREMIUM_THRESHOLD
                                                || priceToValuePct < FORCE_THREE_STAGE_DISCOUNT_THRESHOLD);
        }

        private Double calculatePriceToValuePct(ValuationOutputDTO valuationOutputDTO) {
                if (valuationOutputDTO == null || valuationOutputDTO.getCompanyDTO() == null) {
                        return null;
                }
                Double marketPrice = valuationOutputDTO.getCompanyDTO().getPrice();
                Double intrinsicValue = valuationOutputDTO.getCompanyDTO().getEstimatedValuePerShare();
                if (marketPrice == null || intrinsicValue == null
                                || !Double.isFinite(marketPrice) || !Double.isFinite(intrinsicValue)
                                || marketPrice <= 0 || intrinsicValue <= 0) {
                        return null;
                }
                return (marketPrice / intrinsicValue) * 100.0;
        }

        private String buildForcedThreeStageReason(Double priceToValuePct) {
                if (priceToValuePct == null) {
                        return "Forced THREE_STAGE due to price/value gap";
                }
                return String.format(
                                "Forced THREE_STAGE due to price/value gap (price-to-value %.2f%% outside %.0f%%-%.0f%% band)",
                                priceToValuePct,
                                FORCE_THREE_STAGE_DISCOUNT_THRESHOLD,
                                FORCE_THREE_STAGE_PREMIUM_THRESHOLD);
        }

        private boolean isForcedThreeStageReason(String templateSelectionReason) {
                return templateSelectionReason != null
                                && templateSelectionReason.startsWith("Forced THREE_STAGE due to price/value gap");
        }

        @FunctionalInterface
        private interface InputMutator {
                void apply(FinancialDataInput input, double value);
        }

        private record SolveResult(Double value, boolean solved) {
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

                log.info("[SCENARIO] Processing scenario: {}", scenarioName);
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

                log.info("  [RESULT] {} scenario result: ${} per share", scenarioName,
                                String.format("%.2f", intrinsicValue));
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
                return getEstimatedValuePerShare(input, rdResult, optionValueResultDTO, leaseResultDTO, ticker, null);
        }

        private double getEstimatedValuePerShare(FinancialDataInput input, RDResult rdResult,
                        OptionValueResultDTO optionValueResultDTO, LeaseResultDTO leaseResultDTO,
                        String ticker, ValuationTemplate template) {
                FinancialDTO financialDTO = valuationOutputService.calculateFinancialData(input, rdResult,
                                leaseResultDTO, ticker, template);
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
                double targetPreTaxOperatingMargin = isPositiveFinite(
                                companyDataDTO.getCompanyDriveDataDTO().getTargetPreTaxOperatingMargin())
                                                ? companyDataDTO.getCompanyDriveDataDTO()
                                                                .getTargetPreTaxOperatingMargin() * 100
                                                : operatingMarginNextYear;

                // Apply template adjustments if provided
                if (template != null && template.getNormalizedOperatingMargin() != null) {
                        operatingMarginNextYear = template.getNormalizedOperatingMargin();
                }

                financialDataInput.setRevenueNextYear(
                                companyDataDTO.getCompanyDriveDataDTO().getRevenueNextYear() * 100);
                financialDataInput.setOperatingMarginNextYear(operatingMarginNextYear);
                financialDataInput.setCompoundAnnualGrowth2_5(
                                companyDataDTO.getCompanyDriveDataDTO().getCompoundAnnualGrowth2_5() * 100);
                financialDataInput.setTargetPreTaxOperatingMargin(targetPreTaxOperatingMargin);
                financialDataInput.setConvergenceYearMargin(defaultConvergenceYear(template));
                financialDataInput.setSalesToCapitalYears1To5(
                                companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears1To5());
                financialDataInput.setSalesToCapitalYears6To10(
                                companyDataDTO.getCompanyDriveDataDTO().getSalesToCapitalYears6To10());
                financialDataInput.setRiskFreeRate(
                                companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate() * 100);
                financialDataInput.setInitialCostCapital(
                                companyDataDTO.getCompanyDriveDataDTO().getInitialCostCapital() * 100);

                // Initialize override assumptions
                initializeOverrideAssumptions(financialDataInput);

                return financialDataInput;
        }

        private double defaultConvergenceYear(ValuationTemplate template) {
                if (template == null || template.getGrowthPattern() == null) {
                        return 5.0;
                }
                return switch (template.getGrowthPattern()) {
                        case STABLE -> 3.0;
                        case TWO_STAGE, N_STAGE -> 5.0;
                        case THREE_STAGE -> 10.0;
                };
        }

        private boolean isPositiveFinite(Double value) {
                return value != null && Double.isFinite(value) && value > 0;
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
                        boolean enableSegments,
                        List<String> adjustedParameters) {

                if (!enableSegments) {
                        log.info("Multi-segment analysis disabled for {}", ticker);
                        return;
                }

                if (financialDataInput.getSegments() != null && financialDataInput.getSegments().getSegments() != null
                                && financialDataInput.getSegments().getSegments().size() > 1) {
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
                        boolean enableSegments,
                        List<String> adjustedParameters) {

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

                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments,
                                        adjustedParameters);

                        return valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, addStory, template);
                } else {
                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments,
                                        adjustedParameters);

                        return valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, addStory, template);
                }
        }

        /**
         * Adjusts near-term sales-to-capital ratio so it does not fall below the
         * current company ratio.
         * Long-run sales-to-capital remains available for mature-state reversion.
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

                        double inputSalesToCapital6To10 = financialDataInput.getSalesToCapitalYears6To10();

                        log.info("Sales-to-capital adjustment for {}: Years 1-5: input={}, adjusted={} | Years 6-10: input={}, retained={} | current={}",
                                        financialDataInput.getBasicInfoDataDTO().getTicker(),
                                        inputSalesToCapital1To5, adjustedSalesToCapital1To5,
                                        inputSalesToCapital6To10, inputSalesToCapital6To10,
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
        private List<String> applyUserOverrides(FinancialDataInput baseline, FinancialDataInput overrides) {
                log.info("Applying user overrides to baseline parameters...");
                int overrideCount = 0;
                Set<String> adjustedParameters = new LinkedHashSet<>();

                // Apply each override if present (non-null)
                if (overrides.getRevenueNextYear() != null) {
                        baseline.setRevenueNextYear(overrides.getRevenueNextYear());
                        log.info("   Override: revenueNextYear = {}", overrides.getRevenueNextYear());
                        overrideCount++;
                        adjustedParameters.add("revenueNextYear");
                }

                if (overrides.getOperatingMarginNextYear() != null) {
                        baseline.setOperatingMarginNextYear(overrides.getOperatingMarginNextYear());
                        log.info("   Override: operatingMarginNextYear = {}", overrides.getOperatingMarginNextYear());
                        if (overrides.getTargetPreTaxOperatingMargin() == null) {
                                baseline.setTargetPreTaxOperatingMargin(overrides.getOperatingMarginNextYear());
                                log.info("   Derived override: targetPreTaxOperatingMargin = {} (from operatingMarginNextYear)",
                                                overrides.getOperatingMarginNextYear());
                                adjustedParameters.add("targetPreTaxOperatingMargin");
                        }
                        overrideCount++;
                        adjustedParameters.add("operatingMarginNextYear");
                }

                if (overrides.getCompoundAnnualGrowth2_5() != null) {
                        baseline.setCompoundAnnualGrowth2_5(overrides.getCompoundAnnualGrowth2_5());
                        log.info("   Override: compoundAnnualGrowth2_5 = {}", overrides.getCompoundAnnualGrowth2_5());
                        overrideCount++;
                        adjustedParameters.add("compoundAnnualGrowth2_5");
                }

                if (overrides.getTargetPreTaxOperatingMargin() != null) {
                        baseline.setTargetPreTaxOperatingMargin(overrides.getTargetPreTaxOperatingMargin());
                        log.info("   Override: targetPreTaxOperatingMargin = {}",
                                        overrides.getTargetPreTaxOperatingMargin());
                        overrideCount++;
                        adjustedParameters.add("targetPreTaxOperatingMargin");
                }

                if (overrides.getConvergenceYearMargin() != null) {
                        baseline.setConvergenceYearMargin(overrides.getConvergenceYearMargin());
                        log.info("   Override: convergenceYearMargin = {}", overrides.getConvergenceYearMargin());
                        overrideCount++;
                }

                if (overrides.getGrowthPatternOverride() != null) {
                        baseline.setGrowthPatternOverride(overrides.getGrowthPatternOverride());
                        log.info("   Override: growthPatternOverride = {}", overrides.getGrowthPatternOverride());
                        overrideCount++;
                }

                if (overrides.getSalesToCapitalYears1To5() != null) {
                        baseline.setSalesToCapitalYears1To5(overrides.getSalesToCapitalYears1To5());
                        log.info("   Override: salesToCapitalYears1To5 = {}", overrides.getSalesToCapitalYears1To5());
                        overrideCount++;
                        adjustedParameters.add("salesToCapitalYears1To5");
                }

                if (overrides.getSalesToCapitalYears6To10() != null) {
                        baseline.setSalesToCapitalYears6To10(overrides.getSalesToCapitalYears6To10());
                        log.info("   Override: salesToCapitalYears6To10 = {}", overrides.getSalesToCapitalYears6To10());
                        overrideCount++;
                        adjustedParameters.add("salesToCapitalYears6To10");
                }

                if (overrides.getRiskFreeRate() != null) {
                        baseline.setRiskFreeRate(overrides.getRiskFreeRate());
                        log.info("   Override: riskFreeRate = {}", overrides.getRiskFreeRate());
                        overrideCount++;
                        adjustedParameters.add("riskFreeRate");
                }

                if (overrides.getInitialCostCapital() != null) {
                        baseline.setInitialCostCapital(overrides.getInitialCostCapital());
                        log.info("   Override: initialCostCapital = {}", overrides.getInitialCostCapital());
                        overrideCount++;
                        adjustedParameters.add("initialCostCapital");
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
                        adjustedParameters.add("segments");
                }

                // Copy sector overrides if present
                if (overrides.getSectorOverrides() != null && !overrides.getSectorOverrides().isEmpty()) {
                        baseline.setSectorOverrides(overrides.getSectorOverrides());
                        log.info("   Override: sectorOverrides = {} override(s)",
                                        overrides.getSectorOverrides().size());
                        overrideCount++;
                        adjustedParameters.add("sectorOverrides");
                }

                log.info("Applied {} user override(s) to baseline", overrideCount);
                return new ArrayList<>(adjustedParameters);
        }

        /**
         * Count non-null fields in FinancialDataInput for logging purposes.
         */
        private int countNonNullFields(FinancialDataInput input) {
                if (input == null) {
                        return 0;
                }
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
                if (input.getGrowthPatternOverride() != null)
                        count++;
                if (input.getSectorOverrides() != null && !input.getSectorOverrides().isEmpty())
                        count++;
                return count;
        }

}
