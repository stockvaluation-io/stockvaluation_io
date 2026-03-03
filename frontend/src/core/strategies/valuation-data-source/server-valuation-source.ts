import { Injectable } from '@angular/core';
import { ValuationDataSource } from './valuation-data-source.interface';
import { ValuationStatus, ValuationConfig } from '../../config/valuation.config';
import { environment } from '../../../env/environment';

/**
 * Server-provided valuation data source (preferred method)
 * Uses priceAsPercentageOfValue from API response
 */
@Injectable({
  providedIn: 'root'
})
export class ServerValuationSource implements ValuationDataSource {
  
  private readonly config: ValuationConfig;
  
  constructor() {
    this.config = environment.valuation;
  }

  canHandle(results: any): boolean {
    return results && 
           typeof results.priceAsPercentageOfValue === 'number' && 
           !isNaN(results.priceAsPercentageOfValue);
  }

  getPriority(): number {
    return 100; // Highest priority - server data is preferred
  }

  getValuationStatus(results: any): ValuationStatus | null {
    if (!this.canHandle(results)) {
      return null;
    }

    const priceAsPercentageOfValue = results.priceAsPercentageOfValue;
    const isUndervalued = priceAsPercentageOfValue < 0;
    const percentage = Math.abs(priceAsPercentageOfValue);

    return {
      category: this.determineCategory(priceAsPercentageOfValue),
      isUndervalued,
      percentage,
      direction: isUndervalued ? 'Upside' : 'Downside'
    };
  }

  private determineCategory(priceAsPercentageOfValue: number): string {
    const { thresholds, labels } = this.config;

    // Server provides: (currentPrice / fairValue - 1) * 100
    // Negative = undervalued, Positive = overvalued
    if (priceAsPercentageOfValue < thresholds.significantlyUndervalued) {
      return labels.significantlyUndervalued;
    }
    if (priceAsPercentageOfValue < thresholds.undervalued) {
      return labels.undervalued;
    }
    if (priceAsPercentageOfValue < thresholds.fairValue) {
      return labels.fairValue;
    }
    if (priceAsPercentageOfValue < thresholds.overvalued) {
      return labels.overvalued;
    }
    return labels.significantlyOvervalued;
  }
}