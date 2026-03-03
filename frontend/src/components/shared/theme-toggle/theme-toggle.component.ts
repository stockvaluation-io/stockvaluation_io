import { Component, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppIconComponent } from '../app-icon/app-icon.component';
import { ThemeService } from '../../../core/services';

@Component({
  selector: 'app-theme-toggle',
  standalone: true,
  imports: [CommonModule, AppIconComponent],
  template: `
    <div class="theme-toggle-container">
      <button 
        class="theme-toggle-btn"
        (click)="toggleTheme()"
        [attr.aria-label]="'Switch to ' + (currentTheme() === 'light' ? 'dark' : 'light') + ' theme'"
        [attr.title]="'Switch to ' + (currentTheme() === 'light' ? 'dark' : 'light') + ' mode'"
        type="button"
      >
        <app-icon 
          [icon]="currentIcon()"
          size="lg"
          class="theme-toggle-icon"
        />
        <span class="theme-label">THEME</span>
      </button>
    </div>
  `,
  styleUrls: ['./theme-toggle.component.scss']
})
export class ThemeToggleComponent {
  private themeService = inject(ThemeService);
  
  currentTheme = this.themeService.currentTheme;
  currentIcon = signal<string>('sun');

  constructor() {
    // Update icon when theme changes
    effect(() => {
      const theme = this.currentTheme();
      this.currentIcon.set(theme === 'light' ? 'moon' : 'sun');
    });
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }
}