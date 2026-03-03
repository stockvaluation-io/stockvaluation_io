import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { environment } from '../../../env/environment';
import { ThemeService } from '../../../core/services';

@Component({
    selector: 'app-brand-logo',
    imports: [CommonModule, RouterLink],
    template: `
    <div class="brand-logo" [class]="containerClass">
      <!-- Versioned Logo with WebP support -->
      <ng-container *ngIf="isVersionedLogo">
        <!-- Conditional wrapper: router link if linkTo is provided, otherwise just div -->
        <ng-container *ngIf="linkTo; else noLinkVersioned">
          <a [routerLink]="linkTo" [queryParams]="getLinkQueryParams()" class="logo-link" [class]="linkClass" (click)="onLogoClick($event)">
            <div class="logo-wrapper" [class.footer-wrapper]="variant === 'footer'">
              <picture class="logo-picture">
                <source [srcset]="getOptimizedLogoSrc()" type="image/webp">
                <img 
                  [src]="getLogoSrc()" 
                  [alt]="logoAlt" 
                  class="logo versioned-logo"
                  [class]="logoClass"
                />
              </picture>
            </div>
          </a>
        </ng-container>
        <ng-template #noLinkVersioned>
          <div class="logo-wrapper" [class.footer-wrapper]="variant === 'footer'">
            <picture class="logo-picture">
              <source [srcset]="getOptimizedLogoSrc()" type="image/webp">
              <img 
                [src]="getLogoSrc()" 
                [alt]="logoAlt" 
                class="logo versioned-logo"
                [class]="logoClass"
              />
            </picture>
          </div>
        </ng-template>
        <!-- Show custom tagline for footer variant even with versioned logos -->
        <div class="brand-text" *ngIf="variant === 'footer' && customTagline">
          <p class="footer-tagline">{{ customTagline }}</p>
        </div>
      </ng-container>
      
      <!-- Legacy Logo -->
      <ng-container *ngIf="!isVersionedLogo">
        <!-- Conditional wrapper: router link if linkTo is provided, otherwise just div -->
        <ng-container *ngIf="linkTo; else noLinkLegacy">
          <a [routerLink]="linkTo" [queryParams]="getLinkQueryParams()" class="logo-link" [class]="linkClass" (click)="onLogoClick($event)">
            <div class="logo-wrapper" [class.footer-wrapper]="variant === 'footer'">
              <img 
                [src]="legacyLogoSrc" 
                [alt]="logoAlt" 
                class="logo legacy-logo"
                [class]="logoClass"
              />
            </div>
          </a>
        </ng-container>
        <ng-template #noLinkLegacy>
          <div class="logo-wrapper" [class.footer-wrapper]="variant === 'footer'">
            <img 
              [src]="legacyLogoSrc" 
              [alt]="logoAlt" 
              class="logo legacy-logo"
              [class]="logoClass"
            />
          </div>
        </ng-template>
        <div class="brand-text" [class]="textClass" *ngIf="showText">
          <h3 class="brand-name" *ngIf="variant === 'footer'">
            <ng-container *ngIf="linkTo; else noLinkText">
              <a [routerLink]="linkTo" [queryParams]="getLinkQueryParams()" class="brand-link" [class]="linkClass" (click)="onLogoClick($event)">stockvaluation.io</a>
            </ng-container>
            <ng-template #noLinkText>stockvaluation.io</ng-template>
          </h3>
          <ng-container *ngIf="variant !== 'footer'">
            <ng-container *ngIf="linkTo; else noLinkTextNonFooter">
              <a [routerLink]="linkTo" [queryParams]="getLinkQueryParams()" class="brand-link" [class]="linkClass" (click)="onLogoClick($event)">stockvaluation.io</a>
            </ng-container>
            <ng-template #noLinkTextNonFooter>
              <span class="brand-text-only" [class]="linkClass">stockvaluation.io</span>
            </ng-template>
          </ng-container>
          <p class="footer-tagline" *ngIf="variant === 'footer' && customTagline">{{ customTagline }}</p>
        </div>
      </ng-container>
    </div>
  `,
    styleUrls: ['./brand-logo.component.scss']
})
export class BrandLogoComponent {
  // Legacy logo options (fallback)
  @Input() legacyLogoSrc = '/assets/web-images/logo-legacy.svg';
  @Input() showText = true;
  @Input() linkTo?: string; // Optional hyperlink - null/undefined means no link
  @Input() customTagline?: string; // For footer variant
  
