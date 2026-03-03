import { Injectable } from '@angular/core';
import { 
  DCFAssumptionsFormData, 
  FormSessionData, 
  CoreDCFAssumptions,
  RiskAssessment,
  AdvancedOverrides,
  CompanyRiskLevel
} from '../models';
import { LoggerService, StorageService } from '../../../core/services';
import { DCFFinancialDefaultsService } from './dcf-financial-defaults.service';

@Injectable({
  providedIn: 'root'
})
export class DCFAssumptionsPersistenceService {
  private readonly STORAGE_KEY = 'dcf_assumptions_session';
  private readonly VERSION = '1.0.0';
  private readonly SESSION_TIMEOUT = 24 * 60 * 60 * 1000; // 24 hours

  constructor(
    private logger: LoggerService,
    private storageService: StorageService,
    private financialDefaults: DCFFinancialDefaultsService
  ) {}

  /**
   * Save form data for a specific ticker
   */
  saveFormData(ticker: string, formData: DCFAssumptionsFormData): void {
    try {
      const sessionData = this.getSessionData();
      sessionData[ticker.toUpperCase()] = {
        formData,
        timestamp: Date.now(),
        version: this.VERSION
      };
      
      this.storageService.setItem(this.STORAGE_KEY, sessionData);
    } catch (error) {
      this.logger.warn('Failed to save DCF assumptions form data', error, 'DCFAssumptionsPersistenceService');
    }
  }

  /**
   * Load form data for a specific ticker
   */
  loadFormData(ticker: string): DCFAssumptionsFormData | null {
    try {
      const sessionData = this.getSessionData();
      const tickerData = sessionData[ticker.toUpperCase()];
      
      if (!tickerData) {
        return null;
      }
      
      // Check if data is expired
      if (this.isDataExpired(tickerData.timestamp)) {
        this.removeFormData(ticker);
        return null;
      }
      
      return this.normalizeLoadedFormData(tickerData.formData);
    } catch (error) {
      this.logger.warn('Failed to load DCF assumptions form data', error, 'DCFAssumptionsPersistenceService');
      return null;
    }
  }

  /**
   * Remove form data for a specific ticker
   */
  removeFormData(ticker: string): void {
    try {
      const sessionData = this.getSessionData();
      delete sessionData[ticker.toUpperCase()];
      this.storageService.setItem(this.STORAGE_KEY, sessionData);
    } catch (error) {
      this.logger.warn('Failed to remove DCF assumptions form data', error, 'DCFAssumptionsPersistenceService');
    }
  }

  /**
   * Clear all stored form data
   */
  clearAllFormData(): void {
    try {
      this.storageService.removeItem(this.STORAGE_KEY);
    } catch (error) {
      this.logger.warn('Failed to clear DCF assumptions form data', error, 'DCFAssumptionsPersistenceService');
    }
  }

  /**
   * Check if form data exists for a ticker
   */
  hasFormData(ticker: string): boolean {
    const formData = this.loadFormData(ticker);
    return formData !== null;
  }

  /**
   * Get default form data structure from company API data
   */
  getDefaultFormData(companyData: any): DCFAssumptionsFormData {
    return {
      coreAssumptions: this.mapCoreAssumptions(companyData),
      riskAssessment: this.getDefaultRiskAssessment(),
      overrides: this.getDefaultOverrides()
    };
  }

  /**
   * Create form data with user modifications merged with defaults
   */
  mergeWithDefaults(
    defaults: DCFAssumptionsFormData, 
    userModifications: Partial<DCFAssumptionsFormData>
  ): DCFAssumptionsFormData {
    return {
      coreAssumptions: { 
        ...defaults.coreAssumptions, 
        ...userModifications.coreAssumptions 
      },
      riskAssessment: { 
        ...defaults.riskAssessment, 
        ...userModifications.riskAssessment 
      },
      overrides: { 
        ...defaults.overrides, 
        ...userModifications.overrides 
      }
    };
  }

