export interface CompanyData {
  symbol: string;
  name: string;
  price?: number;
  exchange?: string;
  exchangeShortName?: string;
  type?: string;
  sector?: string;
  industry?: string;
  marketCap?: number;
  logo?: string;
  description?: string;
  website?: string;
  employees?: number;
}

export interface CompanySearchConfig {
  title: string;
  subtitle: string;
  placeholder: string;
  searchInstructions: string[];
  examples: string[];
}