  // Version-based logo system - defaults from environment
  @Input() version: string = environment.logo.version;                           // 'legacy', 'v2', 'v3', etc.
  @Input() logoVariant: 'horizontal' | 'stacked' = environment.logo.variant;     // 'horizontal' or 'stacked'
  @Input() theme: 'light' | 'dark' | 'auto' = 'auto';                     // 'light', 'dark', or 'auto' to use theme service
  @Input() useOptimized = environment.logo.useOptimized;                         // Use WebP when available
  
  // Common options
  @Input() size: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | '3xl' = 'md';
  @Input() layout: 'horizontal' | 'vertical' = 'horizontal';
  @Input() variant: 'default' | 'centered' | 'nav' | 'footer' = 'default';
  
  // Supported logo versions
  private supportedVersions = ['v1']; // Add new versions as they become available

  constructor(private router: Router, private themeService: ThemeService) {}
  
  get logoAlt(): string {
    return this.isVersionedLogo ? 'StockValuation.io' : 'StockValuation.io Logo';
  }

  get effectiveTheme(): 'light' | 'dark' {
    if (this.theme === 'auto') {
      return this.themeService.currentTheme();
    }
    return this.theme;
  }
  
  get isVersionedLogo(): boolean {
    return this.version !== 'legacy' && this.supportedVersions.includes(this.version);
  }
  
  get containerClass(): string {
    const classes = [`size-${this.size}`, `layout-${this.layout}`, `variant-${this.variant}`];
    if (this.isVersionedLogo) {
      classes.push(`logo-variant-${this.logoVariant}`, `theme-${this.effectiveTheme}`, `version-${this.version}`);
    }
    return classes.join(' ');
  }
  
  get logoClass(): string {
    return `logo-${this.size}`;
  }
  
  get textClass(): string {
    return `text-${this.size}`;
  }
  
  get linkClass(): string {
    return `link-${this.size}`;
  }
  
  getLogoSrc(): string {
    if (!this.isVersionedLogo) return this.legacyLogoSrc;
    
    // Convert full text variant to short form for file naming
    const variantCode = this.logoVariant === 'horizontal' ? 'h' : 's';
    
    // Choose optimal resolution based on size
    const resolutionPath = this.getResolutionPath();
    
    return `/assets/logo/${this.version}/${resolutionPath}/logo-${variantCode}-${this.effectiveTheme}.png`;
  }
  
  getOptimizedLogoSrc(): string {
    if (!this.isVersionedLogo || !this.useOptimized) return this.getLogoSrc();
    
    // Convert full text variant to short form for file naming
    const variantCode = this.logoVariant === 'horizontal' ? 'h' : 's';
    
    // Choose optimal resolution based on size
    const resolutionPath = this.getResolutionPath();
    
    return `/assets/logo/${this.version}/${resolutionPath}/o/logo-${variantCode}-${this.effectiveTheme}.webp`;
  }

  getLinkQueryParams(): { [key: string]: string } | null {
    // Canonical valuation route no longer uses legacy search intent query params.
    return null;
  }

  onLogoClick(event: Event): void {
    // Let the router handle the navigation with query params
    // This method can be extended for future custom logic if needed
  }
  
  private getResolutionPath(): string {
    // Three-tier resolution system:
    // uhres (1225x145) - Ultra-high for largest displays and hero sections
    // hres (784x93)   - High-res for medium-large UI elements  
    // sres (392x47)   - Standard for navigation, footer, small elements
    switch (this.size) {
      case '3xl':
      case '2xl':
        return 'uhres'; // Ultra-high for biggest sizes
      case 'xl':
      case 'lg':
        return 'hres';  // High-res for medium-large
      case 'md':
      case 'sm':
      default:
        return 'sres';  // Standard for smaller elements
    }
  }
}
