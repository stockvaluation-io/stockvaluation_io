import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface CompanySearchResult {
  symbol: string;
  name: string;
  price?: number;
  exchange?: string;
  exchangeShortName?: string;
  type?: string;
  industry?: string;
}

export interface SearchConfig {
  maxResults?: number;
  debounceMs?: number;
  enableRanking?: boolean;
  filterExchanges?: string[];
}

export interface SearchOptions {
  query: string;
  config?: SearchConfig;
}

/**
 * Abstract service for company searching functionality
 * Allows different implementations (stock data, mock data, etc.)
 */
export abstract class CompanySearchService {
  /**
   * Search for companies based on query
   */
  abstract searchCompanies(options: SearchOptions): Observable<CompanySearchResult[]>;

  /**
   * Get all available companies (for local filtering)
   */
  abstract getAllCompanies(): Observable<CompanySearchResult[]>;

  /**
   * Rank search results by relevance
   */
  abstract rankResults(companies: CompanySearchResult[], query: string): CompanySearchResult[];

  /**
   * Check if service is ready to perform searches
   */
  abstract isReady(): Observable<boolean>;
}