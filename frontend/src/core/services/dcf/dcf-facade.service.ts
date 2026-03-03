import { Injectable, inject } from '@angular/core';
import { Observable, throwError, BehaviorSubject, combineLatest } from 'rxjs';
import { map, tap, catchError, switchMap, shareReplay } from 'rxjs/operators';

// Core services
import { LoggerService } from '../infrastructure/logger.service';
import { StockCompanySearchService } from '../search/stock-company-search.service';
import { CompanySearchResult } from '../search/company-search.service';

// DCF feature services
import { DCFStateService } from '../../../components/dcf-analysis/services/dcf-state.service';
import { DCFAnalysisService } from '../../../components/dcf-analysis/services/dcf-analysis.service';
import { DCFApiService } from '../../../components/dcf-analysis/services/dcf-api.service';
import { DCFNotificationService } from '../../../components/dcf-analysis/services/dcf-notification.service';
import { SavedAnalysisService, SavedAnalysis } from '../../../components/dcf-analysis/services/saved-analysis.service';

// Models
import {
  DCFState,
  CompanyData,
  AnalysisInputs,
  ValuationResults,
  DCFAssumptionsFormData
} from '../../../components/dcf-analysis/models';
import { DCFValuationResponse } from '../../../components/dcf-analysis/models/api-response.interface';

/**
 * Interface for facade search and analysis operations
 */
export interface DCFSearchAndAnalysisResult {
  company: CompanyData;
  apiData: DCFValuationResponse;
  results: ValuationResults;
}

/**
 * Interface for facade analysis state
 */
export interface DCFFacadeState extends DCFState {
  // Additional facade-specific state
  hasSearchError?: boolean;
  searchErrorMessage?: string | null;
  lastSearchQuery?: string | null;
}

/**
 * Configuration for DCF operations through the facade
 */
export interface DCFFacadeConfig {
  enableNotifications?: boolean;
  autoSaveState?: boolean;
  enableAnalyticsTracking?: boolean;
}

/**
 * DCF Facade Service - Simplified interface for DCF operations
 * 
 * This facade consolidates multiple DCF services into a single, coherent API
 * that reduces complexity for consuming components and provides unified error handling.
 * 
 * Benefits:
 * - Single injection point for components
 * - Simplified testing with fewer mocks needed
 * - Better encapsulation of business logic
 * - Unified error handling and notification system
 * - Automatic state coordination between services
 * 
 * @example
 * ```typescript
 * constructor(private dcfFacade: DCFFacadeService) {}
 * 
 * // Search and analyze in one operation
 * this.dcfFacade.searchAndAnalyzeCompany('AAPL').subscribe(result => {
 *   // Access company, apiData, and results in one place
 * });
 * 
 * // Access unified state
 * this.dcfFacade.state$.subscribe(state => {
 *   // Get all DCF state including search status
 * });
 * ```
 */
@Injectable({
  providedIn: 'root'
})
export class DCFFacadeService {
  private logger = inject(LoggerService);
  private dcfState = inject(DCFStateService);
  private dcfAnalysis = inject(DCFAnalysisService);
  private dcfApi = inject(DCFApiService);
  private dcfNotifications = inject(DCFNotificationService);
  private savedAnalysis = inject(SavedAnalysisService);
  private stockSearch = inject(StockCompanySearchService);

  // Enhanced state including search status
  private facadeStateSubject = new BehaviorSubject<DCFFacadeState>({
    selectedCompany: null,
    results: null,
    isLoading: false,
    error: null,
    hasSearchError: false,
    searchErrorMessage: null,
    lastSearchQuery: null
  });

  // Configuration
  private config: DCFFacadeConfig = {
    enableNotifications: true,
    autoSaveState: true,
    enableAnalyticsTracking: false // Future enhancement
  };

  // Public state observable combining DCF state with search state
  state$: Observable<DCFFacadeState> = combineLatest([
    this.dcfState.state$,
    this.facadeStateSubject.asObservable()
  ]).pipe(
    map(([dcfState, facadeState]) => ({
      ...dcfState,
      hasSearchError: facadeState.hasSearchError,
      searchErrorMessage: facadeState.searchErrorMessage,
      lastSearchQuery: facadeState.lastSearchQuery
    })),
    shareReplay(1)
  );

  constructor() {
    this.logger.info('DCFFacadeService initialized', undefined, 'DCFFacadeService');
  }

  // =============================================================================
  // PRIMARY OPERATIONS - High-level business operations
  // =============================================================================

