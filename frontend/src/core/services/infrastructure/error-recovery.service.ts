import { Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { retry, catchError, switchMap, tap } from 'rxjs/operators';
import { 
  ErrorRecoveryConfig, 
  AssetErrorContext, 
  RecoveryResult, 
  AssetErrorType,
  AssetCriticality,
  AssetErrorClassifier,
  RetryDelayCalculator,
  DEFAULT_ERROR_RECOVERY_CONFIG
} from '../../config/error-recovery.config';
import { LoggerService } from './logger.service';
import { AssetCacheService } from './asset-cache.service';

export abstract class ErrorRecoveryStrategy {
  abstract canRecover(error: any, context: AssetErrorContext): boolean;
  abstract recover<T>(error: any, context: AssetErrorContext): Observable<RecoveryResult<T>>;
  abstract getStrategyName(): string;
}

@Injectable({
  providedIn: 'root'
})
export class NetworkErrorRecovery extends ErrorRecoveryStrategy {
  
  constructor(
    private logger: LoggerService,
    private cache: AssetCacheService
  ) {
    super();
  }

  canRecover(error: any, context: AssetErrorContext): boolean {
    return AssetErrorClassifier.classifyError(error) === AssetErrorType.NETWORK_ERROR;
  }

  recover<T>(error: any, context: AssetErrorContext): Observable<RecoveryResult<T>> {
    const startTime = Date.now();
    
    // For network errors, we currently don't have a direct cache get method
    // This would require implementing a synchronous cache lookup
    // For now, indicate that retry should be attempted
    this.logger.warn(
      `Network error recovery: Recommending retry for ${context.filename}`,
      { context },
      'NetworkErrorRecovery'
    );
    
    return of({
      success: false,
      strategy: 'retry',
      fallbackUsed: false,
      recoveryTime: Date.now() - startTime,
      error: 'Network error - retry recommended'
    });
  }

  getStrategyName(): string {
    return 'NetworkErrorRecovery';
  }
}

@Injectable({
  providedIn: 'root'
})
export class NotFoundErrorRecovery extends ErrorRecoveryStrategy {
  
  constructor(private logger: LoggerService) {
    super();
  }

  canRecover(error: any, context: AssetErrorContext): boolean {
    return AssetErrorClassifier.classifyError(error) === AssetErrorType.NOT_FOUND;
  }

  recover<T>(error: any, context: AssetErrorContext): Observable<RecoveryResult<T>> {
    const startTime = Date.now();
    
    // Get default data based on asset type
    const defaultData = this.getDefaultData<T>(context.filename, context.assetType);
    
    if (defaultData) {
      this.logger.info(
        `NotFound error recovery: Using default data for ${context.filename}`,
        { context },
        'NotFoundErrorRecovery'
      );
      
      return of({
        success: true,
        data: defaultData,
        strategy: 'default_data',
        fallbackUsed: true,
        recoveryTime: Date.now() - startTime
      });
    }
    
    this.logger.warn(
      `NotFound error recovery: No default data available for ${context.filename}`,
      { context },
      'NotFoundErrorRecovery'
    );
    
    return of({
      success: false,
      strategy: 'default_data',
      fallbackUsed: false,
      recoveryTime: Date.now() - startTime,
      error: 'No default data available'
    });
  }

  private getDefaultData<T>(filename: string, assetType: string): T | null {
    // Define default data structures based on filename
    const defaultDataMap: { [key: string]: any } = {
      'faq.json': {
        hero: {
          title: 'Frequently Asked Questions',
          subtitle: 'Find answers to common questions about stock valuation'
        },
        categories: [
          {
            id: 'general',
            name: 'General',
            icon: 'help',
            description: 'Basic questions about our platform'
          }
        ],
        faqs: [
          {
            id: 'default-1',
            question: 'What is DCF analysis?',
            answer: 'DCF (Discounted Cash Flow) analysis is a valuation method that estimates the value of a company based on its expected future cash flows.',
            category: 'general',
            searchKeywords: ['dcf', 'valuation', 'analysis']
          }
        ],
        contact: {
          title: 'Need More Help?',
          description: 'Contact our support team for additional assistance',
          email: 'stockvaluation.io@gmail.com'
        }
      },
      'privacy.json': {
        lastUpdated: new Date().toISOString(),
        hero: {
          title: 'Privacy Policy',
          subtitle: 'Your privacy is important to us',
          summary: {
            title: 'Quick Summary',
            items: [
              'We collect minimal personal information',
              'We do not sell your data to third parties',
              'You can request data deletion at any time'
            ]
          }
        },
        sections: [
          {
            title: 'Information We Collect',
            content: 'We collect basic usage analytics to improve our service.',
            icon: 'info'
          }
        ],
        contact: {
          title: 'Privacy Questions',
          description: 'Contact us for any privacy-related questions',
          email: 'stockvaluation.io@gmail.com',
          headerIcon: 'shield',
          buttonIcon: 'mail'
        }
      },
      'top-stocks.json': [],
      'damodaran-quotes.json': {
        title: 'Investment Wisdom',
        subtitle: 'Inspirational quotes about investing',
        quotes: [
          {
            text: 'The stock market is designed to transfer money from the active to the patient.',
            author: 'Warren Buffett'
          },
          {
            text: 'Risk comes from not knowing what you are doing.',
            author: 'Warren Buffett'
          }
        ]
      }
    };
    
    return defaultDataMap[filename] || null;
  }

  getStrategyName(): string {
    return 'NotFoundErrorRecovery';
  }
}

@Injectable({
  providedIn: 'root'
})
export class TimeoutErrorRecovery extends ErrorRecoveryStrategy {
  
  constructor(private logger: LoggerService) {
    super();
  }

  canRecover(error: any, context: AssetErrorContext): boolean {
    return AssetErrorClassifier.classifyError(error) === AssetErrorType.TIMEOUT;
  }

  recover<T>(error: any, context: AssetErrorContext): Observable<RecoveryResult<T>> {
    const startTime = Date.now();
    
    // For timeout errors, we could implement alternative URL strategy
    // For now, we'll indicate that retry should be attempted
    this.logger.warn(
      `Timeout error recovery: Recommending retry for ${context.filename}`,
      { context },
      'TimeoutErrorRecovery'
    );
    
    return of({
      success: false,
      strategy: 'retry',
      fallbackUsed: false,
      recoveryTime: Date.now() - startTime,
      error: 'Timeout - retry recommended'
    });
  }

  getStrategyName(): string {
    return 'TimeoutErrorRecovery';
  }
}

@Injectable({
  providedIn: 'root'
})
export class ErrorRecoveryService {
  
  private config: ErrorRecoveryConfig = DEFAULT_ERROR_RECOVERY_CONFIG;
  private strategies: ErrorRecoveryStrategy[] = [];
  private errorCounts: Map<string, number> = new Map();
  
  constructor(
    private logger: LoggerService,
    private networkRecovery: NetworkErrorRecovery,
    private notFoundRecovery: NotFoundErrorRecovery,
    private timeoutRecovery: TimeoutErrorRecovery
  ) {
    this.strategies = [
      this.networkRecovery,
      this.notFoundRecovery,
      this.timeoutRecovery
    ];
  }

  /**
   * Attempt to recover from an asset loading error
   * @param error The error that occurred
   * @param context Context information about the failed asset
   * @returns Observable with recovery result
   */
  attemptRecovery<T>(error: any, context: AssetErrorContext): Observable<RecoveryResult<T>> {
    if (!this.config.enableRecovery) {
      return of({
        success: false,
        strategy: 'none',
        fallbackUsed: false,
        recoveryTime: 0,
        error: 'Recovery disabled'
      });
    }

    // Find a suitable recovery strategy
    const strategy = this.strategies.find(s => s.canRecover(error, context));
    
    if (!strategy) {
      this.logger.warn(
        `No recovery strategy found for error type: ${AssetErrorClassifier.classifyError(error)}`,
        { error, context },
        'ErrorRecoveryService'
      );
      
      return of({
        success: false,
        strategy: 'none',
        fallbackUsed: false,
        recoveryTime: 0,
        error: 'No applicable recovery strategy'
      });
    }

    // Attempt recovery
    return strategy.recover<T>(error, context).pipe(
      tap(result => {
        // Track error counts for monitoring
        this.trackError(context, result);
        
        // Log recovery attempt
        this.logger.info(
          `Recovery attempt for ${context.filename}: ${result.success ? 'SUCCESS' : 'FAILED'}`,
          { context, result, strategy: strategy.getStrategyName() },
          'ErrorRecoveryService'
        );
      })
    );
  }

  /**
   * Create retry logic with exponential backoff
   * @param source$ Source observable to retry
   * @param context Error context
   * @returns Observable with retry logic
   */
  createRetryLogic<T>(source$: Observable<T>, context: AssetErrorContext): Observable<T> {
    return source$.pipe(
      retry({
        count: this.config.retryConfig.maxAttempts - 1,
        delay: (error, retryCount) => {
          const delay = RetryDelayCalculator.calculateDelay(retryCount, this.config.retryConfig);
          
          this.logger.debug(
            `Retry attempt ${retryCount} for ${context.filename} in ${delay}ms`,
            { context, retryCount, delay },
            'ErrorRecoveryService'
          );
          
          return timer(delay);
        }
      })
    );
  }

  /**
   * Track error occurrences for monitoring
   * @param context Error context
   * @param result Recovery result
   */
  private trackError(context: AssetErrorContext, result: RecoveryResult): void {
    const key = `${context.filename}-${context.errorType}`;
    const currentCount = this.errorCounts.get(key) || 0;
    this.errorCounts.set(key, currentCount + 1);
    
    // Check if we've exceeded alert thresholds
    const criticality = AssetErrorClassifier.getAssetCriticality(context.filename);
    if (criticality === AssetCriticality.CRITICAL && currentCount >= this.config.monitoringConfig.alertThresholds.criticalAssetFailures) {
      this.logger.error(
        `Critical asset failure threshold exceeded for ${context.filename}`,
        { context, result, errorCount: currentCount },
        'ErrorRecoveryService'
      );
    }
  }

  /**
   * Get error statistics for monitoring
   * @returns Error statistics
   */
  getErrorStats(): { [key: string]: number } {
    return Object.fromEntries(this.errorCounts);
  }

  /**
   * Clear error statistics
   */
  clearErrorStats(): void {
    this.errorCounts.clear();
  }

  /**
   * Update error recovery configuration
   * @param config New configuration
   */
  updateConfig(config: Partial<ErrorRecoveryConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   * @returns Current error recovery configuration
   */
  getConfig(): ErrorRecoveryConfig {
    return { ...this.config };
  }
}
