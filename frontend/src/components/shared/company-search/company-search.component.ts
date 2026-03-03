import { Component, Input, Output, EventEmitter, OnInit, ViewChild, DestroyRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { switchMap } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CompanySearchService, CompanySearchResult, SearchConfig, StockCompanySearchService } from '../../../core/services';
import { CompanySearchInputComponent, SearchInputConfig, SearchInputSize } from '../company-search-input/company-search-input.component';
import { CompanySearchResultsComponent, SearchResultsConfig, SearchResultsSize } from '../company-search-results/company-search-results.component';
import { SearchSuggestionsComponent, SearchSuggestionsSize } from '../search-suggestions/search-suggestions.component';
import { LoggerService } from '../../../core/services';

export type SearchSize = 'sm' | 'md' | 'lg';

export interface CompanySearchConfig {
  search?: SearchConfig;
  input?: SearchInputConfig;
  results?: SearchResultsConfig;
  suggestions?: {
    show?: boolean;
    items?: string[];
  };
  hideSuggestions?: boolean;
  size?: SearchSize;
}

/**
 * Smart container component for company search functionality
 * Orchestrates pure UI components and delegates business logic to services
 */
@Component({
    selector: 'app-company-search',
    imports: [
        CommonModule,
        CompanySearchInputComponent,
        CompanySearchResultsComponent,
        SearchSuggestionsComponent
    ],
    template: `
    <div class="company-search-container" [ngClass]="'size-' + (config.size || 'md')">
      <!-- Search Input Section -->
      <div class="search-input-section">
        <app-company-search-input
          [config]="getInputConfig()"
          [loading]="isSearching"
          [loadingMessage]="loadingMessage"
          [selectedCompany]="selectedCompany"
          [isInitializing]="isSearchServiceInitializing"
          (queryChanged)="onQueryChanged($event)"
          (clearClicked)="onClearSearch()">
        </app-company-search-input>

        <!-- Search Results Dropdown -->
        <app-company-search-results
          [results]="searchResults"
          [loading]="isSearching"
          [showResults]="showResults"
          [config]="getResultsConfig()"
          [currentQuery]="currentQuery"
          [apiSearchLoading]="isApiSearching"
          [apiSearchError]="apiSearchError"
          (companySelected)="onCompanySelected($event)"
          (viewMoreClicked)="onViewMoreResults($event)"
          (apiSearchRequested)="onApiSearchRequested($event)">
        </app-company-search-results>

        <!-- Search Suggestions -->
        <app-search-suggestions
          #searchSuggestions
          *ngIf="!config.hideSuggestions"
          [showSuggestions]="config.suggestions?.show !== false"
          [searchQuery]="currentQuery"
          [selectedCompany]="selectedCompany"
          [suggestions]="config.suggestions?.items || []"
          [size]="config.size || 'md'"
          (suggestionSelected)="onSuggestionSelected($event)">
        </app-search-suggestions>
      </div>
    </div>
  `,
    styleUrls: ['./company-search.component.scss']
})
export class CompanySearchComponent implements OnInit {
  @Input() searchService!: CompanySearchService;
  @Input() config: CompanySearchConfig = {};
  @Input() selectedCompany: CompanySearchResult | null = null;
  
  @Output() companySelected = new EventEmitter<CompanySearchResult>();
  @Output() viewMoreResults = new EventEmitter<CompanySearchResult[]>();
  @Output() searchStateChanged = new EventEmitter<{searching: boolean, query: string, hasResults: boolean}>();

  @ViewChild(CompanySearchInputComponent) searchInput!: CompanySearchInputComponent;
  @ViewChild('searchSuggestions') searchSuggestions!: SearchSuggestionsComponent;

  private destroyRef = inject(DestroyRef);
  
  currentQuery = '';
  searchResults: CompanySearchResult[] = [];
  isSearching = false;
  loadingMessage = 'Searching companies...';
  isApiSearching = false;
  apiSearchError: string | null = null;
  isSearchServiceInitializing = false;
  
