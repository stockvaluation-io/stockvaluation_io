/**
 * DCF Analysis Constants
 * Centralized configuration values for the DCF analysis feature
 */

// Storage and Cache Configuration
export const DCF_STORAGE_CONFIG = {
  /** DCF state cache duration - 24 hours */
  STATE_EXPIRY_TIME: 24 * 60 * 60 * 1000,
  
  /** Storage key prefixes */
  STORAGE_KEY: 'dcf_analysis_state',
  
  /** Test key for localStorage availability check */
  STORAGE_TEST_KEY: '__dcf_storage_test__'
} as const;

// Timing and Performance Configuration
export const DCF_TIMING_CONFIG = {
  /** Delay before triggering quick analysis (ms) */
  QUICK_ANALYSIS_DELAY: 100,
  
  /** Loading animation update interval (ms) */
  LOADING_UPDATE_INTERVAL: 100,
  
  /** Loading stage transition duration (ms) */
  LOADING_STAGE_DURATION: 1000,
  
  /** Maximum progress percentage before completion */
  MAX_PROGRESS_PERCENTAGE: 95
} as const;

// Calculation and Display Configuration
export const DCF_CALCULATION_CONFIG = {
  /** Percentage conversion multiplier */
  PERCENTAGE_MULTIPLIER: 100,
  
  /** Price threshold for formatting (above this uses K/M notation) */
  PRICE_FORMAT_THRESHOLD: 1000,
  
  /** Number of decimal places for currency formatting */
  CURRENCY_DECIMAL_PLACES: 2,
  
  /** Number of decimal places for percentage formatting */
  PERCENTAGE_DECIMAL_PLACES: 1
} as const;

// Default Risk and Override Settings
export const DCF_DEFAULT_SETTINGS = {
  /** Default company risk level */
  DEFAULT_RISK_LEVEL: 'Medium' as const,
  
  /** Default expense capitalization setting */
  DEFAULT_EXPENSE_CAPITALIZATION: true,
  
  /** Default operating lease setting */
  DEFAULT_OPERATING_LEASE: false,
  
  /** Default employee options setting */
  DEFAULT_EMPLOYEE_OPTIONS: false,
  
  /** Default number of employee options */
  DEFAULT_OPTIONS_COUNT: 0,
  
  /** Default average strike price */
  DEFAULT_STRIKE_PRICE: 0,
  
  /** Default average maturity */
  DEFAULT_MATURITY: 0,
  
  /** Default stock price standard deviation */
  DEFAULT_STOCK_PRICE_STD_DEV: 0
} as const;

// API Configuration
export const DCF_API_CONFIG = {
  /** Timeout for API requests (ms) */
  REQUEST_TIMEOUT: 30000,
  
  /** Retry attempts for failed requests */
  MAX_RETRY_ATTEMPTS: 3,
  
  /** Delay between retry attempts (ms) */
  RETRY_DELAY: 1000
} as const;

// UI Configuration
export const DCF_UI_CONFIG = {
  /** Maximum width for main content containers */
  MAX_CONTENT_WIDTH: 1000,
  
  /** Z-index for modals and overlays */
  MODAL_Z_INDEX: 1000,
  
  /** Z-index for loading overlays */
  LOADING_Z_INDEX: 1001,
  
  /** Grid column minimum width for responsive layouts */
  GRID_MIN_COLUMN_WIDTH: 100,
  
  /** Breakpoint for mobile layouts (px) */
  MOBILE_BREAKPOINT: 768
} as const;

// Progress and Loading Configuration
export const DCF_PROGRESS_CONFIG = {
  /** Total number of analysis steps */
  TOTAL_ANALYSIS_STEPS: 4,
  
  /** Progress calculation base (for percentage calculations) */
  PROGRESS_BASE: 100,
  
  /** Minimum progress value */
  MIN_PROGRESS: 0,
  
  /** Maximum progress value */
  MAX_PROGRESS: 100
} as const;

// Export all constants as a single object for convenience
export const DCF_CONSTANTS = {
  STORAGE: DCF_STORAGE_CONFIG,
  TIMING: DCF_TIMING_CONFIG,
  CALCULATION: DCF_CALCULATION_CONFIG,
  DEFAULTS: DCF_DEFAULT_SETTINGS,
  API: DCF_API_CONFIG,
  UI: DCF_UI_CONFIG,
  PROGRESS: DCF_PROGRESS_CONFIG
} as const;