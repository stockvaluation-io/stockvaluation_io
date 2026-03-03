import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  OnInit,
  Output,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject } from 'rxjs';

import { CompanyData, DCFAssumptionsFormData, ValuationResults } from '../../models';
import { FinancialDataDTO } from '../../models';
import { CompanyContextBarComponent, NavigationMenuComponent } from './components';
import { CompanyInfoRowComponent } from './components/company-info-row/company-info-row.component';
import { StickyCompanyBarComponent } from './components';
import { LoadingSpinnerComponent } from './components/loading-spinner/loading-spinner.component';
import { ErrorStateComponent, ErrorStateConfig } from './components/error-state/error-state.component';
import { EmptyStateComponent, EmptyStateConfig } from './components/empty-state/empty-state.component';
import { SkeletonLoaderComponent } from './components/skeleton-loader/skeleton-loader.component';
import { LoggerService, PlatformDetectionService, DomUtilitiesService } from '../../../../core/services';
import { environment } from '@env/environment';
// Import types only - actual components will be loaded dynamically
import type {
  FinancialHealthOverviewSection,
  FinancialProjectionsSection,
  PerformanceComparisonSection,
  TerminalValueSection,
  ValuationBreakdownSection,
  ValuationOverviewSection,
  ValuationAnimationSection
} from './sections';
import { SavedAnalysisBrowserComponent } from './components/saved-analysis-browser/saved-analysis-browser.component';
import { SignupModalComponent } from './components/signup-modal/signup-modal.component';
import { DisclaimerBannerComponent } from './components';
import { NarrativeSectionComponent } from './shared/components/narrative-section/narrative-section.component';
import { DCFAssumptionsSheetComponent } from '../../components/dcf-assumptions-sheet';
import { AssumptionsTransparencySectionComponent } from './sections/assumptions-transparency/assumptions-transparency.component';

export interface NavigationItem {
  id: string;
  title: string;
  description: string;
  icon: string;
  anchor: string;
}

