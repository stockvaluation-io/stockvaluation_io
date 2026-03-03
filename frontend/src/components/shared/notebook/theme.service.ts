import { Injectable, signal, computed, effect, PLATFORM_ID, inject } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

export type Theme = 'dark' | 'light';

const THEME_STORAGE_KEY = 'notebook-theme';

/**
 * Theme Service for Notebook
 * Manages dark/light mode with persistence and CSS variable updates.
 */
@Injectable({
    providedIn: 'root'
})
export class ThemeService {
    private platformId = inject(PLATFORM_ID);
    private isBrowser = isPlatformBrowser(this.platformId);

    // Signal for current theme
    private _theme = signal<Theme>(this.getInitialTheme());

    // Public readonly computed
    readonly theme = computed(() => this._theme());
    readonly isDark = computed(() => this._theme() === 'dark');

    constructor() {
        // Effect to apply theme changes
        effect(() => {
            const theme = this._theme();
            if (this.isBrowser) {
                this.applyTheme(theme);
                localStorage.setItem(THEME_STORAGE_KEY, theme);
            }
        });
    }

    private getInitialTheme(): Theme {
        if (!this.isBrowser) {
            return 'dark'; // Default for SSR
        }

        // Check localStorage first
        const stored = localStorage.getItem(THEME_STORAGE_KEY);
        if (stored === 'light' || stored === 'dark') {
            return stored;
        }

        // Check system preference
        if (window.matchMedia?.('(prefers-color-scheme: light)').matches) {
            return 'light';
        }

        return 'dark';
    }

    private applyTheme(theme: Theme): void {
        if (!this.isBrowser) return;

        const root = document.documentElement;
        const body = document.body;

        // Update data attribute for CSS selectors
        root.setAttribute('data-theme', theme);
        body.setAttribute('data-theme', theme);

        if (theme === 'dark') {
            // Set CSS variables
            root.style.setProperty('--nb-bg-primary', '#0d1117');
            root.style.setProperty('--nb-bg-secondary', '#161b22');
            root.style.setProperty('--nb-bg-tertiary', '#21262d');
            root.style.setProperty('--nb-border', '#30363d');
            root.style.setProperty('--nb-text-primary', '#f3f4f6');
            root.style.setProperty('--nb-text-secondary', '#9ca3af');
            root.style.setProperty('--nb-text-muted', '#6b7280');
            root.style.setProperty('--nb-accent', '#10b981');
            root.style.setProperty('--nb-accent-hover', '#059669');
            root.style.setProperty('--nb-error', '#ef4444');
            root.style.setProperty('--nb-warning', '#f59e0b');
            root.style.setProperty('--nb-code-bg', '#0d1117');

            // Apply directly to body and html for full page coverage
            body.style.backgroundColor = '#0d1117';
            body.style.color = '#f3f4f6';
            root.style.backgroundColor = '#0d1117';
            root.style.color = '#f3f4f6';
        } else {
            // Set CSS variables
            root.style.setProperty('--nb-bg-primary', '#ffffff');
            root.style.setProperty('--nb-bg-secondary', '#f9fafb');
            root.style.setProperty('--nb-bg-tertiary', '#f3f4f6');
            root.style.setProperty('--nb-border', '#e5e7eb');
            root.style.setProperty('--nb-text-primary', '#111827');
            root.style.setProperty('--nb-text-secondary', '#4b5563');
            root.style.setProperty('--nb-text-muted', '#9ca3af');
            root.style.setProperty('--nb-accent', '#10b981');
            root.style.setProperty('--nb-accent-hover', '#059669');
            root.style.setProperty('--nb-error', '#dc2626');
            root.style.setProperty('--nb-warning', '#d97706');
            root.style.setProperty('--nb-code-bg', '#f3f4f6');

            // Apply directly to body and html for full page coverage
            body.style.backgroundColor = '#ffffff';
            body.style.color = '#111827';
            root.style.backgroundColor = '#ffffff';
            root.style.color = '#111827';
        }
    }

    /**
     * Toggle between dark and light themes.
     */
    toggle(): void {
        this._theme.update(current => current === 'dark' ? 'light' : 'dark');
    }

    /**
     * Set a specific theme.
     */
    setTheme(theme: Theme): void {
        this._theme.set(theme);
    }
}
