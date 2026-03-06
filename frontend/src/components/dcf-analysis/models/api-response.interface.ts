// API Response interfaces based on actual API documentation

export interface ApiResponse<T> {
  success: boolean;
  httpStatus: number;
  timestamp: number;
  data: T;
}

export interface BasicInfoDataDTO {
  ticker: string;
  dateOfValuation: string;
  companyName: string;
  countryOfIncorporation: string;
  industryUs: string;
  industryGlobal: string;
  currency: string;
  stockCurrency: string;
  summary: string;
  compensationRisk: number;
  marketCap: number;
  heldPercentInstitutions: number;
  heldPercentInsiders: number | null;
  firstTradeDateEpochUtc: number;
  debtToEquity: number;
  timeZoneFullName: string | null;
  beta: number;
}

export interface FinancialDataDTO {
  revenueTTM: number;
  revenueLTM: number;
  operatingIncomeTTM: number;
  operatingIncomeLTM: number;
  interestExpenseTTM: number;
  interestExpenseLTM: number;
  bookValueEqualityTTM: number;
  bookValueEqualityLTM: number;
  bookValueDebtTTM: number;
  bookValueDebtLTM: number;
  cashAndMarkablTTM: number;
  cashAndMarkablLTM: number;
  nonOperatingAssetTTM: number;
  nonOperatingAssetLTM: number;
  minorityInterestTTM: number;
  minorityInterestLTM: number;
  noOfShareOutstanding: number;
  stockPrice: number;
  lowestStockPrice: number;
  highestStockPrice: number;
  previousDayStockPrice: number;
  effectiveTaxRate: number;
  marginalTaxRate: number;
  researchAndDevelopmentMap: {
    [key: string]: number;
  };
}

export interface CompanyDriveDataDTO {
  revenueNextYear: number;
  operatingMarginNextYear: number;
  compoundAnnualGrowth2_5: number;
  riskFreeRate: number;
  initialCostCapital: number;
  convergenceYearMargin: number;
  salesToCapitalYears1To5: number;
  salesToCapitalYears6To10: number;
  targetPreTaxOperatingMargin: number;
}

export interface GrowthDTO {
  revenueMu: number;
  revenueSigma: number;
  revenueStdDev: number;
  marginMu: number;
  marginSigma: number;
  marginStdDev: number;
  marginMin: number;
  marginMax: number;
  revenueMarginCorrelation: number;
}

export interface CompanyDataResponse {
  basicInfoDataDTO: BasicInfoDataDTO;
  financialDataDTO: FinancialDataDTO;
  companyDriveDataDTO: CompanyDriveDataDTO;
  growthDto: GrowthDTO;
}

export interface RDConverterResponse {
  totalResearchAsset: number;
  totalAmortization: number;
  adjustmentToOperatingIncome: number;
  taxEffect: number;
}

export interface FinancialDTO {
  revenueGrowthRate: (number | null)[];
  revenues: number[];
  ebitOperatingMargin: number[];
  ebitOperatingIncome: number[];
  taxRate: number[];
  ebit1MinusTax: number[];
  reinvestment: (number | null)[];
  fcff: (number | null)[];
  nol: number[];
  costOfCapital: (number | null)[];
  comulatedDiscountedFactor: (number | null)[];
  pvFcff: (number | null)[];
  salesToCapitalRatio: (number | null)[];
  investedCapital: number[];
  roic: number[];
  // Sector-specific data (optional, when company operates in multiple sectors)
  revenueGrowthRateBySector?: { [sector: string]: (number | null)[] };
  revenuesBySector?: { [sector: string]: number[] };
  ebitOperatingMarginBySector?: { [sector: string]: number[] };
  ebitOperatingIncomeSector?: { [sector: string]: number[] };
  ebit1MinusTaxBySector?: { [sector: string]: number[] };
  salesToCapitalRatioBySector?: { [sector: string]: (number | null)[] };
  reinvestmentBySector?: { [sector: string]: (number | null)[] };
  investedCapitalBySector?: { [sector: string]: number[] };
  fcffBySector?: { [sector: string]: (number | null)[] };
  roicBySector?: { [sector: string]: number[] };
  costOfCapitalBySector?: { [sector: string]: (number | null)[] };
  pvFcffBySector?: { [sector: string]: (number | null)[] };
}

export interface CompanyDTO {
  terminalCashFlow: number;
  terminalCostOfCapital: number;
  terminalValue: number;
  pvTerminalValue: number;
  pvCFOverNext10Years: number;
  sumOfPV: number;
  probabilityOfFailure: number;
  proceedsIfFirmFails: number;
  valueOfOperatingAssets: number;
  debt: number;
  minorityInterests: number;
  cash: number;
  nonOperatingAssets: number;
  valueOfEquity: number;
  valueOfOptions: number;
  valueOfEquityInCommonStock: number;
  numberOfShares: number;
  estimatedValuePerShare: number;
  price: number;
  priceAsPercentageOfValue: number;
}

