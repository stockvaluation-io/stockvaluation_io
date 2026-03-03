import { Injectable } from '@angular/core';
import { ValuationDataSource } from './valuation-data-source.interface';
import { ValuationStatus, ValuationConfig } from '../../config/valuation.config';
import { environment } from '../../../env/environment';

/**
 * Legacy upside calculation data source (fallback method)
 * Uses upside field from API response when server valuation is not available
 */
@Injectable({
  providedIn: 'root'
})
export class LegacyUpsideSource implements ValuationDataSource {
  
  private readonly config: ValuationConfig;
  
  constructor() {
    this.config = environment.valuation;
  }

  canHandle(results: any): boolean {
    return results && 
           typeof results.upside === 'number' && 
           !isNaN(results.upside);
  }

  getPriority(): number {
    return 50; // Lower priority - fallback method
  }

  getValuationStatus(results: any): ValuationStatus | null {
    if (!this.canHandle(results)) {
      return null;
    }

    const upside = results.upside;
    const isUndervalued = upside > 0;
    const percentage = Math.abs(upside);

    return {
      category: this.determineCategoryFromUpside(upside),
      isUndervalued,
      percentage,
      direction: isUndervalued ? 'Upside' : 'Downside'
    };
  }

  private determineCategoryFromUpside(upside: number): string {
    const { thresholds, labels } = this.config;

    // Legacy upside calculation:
    // Positive upside = undervalued, Negative = overvalued
    // Convert to server-equivalent format for threshold comparison
    const serverEquivalent = -upside; // Invert the sign

    if (serverEquivalent < thresholds.significantlyUndervalued) {
      return labels.significantlyUndervalued;
    }
    if (serverEquivalent < thresholds.undervalued) {
      return labels.undervalued;
    }
    if (serverEquivalent < thresholds.fairValue) {
      return labels.fairValue;
    }
    if (serverEquivalent < thresholds.overvalued) {
      return labels.overvalued;
    }
    return labels.significantlyOvervalued;
  }
}