  constructor(private logger: LoggerService) {}

  ngOnInit(): void {
    // Track search service initialization state
    this.trackSearchServiceInitialization();
  }
  
  private trackSearchServiceInitialization(): void {
    if (this.searchService) {
      this.searchService.isReady()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(isReady => {
          this.isSearchServiceInitializing = !isReady;
          this.emitSearchState();
        });
    }
  }


  get showResults(): boolean {
    return !!(this.currentQuery && !this.selectedCompany);
  }

  onQueryChanged(query: string): void {
    this.currentQuery = query;
    
    if (!query.trim()) {
      this.clearResults();
      return;
    }

    this.performSearch(query);
  }

  onClearSearch(): void {
    this.currentQuery = '';
    this.clearResults();
    this.emitSearchState();
  }

  onCompanySelected(company: CompanySearchResult): void {
    this.selectedCompany = company;
    this.clearResults();

    // Track the successful company selection in recent searches using ticker
    if (this.searchSuggestions && company.symbol) {
      this.searchSuggestions.trackSearch(company.symbol);
    }

    // Emit the selection - let parent component handle data enrichment
    this.companySelected.emit(company);
    this.emitSearchState();
  }


  onSuggestionSelected(suggestion: string): void {
    this.currentQuery = suggestion;
    // Also update the search input component
    if (this.searchInput) {
      this.searchInput.searchQuery = suggestion;
    }
    this.performSearch(suggestion);
  }

  onViewMoreResults(results: CompanySearchResult[]): void {
    this.viewMoreResults.emit(results);
  }

  onApiSearchRequested(query: string): void {
    // Check if the search service is the StockCompanySearchService with API capability
    if (!(this.searchService instanceof StockCompanySearchService)) {
      this.logger.warn('API search not supported by current search service', undefined, 'CompanySearchComponent');
      return;
    }

    this.isApiSearching = true;
    this.apiSearchError = null;
    this.emitSearchState();

    this.searchService.searchCompanyViaAPI(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => {
          this.searchResults = results;
          this.isApiSearching = false;
          this.emitSearchState();
        },
        error: (error) => {
          this.logger.error('API search failed', error, 'CompanySearchComponent');
          this.apiSearchError = error.message || 'Failed to search company via API';
          this.isApiSearching = false;
          this.emitSearchState();
        }
      });
  }

  /**
   * Reset the loading state - called by parent components
   */
  resetLoadingState(): void {
    this.isSearching = false;
    this.emitSearchState();
  }

  /**
   * Clear selected company and reset search
   */
  clearSelection(): void {
    this.selectedCompany = null;
    this.currentQuery = '';
    this.clearResults();
  }

  private performSearch(query: string): void {
    this.isSearching = true;
    this.loadingMessage = 'Searching companies...';
    this.emitSearchState();

    // Wait for search service to be ready before searching
    this.searchService.isReady()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(isReady => {
          if (!isReady) {
            throw new Error('Search service not ready');
          }
          
          const searchOptions = {
            query,
            config: this.config.search
          };

          return this.searchService.searchCompanies(searchOptions);
        })
      )
      .subscribe({
        next: (results) => {
          this.searchResults = results;
          this.isSearching = false;
          this.emitSearchState();
        },
        error: (error) => {
          this.logger.error('Search failed', error, 'CompanySearchComponent');
          this.searchResults = [];
          this.isSearching = false;
          this.emitSearchState();
        }
      });
  }


  private clearResults(): void {
    this.searchResults = [];
    this.apiSearchError = null;
  }


  private emitSearchState(): void {
    this.searchStateChanged.emit({
      searching: this.isSearching,
      query: this.currentQuery,
      hasResults: this.searchResults.length > 0
    });
  }

  getInputConfig(): SearchInputConfig {
    return {
      ...this.config.input,
      size: this.config.size || 'md'
    };
  }

  getResultsConfig(): SearchResultsConfig {
    return {
      ...this.config.results,
      size: this.config.size || 'md'
    };
  }
}