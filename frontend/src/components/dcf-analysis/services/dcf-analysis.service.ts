import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, map, shareReplay, tap } from 'rxjs/operators';

import { DCFApiService } from './dcf-api.service';
import { LoggerService } from '../../../core/services';
import {
  CompanyDTO,
  DCFCalculationRequest,
  DCFValuationResponse,
  FinancialDTO,
  NarrativeDTO,
  RDConverterResponse
} from '../models';
import { AnalysisInputs, CompanyData, ValuationResults, YearlyProjection } from '../models';

export interface QuickAnalysisResult {
  companyData: CompanyData;
  rawApiData: DCFValuationResponse;
  results: ValuationResults;
}

@Injectable({
  providedIn: 'root'
})
export class DCFAnalysisService {
  // Request deduplication for rapid-fire prevention only (NOT for financial data caching)
  private ongoingRequests = new Map<string, Observable<QuickAnalysisResult | ValuationResults>>();

  constructor(
    private dcfApi: DCFApiService,
    private logger: LoggerService
  ) { }

  /**
   * Generate request key for deduplication (prevents rapid-fire duplicate requests)
   */
  private generateRequestKey(ticker: string, customInputs?: AnalysisInputs): string {
    const baseKey = `${ticker.toUpperCase()}_quick`;
    if (customInputs) {
      // Create a hash of custom inputs for request key
      const inputsHash = btoa(JSON.stringify(customInputs)).substring(0, 10);
      return `${baseKey}_${inputsHash}`;
    }
    return baseKey;
  }

  /**
   * Perform quick DCF analysis with request deduplication (no caching for financial accuracy)
   */
  performQuickAnalysis(ticker: string): Observable<QuickAnalysisResult> {
    const requestKey = this.generateRequestKey(ticker);

    // Check if request is already in progress to prevent duplicate API calls
    const ongoingRequest = this.ongoingRequests.get(requestKey);
    if (ongoingRequest) {
      return ongoingRequest as Observable<QuickAnalysisResult>;
    }

    // Create new request and store it for deduplication
    const request$ = this.dcfApi.getAutomatedDCFAnalysis(ticker).pipe(
      map(response => ({
        companyData: this.convertToCompanyData(response, ticker),
        rawApiData: response,
        results: this.processValuationResultsWithNarratives(response)
      })),
      catchError(error => {
        this.logger.error('Error performing quick analysis', error, 'DCFAnalysisService');
        return throwError(() => new Error('Failed to perform quick analysis. Please try again.'));
      }),
      // Clean up ongoing request when complete
      tap({
        next: () => this.ongoingRequests.delete(requestKey),
        error: () => this.ongoingRequests.delete(requestKey)
      }),
      // Share the observable to prevent multiple subscriptions from triggering multiple API calls
      shareReplay(1)
    );

    // Store the request for deduplication
    this.ongoingRequests.set(requestKey, request$);

    return request$;
  }

  /**
   * Perform custom DCF analysis with user inputs and request deduplication (no caching for financial accuracy)
   */
  performCustomAnalysis(
    ticker: string,
    userInputs: AnalysisInputs
  ): Observable<ValuationResults> {
    const requestKey = this.generateRequestKey(ticker, userInputs);

    // Check if identical request is already in progress
    const ongoingRequest = this.ongoingRequests.get(requestKey);
    if (ongoingRequest) {
      return ongoingRequest as Observable<ValuationResults>;
    }

    // Create custom DCF request based on user inputs
    const customRequest = this.createCustomDCFRequest(userInputs);

    const request$ = this.dcfApi.calculateDCFValuation(ticker, customRequest).pipe(
      map(response => this.processValuationResultsWithNarratives(response)),
      catchError(error => {
        this.logger.error('Error performing custom analysis', error, 'DCFAnalysisService');
        return throwError(() => new Error('Failed to perform custom analysis. Please check your inputs and try again.'));
      }),
      // Clean up ongoing request when complete
      tap({
        next: () => this.ongoingRequests.delete(requestKey),
        error: () => this.ongoingRequests.delete(requestKey)
      }),
      // Share the observable to prevent multiple subscriptions from triggering multiple API calls
      shareReplay(1)
    );

    // Store the request for deduplication
    this.ongoingRequests.set(requestKey, request$);

    return request$;
  }

