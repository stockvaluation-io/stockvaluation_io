// DCF Assumptions Form Data Interfaces
// Based on analysis of legacy company-drive and user-input components

export interface DCFAssumptionsFormData {
  // Core DCF Parameters (from defaultFields in Model.model.ts)
  coreAssumptions: CoreDCFAssumptions;
  
  // Risk Assessment & Employee Options (from user-input questions)
  riskAssessment: RiskAssessment;
  
  // Advanced Override Assumptions (from user-input tabs)
  overrides: AdvancedOverrides;
}

export interface CoreDCFAssumptions {
  revenueNextYear: number;                    // 0-100%, Revenue growth rate for next year
  operatingMarginNextYear: number;            // 0-99%, Operating Margin for next year
  compoundAnnualGrowth2_5: number;           // 0-100%, Compounded annual revenue growth rate - years 2-5
  targetPreTaxOperatingMargin: number;       // 0-99%, Target pre-tax operating margin
  salesToCapitalYears1To5: number;           // 0-10, Sales to capital ratio (for years 1-5)
  salesToCapitalYears6To10: number;          // 0-10, Sales to capital ratio (for years 6-10)
  riskFreeRate: number;                      // Read-only from API
  initialCostCapital: number;                // Read-only from API
}

export interface RiskAssessment {
  isExpensesCapitalize: boolean;             // Do you have R & D expenses to capitalize?
  hasOperatingLease: boolean;                // Do you have operating lease commitments?
  companyRiskLevel: CompanyRiskLevel;        // How risky is your company?
  hasEmployeeOptions: boolean;               // Do you have employee options outstanding?
  
  // Employee Options Details (only if hasEmployeeOptions = true)
  employeeOptions?: EmployeeOptionsData;
}

export interface EmployeeOptionsData {
  numberOfOptions: number;                   // No of options outstanding
  averageStrikePrice: number;               // Avg strike price
  averageMaturity: number;                  // Avg maturity
  stockPriceStdDev: number;                 // Standard deviation on stock price
}

export type CompanyRiskLevel = 'Very Low' | 'Low' | 'Medium' | 'High' | 'Very High';

export interface AdvancedOverrides {
  overrideAssumptionCostCapital: DCFOverrideAssumption;
  overrideAssumptionReturnOnCapital: DCFOverrideAssumption;
  overrideAssumptionProbabilityOfFailure: DCFFailureOverride;
  overrideAssumptionReinvestmentLag: DCFOverrideAssumption;
  overrideAssumptionTaxRate: DCFOverrideAssumption;
  overrideAssumptionNOL: DCFOverrideAssumption;
  overrideAssumptionRiskFreeRate: DCFOverrideAssumption;
  overrideAssumptionGrowthRate: DCFOverrideAssumption;
  overrideAssumptionCashPosition: DCFCashOverride;
}

export interface DCFOverrideAssumption {
  isOverride: boolean;
  overrideCost: number;
}

export interface DCFFailureOverride extends DCFOverrideAssumption {
  additionalInputValue: number;              // Distress proceeds as percentage
  additionalRadioValue: 'V' | 'B';          // Value (V) or Book (B)
}

export interface DCFCashOverride extends DCFOverrideAssumption {
  additionalInputValue: number;              // Average tax rate of foreign markets
}

// Default field configuration (from Model.model.ts)
export interface DCFFieldConfig {
  key: keyof CoreDCFAssumptions;
  name: string;
  value: number;
  max: number;
  isVisible: boolean;
  toolTip: string;
  isReadOnly?: boolean;
}

// Override tab configuration
export interface OverrideTabConfig {
  key: keyof AdvancedOverrides;
  header: string;
  question: string;
  inputLabel: string;
  additionalInputLabel?: string;
  additionalRadioLabel?: string;
  options?: string[];
  hasAdditionalInput?: boolean;
  hasAdditionalRadio?: boolean;
}

// Form validation result
export interface FormValidationResult {
  isValid: boolean;
  errors: FormValidationError[];
}

export interface FormValidationError {
  field: string;
  message: string;
  severity: 'error' | 'warning';
}

// Form persistence data
export interface FormSessionData {
  [ticker: string]: {
    formData: DCFAssumptionsFormData;
    timestamp: number;
    version: string;
  };
}

// Legacy payload mapping (for API compatibility)
export interface LegacyDCFPayload {
  // Core assumptions (direct mapping)
  revenueNextYear: number;
  operatingMarginNextYear: number;
  compoundAnnualGrowth2_5: number;
  targetPreTaxOperatingMargin: number;
  salesToCapitalYears1To5: number;
  salesToCapitalYears6To10: number;
  riskFreeRate: number;
  initialCostCapital: number;
  
  // Risk assessment (question mapping)
  isExpensesCapitalize: boolean;
  hasOperatingLease: boolean;
  companyRiskLevel: CompanyRiskLevel;
  hasEmployeeOptions: boolean;
  numberOfOptions?: number;
  averageStrikePrice?: number;
  averageMaturity?: number;
  stockPriceStdDev?: number;
  
  // Override assumptions (tab mapping)
  overrideAssumptionCostCapital: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionReturnOnCapital: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionProbabilityOfFailure: {
    isOverride: boolean;
    overrideCost: number;
    additionalInputValue: number;
    additionalRadioValue: string;
  };
  overrideAssumptionReinvestmentLag: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionTaxRate: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionNOL: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionRiskFreeRate: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionGrowthRate: {
    isOverride: boolean;
    overrideCost: number;
  };
  overrideAssumptionCashPosition: {
    isOverride: boolean;
    overrideCost: number;
    additionalInputValue: number;
  };
}