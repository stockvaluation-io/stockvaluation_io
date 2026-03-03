/**
 * Shared formatting utilities for DCF components
 */

/**
 * Formats a decimal value as a percentage
 * @param value - Decimal value (e.g., 0.12 for 12%)
 * @returns Formatted percentage string (e.g., "12.0%")
 */
export function formatPercentage(value: number | undefined | null): string {
  if (value === null || value === undefined) return '—';
  // Truncate to 2 decimal places without rounding to preserve API values
  return `${(Math.floor(value * 100) / 100).toFixed(2)}%`;
}

/**
 * Formats large currency values with currency codes
 * @param value - Currency value
 * @param currency - Currency code (e.g., 'USD', 'EUR')
 * @returns Formatted currency string (e.g., "1.2B USD")
 */
export function formatCompactCurrency(
  value: number | undefined | null, 
  currency?: string
): string {
  if (!value || value === 0) return '—';
  
  const absValue = Math.abs(value);
  let formattedValue = '';
  
  if (absValue >= 1e12) formattedValue = `${(Math.floor((value / 1e12) * 100) / 100).toFixed(2)}T`;
  else if (absValue >= 1e9) formattedValue = `${(Math.floor((value / 1e9) * 100) / 100).toFixed(2)}B`;
  else if (absValue >= 1e6) formattedValue = `${(Math.floor((value / 1e6) * 100) / 100).toFixed(2)}M`;
  else if (absValue >= 1e3) formattedValue = `${(Math.floor((value / 1e3) * 100) / 100).toFixed(2)}K`;
  else formattedValue = (Math.floor(value * 100) / 100).toFixed(2);
  
  const currencyCode = currency || 'USD';
  return `${formattedValue} ${currencyCode}`;
}

/**
 * Formats large numbers with appropriate suffixes (no currency symbol)
 * @param value - Numeric value
 * @returns Formatted number string (e.g., "1.2B")
 */
export function formatLargeNumber(value: number | undefined | null): string {
  if (!value || value === 0) return '—';
  
  const absValue = Math.abs(value);
  if (absValue >= 1e12) return `${(Math.floor((value / 1e12) * 100) / 100).toFixed(2)}T`;
  if (absValue >= 1e9) return `${(Math.floor((value / 1e9) * 100) / 100).toFixed(2)}B`;
  if (absValue >= 1e6) return `${(Math.floor((value / 1e6) * 100) / 100).toFixed(2)}M`;
  if (absValue >= 1e3) return `${(Math.floor((value / 1e3) * 100) / 100).toFixed(2)}K`;
  return (Math.floor(value * 100) / 100).toFixed(2);
}

/**
 * Formats ratio values
 * @param value - Ratio value
 * @returns Formatted ratio string (e.g., "1.5x")
 */
export function formatRatio(value: number | undefined | null): string {
  if (!value) return '—';
  return `${(Math.floor(value * 100) / 100).toFixed(2)}x`;
}

/**
 * Formats price values with 2 decimal places and currency code
 * @param price - Price value
 * @param currency - Currency code (e.g., 'USD', 'EUR')
 * @returns Formatted price string (e.g., "123.45 USD")
 */
export function formatPrice(
  price: number | undefined | null,
  currency?: string
): string {
  if (!price) return '—';
  
  const formattedPrice = (Math.floor(price * 100) / 100).toFixed(2);
  const currencyCode = currency || 'USD';
  return `${formattedPrice} ${currencyCode}`;
}

/**
 * Formats share counts with appropriate suffixes
 * @param value - Share count
 * @returns Formatted shares string (e.g., "1.2B shares")
 */
export function formatShares(value: number | undefined | null): string {
  if (!value) return '—';
  
  if (value >= 1e9) {
    return `${(value / 1e9).toFixed(2)}B shares`;
  } else if (value >= 1e6) {
    return `${(value / 1e6).toFixed(2)}M shares`;
  } else if (value >= 1e3) {
    return `${(value / 1e3).toFixed(2)}K shares`;
  }
  return `${value.toFixed(2)} shares`;
}

/**
 * Get currency symbol for a given currency code
 * @param currency - Currency code (e.g., 'USD', 'EUR')
 * @returns Currency symbol (e.g., '$', '€')
 */
export function getCurrencySymbol(currency: string): string {
  const currencySymbols: { [key: string]: string } = {
    'USD': '$',
    'EUR': '€',
    'GBP': '£',
    'JPY': '¥',
    'SEK': 'kr',
    'NOK': 'kr',
    'DKK': 'kr',
    'CHF': 'CHF',
    'CAD': 'C$',
    'AUD': 'A$',
    'CNY': '¥',
    'INR': '₹',
    'KRW': '₩',
    'SGD': 'S$',
    'HKD': 'HK$',
    'BRL': 'R$',
    'MXN': '$',
    'RUB': '₽'
  };
  
  return currencySymbols[currency] || currency || '$';
}

/**
 * Formats currency values for table display with optional asterisk for notes
 * @param value - Currency value
 * @param currency - Currency code
 * @param showNote - Whether to add asterisk for table note
 * @returns Formatted currency string with optional asterisk
 */
export function formatTableCurrency(
  value: number | undefined | null,
  currency?: string,
  showNote: boolean = false
): string {
  const formatted = formatCompactCurrency(value, currency);
  return showNote && formatted !== '—' ? `${formatted}*` : formatted;
}

/**
 * Gets the appropriate currency note text for table footers
 * @param currency - Currency code
 * @param stockCurrency - Stock currency code (if different)
 * @returns Currency note text
 */
export function getCurrencyNote(currency?: string, stockCurrency?: string): string {
  if (!currency) return '';
  
  if (stockCurrency && currency !== stockCurrency) {
    return `* All financial values in ${currency}. Stock price in ${stockCurrency}.`;
  }
  
  return `* All values in ${currency}.`;
}

/**
 * Always returns false since we now use currency codes consistently
 * @deprecated - We now always use currency codes, not symbols
 */
export function shouldUseCurrencySymbol(): boolean {
  return false;
}

/**
 * Creates a centralized currency formatting context
 * @param currency - Primary currency code from API
 * @param stockCurrency - Stock currency code from API
 * @returns Currency formatting context with helper methods
 */
export function createCurrencyContext(currency?: string, stockCurrency?: string) {
  return {
    currency,
    stockCurrency,
    
    // Format methods with currency context built-in
    formatCompact: (value: number | undefined | null) => 
      formatCompactCurrency(value, currency),
    
    formatPrice: (value: number | undefined | null, targetCurrency?: string) => 
      formatPrice(value, targetCurrency || stockCurrency),
    
    formatTable: (value: number | undefined | null, showNote: boolean = false) =>
      formatTableCurrency(value, currency, showNote),
    
    // Get currency note for tables
    getNote: () => getCurrencyNote(currency, stockCurrency),
    
    // Check if currencies match for percentage display
    currenciesMatch: () => currency === stockCurrency
  };
}