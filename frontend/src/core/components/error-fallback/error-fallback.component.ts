import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Error information for the fallback component
 */
export interface ErrorFallbackContext {
  error: Error;
  retryCount: number;
  maxRetries: number;
  canRetry: boolean;
}

/**
 * Reusable Error Fallback Component
 * 
 * A standardized error fallback UI that can be used with the ErrorBoundaryDirective.
 * Provides consistent error display with retry and recovery options.
 * 
 * Features:
 * - User-friendly error messages
 * - Retry button with attempt tracking
 * - Clear error action
 * - Support action for critical errors
 * - Responsive design with Tailwind CSS
 * - Accessibility features
 * 
 * @example
 * ```html
 * <!-- Basic usage in error boundary -->
 * <ng-template #errorTemplate let-error="error" let-retry="retry" let-clear="clear">
 *   <app-error-fallback 
 *     [error]="error" 
 *     [canRetry]="true"
 *     (retryClicked)="retry()"
 *     (clearClicked)="clear()">
 *   </app-error-fallback>
 * </ng-template>
 * 
 * <!-- Advanced usage with custom context -->
 * <app-error-fallback 
 *   [error]="errorContext.error"
 *   [retryCount]="errorContext.retryCount"
 *   [maxRetries]="errorContext.maxRetries"
 *   [canRetry]="errorContext.canRetry"
 *   [showSupportLink]="true"
 *   [customTitle]="'Data Loading Failed'"
 *   (retryClicked)="handleRetry()"
 *   (clearClicked)="handleClear()"
 *   (supportClicked)="contactSupport()">
 * </app-error-fallback>
 * ```
 */
