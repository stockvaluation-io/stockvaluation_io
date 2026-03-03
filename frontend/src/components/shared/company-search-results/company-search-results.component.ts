import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanySearchResult } from '../../../core/services';
import { CompanyCardComponent } from '../../dcf-analysis/shared';

export type SearchResultsSize = 'sm' | 'md' | 'lg';

export interface SearchResultsConfig {
  maxDisplayed?: number;
  showViewMore?: boolean;
  showNoResults?: boolean;
  noResultsMessage?: string;
  loadingMessage?: string;
  size?: SearchResultsSize;
  showApiSearch?: boolean;
  apiSearchLabel?: string;
}

/**
 * Pure UI component for displaying company search results
 * Handles only presentation of results list
 */
@Component({
    selector: 'app-company-search-results',
    imports: [CommonModule, CompanyCardComponent],
    template: `
    <div class="search-results-container" [ngClass]="'size-' + (config.size || 'md')" *ngIf="showResults">
      <!-- Loading State -->
      <div class="results-loading" *ngIf="loading">
        <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
        <span>{{ config.loadingMessage || 'Searching companies...' }}</span>
      </div>

      <!-- Results List -->
      <div class="results-list" *ngIf="!loading && displayedResults.length > 0">
        <app-company-card
          *ngFor="let company of displayedResults; trackBy: trackBySymbol"
          [company]="mapToCompanyData(company)"
          [isSelected]="false"
          [showActions]="false"
          [showDetails]="false"
          (companySelected)="onCompanySelect($event)">
        </app-company-card>
        
        <!-- View More Results Button -->
        <div class="view-more-section" 
             *ngIf="config.showViewMore !== false && hasMoreResults">
          <button 
            class="view-more-button"
            type="button"
            (click)="onViewMore()"
            [attr.aria-label]="'View ' + remainingCount + ' more results'"
          >
            <span class="view-more-text">
              View {{ remainingCount }} more result{{ remainingCount === 1 ? '' : 's' }}
            </span>
            <i class="pi pi-external-link view-more-icon"></i>
          </button>
        </div>
      </div>

      <!-- No Results State -->
      <div class="no-results" 
           *ngIf="!loading && displayedResults.length === 0 && config.showNoResults !== false">
        <div class="no-results-icon">
          <i class="pi pi-exclamation-circle" aria-hidden="true"></i>
        </div>
        <h4 class="no-results-title">No companies found</h4>
        <p class="no-results-message">
          {{ config.noResultsMessage || 'Try searching with a different company name or stock symbol.' }}
        </p>
        
        <!-- API Search Fallback Option -->
        <div class="api-search-fallback" 
             *ngIf="config.showApiSearch !== false && currentQuery.trim().length > 0 && !apiSearchLoading && !apiSearchError">
          <p class="api-search-description">
            Can't find what you're looking for? We can search our broader database.
          </p>
          <button 
            class="api-search-button"
            type="button"
            (click)="onApiSearchRequested()"
            [attr.aria-label]="'Search for ' + currentQuery + ' in our broader database'"
          >
            <i class="pi pi-search api-search-icon"></i>
            <span>{{ config.apiSearchLabel || 'Continue searching for "' + currentQuery + '"' }}</span>
          </button>
        </div>
        
        <!-- API Search Loading -->
        <div class="api-search-loading" *ngIf="apiSearchLoading">
          <i class="pi pi-spin pi-spinner" aria-hidden="true"></i>
          <span>Searching broader database...</span>
        </div>
        
        <!-- API Search Error -->
        <div class="api-search-error" *ngIf="apiSearchError">
          <div class="error-icon">
            <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
          </div>
          <p class="error-message">{{ apiSearchError }}</p>
          <button 
            class="retry-button"
            type="button"
            (click)="onApiSearchRequested()"
            [attr.aria-label]="'Retry search for ' + currentQuery"
          >
            <i class="pi pi-refresh"></i>
            <span>Try Again</span>
          </button>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./company-search-results.component.scss']
})
export class CompanySearchResultsComponent {
  @Input() results: CompanySearchResult[] = [];
  @Input() loading = false;
  @Input() showResults = false;
  @Input() config: SearchResultsConfig = {};
  @Input() currentQuery = '';
  @Input() apiSearchLoading = false;
  @Input() apiSearchError: string | null = null;
  
  @Output() companySelected = new EventEmitter<CompanySearchResult>();
  @Output() viewMoreClicked = new EventEmitter<CompanySearchResult[]>();
  @Output() apiSearchRequested = new EventEmitter<string>();

  get maxDisplayed(): number {
    return this.config.maxDisplayed || 5;
  }

  get displayedResults(): CompanySearchResult[] {
    return this.results.slice(0, this.maxDisplayed);
  }

  get hasMoreResults(): boolean {
    return this.results.length > this.maxDisplayed;
  }

  get remainingCount(): number {
    return this.results.length - this.maxDisplayed;
  }

  onCompanySelect(companyData: any): void {
    // Find the original search result that matches this company
    const searchResult = this.results.find(result => result.symbol === companyData.symbol);
    if (searchResult) {
      this.companySelected.emit(searchResult);
    }
  }

  onViewMore(): void {
    this.viewMoreClicked.emit([...this.results]);
  }

  onApiSearchRequested(): void {
    this.apiSearchRequested.emit(this.currentQuery.trim());
  }

  trackBySymbol(index: number, company: CompanySearchResult): string {
    return company.symbol;
  }

  /**
   * Map CompanySearchResult to CompanyData format expected by CompanyCardComponent
   * This is a temporary bridge until we can refactor CompanyCardComponent to use the new interface
   */
  mapToCompanyData(company: CompanySearchResult): any {
    return {
      symbol: company.symbol,
      name: company.name,
      price: company.price,
      exchange: company.exchange,
      exchangeShortName: company.exchangeShortName,
      type: company.type,
      industry: company.industry
    };
  }
}