  /**
   * Search for a company and perform quick analysis in one operation
   * This is the primary method for automated DCF analysis
   * 
   * @param symbol - Company ticker symbol (e.g., 'AAPL')
   * @returns Observable with company data, API data, and analysis results
   */
  searchAndAnalyzeCompany(symbol: string): Observable<DCFSearchAndAnalysisResult> {
    if (!symbol?.trim()) {
      return throwError(() => new Error('Company symbol is required'));
    }

    const normalizedSymbol = symbol.trim().toUpperCase();
    this.updateSearchState(normalizedSymbol, false, null);
    this.dcfState.setLoading(true);

    this.logger.info(`Starting search and analysis for ${normalizedSymbol}`, undefined, 'DCFFacadeService');

    return this.dcfAnalysis.performQuickAnalysis(normalizedSymbol).pipe(
      tap(({ companyData, rawApiData }) => {
        // Update state with company selection
        this.dcfState.setSelectedCompany(companyData, rawApiData);
        this.logger.info(`Company data loaded for ${companyData.name}`, undefined, 'DCFFacadeService');
      }),
      map(({ companyData, rawApiData, results }) => ({
        company: companyData,
        apiData: rawApiData,
        results
      })),
      tap(({ results }) => {
        // Update state with results
        this.dcfState.setResults(results);
        this.updateSearchState(normalizedSymbol, false, null);

        if (this.config.enableNotifications) {
          // Success notification handled by notification service
        }

        this.logger.info(`Analysis completed for ${normalizedSymbol}`, undefined, 'DCFFacadeService');
      }),
      catchError(error => {
        this.handleError(error, `search and analysis for ${normalizedSymbol}`);
        this.updateSearchState(normalizedSymbol, true, error.message);
        return throwError(() => error);
      })
    );
  }

  /**
   * Perform custom DCF analysis with user-provided assumptions
   * 
   * @param symbol - Company ticker symbol
   * @param inputs - User analysis inputs and assumptions
   * @returns Observable with analysis results
   */
  performCustomAnalysis(symbol: string, inputs: AnalysisInputs): Observable<ValuationResults> {
    if (!symbol?.trim()) {
      return throwError(() => new Error('Company symbol is required for custom analysis'));
    }

    if (!inputs) {
      return throwError(() => new Error('Analysis inputs are required for custom analysis'));
    }

    const normalizedSymbol = symbol.trim().toUpperCase();
    this.dcfState.setLoading(true);
    this.dcfState.setUserInputs(inputs);

    this.logger.info(`Starting custom analysis for ${normalizedSymbol}`, undefined, 'DCFFacadeService');

    return this.dcfAnalysis.performCustomAnalysis(normalizedSymbol, inputs).pipe(
      tap(results => {
        this.dcfState.setResults(results);

        if (this.config.enableNotifications) {
          this.dcfNotifications.showCustomCalculationSuccess();
        }

        this.logger.info(`Custom analysis completed for ${normalizedSymbol}`, undefined, 'DCFFacadeService');
      }),
      catchError(error => {
        this.handleError(error, `custom analysis for ${normalizedSymbol}`);

        if (this.config.enableNotifications) {
          this.dcfNotifications.showCustomCalculationError(error.message);
        }

        return throwError(() => error);
      })
    );
  }

  /**
   * Update analysis assumptions and recalculate
   * Used when users modify assumptions in the results page
   * 
   * @param formData - New DCF assumptions
   * @returns Observable with updated results
   */
  updateAssumptions(formData: DCFAssumptionsFormData): Observable<ValuationResults> {
    const currentState = this.dcfState.currentState;

    if (!currentState.selectedCompany) {
      const error = new Error('No company selected for assumptions update');
      this.handleError(error, 'assumptions update');
      return throwError(() => error);
    }

    // Convert form data to analysis inputs
    const analysisInputs: AnalysisInputs = this.convertFormDataToInputs(formData);

    return this.performCustomAnalysis(currentState.selectedCompany.symbol, analysisInputs);
  }

  /**
   * Recalculate DCF with new assumptions while preserving narratives
   * This method maintains the exact behavior of the original assumptions form:
   * - Direct API call without navigation
   * - Preserves existing narrative data
   * - Updates results in-place
   * 
   * @param symbol - Company symbol
   * @param dcfRequest - DCF calculation request
   * @returns Observable with API response (not converted to ValuationResults)
   */
  recalculateWithAssumptions(symbol: string, dcfRequest: any): Observable<any> {
    return this.dcfApi.calculateDCFValuation(symbol, dcfRequest).pipe(
      catchError(error => {
        this.handleError(error, `assumptions recalculation for ${symbol}`);
        return throwError(() => error);
      })
    );
  }

  /**
   * Convert API response to ValuationResults (exposed for container use)
   */
  convertApiResponseToResults(apiResponse: any): ValuationResults {
    return this.dcfAnalysis.convertApiResponseToResults(apiResponse);
  }

  /**
   * Set results directly (exposed for assumptions updates)
   */
  setResults(results: ValuationResults): void {
    this.dcfState.setResults(results);
  }

