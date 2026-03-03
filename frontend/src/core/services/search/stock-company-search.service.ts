import { Injectable, Inject, PLATFORM_ID } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { Observable, BehaviorSubject, of, throwError } from 'rxjs';
import { map, tap, catchError, switchMap } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';

import { CompanySearchService, CompanySearchResult, SearchOptions } from './company-search.service';
import { DCFApiService } from '../../../components/dcf-analysis/services/dcf-api.service';
import { LoggerService } from '../infrastructure/logger.service';

// Local interface for stock data
export interface StockSearchResult {
  symbol: string;
  name: string;
  price?: number;
  exchange?: string;
  exchangeShortName?: string;
  type?: string;
}

/**
 * Stock-specific implementation of CompanySearchService
 * Self-contained service with async JSON data loading
 */
@Injectable({
  providedIn: 'root'
})
export class StockCompanySearchService extends CompanySearchService {
  private companiesCache: CompanySearchResult[] = [];
  private isReadySubject = new BehaviorSubject<boolean>(false);
  private stocks: StockSearchResult[] = [];
  private dataLoaded = false;

  constructor(
    private dcfApiService: DCFApiService,
    private logger: LoggerService,
    private http: HttpClient,
    @Inject(PLATFORM_ID) private platformId: Object
  ) {
    super();
    
    // Only load tickers data in browser, not during SSR
    if (isPlatformBrowser(this.platformId)) {
      this.loadTickersData();
    } else {
      // During SSR, mark as not ready
      this.isReadySubject.next(false);
    }
  }

  /**
   * Load tickers data from assets asynchronously
   */
  private loadTickersData(): void {
    if (this.dataLoaded) {
      return;
    }

    this.http.get<StockSearchResult[]>('/assets/data/tickers.min.json')
      .pipe(
        tap(data => {
          this.logger.info(`Loaded ${data.length} tickers from assets - search ready`, {}, 'StockCompanySearchService');
        }),
        catchError(error => {
          this.logger.error('Failed to load tickers data from assets', error, 'StockCompanySearchService');
          this.isReadySubject.next(false);
          return of([]); // Return empty array as fallback
        })
      )
      .subscribe(data => {
        try {
          this.stocks = data;
          this.companiesCache = this.mapStockResultsToCompanyResults(this.stocks);
          this.dataLoaded = true;
          this.isReadySubject.next(true);
        } catch (error: any) {
          this.logger.error('Failed to process tickers data', error, 'StockCompanySearchService');
          this.isReadySubject.next(false);
        }
      });
  }

  private mapStockResultsToCompanyResults(stockResults: StockSearchResult[]): CompanySearchResult[] {
    return stockResults.map(stock => ({
      symbol: stock.symbol,
      name: stock.name,
      price: stock.price,
      exchange: stock.exchange,
      exchangeShortName: stock.exchangeShortName,
      type: stock.type
    }));
  }

  searchCompanies(options: SearchOptions): Observable<CompanySearchResult[]> {
    const { query, config = {} } = options;
    
    if (!query.trim()) {
      return of([]);
    }

    // If data not loaded yet, wait for it
    if (!this.dataLoaded) {
      return this.isReadySubject.pipe(
        switchMap(isReady => {
          if (isReady) {
            return this.performSearch(query, config);
          } else {
            return of([]);
          }
        })
      );
    }

    return this.performSearch(query, config);
  }

  private performSearch(query: string, config: any = {}): Observable<CompanySearchResult[]> {
    const searchTerm = query.toLowerCase();
    
    // Filter companies from local cache
    const filteredCompanies = this.companiesCache.filter(company =>
      company.name.toLowerCase().includes(searchTerm) ||
      company.symbol.toLowerCase().includes(searchTerm)
    );

    // Apply ranking if enabled (default: true)
    const rankedCompanies = config.enableRanking !== false 
      ? this.rankResults(filteredCompanies, searchTerm)
      : filteredCompanies;

    // Apply result limit
    const maxResults = config.maxResults || 10;
    const limitedResults = rankedCompanies.slice(0, maxResults);

    return of(limitedResults);
  }

