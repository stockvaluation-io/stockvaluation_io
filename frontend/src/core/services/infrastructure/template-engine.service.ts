import { Injectable, Inject } from '@angular/core';
import { environment } from '../../../env/environment';
import { ValuationConfig, ValuationStatus } from '../../config/valuation.config';
import { ValuationSourceFactory } from '../../strategies/valuation-data-source';

export interface TemplateData {
  [key: string]: string | number | boolean | null | undefined;
}

export interface TemplateOptions {
  fallbackValue?: string;
  throwOnMissingKeys?: boolean;
  formatNumbers?: boolean;
  currencySymbol?: string;
}

@Injectable({
  providedIn: 'root'
})
export class TemplateEngineService {
  
  private readonly valuationConfig: ValuationConfig;

  constructor(private valuationSourceFactory: ValuationSourceFactory) {
    this.valuationConfig = environment.valuation;
  }

  /**
   * Render a template string with provided data
   * @param template Template string with placeholders like {key}
   * @param data Data object with values to replace placeholders
   * @param options Optional configuration for rendering
   * @returns Rendered string with placeholders replaced
   */
  render(template: string, data: TemplateData, options: TemplateOptions = {}): string {
    if (!template || typeof template !== 'string') {
      return options.fallbackValue || '';
    }

    const opts: Required<TemplateOptions> = {
      fallbackValue: '',
      throwOnMissingKeys: false,
      formatNumbers: true,
      currencySymbol: '$',
      ...options
    };

    // Replace placeholders using regex
    return template.replace(/\{([^}]+)\}/g, (match, key) => {
      const trimmedKey = key.trim();
      
      if (!(trimmedKey in data)) {
        if (opts.throwOnMissingKeys) {
          throw new Error(`Template key '${trimmedKey}' not found in data`);
        }
        return opts.fallbackValue;
      }

      const value = data[trimmedKey];
      
      if (value === null || value === undefined) {
        return opts.fallbackValue;
      }

      // Handle different value types
      if (typeof value === 'number') {
        return this.formatNumber(value, trimmedKey, opts);
      }

      if (typeof value === 'boolean') {
        return value.toString();
      }

      return String(value);
    });
  }

  /**
   * Format currency values with proper symbols and decimal places
   * @param value Numeric value to format
   * @param currency Currency code or symbol
   * @returns Formatted currency string
   */
  formatCurrency(value: number, currency: string = 'USD'): string {
    if (typeof value !== 'number' || isNaN(value)) {
      return '--';
    }

    // Handle different currency formats
    const currencySymbols: Record<string, string> = {
      'USD': '$',
      'EUR': '€',
      'GBP': '£',
      'JPY': '¥',
      'CAD': 'C$',
      'AUD': 'A$'
    };

    const symbol = currencySymbols[currency] || currency;
    
    // Format with appropriate decimal places
    if (value >= 1000000000) {
      return `${symbol}${(value / 1000000000).toFixed(2)}B`;
    } else if (value >= 1000000) {
      return `${symbol}${(value / 1000000).toFixed(2)}M`;
    } else if (value >= 1000) {
      return `${symbol}${(value / 1000).toFixed(2)}K`;
    } else {
      return `${symbol}${value.toFixed(2)}`;
    }
  }

  /**
   * Format percentage values
   * @param value Decimal value (e.g., 0.15 for 15%)
   * @param decimalPlaces Number of decimal places to show
   * @returns Formatted percentage string
   */
  formatPercentage(value: number, decimalPlaces: number = 2): string {
    if (typeof value !== 'number' || isNaN(value)) {
      return '--';
    }
    
    return `${(value * 100).toFixed(decimalPlaces)}%`;
  }

  /**
   * Generate template data for DCF results
   * @param results Valuation results data
   * @param company Company information
   * @returns Template data object with all available placeholders
   */
  generateDCFTemplateData(results: any, company: any): TemplateData {
    // Use valuation source factory to get valuation status
    const valuationStatus = this.valuationSourceFactory.getValuationStatus(results);
    
    // Get legacy upside for backward compatibility
    const upside = results.upside || 0;
    const fallbackIsPositive = upside > 0;
    
    return {
      // Basic company info
      symbol: company.symbol || '',
      companyName: company.name || '',
      
      // Financial values (raw numbers)
      intrinsicValue: results.intrinsicValue || 0,
      currentPrice: results.currentPrice || 0,
      
      // Modern valuation status (from strategy pattern)
      valuationStatus: valuationStatus?.isUndervalued ? 'Undervalued' : 'Overvalued',
      valuationDirection: valuationStatus?.direction || 'Upside',
      valuationCategory: valuationStatus?.category || this.valuationConfig.labels.fairValue,
      valuationPercentage: valuationStatus?.percentage || 0,
      isUndervalued: valuationStatus?.isUndervalued || false,
      
      // Server-provided raw values (for backward compatibility)
      priceAsPercentageOfValue: results.priceAsPercentageOfValue || 0,
      
      // Legacy upside values (for backward compatibility)
      upside: Math.abs(upside),
      upsideRaw: upside,
      upsideDirection: fallbackIsPositive ? 'Upside' : 'Downside',
      upsideType: fallbackIsPositive ? 'Upside' : 'Downside',
      
      // Currency
      currency: results.currency || 'USD',
      currencySymbol: this.getCurrencySymbol(results.currency || 'USD'),
      
      // Formatted values
      formattedIntrinsicValue: this.formatCurrency(results.intrinsicValue, results.currency),
      formattedCurrentPrice: this.formatCurrency(results.currentPrice, results.currency),
      formattedValuationPercentage: `${(valuationStatus?.percentage || 0).toFixed(2)}%`,
      formattedUpside: `${Math.abs(upside).toFixed(2)}%`,
      
      // Additional metrics (if available)
      marketCap: results.marketCap || 0,
      formattedMarketCap: this.formatCurrency(results.marketCap || 0, results.currency),
      
      // Meta information
      analysisDate: new Date().toLocaleDateString(),
      lastUpdated: new Date().toISOString()
    };
  }

  /**
   * Validate template syntax
   * @param template Template string to validate
   * @returns Array of validation errors, empty if valid
   */
  validateTemplate(template: string): string[] {
    const errors: string[] = [];
    
    if (!template || typeof template !== 'string') {
      errors.push('Template must be a non-empty string');
      return errors;
    }

    // Check for unmatched braces
    const openBraces = (template.match(/\{/g) || []).length;
    const closeBraces = (template.match(/\}/g) || []).length;
    
    if (openBraces !== closeBraces) {
      errors.push('Unmatched braces in template');
    }

    // Check for empty placeholders
    const emptyPlaceholders = template.match(/\{\s*\}/g);
    if (emptyPlaceholders) {
      errors.push('Empty placeholders found');
    }

    // Check for nested braces
    const nestedBraces = template.match(/\{[^}]*\{[^}]*\}/g);
    if (nestedBraces) {
      errors.push('Nested braces are not supported');
    }

    return errors;
  }

  /**
   * Get available template keys for DCF results
   * @returns Array of available placeholder keys with descriptions
   */
  getAvailableKeys(): Array<{key: string, description: string, example: string}> {
    return [
      { key: 'symbol', description: 'Stock ticker symbol', example: 'AAPL' },
      { key: 'companyName', description: 'Full company name', example: 'Apple Inc.' },
      { key: 'intrinsicValue', description: 'Raw intrinsic value number', example: '180.50' },
      { key: 'currentPrice', description: 'Raw current price number', example: '150.25' },
      
      // Server-provided valuation data (preferred)
      { key: 'valuationStatus', description: 'Undervalued or Overvalued (from server)', example: 'Undervalued' },
      { key: 'valuationDirection', description: 'Upside or Downside (from server)', example: 'Upside' },
      { key: 'valuationPercentage', description: 'Server-calculated valuation percentage', example: '20.15' },
      { key: 'formattedValuationPercentage', description: 'Formatted server valuation percentage', example: '20.15%' },
      
      // Legacy upside data (fallback)
      { key: 'upside', description: 'Absolute upside/downside percentage (legacy)', example: '20.15' },
      { key: 'upsideDirection', description: 'Upside or Downside text (legacy)', example: 'Upside' },
      { key: 'formattedUpside', description: 'Formatted upside percentage (legacy)', example: '20.15%' },
      
      // Currency and formatting
      { key: 'currency', description: 'Currency code', example: 'USD' },
      { key: 'currencySymbol', description: 'Currency symbol', example: '$' },
      { key: 'formattedIntrinsicValue', description: 'Currency-formatted intrinsic value', example: '$180.50' },
      { key: 'formattedCurrentPrice', description: 'Currency-formatted current price', example: '$150.25' },
      
      // Valuation analysis and metadata
      { key: 'valuationCategory', description: 'Valuation category assessment', example: 'Significantly Undervalued' },
      { key: 'analysisDate', description: 'Current analysis date', example: '12/25/2024' }
    ];
  }

  private formatNumber(value: number, key: string, options: Required<TemplateOptions>): string {
    if (!options.formatNumbers) {
      return value.toString();
    }

    // Special formatting based on key names
    if (key.toLowerCase().includes('price') || key.toLowerCase().includes('value')) {
      return this.formatCurrency(value, options.currencySymbol);
    }

    if (key.toLowerCase().includes('percent') || key.toLowerCase().includes('upside')) {
      return `${value.toFixed(2)}%`;
    }

    // Default number formatting
    return value.toLocaleString();
  }

  private getCurrencySymbol(currency: string): string {
    const symbols: Record<string, string> = {
      'USD': '$',
      'EUR': '€',
      'GBP': '£',
      'JPY': '¥',
      'CAD': 'C$',
      'AUD': 'A$'
    };
    
    return symbols[currency] || currency;
  }

  /**
   * Get the current valuation configuration
   * @returns Current valuation configuration object
   */
  getValuationConfig(): ValuationConfig {
    return this.valuationConfig;
  }

  /**
   * Get valuation status using the strategy pattern
   * @param results Valuation results data
   * @returns Valuation status with category, direction, and percentage
   */
  getValuationStatus(results: any): ValuationStatus | null {
    return this.valuationSourceFactory.getValuationStatus(results);
  }
}