export interface TerminalValueDTO {
  growthRate: number;
  costOfCapital: number;
  returnOnCapital: number;
  reinvestmentRate: number;
}

export interface BaseYearComparison {
  revenueGrowthCompany: number;
  revenueGrowthIndustry: number;
  revenue: number;
  operatingMarginCompany: number;
  operatingMarginIndustry: number;
  operatingIncome: number;
  ebit: number;
}

export interface CalibrationResultDTO {
  revenueGrowth: number;
  operatingMargin: number;
}

export interface ScenarioAdjustments {
  revenueGrowthRate: (number | null)[];
  operatingMargin: (number | null)[];
  salesToCapitalRatio: (number | null)[];
  discountRate: (number | null)[];
}

export interface Scenario {
  description: string;
  keyChanges: string[];
  valuationImpact: string;
  adjustments: ScenarioAdjustments;
  investmentThesis: string;
  intrinsicValue: number;
}

export interface NarrativeDTO {
  growth: {
    narrative: string;
    title: string;
  };
  margins: {
    narrative: string;
    title: string;
  };
  investmentEfficiency: {
    narrative: string;
    title: string;
  };
  risks: {
    narrative: string;
    title: string;
  };
  keyAssumptions: {
    narrative: string;
    growthRate: {
      initial: string;
      terminal: string;
    };
    costOfCapital: {
      initial: string;
      terminal: string;
    };
    operatingMargin: {
      average: string;
    };
    terminalGrowthRate: number;
  };
  valueDrivers: {
    narrative: string;
    terminalValueContribution: {
      pdata: string;
    };
    explicitPeriodPv: {
      pdata: string;
    };
    terminalValuePv: {
      pdata: string;
    };
  };
  valuationSummary: {
    narrative: string;
    intrinsicValuePerShare: {
      pdata: string;
    };
    currentMarketPrice: {
      pdata: string;
    };
    premiumToIntrinsic: {
      percentage: string;
    };
  };
  sensitivityAndUncertainties: {
    narrative: string;
    sensitivityExamples: {
      terminalGrowthRate: {
        [key: string]: string;
      };
      wacc: {
        [key: string]: string;
      };
    };
  };
  keyTakeaways: {
    narrative: string;
    title: string;
  };
  bullBearDebate?: {
    bear: string;
    bull: string;
    round: number;
  }[];
  scenarioAnalysis: {
    optimistic: Scenario;
    base_case: Scenario;
    pessimistic: Scenario;
  };
}

export interface MonteCarloYearDistribution {
  year: number;
  p5: number;
  p50: number;
  p95: number;
  mean?: number;
  std?: number;
  explanation?: string;
}

export interface MonteCarloResult {
  p5: number;      // 5th percentile (bear case)
  p25: number;     // 25th percentile
  p50: number;     // 50th percentile (median)
  p75: number;     // 75th percentile
  p95: number;     // 95th percentile (bull case)
  mean: number;    // Mean valuation
  std: number;     // Standard deviation
  successfulPaths: number;

  // Year-by-year distributions for visualization
  revenueGrowthDistributions?: MonteCarloYearDistribution[];
  operatingMarginDistributions?: MonteCarloYearDistribution[];
  salesToCapitalDistributions?: MonteCarloYearDistribution[];
  costOfCapitalDistributions?: MonteCarloYearDistribution[];
}

export interface NewsSource {
  title: string;
  url: string;
  source?: string;
  category?: string;
}

export interface ValuationSegment {
  sector: string;
  industry?: string | null;
  components?: string[];
  mappingScore?: number | null;
  revenueShare?: number | null;
  operatingMargin?: number | null;
}

export interface GrowthAnchor {
  entity?: string;
  entityDisplay?: string;
  region?: string;
  year?: number;
  numberOfFirms?: number;
  fundamentalGrowth?: number;
  historicalGrowthProxy?: number;
  expectedGrowthProxy?: number;
  confidenceScore?: number;
  p25?: number;
  p50?: number;
  p75?: number;
  source?: string;
}