  /**
   * Map company API data to core DCF assumptions with intelligent defaults
   * Enhanced to use actual financial data instead of static fallbacks
   */
  private mapCoreAssumptions(companyData: any): CoreDCFAssumptions {
    try {
      // Use the enhanced financial defaults service for intelligent calculation
      const intelligentDefaults = this.financialDefaults.getIntelligentDefaults(companyData);
      
      this.logger.info(
        'DCF assumptions mapped using intelligent defaults',
        {
          company: companyData?.basicInfoDataDTO?.companyName,
          hasFinancialData: !!companyData?.financialDataDTO,
          hasDriveData: !!companyData?.companyDriveDataDTO,
          defaults: intelligentDefaults
        },
        'DCFAssumptionsPersistenceService'
      );
      
      return intelligentDefaults;
      
    } catch (error) {
      // Fallback to original logic if intelligent defaults fail
      this.logger.warn(
        'Failed to calculate intelligent defaults, using fallback logic',
        error,
        'DCFAssumptionsPersistenceService'
      );
      
      return this.mapCoreAssumptionsFallback(companyData);
    }
  }

  /**
   * Fallback method using original static defaults logic
   * Preserved for error recovery scenarios
   */
  private mapCoreAssumptionsFallback(companyData: any): CoreDCFAssumptions {
    const driveData = companyData?.companyDriveDataDTO || {};
    
    const result = {
      revenueNextYear: this.normalizePercentValue(this.parseValue(driveData.revenueNextYear)) || 10,
      operatingMarginNextYear: this.normalizePercentValue(this.parseValue(driveData.operatingMarginNextYear)) || 15,
      compoundAnnualGrowth2_5: this.normalizePercentValue(this.parseValue(driveData.compoundAnnualGrowth2_5)) || 8,
      targetPreTaxOperatingMargin: this.normalizePercentValue(this.parseValue(driveData.targetPreTaxOperatingMargin)) || 20,
      salesToCapitalYears1To5: this.normalizeMultipleValue(this.parseValue(driveData.salesToCapitalYears1To5)) || 2.5,
      salesToCapitalYears6To10: this.normalizeMultipleValue(this.parseValue(driveData.salesToCapitalYears6To10)) || 1.5,
      riskFreeRate: this.normalizePercentValue(this.parseValue(driveData.riskFreeRate)) || 4.5,
      initialCostCapital: this.normalizePercentValue(this.parseValue(driveData.initialCostCapital)) || 10.5
    };
    
    this.logger.debug('Using fallback static defaults', result, 'DCFAssumptionsPersistenceService');
    return result;
  }

  /**
   * Get default risk assessment values
   */
  private getDefaultRiskAssessment(): RiskAssessment {
    return {
      isExpensesCapitalize: true,
      hasOperatingLease: false,
      companyRiskLevel: 'Medium' as CompanyRiskLevel,
      hasEmployeeOptions: false,
      employeeOptions: {
        numberOfOptions: 0,
        averageStrikePrice: 0,
        averageMaturity: 0,
        stockPriceStdDev: 0
      }
    };
  }

  /**
   * Get default override assumptions
   */
  private getDefaultOverrides(): AdvancedOverrides {
    return {
      overrideAssumptionCostCapital: { isOverride: false, overrideCost: 0 },
      overrideAssumptionReturnOnCapital: { isOverride: false, overrideCost: 0 },
      overrideAssumptionProbabilityOfFailure: { 
        isOverride: false, 
        overrideCost: 0, 
        additionalInputValue: 0, 
        additionalRadioValue: 'V' 
      },
      overrideAssumptionReinvestmentLag: { isOverride: false, overrideCost: 0 },
      overrideAssumptionTaxRate: { isOverride: false, overrideCost: 0 },
      overrideAssumptionNOL: { isOverride: false, overrideCost: 0 },
      overrideAssumptionRiskFreeRate: { isOverride: false, overrideCost: 0 },
      overrideAssumptionGrowthRate: { isOverride: false, overrideCost: 0 },
      overrideAssumptionCashPosition: { 
        isOverride: false, 
        overrideCost: 0, 
        additionalInputValue: 0 
      }
    };
  }

