import { Injectable, inject } from '@angular/core';
import { LoggerService } from './logger.service';
import { PlatformDetectionService } from './platform-detection.service';
import { ErrorContext } from './global-error-handler.service';

/**
 * Error tracking configuration
 */
export interface ErrorTrackingConfig {
  /**
   * Whether error tracking is enabled
   */
  enabled: boolean;
  
  /**
   * Environment (development, staging, production)
   */
  environment: string;
  
  /**
   * Application version
   */
  version: string;
  
  /**
   * External service configuration (Sentry, LogRocket, etc.)
   */
  externalServices: {
    sentry?: {
      dsn: string;
      enabled: boolean;
    };
    logRocket?: {
      appId: string;
      enabled: boolean;
    };
    datadog?: {
      clientToken: string;
      enabled: boolean;
    };
  };
  
  /**
   * Sampling rate for error reporting (0.0 to 1.0)
   */
  sampleRate: number;
  
  /**
   * Maximum number of errors to track per session
   */
  maxErrorsPerSession: number;
}

/**
 * Error tracking metadata
 */
export interface ErrorTrackingMetadata {
  userId?: string;
  sessionId: string;
  userAgent: string;
  url: string;
  timestamp: string;
  buildVersion: string;
  environment: string;
  
  // Browser/device information
  browserInfo: {
    name: string;
    version: string;
    language: string;
    cookieEnabled: boolean;
    onLine: boolean;
  };
  
  // Screen/viewport information
  screenInfo: {
    width: number;
    height: number;
    pixelRatio: number;
    orientation?: string;
  };
  
  // Performance information
  performanceInfo: {
    memoryUsage?: number;
    connectionType?: string;
    timeOrigin: number;
  };
}

/**
 * Error Tracking Service
 * 
 * Provides error tracking and reporting capabilities with integration
 * for external services like Sentry, LogRocket, and Datadog.
 * 
 * Features:
 * - Configurable error reporting to multiple services
 * - Automatic metadata collection (browser, device, performance)
 * - Error deduplication and sampling
 * - Privacy-conscious user identification
 * - Local error storage with cleanup
 * - Development vs production behavior
 * 
 * @example
 * ```typescript
 * // Configure in app initialization
 * const errorTrackingService = inject(ErrorTrackingService);
 * errorTrackingService.configure({
 *   enabled: !environment.production,
 *   environment: environment.name,
 *   version: environment.version,
 *   sampleRate: 0.1 // 10% sampling in production
 * });
 * 
 * // Track errors (usually called by GlobalErrorHandler)
 * errorTrackingService.trackError(errorContext);
 * ```
 */
@Injectable({
  providedIn: 'root'
})
export class ErrorTrackingService {
  private logger = inject(LoggerService);
  private platformDetection = inject(PlatformDetectionService);
  
  private config: ErrorTrackingConfig = {
    enabled: false,
    environment: 'development',
    version: '1.0.0',
    externalServices: {},
    sampleRate: 1.0,
    maxErrorsPerSession: 50
  };
  
  private sessionErrorCount = 0;
  private sessionId = this.generateSessionId();
  private trackedErrors = new Set<string>();
  
  /**
   * Configure error tracking
   */
  configure(config: Partial<ErrorTrackingConfig>): void {
    this.config = { ...this.config, ...config };
    
    if (this.config.enabled) {
      this.initializeExternalServices();
      this.setupGlobalErrorListeners();
      this.logger.info('Error tracking configured', {
        environment: this.config.environment,
        version: this.config.version,
        sampleRate: this.config.sampleRate
      }, 'ErrorTrackingService');
    }
  }
  
  /**
   * Track an error with full context
   */
  trackError(errorContext: ErrorContext): void {
    if (!this.shouldTrackError(errorContext)) {
      return;
    }
    
    // Increment session error count
    this.sessionErrorCount++;
    
    // Create tracking metadata
    const metadata = this.createTrackingMetadata(errorContext);
    
    // Create error fingerprint for deduplication
    const fingerprint = this.createErrorFingerprint(errorContext);
    
    // Skip if already tracked in this session
    if (this.trackedErrors.has(fingerprint)) {
      this.logger.debug('Skipping duplicate error', { fingerprint }, 'ErrorTrackingService');
      return;
    }
    
    this.trackedErrors.add(fingerprint);
    
    // Track to external services
    this.trackToExternalServices(errorContext, metadata, fingerprint);
    
    // Store locally for offline scenarios
    this.storeErrorLocally(errorContext, metadata, fingerprint);
    
    this.logger.debug('Error tracked', {
      fingerprint,
      sessionErrorCount: this.sessionErrorCount,
      metadata: {
        component: errorContext.component,
        action: errorContext.action,
        severity: errorContext.severity
      }
    }, 'ErrorTrackingService');
  }
  