@Component({
  selector: 'app-error-fallback',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="error-fallback-container" 
         role="alert" 
         aria-live="assertive"
         [class]="containerClasses">
      
      <!-- Error Icon -->
      <div class="error-icon">
        <svg class="w-12 h-12 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 15.5c-.77.833.192 2.5 1.732 2.5z">
          </path>
        </svg>
      </div>

      <!-- Error Content -->
      <div class="error-content">
        <!-- Title -->
        <h3 class="error-title">
          {{ customTitle || getDefaultTitle() }}
        </h3>

        <!-- Message -->
        <p class="error-message">
          {{ customMessage || getUserFriendlyMessage() }}
        </p>

        <!-- Technical Details (collapsible in development) -->
        <details *ngIf="showTechnicalDetails" class="error-details">
          <summary class="error-details-summary">
            Technical Details
          </summary>
          <div class="error-details-content">
            <p><strong>Error:</strong> {{ error?.name || 'Unknown' }}</p>
            <p><strong>Message:</strong> {{ error?.message || 'No message available' }}</p>
            <p *ngIf="retryCount > 0"><strong>Retry Attempts:</strong> {{ retryCount }}/{{ maxRetries }}</p>
          </div>
        </details>

        <!-- Retry Information -->
        <div *ngIf="retryCount > 0" class="retry-info">
          <p class="text-sm text-gray-600 dark:text-gray-400">
            Attempted {{ retryCount }} of {{ maxRetries }} retries
          </p>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="error-actions">
        <!-- Retry Button -->
        <button 
          *ngIf="canRetry && showRetryButton"
          (click)="onRetryClicked()"
          class="retry-button"
          [disabled]="isRetrying"
          type="button">
          <svg *ngIf="!isRetrying" class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15">
            </path>
          </svg>
          <svg *ngIf="isRetrying" class="w-4 h-4 mr-2 animate-spin" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15">
            </path>
          </svg>
          {{ isRetrying ? 'Retrying...' : 'Try Again' }}
        </button>

        <!-- Clear/Dismiss Button -->
        <button 
          (click)="onClearClicked()"
          class="clear-button"
          type="button">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M6 18L18 6M6 6l12 12">
            </path>
          </svg>
          {{ clearButtonText }}
        </button>

        <!-- Support Link -->
        <button 
          *ngIf="showSupportLink"
          (click)="onSupportClicked()"
          class="support-button"
          type="button">
          <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" 
                  d="M18.364 5.636l-3.536 3.536m0 5.656l3.536 3.536M9.172 9.172L5.636 5.636m3.536 9.192L5.636 18.364M12 2.25a9.75 9.75 0 109.75 9.75A9.75 9.75 0 0012 2.25z">
            </path>
          </svg>
          Contact Support
        </button>
      </div>
    </div>
  `,
  styles: [`
    @reference '../../../styles.scss';
    
    .error-fallback-container {
      @apply flex flex-col items-center justify-center p-8 text-center;
      @apply bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800;
      @apply rounded-lg shadow-sm;
      min-height: 200px;
    }

    .error-fallback-container.compact {
      @apply p-4;
      min-height: 120px;
    }

    .error-fallback-container.inline {
      @apply p-3 flex-row text-left;
      min-height: auto;
    }

    .error-icon {
      @apply mb-4;
    }

    .compact .error-icon,
    .inline .error-icon {
      @apply mb-2;
    }

    .inline .error-icon {
      @apply mr-4 mb-0;
    }

    .error-content {
      @apply mb-6 max-w-md;
    }

    .compact .error-content,
    .inline .error-content {
      @apply mb-4;
    }

    .error-title {
      @apply text-lg font-semibold text-red-900 dark:text-red-100 mb-2;
    }

    .compact .error-title {
      @apply text-base mb-1;
    }

    .error-message {
      @apply text-red-700 dark:text-red-200 mb-4;
    }

    .compact .error-message {
      @apply text-sm mb-2;
    }

    .error-details {
      @apply mt-4 text-left bg-red-100 dark:bg-red-900/40 rounded p-3;
    }

    .error-details-summary {
      @apply cursor-pointer font-medium text-red-800 dark:text-red-200;
      @apply hover:text-red-900 dark:hover:text-red-100;
    }

    .error-details-content {
      @apply mt-2 text-sm text-red-700 dark:text-red-300 space-y-1;
    }

    .retry-info {
      @apply mt-2;
    }

    .error-actions {
      @apply flex flex-wrap gap-3 justify-center;
    }

    .inline .error-actions {
      @apply justify-start ml-auto;
    }

    .retry-button {
      @apply inline-flex items-center px-4 py-2;
      @apply bg-blue-600 hover:bg-blue-700 text-white;
      @apply border border-transparent rounded-md;
      @apply font-medium text-sm transition-colors;
      @apply focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2;
      @apply disabled:opacity-50 disabled:cursor-not-allowed;
    }

    .clear-button {
      @apply inline-flex items-center px-4 py-2;
      @apply bg-gray-600 hover:bg-gray-700 text-white;
      @apply border border-transparent rounded-md;
      @apply font-medium text-sm transition-colors;
      @apply focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2;
    }

    .support-button {
      @apply inline-flex items-center px-4 py-2;
      @apply bg-red-600 hover:bg-red-700 text-white;
      @apply border border-transparent rounded-md;
      @apply font-medium text-sm transition-colors;
      @apply focus:outline-none focus:ring-2 focus:ring-red-500 focus:ring-offset-2;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ErrorFallbackComponent {
  /**
   * The error object
   */
  @Input() error?: Error;

  /**
   * Current retry attempt count
   */
  @Input() retryCount = 0;

  /**
   * Maximum number of retry attempts
   */
  @Input() maxRetries = 3;

  /**
   * Whether retry is available
   */
  @Input() canRetry = true;

  /**
   * Whether to show the retry button
   */
  @Input() showRetryButton = true;

  /**
   * Whether to show technical error details
   */
  @Input() showTechnicalDetails = false;

  /**
   * Whether to show the support link
   */
  @Input() showSupportLink = false;

  /**
   * Custom title for the error
   */
  @Input() customTitle?: string;

  /**
   * Custom message for the error
   */
  @Input() customMessage?: string;

  /**
   * Text for the clear/dismiss button
   */
  @Input() clearButtonText = 'Dismiss';

  /**
   * Display variant: 'default' | 'compact' | 'inline'
   */
  @Input() variant: 'default' | 'compact' | 'inline' = 'default';

  /**
   * Whether the retry operation is currently in progress
   */
  @Input() isRetrying = false;

  /**
   * Emitted when the retry button is clicked
   */
  @Output() retryClicked = new EventEmitter<void>();

  /**
   * Emitted when the clear/dismiss button is clicked
   */
  @Output() clearClicked = new EventEmitter<void>();

  /**
   * Emitted when the support button is clicked
   */
  @Output() supportClicked = new EventEmitter<void>();

  /**
   * Get CSS classes for the container based on variant
   */
  get containerClasses(): string {
    return this.variant !== 'default' ? this.variant : '';
  }

  /**
   * Get default title based on error type
   */
  getDefaultTitle(): string {
    if (!this.error) return 'Something went wrong';

    if (this.error.name === 'ChunkLoadError') {
      return 'Loading Error';
    }

    if (this.error.message?.includes('network') || this.error.message?.includes('fetch')) {
      return 'Connection Error';
    }

    if (this.error.name === 'ValidationError') {
      return 'Validation Error';
    }

    return 'Unexpected Error';
  }

  /**
   * Get user-friendly error message
   */
  getUserFriendlyMessage(): string {
    if (!this.error) return 'An unexpected error occurred. Please try again.';

    if (this.error.name === 'ChunkLoadError') {
      return 'Failed to load application resources. Please refresh the page or try again.';
    }

    if (this.error.message?.includes('network') || this.error.message?.includes('fetch')) {
      return 'Unable to connect to the server. Please check your internet connection and try again.';
    }

    if (this.error.name === 'ValidationError') {
      return 'Please check your input and try again.';
    }

    // Generic fallback message
    return 'Something unexpected happened. Please try again or contact support if the problem persists.';
  }

  /**
   * Handle retry button click
   */
  onRetryClicked(): void {
    this.retryClicked.emit();
  }

  /**
   * Handle clear button click
   */
  onClearClicked(): void {
    this.clearClicked.emit();
  }

  /**
   * Handle support button click
   */
  onSupportClicked(): void {
    this.supportClicked.emit();
  }
}