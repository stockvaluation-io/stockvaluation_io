import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { shareReplay, tap } from 'rxjs/operators';

export interface CacheEntry<T> {
  data: Observable<T>;
  timestamp: number;
  key: string;
}

export interface CacheOptions {
  ttl?: number; // Time to live in milliseconds
  maxSize?: number; // Maximum number of cached entries
}

@Injectable({
  providedIn: 'root'
})
export class AssetCacheService {
  private cache = new Map<string, CacheEntry<any>>();
  private defaultOptions: Required<CacheOptions> = {
    ttl: 5 * 60 * 1000, // 5 minutes default TTL
    maxSize: 50 // Maximum 50 cached entries
  };

  /**
   * Get cached data or execute the provider function and cache the result
   * @param key Unique cache key
   * @param provider Function that returns an Observable with the data
   * @param options Caching options
   * @returns Observable with cached or fresh data
   */
  getOrSet<T>(
    key: string, 
    provider: () => Observable<T>, 
    options: CacheOptions = {}
  ): Observable<T> {
    const opts = { ...this.defaultOptions, ...options };
    
    // Check if we have valid cached data
    const cached = this.cache.get(key);
    if (cached && this.isValidCache(cached, opts.ttl)) {
      return cached.data;
    }

    // Remove expired entry if it exists
    if (cached) {
      this.cache.delete(key);
    }

    // Ensure cache doesn't exceed max size
    this.enforceMaxSize(opts.maxSize);

    // Create new cache entry
    const data$ = provider().pipe(
      shareReplay(1), // Cache the result
      tap(() => {
        // Update timestamp when data is successfully loaded
        const entry = this.cache.get(key);
        if (entry) {
          entry.timestamp = Date.now();
        }
      })
    );

    // Store in cache immediately
    const cacheEntry: CacheEntry<T> = {
      data: data$,
      timestamp: Date.now(),
      key
    };
    
    this.cache.set(key, cacheEntry);
    return data$;
  }

  /**
   * Clear a specific cache entry
   * @param key Cache key to clear
   */
  clear(key: string): void {
    this.cache.delete(key);
  }

  /**
   * Clear all cache entries
   */
  clearAll(): void {
    this.cache.clear();
  }

  /**
   * Clear expired cache entries
   * @param ttl Time to live in milliseconds (uses default if not provided)
   */
  clearExpired(ttl: number = this.defaultOptions.ttl): void {
    const now = Date.now();
    const expiredKeys: string[] = [];

    this.cache.forEach((entry, key) => {
      if (now - entry.timestamp > ttl) {
        expiredKeys.push(key);
      }
    });

    expiredKeys.forEach(key => this.cache.delete(key));
  }

  /**
   * Get cache statistics
   * @returns Object with cache statistics
   */
  getStats(): {
    size: number;
    maxSize: number;
    entries: Array<{key: string, age: number}>;
  } {
    const now = Date.now();
    const entries = Array.from(this.cache.entries()).map(([key, entry]) => ({
      key,
      age: now - entry.timestamp
    }));

    return {
      size: this.cache.size,
      maxSize: this.defaultOptions.maxSize,
      entries
    };
  }

  /**
   * Check if a cache entry is still valid
   * @param entry Cache entry to check
   * @param ttl Time to live in milliseconds
   * @returns True if cache entry is still valid
   */
  private isValidCache<T>(entry: CacheEntry<T>, ttl: number): boolean {
    const age = Date.now() - entry.timestamp;
    return age < ttl;
  }

  /**
   * Enforce maximum cache size by removing oldest entries
   * @param maxSize Maximum allowed cache size
   */
  private enforceMaxSize(maxSize: number): void {
    if (this.cache.size >= maxSize) {
      // Sort entries by timestamp (oldest first) and remove oldest entries
      const entries = Array.from(this.cache.entries())
        .sort(([, a], [, b]) => a.timestamp - b.timestamp);

      const entriesToRemove = entries.slice(0, this.cache.size - maxSize + 1);
      entriesToRemove.forEach(([key]) => this.cache.delete(key));
    }
  }
}