  /**
   * Search for a company using the API as fallback when local search fails
   * This method is called when user chooses to "continue searching" for a ticker
   */
  searchCompanyViaAPI(ticker: string): Observable<CompanySearchResult[]> {
    if (!ticker.trim()) {
      return of([]);
    }

    const normalizedTicker = ticker.toUpperCase().trim();
    
    return this.dcfApiService.getBaselineValuation(normalizedTicker).pipe(
      map(valuationData => {
        const searchResult: CompanySearchResult = {
          symbol: normalizedTicker,
          name: valuationData.companyName || normalizedTicker,
          price: valuationData.companyDTO?.price || 0,
          exchange: 'N/A',
          exchangeShortName: 'N/A',
          type: 'Stock'
        };

        return [searchResult];
      }),
      catchError(error => {
        this.logger.error('API search error', error, 'StockCompanySearchService');
        // Return empty array with error information that can be handled by UI
        return throwError(() => ({
          message: this.getErrorMessage(error),
          originalError: error
        }));
      })
    );
  }

  getAllCompanies(): Observable<CompanySearchResult[]> {
    return of([...this.companiesCache]);
  }

  rankResults(companies: CompanySearchResult[], query: string): CompanySearchResult[] {
    const searchTerm = query.toLowerCase();
    
    // Add relevance scores and sort
    const companiesWithScores = companies.map(company => ({
      ...company,
      relevanceScore: this.calculateRelevanceScore(company, searchTerm)
    }));

    return companiesWithScores
      .sort((a, b) => b.relevanceScore - a.relevanceScore)
      .map(({ relevanceScore, ...company }) => company); // Remove score from final result
  }

  isReady(): Observable<boolean> {
    return this.isReadySubject.asObservable();
  }

  /**
   * Calculate relevance score for search results
   * Higher scores appear first in results
   */
  private calculateRelevanceScore(company: CompanySearchResult, searchTerm: string): number {
    const symbol = company.symbol.toLowerCase();
    const name = company.name.toLowerCase();
    let score = 0;

    // 1. Exact symbol match (highest priority) - Score: 1000
    if (symbol === searchTerm) {
      score += 1000;
    }
    // 2. Symbol starts with search term - Score: 800
    else if (symbol.startsWith(searchTerm)) {
      score += 800;
    }
    // 3. Symbol contains search term - Score: 600
    else if (symbol.includes(searchTerm)) {
      score += 600;
    }

    // 4. Exact company name match - Score: 900
    if (name === searchTerm) {
      score += 900;
    }
    // 5. Company name starts with search term - Score: 700
    else if (name.startsWith(searchTerm)) {
      score += 700;
    }
    // 6. Company name contains search term as whole word - Score: 500
    else if (this.containsWholeWord(name, searchTerm)) {
      score += 500;
    }
    // 7. Company name contains search term anywhere - Score: 300
    else if (name.includes(searchTerm)) {
      score += 300;
    }

    // 8. Bonus points for shorter names (more likely to be relevant)
    if (name.length <= 20) {
      score += 50;
    }

    // 9. Bonus points for well-known symbols (common patterns)
    if (this.isWellKnownSymbol(symbol)) {
      score += 25;
    }

    // 10. Length-based relevance for partial matches
    if (searchTerm.length >= 3) {
      const symbolMatchLength = this.getMatchLength(symbol, searchTerm);
      const nameMatchLength = this.getMatchLength(name, searchTerm);
      score += (symbolMatchLength * 10) + (nameMatchLength * 5);
    }

    return score;
  }

  /**
   * Check if search term appears as a whole word in the text
   */
  private containsWholeWord(text: string, searchTerm: string): boolean {
    const regex = new RegExp(`\\b${searchTerm}\\b`, 'i');
    return regex.test(text);
  }

  /**
   * Check if symbol follows common patterns for well-known companies
   */
  private isWellKnownSymbol(symbol: string): boolean {
    // Common patterns: 1-5 characters, all lowercase (normalized)
    return symbol.length <= 5 && /^[a-z]+$/.test(symbol);
  }

  /**
   * Calculate the length of the matching portion
   */
  private getMatchLength(text: string, searchTerm: string): number {
    let matchLength = 0;
    for (let i = 0; i < Math.min(text.length, searchTerm.length); i++) {
      if (text[i] === searchTerm[i]) {
        matchLength++;
      } else {
        break;
      }
    }
    return matchLength;
  }

  /**
   * Get user-friendly error message from API error
   */
  private getErrorMessage(error: any): string {
    if (error.status === 404) {
      return 'Company not found. Please check the ticker symbol and try again.';
    } else if (error.status === 400) {
      return 'Invalid ticker symbol format. Please enter a valid stock symbol.';
    } else if (error.status === 500) {
      return 'Server error occurred. Please try again later.';
    } else if (error.status === 0) {
      return 'Network error. Please check your internet connection.';
    } else {
      return 'Unable to find this company. Please verify the ticker symbol is correct.';
    }
  }
}
