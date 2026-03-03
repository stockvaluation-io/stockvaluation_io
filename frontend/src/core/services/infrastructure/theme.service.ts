import { Injectable, signal, computed } from '@angular/core';
import { PlatformDetectionService } from './platform-detection.service';

export type Theme = 'light' | 'dark';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'stockvaluation-theme';
  private readonly DEFAULT_THEME: Theme = 'dark';
  
  private _currentTheme = signal<Theme>(this.DEFAULT_THEME);
  
  // Public readonly signals
  readonly currentTheme = this._currentTheme.asReadonly();
  readonly isDarkMode = computed(() => this._currentTheme() === 'dark');
  readonly isLightMode = computed(() => this._currentTheme() === 'light');

  constructor(private platformDetection: PlatformDetectionService) {
    this.initializeTheme();
  }

  /**
   * Set the theme and persist to localStorage
   */
  setTheme(theme: Theme): void {
    this._currentTheme.set(theme);
    this.applyThemeToDOM(theme);
    this.persistTheme(theme);
  }

  /**
   * Toggle between light and dark themes
   */
  toggleTheme(): void {
    const newTheme = this._currentTheme() === 'light' ? 'dark' : 'light';
    this.setTheme(newTheme);
  }

  /**
   * Get the current theme value
   */
  getTheme(): Theme {
    return this._currentTheme();
  }

  /**
   * Initialize theme from localStorage or system preference
   */
  private initializeTheme(): void {
    const savedTheme = this.getSavedTheme();
    const systemTheme = this.getSystemTheme();
    const initialTheme = savedTheme || systemTheme || this.DEFAULT_THEME;
    
    this.setTheme(initialTheme);
    
    // Listen for system theme changes
    this.listenForSystemThemeChanges();
  }

  /**
   * Apply theme to DOM by setting data-theme attribute
   */
  private applyThemeToDOM(theme: Theme): void {
    const document = this.platformDetection.getDocument();
    if (document) {
      document.documentElement.setAttribute('data-theme', theme);
    }
  }

  /**
   * Persist theme to localStorage
   */
  private persistTheme(theme: Theme): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (localStorage) {
      localStorage.setItem(this.THEME_KEY, theme);
    }
  }

  /**
   * Get saved theme from localStorage
   */
  private getSavedTheme(): Theme | null {
    const localStorage = this.platformDetection.getLocalStorage();
    if (localStorage) {
      const saved = localStorage.getItem(this.THEME_KEY);
      return saved === 'light' || saved === 'dark' ? saved : null;
    }
    return null;
  }

  /**
   * Get system theme preference
   */
  private getSystemTheme(): Theme {
    const window = this.platformDetection.getWindow();
    if (window && window.matchMedia) {
      return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }
    return this.DEFAULT_THEME;
  }

  /**
   * Listen for system theme changes and update if no saved preference
   */
  private listenForSystemThemeChanges(): void {
    const window = this.platformDetection.getWindow();
    if (window && window.matchMedia) {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      
      mediaQuery.addEventListener('change', (e) => {
        // Only update if user hasn't set a preference
        if (!this.getSavedTheme()) {
          const systemTheme = e.matches ? 'dark' : 'light';
          this.setTheme(systemTheme);
        }
      });
    }
  }
}