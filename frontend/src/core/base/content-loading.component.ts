import { Component, OnDestroy } from '@angular/core';
import { Observable, Subject, throwError } from 'rxjs';
import { catchError, takeUntil } from 'rxjs/operators';
import { AsyncState, createInitialAsyncState, createLoadingState, createSuccessState, createErrorState } from './async-state.interface';

/**
 * Base component for content loading with standardized async patterns
 * Provides consistent loading states, error handling, and retry functionality
 */
@Component({
  template: ''
})
export abstract class ContentLoadingComponent<T> implements OnDestroy {
  protected readonly destroy$ = new Subject<void>();
  
  // Async state management
  state: AsyncState<T> = createInitialAsyncState<T>();
  
  // Convenience getters for template use
  get loading(): boolean { return this.state.loading; }
  get data(): T | null { return this.state.data; }
  get error(): string | null { return this.state.error; }
  get hasData(): boolean { return this.state.data !== null; }
  get hasError(): boolean { return this.state.error !== null; }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load content with standardized error handling and state management
   * @param source Observable source to load data from
   * @param retainDataOnError Whether to retain existing data when error occurs
   */
  protected loadContent(source: Observable<T>, retainDataOnError: boolean = false): void {
    this.state = createLoadingState(retainDataOnError ? this.state.data : null);

    source.pipe(
      takeUntil(this.destroy$),
      catchError(error => {
        const errorMessage = this.getErrorMessage(error);
        this.state = createErrorState(errorMessage, retainDataOnError ? this.state.data : null);
        
        // Log error for debugging
        console.error('Content loading error:', error);
        
        // Re-throw if needed for component-specific handling
        return throwError(() => error);
      })
    ).subscribe({
      next: (data) => {
        this.state = createSuccessState(data);
        this.onContentLoaded(data);
      },
      error: () => {
        // Error already handled in catchError
        this.onContentError();
      }
    });
  }

  /**
   * Retry loading content - calls the abstract reload method
   */
  public retry(): void {
    this.reload();
  }

  /**
   * Abstract method to be implemented by child components
   * Should define how to reload the content
   */
  protected abstract reload(): void;

  /**
   * Get user-friendly error message from error object
   * Can be overridden by child components for custom error handling
   */
  protected getErrorMessage(error: any): string {
    if (error?.message) {
      return error.message;
    }
    
    if (error?.error?.message) {
      return error.error.message;
    }
    
    if (typeof error === 'string') {
      return error;
    }
    
    return 'Unable to load content. Please try again.';
  }

  /**
   * Hook called when content is successfully loaded
   * Can be overridden by child components for custom behavior
   */
  protected onContentLoaded(data: T): void {
    // Default implementation - no action needed
  }

  /**
   * Hook called when content loading fails
   * Can be overridden by child components for custom error handling
   */
  protected onContentError(): void {
    // Default implementation - no action needed
  }

  /**
   * Reset state to initial loading state
   */
  protected resetState(): void {
    this.state = createInitialAsyncState<T>();
  }

  /**
   * Manually set success state with data
   */
  protected setSuccessState(data: T): void {
    this.state = createSuccessState(data);
  }

  /**
   * Manually set error state
   */
  protected setErrorState(error: string): void {
    this.state = createErrorState(error, this.state.data);
  }
}