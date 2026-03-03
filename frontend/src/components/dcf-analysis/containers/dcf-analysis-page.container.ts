import { Component, OnDestroy, OnInit, ViewChild, Inject, Optional } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, BehaviorSubject, EMPTY } from 'rxjs';
import { takeUntil, switchMap, distinctUntilChanged, filter } from 'rxjs/operators';
import { environment } from '../../../env/environment';

// Import section components
import { NewResultsPageComponent } from '../sections';

// Import shared components
import { EnhancedLoadingComponent, ErrorBoundaryComponent, ErrorInfo } from '../shared';
import { CompanySearchComponent, CompanySearchConfig } from '../../shared/company-search/company-search.component';
import { BrandLogoComponent } from '../../shared/brand-logo/brand-logo.component';
import { ThemeToggleComponent } from '../../shared/theme-toggle/theme-toggle.component';


// Import models and services
import { AnalysisInputs, DCFConfig, DCFState, DCFAssumptionsFormData, DCFCalculationRequest } from '../models';
import { FinancialDataDTO } from '../models/api-response.interface';
import { DCFFacadeService, DCFFacadeState } from '../../../core/services';
import { StockCompanySearchService, CompanySearchResult } from '../../../core/services';
import { CompanyDataMapper } from '../utils/company-data-mapper';
import { DCF_TIMING_CONFIG } from '../constants/dcf-constants';
import { validateCompanyData, validateTickerSymbol } from '../utils/validation.utils';
import { ThemeService, PlatformDetectionService } from '../../../core/services';
import { LoggerService, TemplateEngineService } from '../../../core/services';

@Component({
  selector: 'app-dcf-analysis-page',
  imports: [
    CommonModule,
    NewResultsPageComponent,
    ErrorBoundaryComponent,
    EnhancedLoadingComponent,
    CompanySearchComponent,
    BrandLogoComponent,
    ThemeToggleComponent
  ],
  template: `
    <div class="dcf-analysis-container">
      <!-- Company Search Page -->
      <div *ngIf="state && !state.selectedCompany" class="company-search-page">
        <!-- Theme Toggle - Only on search page -->
        <div class="search-page-theme-toggle">
          <app-theme-toggle></app-theme-toggle>
        </div>
        
        
        <!-- Main Content - Top aligned with scrollable area -->
        <div class="main-search-content">
          <!-- Top Section: Brand Logo and Search -->
          <div class="search-section">
            <!-- Brand Logo Header -->
            <div class="brand-header">
              <app-brand-logo 
                size="xl" 
                variant="default"
                [linkTo]="defaultSearchRoute">
              </app-brand-logo>
            </div>

            <!-- Search Component -->
            <div class="search-container">
              <app-company-search
                [searchService]="stockSearchService"
                [config]="searchConfig"
                [selectedCompany]="getSelectedCompanyAsSearchResult()"
                (companySelected)="onCompanySelected($event)"
                (searchStateChanged)="onSearchStateChanged($event)">
              </app-company-search>
            </div>
          </div>
        </div>
        
      </div>

      <!-- Analysis Results Page -->
      <main *ngIf="state && state.selectedCompany && state.results" class="dcf-content">
        <app-new-results-page
          [company]="state.selectedCompany"
          [results]="state.results"
          [financialData]="getFinancialData()"
          [isCustomCalculating]="isCustomCalculating"
          (assumptionsChanged)="onAssumptionsChanged($event)"
          (assumptionsRequestCancelled)="cancelAssumptionsRequest()">
        </app-new-results-page>
      </main>

      <!-- Enhanced Loading State -->
      <main *ngIf="state && state.isLoading && !state.error" class="dcf-content">
        <app-enhanced-loading
          [title]="getLoadingTitle()"
          [subtitle]="getLoadingMessage()"
          [companyName]="state.selectedCompany?.name || ''"
          [currentStage]="currentLoadingStage"
          [completedStages]="completedLoadingStages">
        </app-enhanced-loading>
        
      </main>

      <!-- Enhanced Error State -->
      <main *ngIf="state && state.error" class="dcf-content">
        <app-error-boundary
          [error]="getErrorInfo()"
          [errorType]="getErrorType()"
          (retryClicked)="onRetryAction()"
          (homeClicked)="onResetToHome()"
          (supportClicked)="onContactSupport()">
        </app-error-boundary>
        
      </main>
      
      <!-- Footer - only show on search page, not on results page -->
      <footer *ngIf="state && !state.selectedCompany" class="dcf-footer">
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
  `,
  styleUrls: ['./dcf-analysis-page.container.scss']
})
export class DCFAnalysisPageContainer implements OnInit, OnDestroy {
  @ViewChild(NewResultsPageComponent) resultsPageComponent?: NewResultsPageComponent;