  /**
   * Public method to convert API response to ValuationResults format
   * Used by container when updating state with custom analysis results
   */
  convertApiResponseToResults(
    apiResponse: DCFValuationResponse
  ): ValuationResults {
    return this.convertToValuationResults(apiResponse);
  }

  /**
   * Get R&D converter data for a company
   */
  getRDConverterData(
    industry: string,
    marginalTaxRate: number
  ): Observable<RDConverterResponse> {
    return this.dcfApi.getRDConverterData(industry, marginalTaxRate).pipe(
      catchError(error => {
        this.logger.error('Error fetching R&D converter data', error, 'DCFAnalysisService');
        return throwError(() => new Error('Failed to fetch R&D converter data.'));
      })
    );
  }

  /**
   * Process valuation results and attach narrative data if available
   */
  private processValuationResultsWithNarratives(response: DCFValuationResponse): ValuationResults {
    const results = this.convertToValuationResults(response);

    // Use API narrative data if available, otherwise leave narratives as null
    if (response.narrativeDTO) {
      const convertedNarratives = this.convertNarrativeDTO(response.narrativeDTO);
      // Map Real Options Analysis (optionality premium) if present
      const realOptionAnalysis = this.convertRealOptionAnalysis((response as any).narrativeDTO?.realOptionAnalysis);
      if (convertedNarratives) {
        return {
          ...results,
          narratives: convertedNarratives,
          ...(realOptionAnalysis ? { realOptionAnalysis } : {})
        } as unknown as ValuationResults;
      } else {
        // Conversion failed, hide narrative section
        return {
          ...results,
          narratives: undefined,
          ...(realOptionAnalysis ? { realOptionAnalysis } : {})
        } as unknown as ValuationResults;
      }
    } else {
      // No narrative data from API, hide narrative section
      return {
        ...results,
        narratives: undefined
      };
    }
  }

  /**
   * Convert API company data to internal CompanyData format
   */
  private convertToCompanyData(apiData: any, ticker: string): CompanyData {
    try {
      return {
        symbol: ticker,
        name: apiData?.companyName || 'Unknown Company',
        price: apiData?.companyDTO?.price,
        exchange: undefined,
        sector: undefined,
        industry: undefined,
        marketCap: undefined,
        logo: undefined
      };
    } catch (error) {
      this.logger.error('Error converting company data', { error, apiData }, 'DCFAnalysisService');

      // Return minimal valid data structure to prevent crashes
      return {
        symbol: ticker,
        name: 'Unknown Company',
        price: 0,
        exchange: undefined,
        sector: undefined,
        industry: undefined,
        marketCap: undefined,
        logo: undefined
      };
    }
  }