  /**
   * Get all session data from localStorage
   */
  private getSessionData(): FormSessionData {
    try {
      const data = this.storageService.getItem<FormSessionData>(this.STORAGE_KEY, 'local', {});
      return data || {};
    } catch (error) {
      this.logger.warn('Failed to parse DCF assumptions session data', error, 'DCFAssumptionsPersistenceService');
      return {};
    }
  }

  /**
   * Check if data timestamp is expired
   */
  private isDataExpired(timestamp: number): boolean {
    return Date.now() - timestamp > this.SESSION_TIMEOUT;
  }

  /**
   * Parse numeric value with fallback
   */
  private parseValue(value: any): number {
    const parsed = parseFloat(value);
    return isNaN(parsed) ? 0 : parsed;
  }

  private normalizePercentValue(value: number): number {
    if (!Number.isFinite(value) || value === 0) {
      return 0;
    }
    if (Math.abs(value) <= 1) {
      return value * 100;
    }
    if (Math.abs(value) > 100) {
      return value / 100;
    }
    return value;
  }

  private normalizeMultipleValue(value: number): number {
    if (!Number.isFinite(value) || value === 0) {
      return 0;
    }
    if (Math.abs(value) > 10) {
      return value / 100;
    }
    return value;
  }

  private normalizeLoadedFormData(formData: DCFAssumptionsFormData): DCFAssumptionsFormData {
    if (!formData?.coreAssumptions) {
      return formData;
    }

    const core = formData.coreAssumptions;
    return {
      ...formData,
      coreAssumptions: {
        ...core,
        revenueNextYear: this.normalizePercentValue(this.parseValue(core.revenueNextYear)),
        operatingMarginNextYear: this.normalizePercentValue(this.parseValue(core.operatingMarginNextYear)),
        compoundAnnualGrowth2_5: this.normalizePercentValue(this.parseValue(core.compoundAnnualGrowth2_5)),
        targetPreTaxOperatingMargin: this.normalizePercentValue(this.parseValue(core.targetPreTaxOperatingMargin)),
        salesToCapitalYears1To5: this.normalizeMultipleValue(this.parseValue(core.salesToCapitalYears1To5)),
        salesToCapitalYears6To10: this.normalizeMultipleValue(this.parseValue(core.salesToCapitalYears6To10)),
        riskFreeRate: this.normalizePercentValue(this.parseValue(core.riskFreeRate)),
        initialCostCapital: this.normalizePercentValue(this.parseValue(core.initialCostCapital)),
      }
    };
  }

  /**
   * Clean up expired data (can be called periodically)
   */
  cleanupExpiredData(): void {
    try {
      const sessionData = this.getSessionData();
      const cleanedData: FormSessionData = {};
      
      Object.keys(sessionData).forEach(ticker => {
        if (!this.isDataExpired(sessionData[ticker].timestamp)) {
          cleanedData[ticker] = sessionData[ticker];
        }
      });
      
      this.storageService.setItem(this.STORAGE_KEY, cleanedData);
    } catch (error) {
      this.logger.warn('Failed to cleanup expired DCF assumptions data', error, 'DCFAssumptionsPersistenceService');
    }
  }

  /**
   * Get statistics about stored data
   */
  getStorageStats(): { totalTickers: number; totalSize: string; oldestEntry: Date | null } {
    try {
      const sessionData = this.getSessionData();
      const tickers = Object.keys(sessionData);
      const dataString = JSON.stringify(sessionData);
      const sizeInBytes = new Blob([dataString]).size;
      const sizeInKB = (sizeInBytes / 1024).toFixed(2);
      
      let oldestTimestamp = Infinity;
      tickers.forEach(ticker => {
        if (sessionData[ticker].timestamp < oldestTimestamp) {
          oldestTimestamp = sessionData[ticker].timestamp;
        }
      });
      
      return {
        totalTickers: tickers.length,
        totalSize: `${sizeInKB} KB`,
        oldestEntry: oldestTimestamp === Infinity ? null : new Date(oldestTimestamp)
      };
    } catch (error) {
      this.logger.warn('Failed to get storage stats', error, 'DCFAssumptionsPersistenceService');
      return { totalTickers: 0, totalSize: '0 KB', oldestEntry: null };
    }
  }
}