  readonly defaultSearchRoute = '/automated-dcf-analysis';

  private destroy$ = new Subject<void>();
  private assumptionsRequest$ = new Subject<void>(); // Subject for cancelling assumptions requests

  // Track custom calculation state separately from general loading
  isCustomCalculating = false;
  private isLoadingFromUrl = false; // Flag to prevent redirects during URL parameter changes
  private isExplicitSearchNavigation = false; // Flag to prevent redirects when user explicitly navigates to search

  // Request deduplication is now handled by the facade service

  // Progress tracking for real-time loading stages
  currentLoadingStage = 0;
  completedLoadingStages: string[] = [];

  state: DCFFacadeState | null = null;
  currentYear = new Date().getFullYear();

  // Configuration for the DCF analysis
  // Search configuration for new architecture
  searchConfig: CompanySearchConfig = {
    search: {
      maxResults: 5,
      enableRanking: true
    },
    input: {
      placeholder: 'Search by company name or stock symbol...',
      debounceMs: 300,
      showClearButton: true
    },
    results: {
      maxDisplayed: 5,
      showViewMore: true,
      showNoResults: true,
      loadingMessage: 'Searching companies...'
    },
    suggestions: {
      show: true,
      items: ['Apple', 'TSLA', 'Microsoft', 'NVDA', 'AMZN', 'GOOGL']
    },
  };


  dcfConfig: DCFConfig = {
    title: 'Stock Valuation Made Simple',
    subtitle: 'Choose your preferred approach to analyze companies using discounted cash flow models. Our tools make complex valuations accessible to everyone.',
  };

  constructor(
    private dcfFacade: DCFFacadeService,
    private router: Router,
    private route: ActivatedRoute,
    public stockSearchService: StockCompanySearchService,
    public themeService: ThemeService,
    private logger: LoggerService,
    private platformDetection: PlatformDetectionService,
    private templateEngine: TemplateEngineService,
    @Optional() @Inject('IS_BOT_REQUEST') private isBotRequest: boolean
  ) { }

  ngOnInit(): void {
    // CRITICAL: Process navigation intent FIRST before any state subscriptions
    this.handleNavigationIntent();

    // Initialize state immediately with default values for instant rendering
    this.state = this.dcfFacade.getCurrentState();

    // Setup is simplified with facade pattern

    // Subscribe to route parameter changes to handle ticker changes in URL
    this.route.paramMap
      .pipe(takeUntil(this.destroy$))
      .subscribe(params => {
        const symbol = params.get('symbol');
        // Only handle parameter changes if the symbol is different from current state
        if (symbol && this.state?.selectedCompany?.symbol !== symbol) {
          this.handleUrlParameterChange(symbol);
        }
      });

    // Subscribe to state changes AFTER navigation intent is processed
    this.dcfFacade.state$
      .pipe(takeUntil(this.destroy$))
      .subscribe(state => {
        this.state = state;

        // Update URL based on current state
        this.updateUrl();
      });

    // Defer heavy initialization operations to next tick
    setTimeout(() => {
      this.initializeAsync();
    }, 0);
  }

  private handleNavigationIntent(): void {
    // Process navigation intent SYNCHRONOUSLY before any state subscriptions
    const queryParams = this.route.snapshot.queryParams;
    const clearState = queryParams['clear'] === 'true';
    const searchIntent = queryParams['intent'] === 'search';

    if (clearState || searchIntent) {
      // User wants to start fresh - set flag and clear state immediately
      this.isExplicitSearchNavigation = true;
      // Use resetToNewAnalysis to preserve user's analysis type preference
      this.dcfFacade.resetToNewAnalysis();
      // Clean up URL immediately to remove query parameters
      this.router.navigate([this.defaultSearchRoute], { replaceUrl: true });

      // Reset the flag after a short delay to allow normal navigation to resume
      setTimeout(() => {
        this.isExplicitSearchNavigation = false;
      }, 100);
    }
  }

