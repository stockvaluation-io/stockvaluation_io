import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser, isPlatformServer } from '@angular/common';

@Injectable({
  providedIn: 'root'
})
export class PlatformDetectionService {
  
  constructor(@Inject(PLATFORM_ID) private platformId: Object) {}

  /**
   * Check if code is running in browser environment
   */
  isBrowser(): boolean {
    return isPlatformBrowser(this.platformId);
  }

  /**
   * Check if code is running in server environment (SSR)
   */
  isServer(): boolean {
    return isPlatformServer(this.platformId);
  }

  /**
   * Check if localStorage is available
   */
  isLocalStorageAvailable(): boolean {
    if (!this.isBrowser()) {
      return false;
    }
    
    try {
      const test = '__localStorage_test__';
      localStorage.setItem(test, 'test');
      localStorage.removeItem(test);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Check if sessionStorage is available
   */
  isSessionStorageAvailable(): boolean {
    if (!this.isBrowser()) {
      return false;
    }
    
    try {
      const test = '__sessionStorage_test__';
      sessionStorage.setItem(test, 'test');
      sessionStorage.removeItem(test);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Check if window object is available
   */
  isWindowAvailable(): boolean {
    return this.isBrowser() && typeof window !== 'undefined';
  }

  /**
   * Check if document object is available
   */
  isDocumentAvailable(): boolean {
    return this.isBrowser() && typeof document !== 'undefined';
  }

  /**
   * Check if navigator object is available
   */
  isNavigatorAvailable(): boolean {
    return this.isBrowser() && typeof navigator !== 'undefined';
  }

  /**
   * Check if console is available (should work in both environments)
   */
  isConsoleAvailable(): boolean {
    return typeof console !== 'undefined';
  }

  /**
   * Safe access to window with fallback
   */
  getWindow(): Window | null {
    return this.isWindowAvailable() ? window : null;
  }

  /**
   * Safe access to document with fallback
   */
  getDocument(): Document | null {
    return this.isDocumentAvailable() ? document : null;
  }

  /**
   * Safe access to navigator with fallback
   */
  getNavigator(): Navigator | null {
    return this.isNavigatorAvailable() ? navigator : null;
  }

  /**
   * Safe access to localStorage with fallback
   */
  getLocalStorage(): Storage | null {
    return this.isLocalStorageAvailable() ? localStorage : null;
  }

  /**
   * Safe access to sessionStorage with fallback
   */
  getSessionStorage(): Storage | null {
    return this.isSessionStorageAvailable() ? sessionStorage : null;
  }

  /**
   * Get platform information for debugging
   */
  getPlatformInfo(): { isBrowser: boolean; isServer: boolean; platformId: string } {
    return {
      isBrowser: this.isBrowser(),
      isServer: this.isServer(),
      platformId: this.platformId.toString()
    };
  }
}