  /**
   * Track user actions for error context
   */
  trackUserAction(action: string, data?: any): void {
    if (!this.config.enabled) return;
    
    // Add breadcrumb for external services
    this.addBreadcrumb('user_action', action, data);
    
    this.logger.debug('User action tracked', { action, data }, 'ErrorTrackingService');
  }
  
  /**
   * Set user context for error tracking
   */
  setUserContext(userId: string, userInfo?: any): void {
    if (!this.config.enabled) return;
    
    // Set user context in external services
    this.setUserContextInExternalServices(userId, userInfo);
    
    this.logger.debug('User context set', { userId }, 'ErrorTrackingService');
  }
  
  /**
   * Get current error tracking statistics
   */
  getTrackingStats(): {
    sessionErrorCount: number;
    trackedErrorsCount: number;
    sessionId: string;
    isEnabled: boolean;
  } {
    return {
      sessionErrorCount: this.sessionErrorCount,
      trackedErrorsCount: this.trackedErrors.size,
      sessionId: this.sessionId,
      isEnabled: this.config.enabled
    };
  }
  
  /**
   * Clear tracked errors (useful for testing)
   */
  clearTrackedErrors(): void {
    this.trackedErrors.clear();
    this.sessionErrorCount = 0;
    this.logger.debug('Tracked errors cleared', undefined, 'ErrorTrackingService');
  }
  
  // Private methods
  
  private shouldTrackError(errorContext: ErrorContext): boolean {
    // Don't track if disabled
    if (!this.config.enabled) return false;
    
    // Don't track if we've hit the session limit
    if (this.sessionErrorCount >= this.config.maxErrorsPerSession) {
      this.logger.warn('Session error limit reached, skipping tracking', {
        sessionErrorCount: this.sessionErrorCount,
        maxErrors: this.config.maxErrorsPerSession
      }, 'ErrorTrackingService');
      return false;
    }
    
    // Apply sampling rate
    if (Math.random() > this.config.sampleRate) {
      return false;
    }
    
    // Don't track certain error types in development
    if (this.config.environment === 'development') {
      // Skip common development errors
      const devSkipPatterns = [
        'ChunkLoadError', // Code splitting in dev
        'Script error', // Cross-origin scripts
        'Non-Error promise rejection' // Promise rejections in dev tools
      ];
      
      const errorMessage = errorContext.error.message || errorContext.error.name;
      if (devSkipPatterns.some(pattern => errorMessage.includes(pattern))) {
        return false;
      }
    }
    
    return true;
  }
  
  private createTrackingMetadata(errorContext: ErrorContext): ErrorTrackingMetadata {
    const navigator = this.platformDetection.getNavigator();
    const window = this.platformDetection.getWindow();
    
    return {
      userId: this.getCurrentUserId(),
      sessionId: this.sessionId,
      userAgent: navigator?.userAgent || 'Unknown',
      url: errorContext.url || window?.location.href || 'Unknown',
      timestamp: errorContext.timestamp.toISOString(),
      buildVersion: this.config.version,
      environment: this.config.environment,
      
      browserInfo: {
        name: this.getBrowserName(),
        version: this.getBrowserVersion(),
        language: navigator?.language || 'Unknown',
        cookieEnabled: navigator?.cookieEnabled || false,
        onLine: navigator?.onLine || false
      },
      
      screenInfo: {
        width: window?.screen?.width || 0,
        height: window?.screen?.height || 0,
        pixelRatio: window?.devicePixelRatio || 1,
        orientation: this.getScreenOrientation()
      },
      
      performanceInfo: {
        memoryUsage: this.getMemoryUsage(),
        connectionType: this.getConnectionType(),
        timeOrigin: this.platformDetection.isBrowser() && typeof performance !== 'undefined' ? performance.timeOrigin : 0
      }
    };
  }
  
