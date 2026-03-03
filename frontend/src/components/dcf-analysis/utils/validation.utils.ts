/**
 * Validation utilities for DCF Analysis components
 */

import { CompanyData, AnalysisInputs, DCFState } from '../models';

export interface ValidationResult {
  isValid: boolean;
  errors: string[];
}

export interface FieldValidationResult {
  isValid: boolean;
  errorMessage?: string;
}

/**
 * Validate company data
 */
export function validateCompanyData(company: any): ValidationResult {
  const errors: string[] = [];

  if (!company) {
    errors.push('Company data is required');
    return { isValid: false, errors };
  }

  if (!company.symbol || typeof company.symbol !== 'string' || company.symbol.trim().length === 0) {
    errors.push('Company symbol is required and must be a non-empty string');
  }

  if (!company.name || typeof company.name !== 'string' || company.name.trim().length === 0) {
    errors.push('Company name is required and must be a non-empty string');
  }

  if (company.symbol && !/^[A-Z0-9.-]{1,10}$/i.test(company.symbol.trim())) {
    errors.push('Company symbol must contain only letters, numbers, dots, and hyphens (1-10 characters)');
  }

  if (company.price !== undefined && (typeof company.price !== 'number' || company.price < 0)) {
    errors.push('Company price must be a non-negative number');
  }

  if (company.marketCap !== undefined && (typeof company.marketCap !== 'number' || company.marketCap < 0)) {
    errors.push('Market cap must be a non-negative number');
  }

  return {
    isValid: errors.length === 0,
    errors
  };
}

/**
 * Validate analysis inputs
 */
export function validateAnalysisInputs(inputs: any): ValidationResult {
  const errors: string[] = [];

  if (!inputs) {
    errors.push('Analysis inputs are required');
    return { isValid: false, errors };
  }

  if (!inputs.assumptions) {
    errors.push('DCF assumptions are required');
  } else {
    const assumptionErrors = validateDCFAssumptions(inputs.assumptions);
    errors.push(...assumptionErrors);
  }

  if (!inputs.advancedSettings) {
    errors.push('Advanced settings are required');
  } else {
    const settingsErrors = validateAdvancedSettings(inputs.advancedSettings);
    errors.push(...settingsErrors);
  }

  return {
    isValid: errors.length === 0,
    errors
  };
}

/**
 * Validate DCF assumptions
 */
function validateDCFAssumptions(assumptions: any): string[] {
  const errors: string[] = [];

  // Growth rates validation
  if (typeof assumptions.revenueGrowthYears1to5 !== 'number' || assumptions.revenueGrowthYears1to5 < -50 || assumptions.revenueGrowthYears1to5 > 100) {
    errors.push('Revenue growth years 1-5 must be between -50% and 100%');
  }

  if (typeof assumptions.revenueGrowthYears6to10 !== 'number' || assumptions.revenueGrowthYears6to10 < -50 || assumptions.revenueGrowthYears6to10 > 50) {
    errors.push('Revenue growth years 6-10 must be between -50% and 50%');
  }

  if (typeof assumptions.terminalGrowthRate !== 'number' || assumptions.terminalGrowthRate < 0 || assumptions.terminalGrowthRate > 10) {
    errors.push('Terminal growth rate must be between 0% and 10%');
  }

  // Margin validation
  if (typeof assumptions.targetEbitdaMargin !== 'number' || assumptions.targetEbitdaMargin < -100 || assumptions.targetEbitdaMargin > 100) {
    errors.push('Target EBITDA margin must be between -100% and 100%');
  }

  // Investment assumptions
  if (typeof assumptions.capexAsPercentOfRevenue !== 'number' || assumptions.capexAsPercentOfRevenue < 0 || assumptions.capexAsPercentOfRevenue > 50) {
    errors.push('Capex as percent of revenue must be between 0% and 50%');
  }

  if (typeof assumptions.workingCapitalGrowth !== 'number' || assumptions.workingCapitalGrowth < -50 || assumptions.workingCapitalGrowth > 50) {
    errors.push('Working capital growth must be between -50% and 50%');
  }

  // Discount rate validation
  if (typeof assumptions.costOfEquity !== 'number' || assumptions.costOfEquity < 0 || assumptions.costOfEquity > 50) {
    errors.push('Cost of equity must be between 0% and 50%');
  }

  if (typeof assumptions.costOfDebt !== 'number' || assumptions.costOfDebt < 0 || assumptions.costOfDebt > 30) {
    errors.push('Cost of debt must be between 0% and 30%');
  }

  if (typeof assumptions.targetDebtRatio !== 'number' || assumptions.targetDebtRatio < 0 || assumptions.targetDebtRatio > 100) {
    errors.push('Target debt ratio must be between 0% and 100%');
  }

  return errors;
}

