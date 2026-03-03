import { HttpInterceptorFn, HttpErrorResponse, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, retry, delay, throwError, timer } from 'rxjs';
import { GlobalErrorHandler } from '../services/infrastructure/global-error-handler.service';
import { LoggerService } from '../services/infrastructure/logger.service';

/**
 * Configuration for retry logic
 */
interface RetryConfig {
  maxRetries: number;
  retryDelay: number;
  retryableStatuses: number[];
  exponentialBackoff: boolean;
}

/**
 * Default retry configuration
 */
const DEFAULT_RETRY_CONFIG: RetryConfig = {
  maxRetries: 3,
  retryDelay: 1000, // 1 second
  retryableStatuses: [0, 408, 429, 500, 502, 503, 504],
  exponentialBackoff: true
};

/**
 * URLs that should not trigger global error handling
 * These are typically handled by specific components
 */
const EXCLUDED_URLS = [
  '/api/search', // Search errors are handled by search components
  '/api/health', // Health check errors shouldn't notify users
  '/api/analytics', // Analytics errors shouldn't interrupt user experience
  '/automated-dcf-analysis', // DCF analysis endpoints - expensive operations, no auto-retry
  '/bullbeargpt/api/notebook', // Notebook endpoints - SSE streaming, handled by components
  '/bullbeargpt/api/chat', // Chat endpoints - handled by chat components
];

/**
 * HTTP Error Handling Interceptor
 * 
 * Provides centralized error handling for all HTTP requests with:
 * - Automatic retry for transient errors
 * - Global error reporting and user notification
 * - Context-aware error handling
 * - Smart error categorization
 * - Integration with existing error boundary components
 * 
 * Features:
 * - Retries network errors and server errors automatically
 * - Exponential backoff for retry delays
 * - Excludes certain URLs from global error handling
 * - Preserves request context for better error reporting
 * - Integrates with GlobalErrorHandler for consistent error processing
 * 
 * @example
 * ```typescript
 * // Automatically applied to all HTTP requests
 * // Configuration in app.config.ts:
 * provideHttpClient(
 *   withInterceptors([errorHandlingInterceptor])
 * )
 * ```
 */
export const errorHandlingInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const globalErrorHandler = inject(GlobalErrorHandler);
  const logger = inject(LoggerService);

  // Extract context information from the request
  const context = extractRequestContext(req);

  // Check if this is a DCF analysis or notebook/chat endpoint that should not be retried
  // These are expensive, long-running operations or SSE streams that shouldn't be automatically retried
  const isDCFAnalysisEndpoint = req.url.includes('/automated-dcf-analysis') ||
    req.url.includes('/api-s/valuate') ||
    req.url.includes('/valuation') ||
    req.url.includes('/bullbeargpt');

  return next(req).pipe(
    retry({
      count: isDCFAnalysisEndpoint ? 0 : DEFAULT_RETRY_CONFIG.maxRetries, // Disable retries for DCF endpoints
      delay: (error: HttpErrorResponse, retryCount: number) => {
        // Only retry specific error types
        if (!shouldRetryError(error)) {
          throw error;
        }

        // Calculate delay with exponential backoff
        const delayMs = DEFAULT_RETRY_CONFIG.exponentialBackoff
          ? DEFAULT_RETRY_CONFIG.retryDelay * Math.pow(2, retryCount - 1)
          : DEFAULT_RETRY_CONFIG.retryDelay;

        logger.info(`Retrying HTTP request (attempt ${retryCount}/${DEFAULT_RETRY_CONFIG.maxRetries})`, {
          url: req.url,
          method: req.method,
          status: error.status,
          delayMs
        }, 'ErrorHandlingInterceptor');

        return timer(delayMs);
      }
    }),
    catchError((error: HttpErrorResponse) => {
      // Handle the error through global error handler
      handleHttpError(error, context, globalErrorHandler, logger);

      // Re-throw the error so components can still handle it locally if needed
      return throwError(() => error);
    })
  );
};

/**
 * Extract context information from the HTTP request
 */
function extractRequestContext(req: HttpRequest<unknown>): {
  url: string;
  method: string;
  action: string;
  shouldHandle: boolean;
} {
  const url = req.url;
  const method = req.method;

  // Determine action based on URL patterns
  let action = 'http_request';
  if (url.includes('/search')) {
    action = 'search_companies';
  } else if (url.includes('/dcf') || url.includes('/valuation')) {
    action = 'dcf_analysis';
  } else if (url.includes('/company')) {
    action = 'load_company_data';
  } else if (url.includes('/financial')) {
    action = 'load_financial_data';
  }

  // Check if this URL should be excluded from global error handling
  const shouldHandle = !EXCLUDED_URLS.some(excludedUrl => url.includes(excludedUrl));

  return {
    url,
    method,
    action,
    shouldHandle
  };
}

/**
 * Determine if an error should be retried
 */
