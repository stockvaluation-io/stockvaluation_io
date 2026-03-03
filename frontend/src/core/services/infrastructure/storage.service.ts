import { Injectable } from '@angular/core';
import { PlatformDetectionService } from './platform-detection.service';

/**
 * SSR-Safe Storage Service
 * 
 * Provides safe localStorage and sessionStorage operations that work in both
 * server and browser environments. All methods handle serialization/deserialization
 * and return null when storage is not available (SSR).
 */
@Injectable({
  providedIn: 'root'
})
export class StorageService {
  constructor(private platformDetection: PlatformDetectionService) {}

  /**
   * Store item in localStorage or sessionStorage
   */
  setItem(
    key: string, 
    value: any, 
    storage: 'local' | 'session' = 'local'
  ): boolean {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return false;

    try {
      const serializedValue = typeof value === 'string' 
        ? value 
        : JSON.stringify(value);
      storageObj.setItem(key, serializedValue);
      return true;
    } catch (error) {
      console.warn(`Failed to set ${storage} storage item:`, error);
      return false;
    }
  }

  /**
   * Get item from localStorage or sessionStorage
   */
  getItem<T = any>(
    key: string, 
    storage: 'local' | 'session' = 'local',
    defaultValue: T | null = null
  ): T | null {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return defaultValue;

    try {
      const item = storageObj.getItem(key);
      if (item === null) return defaultValue;

      // Try to parse as JSON, fallback to string
      try {
        return JSON.parse(item) as T;
      } catch {
        return item as T;
      }
    } catch (error) {
      console.warn(`Failed to get ${storage} storage item:`, error);
      return defaultValue;
    }
  }

  /**
   * Remove item from localStorage or sessionStorage
   */
  removeItem(key: string, storage: 'local' | 'session' = 'local'): boolean {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return false;

    try {
      storageObj.removeItem(key);
      return true;
    } catch (error) {
      console.warn(`Failed to remove ${storage} storage item:`, error);
      return false;
    }
  }

  /**
   * Clear all items from localStorage or sessionStorage
   */
  clear(storage: 'local' | 'session' = 'local'): boolean {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return false;

    try {
      storageObj.clear();
      return true;
    } catch (error) {
      console.warn(`Failed to clear ${storage} storage:`, error);
      return false;
    }
  }

  /**
   * Check if key exists in storage
   */
  hasItem(key: string, storage: 'local' | 'session' = 'local'): boolean {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return false;

    try {
      return storageObj.getItem(key) !== null;
    } catch {
      return false;
    }
  }

  /**
   * Get all keys from storage
   */
  getKeys(storage: 'local' | 'session' = 'local'): string[] {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return [];

    try {
      const keys: string[] = [];
      for (let i = 0; i < storageObj.length; i++) {
        const key = storageObj.key(i);
        if (key) keys.push(key);
      }
      return keys;
    } catch {
      return [];
    }
  }

  /**
   * Get storage size (number of items)
   */
  getSize(storage: 'local' | 'session' = 'local'): number {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    if (!storageObj) return 0;

    try {
      return storageObj.length;
    } catch {
      return 0;
    }
  }

  /**
   * Check if storage is available
   */
  isAvailable(storage: 'local' | 'session' = 'local'): boolean {
    const storageObj = storage === 'local' 
      ? this.platformDetection.getLocalStorage()
      : this.platformDetection.getSessionStorage();
    
    return storageObj !== null;
  }

  /**
   * Utility method for managing arrays in storage
   */
  addToArray<T>(
    key: string, 
    item: T, 
    maxItems?: number,
    storage: 'local' | 'session' = 'local'
  ): boolean {
    const currentArray = this.getItem<T[]>(key, storage, []);
    if (!currentArray) return false;

    // Remove existing item if it exists
    const index = currentArray.findIndex(existing => 
      JSON.stringify(existing) === JSON.stringify(item)
    );
    if (index > -1) {
      currentArray.splice(index, 1);
    }

    // Add to beginning
    currentArray.unshift(item);

    // Trim if needed
    if (maxItems && currentArray.length > maxItems) {
      currentArray.splice(maxItems);
    }

    return this.setItem(key, currentArray, storage);
  }

  /**
   * Utility method for removing from arrays in storage
   */
  removeFromArray<T>(
    key: string, 
    item: T, 
    storage: 'local' | 'session' = 'local'
  ): boolean {
    const currentArray = this.getItem<T[]>(key, storage, []);
    if (!currentArray) return false;

    const index = currentArray.findIndex(existing => 
      JSON.stringify(existing) === JSON.stringify(item)
    );
    
    if (index > -1) {
      currentArray.splice(index, 1);
      return this.setItem(key, currentArray, storage);
    }

    return false;
  }
}