  private initializeAsync(): void {
    // Handle URL parameters and state loading (only if not explicit search navigation)
    if (!this.isExplicitSearchNavigation) {
      this.handleUrlParametersAndState();
    }

  }

  private handleUrlParametersAndState(): void {
    const symbol = this.route.snapshot.paramMap.get('symbol');

    if (symbol) {
      this.handleUrlParameterChange(symbol);
    } else if (this.state?.selectedCompany || this.state?.results) {
      this.dcfFacade.resetToNewAnalysis();
    }
  }

  private handleUrlParameterChange(symbol: string): void {
    // Normalize ticker to uppercase
    const normalizedSymbol = symbol.toUpperCase();

    // If the original symbol was lowercase, redirect to uppercase URL
    if (symbol !== normalizedSymbol) {
      this.router.navigate(['/automated-dcf-analysis', normalizedSymbol, 'valuation'], {
        replaceUrl: true
      });
      return;
    }

    // Set flag to prevent redirects during URL parameter change
    this.isLoadingFromUrl = true;

    // Use unified trigger method for URL navigation
    this.triggerAnalysis(normalizedSymbol, 'url-navigation');
  }


  private updateUrl(): void {
    if (!this.state) return;

    // Don't perform URL redirects while loading from URL parameter changes
    if (this.isLoadingFromUrl) {
      return;
    }

    // Don't perform URL redirects when user explicitly navigated to search page
    if (this.isExplicitSearchNavigation) {
      return;
    }

    const state = this.state;
    const currentUrl = this.router.url;

    if (state.selectedCompany) {
      // If we have a company, ensure we're on the valuation route
      const expectedUrl = `/ automated - dcf - analysis / ${state.selectedCompany.symbol}/valuation`;
      if (currentUrl !== expectedUrl) {
        this.router.navigate(this.buildValuationRoute(state.selectedCompany.symbol), { replaceUrl: true });
      }
    }
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // Facade pattern simplifies request deduplication - handled internally by DCFFacadeService


  // Progress tracking methods for real-time loading
  private resetLoadingProgress(): void {
    this.currentLoadingStage = 0;
    this.completedLoadingStages = [];
  }

  private markStageCompleted(stageId: string): void {
    if (!this.completedLoadingStages.includes(stageId)) {
      this.completedLoadingStages.push(stageId);
    }
  }

  private setCurrentStage(stageIndex: number): void {
    this.currentLoadingStage = stageIndex;
  }

  /**
   * Unified method to trigger DCF analysis - consolidates all trigger pathways
   * Eliminates redundant logic and provides single entry point for analysis
   */
  private triggerAnalysis(
    symbol: string,
    source: 'user-selection' | 'url-navigation' | 'context-navigation'
  ): void {

    // Reset progress tracking for new analysis
    this.resetLoadingProgress();

    // Show immediate progress to avoid "stuck" feeling
    this.showImmediateProgress();

    // Check if this is a bot request during SSR
    const isBotRequest = this.isBotRequest ?? false;
    const isSSR = this.platformDetection.isServer();

    // If this is a human user during SSR, skip the API call and let client handle it
    if (isSSR && !isBotRequest) {
      this.logger.info(`Skipping SSR API call for human user - will load on client`, undefined, 'DCFAnalysisPageContainer');

      // Reset loading flag and let client-side handle the API call
      this.isLoadingFromUrl = false;

      // On client-side, trigger the analysis after hydration
      if (this.platformDetection.isBrowser()) {
        setTimeout(() => {
          this.triggerAnalysis(symbol, source);
        }, 100);
      }
      return;
    }


    // Use facade for search and analysis (for bots during SSR or client-side)
    this.dcfFacade.searchAndAnalyzeCompany(symbol)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (result) => {
          this.markStageCompleted('company-data');
          // Simulate remaining stages for better UX
          this.simulateRemainingStages(result.results);
          this.logger.info(`Analysis completed for ${symbol}`, undefined, 'DCFAnalysisPageContainer');

          // Reset loading flag after successful completion
          setTimeout(() => {
            this.isLoadingFromUrl = false;
          }, 1000); // Give time for loading animation to complete
        },
        error: (error) => {
          this.logger.error(`Analysis failed for ${symbol}`, error, 'DCFAnalysisPageContainer');
          // Reset loading flag on error
          this.isLoadingFromUrl = false;
          // Error handling is managed by the facade
        }
      });