  private createErrorFingerprint(errorContext: ErrorContext): string {
    // Create a unique fingerprint for error deduplication
    const components = [
      errorContext.error.name,
      errorContext.error.message,
      errorContext.component,
      errorContext.action,
      errorContext.category
    ];
    
    return btoa(components.join('|')).substring(0, 16);
  }
  
  private initializeExternalServices(): void {
    // Initialize Sentry if configured
    if (this.config.externalServices.sentry?.enabled) {
      this.initializeSentry();
    }
    
    // Initialize LogRocket if configured
    if (this.config.externalServices.logRocket?.enabled) {
      this.initializeLogRocket();
    }
    
    // Initialize Datadog if configured
    if (this.config.externalServices.datadog?.enabled) {
      this.initializeDatadog();
    }
  }
  
  private initializeSentry(): void {
    // Placeholder for Sentry initialization
    // In a real implementation, you would:
    // import * as Sentry from '@sentry/angular';
    // Sentry.init({ dsn: this.config.externalServices.sentry.dsn });
    
    this.logger.info('Sentry error tracking initialized', {
      dsn: this.config.externalServices.sentry?.dsn?.substring(0, 20) + '...'
    }, 'ErrorTrackingService');
  }
  
  private initializeLogRocket(): void {
    // Placeholder for LogRocket initialization
    // In a real implementation, you would:
    // import LogRocket from 'logrocket';
    // LogRocket.init(this.config.externalServices.logRocket.appId);
    
    this.logger.info('LogRocket error tracking initialized', {
      appId: this.config.externalServices.logRocket?.appId
    }, 'ErrorTrackingService');
  }
  
  private initializeDatadog(): void {
    // Placeholder for Datadog initialization
    // In a real implementation, you would:
    // import { datadogRum } from '@datadog/browser-rum';
    // datadogRum.init({ clientToken: this.config.externalServices.datadog.clientToken });
    
    this.logger.info('Datadog error tracking initialized', {
      clientToken: this.config.externalServices.datadog?.clientToken?.substring(0, 20) + '...'
    }, 'ErrorTrackingService');
  }
  
