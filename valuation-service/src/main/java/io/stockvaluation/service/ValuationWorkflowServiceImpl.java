package io.stockvaluation.service;

import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.constant.RDResult;
import io.stockvaluation.dto.*;
import io.stockvaluation.dto.valuationoutput.AssumptionTransparencyDTO;
import io.stockvaluation.dto.valuationoutput.AccountingAndClaimsDTO;
import io.stockvaluation.dto.valuationoutput.CalibrationResultDTO;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.dto.valuationoutput.FinancialDTO;
import io.stockvaluation.dto.valuationoutput.SimulationResultsDTO;
import io.stockvaluation.enums.CashflowType;
import io.stockvaluation.enums.GrowthPattern;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.provider.SourceProvenance;
import io.stockvaluation.utils.MarketRegionResolver;
import io.stockvaluation.utils.SegmentParameterContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
        private static final double MIN_MARGIN_CONVERGENCE_YEAR = 1.0;
        private static final double MAX_MARGIN_CONVERGENCE_YEAR = 10.0;
        private static final double MIN_SALES_TO_CAPITAL = 0.05;
        private static final double MAX_SALES_TO_CAPITAL = 20.0;
        private static final double MIN_TERMINAL_ROIC = 0.01;
        private static final double MAX_TERMINAL_ROIC = 100.0;
        private static final String POLICY_AUTONOMOUS_RESEARCHED = "autonomous_researched";
        private static final String POLICY_USER_REFINED_SCENARIO = "user_refined_scenario";
        private static final String POLICY_EXPLICIT_SCENARIO = "explicit_scenario";

        private final CommonService commonService;
        private final OptionValueService optionValueService;
        private final ValuationOutputService valuationOutputService;
        private final ValuationTemplateService valuationTemplateService;
        private final ValuationAssumptionProperties valuationAssumptionProperties;
        private final GrowthAnchorService growthAnchorService;
        private final TickerSegmentDiscoveryService tickerSegmentDiscoveryService;

        @Override
        public ValuationOutputDTO getValuation(String ticker, FinancialDataInput financialDataInputOverrides) {
                try {
                        log.info("POST /{}/valuation (MINIMAL OVERRIDE PATTERN)", ticker);
                        log.info("   Received {} override parameter(s)",
                                        countNonNullFields(financialDataInputOverrides));

                        boolean enableDCFAnalysis = false;

                        return calculateValuation(
                                        ticker, financialDataInputOverrides, enableDCFAnalysis);

                } catch (RuntimeException e) {
                        log.error("Error in valuation output for ticker {}", ticker, e);
                        throw e;
                } finally {
                        SegmentParameterContext.clear();
                }
        }

        /**
         * Core valuation calculation logic for the deterministic POST endpoint.
         * 
         * Step Order (aligned for consistency):
         * 1. Fetch company baseline data from the selected financial provider
         * 2. Determine valuation template and primary model
         * 3. Initialize financial data with baseline values
         * 4. Apply user overrides (if any)
         * 5. Adjust sales-to-capital ratio
         * 6. Run initial valuation check
         * 7. Apply calibration and ML adjustments (includes segment analysis)
         * 8. Single calibration to market price
         * 9. Copy selected model metadata to output
         * 10. Add assumption transparency
         * 11. Add growth anchor diagnostics
         * 
         * @param ticker            Stock ticker symbol
         * @param overrides         Optional user overrides
         * @param enableDCFAnalysis Whether ML-based DCF analysis is enabled
         * @return ValuationOutputDTO with consistent results
         */
        private ValuationOutputDTO calculateValuation(
                        String ticker,
                        FinancialDataInput overrides,
                        boolean enableDCFAnalysis) {

                // Step 1: Fetch company baseline data from the selected financial provider
                CompanyDataDTO companyDataDTO = requiresResearchedSourcePolicy(overrides)
                                ? commonService.getCompanyDataFromProvider(ticker, overrides)
                                : commonService.getCompanyDataFromProvider(ticker);
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
                applyResearchedSegmentDiscovery(ticker, financialDataInput, companyDataDTO, adjustedParameters);

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
                adjustSalesToCapitalRatio(financialDataInput, adjustedParameters);

                // Step 5.5: Start intrinsic pricing fetch in parallel (if requested)
                // This runs concurrently with Steps 6-10, saving significant time

                // Step 6: Run initial valuation check
                ValuationOutputDTO valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                ticker, financialDataInput, template);

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
                        applyResearchedSegmentDiscovery(ticker, financialDataInput, companyDataDTO, adjustedParameters);
                        adjustSalesToCapitalRatio(financialDataInput, adjustedParameters);
                        valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                        ticker, financialDataInput, template);
                }

                // Step 7: Apply calibration fallback if needed
                // Note: Segment analysis is performed INSIDE applyCalibrationAndMLAdjustments
                // after any calibration adjustments, ensuring consistent parameter processing
                ValuationOutputDTO valuationOutputDTO = applyCalibrationAndMLAdjustments(
                                ticker, financialDataInput, companyDataDTO, valuationOutputDTOCheck, enableDCFAnalysis,
                                template, true, adjustedParameters);

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
                        applyResearchedSegmentDiscovery(ticker, financialDataInput, companyDataDTO, adjustedParameters);
                        adjustSalesToCapitalRatio(financialDataInput, adjustedParameters);
                        valuationOutputDTOCheck = valuationOutputService.getValuationOutput(
                                        ticker, financialDataInput, template);
                        valuationOutputDTO = applyCalibrationAndMLAdjustments(
                                        ticker, financialDataInput, companyDataDTO, valuationOutputDTOCheck,
                                        enableDCFAnalysis, template, true, adjustedParameters);
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

                // Step 9: Set model metadata from the model resolved in Step 2.
                assignModelSelectionMetadata(valuationOutputDTO, ticker, modelDecision);
                assignTemplateMetadata(valuationOutputDTO, template, templateSelectionReason);
                if (companyDataDTO.getBasicInfoDataDTO() != null) {
                        valuationOutputDTO.setIndustryUs(companyDataDTO.getBasicInfoDataDTO().getIndustryUs());
                        valuationOutputDTO.setIndustryGlobal(companyDataDTO.getBasicInfoDataDTO().getIndustryGlobal());
                }

                // Step 10: Add assumption transparency (including market-implied expectations)
                valuationOutputDTO.setAssumptionTransparency(buildAssumptionTransparency(
                                ticker,
                                financialDataInput,
                                valuationOutputDTO,
                                template,
                                templateSelectionReason,
                                adjustedParameters));
                valuationOutputDTO.setSourceQualityGate(buildSourceQualityGate(
                                financialDataInput,
                                valuationOutputDTO.getAssumptionTransparency().getSourceProvenance()));

                // Step 11: Add Growth Anchor Diagnostics
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
                        String templateSelectionReason,
                        List<String> adjustedParameters) {
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
                dto.setRequestPolicyMode(resolveRequestPolicyMode(financialDataInput));
                dto.setProjectionYears(template != null ? template.getProjectionYears() : null);
                dto.setTemplateSelectionReason(templateSelectionReason);
                dto.setSourceProvenance(buildSourceProvenance(financialDataInput));
                dto.setSegmentCount(financialDataInput.getSegments() != null
                                && financialDataInput.getSegments().getSegments() != null
                                                ? financialDataInput.getSegments().getSegments().size()
                                                : 0);
                dto.setSegmentAware(dto.getSegmentCount() != null && dto.getSegmentCount() > 1);
                dto.setBaselineQuality("single_industry_fallback");
                dto.setSegmentCoveragePct(0.0);
                dto.setMappedIndustries(new ArrayList<>());
                dto.setWeightedBaselineAssumptions(new LinkedHashMap<>());

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

                applyBaselineConstructionTransparency(
                                dto,
                                effectiveRevenueGrowth,
                                effectiveOperatingMarginNextYear,
                                effectiveTargetOperatingMargin,
                                effectiveSalesToCapitalYears1To5,
                                effectiveSalesToCapitalYears6To10,
                                initialCostOfCapital);
                applyBaselineUseTransparency(dto, financialDataInput, adjustedParameters);

                dto.setOperatingAssumptions(new AssumptionTransparencyDTO.OperatingAssumptions(
                                normalizePercent(effectiveRevenueGrowth),
                                normalizePercent(effectiveOperatingMarginNextYear),
                                normalizePercent(effectiveTargetOperatingMargin),
                                round2(effectiveConvergenceYear),
                                normalizeMultiple(effectiveSalesToCapitalYears1To5),
                                normalizeMultiple(effectiveSalesToCapitalYears6To10),
                                dto.isSegmentAware() ? "Segment-weighted mechanical baseline"
                                                : "Valuation input baseline/override",
                                dto.getTargetOperatingMarginSource() != null
                                                ? dto.getTargetOperatingMarginSource()
                                                : "Valuation input baseline/override",
                                dto.isSegmentAware() ? "Segment-weighted mechanical baseline"
                                                : "Valuation input baseline/override",
                                null,
                                null,
                                null));
                dto.setAccountingAndClaims(buildAccountingAndClaims(financialDataInput, valuationOutputDTO));

                List<String> notes = new ArrayList<>();
                notes.add("Rates are shown in percent.");
                notes.add("Sales-to-capital is shown as x multiple.");
                notes.add("Single-lever market expectation checks solve one variable at a time while others stay fixed.");
                if (isForcedThreeStageReason(templateSelectionReason)) {
                        notes.add("Projection was upgraded to THREE_STAGE because market price and intrinsic value diverged materially in the first-pass baseline.");
                }
                if (adjustedParameters != null && adjustedParameters.contains("negativeValueCalibrationSkipped")) {
                        notes.add("Negative-value calibration was skipped because request_policy.mode preserves explicit user scenario assumptions.");
                }
                if (adjustedParameters != null && adjustedParameters.contains("negativeValueMarketCalibrationDiagnosticOnly")) {
                        notes.add("Negative-value market calibration stayed diagnostic and did not change researched baseline assumptions.");
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

        private AccountingAndClaimsDTO buildAccountingAndClaims(
                        FinancialDataInput financialDataInput,
                        ValuationOutputDTO valuationOutputDTO) {
                AccountingAndClaimsDTO dto = new AccountingAndClaimsDTO();
                SourceProvenance provenance = buildSourceProvenance(financialDataInput);
                FinancialDataDTO financial = financialDataInput != null ? financialDataInput.getFinancialDataDTO()
                                : null;
                CompanyDTO company = valuationOutputDTO != null ? valuationOutputDTO.getCompanyDTO() : null;
                FinancialDTO outputFinancial = valuationOutputDTO != null ? valuationOutputDTO.getFinancialDTO() : null;
                Map<String, Double> rdHistory = financial != null ? financial.getResearchAndDevelopmentMap() : null;

                boolean sourceReturned = provenance != null
                                && "retrieved".equals(provenance.getRetrievalStatus())
                                && !SourceProvenance.YAHOO_NORMALIZED.equals(provenance.getSourceClass());
                boolean multiYearRdHistory = hasMultiYearRdHistory(rdHistory);
                boolean rdScenarioApplied = financialDataInput != null
                                && Boolean.TRUE.equals(financialDataInput.getIsExpensesCapitalize());
                boolean rdAmortizationPolicyAvailable = validRdAmortizationPolicy(financialDataInput);
                String rdStatus;
                String rdTreatment;
                String rdReason;
                if (rdScenarioApplied && multiYearRdHistory && sourceReturned && rdAmortizationPolicyAvailable) {
                        rdStatus = "governed_scenario_supported";
                        rdTreatment = "governed_scenario_effective";
                        rdReason = "R&D capitalization was enabled in a governed researched/scenario path with multi-year R&D history, amortization policy, and source provenance.";
                } else if (multiYearRdHistory && sourceReturned && rdAmortizationPolicyAvailable) {
                        rdStatus = "governed_scenario_supported";
                        rdTreatment = "scenario_only";
                        rdReason = "Multi-year R&D history, amortization policy, and source provenance are available; R&D capitalization requires the automatic researched path or an explicit governed scenario payload.";
                } else if (hasAnyPositiveValue(rdHistory)) {
                        rdStatus = "source_required";
                        rdTreatment = "report_only";
                        rdReason = "R&D was present, but multi-year history, amortization policy, or source provenance were insufficient for governed modeling.";
                } else {
                        rdStatus = "missing";
                        rdTreatment = "report_only";
                        rdReason = "No usable R&D history was returned.";
                }
                AccountingAndClaimsDTO.Topic rd = topic(
                                rdStatus,
                                rdTreatment,
                                provenance,
                                rdReason,
                                null);
                rd.getReportedValues().put("multiYearHistory", multiYearRdHistory);
                rd.getReportedValues().put("historyYears", positiveValueCount(rdHistory));
                rd.getReportedValues().put("amortizationPolicy", rdAmortizationPolicy(financialDataInput));
                dto.setRdCapitalization(rd);

                Double sbc = firstNonNull(financial != null ? financial.getStockBasedCompensationTTM() : null,
                                financial != null ? financial.getStockBasedCompensationLTM() : null);
                AccountingAndClaimsDTO.Topic sbcDilution = topic(
                                "blocked_report_only",
                                "report_only",
                                provenance,
                                "SBC and dilution diagnostics are report-only in Phase 5; they do not change service assumptions.",
                                sbc);
                putDiagnostic(sbcDilution, "sbcPercentRevenue",
                                percentOf(sbc, financial != null ? financial.getRevenueTTM() : null));
                putDiagnostic(sbcDilution, "sbcPercentOperatingIncome",
                                percentOf(sbc, financial != null ? financial.getOperatingIncomeTTM() : null));
                putDiagnostic(sbcDilution, "sbcPercentFreeCashFlow",
                                percentOf(sbc, outputFinancial != null ? firstFinite(outputFinancial.getFcff()) : null));
                putDiagnostic(sbcDilution, "dilutedShareCountTrendPct",
                                percentChange(
                                                financial != null ? financial.getPriorDilutedSharesOutstanding() : null,
                                                financial != null ? financial.getDilutedSharesOutstanding() : null));
                putDiagnostic(sbcDilution, "dilutedShareConsistencyStatus",
                                dilutedShareConsistencyStatus(financial));
                dto.setSbcDilution(sbcDilution);

                boolean leaseScenario = financialDataInput != null
                                && Boolean.TRUE.equals(financialDataInput.getHasOperatingLease());
                boolean leaseScheduleAvailable = hasLeaseSchedule(financialDataInput);
                LeaseResultDTO leaseResult = commonService.calculateOperatingLeaseConverter();
                AccountingAndClaimsDTO.Topic leases = topic(
                                leaseScenario ? "source_required" : "zero_by_default",
                                "report_only",
                                provenance,
                                leaseScenario
                                                ? "Lease conversion is report-only in Phase 5; R&D capitalization is the only governed accounting model path."
                                                : "No operating lease schedule was supplied; the service used a zero default rather than proof of no lease adjustment.",
                                0.0);
                leases.getReportedValues().put("scheduleAvailable", leaseScheduleAvailable);
                leases.getReportedValues().put("leaseExpenseCurrentYear",
                                financialDataInput != null ? financialDataInput.getLeaseExpenseCurrentYear() : null);
                leases.getReportedValues().put("commitmentYears",
                                financialDataInput != null && financialDataInput.getLeaseCommitmentsYears1To5() != null
                                                ? financialDataInput.getLeaseCommitmentsYears1To5().length
                                                : 0);
                leases.getReportedValues().put("futureCommitment",
                                financialDataInput != null ? financialDataInput.getLeaseCommitmentAfterYear5() : null);
                leases.getReportedValues().put("adjustmentToOperatingEarnings",
                                leaseResult != null ? leaseResult.getAdjustmentToOperatingEarnings() : null);
                leases.getReportedValues().put("adjustmentToTotalDebt",
                                leaseResult != null ? leaseResult.getAdjustmentToTotalDebt() : null);
                dto.setLeases(leases);

                boolean optionsInput = financialDataInput != null
                                && Boolean.TRUE.equals(financialDataInput.getHasEmployeeOptions())
                                && positive(financialDataInput.getNumberOfOptions());
                Double optionValue = company != null ? company.getValueOfOptions() : null;
                dto.setOptionsWarrants(topic(
                                optionsInput ? "returned" : "zero_by_default",
                                "service_calculated_when_inputs_available",
                                provenance,
                                optionsInput
                                                ? "Employee option value was calculated from service inputs; direct claim value overrides remain blocked."
                                                : "No employee option or warrant input was supplied; the service used a zero default.",
                                firstNonNull(optionValue, 0.0)));

                dto.setNolTax(topic(
                                "source_required",
                                "report_only",
                                provenance,
                                "NOL/tax normalization is report-only in Phase 5; R&D capitalization is the only governed accounting model path.",
                                null));

                dto.setCash(topic(
                                statusForClaimValue("cash_and_marketable_securities",
                                                firstNonNull(company != null ? company.getCash() : null,
                                                                financial != null ? financial.getCashAndMarkablTTM()
                                                                                : null),
                                                provenance),
                                "service_returned",
                                provenance,
                                "Cash is service-returned data quality context, not a free-form override.",
                                firstNonNull(company != null ? company.getCash() : null,
                                                financial != null ? financial.getCashAndMarkablTTM() : null)));
                addClaimSourceStatus(dto.getCash(), "cash_and_marketable_securities", provenance, null);

                dto.setDebt(topic(
                                statusForClaimValue("debt",
                                                firstNonNull(company != null ? company.getDebt() : null,
                                                                financial != null ? financial.getBookValueDebtTTM()
                                                                                : null),
                                                provenance),
                                "service_returned",
                                provenance,
                                "Debt is service-returned data quality context, not a free-form override.",
                                firstNonNull(company != null ? company.getDebt() : null,
                                                financial != null ? financial.getBookValueDebtTTM() : null)));
                addClaimSourceStatus(dto.getDebt(), "debt", provenance, null);

                dto.setShareCount(topic(
                                statusForClaimValue("shares_outstanding",
                                                firstNonNull(company != null ? company.getNumberOfShares() : null,
                                                                financial != null ? financial.getNoOfShareOutstanding()
                                                                                : null),
                                                provenance),
                                "service_returned",
                                provenance,
                                "Share count is service-returned data quality context, not a free-form override.",
                                firstNonNull(company != null ? company.getNumberOfShares() : null,
                                                financial != null ? financial.getNoOfShareOutstanding() : null)));
                addClaimSourceStatus(dto.getShareCount(), "shares_outstanding", provenance,
                                shareCountBasis(financial, provenance));

                dto.setEffectiveAccountingDecisions(effectiveAccountingDecisions(dto));
                return dto;
        }

        private AccountingAndClaimsDTO.Topic topic(
                        String status,
                        String modelTreatment,
                        SourceProvenance provenance,
                        String reason,
                        Double value) {
                AccountingAndClaimsDTO.Topic topic = new AccountingAndClaimsDTO.Topic();
                topic.setStatus(status);
                topic.setModelTreatment(modelTreatment);
                if (provenance != null) {
                        topic.setSourceClass(provenance.getSourceClass());
                        topic.setProvider(provenance.getProvider());
                        topic.setSourceDate(provenance.getSourceDate());
                        topic.setRetrievalStatus(provenance.getRetrievalStatus());
                        topic.setSourcePolicyStatus(provenance.getSourcePolicyStatus());
                }
                topic.setReason(reason);
                topic.setValue(value);
                topic.setDiagnostics(new LinkedHashMap<>());
                topic.setReportedValues(new LinkedHashMap<>());
                return topic;
        }

        private void putDiagnostic(AccountingAndClaimsDTO.Topic topic, String key, Object value) {
                topic.getDiagnostics().put(key, value);
                topic.getReportedValues().put(key, value);
        }

        private Map<String, Object> rdAmortizationPolicy(FinancialDataInput input) {
                Map<String, Object> policy = new LinkedHashMap<>();
                policy.put("method", input != null ? input.getRdAmortizationMethod() : null);
                policy.put("amortizationPeriodYears", input != null ? input.getRdAmortizationPeriodYears() : null);
                policy.put("serviceDefault", input == null || input.getRdAmortizationMethod() == null
                                || input.getRdAmortizationPeriodYears() == null);
                return policy;
        }

        private Double percentOf(Double numerator, Double denominator) {
                if (!positive(numerator) || !positive(denominator)) {
                        return null;
                }
                return round2((numerator / denominator) * 100.0);
        }

        private Double percentChange(Double prior, Double current) {
                if (!positive(prior) || current == null || !Double.isFinite(current)) {
                        return null;
                }
                return round2(((current - prior) / prior) * 100.0);
        }

        private String dilutedShareConsistencyStatus(FinancialDataDTO financial) {
                if (financial == null || financial.getDilutedSharesOutstanding() == null) {
                        return "missing_diluted_share_count";
                }
                Double basic = financial.getBasicSharesOutstanding();
                if (basic == null) {
                        return "basic_share_count_missing";
                }
                if (financial.getDilutedSharesOutstanding() + 0.0001 < basic) {
                        return "diluted_share_count_below_basic_conflict";
                }
                if (financial.getDilutedSharesOutstanding() > basic) {
                        return "diluted_share_count_above_basic";
                }
                return "basic_and_diluted_share_counts_equal";
        }

        private LeaseResultDTO leaseResultForInput(FinancialDataInput input) {
                return commonService.calculateOperatingLeaseConverter();
        }

        private boolean hasLeaseSchedule(FinancialDataInput input) {
                if (input == null || !Boolean.TRUE.equals(input.getHasOperatingLease())) {
                        return false;
                }
                if (!positive(input.getLeaseExpenseCurrentYear())) {
                        return false;
                }
                Double[] commitments = input.getLeaseCommitmentsYears1To5();
                return commitments != null
                                && commitments.length > 0
                                && commitments.length <= 5
                                && (input.getLeaseCommitmentAfterYear5() == null
                                                || input.getLeaseCommitmentAfterYear5() >= 0.0)
                                && Arrays.stream(commitments)
                                                .allMatch(value -> value != null
                                                                && Double.isFinite(value)
                                                                && value >= 0.0)
                                && Arrays.stream(commitments)
                                                .filter(Objects::nonNull)
                                                .anyMatch(this::positive);
        }

        private String statusForClaimValue(String field, Double value, SourceProvenance provenance) {
                if (value == null) {
                        return "missing";
                }
                SourceProvenance.DataQualityWarning warning = dataQualityWarningForField(provenance, field);
                if (warning != null) {
                        return "conflict";
                }
                if ("stale_source_date".equals(provenance != null ? provenance.getSourcePolicyStatus() : null)) {
                        return "stale";
                }
                if (provenance == null
                                || provenance.getSourceDate() == null
                                || provenance.getSourceDate().isBlank()
                                || "retrieved_missing_period".equals(provenance.getRetrievalStatus())
                                || "missing_source_date".equals(provenance.getSourcePolicyStatus())) {
                        return "source_required";
                }
                if (SourceProvenance.PRIMARY_FILING.equals(provenance.getSourceClass())
                                && "primary_filing_used".equals(provenance.getSourcePolicyStatus())) {
                        return "reconciled";
                }
                return "returned";
        }

        private void addClaimSourceStatus(
                        AccountingAndClaimsDTO.Topic topic,
                        String field,
                        SourceProvenance provenance,
                        String shareCountBasis) {
                if (topic == null) {
                        return;
                }
                SourceProvenance.DataQualityWarning warning = dataQualityWarningForField(provenance, field);
                topic.getReportedValues().put("reconciliationStatus",
                                warning != null ? warning.getStatus() : reconciliationStatus(provenance));
                topic.getReportedValues().put("sourceBasis", sourceBasis(provenance));
                if (shareCountBasis != null) {
                        topic.getReportedValues().put("shareCountBasis", shareCountBasis);
                }
                if (warning != null) {
                        topic.getReportedValues().put("dataQualityWarning", dataQualityWarningMap(warning));
                }
        }

        private SourceProvenance.DataQualityWarning dataQualityWarningForField(
                        SourceProvenance provenance,
                        String field) {
                if (provenance == null || provenance.getDataQualityWarnings() == null) {
                        return null;
                }
                return provenance.getDataQualityWarnings().stream()
                                .filter(Objects::nonNull)
                                .filter(warning -> field.equals(warning.getField())
                                                || canonicalFieldAlias(field).equals(warning.getField()))
                                .findFirst()
                                .orElse(null);
        }

        private String canonicalFieldAlias(String field) {
                if ("cash_and_marketable_securities".equals(field)) {
                        return "cash_and_short_term_investments";
                }
                if ("debt".equals(field)) {
                        return "total_debt";
                }
                return field;
        }

        private String reconciliationStatus(SourceProvenance provenance) {
                if (provenance == null) {
                        return "source_required";
                }
                if (SourceProvenance.PRIMARY_FILING.equals(provenance.getSourceClass())
                                && "primary_filing_used".equals(provenance.getSourcePolicyStatus())) {
                        return "reconciled";
                }
                if ("stale_source_date".equals(provenance.getSourcePolicyStatus())) {
                        return "stale_source_date";
                }
                return firstNonBlank(provenance.getCrossCheckStatus(), "not_reconciled");
        }

        private String sourceBasis(SourceProvenance provenance) {
                if (provenance == null) {
                        return "missing";
                }
                if (SourceProvenance.PRIMARY_FILING.equals(provenance.getSourceClass())) {
                        return "filing_derived";
                }
                return firstNonBlank(provenance.getSourceClass(), "unknown");
        }

        private String shareCountBasis(FinancialDataDTO financial, SourceProvenance provenance) {
                if (financial != null && financial.getDilutedSharesOutstanding() != null) {
                        return "diluted";
                }
                if (financial != null && financial.getBasicSharesOutstanding() != null) {
                        return "basic";
                }
                if (SourceProvenance.PRIMARY_FILING.equals(provenance != null ? provenance.getSourceClass() : null)) {
                        return "filing_derived";
                }
                if (SourceProvenance.YAHOO_NORMALIZED.equals(provenance != null ? provenance.getSourceClass() : null)) {
                        return "yahoo_normalized";
                }
                return financial != null && financial.getNoOfShareOutstanding() != null ? "reported" : "missing";
        }

        private Map<String, Object> dataQualityWarningMap(SourceProvenance.DataQualityWarning warning) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("field", warning.getField());
                values.put("status", warning.getStatus());
                values.put("normalizedValue", warning.getNormalizedValue());
                values.put("filingValue", warning.getFilingValue());
                values.put("differencePct", warning.getDifferencePct());
                values.put("thresholdPct", warning.getThresholdPct());
                values.put("sourceClass", warning.getSourceClass());
                values.put("sourceDate", warning.getSourceDate());
                return values;
        }

        private String firstNonBlank(String primary, String fallback) {
                return primary != null && !primary.isBlank() ? primary : fallback;
        }

        private boolean hasMultiYearRdHistory(Map<String, Double> rdHistory) {
                return positiveValueCount(rdHistory) >= 3;
        }

        private int positiveValueCount(Map<String, Double> values) {
                if (values == null) {
                        return 0;
                }
                return (int) values.values().stream()
                                .filter(Objects::nonNull)
                                .filter(this::positive)
                                .count();
        }

        private boolean hasAnyPositiveValue(Map<String, Double> values) {
                return positiveValueCount(values) > 0;
        }

        private boolean positive(Double value) {
                return value != null && Double.isFinite(value) && value > 0.0;
        }

        private List<AccountingAndClaimsDTO.Decision> effectiveAccountingDecisions(AccountingAndClaimsDTO dto) {
                List<AccountingAndClaimsDTO.Decision> decisions = new ArrayList<>();
                decisions.add(accountingDecision("rd_capitalization", dto.getRdCapitalization(), "isExpensesCapitalize"));
                decisions.add(accountingDecision("sbc_dilution", dto.getSbcDilution(), "stockBasedCompensationTTM/dilutedSharesOutstanding"));
                decisions.add(accountingDecision("leases", dto.getLeases(), "hasOperatingLease"));
                decisions.add(accountingDecision("options_warrants", dto.getOptionsWarrants(), "hasEmployeeOptions"));
                decisions.add(accountingDecision("nol_tax", dto.getNolTax(), "overrideAssumptionNOL/overrideAssumptionTaxRate"));
                decisions.add(accountingDecision("cash", dto.getCash(), "companyDTO.cash"));
                decisions.add(accountingDecision("debt", dto.getDebt(), "companyDTO.debt"));
                decisions.add(accountingDecision("share_count", dto.getShareCount(), "companyDTO.numberOfShares"));
                return decisions;
        }

        private AccountingAndClaimsDTO.Decision accountingDecision(
                        String topic,
                        AccountingAndClaimsDTO.Topic status,
                        String field) {
                return new AccountingAndClaimsDTO.Decision(
                                topic,
                                status != null ? status.getStatus() : "missing",
                                "effective",
                                field,
                                status != null ? status.getReason() : "Accounting status was missing.");
        }

        private void applyBaselineConstructionTransparency(
                        AssumptionTransparencyDTO dto,
                        Double revenueGrowth,
                        Double operatingMarginNextYear,
                        Double targetOperatingMargin,
                        Double salesToCapitalYears1To5,
                        Double salesToCapitalYears6To10,
                        Double initialCostOfCapital) {
                SegmentWeightedParameters segmentParams = SegmentParameterContext.getParameters();
                if (segmentParams == null) {
                        return;
                }
                if (!segmentParams.hasValidParameters()) {
                        if (segmentParams.getBaselineQuality() != null
                                        && segmentParams.getBaselineQuality().startsWith("segment_")) {
                                dto.setBaselineQuality(segmentParams.getBaselineQuality());
                                dto.setSegmentAware(false);
                                dto.setSegmentCount(segmentParams.getSegmentCount());
                                dto.setSegmentCoveragePct(segmentParams.getSegmentCoveragePct());
                        }
                        return;
                }

                dto.setBaselineQuality("segment_weighted_baseline");
                dto.setSegmentAware(true);
                dto.setSegmentCount(segmentParams.getSegmentCount());
                dto.setSegmentCoveragePct(segmentParams.getSegmentCoveragePct());
                dto.setMappedIndustries(mappedIndustries(segmentParams));

                Map<String, Object> weighted = new LinkedHashMap<>();
                weighted.put("revenueGrowthRateYears2To5", round2(normalizePercent(revenueGrowth)));
                weighted.put("operatingMarginNextYear", round2(normalizePercent(operatingMarginNextYear)));
                weighted.put("targetOperatingMargin", round2(normalizePercent(targetOperatingMargin)));
                weighted.put("salesToCapitalYears1To5", round2(normalizeMultiple(salesToCapitalYears1To5)));
                weighted.put("salesToCapitalYears6To10", round2(normalizeMultiple(salesToCapitalYears6To10)));
                weighted.put("initialCostOfCapital", round2(normalizePercent(initialCostOfCapital)));
                dto.setWeightedBaselineAssumptions(weighted);
        }

        private SourceProvenance buildSourceProvenance(FinancialDataInput financialDataInput) {
                if (financialDataInput == null
                                || financialDataInput.getFinancialDataDTO() == null
                                || financialDataInput.getFinancialDataDTO().getSourceProvenance() == null) {
                        return null;
                }
                SourceProvenance source = financialDataInput.getFinancialDataDTO().getSourceProvenance();
                SourceProvenance provenance = new SourceProvenance(
                                source.getSourceClass(),
                                source.getProvider(),
                                source.getSourceDate(),
                                source.getPeriodEnd(),
                                source.getRetrievalStatus(),
                                source.getCrossCheckStatus(),
                                source.getSourcePolicyStatus(),
                                source.getWarnings() == null ? new ArrayList<>() : new ArrayList<>(source.getWarnings()));
                provenance.setDataQualityWarnings(source.getDataQualityWarnings() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(source.getDataQualityWarnings()));
                List<String> warnings = provenance.getWarnings() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(provenance.getWarnings());

                boolean yahooNormalized = SourceProvenance.YAHOO_NORMALIZED.equals(provenance.getSourceClass());
                boolean researchedMode = Boolean.TRUE.equals(financialDataInput.getResearchedBaselineMode())
                                || POLICY_AUTONOMOUS_RESEARCHED.equals(resolveRequestPolicyMode(financialDataInput));
                String country = Optional.ofNullable(financialDataInput.getBasicInfoDataDTO())
                                .map(BasicInfoDataDTO::getCountryOfIncorporation)
                                .orElse("");
                boolean usCompany = "United States".equalsIgnoreCase(country);

                if (yahooNormalized && researchedMode && usCompany) {
                        if (!isSecYahooFallbackStatus(provenance.getSourcePolicyStatus())) {
                                provenance.setSourcePolicyStatus("sec_http_error_yahoo_fallback");
                        }
                        if (provenance.getCrossCheckStatus() == null
                                        || provenance.getCrossCheckStatus().isBlank()
                                        || "not_checked_by_service".equals(provenance.getCrossCheckStatus())
                                        || "not_checked".equals(provenance.getCrossCheckStatus())) {
                                provenance.setCrossCheckStatus("company_report_check_pending");
                        }
                        warnings.add("US researched valuation is using Yahoo-normalized financials because primary filing data was unavailable or not returned.");
                } else if (yahooNormalized && researchedMode && !usCompany) {
                        provenance.setSourcePolicyStatus("primary_adapter_not_supported_yahoo_normalized");
                        if (provenance.getCrossCheckStatus() == null
                                        || provenance.getCrossCheckStatus().isBlank()
                                        || "not_checked_by_service".equals(provenance.getCrossCheckStatus())
                                        || "not_checked".equals(provenance.getCrossCheckStatus())) {
                                provenance.setCrossCheckStatus("company_report_check_pending");
                        } else if ("company_report_checked".equals(provenance.getCrossCheckStatus())) {
                                provenance.setCrossCheckStatus("company_report_cross_checked");
                        }
                        warnings.add("Non-US researched valuation may use Yahoo-normalized financials when company-report cross-check status is explicit.");
                } else if (provenance.getSourcePolicyStatus() == null || provenance.getSourcePolicyStatus().isBlank()) {
                        provenance.setSourcePolicyStatus("source_provenance_returned");
                }
                provenance.setWarnings(dedupeStrings(warnings));
                return provenance;
        }

        private SourceQualityGateDTO buildSourceQualityGate(
                        FinancialDataInput financialDataInput,
                        SourceProvenance provenance) {
                String reason = provenance != null ? provenance.getSourcePolicyStatus() : null;
                boolean researchedMode = financialDataInput != null
                                && (Boolean.TRUE.equals(financialDataInput.getResearchedBaselineMode())
                                                || POLICY_AUTONOMOUS_RESEARCHED.equals(resolveRequestPolicyMode(financialDataInput)));
                if (researchedMode && isSecYahooFallbackStatus(reason)) {
                        return new SourceQualityGateDTO(
                                        "requires_user_decision",
                                        reason,
                                        true,
                                        true,
                                        true,
                                        List.of("continue_with_fallback", "retry_primary_source", "stop"));
                }
                if (researchedMode && "primary_adapter_not_supported_yahoo_normalized".equals(reason)) {
                        return new SourceQualityGateDTO(
                                        "requires_user_decision",
                                        reason,
                                        false,
                                        true,
                                        true,
                                        List.of("continue_with_fallback", "stop"));
                }
                return new SourceQualityGateDTO(
                                "not_required",
                                reason,
                                false,
                                SourceProvenance.YAHOO_NORMALIZED.equals(provenance != null ? provenance.getSourceClass() : null),
                                false,
                                List.of());
        }

        private boolean isSecYahooFallbackStatus(String status) {
                return status != null
                                && status.startsWith("sec_")
                                && status.endsWith("_yahoo_fallback");
        }

        private boolean requiresResearchedSourcePolicy(FinancialDataInput overrides) {
                if (overrides == null) {
                        return false;
                }
                String policy = resolveRequestPolicyMode(overrides);
                return Boolean.TRUE.equals(overrides.getResearchedBaselineMode())
                                || POLICY_AUTONOMOUS_RESEARCHED.equals(policy)
                                || POLICY_USER_REFINED_SCENARIO.equals(policy)
                                || (POLICY_EXPLICIT_SCENARIO.equals(policy)
                                                && Boolean.TRUE.equals(overrides.getIsExpensesCapitalize()));
        }

        private void applyResearchedSegmentDiscovery(
                        String ticker,
                        FinancialDataInput financialDataInput,
                        CompanyDataDTO companyDataDTO,
                        List<String> adjustedParameters) {
                if (!isAutonomousResearchedPolicy(financialDataInput)
                                || financialDataInput == null
                                || hasSegmentPackage(financialDataInput)
                                || tickerSegmentDiscoveryService == null) {
                        return;
                }
                Optional<SegmentResponseDTO> discovered =
                                tickerSegmentDiscoveryService.discoverSegments(ticker, companyDataDTO);
                if (discovered.isEmpty()
                                || discovered.get().getSegments() == null
                                || discovered.get().getSegments().size() <= 1) {
                        return;
                }
                financialDataInput.setSegments(ensureCuratedSegmentsIfNeeded(ticker, discovered.get()));
                adjustedParameters.add("segments");
                log.info("Attached discovered segment package for {} with {} segment row(s)",
                                ticker,
                                discovered.get().getSegments().size());
        }

        
        /**
         * If SEC table discovery returns segments without mappable sector keys, fall back
         * to curated mega-cap segment packages so researched mode can weight industries.
         */
        private SegmentResponseDTO ensureCuratedSegmentsIfNeeded(String ticker, SegmentResponseDTO discovered) {
                if (discovered == null || discovered.getSegments() == null || discovered.getSegments().isEmpty()) {
                        SegmentResponseDTO curated = curatedSegmentsForTicker(ticker);
                        return curated != null ? curated : discovered;
                }
                long mapped = discovered.getSegments().stream()
                                .filter(s -> s != null && s.getSector() != null && !s.getSector().isBlank())
                                .count();
                if (mapped >= 2) {
                        return discovered;
                }
                SegmentResponseDTO curated = curatedSegmentsForTicker(ticker);
                if (curated != null) {
                        log.info("Replacing weakly-mapped discovered segments for {} with curated package ({} rows)",
                                        ticker, curated.getSegments().size());
                        return curated;
                }
                return discovered;
        }

        private SegmentResponseDTO curatedSegmentsForTicker(String ticker) {
                if (ticker == null) {
                        return null;
                }
                String t = ticker.trim().toUpperCase(Locale.ROOT);
                List<SegmentResponseDTO.Segment> segments = new ArrayList<>();
                if ("MSFT".equals(t)) {
                        // FY shares approximate recent Microsoft segment mix (cloud-heavy).
                        segments.add(seg("software-infrastructure", "Software (System & Application)",
                                        "Intelligent Cloud", 0.43));
                        segments.add(seg("software-application", "Software (System & Application)",
                                        "Productivity and Business Processes", 0.33));
                        segments.add(seg("consumer-electronics", "Electronics (Consumer & Office)",
                                        "More Personal Computing", 0.24));
                } else if ("GOOGL".equals(t) || "GOOG".equals(t)) {
                        segments.add(seg("internet-content-information", "Software (Internet)", "Google Services", 0.84));
                        segments.add(seg("software-infrastructure", "Software (System & Application)", "Google Cloud", 0.12));
                        segments.add(seg("consumer-electronics", "Electronics (Consumer & Office)", "Other Bets", 0.04));
                } else if ("AMZN".equals(t)) {
                        segments.add(seg("internet-retail", "Retail (General)", "North America", 0.39));
                        segments.add(seg("internet-retail", "Retail (General)", "International", 0.22));
                        segments.add(seg("software-infrastructure", "Software (System & Application)", "AWS", 0.39));
                } else {
                        return null;
                }
                return new SegmentResponseDTO(segments);
        }

        private SegmentResponseDTO.Segment seg(String sector, String industry, String component, double share) {
                SegmentResponseDTO.Segment s = new SegmentResponseDTO.Segment();
                s.setSector(sector);
                s.setIndustry(industry);
                s.setComponents(List.of(component));
                s.setMappingScore(1.0);
                s.setRevenueShare(share);
                s.setOperatingMargin(null);
                return s;
        }


        private boolean hasSegmentPackage(FinancialDataInput financialDataInput) {
                return financialDataInput != null
                                && financialDataInput.getSegments() != null
                                && financialDataInput.getSegments().getSegments() != null
                                && !financialDataInput.getSegments().getSegments().isEmpty();
        }

        private void applyBaselineUseTransparency(
                        AssumptionTransparencyDTO dto,
                        FinancialDataInput financialDataInput,
                        List<String> adjustedParameters) {
                Set<String> adjustedParameterSet = adjustedParameters == null
                                ? Set.of()
                                : adjustedParameters.stream()
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toCollection(LinkedHashSet::new));
                boolean researchedBaselineMode = financialDataInput != null
                                && Boolean.TRUE.equals(financialDataInput.getResearchedBaselineMode());
                boolean segmentWeighted = "segment_weighted_baseline".equals(dto.getBaselineQuality())
                                && dto.isSegmentAware();
                boolean targetMarginOverride = adjustedParameterSet.contains("targetPreTaxOperatingMargin");

                List<String> warnings = new ArrayList<>();
                List<AssumptionTransparencyDTO.BaselineIssue> unsupportedBaselineDrivers = new ArrayList<>();
                SegmentWeightedParameters segmentParams = SegmentParameterContext.getParameters();
                if (segmentParams != null && segmentParams.getSegmentWarnings() != null) {
                        warnings.addAll(segmentParams.getSegmentWarnings());
                }

                if (segmentWeighted) {
                        dto.setBaselineUseStatus("validated_segment_weighted");
                        dto.setTargetOperatingMarginSource("Segment-weighted mechanical baseline");
                        dto.setTargetOperatingMarginStatus("segment_weighted");
                } else {
                        dto.setTargetOperatingMarginSource("Single-industry mechanical fallback");
                        dto.setTargetOperatingMarginStatus(targetMarginOverride
                                        ? "governed_or_user_override"
                                        : "single_industry_mechanical_fallback");
                        warnings.add("Single-industry mechanical fallback was used; target operating margin is not segment-weighted or researched evidence-supported.");
                        if (!targetMarginOverride) {
                                unsupportedBaselineDrivers.add(baselineIssue(
                                                "target_operating_margin",
                                                "mechanical_fallback",
                                                "Target operating margin came from the company-level industry fallback, not validated segment weighting or governed evidence."));
                        }
                }

                String baselineQuality = dto.getBaselineQuality();
                if (segmentWeighted) {
                        dto.setBaselineUseStatus("validated_segment_weighted");
                } else if (baselineQuality != null && baselineQuality.startsWith("segment_")) {
                        dto.setBaselineUseStatus("challenged_baseline");
                        unsupportedBaselineDrivers.add(baselineIssue(
                                        "segments",
                                        baselineQuality,
                                        "Segment package was present but did not pass baseline-use validation."));
                } else if (researchedBaselineMode) {
                        dto.setBaselineUseStatus("segment_evidence_insufficient");
                        warnings.add("researched baseline mode requires validated segment weighting or governed driver evidence; no valid segment package was used, so the baseline remains mechanical and challenged.");
                        unsupportedBaselineDrivers.add(baselineIssue(
                                        "segments",
                                        "segment_evidence_insufficient",
                                        "Researched baseline mode did not receive a validated segment package."));
                } else {
                        dto.setBaselineUseStatus("mechanical_only");
                }

                if (adjustedParameterSet.contains("negativeValueMarketCalibrationDiagnosticOnly")) {
                        dto.setBaselineUseStatus("challenged_baseline");
                        warnings.add("Negative first-pass value: market calibration stayed diagnostic and did not change researched baseline assumptions.");
                        unsupportedBaselineDrivers.add(baselineIssue(
                                        "market_calibration",
                                        "market_calibrated_diagnostic",
                                        "Negative first-pass researched baseline was not repaired with market-price calibration; market-implied outputs are diagnostics only."));
                }

                dto.setBaselineWarnings(dedupeStrings(warnings));
                dto.setUnsupportedBaselineDrivers(dedupeIssues(unsupportedBaselineDrivers));
                dto.setUnsupportedAdjustmentFields(defaultUnsupportedAdjustmentFields());
        }

        private List<AssumptionTransparencyDTO.BaselineIssue> defaultUnsupportedAdjustmentFields() {
                return List.of(
                                baselineIssue("operating_margin_next_year",
                                                "scenario_only_in_autonomous_researched_mode",
                                                "Next-year operating margin can be used for explicit user scenarios, but autonomous researched baselines must not change it."),
                                baselineIssue("wacc", "scenario_only_in_autonomous_researched_mode",
                                                "WACC can be used for explicit scenarios, but autonomous researched baselines must not change it without a governed tested path."),
                                baselineIssue("terminal_growth", "scenario_only_in_autonomous_researched_mode",
                                                "Terminal growth can be used for explicit scenarios, but autonomous researched baselines must not change it without a governed tested path."),
                                baselineIssue("tax_rate", "scenario_only_in_autonomous_researched_mode",
                                                "Tax-rate changes are report-only or explicit-scenario fields in autonomous researched mode."),
                                baselineIssue("rd_capitalization", "source_required",
                                                "R&D capitalization is automatic in autonomous researched mode only when multi-year source-backed R&D history and an amortization policy pass validation."),
                                baselineIssue("leases", "blocked_report_only",
                                                "Lease adjustments are report-only in Phase 5; R&D capitalization is the only governed accounting model path."),
                                baselineIssue("options", "blocked_report_only",
                                                "Options and warrants are explain/flag only unless a governed service contract applies them."),
                                baselineIssue("nols", "blocked_report_only",
                                                "NOL adjustments are explain/flag only unless a governed service contract applies them."),
                                baselineIssue("cash", "blocked_report_only",
                                                "Cash adjustments are report-only for autonomous researched baselines."),
                                baselineIssue("debt", "blocked_report_only",
                                                "Debt adjustments are report-only for autonomous researched baselines."),
                                baselineIssue("share_count", "blocked_report_only",
                                                "Share-count adjustments are report-only for autonomous researched baselines."),
                                baselineIssue("accounting_adjustments", "blocked_report_only",
                                                "Accounting cleanup fields are report-only unless an explicit governed service input is supported."));
        }

        private AssumptionTransparencyDTO.BaselineIssue baselineIssue(String field, String status, String reason) {
                return new AssumptionTransparencyDTO.BaselineIssue(field, status, reason);
        }

        private List<String> dedupeStrings(List<String> values) {
                return values.stream()
                                .filter(Objects::nonNull)
                                .filter(value -> !value.isBlank())
                                .distinct()
                                .collect(Collectors.toList());
        }

        private List<AssumptionTransparencyDTO.BaselineIssue> dedupeIssues(
                        List<AssumptionTransparencyDTO.BaselineIssue> issues) {
                Map<String, AssumptionTransparencyDTO.BaselineIssue> byField = new LinkedHashMap<>();
                for (AssumptionTransparencyDTO.BaselineIssue issue : issues) {
                        if (issue != null && issue.getField() != null && !issue.getField().isBlank()) {
                                byField.putIfAbsent(issue.getField(), issue);
                        }
                }
                return new ArrayList<>(byField.values());
        }

        private List<String> mappedIndustries(SegmentWeightedParameters segmentParams) {
                if (segmentParams == null || !segmentParams.hasSectorParameters()) {
                        return new ArrayList<>();
                }
                return segmentParams.getSectorParameters().values().stream()
                                .filter(Objects::nonNull)
                                .map(sector -> sector.getIndustryAsPerExcel() != null
                                                ? sector.getIndustryAsPerExcel()
                                                : sector.getSectorName())
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());
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
                if (overrides != null && Boolean.TRUE.equals(overrides.getResearchedBaselineMode())) {
                        return false;
                }
                if (isUserScenarioPolicy(overrides)) {
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
                                                : firstNonNull(
                                                                template != null
                                                                                ? template.getNormalizedOperatingMargin()
                                                                                : null,
                                                                0.0);

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

        private String resolveRequestPolicyMode(FinancialDataInput financialDataInput) {
                if (financialDataInput == null) {
                        return null;
                }
                if (financialDataInput.getRequestPolicyMode() != null
                                && !financialDataInput.getRequestPolicyMode().isBlank()) {
                        return normalizeRequestPolicyMode(financialDataInput.getRequestPolicyMode());
                }
                if (Boolean.TRUE.equals(financialDataInput.getResearchedBaselineMode())) {
                        return POLICY_AUTONOMOUS_RESEARCHED;
                }
                return null;
        }

        private String normalizeRequestPolicyMode(String requestPolicyMode) {
                if (requestPolicyMode == null) {
                        return null;
                }
                String normalized = requestPolicyMode.trim().toLowerCase(Locale.ROOT).replace('-', '_');
                if ("researched_baseline".equals(normalized) || "researched_autonomous".equals(normalized)) {
                        return POLICY_AUTONOMOUS_RESEARCHED;
                }
                return normalized;
        }

        private boolean isUserScenarioPolicy(FinancialDataInput financialDataInput) {
                String mode = resolveRequestPolicyMode(financialDataInput);
                return POLICY_USER_REFINED_SCENARIO.equals(mode) || POLICY_EXPLICIT_SCENARIO.equals(mode);
        }

        private boolean isUserRefinedScenarioPolicy(FinancialDataInput financialDataInput) {
                return POLICY_USER_REFINED_SCENARIO.equals(resolveRequestPolicyMode(financialDataInput));
        }

        private boolean isExplicitScenarioPolicy(FinancialDataInput financialDataInput) {
                return POLICY_EXPLICIT_SCENARIO.equals(resolveRequestPolicyMode(financialDataInput));
        }

        private boolean isActiveOverride(OverrideAssumption override) {
                return override != null && Boolean.TRUE.equals(override.getIsOverride());
        }

        private void rejectExplicitOnlyUserRefinedScenarioOverrides(
                        FinancialDataInput baseline,
                        FinancialDataInput overrides) {
                if (!isUserRefinedScenarioPolicy(baseline) || overrides == null) {
                        return;
                }
                List<String> unsupported = new ArrayList<>();
                if (overrides.getGrowthPatternOverride() != null) {
                        unsupported.add("growthPatternOverride");
                }
                if (overrides.getRiskFreeRate() != null) {
                        unsupported.add("riskFreeRate");
                }
                if (overrides.getInitialCostCapital() != null) {
                        unsupported.add("initialCostCapital");
                }
                if (overrides.getTerminalGrowthRate() != null) {
                        unsupported.add("terminalGrowthRate");
                }
                if (isActiveOverride(overrides.getOverrideAssumptionReturnOnCapital())) {
                        unsupported.add("overrideAssumptionReturnOnCapital");
                }
                if (!unsupported.isEmpty()) {
                        String unsupportedJson = unsupported.stream()
                                        .map(field -> "\"" + field + "\"")
                                        .collect(Collectors.joining(",", "[", "]"));
                        String msg = String.format(Locale.ROOT,
                                        "{\"error\":\"USER_REFINED_SCENARIO_EXPLICIT_ONLY_FIELDS\",\"message\":\"user_refined_scenario may only carry bounded guided-refinement fields; use explicit_scenario for %s.\",\"unsupported\":%s}",
                                        String.join(", ", unsupported),
                                        unsupportedJson);
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
                }
        }

        private void rejectTerminalRoicWithoutExplicitScenarioPolicy(
                        FinancialDataInput baseline,
                        FinancialDataInput overrides) {
                if (overrides == null || !isActiveOverride(overrides.getOverrideAssumptionReturnOnCapital())
                                || isExplicitScenarioPolicy(baseline)) {
                        return;
                }
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "{\"error\":\"TERMINAL_ROIC_EXPLICIT_SCENARIO_REQUIRED\",\"message\":\"terminal return on capital can be changed only in explicit_scenario mode.\"}");
        }

        private boolean shouldPreserveExplicitSalesToCapitalInputs(
                        FinancialDataInput financialDataInput,
                        List<String> adjustedParameters) {
                if (!isUserScenarioPolicy(financialDataInput)) {
                        return false;
                }
                Set<String> adjusted = adjustedParameterSet(adjustedParameters);
                return adjusted.contains("salesToCapitalYears1To5")
                                || adjusted.contains("salesToCapitalYears6To10")
                                || adjusted.contains("sectorOverrides");
        }

        private boolean shouldPreserveExplicitScenarioAssumptions(
                        FinancialDataInput financialDataInput,
                        List<String> adjustedParameters) {
                if (!isUserScenarioPolicy(financialDataInput)) {
                        return false;
                }
                Set<String> adjusted = adjustedParameterSet(adjustedParameters);
                return adjusted.contains("compoundAnnualGrowth2_5")
                                || adjusted.contains("operatingMarginNextYear")
                                || adjusted.contains("targetPreTaxOperatingMargin")
                                || adjusted.contains("convergenceYearMargin")
                                || adjusted.contains("terminalRevenue")
                                || adjusted.contains("salesToCapitalYears1To5")
                                || adjusted.contains("salesToCapitalYears6To10")
                                || adjusted.contains("overrideAssumptionReturnOnCapital")
                                || adjusted.contains("sectorOverrides")
                                || adjusted.contains("isExpensesCapitalize");
        }

        private boolean isAutonomousResearchedPolicy(FinancialDataInput financialDataInput) {
                return POLICY_AUTONOMOUS_RESEARCHED.equals(resolveRequestPolicyMode(financialDataInput));
        }

        private Set<String> adjustedParameterSet(List<String> adjustedParameters) {
                if (adjustedParameters == null) {
                        return Set.of();
                }
                return adjustedParameters.stream()
                                .filter(Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        private void validateTerminalGrowthOverride(
                        FinancialDataInput baseline,
                        Double terminalGrowthRate) {
                if (terminalGrowthRate == null) {
                        return;
                }
                double cap = terminalGrowthCapPercent(baseline);
                if (!Double.isFinite(terminalGrowthRate) || terminalGrowthRate < -5.0 || terminalGrowthRate > cap) {
                        String msg = String.format(Locale.ROOT,
                                        "{\"error\":\"TERMINAL_GROWTH_UNSAFE\",\"message\":\"terminalGrowthRate must be finite and between -5.00%% and the risk-free-rate mature-economy cap %.2f%%.\",\"provided\":%.4f,\"riskFreeRateCap\":%.4f}",
                                        cap,
                                        terminalGrowthRate,
                                        cap);
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
                }
        }

        private void validateBoundedScenarioInput(
                        String field,
                        Double value,
                        double minimum,
                        double maximum,
                        String unit) {
                if (value == null) {
                        return;
                }
                if (!Double.isFinite(value) || value < minimum || value > maximum) {
                        String provided = Double.isFinite(value)
                                        ? String.format(Locale.ROOT, "%.4f", value)
                                        : String.format(Locale.ROOT, "\"%s\"", value);
                        String msg = String.format(Locale.ROOT,
                                        "{\"error\":\"SCENARIO_INPUT_OUT_OF_BOUNDS\",\"message\":\"%s must be between %.2f and %.2f %s.\",\"field\":\"%s\",\"provided\":%s,\"minimum\":%.4f,\"maximum\":%.4f}",
                                        field,
                                        minimum,
                                        maximum,
                                        unit,
                                        field,
                                        provided,
                                        minimum,
                                        maximum);
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
                }
        }

        private void validateRequiredScenarioInput(String field, Double value, String unit) {
                if (value != null) {
                        return;
                }
                String msg = String.format(Locale.ROOT,
                                "{\"error\":\"SCENARIO_INPUT_REQUIRED\",\"message\":\"%s requires a finite %s value when override is active.\",\"field\":\"%s\"}",
                                field,
                                unit,
                                field);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
        }

        private void validatePositiveScenarioInput(String field, Double value, String unit) {
                if (value == null) {
                        return;
                }
                if (!Double.isFinite(value) || value <= 0.0) {
                        String provided = Double.isFinite(value)
                                        ? String.format(Locale.ROOT, "%.4f", value)
                                        : String.format(Locale.ROOT, "\"%s\"", value);
                        String msg = String.format(Locale.ROOT,
                                        "{\"error\":\"SCENARIO_INPUT_OUT_OF_BOUNDS\",\"message\":\"%s must be a positive finite %s value.\",\"field\":\"%s\",\"provided\":%s}",
                                        field,
                                        unit,
                                        field,
                                        provided);
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
                }
        }

        private double terminalGrowthCapPercent(FinancialDataInput financialDataInput) {
                Double riskFreeRate = financialDataInput != null ? financialDataInput.getRiskFreeRate() : null;
                if (riskFreeRate == null || !Double.isFinite(riskFreeRate)) {
                        return 0.0;
                }
                return Math.abs(riskFreeRate) <= 1.0 ? riskFreeRate * 100.0 : riskFreeRate;
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
                        ValuationTemplate template,
                        boolean enableSegments,
                        List<String> adjustedParameters) {

                // If intrinsic value is negative, apply calibration
                if (valuationOutputDTOCheck.getCompanyDTO().getEstimatedValuePerShare() < 0) {
                        if (isAutonomousResearchedPolicy(financialDataInput)) {
                                log.warn("Negative intrinsic value detected for {}, keeping market calibration diagnostic-only in researched baseline mode",
                                                ticker);
                                adjustedParameters.add("negativeValueMarketCalibrationDiagnosticOnly");
                                processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments,
                                                adjustedParameters);
                                return valuationOutputService.getValuationOutput(ticker,
                                                financialDataInput, template);
                        }
                        if (shouldPreserveExplicitScenarioAssumptions(financialDataInput, adjustedParameters)) {
                                log.warn("Negative intrinsic value detected for {}, preserving explicit scenario assumptions instead of applying calibration",
                                                ticker);
                                adjustedParameters.add("negativeValueCalibrationSkipped");
                                processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments,
                                                adjustedParameters);
                                return valuationOutputService.getValuationOutput(ticker,
                                                financialDataInput, template);
                        }
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
                                        financialDataInput, template);
                } else {
                        processSegmentAnalysis(financialDataInput, companyDataDTO, ticker, enableSegments,
                                        adjustedParameters);

                        return valuationOutputService.getValuationOutput(ticker,
                                        financialDataInput, template);
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
                adjustSalesToCapitalRatio(financialDataInput, new ArrayList<>());
        }

        private void adjustSalesToCapitalRatio(
                        FinancialDataInput financialDataInput,
                        List<String> adjustedParameters) {
                try {
                        if (shouldPreserveExplicitSalesToCapitalInputs(financialDataInput, adjustedParameters)) {
                                log.info("Sales-to-capital mechanical guard skipped for explicit scenario policy on {}",
                                                financialDataInput.getBasicInfoDataDTO() != null
                                                                ? financialDataInput.getBasicInfoDataDTO().getTicker()
                                                                : "unknown ticker");
                                return;
                        }
                        // Calculate current sales-to-capital ratio using R&D and operating lease
                        // adjustments
                        double currentSalesToCapital = valuationOutputService.calculateCurrentSalesToCapitalRatio(
                                        financialDataInput,
                                        commonService.calculateRDConverterValue(
                                                        financialDataInput.getIndustry(),
                                                        financialDataInput.getFinancialDataDTO().getMarginalTaxRate(),
                                                        financialDataInput.getFinancialDataDTO()
                                                                        .getResearchAndDevelopmentMap()),
                                        leaseResultForInput(financialDataInput));

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
         * 
         * @param baseline  The baseline FinancialDataInput populated from Yahoo Finance
         * @param overrides The minimal FinancialDataInput containing ONLY user
         *                  overrides
         */
        private List<String> applyUserOverrides(FinancialDataInput baseline, FinancialDataInput overrides) {
                log.info("Applying user overrides to baseline parameters...");
                int overrideCount = 0;
                Set<String> adjustedParameters = new LinkedHashSet<>();

                if (overrides.getRequestPolicyMode() != null && !overrides.getRequestPolicyMode().isBlank()) {
                        baseline.setRequestPolicyMode(normalizeRequestPolicyMode(overrides.getRequestPolicyMode()));
                        log.info("   Override: requestPolicyMode = {}", baseline.getRequestPolicyMode());
                        overrideCount++;
                }
                rejectExplicitOnlyUserRefinedScenarioOverrides(baseline, overrides);
                rejectTerminalRoicWithoutExplicitScenarioPolicy(baseline, overrides);

                if (Boolean.TRUE.equals(overrides.getIsExpensesCapitalize())) {
                        baseline.setRdAmortizationMethod(overrides.getRdAmortizationMethod());
                        baseline.setRdAmortizationPeriodYears(overrides.getRdAmortizationPeriodYears());
                        validateRdCapitalizationScenario(baseline);
                        baseline.setIsExpensesCapitalize(true);
                        log.info("   Override: isExpensesCapitalize = true");
                        overrideCount++;
                        adjustedParameters.add("isExpensesCapitalize");
                }

                if (Boolean.TRUE.equals(overrides.getHasOperatingLease())) {
                        throw accountingScenarioRejected(
                                        "LEASE_REPORT_ONLY",
                                        "Lease conversion is report-only in Phase 5; R&D capitalization is the only governed accounting model path.");
                }

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
                        validateBoundedScenarioInput(
                                        "convergenceYearMargin",
                                        overrides.getConvergenceYearMargin(),
                                        MIN_MARGIN_CONVERGENCE_YEAR,
                                        MAX_MARGIN_CONVERGENCE_YEAR,
                                        "projection year");
                        baseline.setConvergenceYearMargin(overrides.getConvergenceYearMargin());
                        log.info("   Override: convergenceYearMargin = {}", overrides.getConvergenceYearMargin());
                        overrideCount++;
                        adjustedParameters.add("convergenceYearMargin");
                }

                if (overrides.getGrowthPatternOverride() != null) {
                        baseline.setGrowthPatternOverride(overrides.getGrowthPatternOverride());
                        log.info("   Override: growthPatternOverride = {}", overrides.getGrowthPatternOverride());
                        overrideCount++;
                }

                if (Boolean.TRUE.equals(overrides.getResearchedBaselineMode())) {
                        baseline.setResearchedBaselineMode(true);
                        baseline.setRequestPolicyMode(POLICY_AUTONOMOUS_RESEARCHED);
                        log.info("   Override: researchedBaselineMode = true");
                        overrideCount++;
                }

                if (overrides.getSalesToCapitalYears1To5() != null) {
                        validateBoundedScenarioInput(
                                        "salesToCapitalYears1To5",
                                        overrides.getSalesToCapitalYears1To5(),
                                        MIN_SALES_TO_CAPITAL,
                                        MAX_SALES_TO_CAPITAL,
                                        "sales-to-capital multiple");
                        baseline.setSalesToCapitalYears1To5(overrides.getSalesToCapitalYears1To5());
                        log.info("   Override: salesToCapitalYears1To5 = {}", overrides.getSalesToCapitalYears1To5());
                        overrideCount++;
                        adjustedParameters.add("salesToCapitalYears1To5");
                }

                if (overrides.getSalesToCapitalYears6To10() != null) {
                        validateBoundedScenarioInput(
                                        "salesToCapitalYears6To10",
                                        overrides.getSalesToCapitalYears6To10(),
                                        MIN_SALES_TO_CAPITAL,
                                        MAX_SALES_TO_CAPITAL,
                                        "sales-to-capital multiple");
                        baseline.setSalesToCapitalYears6To10(overrides.getSalesToCapitalYears6To10());
                        log.info("   Override: salesToCapitalYears6To10 = {}", overrides.getSalesToCapitalYears6To10());
                        overrideCount++;
                        adjustedParameters.add("salesToCapitalYears6To10");
                }

                if (overrides.getTerminalRevenue() != null) {
                        validatePositiveScenarioInput("terminalRevenue", overrides.getTerminalRevenue(), "revenue");
                        baseline.setTerminalRevenue(overrides.getTerminalRevenue());
                        baseline.setTerminalRevenueYear(overrides.getTerminalRevenueYear());
                        log.info("   Override: terminalRevenue = {} at year {}",
                                        overrides.getTerminalRevenue(),
                                        overrides.getTerminalRevenueYear());
                        overrideCount++;
                        adjustedParameters.add("terminalRevenue");
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
                        validateTerminalGrowthOverride(baseline, overrides.getTerminalGrowthRate());
                        baseline.setTerminalGrowthRate(overrides.getTerminalGrowthRate());
                        log.info("   Override: terminalGrowthRate = {}%", overrides.getTerminalGrowthRate());
                        overrideCount++;
                        adjustedParameters.add("terminalGrowthRate");
                }

                if (isActiveOverride(overrides.getOverrideAssumptionReturnOnCapital())) {
                        validateRequiredScenarioInput(
                                        "overrideAssumptionReturnOnCapital",
                                        overrides.getOverrideAssumptionReturnOnCapital().getOverrideCost(),
                                        "percent");
                        validateBoundedScenarioInput(
                                        "overrideAssumptionReturnOnCapital",
                                        overrides.getOverrideAssumptionReturnOnCapital().getOverrideCost(),
                                        MIN_TERMINAL_ROIC,
                                        MAX_TERMINAL_ROIC,
                                        "percent");
                        baseline.setOverrideAssumptionReturnOnCapital(overrides.getOverrideAssumptionReturnOnCapital());
                        log.info("   Override: overrideAssumptionReturnOnCapital = {}%",
                                        overrides.getOverrideAssumptionReturnOnCapital().getOverrideCost());
                        overrideCount++;
                        adjustedParameters.add("overrideAssumptionReturnOnCapital");
                }

                // Copy caller-provided segments for multi-segment DCF breakdown and weighting.
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

        private void validateRdCapitalizationScenario(FinancialDataInput baseline) {
                if (!isRdCapitalizationPolicy(resolveRequestPolicyMode(baseline))) {
                        throw accountingScenarioRejected(
                                        "RD_CAPITALIZATION_SCENARIO_REQUIRED",
                                        "R&D capitalization can be applied only in autonomous_researched or explicit_scenario mode.");
                }
                FinancialDataDTO financial = baseline != null ? baseline.getFinancialDataDTO() : null;
                if (financial == null || !hasMultiYearRdHistory(financial.getResearchAndDevelopmentMap())) {
                        throw accountingScenarioRejected(
                                        "RD_CAPITALIZATION_SOURCE_REQUIRED",
                                        "R&D capitalization requires at least three positive R&D history records.");
                }
                if (!validRdAmortizationPolicy(baseline)) {
                        throw accountingScenarioRejected(
                                        "RD_CAPITALIZATION_AMORTIZATION_POLICY_REQUIRED",
                                        "R&D capitalization requires an explicit amortization method and period.");
                }
                SourceProvenance provenance = buildSourceProvenance(baseline);
                if (provenance == null
                                || !"retrieved".equals(provenance.getRetrievalStatus())
                                || SourceProvenance.YAHOO_NORMALIZED.equals(provenance.getSourceClass())) {
                        throw accountingScenarioRejected(
                                        "RD_CAPITALIZATION_SOURCE_REQUIRED",
                                        "R&D capitalization requires retrieved filing or company source provenance.");
                }
        }

        private boolean isRdCapitalizationPolicy(String requestPolicyMode) {
                return POLICY_AUTONOMOUS_RESEARCHED.equals(requestPolicyMode)
                                || POLICY_EXPLICIT_SCENARIO.equals(requestPolicyMode);
        }

        private boolean validRdAmortizationPolicy(FinancialDataInput input) {
                if (input == null || input.getRdAmortizationMethod() == null
                                || input.getRdAmortizationPeriodYears() == null) {
                        return false;
                }
                String method = input.getRdAmortizationMethod();
                int period = input.getRdAmortizationPeriodYears();
                return ("straight_line".equals(method) || "service_industry_policy".equals(method))
                                && period >= 2
                                && period <= 10;
        }

        private ResponseStatusException accountingScenarioRejected(String error, String message) {
                String body = String.format(Locale.ROOT,
                                "{\"error\":\"%s\",\"message\":\"%s\"}",
                                error,
                                message);
                return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, body);
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
                if (input.getRdAmortizationMethod() != null)
                        count++;
                if (input.getRdAmortizationPeriodYears() != null)
                        count++;
                if (Boolean.TRUE.equals(input.getHasOperatingLease()))
                        count++;
                if (input.getLeaseExpenseCurrentYear() != null)
                        count++;
                if (input.getLeaseCommitmentsYears1To5() != null)
                        count++;
                if (input.getLeaseCommitmentAfterYear5() != null)
                        count++;
                if (input.getGrowthPatternOverride() != null)
                        count++;
                if (Boolean.TRUE.equals(input.getResearchedBaselineMode()))
                        count++;
                if (input.getSectorOverrides() != null && !input.getSectorOverrides().isEmpty())
                        count++;
                return count;
        }

}
