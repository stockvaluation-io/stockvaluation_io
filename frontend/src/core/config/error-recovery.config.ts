/**
 * Error Recovery Configuration System
 * Centralized error handling and recovery strategies for asset loading
 */

export interface RetryConfig {
  maxAttempts: number;
  backoffStrategy: 'linear' | 'exponential';
  baseDelay: number;
  maxDelay: number;
  jitter: boolean;
}

export interface ErrorRecoveryConfig {
  enableRecovery: boolean;
  retryConfig: RetryConfig;
  fallbackStrategies: FallbackStrategy[];
  monitoringConfig: MonitoringConfig;
}

export interface FallbackStrategy {
  errorType: string;
  strategyType: 'default_data' | 'cache' | 'alternative_url' | 'graceful_degradation';
  priority: number;
  enabled: boolean;
}

export interface MonitoringConfig {
  enableErrorTracking: boolean;
  enablePerformanceTracking: boolean;
  alertThresholds: {
    failureRate: number;
    responseTime: number;
    criticalAssetFailures: number;
  };
}

export interface AssetErrorContext {
  filename: string;
  assetType: string;
  errorType: string;
  attemptNumber: number;
  timestamp: number;
  url: string;
  responseTime?: number;
  statusCode?: number;
  userAgent?: string;
  sessionId?: string;
}

export interface RecoveryResult<T = any> {
  success: boolean;
  data?: T;
  strategy: string;
  fallbackUsed: boolean;
  recoveryTime: number;
  error?: any;
}

// Default error recovery configuration
export const DEFAULT_ERROR_RECOVERY_CONFIG: ErrorRecoveryConfig = {
  enableRecovery: true,
  retryConfig: {
    maxAttempts: 3,
    backoffStrategy: 'exponential',
    baseDelay: 1000,
    maxDelay: 10000,
    jitter: true
  },
  fallbackStrategies: [
    {
      errorType: 'network',
      strategyType: 'cache',
      priority: 1,
      enabled: true
    },
    {
      errorType: 'not_found',
      strategyType: 'default_data',
      priority: 2,
      enabled: true
    },
    {
      errorType: 'timeout',
      strategyType: 'alternative_url',
      priority: 3,
      enabled: true
    },
    {
      errorType: 'server_error',
      strategyType: 'graceful_degradation',
      priority: 4,
      enabled: true
    }
  ],
  monitoringConfig: {
    enableErrorTracking: true,
    enablePerformanceTracking: true,
    alertThresholds: {
      failureRate: 0.1, // 10% failure rate
      responseTime: 5000, // 5 seconds
      criticalAssetFailures: 3 // 3 critical asset failures
    }
  }
};

// Error type classification
export enum AssetErrorType {
  NETWORK_ERROR = 'network',
  NOT_FOUND = 'not_found',
  TIMEOUT = 'timeout',
  SERVER_ERROR = 'server_error',
  PARSE_ERROR = 'parse_error',
  UNKNOWN = 'unknown'
}

// Asset criticality levels
export enum AssetCriticality {
  CRITICAL = 'critical',    // App cannot function without this asset
  HIGH = 'high',           // Important for user experience
  MEDIUM = 'medium',       // Nice to have
  LOW = 'low'             // Optional enhancement
}

// Asset error classification utility
export class AssetErrorClassifier {
  static classifyError(error: any): AssetErrorType {
    if (!error) return AssetErrorType.UNKNOWN;
    
    // Network connectivity issues
    if (error.status === 0 || error.name === 'NetworkError') {
      return AssetErrorType.NETWORK_ERROR;
    }
    
    // Resource not found
    if (error.status === 404) {
      return AssetErrorType.NOT_FOUND;
    }
    
    // Timeout errors
    if (error.name === 'TimeoutError' || error.status === 408) {
      return AssetErrorType.TIMEOUT;
    }
    
    // Server errors
    if (error.status >= 500 && error.status < 600) {
      return AssetErrorType.SERVER_ERROR;
    }
    
    // Parse errors
    if (error.name === 'SyntaxError' || error.message?.includes('JSON')) {
      return AssetErrorType.PARSE_ERROR;
    }
    
    return AssetErrorType.UNKNOWN;
  }
  
  static getAssetCriticality(filename: string): AssetCriticality {
    // Define criticality based on filename patterns
    const criticalAssets = ['faq.json', 'privacy.json'];
    const highAssets = ['top-stocks.json'];
    const mediumAssets = ['damodaran-quotes.json'];
    
    if (criticalAssets.includes(filename)) {
      return AssetCriticality.CRITICAL;
    }
    
    if (highAssets.includes(filename)) {
      return AssetCriticality.HIGH;
    }
    
    if (mediumAssets.includes(filename)) {
      return AssetCriticality.MEDIUM;
    }
    
    return AssetCriticality.LOW;
  }
}

// Retry delay calculator
export class RetryDelayCalculator {
  static calculateDelay(
    attempt: number, 
    config: RetryConfig
  ): number {
    let delay: number;
    
    switch (config.backoffStrategy) {
      case 'linear':
        delay = config.baseDelay * attempt;
        break;
      case 'exponential':
        delay = config.baseDelay * Math.pow(2, attempt - 1);
        break;
      default:
        delay = config.baseDelay;
    }
    
    // Apply maximum delay limit
    delay = Math.min(delay, config.maxDelay);
    
    // Apply jitter if enabled
    if (config.jitter) {
      delay = delay * (0.5 + Math.random() * 0.5);
    }
    
    return Math.floor(delay);
  }
}