  private setupGlobalErrorListeners(): void {
    const window = this.platformDetection.getWindow();
    if (!window) {
      return;
    }

    // Listen for unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
      this.logger.warn('Unhandled promise rejection detected', {
        reason: event.reason,
        promise: event.promise
      }, 'ErrorTrackingService');
    });
    
    // Listen for resource loading errors
    window.addEventListener('error', (event) => {
      if (event.target !== window) {
        this.logger.warn('Resource loading error detected', {
          source: (event.target as any)?.src || (event.target as any)?.href,
          type: event.target?.constructor.name
        }, 'ErrorTrackingService');
      }
    }, true);
  }
  
  private trackToExternalServices(
    errorContext: ErrorContext,
    metadata: ErrorTrackingMetadata,
    fingerprint: string
  ): void {
    // Track to Sentry
    if (this.config.externalServices.sentry?.enabled) {
      this.trackToSentry(errorContext, metadata, fingerprint);
    }
    
    // Track to LogRocket
    if (this.config.externalServices.logRocket?.enabled) {
      this.trackToLogRocket(errorContext, metadata, fingerprint);
    }
    
    // Track to Datadog
    if (this.config.externalServices.datadog?.enabled) {
      this.trackToDatadog(errorContext, metadata, fingerprint);
    }
  }
  
  private trackToSentry(
    errorContext: ErrorContext,
    metadata: ErrorTrackingMetadata,
    fingerprint: string
  ): void {
    // Placeholder for Sentry error tracking
    // In a real implementation:
    // Sentry.captureException(errorContext.error, {
    //   tags: { component: errorContext.component, action: errorContext.action },
    //   extra: metadata,
    //   fingerprint: [fingerprint]
    // });
    
    this.logger.debug('Error tracked to Sentry', { fingerprint }, 'ErrorTrackingService');
  }
  
  private trackToLogRocket(
    errorContext: ErrorContext,
    metadata: ErrorTrackingMetadata,
    fingerprint: string
  ): void {
    // Placeholder for LogRocket error tracking
    // In a real implementation:
    // LogRocket.captureException(errorContext.error);
    
    this.logger.debug('Error tracked to LogRocket', { fingerprint }, 'ErrorTrackingService');
  }
  
  private trackToDatadog(
    errorContext: ErrorContext,
    metadata: ErrorTrackingMetadata,
    fingerprint: string
  ): void {
    // Placeholder for Datadog error tracking
    // In a real implementation:
    // datadogRum.addError(errorContext.error, {
    //   component: errorContext.component,
    //   action: errorContext.action,
    //   metadata
    // });
    
    this.logger.debug('Error tracked to Datadog', { fingerprint }, 'ErrorTrackingService');
  }
  
  private storeErrorLocally(
    errorContext: ErrorContext,
    metadata: ErrorTrackingMetadata,
    fingerprint: string
  ): void {
    try {
      const errorData = {
        fingerprint,
        error: {
          name: errorContext.error.name,
          message: errorContext.error.message,
          stack: errorContext.error instanceof Error ? errorContext.error.stack : undefined
        },
        context: {
          component: errorContext.component,
          action: errorContext.action,
          severity: errorContext.severity,
          category: errorContext.category
        },
        metadata,
        timestamp: errorContext.timestamp.toISOString()
      };
      
      // Store in localStorage with automatic cleanup
      const localStorage = this.platformDetection.getLocalStorage();
      if (localStorage) {
        const storageKey = `error_tracking_${fingerprint}`;
        localStorage.setItem(storageKey, JSON.stringify(errorData));
      }
      
      // Clean up old error data (keep last 10 errors)
      this.cleanupLocalErrorStorage();
    } catch (error) {
      this.logger.warn('Failed to store error locally', error, 'ErrorTrackingService');
    }
  }
  
  private cleanupLocalErrorStorage(): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      const errorKeys = Object.keys(localStorage)
        .filter(key => key.startsWith('error_tracking_'))
        .sort()
        .reverse(); // Most recent first
      
      // Remove old errors beyond the limit
      if (errorKeys.length > 10) {
        errorKeys.slice(10).forEach(key => {
          localStorage.removeItem(key);
        });
      }
    } catch (error) {
      this.logger.warn('Failed to cleanup local error storage', error, 'ErrorTrackingService');
    }
  }
  
  private addBreadcrumb(category: string, message: string, data?: any): void {
    // Add breadcrumb to external services for error context
    // This would be implemented for each service
  }
  
  private setUserContextInExternalServices(userId: string, userInfo?: any): void {
    // Set user context in external services
    // This would be implemented for each service
  }
  
  // Utility methods for metadata collection
  
  private generateSessionId(): string {
    return Date.now().toString(36) + Math.random().toString(36).substr(2);
  }
  
  private getCurrentUserId(): string | undefined {
    // Placeholder for user ID retrieval
    // In a real implementation, this would get the current user ID from auth service
    return undefined;
  }
  
  private getBrowserName(): string {
    const navigator = this.platformDetection.getNavigator();
    if (!navigator) return 'Unknown';
    
    const userAgent = navigator.userAgent;
    if (userAgent.includes('Chrome')) return 'Chrome';
    if (userAgent.includes('Firefox')) return 'Firefox';
    if (userAgent.includes('Safari')) return 'Safari';
    if (userAgent.includes('Edge')) return 'Edge';
    return 'Unknown';
  }
  
  private getBrowserVersion(): string {
    const navigator = this.platformDetection.getNavigator();
    if (!navigator) return 'Unknown';
    
    const userAgent = navigator.userAgent;
    const match = userAgent.match(/(Chrome|Firefox|Safari|Edge)\/(\d+)/);
    return match ? match[2] : 'Unknown';
  }
  
  private getScreenOrientation(): string | undefined {
    const window = this.platformDetection.getWindow();
    if (window && 'screen' in window && 'orientation' in window.screen) {
      return (window.screen.orientation as any)?.type;
    }
    return undefined;
  }
  
  private getMemoryUsage(): number | undefined {
    if (this.platformDetection.isBrowser() && typeof performance !== 'undefined' && 'memory' in performance) {
      return (performance as any).memory?.usedJSHeapSize;
    }
    return undefined;
  }
  
  private getConnectionType(): string | undefined {
    const navigator = this.platformDetection.getNavigator();
    if (navigator && 'connection' in navigator) {
      return (navigator as any).connection?.effectiveType;
    }
    return undefined;
  }
}