export interface AssumptionTransparency {
  valuationModel?: string;
  industryUs?: string;
  industryGlobal?: string;
  currency?: string;
  segmentCount?: number;
  segmentAware?: boolean;
  discountRate?: {
    riskFreeRate?: number | null;
    equityRiskPremium?: number | null;
    initialCostOfCapital?: number | null;
    terminalCostOfCapital?: number | null;
    costOfCapitalFormula?: string;
    riskFreeRateSource?: string;
    equityRiskPremiumSource?: string;
    initialCostOfCapitalSource?: string;
  };
  operatingAssumptions?: {
    revenueGrowthRateYears2To5?: number | null;
    targetOperatingMargin?: number | null;
    salesToCapitalYears1To5?: number | null;
    salesToCapitalYears6To10?: number | null;
    revenueGrowthSource?: string;
    operatingMarginSource?: string;
    salesToCapitalSource?: string;
    revenueGrowthRationale?: string;
    operatingMarginRationale?: string;
    salesToCapitalRationale?: string;
  };
  adjustmentRationales?: {
    revenueGrowth?: string;
    operatingMargin?: string;
    salesToCapital?: string;
    costOfCapital?: string;
  };
  growthAnchor?: GrowthAnchor;
  marketImpliedExpectations?: {
    marketPrice?: number | null;
    modelIntrinsicValue?: number | null;
    method?: string;
    metrics?: Array<{
      key?: string;
      label?: string;
      unit?: string;
      modelValue?: number | null;
      impliedValue?: number | null;
      gap?: number | null;
      solved?: boolean;
      note?: string;
    }>;
  };
  notes?: string[];
}

export interface DCFValuationResponse {
  companyName: string;
  profitabilityStory?: string;
  riskStory?: string;
  currency: string;
  stockCurrency: string;
  industryUs?: string;
  industryGlobal?: string;
  financialDTO: FinancialDTO;
  companyDTO: CompanyDTO;
  terminalValueDTO: TerminalValueDTO;
  baseYearComparison: BaseYearComparison;
  story: any | null;
  simulationResultsDto: any | null;
  calibrationResultDTO: CalibrationResultDTO;
  narrativeDTO: NarrativeDTO | null;
  newsSources?: NewsSource[];
  monteCarloResult?: MonteCarloResult;
  segments?: ValuationSegment[];
  assumptionTransparency?: AssumptionTransparency;
  valuation_id?: string; // UUID of the saved valuation in Supabase
  user_valuation_id?: string; // UUID of the user_valuations link record
  intrinsicPricingDTO?: any; // Intrinsic pricing data from peer comparison service (V1)
  intrinsicPricingV2DTO?: any; // Intrinsic pricing data from peer comparison service (V2 - improved R²)
  valuation_animation_base64?: string; // Base64-encoded Manim DCF animation video
}

// DCF Calculation Request Payload
export interface DCFCalculationRequest {
  isExpensesCapitalize?: boolean;
  hasOperatingLease?: boolean;
  companyRiskLevel?: 'Low' | 'Medium' | 'High';
  hasEmployeeOptions?: boolean;
  numberOfOptions?: number;
  averageStrikePrice?: number;
  averageMaturity?: number;
  stockPriceStdDev?: number;
  overrideAssumptionCostCapital?: OverrideAssumption;
  overrideAssumptionReturnOnCapital?: OverrideAssumption;
  overrideAssumptionProbabilityOfFailure?: OverrideAssumptionExtended;
  overrideAssumptionReinvestmentLag?: OverrideAssumption;
  overrideAssumptionTaxRate?: OverrideAssumption;
  overrideAssumptionNOL?: OverrideAssumption;
  overrideAssumptionRiskFreeRate?: OverrideAssumption;
  overrideAssumptionGrowthRate?: OverrideAssumption;
  overrideAssumptionCashPosition?: OverrideAssumptionCash;
  revenueNextYear?: number;
  operatingMarginNextYear?: number;
  compoundAnnualGrowth2_5?: number;
  targetPreTaxOperatingMargin?: number;
  convergenceYearMargin?: number;
  salesToCapitalYears1To5?: number;
  salesToCapitalYears6To10?: number;
  riskFreeRate?: number;
  initialCostCapital?: number;
  terminalGrowthRate?: number;
  basicInfoDataDTO?: BasicInfoDataDTO;
  financialDataDTO?: FinancialDataDTO;
  industry?: string;
  segments?: {
    segments: ValuationSegment[];
  };
  sectorOverrides?: Array<{
    sectorName: string;
    parameterType: string;
    value: number;
    adjustmentType: string;
    timeframe?: string;
  }>;
}

export interface OverrideAssumption {
  overrideCost: number;
  isOverride: boolean;
}

export interface OverrideAssumptionExtended extends OverrideAssumption {
  additionalInputValue: number;
  additionalRadioValue: string;
}

export interface OverrideAssumptionCash extends OverrideAssumption {
  additionalInputValue: number | null;
}