function shouldRetryError(error: HttpErrorResponse): boolean {
  // Don't retry client errors (4xx) except for specific cases
  if (error.status >= 400 && error.status < 500) {
    // Retry specific client errors
    return [408, 429].includes(error.status); // Timeout and rate limit
  }

  // Retry network errors and server errors
  return DEFAULT_RETRY_CONFIG.retryableStatuses.includes(error.status);
}

/**
 * Handle HTTP error through global error handler
 */
function handleHttpError(
  error: HttpErrorResponse,
  context: ReturnType<typeof extractRequestContext>,
  globalErrorHandler: GlobalErrorHandler,
  logger: LoggerService
): void {
  // Log the error with context
  logger.error(`HTTP ${error.status} Error`, error, 'ErrorHandlingInterceptor');

  // Only handle through global error handler if not excluded
  if (context.shouldHandle) {
    globalErrorHandler.handleHttpError(error, {
      url: context.url,
      action: context.action
    });
  } else {
    logger.debug(`Skipping global error handling for excluded URL: ${context.url}`, {
      url: context.url,
      status: error.status
    }, 'ErrorHandlingInterceptor');
  }
}

/**
 * Extract relevant headers for error logging
 */
function extractRelevantHeaders(error: HttpErrorResponse): Record<string, string> {
  const relevantHeaders: Record<string, string> = {};

  // Headers that might be useful for debugging
  const headersToExtract = [
    'content-type',
    'x-request-id',
    'x-correlation-id',
    'retry-after',
    'x-ratelimit-remaining',
    'x-ratelimit-reset'
  ];

  headersToExtract.forEach(headerName => {
    const value = error.headers?.get(headerName);
    if (value) {
      relevantHeaders[headerName] = value;
    }
  });

  return relevantHeaders;
}

/**
 * Alternative interceptor configuration for specific use cases
 */
export interface ErrorInterceptorConfig {
  retryConfig?: Partial<RetryConfig>;
  excludedUrls?: string[];
  enableGlobalHandling?: boolean;
}

/**
 * Factory function to create a customized error handling interceptor
 */
export function createErrorHandlingInterceptor(
  config: ErrorInterceptorConfig = {}
): HttpInterceptorFn {
  const retryConfig = { ...DEFAULT_RETRY_CONFIG, ...config.retryConfig };
  const excludedUrls = [...EXCLUDED_URLS, ...(config.excludedUrls || [])];
  const enableGlobalHandling = config.enableGlobalHandling ?? true;

  return (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
    const globalErrorHandler = inject(GlobalErrorHandler);
    const logger = inject(LoggerService);

    const context = {
      ...extractRequestContext(req),
      shouldHandle: enableGlobalHandling &&
        !excludedUrls.some(excludedUrl => req.url.includes(excludedUrl))
    };

    return next(req).pipe(
      retry({
        count: retryConfig.maxRetries,
        delay: (error: HttpErrorResponse, retryCount: number) => {
          if (!shouldRetryError(error)) {
            throw error;
          }

          const delayMs = retryConfig.exponentialBackoff
            ? retryConfig.retryDelay * Math.pow(2, retryCount - 1)
            : retryConfig.retryDelay;

          logger.info(`Retrying HTTP request (attempt ${retryCount}/${retryConfig.maxRetries})`, {
            url: req.url,
            method: req.method,
            status: error.status,
            delayMs
          }, 'ErrorHandlingInterceptor');

          return timer(delayMs);
        }
      }),
      catchError((error: HttpErrorResponse) => {
        handleHttpError(error, context, globalErrorHandler, logger);
        return throwError(() => error);
      })
    );
  };
}

/**
 * Preset configurations for common scenarios
 */
export const ERROR_INTERCEPTOR_PRESETS = {
  /**
   * Configuration for API endpoints that should have aggressive retry
   */
  CRITICAL_API: {
    retryConfig: {
      maxRetries: 5,
      retryDelay: 500,
      exponentialBackoff: true
    },
    enableGlobalHandling: true
  },

  /**
   * Configuration for search/autocomplete endpoints
   */
  SEARCH_API: {
    retryConfig: {
      maxRetries: 1,
      retryDelay: 300,
      exponentialBackoff: false
    },
    enableGlobalHandling: false
  },

  /**
   * Configuration for analytics/telemetry endpoints
   */
  ANALYTICS_API: {
    retryConfig: {
      maxRetries: 2,
      retryDelay: 1000,
      exponentialBackoff: true
    },
    enableGlobalHandling: false,
    excludedUrls: ['/api/analytics', '/api/telemetry', '/api/metrics']
  },

  /**
   * Configuration for development/testing
   */
  DEVELOPMENT: {
    retryConfig: {
      maxRetries: 1,
      retryDelay: 100,
      exponentialBackoff: false
    },
    enableGlobalHandling: true
  }
} as const;
