import { Component, Input, HostListener, ChangeDetectionStrategy, ChangeDetectorRef, AfterViewInit, OnDestroy, NgZone } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CompanyData, ValuationResults } from '../../../../models';
import { PlatformDetectionService } from '../../../../../../core/services';

@Component({
  selector: 'app-sticky-company-bar',
  imports: [CommonModule],
  template: `
    <div 
      class="sticky-company-bar" 
      [class.visible]="isVisible"
      [class.hidden]="!isVisible"
    >
      <div class="bar-container">
        <!-- Left side: Company info -->
        <div class="company-info">
          <div class="company-logo-mini">
            <img 
              [src]="getCompanyLogoUrl()" 
              [alt]="company.name + ' logo'" 
              class="logo"
              (error)="onLogoError()"
              *ngIf="!logoError; else logoPlaceholder"
            >
            <ng-template #logoPlaceholder>
              <div class="logo-placeholder">
                {{ getCompanyInitials() }}
              </div>
            </ng-template>
          </div>
          
          <div class="company-details">
            <span class="company-name">{{ company.name }}</span>
            <span class="company-meta">
              <span class="symbol">{{ company.symbol }}</span>
              <span class="separator" *ngIf="company.industry">•</span>
              <span class="industry" *ngIf="company.industry">{{ company.industry }}</span>
            </span>
          </div>
        </div>

        <!-- Right side: Key metrics -->
        <div class="key-metrics">
          <div class="metric">
            <span class="value">{{ company.price | number:'1.2-2' }} <span class="currency">{{ getStockCurrency() }}</span></span>
            <span class="label">Current Price</span>
          </div>
          
          <div class="metric">
            <span class="value primary">{{ results.intrinsicValue | number:'1.2-2' }} <span class="currency">{{ getDCFCurrency() }}</span></span>
            <span class="label">Fair Value</span>
          </div>
          
          <div class="metric valuation" *ngIf="canShowValuationStatus()">
            <span 
              class="value" 
              [class.undervalued]="isUndervalued()" 
              [class.overvalued]="!isUndervalued()"
            >
              {{ Math.abs(getPriceAsPercentageOfValue()) | number:'1.1-1' }}%
            </span>
            <span class="label" [class.undervalued]="isUndervalued()" [class.overvalued]="!isUndervalued()">
              {{ isUndervalued() ? 'Undervalued' : 'Overvalued' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./sticky-company-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StickyCompanyBarComponent implements AfterViewInit, OnDestroy {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  isVisible = false;
  logoError = false;
  Math = Math;
  
  private companyInfoElement?: HTMLElement;
  private headerElement?: HTMLElement;
  private navElement?: HTMLElement;
  private scrollListener?: () => void;
  private wheelListener?: () => void;
  private keyListener?: (event: KeyboardEvent) => void;
  private readonly SCROLL_KEYS = ['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', 'Space'];
  private readonly BUFFER_DISTANCE = 120; // Buffer for early appearance

  constructor(
    private cdr: ChangeDetectorRef, 
    private ngZone: NgZone,
    private platformDetection: PlatformDetectionService
  ) {}

  ngAfterViewInit(): void {
    // Get reference elements after view init with multiple attempts
    this.initializeReferences();
  }

  ngOnDestroy(): void {
    this.removeScrollListeners();
  }

  private initializeReferences(): void {
    const document = this.platformDetection.getDocument();
    if (!document) {
      return;
    }

    const maxAttempts = 10;
    let attempts = 0;

    const tryInitialize = () => {
      attempts++;
      
      // Try multiple possible selectors for company info
      this.companyInfoElement = 
        document.querySelector('.company-info-wrapper') as HTMLElement ||
        document.querySelector('app-company-info-row') as HTMLElement ||
        document.querySelector('.company-info-row') as HTMLElement;
      
      this.headerElement = document.querySelector('.main-header') as HTMLElement;
      this.navElement = document.querySelector('.navigation-menu') as HTMLElement;
      
      if (this.companyInfoElement) {
        this.setupScrollListeners();
        this.checkVisibility();
        return;
      }

      if (attempts < maxAttempts) {
        setTimeout(tryInitialize, 200);
      }
    };

    tryInitialize();
  }

  private setupScrollListeners(): void {
    this.removeScrollListeners();
    this.createScrollListeners();
    this.attachScrollListeners();
  }

  private removeScrollListeners(): void {
    const windowObj = this.platformDetection.getWindow();
    if (!windowObj) {
      return;
    }

    if (this.scrollListener) {
      windowObj.removeEventListener('scroll', this.scrollListener);
    }
    if (this.wheelListener) {
      windowObj.removeEventListener('wheel', this.wheelListener);
    }
    if (this.keyListener) {
      windowObj.removeEventListener('keydown', this.keyListener);
      windowObj.removeEventListener('keyup', this.keyListener);
    }
  }

  private createScrollListeners(): void {
    const checkVisibilityInZone = () => {
      this.ngZone.run(() => this.checkVisibility());
    };

    this.scrollListener = checkVisibilityInZone;
    this.wheelListener = checkVisibilityInZone;
    
    this.keyListener = (event: KeyboardEvent) => {
      if (this.SCROLL_KEYS.includes(event.code)) {
        this.ngZone.run(() => {
          setTimeout(() => this.checkVisibility(), 50);
        });
      }
    };
  }

  private attachScrollListeners(): void {
    const windowObj = this.platformDetection.getWindow();
    if (!windowObj) {
      return;
    }

    const options = { passive: true };
    windowObj.addEventListener('scroll', this.scrollListener!, options);
    windowObj.addEventListener('wheel', this.wheelListener!, options);
    windowObj.addEventListener('keydown', this.keyListener!, options);
    windowObj.addEventListener('keyup', this.keyListener!, options);
  }

  @HostListener('window:scroll')
  onScroll(): void {
    this.checkVisibility();
  }

  @HostListener('window:wheel')
  onWheel(): void {
    this.checkVisibility();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (this.SCROLL_KEYS.includes(event.code)) {
      setTimeout(() => this.checkVisibility(), 50);
    }
  }

  private checkVisibility(): void {
    if (!this.companyInfoElement) {
      // Try to re-initialize if elements are missing
      this.initializeReferences();
      return;
    }

    try {
      const rect = this.companyInfoElement.getBoundingClientRect();
      const headerHeight = this.headerElement?.offsetHeight || 60;
      const navHeight = this.navElement?.offsetHeight || 50;
      const totalHeaderHeight = headerHeight + navHeight;

      // Show sticky bar when company info moves toward header
      // Hide it as soon as company info starts coming back into view
      const shouldShow = rect.bottom <= totalHeaderHeight + this.BUFFER_DISTANCE;
      const shouldHide = rect.bottom > totalHeaderHeight + (this.BUFFER_DISTANCE * 0.75); // Hide much earlier
      
      const finalShouldShow = shouldShow && !shouldHide;

      if (this.isVisible !== finalShouldShow) {
        this.isVisible = finalShouldShow;
        this.cdr.detectChanges();
      }
    } catch (error) {
      // Silently handle errors to avoid console spam
    }
  }

  // Logo handling
  getCompanyLogoUrl(): string {
    return `https://financialmodelingprep.com/image-stock/${this.company.symbol.toUpperCase()}.png`;
  }

  onLogoError(): void {
    this.logoError = true;
  }

  getCompanyInitials(): string {
    return this.company.name
      .split(' ')
      .map(word => word[0])
      .join('')
      .toUpperCase()
      .substring(0, 2);
  }

  // Valuation calculations
  getPriceAsPercentageOfValue(): number {
    if ('priceAsPercentageOfValue' in this.results) {
      return (this.results as any).priceAsPercentageOfValue;
    }
    
    if (this.company.price && this.results.intrinsicValue) {
      return ((this.company.price / this.results.intrinsicValue) - 1) * 100;
    }
    return 0;
  }

  isUndervalued(): boolean {
    return this.getPriceAsPercentageOfValue() < 0;
  }

  // Currency utilities
  getStockCurrency(): string {
    return this.results.stockCurrency || this.results.currency || 'USD';
  }

  getDCFCurrency(): string {
    return this.results.currency || 'USD';
  }

  canShowValuationStatus(): boolean {
    return this.getStockCurrency() === this.getDCFCurrency();
  }

  /**
   * Public method to force visibility check
   * Called by parent component after programmatic navigation
   */
  forceVisibilityCheck(): void {
    this.checkVisibility();
  }

}