import { Injectable } from '@angular/core';
import { LoggerService } from '../../../core/services';
import { 
  FinancialDataDTO, 
  CompanyDriveDataDTO, 
  BasicInfoDataDTO 
} from '../models/api-response.interface';

/**
 * DCF Financial Defaults Service
 * 
 * Calculates intelligent default values for DCF assumptions based on actual company
 * financial data instead of static fallbacks. Implements the business logic from
 * the original valuation system with enhanced data-driven methodology.
 * 
 * Hierarchy of default calculation:
 * 1. API-provided defaults (companyDriveDataDTO) - preferred
 * 2. Calculated from financial statements (financialDataDTO) - fallback
 * 3. Static industry defaults - last resort
 */
@Injectable({
  providedIn: 'root'
})
export class DCFFinancialDefaultsService {
  
  constructor(private logger: LoggerService) {}

  /**
   * Calculate revenue growth default for next year
   * Business Rule: Use analyst projections OR calculate from recent performance
   * 
   * @param financialData - Company financial statements
   * @param driveData - API-provided default values
   * @returns Revenue growth rate as percentage (0-100)
   */
  calculateRevenueGrowthDefault(
    financialData: FinancialDataDTO | null, 
    driveData: CompanyDriveDataDTO | null
  ): number {
    // Primary: Use API-provided analyst projection
    if (driveData?.revenueNextYear && driveData.revenueNextYear > 0) {
      // API returns decimal format (0.20 = 20%), convert to percentage for display
      const apiDefault = driveData.revenueNextYear * 100;
      this.logger.debug(`Using API revenue growth default: ${apiDefault}%`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Calculate from TTM vs LTM performance
    if (financialData?.revenueTTM && financialData?.revenueLTM && 
        financialData.revenueTTM > 0 && financialData.revenueLTM > 0) {
      
      const calculatedGrowth = ((financialData.revenueTTM / financialData.revenueLTM) - 1) * 100;
      
      // Apply sustainability cap for extreme values (max 50% for reasonableness)
      const sustainableGrowth = Math.max(-30, Math.min(calculatedGrowth, 50));
      
      this.logger.debug(
        `Calculated revenue growth from financial data: ${calculatedGrowth.toFixed(2)}% (capped: ${sustainableGrowth.toFixed(2)}%)`, 
        undefined, 
        'DCFFinancialDefaultsService'
      );
      
      return Math.round(sustainableGrowth * 100) / 100; // Round to 2 decimal places
    }

    // Fallback: Static default
    this.logger.warn('Using static revenue growth fallback (10%)', undefined, 'DCFFinancialDefaultsService');
    return 10;
  }

  /**
   * Calculate operating margin default for next year
   * Business Rule: Use current year margin as baseline
   * 
   * @param financialData - Company financial statements
   * @param driveData - API-provided default values
   * @returns Operating margin as percentage (0-99)
   */
  calculateOperatingMarginDefault(
    financialData: FinancialDataDTO | null,
    driveData: CompanyDriveDataDTO | null
  ): number {
    // Primary: Use API-provided default
    if (driveData?.operatingMarginNextYear && driveData.operatingMarginNextYear > 0) {
      // API returns decimal format (0.0877 = 8.77%), convert to percentage for display
      const apiDefault = driveData.operatingMarginNextYear * 100;
      this.logger.debug(`Using API operating margin default: ${apiDefault}%`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Calculate current operating margin from TTM data
    if (financialData?.operatingIncomeTTM && financialData?.revenueTTM && 
        financialData.revenueTTM > 0) {
      
      const currentMargin = (financialData.operatingIncomeTTM / financialData.revenueTTM) * 100;
      
      // Apply reasonable bounds (0-50% operating margin)
      const boundedMargin = Math.max(0, Math.min(currentMargin, 50));
      
      this.logger.debug(
        `Calculated operating margin from TTM data: ${currentMargin.toFixed(2)}% (bounded: ${boundedMargin.toFixed(2)}%)`,
        undefined,
        'DCFFinancialDefaultsService'
      );
      
      return Math.round(boundedMargin * 100) / 100; // Round to 2 decimal places
    }

    // Fallback: Static default
    this.logger.warn('Using static operating margin fallback (15%)', undefined, 'DCFFinancialDefaultsService');
    return 15;
  }

  /**
   * Calculate compound annual growth rate for years 2-5
   * Business Rule: Blend current performance with sustainability considerations
   * 
   * @param financialData - Company financial statements
   * @param driveData - API-provided default values
   * @param currentGrowthRate - Year 1 growth rate for context
   * @returns Growth rate as percentage (0-100)
   */
  calculateGrowthRate2to5Default(
    financialData: FinancialDataDTO | null,
    driveData: CompanyDriveDataDTO | null,
    currentGrowthRate: number
  ): number {
    // Primary: Use API-provided default
    if (driveData?.compoundAnnualGrowth2_5 && driveData.compoundAnnualGrowth2_5 > 0) {
      // API returns decimal format (0.1946 = 19.46%), convert to percentage for display
      const apiDefault = driveData.compoundAnnualGrowth2_5 * 100;
      this.logger.debug(`Using API growth 2-5 default: ${apiDefault}%`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Conservative estimate based on current growth
    // Apply decay factor for sustainability (80% of current growth)
    const conservativeGrowth = currentGrowthRate * 0.8;
    
    // Apply bounds (min 2%, max 25% for years 2-5)
    const boundedGrowth = Math.max(2, Math.min(conservativeGrowth, 25));
    
    this.logger.debug(
      `Calculated growth 2-5 from current growth: ${conservativeGrowth.toFixed(2)}% (bounded: ${boundedGrowth.toFixed(2)}%)`,
      undefined,
      'DCFFinancialDefaultsService'
    );
    
    return Math.round(boundedGrowth * 100) / 100; // Round to 2 decimal places
  }

  /**
   * Calculate target pre-tax operating margin
   * Business Rule: Industry average with company context
   * 
   * @param financialData - Company financial statements
   * @param driveData - API-provided default values
   * @param currentMargin - Current operating margin for context
   * @returns Target margin as percentage (0-99)
   */
  calculateTargetMarginDefault(
    financialData: FinancialDataDTO | null,
    driveData: CompanyDriveDataDTO | null,
    currentMargin: number
  ): number {
    // Primary: Use API-provided industry average
    if (driveData?.targetPreTaxOperatingMargin && driveData.targetPreTaxOperatingMargin > 0) {
      // API returns decimal format (0.0877 = 8.77%), convert to percentage for display
      const apiDefault = driveData.targetPreTaxOperatingMargin * 100;
      this.logger.debug(`Using API target margin default: ${apiDefault}%`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Conservative improvement on current margin
    // If current margin is reasonable, use it as target; otherwise, use industry-typical 20%
    const targetMargin = currentMargin >= 5 && currentMargin <= 40 ? currentMargin : 20;
    
    this.logger.debug(
      `Calculated target margin based on current (${currentMargin.toFixed(2)}%): ${targetMargin.toFixed(2)}%`,
      undefined,
      'DCFFinancialDefaultsService'
    );
    
    return Math.round(targetMargin * 100) / 100; // Round to 2 decimal places
  }

  /**
   * Calculate sales to capital ratio for years 1-5
   * Business Rule: 90th percentile efficiency for growth phase
   * 
   * @param financialData - Company financial statements
   * @param driveData - API-provided default values
   * @returns Sales to capital ratio (0-10)
   */
  calculateSalesToCapital1to5Default(
    financialData: FinancialDataDTO | null,
    driveData: CompanyDriveDataDTO | null
  ): number {
    // Primary: Use API-provided default
    if (driveData?.salesToCapitalYears1To5 && driveData.salesToCapitalYears1To5 > 0) {
      // API might return very small decimals, check if we need to scale up
      let apiDefault = driveData.salesToCapitalYears1To5;
      
      // If the value is very small (< 0.1), it might need scaling
      if (apiDefault < 0.1) {
        apiDefault = apiDefault * 100; // Scale up small decimals
      }
      
      this.logger.debug(`Using API sales/capital 1-5 default: ${apiDefault}`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Calculate from revenue and capital efficiency
    // For growth companies, assume efficient capital deployment (2.5x industry average)
    const defaultRatio = 2.5;
    
    this.logger.debug(
      `Using calculated sales/capital 1-5 default: ${defaultRatio}`,
      undefined,
      'DCFFinancialDefaultsService'
    );
    
    return defaultRatio;
  }

  /**
   * Calculate sales to capital ratio for years 6-10
   * Business Rule: Industry average for mature phase
   * 
   * @param financialData - Company financial statements
   * @param driveData - API-provided default values
   * @returns Sales to capital ratio (0-10)
   */
  calculateSalesToCapital6to10Default(
    financialData: FinancialDataDTO | null,
    driveData: CompanyDriveDataDTO | null
  ): number {
    // Primary: Use API-provided default
    if (driveData?.salesToCapitalYears6To10 && driveData.salesToCapitalYears6To10 > 0) {
      // API might return very small decimals, check if we need to scale up
      let apiDefault = driveData.salesToCapitalYears6To10;
      
      // If the value is very small (< 0.1), it might need scaling
      if (apiDefault < 0.1) {
        apiDefault = apiDefault * 100; // Scale up small decimals
      }
      
      this.logger.debug(`Using API sales/capital 6-10 default: ${apiDefault}`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Use industry average for mature companies
    const defaultRatio = 1.5;
    
    this.logger.debug(
      `Using calculated sales/capital 6-10 default: ${defaultRatio}`,
      undefined,
      'DCFFinancialDefaultsService'
    );
    
    return defaultRatio;
  }

  /**
   * Calculate risk-free rate default
   * Business Rule: Currency-specific 10-year bond rate
   * 
   * @param basicInfo - Company basic information
   * @param driveData - API-provided default values
   * @returns Risk-free rate as percentage (0-100)
   */
  calculateRiskFreeRateDefault(
    basicInfo: BasicInfoDataDTO | null,
    driveData: CompanyDriveDataDTO | null
  ): number {
    // Primary: Use API-provided rate (currency-specific)
    if (driveData?.riskFreeRate && driveData.riskFreeRate > 0) {
      // API might return as percentage already, check the value range
      let apiDefault = driveData.riskFreeRate;
      
      // If value is very small (< 1), it's in decimal format, convert to percentage
      if (apiDefault < 1) {
        apiDefault = apiDefault * 100;
      }
      
      this.logger.debug(`Using API risk-free rate default: ${apiDefault}%`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Default rates by currency
    const currency = basicInfo?.currency?.toUpperCase();
    let defaultRate = 4.5; // USD default
    
    switch (currency) {
      case 'EUR':
        defaultRate = 3.0;
        break;
      case 'GBP':
        defaultRate = 4.0;
        break;
      case 'JPY':
        defaultRate = 0.5;
        break;
      case 'CAD':
        defaultRate = 3.5;
        break;
      case 'AUD':
        defaultRate = 4.0;
        break;
      default:
        defaultRate = 4.5; // USD and others
    }
    
    this.logger.debug(
      `Using currency-specific risk-free rate for ${currency}: ${defaultRate}%`,
      undefined,
      'DCFFinancialDefaultsService'
    );
    
    return defaultRate;
  }

  /**
   * Calculate initial cost of capital default
   * Business Rule: Risk-free rate + equity risk premium based on beta
   * 
   * @param basicInfo - Company basic information (includes beta)
   * @param driveData - API-provided default values
   * @param riskFreeRate - Risk-free rate for calculation
   * @returns Cost of capital as percentage (0-100)
   */
  calculateInitialCostCapitalDefault(
    basicInfo: BasicInfoDataDTO | null,
    driveData: CompanyDriveDataDTO | null,
    riskFreeRate: number
  ): number {
    // Primary: Use API-provided default
    if (driveData?.initialCostCapital && driveData.initialCostCapital > 0) {
      // API might return as percentage already, check the value range
      let apiDefault = driveData.initialCostCapital;
      
      // If value is very small (< 1), it's in decimal format, convert to percentage
      if (apiDefault < 1) {
        apiDefault = apiDefault * 100;
      }
      
      this.logger.debug(`Using API cost of capital default: ${apiDefault}%`, undefined, 'DCFFinancialDefaultsService');
      return Math.round(apiDefault * 100) / 100; // Round to 2 decimal places
    }

    // Secondary: Calculate using CAPM (Risk-free rate + Beta * Market risk premium)
    const beta = basicInfo?.beta || 1.0; // Default beta of 1.0
    const marketRiskPremium = 6.0; // Standard market risk premium
    
    const costOfEquity = riskFreeRate + (beta * marketRiskPremium);
    
    // Apply reasonable bounds (5% - 20%)
    const boundedCost = Math.max(5, Math.min(costOfEquity, 20));
    
    this.logger.debug(
      `Calculated cost of capital: ${costOfEquity.toFixed(2)}% (bounded: ${boundedCost.toFixed(2)}%) [RF: ${riskFreeRate}%, Beta: ${beta}]`,
      undefined,
      'DCFFinancialDefaultsService'
    );
    
    return Math.round(boundedCost * 100) / 100; // Round to 2 decimal places
  }

  /**
   * Get comprehensive DCF defaults using financial data intelligence
   * Main entry point that orchestrates all default calculations
   * 
   * @param companyData - Complete company data from API
   * @returns Object with all calculated defaults
   */
  getIntelligentDefaults(companyData: any): {
    revenueNextYear: number;
    operatingMarginNextYear: number;
    compoundAnnualGrowth2_5: number;
    targetPreTaxOperatingMargin: number;
    salesToCapitalYears1To5: number;
    salesToCapitalYears6To10: number;
    riskFreeRate: number;
    initialCostCapital: number;
  } {
    const basicInfo = companyData?.basicInfoDataDTO || null;
    const financialData = companyData?.financialDataDTO || null;
    const driveData = companyData?.companyDriveDataDTO || null;

    this.logger.info(
      `Calculating intelligent defaults for ${basicInfo?.companyName || 'Unknown Company'}`,
      undefined,
      'DCFFinancialDefaultsService'
    );

    // Calculate each default using the business logic
    const revenueNextYear = this.calculateRevenueGrowthDefault(financialData, driveData);
    const operatingMarginNextYear = this.calculateOperatingMarginDefault(financialData, driveData);
    const compoundAnnualGrowth2_5 = this.calculateGrowthRate2to5Default(financialData, driveData, revenueNextYear);
    const targetPreTaxOperatingMargin = this.calculateTargetMarginDefault(financialData, driveData, operatingMarginNextYear);
    const salesToCapitalYears1To5 = this.calculateSalesToCapital1to5Default(financialData, driveData);
    const salesToCapitalYears6To10 = this.calculateSalesToCapital6to10Default(financialData, driveData);
    const riskFreeRate = this.calculateRiskFreeRateDefault(basicInfo, driveData);
    const initialCostCapital = this.calculateInitialCostCapitalDefault(basicInfo, driveData, riskFreeRate);

    const result = {
      revenueNextYear,
      operatingMarginNextYear,
      compoundAnnualGrowth2_5,
      targetPreTaxOperatingMargin,
      salesToCapitalYears1To5,
      salesToCapitalYears6To10,
      riskFreeRate,
      initialCostCapital
    };

    this.logger.info(
      'Intelligent defaults calculated successfully',
      result,
      'DCFFinancialDefaultsService'
    );

    return result;
  }
}