    // Handle navigation based on source
    if (source === 'user-selection') {
      this.router.navigate(this.buildValuationRoute(symbol));
    }
    // URL navigation and context navigation don't need router.navigate (already handled)
  }

  private simulateRemainingStages(results: any): void {
    // First mark DCF analysis as completed
    this.markStageCompleted('dcf-analysis');

    // Random delay between 500-1000ms for "Generating Insights" stage
    const insightsDelay = Math.random() * 500 + 500; // 500-1000ms

    setTimeout(() => {
      this.markStageCompleted('narrative-insights');

      // Show 100% completion for a moment to display completion animation
      setTimeout(() => {
        // Additional delay to show the loader disappear animation for better UX
        setTimeout(() => {
          // Results are already set by facade, just need to complete the loading animation
          // The facade handles the state management
        }, 600); // 600ms delay to show completion animation
      }, 200);
    }, insightsDelay);
  }

  private showImmediateProgress(): void {
    // Show immediate progress to avoid "stuck at 0%" feeling
    // This gives users confidence that something is happening
    setTimeout(() => {
      // If no stages completed yet, at least show we're in progress
      if (this.completedLoadingStages.length === 0) {
        this.setCurrentStage(0); // Show first stage as active
      }
    }, 100);
  }

  onCompanySelected(company: CompanySearchResult): void {
    // Use unified trigger method for company selection
    this.triggerAnalysis(company.symbol, 'user-selection');
  }


  onSearchStateChanged(state: { searching: boolean, query: string, hasResults: boolean }): void {
    // Update search state changes
  }

  onContinueFromCompanySearch(): void {
    if (!this.state) return;

    // Validate required data before proceeding
    if (!this.state.selectedCompany) {
      this.dcfFacade.setError('Please select a company before continuing');
      return;
    }

    // Validate company data
    const companyValidation = validateCompanyData(this.state.selectedCompany);
    if (!companyValidation.isValid) {
      this.dcfFacade.setError(`Invalid company data: ${companyValidation.errors.join(', ')}`);
      return;
    }

    // Start calculation immediately using facade
    setTimeout(() => {
      if (this.state?.selectedCompany) {
        this.triggerAnalysis(this.state.selectedCompany.symbol, 'context-navigation');
      }
    }, DCF_TIMING_CONFIG.QUICK_ANALYSIS_DELAY);
  }


  onAnalysisSubmitted(inputs: AnalysisInputs): void {
    if (!this.state?.selectedCompany) {
      this.logger.error('No company selected for custom analysis', undefined, 'DCFAnalysisPageContainer');
      return;
    }

    // Use facade for custom analysis
    this.dcfFacade.performCustomAnalysis(this.state.selectedCompany.symbol, inputs)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (results) => {
          this.logger.info('Custom analysis completed', undefined, 'DCFAnalysisPageContainer');
        },
        error: (error) => {
          this.logger.error('Custom analysis failed', error, 'DCFAnalysisPageContainer');
        }
      });
  }

  private resetScrollPosition(): void {
    // Force scroll reset for direct URL navigation
    setTimeout(() => {
      const window = this.platformDetection.getWindow();
      const document = this.platformDetection.getDocument();

      if (window) {
        window.scrollTo({ top: 0, behavior: 'auto' });
      }
      if (document) {
        document.documentElement.scrollTop = 0;
        document.body.scrollTop = 0;
      }
    }, 0);
  }



  onAssumptionsChanged(formData: DCFAssumptionsFormData): void {
    if (!this.state?.selectedCompany) {
      this.notifyAssumptionsCompletion(false, 'No company selected for recalculation');
      return;
    }

    const companyApiData = this.dcfFacade.getCompanyApiData();

    // Convert form data to DCF calculation request
    const dcfRequest = this.convertFormDataToRequest(formData, companyApiData);

    // Set custom calculation state for blur overlay
    this.isCustomCalculating = true;

    // Clear previous error state
    this.dcfFacade.setError(null);

    // Cancel any previous request
    this.assumptionsRequest$.next();

    // Use facade method for assumptions recalculation (preserve original behavior)
    // This maintains in-place updates without navigation
    this.dcfFacade.recalculateWithAssumptions(this.state.selectedCompany.symbol, dcfRequest)
      .pipe(takeUntil(this.assumptionsRequest$), takeUntil(this.destroy$))
      .subscribe({
        next: (apiResponse) => {
          // Validate API response before processing
          if (!apiResponse) {
            throw new Error('API returned empty response');
          }

          // Convert API response to ValuationResults format and update state
          const customResults = this.dcfFacade.convertApiResponseToResults(apiResponse);

          // Update the DCF state with new results (keep existing narrative from story analysis)
          const currentState = this.dcfFacade.getCurrentState();
          if (currentState && currentState.results) {
            const updatedResults = {
              ...customResults,
              // Preserve narrative data from original story analysis
              narratives: currentState.results.narratives
            };

            // Use facade to update results, preserving narratives
            this.dcfFacade.setResults(updatedResults);
          }

          // Clear custom calculation state
          this.isCustomCalculating = false;

          // Show success toast notification
          this.dcfFacade.showCustomCalculationSuccess(this.state?.selectedCompany?.symbol);

          // Notify successful completion (results page will handle scrolling to financial projections)
          this.notifyAssumptionsCompletion(true);
        },
        error: (error) => {
          this.logger.error('DCF recalculation error', error, 'DCFAnalysisPageContainer');
          const errorMessage = error.message || 'Failed to recalculate DCF with new assumptions';

          // Clear custom calculation state on error
          this.isCustomCalculating = false;

          // Show error toast notification
          this.dcfFacade.showCustomCalculationError(errorMessage);

          this.dcfFacade.setError(errorMessage);
          this.notifyAssumptionsCompletion(false, errorMessage);
        }
      });
  }

  private notifyAssumptionsCompletion(success: boolean, error?: string): void {
    if (this.resultsPageComponent) {
      this.resultsPageComponent.notifyAssumptionsRequestComplete(success, error);
    }
  }


  cancelAssumptionsRequest(): void {
    // Cancel the current assumptions request
    this.assumptionsRequest$.next();

    // Clear custom calculation state on cancellation
    this.isCustomCalculating = false;

    // Reset loading state  
    // Note: facade will handle its own state management

    // Note: Cancellation notification handled by results page component

    // Notify cancellation
    this.notifyAssumptionsCompletion(false, 'Request cancelled by user');
  }

  private convertFormDataToRequest(formData: DCFAssumptionsFormData, companyApiData?: any): DCFCalculationRequest {
    const dcfRequest: DCFCalculationRequest = {
      revenueNextYear: formData.coreAssumptions.revenueNextYear,
      operatingMarginNextYear: formData.coreAssumptions.operatingMarginNextYear,
      compoundAnnualGrowth2_5: formData.coreAssumptions.compoundAnnualGrowth2_5,
      targetPreTaxOperatingMargin: formData.coreAssumptions.targetPreTaxOperatingMargin,
      convergenceYearMargin: 3,
      salesToCapitalYears1To5: formData.coreAssumptions.salesToCapitalYears1To5,
      salesToCapitalYears6To10: formData.coreAssumptions.salesToCapitalYears6To10,
      riskFreeRate: formData.coreAssumptions.riskFreeRate,
      initialCostCapital: formData.coreAssumptions.initialCostCapital,

      isExpensesCapitalize: formData.riskAssessment.isExpensesCapitalize,
      hasOperatingLease: formData.riskAssessment.hasOperatingLease,
      companyRiskLevel: this.normalizeRiskLevel(formData.riskAssessment.companyRiskLevel),
      hasEmployeeOptions: formData.riskAssessment.hasEmployeeOptions,
      numberOfOptions: formData.riskAssessment.employeeOptions?.numberOfOptions || 0,
      averageStrikePrice: formData.riskAssessment.employeeOptions?.averageStrikePrice || 0,
      averageMaturity: formData.riskAssessment.employeeOptions?.averageMaturity || 0,
      stockPriceStdDev: formData.riskAssessment.employeeOptions?.stockPriceStdDev || 0,

      overrideAssumptionCostCapital: formData.overrides.overrideAssumptionCostCapital.isOverride ? formData.overrides.overrideAssumptionCostCapital : { isOverride: false, overrideCost: 0 },
      overrideAssumptionReturnOnCapital: formData.overrides.overrideAssumptionReturnOnCapital.isOverride ? formData.overrides.overrideAssumptionReturnOnCapital : { isOverride: false, overrideCost: 0 },
      overrideAssumptionProbabilityOfFailure: formData.overrides.overrideAssumptionProbabilityOfFailure.isOverride ? formData.overrides.overrideAssumptionProbabilityOfFailure : { isOverride: false, overrideCost: 0, additionalInputValue: 0, additionalRadioValue: 'V' },
      overrideAssumptionReinvestmentLag: formData.overrides.overrideAssumptionReinvestmentLag.isOverride ? formData.overrides.overrideAssumptionReinvestmentLag : { isOverride: false, overrideCost: 0 },
      overrideAssumptionTaxRate: formData.overrides.overrideAssumptionTaxRate.isOverride ? formData.overrides.overrideAssumptionTaxRate : { isOverride: false, overrideCost: 0 },
      overrideAssumptionNOL: formData.overrides.overrideAssumptionNOL.isOverride ? formData.overrides.overrideAssumptionNOL : { isOverride: false, overrideCost: 0 },
      overrideAssumptionRiskFreeRate: formData.overrides.overrideAssumptionRiskFreeRate.isOverride ? formData.overrides.overrideAssumptionRiskFreeRate : { isOverride: false, overrideCost: 0 },
      overrideAssumptionGrowthRate: formData.overrides.overrideAssumptionGrowthRate.isOverride ? formData.overrides.overrideAssumptionGrowthRate : { isOverride: false, overrideCost: 0 },
      overrideAssumptionCashPosition: formData.overrides.overrideAssumptionCashPosition.isOverride ? formData.overrides.overrideAssumptionCashPosition : { isOverride: false, overrideCost: 0, additionalInputValue: 0 }
    };

    const segments = this.extractJavaSegments(companyApiData);
    if (segments.length > 0) {
      dcfRequest.segments = { segments };
    }

    if (Array.isArray(companyApiData?.sectorOverrides) && companyApiData.sectorOverrides.length > 0) {
      dcfRequest.sectorOverrides = companyApiData.sectorOverrides;
    }

    return dcfRequest;
  }

  private normalizeRiskLevel(riskLevel: string): 'Low' | 'Medium' | 'High' {
    if (riskLevel === 'Very Low') {
      return 'Low';
    }
    if (riskLevel === 'Very High') {
      return 'High';
    }
    if (riskLevel === 'Low' || riskLevel === 'Medium' || riskLevel === 'High') {
      return riskLevel;
    }
    return 'Medium';
  }

  private extractJavaSegments(companyApiData?: any): Array<{
    sector: string;
    industry?: string | null;
    components: string[];
    mappingScore?: number | null;
    revenueShare?: number | null;
    operatingMargin?: number | null;
  }> {
    if (!Array.isArray(companyApiData?.segments)) {
      return [];
    }

    return companyApiData.segments
      .map((segment: any) => {
        const sector = typeof segment?.sector === 'string' ? segment.sector.trim() : '';
        if (!sector) {
          return null;
        }

        return {
          sector,
          industry: typeof segment?.industry === 'string' ? segment.industry : null,
          components: Array.isArray(segment?.components) ? segment.components.map((component: any) => String(component)) : [],
          mappingScore: this.toFiniteNumber(segment?.mappingScore ?? segment?.mapping_score),
          revenueShare: this.toFiniteNumber(segment?.revenueShare ?? segment?.revenue_share),
          operatingMargin: this.toFiniteNumber(segment?.operatingMargin ?? segment?.operating_margin),
        };
      })
      .filter((segment: any) => !!segment);
  }

  private toFiniteNumber(value: any): number | null {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }


  clearError(): void {
    // Facade will handle error clearing through its own state management
    // Could expose a clearError method on facade if needed
  }

  // Enhanced Loading Methods
  getLoadingTitle(): string {
    if (this.state?.selectedCompany && !this.state?.results) {
      return 'Calculating DCF Valuation';
    } else if (this.state?.selectedCompany) {
      return 'Loading Company Data';
    } else {
      return 'Processing Request';
    }
  }

  getLoadingMessage(): string {
    if (this.state?.selectedCompany && !this.state?.results) {
      return 'Running 10-year financial projections and valuation calculations...';
    } else if (this.state?.selectedCompany) {
      return 'Fetching financial data and company information...';
    } else {
      return 'Please wait while we process your request.';
    }
  }


  // Enhanced Error Methods
  getErrorInfo(): ErrorInfo {
    const error = this.state?.error || 'An unexpected error occurred';

    // Parse error and return appropriate ErrorInfo with more specific categorization

    // Network and connection errors
    if (error.includes('network') || error.includes('fetch') || error.includes('connection') || error.includes('CORS')) {
      return {
        title: 'Connection Error',
        message: 'Unable to connect to our servers. Please check your internet connection and try again.',
        code: 'NETWORK_ERROR',
        recoverable: true,
        suggestions: [
          'Check your internet connection',
          'Refresh the page and try again',
          'Try again in a few minutes'
        ]
      };
    }

    // HTTP status errors
    if (error.includes('404') || error.includes('not found')) {
      return {
        title: 'Company Not Found',
        message: 'The requested company data could not be found. The ticker symbol may be invalid or delisted.',
        code: 'COMPANY_NOT_FOUND',
        recoverable: true,
        suggestions: [
          'Double-check the ticker symbol spelling',
          'Try searching for the company by name',
          'Verify the company is publicly traded'
        ]
      };
    }

    if (error.includes('401') || error.includes('unauthorized')) {
      return {
        title: 'Authentication Error',
        message: 'Your session has expired or you are not authorized to access this data.',
        code: 'AUTH_ERROR',
        recoverable: true,
        suggestions: [
          'Refresh the page to renew your session',
          'Try logging out and back in',
          'Contact support if the issue persists'
        ]
      };
    }

    if (error.includes('500') || error.includes('server error') || error.includes('internal')) {
      return {
        title: 'Server Error',
        message: 'Our servers are experiencing issues. This is temporary and our team has been notified.',
        code: 'SERVER_ERROR',
        recoverable: true,
        suggestions: [
          'Try again in a few minutes',
          'Check our status page for updates',
          'Contact support if the issue persists'
        ]
      };
    }

    if (error.includes('503') || error.includes('service unavailable')) {
      return {
        title: 'Service Temporarily Unavailable',
        message: 'Our valuation service is temporarily down for maintenance. Please try again shortly.',
        code: 'SERVICE_UNAVAILABLE',
        recoverable: true,
        suggestions: [
          'Try again in 5-10 minutes',
          'Check our status page for maintenance updates',
          'Check again shortly once provider limits reset'
        ]
      };
    }

    // Validation and data errors
    if (error.includes('validation') || error.includes('invalid')) {
      return {
        title: 'Invalid Data',
        message: 'The provided information is invalid or incomplete. Please review your inputs and try again.',
        code: 'VALIDATION_ERROR',
        recoverable: true,
        suggestions: [
          'Check all required fields are filled',
          'Ensure numeric values are valid',
          'Verify company symbol is correct'
        ]
      };
    }

    // Timeout errors
    if (error.includes('timeout')) {
      return {
        title: 'Request Timeout',
        message: 'The analysis is taking longer than expected. This might be due to high server load.',
        code: 'TIMEOUT_ERROR',
        recoverable: true,
        suggestions: [
          'Try again in a few minutes',
          'Consider using Quick Analysis for faster results',
          'Check your internet connection'
        ]
      };
    }

    // Rate limiting
    if (error.includes('rate limit') || error.includes('too many requests') || error.includes('429')) {
      return {
        title: 'Too Many Requests',
        message: 'You have made too many requests in a short time. Please wait a moment before trying again.',
        code: 'RATE_LIMIT_ERROR',
        recoverable: true,
        suggestions: [
          'Wait 30 seconds before trying again',
          'Avoid rapidly clicking buttons',
          'Consider upgrading for higher limits'
        ]
      };
    }

    // Financial data specific errors
    if (error.includes('financial data') || error.includes('insufficient data')) {
      return {
        title: 'Insufficient Financial Data',
        message: 'This company does not have enough financial data for a reliable DCF analysis.',
        code: 'INSUFFICIENT_DATA',
        recoverable: false,
        suggestions: [
          'Try a different company with more trading history',
          'Check if this is a recently listed company',
          'Consider using a different analysis method'
        ]
      };
    }

    // API key or subscription errors
    if (error.includes('api key') || error.includes('subscription') || error.includes('quota')) {
      return {
        title: 'Service Limitation',
        message: 'We have reached our data provider limits. Our team has been notified and this should resolve shortly.',
        code: 'API_QUOTA_ERROR',
        recoverable: true,
        suggestions: [
          'Try again in 10-15 minutes',
          'Consider using Quick Analysis for now',
          'Contact support if this persists'
        ]
      };
    }

    // Parse JSON errors
    if (error.includes('JSON') || error.includes('parse')) {
      return {
        title: 'Data Processing Error',
        message: 'There was an issue processing the financial data. This is usually temporary.',
        code: 'DATA_PARSE_ERROR',
        recoverable: true,
        suggestions: [
          'Try refreshing the page',
          'Wait a moment and try again',
          'Contact support if this keeps happening'
        ]
      };
    }

    // Default error with more information
    const errorCode = this.generateErrorCode(error);
    return {
      title: 'Unexpected Error',
      message: error || 'An unexpected error occurred while processing your request.',
      code: errorCode,
      recoverable: true,
      suggestions: [
        'Try refreshing the page',
        'Start a new analysis',
        'Contact support with error code: ' + errorCode
      ]
    };
  }

  private generateErrorCode(error: string): string {
    // Generate a more specific error code based on the error message
    const timestamp = Date.now().toString().slice(-6);

    if (error.length < 10) {
      return `ERR_GENERIC_${timestamp}`;
    }

    // Create a hash-like code from the error message
    let hash = 0;
    for (let i = 0; i < Math.min(error.length, 50); i++) {
      const char = error.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash; // Convert to 32bit integer
    }

    const hashCode = Math.abs(hash).toString(16).slice(0, 6).toUpperCase();
    return `ERR_${hashCode}_${timestamp}`;
  }

  getErrorType(): 'warning' | 'error' | 'critical' {
    const error = this.state?.error || '';

    if (error.includes('critical') || error.includes('fatal')) {
      return 'critical';
    }

    if (error.includes('warning') || error.includes('validation')) {
      return 'warning';
    }

    return 'error';
  }

  onRetryAction(): void {
    this.clearError();

    // Retry based on current state
    if (this.state?.selectedCompany) {
      // If we have a selected company, retry the analysis
      this.triggerAnalysis(this.state.selectedCompany.symbol, 'context-navigation');
    } else {
      // If no company selected, reset to start fresh
      this.dcfFacade.resetAnalysis();
    }
  }

  onResetToHome(): void {
    this.dcfFacade.resetAnalysis();
    this.router.navigate([this.defaultSearchRoute]);
  }

  private buildValuationRoute(symbol: string): string[] {
    return ['/automated-dcf-analysis', symbol.toUpperCase(), 'valuation'];
  }

  onContactSupport(): void {
    // In a real implementation, this would open a support ticket or contact form
    const errorInfo = this.getErrorInfo();
    const subject = encodeURIComponent('DCF Analysis Error Support');
    const body = encodeURIComponent(
      `I encountered an error while using the DCF Analysis tool:\n\n` +
      `Error: ${errorInfo.title}\n` +
      `Message: ${errorInfo.message}\n` +
      `Code: ${errorInfo.code || 'N/A'}\n\n` +
      `State: ${this.state?.selectedCompany ? 'Company Selected' : 'No Company'}\n` +
      `Company: ${this.state?.selectedCompany?.name || 'N/A'}\n\n` +
      `Please help me resolve this issue.`
    );

    const window = this.platformDetection.getWindow();
    if (window) {
      window.open(`mailto:stockvaluation.io@gmail.com?subject=${subject}&body=${body}`, '_blank');
    }
  }



  getSelectedCompanyAsSearchResult(): CompanySearchResult | null {
    if (!this.state || !this.state.selectedCompany) {
      return null;
    }
    return CompanyDataMapper.companyDataToSearchResult(this.state.selectedCompany);
  }

  getFinancialData(): FinancialDataDTO | undefined {
    const companyApiData = this.dcfFacade.getCompanyApiData();
    return companyApiData?.financialDataDTO;
  }

}
