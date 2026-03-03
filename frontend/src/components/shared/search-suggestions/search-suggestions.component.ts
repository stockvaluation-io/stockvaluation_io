import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoggerService, PlatformDetectionService } from '../../../core/services';

export type SearchSuggestionsSize = 'sm' | 'md' | 'lg';

@Component({
    selector: 'app-search-suggestions',
    imports: [CommonModule],
    template: `
    <!-- Search Suggestions -->
    <div class="search-suggestions" 
         [ngClass]="getSuggestionClasses()"
         *ngIf="showSuggestions">
      <span class="suggestions-text">{{ getSuggestionsLabel() }}</span>
      <div class="suggestions-tags">
        <button 
          *ngFor="let suggestion of getDisplaySuggestions()"
          class="suggestion-tag"
          type="button"
          (click)="onSuggestionClick(suggestion)"
        >
          {{ suggestion }}
        </button>
      </div>
    </div>
  `,
    styleUrls: ['./search-suggestions.component.scss']
})
export class SearchSuggestionsComponent implements OnInit {
  @Input() showSuggestions = true;
  @Input() searchQuery = '';
  @Input() selectedCompany: any = null;
  @Input() suggestions: string[] = [];
  @Input() size: SearchSuggestionsSize = 'md';
  
  @Output() suggestionSelected = new EventEmitter<string>();

  constructor(
    private logger: LoggerService,
    private platformDetection: PlatformDetectionService
  ) {}

  private readonly RECENT_SEARCHES_KEY = 'company_search_recent';
  private readonly MAX_RECENT_SEARCHES = 6;
  
  // Default suggestions
  defaultSuggestions = ['Apple', 'TSLA', 'Microsoft', 'NVDA', 'AMZN', 'GOOGL'];
  recentSearches: string[] = [];

  ngOnInit(): void {
    this.loadRecentSearches();
  }

  onSuggestionClick(suggestion: string): void {
    this.addToRecentSearches(suggestion);
    this.suggestionSelected.emit(suggestion);
  }

  getSuggestionsLabel(): string {
    return this.recentSearches.length > 0 ? 'Recent:' : 'Example:';
  }

  getDisplaySuggestions(): string[] {
    // If we have recent searches, prioritize those over everything else
    if (this.recentSearches.length > 0) {
      return this.recentSearches;
    }
    
    // If we have custom suggestions from parent, use those
    if (this.suggestions && this.suggestions.length > 0) {
      return this.suggestions;
    }
    
    // Fall back to default examples
    return this.defaultSuggestions;
  }

  private loadRecentSearches(): void {
    const localStorage = this.platformDetection.getLocalStorage();
    if (!localStorage) {
      return;
    }

    try {
      const stored = localStorage.getItem(this.RECENT_SEARCHES_KEY);
      if (stored) {
        this.recentSearches = JSON.parse(stored);
      }
    } catch (error) {
      this.logger.warn('Failed to load recent searches', error, 'SearchSuggestionsComponent');
      this.recentSearches = [];
    }
  }

  /**
   * Public method to track a search from external sources (e.g., when user types and searches)
   */
  trackSearch(search: string): void {
    this.addToRecentSearches(search);
  }

  private addToRecentSearches(search: string): void {
    const localStorage = this.platformDetection.getLocalStorage();
    
    try {
      // Normalize the search term
      const normalizedSearch = search.trim();
      if (!normalizedSearch) return;
      
      // Remove if already exists (to move to front)
      this.recentSearches = this.recentSearches.filter(s => s !== normalizedSearch);
      
      // Add to front
      this.recentSearches.unshift(normalizedSearch);
      
      // Limit to max recent searches
      if (this.recentSearches.length > this.MAX_RECENT_SEARCHES) {
        this.recentSearches = this.recentSearches.slice(0, this.MAX_RECENT_SEARCHES);
      }
      
      // Save to localStorage if available
      if (localStorage) {
        localStorage.setItem(this.RECENT_SEARCHES_KEY, JSON.stringify(this.recentSearches));
      }
    } catch (error) {
      this.logger.warn('Failed to save recent search', error, 'SearchSuggestionsComponent');
    }
  }

  getSuggestionClasses(): { [key: string]: boolean } {
    return {
      'hidden': !this.showSuggestions || !!this.searchQuery || !!this.selectedCompany,
      [`size-${this.size || 'md'}`]: true
    };
  }
}