@Component({
  selector: 'app-new-results-page',
  imports: [
    CommonModule,
    CompanyContextBarComponent,
    NavigationMenuComponent,
    CompanyInfoRowComponent,
    StickyCompanyBarComponent,
    DisclaimerBannerComponent,
    LoadingSpinnerComponent,
    ErrorStateComponent,
    EmptyStateComponent,
    SkeletonLoaderComponent,
    SavedAnalysisBrowserComponent,
    SignupModalComponent,
    NarrativeSectionComponent,
    DCFAssumptionsSheetComponent,
    AssumptionsTransparencySectionComponent
  ],
  template: `
    <div class="results-page" [class.sheet-open]="showAssumptionsSheet" [class.chat-open]="showChatSidebar">
      <!-- Company Context Bar -->
      <app-company-context-bar
        *ngIf="company && results && !isLoading && !hasError && !isEmpty"
        [company]="company"
        [results]="results"
        [showAssumptionsSheet]="showAssumptionsSheet"
        [isChatOpen]="showChatSidebar"
        (adjustAssumptionsClicked)="onAdjustAssumptions()"
        (chatToggled)="onChatToggle()">
      </app-company-context-bar>

      <!-- Navigation Menu -->
      <app-navigation-menu
        [navigationItems]="navigationItems"
        [activeSection]="activeSection"
        (sectionClicked)="onNavigateToSection($event)">
      </app-navigation-menu>

      <!-- Sticky Company Bar -->
      <app-sticky-company-bar
        #stickyCompanyBar
        *ngIf="company && results && !isLoading && !hasError && !isEmpty"
        [company]="company"
        [results]="results">
      </app-sticky-company-bar>

      <!-- Main Content Container -->
      <div class="results-content">
        <!-- Custom Calculation Blur Overlay -->
        <div *ngIf="isCustomCalculating" class="custom-calculation-overlay">
          <div class="overlay-spinner">
            <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
            <p>Recalculating with your assumptions...</p>
          </div>
        </div>
        
        
        <div class="results-container" [class.blurred]="isCustomCalculating">
          
          <!-- Loading State -->
          <div *ngIf="isLoading" class="loading-container">
            <app-loading-spinner 
              size="large" 
              text="Analyzing financial data and generating DCF model...">
            </app-loading-spinner>
            
            <!-- Loading Skeletons -->
            <div class="loading-skeletons">
              <app-skeleton-loader type="card" [lines]="4"></app-skeleton-loader>
              <app-skeleton-loader type="chart" [lines]="6"></app-skeleton-loader>
              <app-skeleton-loader type="table" [lines]="5"></app-skeleton-loader>
            </div>
          </div>
          
          <!-- Error State -->
          <app-error-state
            *ngIf="hasError && !isLoading"
            [config]="errorConfig"
            size="large"
            (retry)="onRetry()"
            (contactSupport)="onContactSupport()">
          </app-error-state>
          
          <!-- Empty State -->
          <app-empty-state
            *ngIf="isEmpty && !isLoading && !hasError"
            [config]="emptyConfig"
            size="large"
            (action)="onStartAnalysis()">
          </app-empty-state>
          
          <!-- Content State -->
          <ng-container *ngIf="!isLoading && !hasError && !isEmpty">
            
            <!-- Disclaimer Banner -->
            <div class="disclaimer-wrapper">
              <app-disclaimer-banner></app-disclaimer-banner>
            </div>
            
            <!-- Company Info Row -->
            <div class="company-info-wrapper">
              <app-company-info-row
                [company]="company"
                [results]="results">
              </app-company-info-row>
            </div>
            
            <!-- Main Content Area -->
            <div class="main-content">
            
            <!-- Section 1: Analysis Narratives -->
            <app-narrative-section
              id="analysis-narratives"
              class="results-section"
              [narrativeData]="results.narratives || null"
              [ticker]="company.symbol || ''"
              [currency]="results.currency || 'USD'"
              [stockCurrency]="results.stockCurrency || 'USD'"
              [company]="company"
              [results]="results"
              [heatMapData]="results.heatMapData || null">
            </app-narrative-section>

            <!--<div id="optionality-premium" class="results-section" *ngIf="getRealOptionAnalysisData() as optData">
              <app-optionality-premium [data]="optData"></app-optionality-premium>
            </div> -->

            <!-- Assumptions Transparency -->
            <div id="assumptions-transparency" class="results-section">
              <app-assumptions-transparency-section [results]="results"></app-assumptions-transparency-section>
            </div>

            <!-- Section 2.5: Valuation Animation (DCF Visual Breakdown) -->
            <div id="valuation-animation" class="results-section" *ngIf="results?.valuation_animation_base64">
              <app-skeleton-loader *ngIf="!isSectionLoaded('valuation-animation')"></app-skeleton-loader>
              <ng-container *ngIf="isSectionLoaded('valuation-animation') && ValuationAnimationSection">
                <ng-container *ngComponentOutlet="ValuationAnimationSection; inputs: { company: company, results: results }">
                </ng-container>
              </ng-container>
            </div>
            
            <!-- Section 2: Valuation Overview (COMMENTED OUT - Moved to Narrative Section) -->
            <!-- <div id="valuation-overview" class="results-section">
              <app-skeleton-loader *ngIf="!isSectionLoaded('valuation-overview')"></app-skeleton-loader>
              <ng-container *ngIf="isSectionLoaded('valuation-overview') && ValuationOverviewSection">
                <ng-container *ngComponentOutlet="ValuationOverviewSection; inputs: { company: company, results: results }">
                </ng-container>
              </ng-container>
            </div> -->
            
            <!-- Banner ad between valuation overview and financial health -->
            <!-- <div class="results-section banner-ad-section">
              <app-ad-unit 
                placement="banner" 
                [lazy]="true"
                [showFallback]="true">
              </app-ad-unit>
            </div> -->

          <!-- Section 3: Financial Health Overview (COMMENTED OUT) -->
          <!-- <div id="financial-health-overview" class="results-section">
            <app-skeleton-loader *ngIf="!isSectionLoaded('financial-health-overview')"></app-skeleton-loader>
            <ng-container *ngIf="isSectionLoaded('financial-health-overview') && FinancialHealthOverviewSection">
              <ng-container *ngComponentOutlet="FinancialHealthOverviewSection; inputs: { company: company, results: results, financialData: financialData }">
              </ng-container>
            </ng-container>
          </div> -->

          <!-- Section 4: Performance vs Industry & Terminal Value (left) & Valuation Breakdown (right) -->
          <div id="performance-terminal-breakdown" class="results-section side-by-side-sections">
            <div class="section-column">
              <div class="section-wrapper">
                <app-skeleton-loader *ngIf="!isSectionLoaded('performance-comparison')"></app-skeleton-loader>
                <ng-container *ngIf="isSectionLoaded('performance-comparison') && PerformanceComparisonSection">
                  <ng-container *ngComponentOutlet="PerformanceComparisonSection; inputs: { company: company, results: results }">
                  </ng-container>
                </ng-container>
              </div>
              
              <div id="terminal-value-analysis" class="section-wrapper">
                <app-skeleton-loader *ngIf="!isSectionLoaded('terminal-value')"></app-skeleton-loader>
                <ng-container *ngIf="isSectionLoaded('terminal-value') && TerminalValueSection">
                  <ng-container *ngComponentOutlet="TerminalValueSection; inputs: { company: company, results: results }">
                  </ng-container>
                </ng-container>
              </div>
            </div>
            <div class="section-column">
              <div class="section-wrapper">
                <app-skeleton-loader *ngIf="!isSectionLoaded('valuation-breakdown')"></app-skeleton-loader>
                <ng-container *ngIf="isSectionLoaded('valuation-breakdown') && ValuationBreakdownSection">
                  <ng-container *ngComponentOutlet="ValuationBreakdownSection; inputs: { company: company, results: results }">
                  </ng-container>
                </ng-container>
              </div>
            </div>
          </div>

          <!-- Section 5: Financial Projections -->
          <div id="financial-projections" class="results-section">
            <app-skeleton-loader *ngIf="!isSectionLoaded('financial-projections')"></app-skeleton-loader>
            <ng-container *ngIf="isSectionLoaded('financial-projections') && FinancialProjectionsSection">
              <ng-container *ngComponentOutlet="FinancialProjectionsSection; inputs: { company: company, results: results }">
              </ng-container>
            </ng-container>
          </div>
          
          <!-- News Sources -->
          <section id="news-sources" class="results-section news-sources-section">
            <div class="section-header">
              <h2 class="section-title">
                <i class="pi pi-link" aria-hidden="true"></i>
                News Sources
              </h2>
              <p class="section-description">External sources used for this generated narrative.</p>
            </div>
            <div class="news-sources-body" *ngIf="results?.newsSources?.length; else noNewsSources">
              <a
                class="news-source-item"
                *ngFor="let source of results.newsSources; trackBy: trackByNewsSource"
                [href]="source.url"
                target="_blank"
                rel="noopener noreferrer">
                <span class="news-source-title">{{ source.title || source.url }}</span>
                <span class="news-source-meta">{{ source.source || 'External source' }}</span>
              </a>
            </div>
            <ng-template #noNewsSources>
              <p class="news-empty-state">No source links were returned for this run.</p>
            </ng-template>
          </section>
          
          <!-- Strategic ad placement: After financial projections, content-style -->
          <!-- <div class="results-section bottom-ad-section">
            <app-ad-unit 
              placement="content" 
              [lazy]="true"
              [showFallback]="true">
            </app-ad-unit>
          </div> -->

<!--          &lt;!&ndash; Section 6: Growth Analysis &ndash;&gt;-->
<!--          <app-growth-analysis-section-->
<!--            id="growth-analysis"-->
<!--            class="results-section"-->
<!--            [company]="company"-->
<!--            [results]="results">-->
<!--          </app-growth-analysis-section>-->

<!--          &lt;!&ndash; Section 7: Profitability Analysis &ndash;&gt;-->
<!--          <app-profitability-analysis-section-->
<!--            id="profitability-analysis"-->
<!--            class="results-section"-->
<!--            [company]="company"-->
<!--            [results]="results">-->
<!--          </app-profitability-analysis-section>-->

<!--          &lt;!&ndash; Section 8: Risk Assessment &ndash;&gt;-->
<!--          <app-risk-assessment-section-->
<!--            id="risk-assessment"-->
<!--            class="results-section"-->
<!--            [company]="company"-->
<!--            [results]="results">-->
<!--          </app-risk-assessment-section>-->

<!--          &lt;!&ndash; Section 9: Company Overview &ndash;&gt;-->
<!--          <app-company-overview-section-->
<!--            id="company-overview"-->
<!--            class="results-section"-->
<!--            [company]="company"-->
<!--            [results]="results">-->
<!--          </app-company-overview-section>-->

<!--          &lt;!&ndash; Section 10: Model Assumptions &ndash;&gt;-->
<!--          <app-model-assumptions-section-->
<!--            id="model-assumptions"-->
<!--            class="results-section"-->
<!--            [company]="company"-->
<!--            [results]="results">-->
<!--          </app-model-assumptions-section>-->

          <!-- Section 11: Sensitivity Analysis -->
 <!--     <app-sensitivity-analysis-section
            id="sensitivity-analysis"
            class="results-section"
            [company]="company"
            [results]="results"
            [scenarioData]="getScenarioData()">
          </app-sensitivity-analysis-section> -->


<!--          &lt;!&ndash; Section 12: Investment Conclusion &ndash;&gt;-->
<!--          <app-investment-conclusion-section-->
<!--            id="investment-conclusion"-->
<!--            class="results-section"-->
<!--            [company]="company"-->
<!--            [results]="results">-->
<!--          </app-investment-conclusion-section>-->

            </div> <!-- End main-content -->
          </ng-container> <!-- End content state -->
        </div>
      </div>
      
      <!-- Saved Analysis Browser -->
      <app-saved-analysis-browser
        *ngIf="showSavedAnalysisBrowser"
        (close)="onCloseSavedAnalysisBrowser()"
        (loadAnalysis)="onLoadSavedAnalysis($event)">
      </app-saved-analysis-browser>
      
      <!-- Signup Modal -->
      <app-signup-modal
        *ngIf="showSignupModal"
        (close)="onCloseSignupModal()"
        (signup)="onSignup($event)">
      </app-signup-modal>
      
      <!-- Footer that scrolls with results content -->
      <footer *ngIf="company && results && !isLoading && !hasError && !isEmpty" class="results-footer">
        <div class="footer-content">
          <div class="footer-links">
            <a href="/automated-dcf-analysis" class="footer-link">Home</a>
            <a href="mailto:stockvaluation.io@gmail.com" class="footer-link">Contact</a>
          </div>
          <div class="footer-copyright">
            © {{ currentYear }} StockValuation.io. All rights reserved.
          </div>
        </div>
      </footer>
      
    </div>
    
    <!-- DCF Assumptions Sheet - Outside scrolling container -->
    <app-dcf-assumptions-sheet
      [isOpen]="showAssumptionsSheet"
      [company]="company"
      [results]="results"
      [companySymbol]="company.symbol || ''"
      [isCalculating]="isCustomCalculating"
      (closed)="onCloseAssumptionsSheet()"
      (formSubmitted)="onAssumptionsFormSubmitted($event)"
      (formSaved)="onAssumptionsFormSaved($event)"
      (requestCancelled)="onAssumptionsRequestCancelled()">
    </app-dcf-assumptions-sheet>
    
  `,
  styleUrls: ['./new-results-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NewResultsPageComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(DCFAssumptionsSheetComponent) assumptionsSheet?: DCFAssumptionsSheetComponent;
  @ViewChild(StickyCompanyBarComponent) stickyCompanyBar?: StickyCompanyBarComponent;

  constructor(
    private cdr: ChangeDetectorRef,
    private ngZone: NgZone,
    private logger: LoggerService,
    private platformDetection: PlatformDetectionService,
    private domUtilities: DomUtilitiesService
  ) { }
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
  @Input() financialData?: FinancialDataDTO; // Raw financial data from API
  @Input() isLoading = false;
  @Input() hasError = false;
  @Input() isEmpty = false;
  @Input() errorMessage?: string;
  @Input() isCustomCalculating = false;

  @Output() retryClicked = new EventEmitter<void>();
  @Output() contactSupportClicked = new EventEmitter<void>();
  @Output() startAnalysisClicked = new EventEmitter<void>();
  @Output() analysisDataChanged = new EventEmitter<{ company: CompanyData; results: ValuationResults }>();
  @Output() signupRequested = new EventEmitter<{ email: string; name?: string }>();
  @Output() assumptionsChanged = new EventEmitter<DCFAssumptionsFormData>();
  @Output() assumptionsRequestCancelled = new EventEmitter<void>();

  private destroy$ = new Subject<void>();
  activeSection = 'analysis-narratives';
  private observer?: IntersectionObserver | null;
  currentYear = new Date().getFullYear();
  private observerDisabled = false;
  private intersectingEntries = new Map<string, IntersectionObserverEntry>();
  showSavedAnalysisBrowser = false;
  showSignupModal = false;
  showAssumptionsSheet = false;
  showChatSidebar = true; // Track chat sidebar state - open by default
  chatBackendUrl = environment.chatBackendUrl;
  Date = Date; // Make Date available in template

  // Get current user ID for chat
  get currentUserId(): string | null {
    return 'anonymous_user';
  }

  // Lazy loading for chart sections
  private loadedSections = new Set<string>();
  private sectionsToLoad = [
    'valuation-overview',
    'financial-health-overview',
    'performance-comparison',
    'terminal-value',
    'valuation-breakdown',
    'financial-projections',
    'valuation-animation'
  ];

  // Component references for dynamic loading
  ValuationOverviewSection?: typeof ValuationOverviewSection;
  FinancialHealthOverviewSection?: typeof FinancialHealthOverviewSection;
  PerformanceComparisonSection?: typeof PerformanceComparisonSection;
  TerminalValueSection?: typeof TerminalValueSection;
  ValuationBreakdownSection?: typeof ValuationBreakdownSection;
  FinancialProjectionsSection?: typeof FinancialProjectionsSection;
  ValuationAnimationSection?: typeof ValuationAnimationSection;

  errorConfig: ErrorStateConfig = {
    title: 'Analysis Failed',
    message: 'We encountered an issue while analyzing the financial data. Please try again or contact support if the problem persists.',
    icon: 'pi pi-exclamation-triangle',
    showRetry: true,
    retryText: 'Retry Analysis',
    showSupport: true
  };

  emptyConfig: EmptyStateConfig = {
    title: 'No Analysis Available',
    message: 'Start by selecting a company and running a DCF analysis to see detailed financial projections and valuation insights.',
    icon: 'pi pi-chart-line',
    actionText: 'Start Analysis',
    showAction: true
  };

  navigationItems: NavigationItem[] = [
    {
      id: 'analysis-narratives',
      title: 'Narratives',
      description: 'Key insights and explanations',
      icon: 'document-text',
      anchor: '#analysis-narratives'
    },
    // optionality-premium will be conditionally added in ngOnInit based on data
    /*{
      id: 'valuation-overview',
      title: 'Valuation Overview',
      description: 'Investment stories and company analysis',
      icon: 'chart',
      anchor: '#valuation-overview'
    },
    {
      id: 'financial-health-overview',
      title: 'Financial Health',
      description: 'Key financial metrics and ratios',
      icon: 'heart',
      anchor: '#financial-health-overview'
    },*/
    {
      id: 'performance-terminal-breakdown',
      title: 'Performance & Valuation',
      description: 'Industry comparison, terminal value, and breakdown',
      icon: 'stats-chart',
      anchor: '#performance-terminal-breakdown'
    },
    {
      id: 'financial-projections',
      title: 'Financial Projections',
      description: '10-year revenue and cash flow forecasts',
      icon: 'analytics',
      anchor: '#financial-projections'
    },
    // {
    //   id: 'growth-analysis',
    //   title: 'Growth Analysis',
    //   description: 'Revenue growth drivers and sustainability',
    //   icon: 'trending-up',
    //   anchor: '#growth-analysis'
    // },
    // {
    //   id: 'profitability-analysis',
    //   title: 'Profitability Analysis',
    //   description: 'Margin analysis and operational efficiency',
    //   icon: 'pie-chart',
    //   anchor: '#profitability-analysis'
    // },
    // {
    //   id: 'risk-assessment',
    //   title: 'Risk Assessment',
    //   description: 'Investment risks and market volatility',
    //   icon: 'shield',
    //   anchor: '#risk-assessment'
    // },
    // {
    //   id: 'company-overview',
    //   title: 'Company Overview',
    //   description: 'Business model and competitive position',
    //   icon: 'building',
    //   anchor: '#company-overview'
    // },
    // {
    //   id: 'model-assumptions',
    //   title: 'Model Assumptions',
    //   description: 'DCF parameters and methodology',
    //   icon: 'settings',
    //   anchor: '#model-assumptions'
    // },
    // {
    //   id: 'sensitivity-analysis',
    //   title: 'Sensitivity Analysis',
    //   description: 'Impact of key variable changes',
    //   icon: 'adjust',
    //   anchor: '#sensitivity-analysis'
    // },
    // {
    //   id: 'investment-conclusion',
    //   title: 'Investment Conclusion',
    //   description: 'Key takeaways and analysis summary',
    //   icon: 'checkmark-circle',
    //   anchor: '#investment-conclusion'
    // }
  ];


  ngOnInit(): void {
    // Force scroll reset on component initialization (fixes direct URL navigation)
    this.forceScrollReset();

    // Update error message if provided
    if (this.errorMessage) {
      this.errorConfig = {
        ...this.errorConfig,
        message: this.errorMessage
      };
    }


  }

  ngAfterViewInit(): void {
    // Setup intersection observer after view is initialized
    // This ensures all DOM elements are rendered
    setTimeout(() => {
      this.setupSectionObserver();
      // Load initial sections after a short delay for better perceived performance
      this.loadInitialSections();
    }, 100);
  }

  private async loadInitialSections(): Promise<void> {
    // Load the first two most important sections immediately
    await Promise.all([
      this.loadSection('valuation-overview'),
      this.loadSection('financial-health-overview')
    ]);

    // Load remaining sections progressively
    setTimeout(() => {
      this.loadRemainingSection();
    }, 500);
  }

  private async loadRemainingSection(): Promise<void> {
    for (const sectionId of this.sectionsToLoad) {
      if (!this.loadedSections.has(sectionId)) {
        await this.loadSection(sectionId);
        // Small delay between loading sections to prevent UI freeze
        await new Promise(resolve => setTimeout(resolve, 50));
      }
    }
  }

  private async loadSection(sectionId: string): Promise<void> {
    if (this.loadedSections.has(sectionId)) {
      return;
    }

    try {
      switch (sectionId) {
        case 'valuation-overview':
          const { ValuationOverviewSection } = await import('./sections/valuation-overview/valuation-overview.component');
          this.ValuationOverviewSection = ValuationOverviewSection;
          break;
        case 'financial-health-overview':
          const { FinancialHealthOverviewSection } = await import('./sections/financial-health-overview/financial-health-overview.component');
          this.FinancialHealthOverviewSection = FinancialHealthOverviewSection;
          break;
        case 'performance-comparison':
          const { PerformanceComparisonSection } = await import('./sections/performance-comparison/performance-comparison.component');
          this.PerformanceComparisonSection = PerformanceComparisonSection;
          break;
        case 'terminal-value':
          const { TerminalValueSection } = await import('./sections/terminal-value-section/terminal-value-section.component');
          this.TerminalValueSection = TerminalValueSection;
          break;
        case 'valuation-breakdown':
          const { ValuationBreakdownSection } = await import('./sections/valuation-breakdown/valuation-breakdown.component');
          this.ValuationBreakdownSection = ValuationBreakdownSection;
          break;
        case 'financial-projections':
          const { FinancialProjectionsSection } = await import('./sections/financial-projections/financial-projections.component');
          this.FinancialProjectionsSection = FinancialProjectionsSection;
          break;
        case 'valuation-animation':
          const { ValuationAnimationSection } = await import('./sections/valuation-animation-section/valuation-animation-section.component');
          this.ValuationAnimationSection = ValuationAnimationSection;
          break;
      }

      this.loadedSections.add(sectionId);
      this.cdr.detectChanges(); // Trigger change detection to render the loaded component
    } catch (error) {
      this.logger.warn(`Failed to load section ${sectionId}`, error, 'NewResultsPageComponent');
    }
  }

  isSectionLoaded(sectionId: string): boolean {
    return this.loadedSections.has(sectionId);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();

    // Clean up intersection observer
    if (this.observer) {
      this.observer.disconnect();
    }
  }


  onNavigateToSection(sectionId: string): void {
    // Temporarily disable observer during programmatic scrolling
    this.observerDisabled = true;

    // Immediately update active section for visual feedback
    this.activeSection = sectionId;
    this.cdr.markForCheck();

    // Re-enable observer after scroll animation completes
    setTimeout(() => {
      this.observerDisabled = false;

      // Force sticky company bar visibility check after scroll completes
      // This ensures the sticky bar activates when navigating via menu
      this.triggerStickyBarCheck();
    }, 1000); // Allow time for smooth scroll to complete
  }

  onCloseSavedAnalysisBrowser(): void {
    this.showSavedAnalysisBrowser = false;
    this.cdr.markForCheck();
  }

  onLoadSavedAnalysis(event: { company: CompanyData; results: ValuationResults }): void {
    this.analysisDataChanged.emit(event);
  }

  onCloseSignupModal(): void {
    this.showSignupModal = false;
    this.cdr.markForCheck();
  }

  onSignup(event: { email: string; name?: string }): void {
    this.signupRequested.emit(event);
    this.showSignupModal = false;
    this.cdr.markForCheck();
  }

  onRetry(): void {
    this.retryClicked.emit();
  }

  onContactSupport(): void {
    this.contactSupportClicked.emit();
  }

  onStartAnalysis(): void {
    this.startAnalysisClicked.emit();
  }

  onAdjustAssumptions(): void {
    this.showAssumptionsSheet = !this.showAssumptionsSheet;
    this.cdr.markForCheck();

    // Scroll to Financial Health Overview section when opening
    if (this.showAssumptionsSheet) {
      this.scrollToFinancialHealthOverview();
    }
  }

  onChatToggle(): void {
    this.showChatSidebar = !this.showChatSidebar;
    this.cdr.markForCheck();
  }

  onCloseAssumptionsSheet(): void {
    this.showAssumptionsSheet = false;
    this.cdr.markForCheck();
  }

  onAssumptionsFormSubmitted(formData: DCFAssumptionsFormData): void {
    // Emit the form data to parent component for DCF recalculation
    // Note: Sheet will auto-close on mobile after server response is received
    this.assumptionsChanged.emit(formData);
  }



  onAssumptionsFormSaved(formData: DCFAssumptionsFormData): void {
    // Form data saved, just close the sheet
    this.showAssumptionsSheet = false;
    this.cdr.markForCheck();
  }

  onAssumptionsRequestCancelled(): void {
    // Request was cancelled by user, emit to parent
    this.assumptionsRequestCancelled.emit();
    // The sheet will remain open for user to try again
  }

  trackByNewsSource(index: number, source: { url: string }): string {
    return `${source.url}-${index}`;
  }

  // Method to notify assumptions sheet about request completion
  notifyAssumptionsRequestComplete(success: boolean, error?: string): void {
    if (this.assumptionsSheet) {
      this.assumptionsSheet.onSubmissionComplete(success, error);
    }

    // Auto-close sheet and scroll to financial projections on successful calculation
    if (success) {
      // Close the assumptions sheet
      this.showAssumptionsSheet = false;
      this.cdr.markForCheck();

      // Scroll to financial projections after a short delay
      setTimeout(() => {
        this.scrollToFinancialProjections();
      }, 500); // Give time for sheet to close
    }
  }

  private scrollToFinancialProjections(): void {
    // Scroll to financial projections section
    setTimeout(() => {
      const element = document.getElementById('financial-projections');
      if (!element) {
        this.logger.warn('Financial projections section not found', undefined, 'NewResultsPageComponent');
        return;
      }

      // Get the scrollable container (.dcf-content)
      const scrollContainer = element.closest('.dcf-content') as HTMLElement;
      if (!scrollContainer) {
        this.logger.warn('Scroll container (.dcf-content) not found', undefined, 'NewResultsPageComponent');
        return;
      }

      // Calculate offset for fixed elements
      const totalOffset = 20; // Space for header + nav + buffer
      const elementPosition = element.offsetTop - totalOffset;

      // Scroll to financial projections
      scrollContainer.scrollTo({
        top: Math.max(0, elementPosition),
        behavior: 'smooth'
      });
    }, 100);
  }

  private scrollToFinancialHealthOverview(): void {
    // Small delay to allow the assumptions sheet to start opening
    setTimeout(() => {
      const element = document.getElementById('financial-health-overview');
      if (element) {
        // Find the actual scroll container (.dcf-content) - same as navigation menu
        const scrollContainer = document.querySelector('.dcf-content') as HTMLElement;
        if (!scrollContainer) {
          return;
        }

        // Use same offset as navigation menu for consistency
        const totalOffset = 20;


        // Calculate element position and apply the offset
        const elementPosition = element.offsetTop - totalOffset;


        // Scroll the correct container (.dcf-content) - same as navigation menu
        scrollContainer.scrollTo({
          top: Math.max(0, elementPosition),
          behavior: 'smooth'
        });
      }
    }, 200);
  }

  private forceScrollReset(): void {
    // Immediately reset scroll position to prevent stuck scroll issues
    setTimeout(() => {
      const windowObj = this.platformDetection.getWindow();
      const documentObj = this.platformDetection.getDocument();

      if (windowObj && documentObj) {
        // Multiple methods to ensure scroll reset works across browsers
        windowObj.scrollTo({ top: 0, behavior: 'auto' });
        documentObj.documentElement.scrollTop = 0;
        documentObj.body.scrollTop = 0;

        // Force reflow to ensure styles are applied
        documentObj.body.offsetHeight;
      }

      // Don't set global smooth scrolling - let individual scroll methods handle this
    }, 0);
  }

  private setupSectionObserver(): void {
    const observerOptions = {
      rootMargin: '-80px 0px -40% 0px', // Less aggressive margins for better detection
      threshold: [0.1, 0.3, 0.5, 0.7] // Multiple thresholds for better accuracy
    };

    this.observer = this.domUtilities.createIntersectionObserver((entries) => {
      // Skip processing if observer is temporarily disabled
      if (this.observerDisabled) {
        return;
      }

      // Update the map of intersecting entries
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          this.intersectingEntries.set(entry.target.id, entry);
        } else {
          this.intersectingEntries.delete(entry.target.id);
        }
      });

      // Determine the most visible section
      const mostVisibleSection = this.getMostVisibleSection();

      if (mostVisibleSection && mostVisibleSection !== this.activeSection) {
        this.ngZone.run(() => {
          this.activeSection = mostVisibleSection;
          this.cdr.markForCheck();
        });
      }
    }, observerOptions);

    // Setup observer with retry logic for missing elements only if observer was created
    if (this.observer) {
      this.observeElements();
    }
  }

  private observeElements(): void {
    if (!this.observer) {
      return; // Skip if observer is not available (SSR environment)
    }

    let retryCount = 0;
    const maxRetries = 5;

    const setupObserver = () => {
      let foundElements = 0;

      this.navigationItems.forEach(item => {
        const element = document.getElementById(item.id);
        if (element) {
          this.observer!.observe(element);
          foundElements++;
        }
      });

      // If not all elements found and we haven't exceeded retries, try again
      if (foundElements < this.navigationItems.length && retryCount < maxRetries) {
        retryCount++;
        setTimeout(setupObserver, 200);
      } else {
      }
    };

    setupObserver();
  }

  private getMostVisibleSection(): string | null {
    if (this.intersectingEntries.size === 0) {
      return null;
    }

    let maxRatio = 0;
    let mostVisibleId = '';

    // Find the section with the highest intersection ratio
    this.intersectingEntries.forEach((entry, id) => {
      if (entry.intersectionRatio > maxRatio) {
        maxRatio = entry.intersectionRatio;
        mostVisibleId = id;
      }
    });

    // If no section has significant visibility, check by order
    if (maxRatio < 0.1) {
      // Return the first intersecting section in navigation order
      for (const item of this.navigationItems) {
        if (this.intersectingEntries.has(item.id)) {
          return item.id;
        }
      }
    }

    return mostVisibleId || null;
  }



  private isMobileDevice(): boolean {
    // Check if we're on a mobile device using multiple methods

    // Method 1: Check screen width (most reliable for responsive design)
    const windowObj = this.platformDetection.getWindow();
    if (windowObj) {
      return windowObj.innerWidth <= 768; // Mobile/tablet breakpoint
    }

    // Method 2: Check user agent as fallback
    const navigatorObj = this.platformDetection.getNavigator();
    if (navigatorObj) {
      const userAgent = navigatorObj.userAgent.toLowerCase();
      return /android|webos|iphone|ipad|ipod|blackberry|iemobile|opera mini/i.test(userAgent);
    }

    // Method 3: Check for touch capability as additional indicator
    if (windowObj && navigatorObj) {
      return 'ontouchstart' in windowObj || ((navigatorObj as any).maxTouchPoints > 0);
    }

    return false;
  }

  /**
   * Triggers sticky company bar visibility check
   * Used after programmatic navigation to ensure sticky bar activates properly
   */
  private triggerStickyBarCheck(): void {
    if (this.stickyCompanyBar) {
      // Use a small delay to ensure scroll has fully completed
      setTimeout(() => {
        this.stickyCompanyBar!.forceVisibilityCheck();
      }, 100);
    }
  }
}
