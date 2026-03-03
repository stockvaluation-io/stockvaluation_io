export interface AnalysisInputs {
  // Financial Overview (readonly from API)
  financialData?: CompanyFinancialData;

  // User Assumptions
  assumptions: DCFAssumptions;

  // Advanced Settings
  advancedSettings: AdvancedDCFSettings;
}

export interface CompanyFinancialData {
  revenue: number;
  revenueGrowth: number;
  ebitda: number;
  ebitdaMargin: number;
  capex: number;
  workingCapital: number;
  debt: number;
  cash: number;
  sharesOutstanding: number;
  currency: string;
}

export interface DCFAssumptions {
  // Growth assumptions
  revenueGrowthYears1to5: number;
  revenueGrowthYears6to10: number;
  terminalGrowthRate: number;

  // Margin assumptions
  targetEbitdaMargin: number;

  // Investment assumptions
  capexAsPercentOfRevenue: number;
  workingCapitalGrowth: number;

  // Discount rate
  costOfEquity: number;
  costOfDebt: number;
  targetDebtRatio: number;
}

export interface AdvancedDCFSettings {
  // Sensitivity analysis
  enableSensitivityAnalysis: boolean;
  sensitivityVariables: string[];

  // Other settings
  taxRate: number;
  projectionYears: number;
  includeOptionsValue: boolean;
}

