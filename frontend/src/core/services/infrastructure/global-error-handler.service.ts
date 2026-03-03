import { ErrorHandler, Injectable, NgZone, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { LoggerService } from './logger.service';
import { ErrorTrackingService } from './error-tracking.service';
import { DCFNotificationService } from '../../../components/dcf-analysis/services/dcf-notification.service';
import { LoadingStateService } from '../ui/loading-state.service';
import { PlatformDetectionService } from './platform-detection.service';

/**
 * Error severity levels for categorizing and handling different types of errors
 */
export enum ErrorSeverity {
  LOW = 'low',
  MEDIUM = 'medium', 
  HIGH = 'high',
  CRITICAL = 'critical'
}

/**
 * Error categories for classification and specific handling
 */
export enum ErrorCategory {
  NETWORK = 'network',
  API = 'api',
  VALIDATION = 'validation',
  RUNTIME = 'runtime',
  PERMISSION = 'permission',
  TIMEOUT = 'timeout',
  UNKNOWN = 'unknown'
}

/**
 * Structured error information for consistent error handling
 */
export interface ErrorContext {
  error: Error | HttpErrorResponse;
  severity: ErrorSeverity;
  category: ErrorCategory;
  component?: string;
  action?: string;
  userId?: string;
  sessionId?: string;
  url?: string;
  userAgent?: string;
  timestamp: Date;
  recoverable: boolean;
  retryable: boolean;
  shouldNotifyUser: boolean;
  shouldReport: boolean;
}

/**
 * Configuration for error recovery strategies
 */
export interface ErrorRecoveryConfig {
  maxRetries: number;
  retryDelay: number;
  fallbackAction?: () => void;
  customMessage?: string;
}

/**
 * Global Error Handler Service
 * 
 * Centralized error handling that integrates with existing services:
 * - LoggerService for structured logging and storage
 * - DCFNotificationService for user notifications
 * - LoadingStateService for state management
 * 
 * Features:
 * - Error categorization and severity assessment
 * - Automatic user notifications for recoverable errors
 * - Error context preservation and reporting
 * - Integration with existing error display components
 * - Retry mechanisms and recovery strategies
 * 
 * @example
 * ```typescript
 * // Automatic handling via Angular's dependency injection
 * // Manual error handling:
 * this.globalErrorHandler.handleError(error, {
 *   component: 'CompanySearchComponent',
 *   action: 'searchCompanies'
 * });
 * ```
 */
@Injectable({
  providedIn: 'root'
})
export class GlobalErrorHandler implements ErrorHandler {
  private logger = inject(LoggerService);
  private errorTracking = inject(ErrorTrackingService);
  private notificationService = inject(DCFNotificationService);
  private loadingStateService = inject(LoadingStateService);
  private ngZone = inject(NgZone);
  private platformDetection = inject(PlatformDetectionService);

  // Error tracking for preventing spam
  private recentErrors = new Map<string, Date>();
  private readonly ERROR_DEBOUNCE_TIME = 5000; // 5 seconds
  private readonly MAX_RECENT_ERRORS = 100; // Prevent memory leaks

  // Session information for error context
  private readonly sessionId = this.generateSessionId();

  /**
   * Angular ErrorHandler implementation
   * Handles uncaught errors globally
   */
  handleError(error: Error): void {
    const errorContext = this.createErrorContext(error);
    this.processError(errorContext);
  }

  /**
   * Enhanced error handler with additional context
   * Use this for manual error handling in components/services
   * 
   * @param error - The error to handle
   * @param context - Additional context information
   */
  handleErrorWithContext(
    error: Error | HttpErrorResponse,
    context: {
      component?: string;
      action?: string;
      severity?: ErrorSeverity;
      recoverable?: boolean;
    } = {}
  ): void {
    const errorContext = this.createErrorContext(error, context);
    this.processError(errorContext);
  }

  /**
   * Handle HTTP errors specifically
   * Typically called from HTTP interceptors
   * 
   * @param httpError - HTTP error response
   * @param context - Additional context
   */
  handleHttpError(
    httpError: HttpErrorResponse,
    context: { url?: string; action?: string } = {}
  ): void {
    const errorContext = this.createHttpErrorContext(httpError, context);
    this.processError(errorContext);
  }

  /**
   * Create error context for consistent error processing
   */
  private createErrorContext(
    error: Error | HttpErrorResponse,
    additionalContext: any = {}
  ): ErrorContext {
    const isHttpError = error instanceof HttpErrorResponse;
    
    return {
      error,
      severity: this.determineSeverity(error),
      category: this.categorizeError(error),
      component: additionalContext.component,
      action: additionalContext.action,
      userId: this.getCurrentUserId(),
      sessionId: this.sessionId,
      url: isHttpError ? error.url || undefined : this.platformDetection.getWindow()?.location.href,
      userAgent: this.platformDetection.getNavigator()?.userAgent || 'Unknown',
      timestamp: new Date(),
      recoverable: additionalContext.recoverable ?? this.isRecoverable(error),
      retryable: this.isRetryable(error),
      shouldNotifyUser: this.shouldNotifyUser(error),
      shouldReport: this.shouldReport(error)
    };
  }

  /**
   * Create error context specifically for HTTP errors
   */
  private createHttpErrorContext(
    httpError: HttpErrorResponse,
    context: { url?: string; action?: string }
  ): ErrorContext {
    return {
      error: httpError,
      severity: this.determineHttpSeverity(httpError),
      category: ErrorCategory.API,
      action: context.action,
      userId: this.getCurrentUserId(),
      sessionId: this.sessionId,
      url: context.url || httpError.url || this.platformDetection.getWindow()?.location.href,
      userAgent: this.platformDetection.getNavigator()?.userAgent || 'Unknown',
      timestamp: new Date(),
      recoverable: this.isHttpRecoverable(httpError),
      retryable: this.isHttpRetryable(httpError),
      shouldNotifyUser: this.shouldNotifyHttpUser(httpError),
      shouldReport: this.shouldReportHttp(httpError)
    };
  }

  /**
   * Process the error through the complete handling pipeline
   */
  private processError(errorContext: ErrorContext): void {
    // 1. Check for duplicate errors to prevent spam
    if (this.isDuplicateError(errorContext)) {
      return;
    }

    // 2. Log the error with full context
    this.logError(errorContext);

    // 3. Update loading states if needed
    this.updateLoadingStates(errorContext);

    // 4. Notify user if appropriate
    if (errorContext.shouldNotifyUser) {
      this.ngZone.run(() => {
        this.notifyUser(errorContext);
      });
    }

    // 5. Report error to external services (if configured)
    if (errorContext.shouldReport) {
      this.reportError(errorContext);
    }

    // 6. Trigger recovery mechanisms if applicable
    if (errorContext.recoverable) {
      this.initiateRecovery(errorContext);
    }
  }

  /**
   * Determine error severity based on error type and characteristics
   */
  private determineSeverity(error: Error | HttpErrorResponse): ErrorSeverity {
    if (error instanceof HttpErrorResponse) {
      return this.determineHttpSeverity(error);
    }

    // Runtime JavaScript errors
    if (error.name === 'ChunkLoadError' || error.message.includes('Loading chunk')) {
      return ErrorSeverity.MEDIUM; // Code splitting failures
    }

    if (error.name === 'TypeError' && error.message.includes('null')) {
      return ErrorSeverity.HIGH; // Null reference errors
    }

    if (error.name === 'ReferenceError') {
      return ErrorSeverity.CRITICAL; // Missing references
    }

    // Default runtime error severity
    return ErrorSeverity.MEDIUM;
  }

  /**
   * Determine HTTP error severity
   */
  private determineHttpSeverity(httpError: HttpErrorResponse): ErrorSeverity {
    switch (httpError.status) {
      case 0: // Network error
        return ErrorSeverity.HIGH;
      case 400: // Bad Request
        return ErrorSeverity.LOW;
      case 401: // Unauthorized
        return ErrorSeverity.MEDIUM;
      case 403: // Forbidden
        return ErrorSeverity.HIGH;
      case 404: // Not Found
        return ErrorSeverity.LOW;
      case 408: // Request Timeout
        return ErrorSeverity.MEDIUM;
      case 429: // Too Many Requests
        return ErrorSeverity.MEDIUM;
      case 500: // Internal Server Error
        return ErrorSeverity.HIGH;
      case 502: // Bad Gateway
      case 503: // Service Unavailable
      case 504: // Gateway Timeout
        return ErrorSeverity.CRITICAL;
      default:
        return httpError.status >= 500 ? ErrorSeverity.HIGH : ErrorSeverity.MEDIUM;
    }
  }

  /**
   * Categorize errors for specific handling strategies
   */
  private categorizeError(error: Error | HttpErrorResponse): ErrorCategory {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0) return ErrorCategory.NETWORK;
      if (error.status === 401 || error.status === 403) return ErrorCategory.PERMISSION;
      if (error.status === 408 || error.status === 504) return ErrorCategory.TIMEOUT;
      return ErrorCategory.API;
    }

    if (error.name === 'ValidationError') return ErrorCategory.VALIDATION;
    if (error.message.includes('network') || error.message.includes('fetch')) {
      return ErrorCategory.NETWORK;
    }

    return ErrorCategory.RUNTIME;
  }

  /**
   * Determine if error is recoverable
   */
  private isRecoverable(error: Error | HttpErrorResponse): boolean {
    if (error instanceof HttpErrorResponse) {
      return this.isHttpRecoverable(error);
    }

    // Most runtime errors are not automatically recoverable
    return error.name === 'ChunkLoadError'; // Code splitting can be retried
  }

  /**
   * Determine if HTTP error is recoverable
   */
  private isHttpRecoverable(httpError: HttpErrorResponse): boolean {
    // Network errors and temporary server issues are recoverable
    return [0, 408, 429, 502, 503, 504].includes(httpError.status);
  }

  /**
   * Determine if error is retryable
   */
  private isRetryable(error: Error | HttpErrorResponse): boolean {
    if (error instanceof HttpErrorResponse) {
      return this.isHttpRetryable(error);
    }
    return error.name === 'ChunkLoadError';
  }

  /**
   * Determine if HTTP error is retryable
   */
  private isHttpRetryable(httpError: HttpErrorResponse): boolean {
    // Retry network errors, timeouts, and server errors (not client errors)
    return [0, 408, 429, 500, 502, 503, 504].includes(httpError.status);
  }

  /**
   * Determine if user should be notified
   */
  private shouldNotifyUser(error: Error | HttpErrorResponse): boolean {
    if (error instanceof HttpErrorResponse) {
      return this.shouldNotifyHttpUser(error);
    }

    // Notify for high/critical runtime errors
    const severity = this.determineSeverity(error);
    return [ErrorSeverity.HIGH, ErrorSeverity.CRITICAL].includes(severity);
  }

  /**
   * Determine if HTTP error should notify user
   */
  private shouldNotifyHttpUser(httpError: HttpErrorResponse): boolean {
    // Don't notify for client errors that should be handled by components
    const clientErrors = [400, 404];
    if (clientErrors.includes(httpError.status)) {
      return false;
    }

    // Notify for auth, network, and server errors
    return [0, 401, 403, 408, 429, 500, 502, 503, 504].includes(httpError.status);
  }

  /**
   * Determine if error should be reported to external services
   */
  private shouldReport(error: Error | HttpErrorResponse): boolean {
    if (error instanceof HttpErrorResponse) {
      return this.shouldReportHttp(error);
    }

    // Report medium+ severity runtime errors
    const severity = this.determineSeverity(error);
    return [ErrorSeverity.MEDIUM, ErrorSeverity.HIGH, ErrorSeverity.CRITICAL].includes(severity);
  }

  /**
   * Determine if HTTP error should be reported
   */
  private shouldReportHttp(httpError: HttpErrorResponse): boolean {
    // Report server errors and unexpected client errors
    return httpError.status >= 500 || httpError.status === 0;
  }

  /**
   * Check for duplicate errors to prevent spam
   */
  private isDuplicateError(errorContext: ErrorContext): boolean {
    const errorKey = `${errorContext.error.message}_${errorContext.component}_${errorContext.action}`;
    const lastOccurrence = this.recentErrors.get(errorKey);
    const now = new Date();

    if (lastOccurrence && (now.getTime() - lastOccurrence.getTime()) < this.ERROR_DEBOUNCE_TIME) {
      return true;
    }

    // Prevent memory leaks by limiting map size
    if (this.recentErrors.size >= this.MAX_RECENT_ERRORS) {
      this.cleanupOldErrors(now);
    }

    this.recentErrors.set(errorKey, now);
    return false;
  }

  /**
   * Clean up old error entries to prevent memory leaks
   */
  private cleanupOldErrors(now: Date): void {
    const cutoffTime = now.getTime() - this.ERROR_DEBOUNCE_TIME * 2; // Keep double the debounce time
    
    for (const [key, timestamp] of this.recentErrors.entries()) {
      if (timestamp.getTime() < cutoffTime) {
        this.recentErrors.delete(key);
      }
    }
    
    // If still too large, remove oldest entries
    if (this.recentErrors.size >= this.MAX_RECENT_ERRORS) {
      const sortedEntries = Array.from(this.recentErrors.entries())
        .sort(([, a], [, b]) => a.getTime() - b.getTime());
      
      const entriesToRemove = sortedEntries.slice(0, this.recentErrors.size - this.MAX_RECENT_ERRORS + 10);
      entriesToRemove.forEach(([key]) => this.recentErrors.delete(key));
    }
  }

  /**
   * Log error using the existing LoggerService
   */
  private logError(errorContext: ErrorContext): void {
    const logMessage = `${errorContext.category.toUpperCase()} Error in ${errorContext.component || 'Unknown'}`;
    const logData = {
      error: {
        name: errorContext.error.name,
        message: errorContext.error.message,
        stack: errorContext.error instanceof Error ? errorContext.error.stack : undefined
      },
      context: {
        severity: errorContext.severity,
        category: errorContext.category,
        component: errorContext.component,
        action: errorContext.action,
        url: errorContext.url,
        recoverable: errorContext.recoverable,
        retryable: errorContext.retryable
      },
      session: {
        userId: errorContext.userId,
        sessionId: errorContext.sessionId,
        userAgent: errorContext.userAgent,
        timestamp: errorContext.timestamp.toISOString()
      }
    };

    // Use appropriate log level based on severity
    switch (errorContext.severity) {
      case ErrorSeverity.CRITICAL:
      case ErrorSeverity.HIGH:
        this.logger.error(logMessage, errorContext.error, errorContext.component || 'GlobalErrorHandler');
        break;
      case ErrorSeverity.MEDIUM:
        this.logger.warn(logMessage, logData, errorContext.component || 'GlobalErrorHandler');
        break;
      case ErrorSeverity.LOW:
        this.logger.info(logMessage, logData, errorContext.component || 'GlobalErrorHandler');
        break;
    }
  }

  /**
   * Update loading states based on error
   */
  private updateLoadingStates(errorContext: ErrorContext): void {
    // Clear loading states for errors that should stop loading indicators
    if (errorContext.category === ErrorCategory.API) {
      this.loadingStateService.setGlobalLoading(false);
      this.loadingStateService.setGlobalError(this.getUserFriendlyMessage(errorContext));
    }
  }

  /**
   * Notify user using existing notification service
   */
  private notifyUser(errorContext: ErrorContext): void {
    const message = this.getUserFriendlyMessage(errorContext);
    
    switch (errorContext.severity) {
      case ErrorSeverity.CRITICAL:
        this.notificationService.showError(message);
        break;
      
      case ErrorSeverity.HIGH:
        this.notificationService.showError(message);
        break;
      
      case ErrorSeverity.MEDIUM:
        this.notificationService.showWarning(message);
        break;
      
      case ErrorSeverity.LOW:
        this.notificationService.showInfo(message);
        break;
    }
  }

  /**
   * Get user-friendly error message
   */
  private getUserFriendlyMessage(errorContext: ErrorContext): string {
    if (errorContext.error instanceof HttpErrorResponse) {
      return this.getHttpErrorMessage(errorContext.error);
    }

    // Runtime error messages
    if (errorContext.error.name === 'ChunkLoadError') {
      return 'Failed to load application resources. Please refresh the page.';
    }

    if (errorContext.category === ErrorCategory.NETWORK) {
      return 'Network connection issue. Please check your internet connection.';
    }

    // Generic message for unknown errors
    return 'An unexpected error occurred. Please try again or contact support if the problem persists.';
  }

  /**
   * Get user-friendly HTTP error message
   */
  private getHttpErrorMessage(httpError: HttpErrorResponse): string {
    switch (httpError.status) {
      case 0:
        return 'Unable to connect to the server. Please check your internet connection.';
      case 400:
        return 'Invalid request. Please check your input and try again.';
      case 401:
        return 'Authentication required. Please log in and try again.';
      case 403:
        return 'Access denied. You don\'t have permission to perform this action.';
      case 404:
        return 'The requested resource was not found.';
      case 408:
        return 'Request timeout. Please try again.';
      case 429:
        return 'Too many requests. Please wait a moment and try again.';
      case 500:
        return 'Server error. Our team has been notified.';
      case 502:
      case 503:
        return 'Service temporarily unavailable. Please try again in a few minutes.';
      case 504:
        return 'Gateway timeout. Please try again.';
      default:
        return `Server error (${httpError.status}). Please try again or contact support.`;
    }
  }

  /**
   * Report error to external services using ErrorTrackingService
   */
  private reportError(errorContext: ErrorContext): void {
    try {
      // Use the ErrorTrackingService for external reporting
      this.errorTracking.trackError(errorContext);
      
      this.logger.debug('Error reported to tracking service', {
        component: errorContext.component,
        action: errorContext.action,
        severity: errorContext.severity,
        category: errorContext.category
      }, 'GlobalErrorHandler');
    } catch (trackingError) {
      // Don't let tracking errors break the application
      this.logger.warn('Failed to track error', trackingError, 'GlobalErrorHandler');
    }
  }

  /**
   * Initiate error recovery mechanisms
   */
  private initiateRecovery(errorContext: ErrorContext): void {
    // For now, just clear error states
    // More sophisticated recovery can be added later
    // Clear error state by setting loading to false
    this.loadingStateService.setGlobalLoading(false);
    
    this.logger.info('Initiating error recovery', {
      error: errorContext.error.message,
      component: errorContext.component,
      action: errorContext.action
    }, 'GlobalErrorHandler');
  }

  /**
   * Utility methods
   */
  private generateSessionId(): string {
    // Use cryptographically secure random generation
    const array = new Uint8Array(16);
    crypto.getRandomValues(array);
    return Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('');
  }

  private getCurrentUserId(): string | undefined {
    // Placeholder for user ID retrieval
    // Will be implemented when authentication is added
    return undefined;
  }
}