  /**
   * Convert API DCF response to internal ValuationResults format
   */
  private convertToValuationResults(
    apiResponse: DCFValuationResponse
  ): ValuationResults {
    const { companyDTO, terminalValueDTO, baseYearComparison, calibrationResultDTO } = apiResponse;

    // Calculate upside percentage
    const upside = ((companyDTO.estimatedValuePerShare - companyDTO.price) / companyDTO.price) * 100;


    return {
      intrinsicValue: companyDTO.estimatedValuePerShare,
      currentPrice: companyDTO.price,
      upside: upside,
      equityValue: companyDTO.valueOfEquity,
      enterpriseValue: companyDTO.valueOfOperatingAssets,
      terminalValue: companyDTO.terminalValue,
      fcfValue: companyDTO.pvCFOverNext10Years,
      dcfValuePerShare: companyDTO.estimatedValuePerShare,
      bookValuePerShare: companyDTO.valueOfEquity / companyDTO.numberOfShares,
      priceToBook: companyDTO.price / (companyDTO.valueOfEquity / companyDTO.numberOfShares),
      priceAsPercentageOfValue: companyDTO.priceAsPercentageOfValue,
      analysisDate: new Date().toISOString(),
      currency: apiResponse.currency, // DCF currency (valuation currency)
      stockCurrency: apiResponse.stockCurrency, // Stock trading currency

      // Valuation persistence IDs (from backend)
      valuation_id: apiResponse.valuation_id,
      user_valuation_id: apiResponse.user_valuation_id,

      // Story Cards Data
      profitabilityStory: apiResponse.profitabilityStory,
      riskStory: apiResponse.riskStory,
      assumptionTransparency: apiResponse.assumptionTransparency,

      // Equity Waterfall Data
      valueOfOperatingAssets: companyDTO.valueOfOperatingAssets,
      debt: companyDTO.debt,
      minorityInterests: companyDTO.minorityInterests,
      cash: companyDTO.cash,
      numberOfShares: companyDTO.numberOfShares,

      // Terminal Value Data
      terminalCashFlow: companyDTO.terminalCashFlow,
      terminalCostOfCapital: companyDTO.terminalCostOfCapital,
      pvTerminalValue: companyDTO.pvTerminalValue,
      terminalGrowthRate: apiResponse.terminalValueDTO?.growthRate,
      terminalReturnOnCapital: apiResponse.terminalValueDTO?.returnOnCapital,
      terminalReinvestmentRate: apiResponse.terminalValueDTO?.reinvestmentRate,

      // Industry Comparison Data
      revenueGrowthCompany: this.normalizePercentMaybeDecimal(baseYearComparison?.revenueGrowthCompany),
      revenueGrowthIndustry: this.normalizePercentMaybeDecimal(baseYearComparison?.revenueGrowthIndustry),
      operatingMarginCompany: this.normalizePercentMaybeDecimal(baseYearComparison?.operatingMarginCompany),
      operatingMarginIndustry: this.normalizePercentMaybeDecimal(baseYearComparison?.operatingMarginIndustry),

      // Market Expectations (Calibration Data)
      calibrationGrowth: calibrationResultDTO?.revenueGrowth,
      calibrationMargin: calibrationResultDTO?.operatingMargin,

      // News citations for rendering at bottom of results page
      newsSources: apiResponse.newsSources || [],

      projections: this.convertProjections(apiResponse.financialDTO),

      // Intrinsic Pricing (Peer Comparison) Data
      intrinsicPricing: apiResponse.intrinsicPricingDTO || undefined,
      intrinsicPricingV2: apiResponse.intrinsicPricingV2DTO || undefined,

    };
  }

  /**
   * Convert API financial projections to internal format
   * Supports sector-specific data when available
   */
  private convertProjections(financialDTO: FinancialDTO): YearlyProjection[] {
    const years = financialDTO.revenues.length;
    const projections = [];

    // Check if sector data is available
    const hasSectorData = this.hasSectorDataAvailable(financialDTO);
    const sectorNames = hasSectorData ? Object.keys(financialDTO.revenuesBySector || {}) : [];

    for (let i = 0; i < years; i++) {
      const projection: any = {
        year: new Date().getFullYear() + i,
        revenue: financialDTO.revenues[i] || 0,
        revenue_growth_rate: financialDTO.revenueGrowthRate?.[i] || 0,
        ebitda: financialDTO.ebitOperatingIncome[i] || 0, // Using operating income as EBITDA approximation
        ebitda_margin: financialDTO.ebitOperatingMargin[i] || 0,
        operating_income: financialDTO.ebitOperatingIncome[i] || 0,
        operating_margin: financialDTO.ebitOperatingMargin[i] || 0,
        ebit_after_tax: financialDTO.ebit1MinusTax[i] || 0,
        reinvestment: financialDTO.reinvestment[i] || 0,
        capex: 0, // Not directly available in API response
        working_capital_change: 0, // Not directly available in API response
        free_cash_flow: financialDTO.fcff[i] || 0,
        cost_of_capital: financialDTO.costOfCapital[i] || 0,
        sales_to_capital_ratio: financialDTO.salesToCapitalRatio[i] || 0,
        roic: financialDTO.roic[i] || 0,
        discount_factor: financialDTO.comulatedDiscountedFactor[i] || 0,
        present_value: financialDTO.pvFcff[i] || 0
      };

      // Add sector-specific data if available
      if (hasSectorData) {
        projection.sectorData = {};

        for (const sectorName of sectorNames) {
          projection.sectorData[sectorName] = {
            revenue: financialDTO.revenuesBySector?.[sectorName]?.[i] || 0,
            revenue_growth_rate: financialDTO.revenueGrowthRateBySector?.[sectorName]?.[i] || 0,
            operating_income: financialDTO.ebitOperatingIncomeSector?.[sectorName]?.[i] || 0,
            operating_margin: financialDTO.ebitOperatingMarginBySector?.[sectorName]?.[i] || 0,
            ebit_after_tax: financialDTO.ebit1MinusTaxBySector?.[sectorName]?.[i] || 0,
            reinvestment: financialDTO.reinvestmentBySector?.[sectorName]?.[i] || 0,
            free_cash_flow: financialDTO.fcffBySector?.[sectorName]?.[i] || 0,
            cost_of_capital: financialDTO.costOfCapitalBySector?.[sectorName]?.[i] || 0,
            sales_to_capital_ratio: financialDTO.salesToCapitalRatioBySector?.[sectorName]?.[i] || 0,
            roic: financialDTO.roicBySector?.[sectorName]?.[i] || 0,
            present_value: financialDTO.pvFcffBySector?.[sectorName]?.[i] || 0,
            invested_capital: financialDTO.investedCapitalBySector?.[sectorName]?.[i] || 0
          };
        }
      }

      projections.push(projection);
    }

    return projections;
  }

