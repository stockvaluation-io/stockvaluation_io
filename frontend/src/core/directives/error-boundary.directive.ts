import {
  Directive,
  Input,
  Output,
  EventEmitter,
  TemplateRef,
  ViewContainerRef,
  OnInit,
  OnDestroy,
  inject,
  ErrorHandler
} from '@angular/core';
import { Subject, catchError, of } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { GlobalErrorHandler, ErrorSeverity, ErrorCategory } from '../services/infrastructure/global-error-handler.service';
import { PlatformDetectionService } from '../services';

/**
 * Error boundary configuration for customizing error handling behavior
 */
export interface ErrorBoundaryConfig {
  /**
   * Component name for error context
   */
  component?: string;
  
  /**
   * Action being performed when error occurred
   */
  action?: string;
  
  /**
   * Whether to show fallback UI when error occurs
   */
  showFallback?: boolean;
  
  /**
   * Whether to automatically retry failed operations
   */
  enableRetry?: boolean;
  
  /**
   * Maximum number of retry attempts
   */
  maxRetries?: number;
  
  /**
   * Custom error message for user notification
   */
  customMessage?: string;
  
  /**
   * Whether to suppress global error handling for this boundary
   */
  suppressGlobal?: boolean;
  
  /**
   * Error severity override
   */
  severity?: ErrorSeverity;
  
  /**
   * Error category override
   */
  category?: ErrorCategory;
}

/**
 * Error information emitted by the directive
 */
export interface ErrorBoundaryEvent {
  error: Error;
  component?: string;
  action?: string;
  timestamp: Date;
  retryCount: number;
}

/**
 * Error Boundary Directive
 * 
 * Provides component-level error handling with automatic recovery and fallback UI.
 * Integrates with the global error handler while allowing local error management.
 * 
 * Features:
 * - Automatic error catching and handling
 * - Fallback UI display for graceful degradation
 * - Retry mechanisms with configurable attempts
 * - Integration with global error reporting
 * - Custom error context and categorization
 * - Event emission for parent component handling
 * 
 * @example
 * ```html
 * <!-- Basic usage with fallback template -->
 * <div *appErrorBoundary="errorConfig; fallback: errorTemplate; let error">
 *   <app-risky-component></app-risky-component>
 * </div>
 * 
 * <ng-template #errorTemplate let-error="error" let-retry="retry">
 *   <div class="error-fallback">
 *     <p>Something went wrong: {{error.message}}</p>
 *     <button (click)="retry()">Try Again</button>
 *   </div>
 * </ng-template>
 * 
 * <!-- Advanced usage with configuration -->
 * <div *appErrorBoundary="{
 *   component: 'DataTableComponent',
 *   action: 'loadData',
 *   enableRetry: true,
 *   maxRetries: 3,
 *   showFallback: true
 * }; 
 * fallback: errorTemplate;
 * (errorCaught)="onError($event)"
 * (retryAttempted)="onRetry($event)">
 *   <app-data-table [data]="data$ | async"></app-data-table>
 * </div>
 * ```
 */
@Directive({
  selector: '[appErrorBoundary]',
  standalone: true
})
export class ErrorBoundaryDirective implements OnInit, OnDestroy {
  private viewContainer = inject(ViewContainerRef);
  private templateRef = inject(TemplateRef<any>);
  private globalErrorHandler = inject(GlobalErrorHandler);
  private platformDetection = inject(PlatformDetectionService);
  private destroy$ = new Subject<void>();
  
  private retryCount = 0;
  private currentError: Error | null = null;
  private isShowingFallback = false;

  /**
   * Error boundary configuration
   */
  @Input('appErrorBoundary') config: ErrorBoundaryConfig = {};

  /**
   * Fallback template to show when error occurs
   */
  @Input('appErrorBoundaryFallback') fallbackTemplate?: TemplateRef<any>;

