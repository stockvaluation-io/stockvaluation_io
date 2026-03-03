/**
 * Configuration for valuation category thresholds and labels
 * Used by Template Engine for consistent valuation categorization
 */

export interface ValuationThresholds {
  /** Threshold for significantly undervalued (e.g., -20 = 20% below fair value) */
  significantlyUndervalued: number;
  /** Threshold for undervalued (e.g., -10 = 10% below fair value) */
  undervalued: number;
  /** Threshold for fair value range (e.g., 10 = within ±10% of fair value) */
  fairValue: number;
  /** Threshold for overvalued (e.g., 20 = 20% above fair value) */
  overvalued: number;
}

export interface ValuationLabels {
  significantlyUndervalued: string;
  undervalued: string;
  fairValue: string;
  overvalued: string;
  significantlyOvervalued: string;
}

export interface ValuationConfig {
  thresholds: ValuationThresholds;
  labels: ValuationLabels;
}

/**
 * Default valuation configuration
 * These are the standard thresholds used across the platform
 */
export const DEFAULT_VALUATION_CONFIG: ValuationConfig = {
  thresholds: {
    significantlyUndervalued: -20,
    undervalued: -10,
    fairValue: 10,
    overvalued: 20
  },
  labels: {
    significantlyUndervalued: 'Significantly Undervalued',
    undervalued: 'Undervalued',
    fairValue: 'Fair Value',
    overvalued: 'Overvalued',
    significantlyOvervalued: 'Significantly Overvalued'
  }
};

/**
 * Valuation status result interface
 */
export interface ValuationStatus {
  category: string;
  isUndervalued: boolean;
  percentage: number;
  direction: 'Upside' | 'Downside';
}