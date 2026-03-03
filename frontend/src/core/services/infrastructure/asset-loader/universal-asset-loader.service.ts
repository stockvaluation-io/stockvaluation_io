import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';
import { AssetLoadingStrategy } from './asset-loading-strategy.interface';
import { HttpAssetLoadingStrategy } from './http-asset-loading.strategy';
import { FilesystemAssetLoadingStrategy } from './filesystem-asset-loading.strategy';
import { AssetPathResolver } from '../asset-path-resolver.service';
import { LoggerService } from '../logger.service';
import { ErrorRecoveryService } from '../error-recovery.service';
import { AssetErrorContext, AssetErrorClassifier } from '../../../config/error-recovery.config';

export interface AssetLoadOptions {
  enableFallback?: boolean;
  retryAttempts?: number;
  fallbackData?: any;
}

@Injectable({
  providedIn: 'root'
})
export class UniversalAssetLoaderService {
  private strategies: AssetLoadingStrategy[];

  constructor(
    private httpStrategy: HttpAssetLoadingStrategy,
    private filesystemStrategy: FilesystemAssetLoadingStrategy,
    private pathResolver: AssetPathResolver,
    private logger: LoggerService,
    private errorRecovery: ErrorRecoveryService
  ) {
    this.strategies = [this.httpStrategy, this.filesystemStrategy];
  }

  /**
   * Load an asset using the appropriate strategy for the current platform
   * @param filename Name of the asset file to load
   * @param options Loading options including fallback behavior
   * @returns Observable with the parsed asset content
   */
  loadAsset<T>(filename: string, options: AssetLoadOptions = {}): Observable<T> {
    const strategy = this.getApplicableStrategy();
    
    if (!strategy) {
      const error = new Error('No applicable asset loading strategy found');
      this.logger.error('Asset loading failed', error, 'UniversalAssetLoaderService');
      return throwError(() => error);
    }

    const assetPath = this.pathResolver.resolveAssetPath(filename);
    const startTime = Date.now();
    
    this.logger.debug(
      `Loading asset: ${filename} using ${strategy.getStrategyName()}`,
      { assetPath, filename },
      'UniversalAssetLoaderService'
    );

    // Create error context for recovery
    const errorContext: AssetErrorContext = {
      filename,
      assetType: this.getAssetType(filename),
      errorType: '',
      attemptNumber: 1,
      timestamp: startTime,
      url: assetPath
    };

    // Create source observable with retry logic
    const source$ = strategy.loadAsset<T>(assetPath);
    const withRetry$ = this.errorRecovery.createRetryLogic(source$, errorContext);
    
    return withRetry$.pipe(
      tap(() => {
        const loadTime = Date.now() - startTime;
        this.logger.debug(
          `Asset loaded successfully: ${filename} in ${loadTime}ms`,
          { filename, loadTime },
          'UniversalAssetLoaderService'
        );
      }),
      catchError(error => this.handleLoadingError<T>(error, filename, options, errorContext))
    );
  }

  /**
   * Get the first applicable loading strategy for the current environment
   * @returns The appropriate loading strategy or null if none found
   */
  private getApplicableStrategy(): AssetLoadingStrategy | null {
    return this.strategies.find(strategy => strategy.canHandle()) || null;
  }

  /**
   * Handle asset loading errors with fallback mechanisms
   * @param error The loading error
   * @param filename Name of the asset that failed to load
   * @param options Loading options including fallback data
   * @param errorContext Error context for recovery
   * @returns Observable with fallback data or re-thrown error
   */
  private handleLoadingError<T>(
    error: any, 
    filename: string, 
    options: AssetLoadOptions,
    errorContext: AssetErrorContext
  ): Observable<T> {
    // Update error context with error details
    errorContext.errorType = AssetErrorClassifier.classifyError(error);
    errorContext.responseTime = Date.now() - errorContext.timestamp;
    
    if (error?.status) {
      errorContext.statusCode = error.status;
    }

    this.logger.error(
      `Failed to load asset: ${filename}`,
      { error, errorContext },
      'UniversalAssetLoaderService'
    );

    // Attempt error recovery first
    return this.errorRecovery.attemptRecovery<T>(error, errorContext).pipe(
      switchMap(recoveryResult => {
        if (recoveryResult.success && recoveryResult.data !== undefined) {
          this.logger.info(
            `Asset recovery successful for ${filename} using ${recoveryResult.strategy}`,
            { filename, recoveryResult },
            'UniversalAssetLoaderService'
          );
          return new Observable<T>(subscriber => {
            subscriber.next(recoveryResult.data as T);
            subscriber.complete();
          });
        }

        // Recovery failed, try legacy fallback
        if (options.enableFallback && options.fallbackData !== undefined) {
          this.logger.warn(
            `Using legacy fallback data for asset: ${filename}`,
            { fallbackData: options.fallbackData },
            'UniversalAssetLoaderService'
          );
          return new Observable<T>(subscriber => {
            subscriber.next(options.fallbackData);
            subscriber.complete();
          });
        }

        // No recovery or fallback available
        this.logger.error(
          `No recovery mechanism available for asset: ${filename}`,
          { error, errorContext, recoveryResult },
          'UniversalAssetLoaderService'
        );
        
        return throwError(() => error);
      })
    );
  }

  /**
   * Get asset type from filename
   * @param filename Asset filename
   * @returns Asset type string
   */
  private getAssetType(filename: string): string {
    const extension = filename.split('.').pop()?.toLowerCase();
    
    switch (extension) {
      case 'json':
        return 'content';
      case 'png':
      case 'jpg':
      case 'jpeg':
      case 'webp':
        return 'image';
      case 'svg':
        return 'icon';
      case 'pdf':
        return 'document';
      default:
        return 'unknown';
    }
  }

  /**
   * Get information about available loading strategies
   * @returns Array of strategy information
   */
  getAvailableStrategies(): Array<{name: string, canHandle: boolean}> {
    return this.strategies.map(strategy => ({
      name: strategy.getStrategyName(),
      canHandle: strategy.canHandle()
    }));
  }

  /**
   * Get error recovery statistics
   * @returns Error recovery statistics
   */
  getErrorRecoveryStats(): { [key: string]: number } {
    return this.errorRecovery.getErrorStats();
  }

  /**
   * Clear error recovery statistics
   */
  clearErrorRecoveryStats(): void {
    this.errorRecovery.clearErrorStats();
  }
}