  /**
   * Emitted when an error is caught by the boundary
   */
  @Output() errorCaught = new EventEmitter<ErrorBoundaryEvent>();

  /**
   * Emitted when a retry attempt is made
   */
  @Output() retryAttempted = new EventEmitter<ErrorBoundaryEvent>();

  /**
   * Emitted when error is recovered (successful retry or manual clear)
   */
  @Output() errorRecovered = new EventEmitter<void>();

  ngOnInit(): void {
    this.renderContent();
    this.setupErrorHandling();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Manually trigger an error for testing or external error handling
   */
  handleError(error: Error, context?: { action?: string }): void {
    this.processError(error, context?.action);
  }

  /**
   * Manually retry the failed operation
   */
  retry(): void {
    if (!this.currentError) return;

    const maxRetries = this.config.maxRetries ?? 3;
    if (this.retryCount >= maxRetries) {
      this.emitErrorEvent('Maximum retry attempts reached');
      return;
    }

    this.retryCount++;
    this.emitRetryEvent();
    this.clearError();
  }

  /**
   * Manually clear the error state and restore normal content
   */
  clearError(): void {
    this.currentError = null;
    this.isShowingFallback = false;
    this.renderContent();
    this.errorRecovered.emit();
  }

  /**
   * Check if the boundary is currently in an error state
   */
  hasError(): boolean {
    return this.currentError !== null;
  }

  /**
   * Get the current error information
   */
  getCurrentError(): Error | null {
    return this.currentError;
  }

  /**
   * Get the current retry count
   */
  getRetryCount(): number {
    return this.retryCount;
  }

  /**
   * Setup error handling for observables and promises in the component tree
   */
  private setupErrorHandling(): void {
    // Note: This is a simplified implementation
    // In a real-world scenario, you might need to use Zone.js patches
    // or other techniques to catch errors in child components
    
    // Set up global error listener for this boundary (only in browser)
    const windowObj = this.platformDetection.getWindow();
    if (windowObj) {
      windowObj.addEventListener('error', this.handleGlobalError.bind(this));
      windowObj.addEventListener('unhandledrejection', this.handleUnhandledRejection.bind(this));
    }
  }

  /**
   * Handle global JavaScript errors
   */
  private handleGlobalError(event: ErrorEvent): void {
    // Only handle errors if they seem to be related to this component
    // This is a basic implementation - more sophisticated targeting would be needed
    if (this.shouldHandleError(event.error)) {
      event.preventDefault(); // Prevent default browser error handling
      this.processError(event.error);
    }
  }

  /**
   * Handle unhandled promise rejections
   */
  private handleUnhandledRejection(event: PromiseRejectionEvent): void {
    const error = event.reason instanceof Error ? event.reason : new Error(String(event.reason));
    
    if (this.shouldHandleError(error)) {
      event.preventDefault(); // Prevent default browser error handling
      this.processError(error, 'promise_rejection');
    }
  }

  /**
   * Determine if this boundary should handle the given error
   * This is a simplified implementation - in practice, you'd need more sophisticated
   * error attribution to determine which boundary should handle which error
   */
  private shouldHandleError(error: Error): boolean {
    // Basic heuristic: handle errors if we're currently showing content
    // In a more sophisticated implementation, you might:
    // - Check error stack traces for component references
    // - Use Zone.js to track which boundary is active
    // - Implement error attribution mechanisms
    return !this.isShowingFallback && this.viewContainer.length > 0;
  }

  /**
   * Process an error through the boundary
   */
  private processError(error: Error, action?: string): void {
    this.currentError = error;
    
    // Create error context
    const errorContext = {
      error,
      component: this.config.component || 'ErrorBoundary',
      action: action || this.config.action || 'unknown',
      timestamp: new Date(),
      retryCount: this.retryCount
    };

    // Emit error event
    this.errorCaught.emit(errorContext);

    // Handle error globally unless suppressed
    if (!this.config.suppressGlobal) {
      this.globalErrorHandler.handleErrorWithContext(error, {
        component: this.config.component,
        action: action || this.config.action,
        severity: this.config.severity,
        recoverable: this.config.enableRetry ?? true
      });
    }

    // Show fallback UI if configured
    if (this.config.showFallback !== false && this.fallbackTemplate) {
      this.showFallback();
    }

    // Auto-retry if enabled and under limit
    if (this.config.enableRetry && this.retryCount < (this.config.maxRetries ?? 3)) {
      setTimeout(() => {
        this.retry();
      }, this.calculateRetryDelay());
    }
  }

  /**
   * Calculate delay for retry attempt (exponential backoff)
   */
  private calculateRetryDelay(): number {
    const baseDelay = 1000; // 1 second
    return baseDelay * Math.pow(2, this.retryCount); // Exponential backoff
  }

  /**
   * Render the fallback UI
   */
  private showFallback(): void {
    if (!this.fallbackTemplate) return;

    this.isShowingFallback = true;
    this.viewContainer.clear();
    
    const context = {
      $implicit: this.currentError,
      error: this.currentError,
      retry: () => this.retry(),
      clear: () => this.clearError(),
      retryCount: this.retryCount,
      maxRetries: this.config.maxRetries ?? 3,
      canRetry: this.retryCount < (this.config.maxRetries ?? 3)
    };
    
    this.viewContainer.createEmbeddedView(this.fallbackTemplate, context);
  }

  /**
   * Render the normal content
   */
  private renderContent(): void {
    this.viewContainer.clear();
    this.viewContainer.createEmbeddedView(this.templateRef);
  }

  /**
   * Emit error event
   */
  private emitErrorEvent(message?: string): void {
    const error = this.currentError || new Error(message || 'Unknown error');
    this.errorCaught.emit({
      error,
      component: this.config.component,
      action: this.config.action,
      timestamp: new Date(),
      retryCount: this.retryCount
    });
  }

  /**
   * Emit retry event
   */
  private emitRetryEvent(): void {
    if (!this.currentError) return;
    
    this.retryAttempted.emit({
      error: this.currentError,
      component: this.config.component,
      action: this.config.action,
      timestamp: new Date(),
      retryCount: this.retryCount
    });
  }
}

/**
 * Utility function to create error boundary configurations
 */
export function createErrorBoundaryConfig(config: Partial<ErrorBoundaryConfig>): ErrorBoundaryConfig {
  return {
    showFallback: true,
    enableRetry: false,
    maxRetries: 3,
    suppressGlobal: false,
    ...config
  };
}

/**
 * Pre-configured error boundary configurations for common scenarios
 */
export const ERROR_BOUNDARY_PRESETS = {
  /**
   * For API data loading components
   */
  API_LOADING: createErrorBoundaryConfig({
    category: ErrorCategory.API,
    enableRetry: true,
    maxRetries: 3,
    showFallback: true
  }),

  /**
   * For form components
   */
  FORM_VALIDATION: createErrorBoundaryConfig({
    category: ErrorCategory.VALIDATION,
    severity: ErrorSeverity.LOW,
    enableRetry: false,
    showFallback: true
  }),

  /**
   * For critical application sections
   */
  CRITICAL_SECTION: createErrorBoundaryConfig({
    severity: ErrorSeverity.HIGH,
    enableRetry: true,
    maxRetries: 2,
    showFallback: true,
    suppressGlobal: false
  }),

  /**
   * For non-critical UI components
   */
  UI_COMPONENT: createErrorBoundaryConfig({
    severity: ErrorSeverity.LOW,
    enableRetry: false,
    showFallback: true,
    suppressGlobal: true
  }),

  /**
   * For network-dependent components
   */
  NETWORK_DEPENDENT: createErrorBoundaryConfig({
    category: ErrorCategory.NETWORK,
    enableRetry: true,
    maxRetries: 5,
    showFallback: true
  })
} as const;