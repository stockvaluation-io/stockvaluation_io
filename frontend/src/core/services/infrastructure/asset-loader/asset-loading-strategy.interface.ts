import { Observable } from 'rxjs';

export interface AssetLoadingStrategy {
  /**
   * Load an asset file and return its parsed content
   * @param filepath Full path or URL to the asset
   * @returns Observable with parsed content
   */
  loadAsset<T>(filepath: string): Observable<T>;

  /**
   * Check if this strategy can handle the current environment
   * @returns True if this strategy is applicable
   */
  canHandle(): boolean;

  /**
   * Get a human-readable name for this strategy
   * @returns Strategy name for logging/debugging
   */
  getStrategyName(): string;
}