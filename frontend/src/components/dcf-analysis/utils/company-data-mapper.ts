import { CompanySearchResult } from '../../../core/services';
import { CompanyData } from '../models';

/**
 * Utility to map between different company data interfaces
 * Bridges the old DCF-specific CompanyData with the new generic CompanySearchResult
 */
export class CompanyDataMapper {
  
  /**
   * Convert CompanySearchResult to DCF CompanyData format
   */
  static searchResultToCompanyData(searchResult: CompanySearchResult): CompanyData {
    return {
      symbol: searchResult.symbol,
      name: searchResult.name,
      price: searchResult.price,
      exchange: searchResult.exchange,
      exchangeShortName: searchResult.exchangeShortName,
      type: searchResult.type,
      industry: searchResult.industry,
      // Add any DCF-specific fields with defaults
      marketCap: undefined,
      sector: undefined,
      description: undefined,
      website: undefined,
      employees: undefined
    };
  }

  /**
   * Convert DCF CompanyData to CompanySearchResult format
   */
  static companyDataToSearchResult(companyData: CompanyData): CompanySearchResult {
    return {
      symbol: companyData.symbol,
      name: companyData.name,
      price: companyData.price,
      exchange: companyData.exchange,
      exchangeShortName: companyData.exchangeShortName,
      type: companyData.type,
      industry: companyData.industry
    };
  }

  /**
   * Convert enriched data back to CompanyData format
   */
  static enrichedDataToCompanyData(enrichedData: any): CompanyData {
    return {
      symbol: enrichedData.symbol,
      name: enrichedData.name,
      price: enrichedData.price,
      exchange: enrichedData.exchange,
      exchangeShortName: enrichedData.exchangeShortName,
      type: enrichedData.type,
      industry: enrichedData.industry,
      marketCap: enrichedData.marketCap,
      sector: enrichedData.sector,
      description: enrichedData.description,
      website: enrichedData.website,
      employees: enrichedData.employees
    };
  }
}