  /**
   * Access to notification service methods
   */
  showCustomCalculationSuccess(symbol?: string): void {
    if (this.config.enableNotifications) {
      this.dcfNotifications.showCustomCalculationSuccess(symbol);
    }
  }

  showCustomCalculationError(error: string): void {
    if (this.config.enableNotifications) {
      this.dcfNotifications.showCustomCalculationError(error);
    }
  }

  // =============================================================================
  // SEARCH OPERATIONS - Company search functionality
  // =============================================================================

  /**
   * Search for companies by query
   * Delegates to StockCompanySearchService but provides unified error handling
   * 
   * @param query - Search query (company name or symbol)
   * @returns Observable with search results
   */
  searchCompanies(query: string): Observable<CompanySearchResult[]> {
    if (!query?.trim()) {
      return throwError(() => new Error('Search query is required'));
    }

    this.updateSearchState(query, false, null);

    return this.stockSearch.isReady().pipe(
      switchMap(isReady => {
        if (!isReady) {
          throw new Error('Search service not ready');
        }

        return this.stockSearch.searchCompanies({ query, config: { maxResults: 10 } });
      }),
      tap(results => {
        this.updateSearchState(query, false, null);
        this.logger.debug(`Search completed for "${query}", found ${results.length} results`, undefined, 'DCFFacadeService');
      }),
      catchError(error => {
        this.updateSearchState(query, true, error.message);
        this.handleError(error, `company search for "${query}"`);
        return throwError(() => error);
      })
    );
  }

  /**
   * Search companies via API fallback
   * 
   * @param query - Search query
   * @returns Observable with API search results
   */
  searchCompaniesViaAPI(query: string): Observable<CompanySearchResult[]> {
    if (!query?.trim()) {
      return throwError(() => new Error('Search query is required'));
    }

    return this.stockSearch.searchCompanyViaAPI(query).pipe(
      catchError(error => {
        this.handleError(error, `API search for "${query}"`);
        return throwError(() => error);
      })
    );
  }

  // =============================================================================
  // STATE MANAGEMENT - State operations and persistence
  // =============================================================================

  /**
   * Get current DCF state
   */
  getCurrentState(): DCFFacadeState {
    const dcfState = this.dcfState.currentState;
    const facadeState = this.facadeStateSubject.value;

    return {
      ...dcfState,
      hasSearchError: facadeState.hasSearchError,
      searchErrorMessage: facadeState.searchErrorMessage,
      lastSearchQuery: facadeState.lastSearchQuery
    };
  }

  /**
   * Load saved state from storage
   * @returns boolean indicating if state was loaded
   */
  loadSavedState(): boolean {
    try {
      const loaded = this.dcfState.loadSavedStateIfAvailable();
      if (loaded) {
        this.logger.info('Saved state loaded successfully', undefined, 'DCFFacadeService');
      }
      return loaded;
    } catch (error) {
      this.handleError(error as Error, 'load saved state');
      return false;
    }
  }

  /**
   * Reset all analysis state and start fresh
   */
  resetAnalysis(): void {
    this.dcfState.reset();
    this.facadeStateSubject.next({
      selectedCompany: null,
      results: null,
      isLoading: false,
      error: null,
      hasSearchError: false,
      searchErrorMessage: null,
      lastSearchQuery: null
    });

    this.logger.info('Analysis state reset', undefined, 'DCFFacadeService');
  }

  /**
   * Reset to new analysis (preserves user preferences)
   */
  resetToNewAnalysis(): void {
    this.dcfState.resetToNewAnalysis();
    this.facadeStateSubject.next({
      selectedCompany: null,
      results: null,
      isLoading: false,
      error: null,
      hasSearchError: false,
      searchErrorMessage: null,
      lastSearchQuery: null
    });

    this.logger.info('Reset to new analysis', undefined, 'DCFFacadeService');
  }

  // =============================================================================
  // SAVED ANALYSIS OPERATIONS - Analysis history management
  // =============================================================================

  /**
   * Save current analysis
   * 
   * @param title - Optional custom title for the analysis
   * @param description - Optional description
   * @returns SavedAnalysis object or null if save failed
   */
  saveCurrentAnalysis(title?: string, description?: string): SavedAnalysis | null {
    const currentState = this.getCurrentState();

    if (!currentState.selectedCompany || !currentState.results) {
      this.logger.warn('Cannot save analysis: missing company or results', undefined, 'DCFFacadeService');
      return null;
    }

    try {
      const savedAnalysis = this.savedAnalysis.saveAnalysis(
        currentState.selectedCompany,
        currentState.results,
        title,
        description
      );

      if (this.config.enableNotifications) {
        // Could add a save success notification here
      }

      this.logger.info(`Analysis saved for ${currentState.selectedCompany.name}`, undefined, 'DCFFacadeService');
      return savedAnalysis;
    } catch (error) {
      this.handleError(error as Error, 'save analysis');
      return null;
    }
  }

