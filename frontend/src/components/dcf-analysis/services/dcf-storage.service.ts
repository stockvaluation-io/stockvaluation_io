import { Injectable } from '@angular/core';
import { DCFState } from '../models';
import { DCF_STORAGE_CONFIG } from '../constants/dcf-constants';
import { validateDCFState } from '../utils/validation.utils';
import { LoggerService, PlatformDetectionService } from '../../../core/services';

@Injectable({
  providedIn: 'root'
})
export class DCFStorageService {
  private readonly STORAGE_KEY = DCF_STORAGE_CONFIG.STORAGE_KEY;
  private readonly EXPIRY_TIME = DCF_STORAGE_CONFIG.STATE_EXPIRY_TIME;

  constructor(
    private logger: LoggerService,
    private platformDetection: PlatformDetectionService
  ) {}

  /**
   * Save DCF state to localStorage with fallback handling
   */
  saveState(state: DCFState): boolean {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return false;
    }

    // Validate state before saving
    const validation = validateDCFState(state);
    if (!validation.isValid) {
      this.logger.warn('Invalid DCF state, not saving', validation.errors, 'DCFStorageService');
      return false;
    }

    try {
      const storageData = {
        state,
        timestamp: Date.now()
      };
      localStorage.setItem(this.STORAGE_KEY, JSON.stringify(storageData));
      return true;
    } catch (error) {
      // Handle quota exceeded error by clearing old data and retrying
      if (error instanceof DOMException && error.name === 'QuotaExceededError') {
        this.clearOldData();
        try {
          const storageData = {
            state,
            timestamp: Date.now()
          };
          localStorage.setItem(this.STORAGE_KEY, JSON.stringify(storageData));
          return true;
        } catch (retryError) {
          this.logger.warn('Failed to save DCF state after clearing old data', retryError, 'DCFStorageService');
          return false;
        }
      }
      this.logger.warn('Failed to save DCF state to localStorage', error, 'DCFStorageService');
      return false;
    }
  }

  /**
   * Load DCF state from localStorage with graceful fallback
   */
  loadState(): DCFState | null {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return null;
    }

    try {
      const stored = localStorage.getItem(this.STORAGE_KEY);
      if (!stored) return null;

      const storageData = JSON.parse(stored);
      
      // Validate storage data structure
      if (!storageData || typeof storageData !== 'object' || !storageData.state) {
        this.clearState();
        return null;
      }
      
      // Check if data is expired
      if (Date.now() - storageData.timestamp > this.EXPIRY_TIME) {
        this.clearState();
        return null;
      }

      // Validate state structure
      if (!this.isValidState(storageData.state)) {
        this.clearState();
        return null;
      }

      return storageData.state;
    } catch (error) {
      // Clear corrupted data
      this.clearState();
      this.logger.warn('Failed to load DCF state from localStorage, data cleared', error, 'DCFStorageService');
      return null;
    }
  }



  /**
   * Clear DCF state from localStorage
   */
  clearState(): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      localStorage.removeItem(this.STORAGE_KEY);
    } catch (error) {
      this.logger.warn('Failed to clear DCF state from localStorage', error, 'DCFStorageService');
    }
  }


  /**
   * Clear analysis data but preserve user preferences like analysis type
   */
  clearAnalysisData(): boolean {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return false;
    }

    this.clearState();
    return true;
  }

  /**
   * Clear all DCF-related data from localStorage
   */
  clearAllData(): boolean {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return false;
    }

    try {
      const keys = Object.keys(localStorage);
      keys.forEach(key => {
        if (key.startsWith(this.STORAGE_KEY)) {
          try {
            localStorage.removeItem(key);
          } catch (error) {
            this.logger.warn(`Failed to remove key ${key}`, error, 'DCFStorageService');
          }
        }
      });
      return true;
    } catch (error) {
      this.logger.warn('Failed to clear all DCF data from localStorage', error, 'DCFStorageService');
      return false;
    }
  }

  /**
   * Validate DCF state structure
   */
  private isValidState(state: any): boolean {
    return (
      state &&
      typeof state === 'object' &&
      (state.selectedCompany === null || typeof state.selectedCompany === 'object') &&
      (state.results === null || typeof state.results === 'object') &&
      typeof state.isLoading === 'boolean' &&
      (state.error === null || typeof state.error === 'string')
    );
  }

  /**
   * Clear old expired data to free up storage space
   */
  private clearOldData(): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      const keys = Object.keys(localStorage);
      const now = Date.now();
      
      keys.forEach(key => {
        if (key.startsWith(this.STORAGE_KEY)) {
          try {
            const stored = localStorage.getItem(key);
            if (stored) {
              const storageData = JSON.parse(stored);
              
              if (now - storageData.timestamp > this.EXPIRY_TIME) {
                localStorage.removeItem(key);
              }
            }
          } catch (error) {
            // Remove corrupted entries
            localStorage.removeItem(key);
          }
        }
      });
    } catch (error) {
      this.logger.warn('Failed to clear old data', error, 'DCFStorageService');
    }
  }

  /**
   * Check if localStorage is available
   */
  isStorageAvailable(): boolean {
    return this.platformDetection.isLocalStorageAvailable();
  }
}