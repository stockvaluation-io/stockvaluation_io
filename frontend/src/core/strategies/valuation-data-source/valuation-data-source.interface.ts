import { ValuationStatus } from '../../config/valuation.config';

/**
 * Interface for different valuation data source strategies
 * Allows for clean separation between server-provided data and legacy fallback calculations
 */
export interface ValuationDataSource {
  /**
   * Get valuation status from the given results data
   * @param results Raw API response or analysis results
   * @returns Structured valuation status with category, direction, and percentage
   */
  getValuationStatus(results: any): ValuationStatus | null;

  /**
   * Check if this data source can handle the given results
   * @param results Raw API response or analysis results
   * @returns True if this data source can process the results
   */
  canHandle(results: any): boolean;

  /**
   * Get the priority of this data source (higher = more preferred)
   * @returns Priority number (higher values take precedence)
   */
  getPriority(): number;
}