/**
 * Validate advanced settings
 */
function validateAdvancedSettings(settings: any): string[] {
  const errors: string[] = [];

  if (typeof settings.enableScenarioAnalysis !== 'boolean') {
    errors.push('Enable scenario analysis must be a boolean value');
  }

  if (typeof settings.enableSensitivityAnalysis !== 'boolean') {
    errors.push('Enable sensitivity analysis must be a boolean value');
  }

  if (typeof settings.taxRate !== 'number' || settings.taxRate < 0 || settings.taxRate > 100) {
    errors.push('Tax rate must be between 0% and 100%');
  }

  if (typeof settings.projectionYears !== 'number' || settings.projectionYears < 1 || settings.projectionYears > 20) {
    errors.push('Projection years must be between 1 and 20');
  }

  if (typeof settings.includeOptionsValue !== 'boolean') {
    errors.push('Include options value must be a boolean value');
  }

  if (settings.sensitivityVariables && !Array.isArray(settings.sensitivityVariables)) {
    errors.push('Sensitivity variables must be an array');
  }

  return errors;
}

/**
 * Validate ticker symbol format
 */
export function validateTickerSymbol(ticker: string): FieldValidationResult {
  if (!ticker || typeof ticker !== 'string') {
    return {
      isValid: false,
      errorMessage: 'Ticker symbol is required'
    };
  }

  const trimmedTicker = ticker.trim();
  
  if (trimmedTicker.length === 0) {
    return {
      isValid: false,
      errorMessage: 'Ticker symbol cannot be empty'
    };
  }

  if (trimmedTicker.length > 10) {
    return {
      isValid: false,
      errorMessage: 'Ticker symbol cannot exceed 10 characters'
    };
  }

  if (!/^[A-Z0-9.-]+$/i.test(trimmedTicker)) {
    return {
      isValid: false,
      errorMessage: 'Ticker symbol can only contain letters, numbers, dots, and hyphens'
    };
  }

  return { isValid: true };
}

/**
 * Validate percentage input
 */
export function validatePercentage(value: number, min: number = -100, max: number = 100, fieldName: string = 'Value'): FieldValidationResult {
  if (typeof value !== 'number' || isNaN(value)) {
    return {
      isValid: false,
      errorMessage: `${fieldName} must be a valid number`
    };
  }

  if (value < min || value > max) {
    return {
      isValid: false,
      errorMessage: `${fieldName} must be between ${min}% and ${max}%`
    };
  }

  return { isValid: true };
}

/**
 * Validate positive number
 */
export function validatePositiveNumber(value: number, fieldName: string = 'Value', allowZero: boolean = true): FieldValidationResult {
  if (typeof value !== 'number' || isNaN(value)) {
    return {
      isValid: false,
      errorMessage: `${fieldName} must be a valid number`
    };
  }

  const minValue = allowZero ? 0 : 0.01;
  if (value < minValue) {
    return {
      isValid: false,
      errorMessage: `${fieldName} must be ${allowZero ? 'non-negative' : 'positive'}`
    };
  }

  return { isValid: true };
}

/**
 * Validate DCF state before saving
 */
export function validateDCFState(state: any): ValidationResult {
  const errors: string[] = [];

  if (!state || typeof state !== 'object') {
    errors.push('Invalid state object');
    return { isValid: false, errors };
  }

  if (typeof state.isLoading !== 'boolean') {
    errors.push('Loading state must be a boolean');
  }

  if (state.error !== null && typeof state.error !== 'string') {
    errors.push('Error must be null or a string');
  }

  // Validate nested objects if present
  if (state.selectedCompany !== null) {
    const companyValidation = validateCompanyData(state.selectedCompany);
    if (!companyValidation.isValid) {
      errors.push(...companyValidation.errors.map(error => `Selected company: ${error}`));
    }
  }

  return {
    isValid: errors.length === 0,
    errors
  };
}

/**
 * Sanitize and normalize ticker symbol
 */
export function sanitizeTickerSymbol(ticker: string): string {
  if (!ticker || typeof ticker !== 'string') {
    return '';
  }

  return ticker.trim().toUpperCase().replace(/[^A-Z0-9.-]/g, '');
}

/**
 * Sanitize number input
 */
export function sanitizeNumberInput(value: any, defaultValue: number = 0): number {
  if (typeof value === 'number' && !isNaN(value)) {
    return value;
  }

  if (typeof value === 'string') {
    const parsed = parseFloat(value);
    if (!isNaN(parsed)) {
      return parsed;
    }
  }

  return defaultValue;
}