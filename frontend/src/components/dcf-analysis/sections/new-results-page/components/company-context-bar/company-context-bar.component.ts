import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subject, Observable } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { CompanyData, ValuationResults } from '../../../../models';
import { BrandLogoComponent } from '../../../../../shared/brand-logo/brand-logo.component';
import { CompanySearchComponent, CompanySearchConfig } from '../../../../../shared/company-search/company-search.component';
import { ThemeToggleComponent } from '../../../../../shared/theme-toggle/theme-toggle.component';
import { CompanySearchResult, StockCompanySearchService, EventHandlingService, PlatformDetectionService } from '../../../../../../core/services';
import { environment } from '../../../../../../env/environment';

@Component({
  selector: 'app-company-context-bar',
  imports: [CommonModule, BrandLogoComponent, CompanySearchComponent, ThemeToggleComponent],
  template: `
    <header class="main-header">
      <div class="header-container">
        <div class="logo-section">
          <!-- Primary Logo -->
          <app-brand-logo 
            [size]="isMobile ? 'sm' : 'md'" 
            variant="nav"
            [linkTo]="valuationRoute">
          </app-brand-logo>
          <!-- Always show text logo as backup -->
          <div class="text-logo backup-logo">
            <a [href]="valuationHref" class="logo-link">
              <span class="logo-text">📈 StockValuations</span>
            </a>
          </div>
        </div>
        
        <!-- Centered Search Section -->
        <div class="search-section-center">
          <app-company-search
            [searchService]="stockSearchService"
            [config]="searchConfig"
            (companySelected)="onCompanySelected($event)">
          </app-company-search>
        </div>
        
        <div class="header-actions">
          <div class="header-controls">
            <button 
              *ngIf="legacyBullbeargptEnabled"
              type="button" 
              class="chat-toggle-btn"
              [class.active]="isChatOpen"
              (click)="onChatToggle()"
              [attr.aria-label]="isChatOpen ? 'Close AI Assistant' : 'Open AI Assistant'"
              [attr.title]="isChatOpen ? 'Close AI Assistant' : 'Open AI Assistant'">
              <i class="pi pi-comments"></i>
              <span class="chat-label">AI</span>
            </button>
            <app-theme-toggle></app-theme-toggle>
          </div>
          <!--<button 
            type="button"
            class="adjust-assumptions-btn"
            [class.active]="showAssumptionsSheet"
            (click)="onAdjustAssumptions()"
            [attr.aria-label]="showAssumptionsSheet ? 'Close DCF assumptions' : 'Adjust DCF assumptions'"
            [attr.title]="showAssumptionsSheet ? 'Close DCF assumptions' : 'Adjust DCF assumptions'"
            [attr.aria-expanded]="showAssumptionsSheet">
            <i class="pi" [class.pi-sliders-h]="!showAssumptionsSheet" [class.pi-times]="showAssumptionsSheet" aria-hidden="true"></i>
            <span>{{ showAssumptionsSheet ? 'Close' : 'Adjust Assumptions' }}</span>
          </button>-->
        </div>
      </div>
    </header>
  `,
  styleUrls: ['./company-context-bar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CompanyContextBarComponent implements OnInit, OnDestroy {
  readonly legacyBullbeargptEnabled = ((environment as any).features?.legacyBullbeargpt ?? false) === true;
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
  @Input() showAssumptionsSheet: boolean = false;
  @Input() isChatOpen: boolean = false;

  @Output() adjustAssumptionsClicked = new EventEmitter<void>();
  @Output() companySearched = new EventEmitter<CompanySearchResult>();
  @Output() chatToggled = new EventEmitter<void>();

  private destroy$ = new Subject<void>();
  isMobile = false;
  private resizeCleanup?: () => void;

  searchConfig: CompanySearchConfig = {
    size: 'sm',
    hideSuggestions: true,
    input: {
      placeholder: 'Search another company...',
      debounceMs: 300,
      showClearButton: true
    },
    results: {
      maxDisplayed: 5,
      showViewMore: false,
      showNoResults: true
    }
  };

  Math = Math;

  get valuationRoute(): string {
    const symbol = this.company?.symbol || 'AAPL';
    return `/automated-dcf-analysis/${symbol.toUpperCase()}/valuation`;
  }

  get valuationHref(): string {
    return this.valuationRoute;
  }

  constructor(
    public stockSearchService: StockCompanySearchService,
    private router: Router,
    private eventHandling: EventHandlingService,
    private platformDetection: PlatformDetectionService,
    private cdr: ChangeDetectorRef
  ) { }

  formatPrice(price: number): string {
    return price.toFixed(2);
  }

  getPriceChangeText(): string {
    // Calculate change from previous day if available
    // For now, showing upside as proxy
    const change = this.results.upside;
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)}%`;
  }

  getPriceChangeClass(): string {
    return this.results.upside >= 0 ? 'positive' : 'negative';
  }

  getUpsideClass(): string {
    return this.results.upside >= 0 ? 'positive' : 'negative';
  }

  onAdjustAssumptions(): void {
    this.adjustAssumptionsClicked.emit();
  }

  ngOnInit(): void {
    // Check initial mobile state
    this.checkMobileState();

    // Set up authentication observable (same pattern as landing-nav)
    // this.isAuthenticated = this.auth.isAuthenticated$;

    // Listen for resize events using SSR-safe service
    this.resizeCleanup = this.eventHandling.addResizeListener(() => {
      this.checkMobileState();
      this.cdr.detectChanges();
    }, 250);
  }

  private checkMobileState(): void {
    const windowObj = this.platformDetection.getWindow();
    this.isMobile = windowObj ? windowObj.innerWidth < 768 : false;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    // Clean up resize listener
    if (this.resizeCleanup) {
      this.resizeCleanup();
    }
  }

  onCompanySelected(company: CompanySearchResult): void {
    this.companySearched.emit(company);
    // Navigate to the new company's analysis page
    this.router.navigate(['/automated-dcf-analysis', company.symbol, 'valuation']);
  }

  onChatToggle(): void {
    this.chatToggled.emit();
  }

}
