import { Injectable } from '@angular/core';
import { ValuationDataSource } from './valuation-data-source.interface';
import { ServerValuationSource } from './server-valuation-source';
import { LegacyUpsideSource } from './legacy-upside-source';
import { ValuationStatus, ValuationConfig } from '../../config/valuation.config';
import { environment } from '../../../env/environment';

/**
 * Factory for selecting the appropriate valuation data source
 * Implements strategy pattern with priority-based selection
 */
@Injectable({
  providedIn: 'root'
})
export class ValuationSourceFactory {
  
  private sources: ValuationDataSource[] = [];
  private readonly config: ValuationConfig;

  constructor(
    private serverSource: ServerValuationSource,
    private legacySource: LegacyUpsideSource
  ) {
    this.config = environment.valuation;
    // Register sources in order of priority
    this.sources = [
      this.serverSource,
      this.legacySource
    ].sort((a, b) => b.getPriority() - a.getPriority());
  }

  /**
   * Get valuation status using the best available data source
   * @param results Raw API response or analysis results
   * @returns Valuation status or null if no source can handle the data
   */
  getValuationStatus(results: any): ValuationStatus | null {
    for (const source of this.sources) {
      if (source.canHandle(results)) {
        return source.getValuationStatus(results);
      }
    }

    // No source can handle the data, return default fair value
    return {
      category: this.config.labels.fairValue,
      isUndervalued: false,
      percentage: 0,
      direction: 'Upside'
    };
  }

  /**
   * Get the data source that would be used for the given results
   * @param results Raw API response or analysis results
   * @returns The data source that would handle these results, or null
   */
  getDataSource(results: any): ValuationDataSource | null {
    for (const source of this.sources) {
      if (source.canHandle(results)) {
        return source;
      }
    }
    return null;
  }

  /**
   * Get all available data sources ordered by priority
   * @returns Array of all registered data sources
   */
  getAllSources(): ValuationDataSource[] {
    return [...this.sources];
  }
}