  /**
   * Get all saved analyses
   */
  getSavedAnalyses(): Observable<SavedAnalysis[]> {
    return this.savedAnalysis.savedAnalyses$;
  }

  /**
   * Load a saved analysis into current state
   * 
   * @param analysis - Saved analysis to load
   */
  loadSavedAnalysis(analysis: SavedAnalysis): void {
    try {
      this.dcfState.setSelectedCompany(analysis.company);
      this.dcfState.setResults(analysis.results);

      this.logger.info(`Loaded saved analysis for ${analysis.company.name}`, undefined, 'DCFFacadeService');
    } catch (error) {
      this.handleError(error as Error, 'load saved analysis');
    }
  }

  /**
   * Delete a saved analysis
   * 
   * @param analysisId - ID of analysis to delete
   */
  deleteSavedAnalysis(analysisId: string): void {
    try {
      this.savedAnalysis.deleteAnalysis(analysisId);
      this.logger.info(`Deleted saved analysis ${analysisId}`, undefined, 'DCFFacadeService');
    } catch (error) {
      this.handleError(error as Error, 'delete saved analysis');
    }
  }

  // =============================================================================
  // UTILITY OPERATIONS - Export, configuration, etc.
  // =============================================================================


  /**
   * Update facade configuration
   * 
   * @param newConfig - Partial configuration to update
   */
  updateConfig(newConfig: Partial<DCFFacadeConfig>): void {
    this.config = { ...this.config, ...newConfig };
    this.logger.debug('Facade configuration updated', newConfig, 'DCFFacadeService');
  }

  /**
   * Get current facade configuration
   */
  getConfig(): DCFFacadeConfig {
    return { ...this.config };
  }

  /**
   * Get company API data for detailed financial information
   * Used by results page for financial data display
   */
  getCompanyApiData(): any {
    return this.dcfState.getCompanyApiData();
  }

  /**
   * Set error state
   * @param error - Error message to display
   */
  setError(error: string | null): void {
    this.dcfState.setError(error);
  }

  /**
   * Set loading state
   * @param loading - Loading state
   */
  setLoading(loading: boolean): void {
    this.dcfState.setLoading(loading);
  }

  // =============================================================================
  // PRIVATE HELPER METHODS
  // =============================================================================

  /**
   * Update search-related state
   */
  private updateSearchState(query: string | null, hasError: boolean, errorMessage: string | null): void {
    const currentState = this.facadeStateSubject.value;
    this.facadeStateSubject.next({
      ...currentState,
      lastSearchQuery: query,
      hasSearchError: hasError,
      searchErrorMessage: errorMessage
    });
  }

  /**
   * Centralized error handling
   */
  private handleError(error: Error, operation: string): void {
    const errorMessage = `Failed to ${operation}: ${error.message}`;
    this.logger.error(`DCF Facade Error - ${operation}`, error, 'DCFFacadeService');
    this.dcfState.setError(errorMessage);
  }

  /**
   * Convert DCF assumptions form data to analysis inputs
   * This bridges the gap between form structure and analysis service expectations
   * 
   * Note: This is a simplified mapping. For full implementation, we would need
   * to properly map all the form fields to the expected AnalysisInputs structure.
   */
  private convertFormDataToInputs(formData: DCFAssumptionsFormData): AnalysisInputs {
    // For now, create a basic mapping using available core assumptions
    // This would need to be expanded based on the actual form structure
    const coreAssumptions = formData.coreAssumptions;

    return {
      assumptions: {
        revenueGrowthYears1to5: coreAssumptions.revenueNextYear,
        revenueGrowthYears6to10: coreAssumptions.compoundAnnualGrowth2_5,
        targetEbitdaMargin: coreAssumptions.targetPreTaxOperatingMargin,
        capexAsPercentOfRevenue: coreAssumptions.salesToCapitalYears1To5,
        costOfEquity: coreAssumptions.initialCostCapital,
        terminalGrowthRate: 3.0, // Default value
        workingCapitalGrowth: 0, // Default value
        costOfDebt: 5.0, // Default value
        targetDebtRatio: 30 // Default value
      },
      advancedSettings: {
        taxRate: formData.overrides.overrideAssumptionTaxRate.isOverride
          ? formData.overrides.overrideAssumptionTaxRate.overrideCost
          : 25, // Default tax rate
        enableSensitivityAnalysis: false,
        sensitivityVariables: [],
        projectionYears: 10,
        includeOptionsValue: formData.riskAssessment.hasEmployeeOptions
      }
    };
  }
}