  private normalizePercentMaybeDecimal(value: number | undefined): number | undefined {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return value;
    }
    if (Math.abs(value) <= 1) {
      return value * 100;
    }
    if (Math.abs(value) > 100) {
      return value / 100;
    }
    return value;
  }

  /**
   * Check if sector-specific data is available in the API response
   */
  private hasSectorDataAvailable(financialDTO: FinancialDTO): boolean {
    return !!(
      financialDTO.revenuesBySector &&
      financialDTO.revenueGrowthRateBySector &&
      financialDTO.ebitOperatingMarginBySector &&
      financialDTO.ebitOperatingIncomeSector &&
      Object.keys(financialDTO.revenuesBySector).length > 0
    );
  }

  /**
   * Create custom DCF request from user inputs
   */
  private createCustomDCFRequest(
    userInputs: AnalysisInputs
  ): DCFCalculationRequest {
    return {
      isExpensesCapitalize: false,
      hasOperatingLease: false,
      companyRiskLevel: 'Medium',
      hasEmployeeOptions: false,
      numberOfOptions: 0,
      averageStrikePrice: 0,
      averageMaturity: 0,
      stockPriceStdDev: 0,
      overrideAssumptionCostCapital: { overrideCost: 0, isOverride: false },
      overrideAssumptionReturnOnCapital: { overrideCost: 0, isOverride: false },
      overrideAssumptionProbabilityOfFailure: {
        overrideCost: 0,
        isOverride: false,
        additionalInputValue: 0,
        additionalRadioValue: 'V'
      },
      overrideAssumptionReinvestmentLag: { overrideCost: 0, isOverride: false },
      revenueNextYear: userInputs.assumptions.revenueGrowthYears1to5,
      operatingMarginNextYear: userInputs.assumptions.targetEbitdaMargin,
      compoundAnnualGrowth2_5: userInputs.assumptions.revenueGrowthYears6to10,
      targetPreTaxOperatingMargin: userInputs.assumptions.targetEbitdaMargin,
      convergenceYearMargin: 3,
      salesToCapitalYears1To5: userInputs.assumptions.capexAsPercentOfRevenue,
      salesToCapitalYears6To10: userInputs.assumptions.capexAsPercentOfRevenue * 0.5,
      riskFreeRate: userInputs.assumptions.costOfEquity - 4,
      initialCostCapital: userInputs.assumptions.costOfEquity,
      overrideAssumptionNOL: { overrideCost: 0, isOverride: false },
      overrideAssumptionRiskFreeRate: { overrideCost: 0, isOverride: false },
      overrideAssumptionGrowthRate: {
        overrideCost: userInputs.assumptions.terminalGrowthRate,
        isOverride: true
      },
      overrideAssumptionCashPosition: {
        overrideCost: 0,
        isOverride: false,
        additionalInputValue: null
      },
      overrideAssumptionTaxRate: {
        overrideCost: userInputs.advancedSettings.taxRate,
        isOverride: true
      }
    };
  }

  /**
   * Convert API NarrativeDTO to internal narrative format
   */
  private convertNarrativeDTO(narrativeDTO: NarrativeDTO): any {
    try {
      const result: any = {};

      // Key Assumptions - narrative is required, metrics are optional
      if (narrativeDTO.keyAssumptions) {
        result.key_assumptions = {
          narrative: narrativeDTO.keyAssumptions.narrative
        };

        // Add optional growth rate metrics
        if (narrativeDTO.keyAssumptions.growthRate?.initial && narrativeDTO.keyAssumptions.growthRate?.terminal) {
          result.key_assumptions.growth_rate = {
            initial: parseFloat(narrativeDTO.keyAssumptions.growthRate.initial),
            terminal: parseFloat(narrativeDTO.keyAssumptions.growthRate.terminal)
          };
        }

        // Add optional cost of capital metrics
        if (narrativeDTO.keyAssumptions.costOfCapital?.initial && narrativeDTO.keyAssumptions.costOfCapital?.terminal) {
          result.key_assumptions.cost_of_capital = {
            initial: parseFloat(narrativeDTO.keyAssumptions.costOfCapital.initial),
            terminal: parseFloat(narrativeDTO.keyAssumptions.costOfCapital.terminal)
          };
        }

        // Add optional operating margin metrics
        if (narrativeDTO.keyAssumptions.operatingMargin?.average) {
          result.key_assumptions.operating_margin = {
            average: parseFloat(narrativeDTO.keyAssumptions.operatingMargin.average)
          };
        }

        // Add optional terminal growth rate
        if (narrativeDTO.keyAssumptions.terminalGrowthRate) {
          result.key_assumptions.terminal_growth_rate = narrativeDTO.keyAssumptions.terminalGrowthRate;
        }
      }

      // Value Drivers - narrative is required, metrics are optional
      if (narrativeDTO.valueDrivers) {
        result.value_drivers = {
          narrative: narrativeDTO.valueDrivers.narrative
        };

        // Add optional terminal value contribution
        if (narrativeDTO.valueDrivers.terminalValueContribution?.pdata) {
          result.value_drivers.terminal_value_contribution = {
            percentage_of_total: parseFloat(narrativeDTO.valueDrivers.terminalValueContribution.pdata)
          };
        }

        // Add optional explicit period PV
        if (narrativeDTO.valueDrivers.explicitPeriodPv?.pdata) {
          result.value_drivers.explicit_period_pv = {
            usd_billions: parseFloat(narrativeDTO.valueDrivers.explicitPeriodPv.pdata)
          };
        }

        // Add optional terminal value PV
        if (narrativeDTO.valueDrivers.terminalValuePv?.pdata) {
          result.value_drivers.terminal_value_pv = {
            usd_billions: parseFloat(narrativeDTO.valueDrivers.terminalValuePv.pdata)
          };
        }
      }

      // Valuation Summary - narrative is required, metrics are optional
      if (narrativeDTO.valuationSummary) {
        result.valuation_summary = {
          narrative: narrativeDTO.valuationSummary.narrative
        };

        // Add optional intrinsic value
        if (narrativeDTO.valuationSummary.intrinsicValuePerShare?.pdata) {
          result.valuation_summary.intrinsic_value_per_share = {
            usd: parseFloat(narrativeDTO.valuationSummary.intrinsicValuePerShare.pdata)
          };
        }

        // Add optional current market price
        if (narrativeDTO.valuationSummary.currentMarketPrice?.pdata) {
          result.valuation_summary.current_market_price = {
            usd: parseFloat(narrativeDTO.valuationSummary.currentMarketPrice.pdata)
          };
        }

        // Add optional premium to intrinsic
        if (narrativeDTO.valuationSummary.premiumToIntrinsic?.percentage) {
          result.valuation_summary.premium_to_intrinsic = {
            percentage: parseFloat(narrativeDTO.valuationSummary.premiumToIntrinsic.percentage)
          };
        }
      }

      // Sensitivity and Uncertainties - narrative is required, examples are optional
      if (narrativeDTO.sensitivityAndUncertainties) {
        result.sensitivity_and_uncertainties = {
          narrative: narrativeDTO.sensitivityAndUncertainties.narrative
        };

        // Add optional sensitivity examples
        if (narrativeDTO.sensitivityAndUncertainties.sensitivityExamples) {
          result.sensitivity_and_uncertainties.sensitivity_examples = {};

          if (narrativeDTO.sensitivityAndUncertainties.sensitivityExamples.terminalGrowthRate) {
            result.sensitivity_and_uncertainties.sensitivity_examples.terminal_growth_rate =
              this.convertSensitivityExamples(narrativeDTO.sensitivityAndUncertainties.sensitivityExamples.terminalGrowthRate);
          }

          if (narrativeDTO.sensitivityAndUncertainties.sensitivityExamples.wacc) {
            result.sensitivity_and_uncertainties.sensitivity_examples.wacc =
              this.convertSensitivityExamples(narrativeDTO.sensitivityAndUncertainties.sensitivityExamples.wacc);
          }
        }
      }

      // growth - narrative and title expected
      if (narrativeDTO.growth) {
        result.growth = {
          narrative: narrativeDTO.growth.narrative,
          title: narrativeDTO.growth.title
        };
      }

      // growth - narrative and title expected
      if (narrativeDTO.margins) {
        result.margins = {
          narrative: narrativeDTO.margins.narrative,
          title: narrativeDTO.margins.title
        };
      }

      // investment_efficiency - narrative and title expected
      if (narrativeDTO.investmentEfficiency) {
        result.investment_efficiency = {
          narrative: narrativeDTO.investmentEfficiency.narrative,
          title: narrativeDTO.investmentEfficiency.title
        };
      }

      // growth - narrative and title expected
      if (narrativeDTO.risks) {
        result.risks = {
          narrative: narrativeDTO.risks.narrative,
          title: narrativeDTO.risks.title
        };
      }
      // Key Takeaways - only narrative expected
      if (narrativeDTO.keyTakeaways) {
        result.key_takeaways = {
          narrative: narrativeDTO.keyTakeaways.narrative,
          title: narrativeDTO.keyTakeaways.title
        };
      }

      // Bull Bear Debate - include the full debate data
      if (narrativeDTO.bullBearDebate && narrativeDTO.bullBearDebate.length > 0) {
        result.bull_bear_debate = narrativeDTO.bullBearDebate;
      }

      if (narrativeDTO.scenarioAnalysis) {
        result.scenarioAnalysis = narrativeDTO.scenarioAnalysis;
      }


      return result;
    } catch (error) {
      this.logger.error('Error converting narrativeDTO', error, 'DCFAnalysisService');
      // Return a minimal structure with just narratives if possible
      try {
        return {
          key_assumptions: { narrative: narrativeDTO.keyAssumptions?.narrative },
          value_drivers: { narrative: narrativeDTO.valueDrivers?.narrative },
          valuation_summary: { narrative: narrativeDTO.valuationSummary?.narrative },
          sensitivity_and_uncertainties: { narrative: narrativeDTO.sensitivityAndUncertainties?.narrative },
          key_takeaways: { narrative: narrativeDTO.keyTakeaways?.narrative }
        };
      } catch (fallbackError) {
        this.logger.error('Failed to extract even basic narratives', fallbackError, 'DCFAnalysisService');
        return null;
      }
    }
  }

  /**
   * Convert API realOptionAnalysis (camelCase) into internal results.realOptionAnalysis (snake_case)
   */
  private convertRealOptionAnalysis(source: any): any | undefined {
    if (!source) {
      return undefined;
    }
    try {
      const mapped: any = {
        company: source.company,
        valuation_date: source.valuationDate,
        base_company_value: source.baseCompanyValue,
        total_real_options_value: source.totalRealOptionsValue,
        enhanced_company_value: source.enhancedCompanyValue,
        option_premium_percentage: source.optionPremiumPercentage,
        value_per_share: source.valuePerShare,
        options: Array.isArray(source.options) ? source.options.map((o: any) => ({
          option_type: o.optionType,
          confidence: o.confidence,
          description: o.description,
          underlying_value: o.underlyingValue,
          exercise_price: o.exercisePrice,
          time_to_expiry_years: o.timeToExpiryYears,
          volatility: o.volatility,
          pricing_methods: o.pricingMethods ? {
            black_scholes: o.pricingMethods.blackScholes,
            american_binomial: o.pricingMethods.americanBinomial,
            monte_carlo_lsm: o.pricingMethods.monteCarloLsm
          } : undefined,
          final_option_value: o.finalOptionValue
        })) : []
      };
      return mapped;
    } catch (e) {
      return undefined;
    }
  }

  /**
   * Convert sensitivity examples from API format (strings) to internal format (numbers)
   */
  private convertSensitivityExamples(examples: { [key: string]: string }): { [key: string]: number } {
    const result: { [key: string]: number } = {};
    if (examples && typeof examples === 'object') {
      for (const [key, value] of Object.entries(examples)) {
        if (value && typeof value === 'string') {
          const numValue = parseFloat(value);
          if (!isNaN(numValue)) {
            result[key] = numValue;
          }
        }
      }
    }
    return result;
  }
}
