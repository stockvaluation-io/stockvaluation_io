package io.stockvaluation.service;

import io.stockvaluation.constant.RDResult;
import io.stockvaluation.domain.IndustryAveragesGlobal;
import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.domain.SectorMapping;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.GrowthDto;
import io.stockvaluation.dto.LeaseResultDTO;
import io.stockvaluation.dto.OptionValueResultDTO;
import io.stockvaluation.dto.SegmentResponseDTO;
import io.stockvaluation.dto.SegmentWeightedParameters;
import io.stockvaluation.dto.ValuationOutputDTO;
import io.stockvaluation.dto.valuationoutput.BaseYearComparisonDTO;
import io.stockvaluation.dto.valuationoutput.CompanyDTO;
import io.stockvaluation.dto.valuationoutput.FinancialDTO;
import io.stockvaluation.dto.valuationoutput.TerminalValueDTO;
import io.stockvaluation.form.FinancialDataInput;
import io.stockvaluation.repository.IndustryAveragesGlobalRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.stockvaluation.repository.SectorMappingRepository;
import io.stockvaluation.utils.SegmentParameterContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValuationOutputService {

    private final CommonService commonService;

    private final OptionValueService optionValueService;

    private final CostOfCapitalService costOfCapitalService;

    private final SyntheticRatingService syntheticRatingService;

    private final IndustryAveragesGlobalRepository industryAvgGloRepository;

    private final InputStatRepository inputStatRepository;

    private final SectorMappingRepository sectorMappingRepository;

    private final DecimalFormat df = new DecimalFormat("0.00");

    // company data calculation method
    public CompanyDTO calculateCompanyData(FinancialDTO financialDTO, FinancialDataInput valuationInputDTO,
            OptionValueResultDTO optionValueResultDTO, LeaseResultDTO leaseResultDTO) {
        CompanyDTO companyDTO = new CompanyDTO();

        int terminalIndex = financialDTO.getTerminalYearIndex();
        int lastProjectionIndex = financialDTO.getLastProjectionYearIndex();

        companyDTO.setTerminalCashFlow(financialDTO.getFcff()[terminalIndex]);
        companyDTO.setTerminalCostOfCapital(financialDTO.getCostOfCapital()[terminalIndex]);
        // CRITICAL FIX: Ensure consistent scaling - both values are now in percentage
        // format
        companyDTO.setTerminalValue(companyDTO.getTerminalCashFlow() / ((companyDTO.getTerminalCostOfCapital() / 100)
                - (financialDTO.getRevenueGrowthRate()[terminalIndex] / 100)));
        companyDTO.setPvTerminalValue(
                companyDTO.getTerminalValue() * financialDTO.getComulatedDiscountedFactor()[lastProjectionIndex]);

        log.info("=== TERMINAL VALUE CALCULATION (Terminal Year Index: {}) ===", terminalIndex);
        log.info("  Terminal Cash Flow (FCFF[{}]): {}", terminalIndex, companyDTO.getTerminalCashFlow());
        log.info("  Terminal Cost of Capital: {}%", companyDTO.getTerminalCostOfCapital());
        log.info("  Terminal Growth Rate: {}%", financialDTO.getRevenueGrowthRate()[terminalIndex]);
        log.info("  Terminal ROIC: {}%", financialDTO.getRoic()[terminalIndex]);
        log.info("  Formula: Terminal Value = FCFF[{}] / ((CoC/100) - (g/100))", terminalIndex);
        log.info("  Calculation: {} / (({}/100) - ({}/100)) = {}",
                companyDTO.getTerminalCashFlow(),
                companyDTO.getTerminalCostOfCapital(),
                financialDTO.getRevenueGrowthRate()[terminalIndex],
                companyDTO.getTerminalValue());
        log.info("  PV of Terminal Value (discounted to Year 0): {}", companyDTO.getPvTerminalValue());
        log.info("  Discount Factor at Last Projection Year ({}): {}", lastProjectionIndex,
                financialDTO.getComulatedDiscountedFactor()[lastProjectionIndex]);
        log.info("=== END TERMINAL VALUE CALCULATION ===");

        companyDTO.setPvCFOverNext10Years(calculatePVCFOverNextYear(financialDTO.getPvFcff()));
        companyDTO.setSumOfPV(companyDTO.getPvTerminalValue() + companyDTO.getPvCFOverNext10Years());
        companyDTO.setProbabilityOfFailure(calculateProbablityOfFailure(valuationInputDTO));
        Double proceedsIfCompanyFails = calculateProceedsIfCompanyFails(valuationInputDTO, companyDTO.getSumOfPV());
        companyDTO.setProceedsIfFirmFails(proceedsIfCompanyFails);
        companyDTO.setValueOfOperatingAssets(companyDTO.getSumOfPV() * (1 - companyDTO.getProbabilityOfFailure() / 100)
                + companyDTO.getProceedsIfFirmFails() * companyDTO.getProbabilityOfFailure() / 100);
        Double debt = calculateDebt(valuationInputDTO, leaseResultDTO);
        companyDTO.setDebt(debt);
        Double minorityInterests = valuationInputDTO.getFinancialDataDTO().getMinorityInterestTTM();
        companyDTO.setMinorityInterests(minorityInterests != null ? minorityInterests : 0.0);
        Double cash = calculateCash(valuationInputDTO);
        companyDTO.setCash(cash);
        Double nonOperatingAssets = valuationInputDTO.getFinancialDataDTO().getNonOperatingAssetTTM();
        companyDTO.setNonOperatingAssets(nonOperatingAssets != null ? nonOperatingAssets : 0.0);
        companyDTO.setValueOfEquity(companyDTO.getValueOfOperatingAssets() + companyDTO.getCash() - companyDTO.getDebt()
                - companyDTO.getMinorityInterests() + companyDTO.getNonOperatingAssets());

        companyDTO.setValueOfOptions(calculateValueOfOptions(valuationInputDTO, optionValueResultDTO));

        companyDTO.setValueOfEquityInCommonStock(companyDTO.getValueOfEquity() - companyDTO.getValueOfOptions());
        companyDTO.setNumberOfShares(valuationInputDTO.getFinancialDataDTO().getNoOfShareOutstanding());
        companyDTO
                .setEstimatedValuePerShare(companyDTO.getValueOfEquityInCommonStock() / companyDTO.getNumberOfShares());
        companyDTO.setPrice(valuationInputDTO.getFinancialDataDTO().getStockPrice());
        companyDTO.setPriceAsPercentageOfValue(
                ((companyDTO.getPrice() / companyDTO.getEstimatedValuePerShare()) - 1) * 100);
        financialDTO.setIntrinsicValue(companyDTO.getEstimatedValuePerShare());
        return companyDTO;
    }

    private Double calculateDebt(FinancialDataInput valuationInputDTO, LeaseResultDTO leaseResultDTO) {
        Double debt = valuationInputDTO.getFinancialDataDTO().getBookValueDebtTTM();
        if (valuationInputDTO.getHasOperatingLease()) {
            debt += leaseResultDTO.getAdjustmentToTotalDebt();
        }
        return debt;
    }

    private Double calculateCash(FinancialDataInput valuationInputDTO) {
        if (valuationInputDTO.getOverrideAssumptionCashPosition().getIsOverride()) {
            return valuationInputDTO.getFinancialDataDTO().getCashAndMarkablTTM()
                    - valuationInputDTO.getOverrideAssumptionCashPosition().getOverrideCost()
                            * (valuationInputDTO.getFinancialDataDTO().getMarginalTaxRate()
                                    - valuationInputDTO.getOverrideAssumptionCashPosition().getAdditionalInputValue())
                            / 100;
        }
        return valuationInputDTO.getFinancialDataDTO().getCashAndMarkablTTM();
    }

    private Double calculateProceedsIfCompanyFails(FinancialDataInput valuationInputDTO, Double sumOfPV) {
        double fairValue = valuationInputDTO.getOverrideAssumptionProbabilityOfFailure().getAdditionalInputValue()
                / 100; // Convert from percentage to decimal
        Double proceedsValue = 0.0;

        String additionalRadioValue = valuationInputDTO.getOverrideAssumptionProbabilityOfFailure()
                .getAdditionalRadioValue();

        if ("B".equals(additionalRadioValue)) {
            proceedsValue = (valuationInputDTO.getFinancialDataDTO().getBookValueEqualityTTM()
                    + valuationInputDTO.getFinancialDataDTO().getBookValueDebtTTM()) * fairValue;
        } else if ("V".equals(additionalRadioValue)) {
            proceedsValue = sumOfPV * fairValue;
        }

        return proceedsValue;
    }

    private Double calculateProbablityOfFailure(FinancialDataInput financialDataInput) {
        if (financialDataInput.getOverrideAssumptionProbabilityOfFailure().getIsOverride()) {
            return financialDataInput.getOverrideAssumptionProbabilityOfFailure().getOverrideCost();
        }
        return 0.00;
    }

    private Double calculateValueOfOptions(FinancialDataInput financialDataInput,
            OptionValueResultDTO optionValueResultDTO) {
        if (financialDataInput.getHasEmployeeOptions()) {
            return optionValueResultDTO.getValueOfAllOptionsOutstanding();
        }
        return 0.00;
    }

    private Double calculatePVCFOverNextYear(Double[] pvFcff) {
        Double pvcffOverNext10Years = 0.00;
        for (int i = 0; i < pvFcff.length; i++) {
            if (pvFcff[i] != null) {
                pvcffOverNext10Years += pvFcff[i];
            }
        }
        return pvcffOverNext10Years;
    }

    // financials section
    public FinancialDTO calculateFinancialData(FinancialDataInput valuationInputDTO, RDResult rdResult,
            LeaseResultDTO leaseResultDTO, String ticker, io.stockvaluation.dto.ValuationTemplate template) {

        // Create FinancialDTO with template-based array sizing
        log.info("[calculateFinancialData] Template: {}, Projection years: {}",
                template != null && template.getGrowthPattern() != null ? template.getGrowthPattern().getDisplayName()
                        : "null",
                template != null ? template.getProjectionYears() : "default");

        FinancialDTO financialDTO = template != null ? new FinancialDTO(template) : new FinancialDTO();

        log.info("🔧 [calculateFinancialData] FinancialDTO array length: {}, terminal index: {}",
                financialDTO.getArrayLength(), financialDTO.getTerminalYearIndex());

        if (template != null) {
            log.info("📐 [TEMPLATE] Using {}-year DCF model (array length: {})",
                    template.getProjectionYears(), template.getArrayLength());
        }

        // Check if segment-weighted parameters are available in thread-safe context
        boolean useSegmentWeightedParams = SegmentParameterContext.hasValidParameters();
        if (useSegmentWeightedParams) {
            log.info("Using segment-weighted parameters from thread-safe context for {}", ticker);
        } else {
            log.info("Using company-level parameters for {} (no segment weighting)", ticker);
        }

        // Get the array length once to pass to all calculation methods
        int arrayLength = financialDTO.getArrayLength();

        Double[] revenueGrowth = calculateRevenueGrowthRate(valuationInputDTO, arrayLength);
        Double[] revenue = calculateRevenue(revenueGrowth, valuationInputDTO);

        financialDTO.setRevenueGrowthRate(revenueGrowth);
        financialDTO.setRevenues(revenue);

        // Calculate segment-specific revenues if segments exist
        calculateSegmentRevenues(financialDTO, valuationInputDTO, revenue, revenueGrowth);

        // EBIT Operating Margin
        Double[] ebitMargin = calculateEbitMargin(valuationInputDTO, rdResult, leaseResultDTO, arrayLength);
        financialDTO.setEbitOperatingMargin(ebitMargin);

        // EBIT Operating Income
        Double[] ebitIncome = calculateEbitIncome(revenue, ebitMargin, valuationInputDTO, rdResult, leaseResultDTO);
        financialDTO.setEbitOperatingIncome(ebitIncome);

        // Calculate segment-specific margins and incomes
        calculateSegmentMargins(financialDTO, valuationInputDTO, rdResult, leaseResultDTO, arrayLength);

        // Aggregate company-level margins and income from segments (if multi-segment)
        aggregateCompanyMetricsFromSegments(financialDTO, valuationInputDTO);

        // Tax Rate
        Double[] taxRate = calculateTaxRate(valuationInputDTO, arrayLength);
        financialDTO.setTaxRate(taxRate);

        Double[] nol = calculateNOL(ebitIncome, valuationInputDTO);
        financialDTO.setNol(nol);

        // EBIT(1-t)
        Double[] earningBeforeTaxAndIncome = calculateEarningBeforeTaxAndIntrest(ebitIncome, taxRate, nol);
        financialDTO.setEbit1MinusTax(earningBeforeTaxAndIncome);

        Double[] costOfCapital = calculateCostOfCapital(valuationInputDTO, arrayLength);
        financialDTO.setCostOfCapital(costOfCapital);

        // sales to capital ratio - use sector-weighted average if available
        Double[] salesToCapitalRatio = calculateSalesToCapitalRatio(valuationInputDTO, arrayLength);

        // If we have sector parameters, ensure company-level sales-to-capital is
        // properly averaged from sectors
        if (useSegmentWeightedParams && SegmentParameterContext.hasSectorParameters()) {
            salesToCapitalRatio = calculateAveragedSalesToCapitalFromSectors(valuationInputDTO, arrayLength);
        }

        // REINVESTMENT
        Double[] reinvestment = calculateReinvestment(valuationInputDTO, revenue, salesToCapitalRatio, revenueGrowth,
                costOfCapital, earningBeforeTaxAndIncome, ticker);
        financialDTO.setReinvestment(reinvestment);

        Double[] investedCapital = calculateInvestedCapital(reinvestment, valuationInputDTO, financialDTO, rdResult,
                leaseResultDTO);

        Double[] roic = calculateROIC(investedCapital, earningBeforeTaxAndIncome, valuationInputDTO, costOfCapital,
                ticker);

        financialDTO.setSalesToCapitalRatio(salesToCapitalRatio);
        financialDTO.setInvestedCapital(investedCapital);
        financialDTO.setRoic(roic);

        Double[] fcff = calculateFCFF(reinvestment, earningBeforeTaxAndIncome);
        // Free Cash Flow to Firm (FCFF)
        financialDTO.setFcff(fcff);

        Double[] comulatedDiscountFactor = calculateDiscountFactor(costOfCapital);
        financialDTO.setComulatedDiscountedFactor(comulatedDiscountFactor);

        Double[] pvFcff = calculatePVFCFF(fcff, comulatedDiscountFactor); // done
        financialDTO.setPvFcff(pvFcff);

        // Calculate comprehensive sector-level financial metrics (only if segments > 1)
        calculateSectorFinancialMetrics(financialDTO, valuationInputDTO, taxRate, salesToCapitalRatio,
                costOfCapital, comulatedDiscountFactor, investedCapital, arrayLength);

        // Aggregate sector metrics back to company level (reinvestment, FCFF, ROIC,
        // etc.)
        aggregateSectorMetricsToCompany(financialDTO, valuationInputDTO);

        return financialDTO;
    }

    private Double[] calculateReinvestment(FinancialDataInput valuationInputDTO, Double[] revenues,
            Double[] salesToCapitalRatio, Double[] revenueGrowth, Double[] costOfCapital, Double[] ebitBeforeTax,
            String ticker) {

        int arrayLength = revenues.length;
        int projectionYears = arrayLength - 2;
        int lastProjectionIndex = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        Double[] reinvestment = new Double[arrayLength];

        for (int year = 1; year <= projectionYears; year++) {
            Double salesToCapital = salesToCapitalRatio[year];
            if (valuationInputDTO.getOverrideAssumptionReinvestmentLag().getIsOverride()) {
                Double overrideCost = valuationInputDTO.getOverrideAssumptionReinvestmentLag().getOverrideCost();
                if (overrideCost == 0) {
                    reinvestment[year] = (revenues[year] - revenues[year - 1]) / salesToCapital;
                } else if (overrideCost == 1) {
                    if (year < projectionYears) {
                        reinvestment[year] = (revenues[year + 1] - revenues[year]) / salesToCapital;
                    } else {
                        reinvestment[year] = (revenues[year] * (revenueGrowth[terminalIndex] / 100)) / salesToCapital;
                    }
                } else if (overrideCost == 2) {
                    if (year <= projectionYears - 2) {
                        reinvestment[year] = (revenues[year + 2] - revenues[year + 1]) / salesToCapital;
                    } else {
                        reinvestment[year] = (revenues[year + 1] * (revenueGrowth[year + 1] / 100)) / salesToCapital;
                    }
                } else if (overrideCost == 3) {
                    if (year <= projectionYears - 3) {
                        reinvestment[year] = (revenues[year + 3] - revenues[year + 2]) / salesToCapital;
                    } else if (year == projectionYears - 1) {
                        reinvestment[year] = (revenues[year + 1] - revenueGrowth[year + 1] / 100) / salesToCapital;
                    } else if (year == projectionYears) {
                        reinvestment[year] = reinvestment[year - 1] * (1 + revenueGrowth[terminalIndex] / 100);
                    }
                } else {
                    throw new RuntimeException("Please enter lag value between 0 and 3");
                }
            } else {
                if (year < projectionYears) {
                    reinvestment[year] = (revenues[year + 1] - revenues[year]) / salesToCapital;
                } else {
                    // Last projection year: estimate based on terminal growth
                    reinvestment[year] = (revenues[year] * (revenueGrowth[terminalIndex] / 100)) / salesToCapital;
                }
            }
        }
        Double terminalROIC = costOfCapital[lastProjectionIndex];

        if (valuationInputDTO.getOverrideAssumptionReturnOnCapital().getIsOverride()) {
            terminalROIC = valuationInputDTO.getOverrideAssumptionReturnOnCapital().getOverrideCost();
        }

        reinvestment[terminalIndex] = revenueGrowth[terminalIndex] > 0
                ? ((revenueGrowth[terminalIndex] / 100) / (terminalROIC / 100) * ebitBeforeTax[terminalIndex])
                : 0.00;

        return reinvestment;
    }

    private Double[] calculateEbitMargin(FinancialDataInput financialDataInput, RDResult rdResult,
            LeaseResultDTO leaseResultDTO, int arrayLength) {
        Double[] ebitMargin = new Double[arrayLength];
        // calculating base year margin ebitIncome[0] / revenue[0]
        Double revenue = financialDataInput.getFinancialDataDTO().getRevenueTTM();
        Double operatingIncome;
        if (financialDataInput.getHasOperatingLease()) {
            if (financialDataInput.getIsExpensesCapitalize()) {
                operatingIncome = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM()
                        + leaseResultDTO.getAdjustmentToOperatingEarnings() + rdResult.getAdjustmentToOperatingIncome();
            } else {
                operatingIncome = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM()
                        + leaseResultDTO.getAdjustmentToOperatingEarnings();
            }

        } else {
            if (financialDataInput.getIsExpensesCapitalize()) {
                operatingIncome = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM()
                        + rdResult.getAdjustmentToOperatingIncome();
            } else {
                operatingIncome = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM();
            }
        }

        ebitMargin[0] = (operatingIncome / revenue) * 100;

        int projectionYears = arrayLength - 2; // base + projection + terminal
        int terminalIndex = arrayLength - 1;

        for (int year = 1; year < arrayLength; year++) {
            if (year == 1) {
                ebitMargin[year] = ebitMargin[year - 1];
            } else if (year <= projectionYears) {
                // Use segment-weighted parameters if available, otherwise fall back to
                // company-level parameters
                Double targetMargin = SegmentParameterContext.getParameterOrDefault(
                        SegmentWeightedParameters::getWeightedTargetPreTaxOperatingMargin,
                        financialDataInput.getTargetPreTaxOperatingMargin());
                Double convergenceYear = SegmentParameterContext.getParameterOrDefault(
                        SegmentWeightedParameters::getConvergenceYearMargin,
                        financialDataInput.getConvergenceYearMargin());

                if (year > convergenceYear) {
                    ebitMargin[year] = targetMargin;
                } else {
                    Double preTax = targetMargin;
                    Double prevMargin = ebitMargin[1];
                    log.info("convergence year: {}, preTax:{}, prevMargin:{}", convergenceYear, preTax, prevMargin);

                    ebitMargin[year] = preTax - (((preTax - prevMargin) / convergenceYear) * (convergenceYear - year));

                }
            } else {
                // Terminal year
                ebitMargin[year] = ebitMargin[projectionYears];
            }
        }
        return ebitMargin;
    }

    private Double[] calculateEarningBeforeTaxAndIntrest(Double[] ebitIncome, Double[] taxRate, Double[] nol) {
        int arrayLength = ebitIncome.length; // Get from existing array
        int lastProjectionIndex = arrayLength - 2;
        Double[] ebit = new Double[arrayLength];

        for (int year = 0; year < arrayLength; year++) {
            if (year == 0) {
                if (ebitIncome[year] > 0) {
                    ebit[year] = ebitIncome[year] * (1 - taxRate[year] / 100);
                } else {
                    ebit[year] = ebitIncome[year];
                }
                // log.info("value : {} ",123216000000.00 * 0.7591 );
            } else if (year <= lastProjectionIndex) {
                if (ebitIncome[year] > 0) {
                    if (ebitIncome[year] < nol[year]) {
                        ebit[year] = ebitIncome[year];
                    } else {
                        ebit[year] = ebitIncome[year] - (ebitIncome[year] - nol[year]) * taxRate[year] / 100;
                    }
                } else {
                    ebit[year] = ebitIncome[year];
                }
            } else {
                ebit[year] = ebitIncome[year] * (1 - taxRate[year] / 100);
            }

        }

        return ebit;
    }

    /**
     * Calculate present value of FCFF for projection years (not terminal).
     * 
     * Note: Terminal year pvFcff is set to 0.0 (not null) to maintain array
     * consistency.
     * The terminal value (perpetuity of terminal year+ cash flows) is calculated
     * separately
     * in CompanyDTO.pvTerminalValue using the formula: PV = (FCFF[terminal] / (CoC
     * - g)) * DF[lastProjection]
     * This separation keeps the explicit forecast period distinct from the
     * terminal perpetuity value.
     * 
     * @return Array of PV FCFF, where terminal index is 0.0
     */
    private Double[] calculatePVFCFF(Double[] fcff, Double[] comulatedDiscountFactor) {
        int arrayLength = fcff.length;
        int projectionYears = arrayLength - 2; // Exclude base (0) and terminal

        Double[] pvfcff = new Double[arrayLength];
        for (int year = 1; year <= projectionYears; year++) {
            if (fcff[year] != null && comulatedDiscountFactor[year] != null) {
                pvfcff[year] = fcff[year] * comulatedDiscountFactor[year];
            }
        }

        int terminalIndex = arrayLength - 1;
        log.info("Terminal year (Index {}) pvFcff: SET TO 0.0", terminalIndex);
        log.info("  Reason: Terminal perpetuity value calculated separately in CompanyDTO.pvTerminalValue");
        log.info("  Terminal FCFF (single year): {}",
                fcff != null && fcff[terminalIndex] != null ? fcff[terminalIndex] : "null");
        log.info("  This FCFF is used to calculate terminal value = FCFF[{}] / (CoC - g)", terminalIndex);

        return pvfcff;
    }

    private Double[] calculateDiscountFactor(Double[] costOfCapital) {
        int projectionYears = costOfCapital.length - 2; // Exclude base and terminal
        Double[] discountFactor = new Double[costOfCapital.length];
        for (int year = 1; year <= projectionYears; year++) {
            if (year == 1) {
                discountFactor[year] = 1 / (1 + costOfCapital[year] / 100);
            } else {
                discountFactor[year] = discountFactor[year - 1] * (1 / (1 + costOfCapital[year] / 100));
            }
        }
        return discountFactor;
    }

    private Double[] calculateCostOfCapital(FinancialDataInput financialDataInput, int arrayLength) {
        int projectionYears = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        Double[] costOfCapital = new Double[arrayLength];

        // Keep cost of capital in percentage format (e.g., 10.07 for 10.07%)
        // It will be divided by 100 in calculateDiscountFactor

        // Use segment-weighted parameters if available, otherwise fall back to
        // company-level parameters
        // initialCostCapital is multiplied by 100 in controller (10.07 → 1007), so
        // divide to get back to percentage
        Double initialCostofCapital = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getWeightedInitialCostCapital,
                financialDataInput.getInitialCostCapital()) / 100;

        Double riskFreeRateAdjusted = financialDataInput.getRiskFreeRate();
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            riskFreeRateAdjusted = financialDataInput.getRiskFreeRate() / 100;
        }

        // CRITICAL FIX: riskFreeRate is already in percentage format (4.6), no need to
        // divide by 100
        Double riskFreeRate = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getRiskFreeRate,
                riskFreeRateAdjusted);

        // Calculate terminal cost: riskFreeRate + mature market premium
        // = 8.54%
        Double terminalCostOfCapital = riskFreeRate + commonService.resolveMatureMarketPremium();

        if (financialDataInput.getOverrideAssumptionCostCapital().getIsOverride()) {
            terminalCostOfCapital = financialDataInput.getOverrideAssumptionCostCapital().getOverrideCost();
        } else {
            if (financialDataInput.getOverrideAssumptionRiskFreeRate().getIsOverride()) {
                terminalCostOfCapital = financialDataInput.getOverrideAssumptionRiskFreeRate().getOverrideCost()
                        + commonService.resolveMatureMarketPremium();
            }
        }

        int adjustmentYears = projectionYears / 2;
        for (int year = 1; year <= projectionYears; year++) {
            if (year <= adjustmentYears) {
                costOfCapital[year] = initialCostofCapital;
            } else {
                int convergenceYears = projectionYears - adjustmentYears;
                costOfCapital[year] = costOfCapital[year - 1]
                        - (initialCostofCapital - terminalCostOfCapital) / convergenceYears;
            }
        }
        costOfCapital[terminalIndex] = terminalCostOfCapital;
        return costOfCapital;
    }

    // calculate nol
    private Double[] calculateNOL(Double[] ebitIncome, FinancialDataInput financialDataInput) {
        int arrayLength = ebitIncome.length; // Get from existing array
        Double baseNol = 0.00;
        if (financialDataInput.getOverrideAssumptionNOL().getIsOverride()) {
            baseNol = financialDataInput.getOverrideAssumptionNOL().getOverrideCost();
        }
        Double[] nol = new Double[arrayLength];
        nol[0] = baseNol;
        for (int year = 1; year < arrayLength; year++) {
            Double lastYearNol = nol[year - 1];
            Double currentNol;
            if (ebitIncome[year] < 0) {
                currentNol = lastYearNol - ebitIncome[year];
            } else if (lastYearNol > ebitIncome[year]) {
                currentNol = lastYearNol - ebitIncome[year];
            } else {
                currentNol = 0.00;
            }
            nol[year] = currentNol;
        }
        return nol;
    }

    // calculating fcff
    private Double[] calculateFCFF(Double[] reinvestment, Double[] earningBeforeTaxAndIncome) {
        int arrayLength = reinvestment.length;
        int terminalIndex = arrayLength - 1;

        Double[] fcff = new Double[arrayLength];
        for (int year = 1; year <= terminalIndex; year++) {
            if (earningBeforeTaxAndIncome[year] != null && reinvestment[year] != null) {
                Double fcffValue = earningBeforeTaxAndIncome[year] - reinvestment[year];
                fcff[year] = fcffValue;
            }
        }
        return fcff;
    }

    // helper methods to calculate financial data

    // calculating revenue growth Rate
    public Double[] calculateRevenueGrowthRate(FinancialDataInput financialDataInput, int arrayLength) {

        Double revenueGrowthRateNext = financialDataInput.getRevenueNextYear() * 100;
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            revenueGrowthRateNext = financialDataInput.getRevenueNextYear();
        }
        // Use segment-weighted parameters if available, otherwise fall back to
        // company-level parameters
        Double growthRateForNext = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getWeightedRevenueNextYear,
                revenueGrowthRateNext // Convert fallback to percentage
        );
        Double compoundedAnnualGrowthRateFor2To5Years = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getWeightedCompoundAnnualGrowth2_5,
                financialDataInput.getCompoundAnnualGrowth2_5());
        Double riskFreeRate = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getRiskFreeRate,
                financialDataInput.getRiskFreeRate()) / 100;

        // Use array length from template (passed as parameter)
        Double[] growthRate = new Double[arrayLength];
        int projectionYears = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        Double terminalYear;
        // Priority 1: User-provided terminalGrowthRate override (from dcf_recalculator
        // tool)
        if (financialDataInput.getTerminalGrowthRate() != null) {
            terminalYear = financialDataInput.getTerminalGrowthRate();
            log.info("Using user-provided terminal growth rate override: {}%", terminalYear);
        } else if (financialDataInput.getOverrideAssumptionGrowthRate().getIsOverride()) {
            terminalYear = financialDataInput.getOverrideAssumptionGrowthRate().getOverrideCost();
        } else if (financialDataInput.getOverrideAssumptionRiskFreeRate().getIsOverride()) {
            terminalYear = financialDataInput.getOverrideAssumptionRiskFreeRate().getOverrideCost();
        } else {

            if (financialDataInput.getSegments() == null ||
                    financialDataInput.getSegments().getSegments() == null ||
                    financialDataInput.getSegments().getSegments().size() <= 1) {
                terminalYear = riskFreeRate; // Use risk-free rate as terminal growth
            } else {
                // CRITICAL FIX: Allow terminal growth to converge to risk-free rate (4.6%)
                // This is the natural convergence point for sustainable long-term growth
                terminalYear = riskFreeRate * 100; // Use risk-free rate as terminal growth in percentage format
            }
        }
        int adjustmentYears = projectionYears / 2;
        // Dynamic calculation based on projection years
        for (int year = 1; year <= projectionYears; year++) {
            if (year == 1) {
                growthRate[year] = growthRateForNext;
            } else if (year <= adjustmentYears) {
                growthRate[year] = compoundedAnnualGrowthRateFor2To5Years;
            } else {
                int n = year - adjustmentYears;
                int convergenceYears = projectionYears - adjustmentYears;
                growthRate[year] = compoundedAnnualGrowthRateFor2To5Years
                        - ((compoundedAnnualGrowthRateFor2To5Years - terminalYear) / convergenceYears) * n;
            }
        }
        // DAMODARAN CONSTRAINT: Terminal growth must not exceed risk-free rate
        // This is a fundamental constraint in DCF valuation - no company can grow
        // faster than the economy indefinitely
        // EXCEPTION: Skip cap if user explicitly provided terminalGrowthRate override
        if (financialDataInput.getTerminalGrowthRate() != null) {
            growthRate[terminalIndex] = terminalYear; // Use override directly, no cap
            log.info("Terminal growth rate override: {}% (Damodaran cap bypassed)", terminalYear);
        } else {
            Double riskFreeRateCap = riskFreeRate;
            if (financialDataInput.getSegments() != null &&
                    financialDataInput.getSegments().getSegments() != null &&
                    financialDataInput.getSegments().getSegments().size() > 1) {
                riskFreeRateCap = riskFreeRate * 100; // Convert to percentage format for segments
            }
            growthRate[terminalIndex] = Math.min(terminalYear, riskFreeRateCap);

            log.debug("Terminal growth rate: {} (capped at risk-free rate: {})",
                    growthRate[terminalIndex], riskFreeRateCap);
        }

        return growthRate;
    }

    public Double[] calculateRevenueGrowthRateMarkov(List<Double> historicalGrowthRates, int years, int numStates,
            FinancialDataInput financialDataInput) {
        if (historicalGrowthRates == null || historicalGrowthRates.size() < 2) {
            throw new IllegalArgumentException("At least 2 historical growth rates are required");
        }

        Double terminalRate = financialDataInput.getRiskFreeRate() / 100.0;
        Double analystEstimate = financialDataInput.getRevenueNextYear(); // e.g. 0.05 for 5%

        Double[] simulatedGrowthRates = new Double[years + 1]; // index 0 unused
        Random rand = new Random();

        // 1. Determine min and max (in % terms for binning)
        double min = historicalGrowthRates.stream().map(x -> x * 100).min(Double::compareTo).get();
        double max = historicalGrowthRates.stream().map(x -> x * 100).max(Double::compareTo).get();

        int[] states = new int[historicalGrowthRates.size()];

        // 2. Handle edge case: all historical values equal
        if (max == min) {
            Arrays.fill(states, numStates / 2); // assign middle bin
        } else {
            double binSize = (max - min) / numStates;
            for (int i = 0; i < historicalGrowthRates.size(); i++) {
                int bin = (int) ((historicalGrowthRates.get(i) * 100 - min) / binSize);
                bin = Math.max(0, Math.min(numStates - 1, bin));
                states[i] = bin;
            }
        }

        // 3. Build transition matrix
        double[][] transitionMatrix = new double[numStates][numStates];
        int[] stateCounts = new int[numStates];

        for (int i = 1; i < states.length; i++) {
            int prev = states[i - 1];
            int curr = states[i];
            transitionMatrix[prev][curr] += 1.0;
            stateCounts[prev]++;
        }

        for (int i = 0; i < numStates; i++) {
            if (stateCounts[i] > 0) {
                for (int j = 0; j < numStates; j++) {
                    transitionMatrix[i][j] /= stateCounts[i];
                }
            } else {
                for (int j = 0; j < numStates; j++) {
                    transitionMatrix[i][j] = 1.0 / numStates;
                }
            }
        }

        // 4. Simulate growth rates
        int currentState = states[states.length - 1];

        // If analyst estimate exists, map it into a bin
        int analystBin = -1;
        if (analystEstimate != null && max != min) {
            double binSize = (max - min) / numStates;
            analystBin = (int) ((analystEstimate * 100 - min) / binSize);
            analystBin = Math.max(0, Math.min(numStates - 1, analystBin));
        }

        for (int year = 1; year <= years; year++) {
            // Representative growth for current state
            double representativeGrowth;
            if (max == min) {
                representativeGrowth = min;
            } else {
                double binSize = (max - min) / numStates;
                double binMin = min + currentState * binSize;
                double binMax = binMin + binSize;
                representativeGrowth = (binMin + binMax) / 2.0;
            }

            // Blend toward terminal rate
            double t = (double) year / years;
            double blendedGrowth = representativeGrowth / 100.0 * (1 - t) + terminalRate * t;

            simulatedGrowthRates[year] = blendedGrowth;

            // Sample next state
            double[] probs = transitionMatrix[currentState].clone();

            if (year == 1 && analystBin >= 0) {
                // Soft bias toward analyst bin for first year
                double analystWeight = 0.7; // 70% trust analyst
                for (int j = 0; j < numStates; j++) {
                    probs[j] = (1 - analystWeight) * probs[j] + (analystWeight) * (j == analystBin ? 1.0 : 0.0);
                }
            }

            // Normalize (just in case floating-point drift)
            double sum = Arrays.stream(probs).sum();
            for (int j = 0; j < numStates; j++) {
                probs[j] /= sum;
            }

            double r = rand.nextDouble();
            double cumulative = 0.0;
            int nextState = currentState;
            for (int j = 0; j < numStates; j++) {
                cumulative += probs[j];
                if (r < cumulative) {
                    nextState = j;
                    break;
                }
            }
            currentState = nextState;
        }

        return simulatedGrowthRates;
    }

    // calculating revenue
    private Double[] calculateRevenue(Double[] growthRate, FinancialDataInput financialDataInput) {
        int arrayLength = growthRate.length;
        int projectionYears = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        Double[] revenue = new Double[arrayLength];
        Double revenues = financialDataInput.getFinancialDataDTO().getRevenueTTM();
        revenue[0] = revenues;
        for (int year = 1; year <= projectionYears; year++) {
            Double lastYearRevenue = revenue[year - 1];
            Double currentGrowthRate = 1 + growthRate[year] / 100;
            revenues = lastYearRevenue * currentGrowthRate;
            revenue[year] = revenues;
            // log.info("last revenue: {}, growth Rate:{}, growthAfterAfterDecided:{},
            // final:{}",lastYearRevenue,growthRate[1], currentGrowthRate, revenues);
        }
        revenue[terminalIndex] = revenue[projectionYears] * (1 + growthRate[terminalIndex] / 100);
        return revenue;
    }

    // calculating ebit income
    private Double[] calculateEbitIncome(Double[] revenue, Double[] ebitMargin, FinancialDataInput financialDataInput,
            RDResult rdResult, LeaseResultDTO leaseResultDTO) {
        Double baseYear;

        if (financialDataInput.getHasOperatingLease()) {

            if (financialDataInput.getIsExpensesCapitalize()) {
                baseYear = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM()
                        + leaseResultDTO.getAdjustmentToOperatingEarnings() + rdResult.getAdjustmentToOperatingIncome();
            } else {
                baseYear = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM()
                        + leaseResultDTO.getAdjustmentToOperatingEarnings();
            }

        } else {
            if (financialDataInput.getIsExpensesCapitalize()) {
                baseYear = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM()
                        + rdResult.getAdjustmentToOperatingIncome();
            } else {
                baseYear = financialDataInput.getFinancialDataDTO().getOperatingIncomeTTM();
            }
        }
        int arrayLength = revenue.length;
        int terminalIndex = arrayLength - 1;

        Double[] ebitIncome = new Double[arrayLength];
        ebitIncome[0] = baseYear;
        for (int year = 1; year <= terminalIndex; year++) {
            Double income = revenue[year] * ebitMargin[year] / 100;
            ebitIncome[year] = income;
        }
        return ebitIncome;
    }

    // calculating tax rate
    private Double[] calculateTaxRate(FinancialDataInput financialDataInput, int arrayLength) {
        int projectionYears = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        Double[] taxRate = new Double[arrayLength];
        taxRate[0] = financialDataInput.getFinancialDataDTO().getEffectiveTaxRate() * 100;
        Double terminalTaxRate = financialDataInput.getFinancialDataDTO().getMarginalTaxRate();
        if (financialDataInput.getOverrideAssumptionTaxRate().getIsOverride()) {
            terminalTaxRate = financialDataInput.getFinancialDataDTO().getEffectiveTaxRate() * 100;
        }
        Double compundedTaxRate = taxRate[0];
        int adjustmentYears = projectionYears / 2;
        for (int year = 1; year <= projectionYears; year++) {
            if (year <= adjustmentYears) {
                taxRate[year] = taxRate[year - 1];
            } else {
                int convergenceYears = projectionYears - adjustmentYears;
                taxRate[year] = taxRate[year - 1] + (terminalTaxRate - compundedTaxRate) / convergenceYears;
            }
        }
        taxRate[terminalIndex] = terminalTaxRate;
        return taxRate;
    }

    private Double[] calculateROIC(Double[] investedCapital, Double[] ebit1MinusTax,
            FinancialDataInput financialDataInput, Double[] costOfCapital, String ticker) {

        int arrayLength = investedCapital.length;
        int projectionYears = arrayLength - 2;
        int lastProjectionIndex = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        Double[] roic = new Double[arrayLength];
        for (int year = 0; year <= projectionYears; year++) {
            if (investedCapital[year] != null) {
                if (year == 0) {
                    roic[year] = (ebit1MinusTax[year] / investedCapital[year]) * 100;
                } else {
                    roic[year] = (ebit1MinusTax[year] / investedCapital[year - 1]) * 100;
                }
            }
        }
        Double assumptionsValue = costOfCapital[lastProjectionIndex];
        if (financialDataInput.getOverrideAssumptionReturnOnCapital().getIsOverride()) {
            assumptionsValue = financialDataInput.getOverrideAssumptionReturnOnCapital().getOverrideCost();
        }

        roic[terminalIndex] = assumptionsValue;
        return roic;
    }

    private Double calculatePreInvestedCapital(FinancialDataInput financialDataInput, RDResult rdResult,
            LeaseResultDTO leaseResultDTO) {

        FinancialDataDTO dto = financialDataInput.getFinancialDataDTO();
        Double equity = dto.getBookValueEqualityTTM();
        Double debt = dto.getBookValueDebtTTM();
        Double cash = dto.getCashAndMarkablTTM();

        if (financialDataInput.getHasOperatingLease()) {
            if (financialDataInput.getIsExpensesCapitalize()) {
                return equity + debt - cash
                        + leaseResultDTO.getAdjustmentToTotalDebt()
                        + rdResult.getTotalResearchAsset();
            } else {
                return (equity + debt)
                        - (cash + leaseResultDTO.getAdjustmentToTotalDebt());
            }
        } else {
            if (financialDataInput.getIsExpensesCapitalize()) {
                return (equity + debt)
                        - (cash + rdResult.getTotalResearchAsset());
            } else {
                return (equity + debt) - cash;
            }
        }
    }

    /**
     * Calculate invested capital from year 0 to year 10.
     * 
     * Note: Year 11 (terminal year) invested capital is NOT calculated and remains
     * null.
     * This is intentional because the terminal value uses a perpetuity formula that
     * assumes
     * steady-state growth from the year 10 capital base, rather than requiring
     * explicit
     * calculation of year 11 capital. The terminal FCFF is divided by (CoC - g) to
     * get
     * the present value of all future cash flows beyond year 10.
     * 
     * @return Array of invested capital [0-11], where index 11 is null
     */
    private Double[] calculateInvestedCapital(Double[] reinvestment,
            FinancialDataInput financialDataInput,
            FinancialDTO financialDTO,
            RDResult rdResult,
            LeaseResultDTO leaseResultDTO) {

        int arrayLength = reinvestment.length; // Get from existing array
        Double[] investedCapital = new Double[arrayLength];

        // Extracted logic
        Double preInvestedCapital = calculatePreInvestedCapital(financialDataInput, rdResult, leaseResultDTO);

        investedCapital[0] = preInvestedCapital;

        int projectionYears = arrayLength - 2;
        int lastProjectionIndex = arrayLength - 2;
        int terminalIndex = arrayLength - 1;

        for (int year = 1; year <= projectionYears; year++) {
            if (reinvestment[year] != null) {
                if (year == 1) {
                    investedCapital[year] = preInvestedCapital + reinvestment[year];
                } else {
                    investedCapital[year] = investedCapital[year - 1] + reinvestment[year];
                }
            }
        }

        log.info("Terminal year (index {}) invested capital: NOT CALCULATED", terminalIndex);
        log.info("  Reason: Terminal value uses perpetuity formula, not incremental capital calculation");
        log.info("  Last projection year (index {}) ending capital: {}", lastProjectionIndex,
                investedCapital[lastProjectionIndex]);
        if (terminalIndex < reinvestment.length) {
            log.info("  Terminal year reinvestment: {}", reinvestment[terminalIndex]);
        }
        log.info("  Note: Terminal value assumes steady-state growth from last projection year capital base");

        return investedCapital;
    }

    // calculate sales to capital ratio
    private Double[] calculateSalesToCapitalRatio(FinancialDataInput financialDataInput, int arrayLength) {
        int projectionYears = arrayLength - 2;
        Double[] salesToCapitalRatio = new Double[arrayLength];

        // Use segment-weighted parameters if available, otherwise fall back to
        // company-level parameters
        Double ratio1To5 = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getWeightedSalesToCapitalYears1To5,
                financialDataInput.getSalesToCapitalYears1To5());
        Double ratio6to10 = SegmentParameterContext.getParameterOrDefault(
                SegmentWeightedParameters::getWeightedSalesToCapitalYears6To10,
                financialDataInput.getSalesToCapitalYears6To10());

        int adjustmentYears = projectionYears / 2;
        for (int year = 1; year <= projectionYears; year++) {
            if (year <= adjustmentYears) {
                // Fixed ratio for years 1–5
                salesToCapitalRatio[year] = ratio1To5;
            } else {
                // Linear transition from year 6 to 10
                int n = year - adjustmentYears; // how far beyond year 5
                salesToCapitalRatio[year] = ratio1To5 - ((ratio1To5 - ratio6to10) / 5) * n;
            }
        }

        // Optional: assign terminal year (index 11) same as year 10
        salesToCapitalRatio[arrayLength - 1] = ratio6to10;

        return salesToCapitalRatio;
    }

    /**
     * Calculate company-level sales-to-capital ratio as weighted average of
     * sector-specific ratios
     * This ensures proper averaging as requested by the user
     */
    private Double[] calculateAveragedSalesToCapitalFromSectors(FinancialDataInput financialDataInput,
            int arrayLength) {
        Double[] averagedSalesToCapital = new Double[arrayLength];

        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            // Fallback to regular calculation if no segments
            return calculateSalesToCapitalRatio(financialDataInput, arrayLength);
        }

        // Calculate weighted average for years 1-5 and 6-10
        double weightedRatio1To5 = 0.0;
        double weightedRatio6To10 = 0.0;
        double totalWeight = 0.0;

        for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
            String sectorKey = segment.getSector();
            Double revenueShare = segment.getRevenueShare();

            if (revenueShare == null || revenueShare == 0) {
                continue;
            }

            // Get sector-specific sales-to-capital ratios
            Double sectorRatio1To5 = SegmentParameterContext.getSectorParameterOrDefault(
                    sectorKey,
                    SegmentWeightedParameters.SectorParameters::getSalesToCapitalYears1To5,
                    financialDataInput.getSalesToCapitalYears1To5());

            Double sectorRatio6To10 = SegmentParameterContext.getSectorParameterOrDefault(
                    sectorKey,
                    SegmentWeightedParameters.SectorParameters::getSalesToCapitalYears6To10,
                    financialDataInput.getSalesToCapitalYears6To10());

            weightedRatio1To5 += sectorRatio1To5 * revenueShare;
            weightedRatio6To10 += sectorRatio6To10 * revenueShare;
            totalWeight += revenueShare;
        }

        // Calculate final weighted averages or use fallback
        Double ratio1To5;
        Double ratio6To10;

        if (totalWeight > 0) {
            ratio1To5 = weightedRatio1To5 / totalWeight;
            ratio6To10 = weightedRatio6To10 / totalWeight;
        } else {
            // Fallback to company-level values
            ratio1To5 = financialDataInput.getSalesToCapitalYears1To5();
            ratio6To10 = financialDataInput.getSalesToCapitalYears6To10();
        }

        // Apply the same interpolation logic as calculateSalesToCapitalRatio
        int lastProjectionIndex = arrayLength - 2;
        int transitionYears = lastProjectionIndex - 5; // Years 6 to lastProjectionIndex
        int adjustmentYears = lastProjectionIndex / 2;
        for (int year = 1; year <= lastProjectionIndex; year++) {
            if (year <= adjustmentYears) {
                // Fixed ratio for years 1–5
                averagedSalesToCapital[year] = ratio1To5;
            } else {
                // Linear transition from year 6 to lastProjectionIndex
                int n = year - adjustmentYears; // how far beyond year 5
                averagedSalesToCapital[year] = ratio1To5 - ((ratio1To5 - ratio6To10) / transitionYears) * n;
            }
        }

        // Terminal year same as last projection year
        int terminalIndex = arrayLength - 1;
        averagedSalesToCapital[terminalIndex] = ratio6To10;

        log.info("Calculated averaged sales-to-capital from sectors: Year 1-5={}, Year 6-10={}",
                ratio1To5, ratio6To10);

        return averagedSalesToCapital;
    }

    public double calculateCurrentSalesToCapitalRatio(FinancialDataInput financialDataInput, RDResult rdResult,
            LeaseResultDTO leaseResultDTO) {
        return financialDataInput.getFinancialDataDTO().getRevenueTTM()
                / calculatePreInvestedCapital(financialDataInput, rdResult, leaseResultDTO);
    }

    public TerminalValueDTO calculateTerminalValueData(FinancialDTO financialDTO, FinancialDataInput financialDataInput,
            CompanyDTO companyDTO) {
        TerminalValueDTO terminalValueDTO = new TerminalValueDTO();
        int terminalIndex = financialDTO.getTerminalYearIndex();
        terminalValueDTO.setCostOfCapital(companyDTO.getTerminalCostOfCapital());
        terminalValueDTO.setReturnOnCapital(financialDTO.getRoic()[terminalIndex]);
        terminalValueDTO.setGrowthRate(financialDTO.getRevenueGrowthRate()[terminalIndex]);
        terminalValueDTO.setReinvestmentRate(
                terminalValueDTO.getReturnOnCapital() != 0
                        ? (terminalValueDTO.getGrowthRate() / terminalValueDTO.getReturnOnCapital()) * 100
                        : 0);
        return terminalValueDTO;
    }

    private BaseYearComparisonDTO calculateBaseYearData(FinancialDTO financialDTO, FinancialDataInput valuationInput,
            String industryExcel) {

        BaseYearComparisonDTO baseYearComparisonDTO = new BaseYearComparisonDTO();
        baseYearComparisonDTO.setRevenue(financialDTO.getRevenues()[0]);
        baseYearComparisonDTO.setOperatingMarginCompany(financialDTO.getEbitOperatingMargin()[0]);
        baseYearComparisonDTO.setOperatingIncome(financialDTO.getEbitOperatingIncome()[0]);
        baseYearComparisonDTO.setEbit(financialDTO.getEbit1MinusTax()[0]);

        double revenueGrowthCompany = 0.0;
        GrowthDto growthDto = valuationInput.getGrowthDto();
        if (growthDto != null && growthDto.getRevenueGrowthRates() != null
                && !growthDto.getRevenueGrowthRates().isEmpty()) {
            // Use historical growth rates from GrowthDto
            revenueGrowthCompany = growthDto.getRevenueMu() != null ? growthDto.getRevenueMu() * 100 : 0.0;
        } else if (valuationInput.getFinancialDataDTO().getRevenueLTM() > 0) {
            // Fallback to multi-year CAGR if GrowthDto not available
            int years = 3; // smoothing period
            double part1 = valuationInput.getFinancialDataDTO().getRevenueTTM()
                    / valuationInput.getFinancialDataDTO().getRevenueLTM();
            double part2 = Math.pow(part1, 1.0 / years);
            revenueGrowthCompany = (part2 - 1) * 100;
        }

        baseYearComparisonDTO.setRevenueGrowthCompany(revenueGrowthCompany);

        Optional<Double> optionalGrowth = industryAvgGloRepository.findRevenueGrowth(industryExcel);
        baseYearComparisonDTO.setRevenueGrowthIndustry(optionalGrowth.orElse(0.00));

        Optional<Double> optionalMargin = industryAvgGloRepository.findOperatingMargin(industryExcel);
        baseYearComparisonDTO.setOperatingMarginIndustry(optionalMargin.orElse(0.00));
        return baseYearComparisonDTO;
    }

    /**
     * Main method to calculate DCF valuation with template support.
     * 
     * @param ticker            Stock ticker
     * @param valuationInputDTO Input parameters
     * @param addStory          Whether to add narrative story
     * @param template          Valuation template (null defaults to 10-year model)
     * @return Complete valuation output
     */
    public ValuationOutputDTO getValuationOutput(String ticker, final FinancialDataInput valuationInputDTO,
            boolean addStory, io.stockvaluation.dto.ValuationTemplate template) {
        ValuationOutputDTO valuationOutputDTO = new ValuationOutputDTO();

        // TODO: call here R ans D , Operating Lease , Option Value,
        // Cost of Capital , Synthetic Rating calculator :
        // because we may need this in future calculations so ,
        // calculate that in advanced so that we don't have to call that
        // all these converters in advanced and pass the results in
        // all methods in which we may need

        SectorMapping sectorMapping = sectorMappingRepository
                .findByIndustryName(valuationInputDTO.getBasicInfoDataDTO().getIndustryUs());

        RDResult rdResult = commonService.calculateRDConverterValue(
                valuationInputDTO.getIndustry(),
                valuationInputDTO.getFinancialDataDTO().getMarginalTaxRate(),
                valuationInputDTO.getFinancialDataDTO().getResearchAndDevelopmentMap());
        OptionValueResultDTO optionValueResultDTO = optionValueService.calculateOptionValue(ticker,
                valuationInputDTO.getAverageStrikePrice(), valuationInputDTO.getAverageMaturity(),
                valuationInputDTO.getNumberOfOptions(), valuationInputDTO.getStockPriceStdDev());
        LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConverter();

        // calling methods to get the calculated values
        FinancialDTO financialDTO = calculateFinancialData(valuationInputDTO, rdResult, leaseResultDTO, ticker,
                template);
        CompanyDTO companyDTO = calculateCompanyData(financialDTO, valuationInputDTO, optionValueResultDTO,
                leaseResultDTO);
        TerminalValueDTO terminalValueDTO = calculateTerminalValueData(financialDTO, valuationInputDTO, companyDTO);
        BaseYearComparisonDTO baseYearComparisonDTO = calculateBaseYearData(financialDTO, valuationInputDTO,
                sectorMapping.getIndustryAsPerExcel());

        // setting result
        valuationOutputDTO.setCompanyDTO(companyDTO);
        valuationOutputDTO.setFinancialDTO(financialDTO);
        valuationOutputDTO.setTerminalValueDTO(terminalValueDTO);
        valuationOutputDTO.setBaseYearComparison(baseYearComparisonDTO);
        valuationOutputDTO.setCompanyName(valuationInputDTO.getBasicInfoDataDTO().getCompanyName().replace(".", ""));

        valuationOutputDTO.setCurrency(valuationInputDTO.getBasicInfoDataDTO().getCurrency());
        valuationOutputDTO.setStockCurrency(valuationInputDTO.getBasicInfoDataDTO().getStockCurrency());
        ValuationOutputDTO valuationOutputDTOWithStory = this.addStory(valuationOutputDTO);

        return valuationOutputDTOWithStory;
    }

    /**
     * Backward compatibility method - defaults to 10-year model
     */
    public ValuationOutputDTO getValuationOutput(String ticker, final FinancialDataInput valuationInputDTO,
            boolean addStory) {
        return getValuationOutput(ticker, valuationInputDTO, addStory, null);
    }

    public ValuationOutputDTO addStory(ValuationOutputDTO valuationOutputDTO) {
        return valuationOutputDTO;
    }

    // Helper methods for segment-based calculations
    // Note: Weighted parameter calculations are now in
    // CommonService.applySegmentWeightedParameters()

    /**
     * Calculate segment-specific revenues for each year using sector-specific
     * growth rates
     */
    private void calculateSegmentRevenues(FinancialDTO financialDTO, FinancialDataInput financialDataInput,
            Double[] totalRevenues, Double[] revenueGrowth) {
        int arrayLength = totalRevenues.length; // Get from parameter
        int projectionYears = arrayLength - 2;

        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            return;
        }

        for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
            String sectorKey = segment.getSector();
            Double[] segmentRevenues = new Double[arrayLength];
            Double[] segmentGrowthRates = new Double[arrayLength];

            // Get sector-specific parameters if available
            if (SegmentParameterContext.hasSectorParameters()) {
                // Use sector-specific growth rates
                segmentGrowthRates = calculateSectorRevenueGrowthRate(sectorKey, financialDataInput, arrayLength);

                // Calculate segment revenues using sector-specific growth rates
                Double baseRevenue = totalRevenues[0] * segment.getRevenueShare();
                segmentRevenues[0] = baseRevenue;

                for (int year = 1; year <= projectionYears; year++) {
                    if (segmentGrowthRates[year] != null) {
                        segmentRevenues[year] = segmentRevenues[year - 1] * (1 + segmentGrowthRates[year] / 100);
                    } else {
                        segmentRevenues[year] = totalRevenues[year] * segment.getRevenueShare();
                    }
                }

                // Set terminal year value (same as last projection year's growth or
                // proportional to total)
                int terminalIndex = arrayLength - 1;
                if (segmentGrowthRates[terminalIndex] != null) {
                    segmentRevenues[terminalIndex] = segmentRevenues[projectionYears]
                            * (1 + segmentGrowthRates[terminalIndex] / 100);
                } else {
                    segmentRevenues[terminalIndex] = totalRevenues[terminalIndex] * segment.getRevenueShare();
                }

                log.debug("Sector {}: Using sector-specific growth rates", sectorKey);
            } else {
                // Fallback to company-level growth rates
                for (int year = 0; year < arrayLength; year++) {
                    segmentRevenues[year] = totalRevenues[year] * segment.getRevenueShare();
                }
                segmentGrowthRates = revenueGrowth.clone();
                log.debug("Sector {}: Using company-level growth rates (no sector parameters)", sectorKey);
            }

            financialDTO.getRevenuesBySector().put(sectorKey, segmentRevenues);
            financialDTO.getRevenueGrowthRateBySector().put(sectorKey, segmentGrowthRates);
        }
    }

    /**
     * Calculate sector-specific revenue growth rates using stored sector parameters
     */
    private Double[] calculateSectorRevenueGrowthRate(String sectorKey, FinancialDataInput financialDataInput,
            int arrayLength) {
        int projectionYears = arrayLength - 2;
        int terminalIndex = arrayLength - 1;
        Double[] growthRate = new Double[arrayLength];

        // Get sector-specific parameters
        Double growthRateForNext = SegmentParameterContext.getSectorParameterOrDefault(
                sectorKey,
                SegmentWeightedParameters.SectorParameters::getRevenueNextYear,
                financialDataInput.getRevenueNextYear() * 100 // Convert fallback to percentage
        );
        Double compoundedAnnualGrowthRateFor2To5Years = SegmentParameterContext.getSectorParameterOrDefault(
                sectorKey,
                SegmentWeightedParameters.SectorParameters::getCompoundAnnualGrowth2_5,
                financialDataInput.getCompoundAnnualGrowth2_5());
        // CRITICAL FIX: Use sector-specific terminal growth rate (should converge to
        // risk-free rate)
        Double terminalGrowthRate = SegmentParameterContext.getSectorParameterOrDefault(
                sectorKey,
                SegmentWeightedParameters.SectorParameters::getTerminalGrowthRate,
                financialDataInput.getRiskFreeRate() / 100) * 100; // Convert to percentage (terminalGrowthRate is
                                                                   // stored as decimal)

        // Handle overrides - Priority 1: User-provided terminalGrowthRate override
        // (from dcf_recalculator tool)
        Double terminalYear;
        boolean hasUserOverride = false;
        if (financialDataInput.getTerminalGrowthRate() != null) {
            terminalYear = financialDataInput.getTerminalGrowthRate();
            hasUserOverride = true;
            log.info("Sector {} using user-provided terminal growth rate override: {}%", sectorKey, terminalYear);
        } else if (financialDataInput.getOverrideAssumptionGrowthRate().getIsOverride()) {
            terminalYear = financialDataInput.getOverrideAssumptionGrowthRate().getOverrideCost();
        } else if (financialDataInput.getOverrideAssumptionRiskFreeRate().getIsOverride()) {
            terminalYear = financialDataInput.getOverrideAssumptionRiskFreeRate().getOverrideCost();
        } else {
            // CRITICAL FIX: Allow terminal growth to converge to risk-free rate (4.6%)
            // This is the natural convergence point for sustainable long-term growth
            terminalYear = terminalGrowthRate; // Use risk-free rate as terminal growth
        }

        int adjustmentYears = projectionYears / 2;
        int convergenceYears = projectionYears - adjustmentYears;
        // Calculate growth rates for each year
        for (int year = 1; year <= projectionYears; year++) {
            if (year == 1) {
                growthRate[year] = growthRateForNext;
            } else if (year <= adjustmentYears) {
                growthRate[year] = compoundedAnnualGrowthRateFor2To5Years;
            } else {
                int n = year - adjustmentYears;
                growthRate[year] = compoundedAnnualGrowthRateFor2To5Years
                        - ((compoundedAnnualGrowthRateFor2To5Years - terminalYear) / convergenceYears) * n;
            }
        }

        // DAMODARAN CONSTRAINT: Terminal growth must not exceed risk-free rate
        // EXCEPTION: Skip cap if user explicitly provided terminalGrowthRate override
        if (hasUserOverride) {
            growthRate[terminalIndex] = terminalYear; // Use override directly, no cap
            log.info("Sector {} terminal growth rate override: {}% (Damodaran cap bypassed)", sectorKey, terminalYear);
        } else {
            Double riskFreeRateCap = financialDataInput.getRiskFreeRate(); // Already in percentage format
            growthRate[terminalIndex] = Math.min(terminalYear, riskFreeRateCap);

            log.debug("Sector {} growth rates: Year 1={}%, Year 5={}, Terminal (index {})={}% (capped at {}%)",
                    sectorKey, growthRate[1], projectionYears >= 5 ? growthRate[5] : "N/A",
                    terminalIndex, growthRate[terminalIndex], riskFreeRateCap);
        }

        return growthRate;
    }

    /**
     * Aggregate company-level metrics from sector-level data
     * This ensures company-level margins and incomes are weighted averages of
     * sectors
     */
    private void aggregateCompanyMetricsFromSegments(FinancialDTO financialDTO, FinancialDataInput financialDataInput) {
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            return; // No aggregation needed for single-segment companies
        }

        int arrayLength = financialDTO.getArrayLength(); // Get from FinancialDTO
        int terminalIndex = arrayLength - 1;
        log.info("Aggregating company-level metrics from {} segments",
                financialDataInput.getSegments().getSegments().size());

        Double[] companyRevenues = new Double[arrayLength];
        Double[] companyMargins = new Double[arrayLength];
        Double[] companyIncome = new Double[arrayLength];

        // For each year, calculate weighted average margin
        for (int year = 0; year <= terminalIndex; year++) {
            double totalRevenue = 0.0;
            double totalIncome = 0.0;

            // Sum up income and revenue from all sectors
            for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
                String sectorKey = segment.getSector();
                Double[] sectorRevenues = financialDTO.getRevenuesBySector().get(sectorKey);
                Double[] sectorIncome = financialDTO.getEbitOperatingIncomeSector().get(sectorKey);

                if (sectorRevenues != null && sectorIncome != null && sectorRevenues[year] != null
                        && sectorIncome[year] != null) {
                    totalRevenue += sectorRevenues[year];
                    totalIncome += sectorIncome[year];
                }
            }

            // Calculate aggregate revenue, margin, and income
            companyRevenues[year] = totalRevenue;
            companyIncome[year] = totalIncome;
            if (totalRevenue > 0) {
                companyMargins[year] = (totalIncome / totalRevenue) * 100;
            } else {
                companyMargins[year] = financialDTO.getEbitOperatingMargin()[year]; // Fallback
            }

            log.debug("Year {}: totalRevenue={}, totalIncome={}, margin={}%",
                    year, totalRevenue, totalIncome, companyMargins[year]);
        }

        // Update company-level metrics
        financialDTO.setRevenues(companyRevenues);
        financialDTO.setEbitOperatingMargin(companyMargins);
        financialDTO.setEbitOperatingIncome(companyIncome);

    }

    /**
     * Aggregate sector-level financial metrics back to company level
     * This ensures reinvestment, FCFF, ROIC are sum/weighted averages of all
     * sectors
     */
    private void aggregateSectorMetricsToCompany(FinancialDTO financialDTO, FinancialDataInput financialDataInput) {
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            return; // No aggregation needed for single-segment companies
        }

        int arrayLength = financialDTO.getArrayLength(); // Get from FinancialDTO
        int terminalIndex = arrayLength - 1;
        int lastProjectionIndex = arrayLength - 2;
        log.info("Aggregating sector-level financial metrics to company level for {} segments",
                financialDataInput.getSegments().getSegments().size());

        Double[] companyReinvestment = new Double[arrayLength];
        Double[] companyFcff = new Double[arrayLength];
        Double[] companyPvFcff = new Double[arrayLength];
        Double[] companyRoic = new Double[arrayLength];

        // Initialize arrays
        for (int year = 0; year <= terminalIndex; year++) {
            companyReinvestment[year] = 0.0;
            companyFcff[year] = 0.0;
            companyPvFcff[year] = 0.0;
        }

        // Sum reinvestment and FCFF from all sectors
        for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
            String sectorKey = segment.getSector();
            Double[] sectorReinvestment = financialDTO.getReinvestmentBySector().get(sectorKey);
            Double[] sectorFcff = financialDTO.getFcffBySector().get(sectorKey);
            Double[] sectorPvFcff = financialDTO.getPvFcffBySector().get(sectorKey);

            if (sectorReinvestment != null && sectorFcff != null) {
                for (int year = 1; year <= terminalIndex; year++) {
                    if (sectorReinvestment[year] != null) {
                        companyReinvestment[year] += sectorReinvestment[year];
                    }
                    if (sectorFcff[year] != null) {
                        companyFcff[year] += sectorFcff[year];
                    }
                    if (sectorPvFcff != null && sectorPvFcff[year] != null && year <= lastProjectionIndex) {
                        companyPvFcff[year] += sectorPvFcff[year];
                    }
                }
            }
        }

        // Calculate weighted average ROIC based on invested capital
        for (int year = 0; year <= lastProjectionIndex; year++) {
            double totalInvestedCapital = 0.0;
            double weightedRoic = 0.0;

            for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
                String sectorKey = segment.getSector();
                Double[] sectorInvestedCapital = financialDTO.getInvestedCapitalBySector().get(sectorKey);
                Double[] sectorRoic = financialDTO.getRoicBySector().get(sectorKey);

                if (sectorInvestedCapital != null && sectorRoic != null) {
                    int capitalYear = (year == 0) ? 0 : year - 1;
                    Double capital = sectorInvestedCapital[capitalYear];
                    Double roic = sectorRoic[year];

                    if (capital != null && roic != null && capital > 0) {
                        totalInvestedCapital += capital;
                        weightedRoic += roic * capital;
                    }
                }
            }

            if (totalInvestedCapital > 0) {
                companyRoic[year] = weightedRoic / totalInvestedCapital;
            }
        }

        // FIXED: Terminal year should use the same override logic as company level
        Double assumptionsValue = financialDTO.getCostOfCapital()[lastProjectionIndex];
        if (financialDataInput.getOverrideAssumptionReturnOnCapital().getIsOverride()) {
            assumptionsValue = financialDataInput.getOverrideAssumptionReturnOnCapital().getOverrideCost();
        }
        companyRoic[terminalIndex] = assumptionsValue;

        // Update company-level metrics
        financialDTO.setReinvestment(companyReinvestment);
        financialDTO.setFcff(companyFcff);
        financialDTO.setPvFcff(companyPvFcff);
        financialDTO.setRoic(companyRoic);
    }

    /**
     * Calculate segment-specific operating margins and incomes using
     * sector-specific parameters
     */
    private void calculateSegmentMargins(FinancialDTO financialDTO, FinancialDataInput financialDataInput,
            RDResult rdResult, LeaseResultDTO leaseResultDTO, int arrayLength) {
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            return;
        }

        int lastProjectionIndex = arrayLength - 2;

        for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
            String sectorKey = segment.getSector();
            Double[] segmentRevenues = financialDTO.getRevenuesBySector().get(sectorKey);
            if (segmentRevenues == null) {
                continue;
            }

            Double[] segmentMargins = new Double[arrayLength];
            Double[] segmentIncome = new Double[arrayLength];

            // Get sector-specific parameters if available
            Double baseMargin;
            Double targetMargin;
            Double convergenceYear;

            if (SegmentParameterContext.hasSectorParameters()) {
                // Use sector-specific parameters
                baseMargin = SegmentParameterContext.getSectorParameterOrDefault(
                        sectorKey,
                        SegmentWeightedParameters.SectorParameters::getOperatingMarginNextYear,
                        financialDataInput.getOperatingMarginNextYear() * 100 // Convert fallback to percentage
                );
                targetMargin = SegmentParameterContext.getSectorParameterOrDefault(
                        sectorKey,
                        SegmentWeightedParameters.SectorParameters::getTargetPreTaxOperatingMargin,
                        financialDataInput.getTargetPreTaxOperatingMargin());
                convergenceYear = SegmentParameterContext.getSectorParameterOrDefault(
                        sectorKey,
                        SegmentWeightedParameters.SectorParameters::getConvergenceYearMargin,
                        financialDataInput.getConvergenceYearMargin());

                log.debug("Sector {}: Using sector-specific margin parameters - base={}%, target={}%, convergence={}",
                        sectorKey, baseMargin, targetMargin, convergenceYear);
            } else {
                // Fallback to company-level parameters
                baseMargin = financialDataInput.getOperatingMarginNextYear() * 100; // Convert to percentage
                targetMargin = financialDataInput.getTargetPreTaxOperatingMargin();
                convergenceYear = financialDataInput.getConvergenceYearMargin();

                log.debug("Sector {}: Using company-level margin parameters (no sector parameters)", sectorKey);
            }

            segmentMargins[0] = baseMargin;

            // Apply forced convergence if needed
            ConvergenceResult convergenceResult = applyMarginForcedConvergence(baseMargin, targetMargin, sectorKey);
            double effectiveTargetMargin = convergenceResult.effectiveTarget;

            // Calculate margins for years 1 to terminal
            for (int year = 1; year < arrayLength; year++) {
                if (year == 1) {
                    segmentMargins[year] = segmentMargins[0];
                } else if (year <= lastProjectionIndex) {
                    segmentMargins[year] = calculateConvergedValue(year, baseMargin, effectiveTargetMargin,
                            convergenceYear, lastProjectionIndex);
                } else {
                    segmentMargins[year] = segmentMargins[year - 1];
                }
            }

            // Calculate operating income for each year
            for (int year = 0; year < arrayLength; year++) {
                segmentIncome[year] = segmentRevenues[year] * (segmentMargins[year] / 100);
            }

            financialDTO.getEbitOperatingMarginBySector().put(sectorKey, segmentMargins);
            financialDTO.getEbitOperatingIncomeSector().put(sectorKey, segmentIncome);
        }
    }

    private Double[] allocateNOLToSector(Double[] companyNol, Double revenueShare) {
        int arrayLength = companyNol.length;
        Double[] sectorNol = new Double[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            if (companyNol[i] != null) {
                sectorNol[i] = companyNol[i] * revenueShare;
            }
        }
        return sectorNol;
    }

    /**
     * Calculate comprehensive sector-level financial metrics
     * This includes: EBIT(1-t), sales to capital, reinvestment, invested capital,
     * FCFF, ROIC, cost of capital, PV FCFF
     * Only executed when segments > 1 for backward compatibility
     */
    private void calculateSectorFinancialMetrics(FinancialDTO financialDTO, FinancialDataInput financialDataInput,
            Double[] taxRate, Double[] salesToCapitalRatio,
            Double[] costOfCapital, Double[] comulatedDiscountedFactor,
            Double[] investedCapital, int arrayLength) {
        if (financialDataInput.getSegments() == null ||
                financialDataInput.getSegments().getSegments() == null ||
                financialDataInput.getSegments().getSegments().size() <= 1) {
            log.info("Skipping sector financial metrics - segments: {}",
                    financialDataInput.getSegments() != null ? financialDataInput.getSegments().getSegments().size()
                            : "null");
            return; // Backward compatibility: skip if no multi-segment data
        }

        log.info("Calculating sector financial metrics for {} segments",
                financialDataInput.getSegments().getSegments().size());

        int projectionYears = arrayLength - 2; // e.g., 10 for 12-element array, 15 for 17-element array
        int terminalIndex = arrayLength - 1; // Last index (terminal year)
        int lastProjectionIndex = arrayLength - 2; // Second to last index (last projection year)

        Double[] nol = calculateNOL(financialDTO.getEbitOperatingIncome(), financialDataInput);

        for (SegmentResponseDTO.Segment segment : financialDataInput.getSegments().getSegments()) {
            String sectorKey = segment.getSector();
            Double[] segmentRevenues = financialDTO.getRevenuesBySector().get(sectorKey);
            Double[] segmentOperatingIncome = financialDTO.getEbitOperatingIncomeSector().get(sectorKey);

            if (segmentRevenues == null || segmentOperatingIncome == null) {
                log.warn("Skipping sector {} - missing revenue or operating income data", sectorKey);
                continue; // Skip this segment, process others
            }

            // Initialize arrays for this sector
            Double[] sectorEbit1MinusTax = new Double[arrayLength];
            Double[] sectorSalesToCapital = new Double[arrayLength];
            Double[] sectorReinvestment = new Double[arrayLength];
            Double[] sectorInvestedCapital = new Double[arrayLength];
            Double[] sectorFcff = new Double[arrayLength];
            Double[] sectorRoic = new Double[arrayLength];
            Double[] sectorCostOfCapital = new Double[arrayLength];
            Double[] sectorPvFcff = new Double[arrayLength];
            Double[] sectorNol = allocateNOLToSector(nol, segment.getRevenueShare());
            // Get sector-specific sales to capital ratio using stored sector parameters
            Double sectorSalesToCapitalValue1To5;
            Double sectorSalesToCapitalValue6To10;
            Double companySalesToCapital1To5 = financialDataInput.getSalesToCapitalYears1To5();
            Double companySalesToCapital6To10 = financialDataInput.getSalesToCapitalYears6To10();

            if (SegmentParameterContext.hasSectorParameters()) {
                // Use sector-specific parameters from stored context
                sectorSalesToCapitalValue1To5 = Math.max(
                        SegmentParameterContext.getSectorParameterOrDefault(
                                sectorKey,
                                SegmentWeightedParameters.SectorParameters::getSalesToCapitalYears1To5,
                                companySalesToCapital1To5),
                        companySalesToCapital1To5);
                sectorSalesToCapitalValue6To10 = Math.max(SegmentParameterContext.getSectorParameterOrDefault(
                        sectorKey,
                        SegmentWeightedParameters.SectorParameters::getSalesToCapitalYears6To10,
                        companySalesToCapital6To10), companySalesToCapital6To10);

                log.debug("Sector {}: Using stored sector-specific sales-to-capital - Phase 1: {}, Phase 2: {}",
                        sectorKey, sectorSalesToCapitalValue1To5, sectorSalesToCapitalValue6To10);
            } else {
                // Fallback to company-level values
                sectorSalesToCapitalValue1To5 = companySalesToCapital1To5;
                sectorSalesToCapitalValue6To10 = companySalesToCapital6To10;
                log.debug("Sector {}: Using company-level sales-to-capital (no sector parameters)", sectorKey);
            }
            int adjustmentYears = lastProjectionIndex / 2;

            // Apply forced convergence if needed
            ConvergenceResult stcConvergenceResult = applySalesToCapitalForcedConvergence(
                    sectorSalesToCapitalValue1To5, sectorSalesToCapitalValue6To10, sectorKey);
            double effectiveSalesToCapitalValue6To10 = stcConvergenceResult.effectiveTarget;

            // Calculate metrics for each year
            for (int year = 0; year < arrayLength; year++) {
                // EBIT(1-t) per sector
                if (year == 0) {
                    if (segmentOperatingIncome[year] > 0) {
                        sectorEbit1MinusTax[year] = segmentOperatingIncome[year] * (1 - taxRate[year] / 100);
                    } else {
                        sectorEbit1MinusTax[year] = segmentOperatingIncome[year];
                    }
                } else if (year <= lastProjectionIndex) {
                    // Apply NOL shield logic
                    if (segmentOperatingIncome[year] > 0) {
                        if (segmentOperatingIncome[year] < sectorNol[year]) {
                            sectorEbit1MinusTax[year] = segmentOperatingIncome[year];
                        } else {
                            sectorEbit1MinusTax[year] = segmentOperatingIncome[year] -
                                    (segmentOperatingIncome[year] - sectorNol[year]) * taxRate[year] / 100;
                        }
                    } else {
                        sectorEbit1MinusTax[year] = segmentOperatingIncome[year];
                    }
                } else {
                    sectorEbit1MinusTax[year] = segmentOperatingIncome[year] * (1 - taxRate[year] / 100);
                }

                // Sales to Capital per sector (gradual convergence from years 1-5 to
                // 6-lastProjectionIndex)
                if (year >= 1 && year <= lastProjectionIndex) {
                    if (year <= adjustmentYears) {
                        sectorSalesToCapital[year] = sectorSalesToCapitalValue1To5;
                    } else {
                        // Gradual convergence from year 6 to lastProjectionIndex
                        int convergenceYears = lastProjectionIndex - adjustmentYears; // e.g., 5 for 10-year (6-10), 10
                                                                                      // for 15-year (6-15)
                        int yearInConvergence = year - adjustmentYears; // Starts at 1
                        double convergenceFactor = (double) yearInConvergence / convergenceYears;
                        sectorSalesToCapital[year] = sectorSalesToCapitalValue1To5 +
                                (effectiveSalesToCapitalValue6To10 - sectorSalesToCapitalValue1To5) * convergenceFactor;
                    }
                } else if (year == terminalIndex) {
                    sectorSalesToCapital[year] = effectiveSalesToCapitalValue6To10;
                }

                // Cost of Capital per sector (use same as company for now, could be
                // sector-specific)
                if (year > 0) {
                    sectorCostOfCapital[year] = costOfCapital[year];
                }
            }

            log.debug("Sector {}: salesToCapital1-5={}, salesToCapital6-10={} (effective={})",
                    sectorKey, sectorSalesToCapitalValue1To5, sectorSalesToCapitalValue6To10,
                    effectiveSalesToCapitalValue6To10);

            // Calculate Reinvestment per sector
            for (int year = 1; year <= terminalIndex; year++) {
                if (sectorSalesToCapital[year] != null && sectorSalesToCapital[year] > 0) {
                    Double revenueChange = segmentRevenues[year] - segmentRevenues[year - 1];
                    sectorReinvestment[year] = revenueChange / sectorSalesToCapital[year];
                } else {
                    sectorReinvestment[year] = 0.0;
                }
            }

            // Calculate Invested Capital per sector
            // Allocate company's base year invested capital proportionally by revenue share
            sectorInvestedCapital[0] = investedCapital[0] * segment.getRevenueShare();
            log.debug("Sector {} base invested capital: {} (company={} × revenueShare={})",
                    sectorKey, sectorInvestedCapital[0], investedCapital[0], segment.getRevenueShare());

            for (int year = 1; year <= lastProjectionIndex; year++) {
                if (sectorReinvestment[year] != null) {
                    sectorInvestedCapital[year] = sectorInvestedCapital[year - 1] + sectorReinvestment[year];
                }
            }

            // Calculate FCFF per sector
            for (int year = 1; year <= terminalIndex; year++) {
                if (sectorEbit1MinusTax[year] != null && sectorReinvestment[year] != null) {
                    sectorFcff[year] = sectorEbit1MinusTax[year] - sectorReinvestment[year];
                }
            }

            // Calculate ROIC per sector
            for (int year = 0; year <= lastProjectionIndex; year++) {
                if (year == 0 && sectorInvestedCapital[year] != null && sectorInvestedCapital[year] > 0) {
                    sectorRoic[year] = (sectorEbit1MinusTax[year] / sectorInvestedCapital[year]) * 100;
                } else if (year >= 1 && sectorInvestedCapital[year - 1] != null
                        && sectorInvestedCapital[year - 1] > 0) {
                    sectorRoic[year] = (sectorEbit1MinusTax[year] / sectorInvestedCapital[year - 1]) * 100;
                }
            }

            // Terminal year ROIC: use the same override logic as company level
            Double terminalRoicValue = financialDTO.getCostOfCapital()[lastProjectionIndex];
            if (financialDataInput.getOverrideAssumptionReturnOnCapital().getIsOverride()) {
                terminalRoicValue = financialDataInput.getOverrideAssumptionReturnOnCapital().getOverrideCost();
            }
            sectorRoic[terminalIndex] = terminalRoicValue;

            // Calculate PV FCFF per sector
            for (int year = 1; year <= lastProjectionIndex; year++) {
                if (sectorFcff[year] != null && comulatedDiscountedFactor[year] != null) {
                    sectorPvFcff[year] = sectorFcff[year] * comulatedDiscountedFactor[year];
                }
            }

            // Store in FinancialDTO
            financialDTO.getEbit1MinusTaxBySector().put(sectorKey, sectorEbit1MinusTax);
            financialDTO.getSalesToCapitalRatioBySector().put(sectorKey, sectorSalesToCapital);
            financialDTO.getReinvestmentBySector().put(sectorKey, sectorReinvestment);
            financialDTO.getInvestedCapitalBySector().put(sectorKey, sectorInvestedCapital);
            financialDTO.getFcffBySector().put(sectorKey, sectorFcff);
            financialDTO.getRoicBySector().put(sectorKey, sectorRoic);
            financialDTO.getCostOfCapitalBySector().put(sectorKey, sectorCostOfCapital);
            financialDTO.getPvFcffBySector().put(sectorKey, sectorPvFcff);

            log.debug("Calculated comprehensive metrics for sector: {}", sectorKey);
        }
    }

    /**
     * Helper class to hold convergence parameters and result
     */
    private static class ConvergenceResult {
        public final double effectiveTarget;
        public final boolean wasForced;

        public ConvergenceResult(double effectiveTarget, boolean wasForced) {
            this.effectiveTarget = effectiveTarget;
            this.wasForced = wasForced;
        }
    }

    /**
     * Apply forced convergence for operating margins to prevent flat metrics
     * When baseMargin ≈ targetMargin, apply small artificial convergence
     * 
     * @param baseMargin   Starting margin (percentage)
     * @param targetMargin Target margin (percentage)
     * @param sectorKey    Sector name for logging
     * @return ConvergenceResult with effective target and forced flag
     */
    private ConvergenceResult applyMarginForcedConvergence(double baseMargin, double targetMargin, String sectorKey) {
        if (Math.abs(baseMargin - targetMargin) < 0.01) { // essentially equal
            // Apply 3% improvement for positive margins, +2pp for low margins
            double effectiveTarget;
            if (baseMargin > 0) {
                effectiveTarget = baseMargin * 1.03; // 3% improvement
            } else {
                effectiveTarget = baseMargin + 2.0; // +2 percentage points
            }

            log.info(
                    "🔧 [FORCED CONVERGENCE] Sector {}: baseMargin={}% ≈ targetMargin={}%, applying artificial convergence to {}%",
                    sectorKey, baseMargin, targetMargin, effectiveTarget);

            return new ConvergenceResult(effectiveTarget, true);
        }

        return new ConvergenceResult(targetMargin, false);
    }

    /**
     * Apply forced convergence for sales-to-capital ratios to prevent flat metrics
     * When phase1 ≈ phase2, apply capital efficiency improvement
     * 
     * @param phase1Value Sales-to-capital ratio for years 1-5
     * @param phase2Value Sales-to-capital ratio for years 6-10+
     * @param sectorKey   Sector name for logging
     * @return ConvergenceResult with effective target and forced flag
     */
    private ConvergenceResult applySalesToCapitalForcedConvergence(double phase1Value, double phase2Value,
            String sectorKey) {
        if (Math.abs(phase1Value - phase2Value) < 0.01) { // essentially equal
            // Apply 8% improvement in capital efficiency (higher ratio = better)
            double effectiveTarget = phase1Value * 1.08;

            log.info(
                    "🔧 [FORCED CONVERGENCE] Sector {}: salesToCapital phase1={} ≈ phase2={}, applying artificial convergence to {}",
                    sectorKey, phase1Value, phase2Value, effectiveTarget);

            return new ConvergenceResult(effectiveTarget, true);
        }

        return new ConvergenceResult(phase2Value, false);
    }

    /**
     * Calculate converged value for a given year using linear interpolation
     * 
     * @param year                Current year (1-based)
     * @param baseValue           Starting value
     * @param targetValue         Ending value
     * @param convergenceYear     Year by which convergence should complete
     * @param lastProjectionIndex Last projection year index
     * @return Interpolated value for the year
     */
    private double calculateConvergedValue(int year, double baseValue, double targetValue,
            double convergenceYear, int lastProjectionIndex) {
        if (year == 1) {
            return baseValue;
        } else if (year <= lastProjectionIndex) {
            if (year > convergenceYear) {
                return targetValue;
            } else {
                // Linear interpolation
                return baseValue + (((targetValue - baseValue) / convergenceYear) * year);
            }
        } else {
            // Terminal year
            return targetValue;
        }
    }

}
