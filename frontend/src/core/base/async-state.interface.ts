/**
 * Generic async state interface for standardized loading states
 */
export interface AsyncState<T> {
  loading: boolean;
  data: T | null;
  error: string | null;
}

/**
 * Initial async state with loading true
 */
export const initialAsyncState: AsyncState<any> = {
  loading: true,
  data: null,
  error: null
};

/**
 * Helper function to create initial async state
 */
export function createInitialAsyncState<T>(): AsyncState<T> {
  return {
    loading: true,
    data: null,
    error: null
  };
}

/**
 * Helper function to create loading state
 */
export function createLoadingState<T>(data: T | null = null): AsyncState<T> {
  return {
    loading: true,
    data,
    error: null
  };
}

/**
 * Helper function to create success state
 */
export function createSuccessState<T>(data: T): AsyncState<T> {
  return {
    loading: false,
    data,
    error: null
  };
}

/**
 * Helper function to create error state
 */
export function createErrorState<T>(error: string, data: T | null = null): AsyncState<T> {
  return {
    loading: false,
    data,
    error
  };
}