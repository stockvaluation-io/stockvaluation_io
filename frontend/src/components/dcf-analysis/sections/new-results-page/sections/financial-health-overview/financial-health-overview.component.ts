import { Component, Input, ChangeDetectionStrategy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { FinancialDataDTO } from '../../../../models';
import { createCurrencyContext, formatLargeNumber, formatRatio, formatPercentage, formatCompactCurrency } from '../../../../utils/formatting.utils';

interface FinancialOverviewRow {
  label: string;
  currentValue: string;
  previousValue: string;
  isPercentage?: boolean;
}

@Component({
    selector: 'app-financial-health-overview-section',
    imports: [CommonModule],
    template: `
    <div class="financial-health-section">
      <h3 class="section-title">
        <i class="pi pi-chart-pie section-icon"></i>
        Financial Health Overview
      </h3>
      
      <!-- Key Metrics Grid -->
      <div class="compact-metrics-grid">
        <div class="metric-item">
          <div class="metric-label">Enterprise Value</div>
          <div class="metric-value primary" [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatCompact(results.enterpriseValue || 0) || formatCompactCurrency(results.enterpriseValue || 0))"></div>
        </div>
        
        <div class="metric-item">
          <div class="metric-label">Equity Value</div>
          <div class="metric-value" [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatCompact(results.equityValue || 0) || formatCompactCurrency(results.equityValue || 0))"></div>
        </div>
        
        <div class="metric-item">
          <div class="metric-label">FCF Yield</div>
          <div class="metric-value">{{ formatPercentage(getFcfYield()) }}</div>
        </div>
        
        <div class="metric-item">
          <div class="metric-label">Average ROIC</div>
          <div class="metric-value primary">{{ formatPercentage(getAverageROIC()) }}</div>
        </div>
        
        <div class="metric-item">
          <div class="metric-label">Cost of Capital</div>
          <div class="metric-value">{{ formatPercentage(getAverageCostOfCapital()) }}</div>
        </div>
        
        <div class="metric-item">
          <div class="metric-label">P/E Ratio</div>
          <div class="metric-value">{{ formatRatio(getPeRatio()) }}</div>
        </div>
      </div>
      
      <!-- Financial Overview Table -->
      <div class="financial-overview-container">
        <div class="overview-table-wrapper">
          <table class="financial-overview-table">
            <thead>
              <tr>
                <th class="metric-header">Financial Metric</th>
                <th class="data-column">Most Recent 12 months ({{ results.currency || 'USD' }})</th>
                <th class="data-column">Previous Year ({{ results.currency || 'USD' }})</th>
              </tr>
            </thead>
            <tbody>
              <!-- Financial Data Rows -->
              <tr class="data-row" *ngFor="let row of financialRows">
                <td class="metric-label">{{ row.label }}</td>
                <td class="value-cell current" [innerHTML]="formatValueWithCurrencyStyle(row.currentValue)"></td>
                <td class="value-cell previous" [innerHTML]="formatValueWithCurrencyStyle(row.previousValue)"></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./financial-health-overview.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class FinancialHealthOverviewSection implements OnInit {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
  @Input() financialData?: FinancialDataDTO; // Raw financial data from API
  
  // Currency formatting context
  currencyCtx: ReturnType<typeof createCurrencyContext> | null = null;
  financialRows: FinancialOverviewRow[] = [];

  ngOnInit(): void {
    // Initialize currency context
    this.currencyCtx = createCurrencyContext(this.results.currency, this.results.stockCurrency);
    this.buildFinancialRows();
  }

  private buildFinancialRows(): void {
    // Use financial data if available, otherwise fall back to calculated values
    if (this.financialData) {
      this.buildFromApiData();
    } else {
      this.buildFromResultsData();
    }
  }

  private buildFromApiData(): void {
    const fd = this.financialData!;
    const currency = this.results.currency || 'USD';
    
    this.financialRows = [
      {
        label: 'Revenues',
        currentValue: this.formatCurrencyWithCode(fd.revenueTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.revenueLTM, currency)
      },
      {
        label: 'Operating Income or EBIT',
        currentValue: this.formatCurrencyWithCode(fd.operatingIncomeTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.operatingIncomeLTM, currency)
      },
      {
        label: 'Interest Expense',
        currentValue: this.formatCurrencyWithCode(fd.interestExpenseTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.interestExpenseLTM, currency)
      },
      {
        label: 'Book Value Of Equity',
        currentValue: this.formatCurrencyWithCode(fd.bookValueEqualityTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.bookValueEqualityLTM, currency)
      },
      {
        label: 'Book Value Of Debt',
        currentValue: this.formatCurrencyWithCode(fd.bookValueDebtTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.bookValueDebtLTM, currency)
      },
      {
        label: 'Cash and Marketable Securities',
        currentValue: this.formatCurrencyWithCode(fd.cashAndMarkablTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.cashAndMarkablLTM, currency)
      },
      {
        label: 'Number of Shares Outstanding',
        currentValue: this.formatShares(fd.noOfShareOutstanding),
        previousValue: '--'
      },
      {
        label: 'Minority interests',
        currentValue: this.formatCurrencyWithCode(fd.minorityInterestTTM, currency),
        previousValue: this.formatCurrencyWithCode(fd.minorityInterestLTM, currency)
      },
      {
        label: 'Current Stock Price',
        currentValue: this.formatPriceWithCode(fd.stockPrice, this.results.stockCurrency || currency),
        previousValue: '--'
      },
      {
        label: 'Effective Tax Rate',
        currentValue: this.formatPercentageValue(fd.effectiveTaxRate),
        previousValue: '--'
      },
      {
        label: 'Marginal Tax Rate',
        currentValue: this.formatPercentageValue(fd.marginalTaxRate),
        previousValue: '--'
      }
    ];
  }

  private buildFromResultsData(): void {
    // Fallback using projections data when raw financial data is not available
    const currentYearProjection = this.results.projections?.[0] || this.results.projections?.[1];
    const currency = this.results.currency || 'USD';
    
    this.financialRows = [
      {
        label: 'Revenues',
        currentValue: this.formatCurrencyWithCode(currentYearProjection?.revenue || 0, currency),
        previousValue: '--'
      },
      {
        label: 'Operating Income or EBIT',
        currentValue: this.formatCurrencyWithCode(currentYearProjection?.operating_income || currentYearProjection?.ebitda || 0, currency),
        previousValue: '--'
      },
      {
        label: 'Interest Expense',
        currentValue: this.formatCurrencyWithCode(0, currency),
        previousValue: '--'
      },
      {
        label: 'Book Value Of Equity',
        currentValue: this.formatCurrencyWithCode(this.results.equityValue || 0, currency),
        previousValue: '--'
      },
      {
        label: 'Book Value Of Debt',
        currentValue: this.formatCurrencyWithCode(this.results.debt || 0, currency),
        previousValue: '--'
      },
      {
        label: 'Cash and Marketable Securities',
        currentValue: this.formatCurrencyWithCode(this.results.cash || 0, currency),
        previousValue: '--'
      },
      {
        label: 'Number of Shares Outstanding',
        currentValue: this.formatShares(this.results.numberOfShares || 0),
        previousValue: '--'
      },
      {
        label: 'Current Stock Price',
        currentValue: this.formatPriceWithCode(this.results.currentPrice || 0, this.results.stockCurrency || currency),
        previousValue: '--'
      },
      {
        label: 'Effective Tax Rate',
        currentValue: '24.09%', // Default assumption
        previousValue: '--'
      },
      {
        label: 'Marginal Tax Rate',
        currentValue: '25.00%', // Default assumption
        previousValue: '--'
      }
    ];
  }

  private formatCurrencyWithCode(value: number, currency: string): string {
    if (value === null || value === undefined) return '--';
    
    const absValue = Math.abs(value);
    let formattedValue: string;
    
    if (absValue >= 1e12) {
      formattedValue = `${(value / 1e12).toFixed(2).replace(/\.00$/, '')}T`; // Trillions
    } else if (absValue >= 1e9) {
      formattedValue = `${(value / 1e9).toFixed(2).replace(/\.00$/, '')}B`; // Billions
    } else if (absValue >= 1e6) {
      formattedValue = `${(value / 1e6).toFixed(2).replace(/\.00$/, '')}M`; // Millions
    } else if (absValue >= 1e3) {
      formattedValue = `${(value / 1e3).toFixed(2).replace(/\.00$/, '')}k`; // Thousands
    } else {
      formattedValue = `${value.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
    }
    
    return `${formattedValue} ${currency}`;
  }

  private formatPriceWithCode(value: number, currency: string): string {
    if (!value || value === 0) return `0 ${currency}`;
    return `${value.toFixed(2)} ${currency}`;
  }

  private formatShares(value: number): string {
    if (!value || value === 0) return '0';
    
    const absValue = Math.abs(value);
    if (absValue >= 1e9) {
      return `${(value / 1e9).toFixed(2).replace(/\.00$/, '')}B`;
    } else if (absValue >= 1e6) {
      return `${(value / 1e6).toFixed(2).replace(/\.00$/, '')}M`;
    } else if (absValue >= 1e3) {
      return `${(value / 1e3).toFixed(2).replace(/\.00$/, '')}k`;
    }
    return value.toLocaleString();
  }

  private formatPercentageValue(value: number): string {
    if (!value || value === 0) return '0%';
    // If value is greater than 1, assume it's already a percentage (like 25.00)
    // If value is less than or equal to 1, assume it's a decimal (like 0.25)
    const percentage = value > 1 ? value : (value * 100);
    return `${percentage.toFixed(2)}%`;
  }

  // Method to style currency codes in the UI
  formatValueWithCurrencyStyle(value: string): string {
    if (value === '--' || !value) return value;
    
    // Split the value to separate number and currency code
    const parts = value.split(' ');
    if (parts.length >= 2) {
      const numberPart = parts.slice(0, -1).join(' '); // Everything except last part
      const currencyPart = parts[parts.length - 1]; // Last part (currency code)
      
      // Return with styled currency code
      return `${numberPart} <span class="currency-code">${currencyPart}</span>`;
    }
    
    return value;
  }

  // Calculated metrics methods
  getFcfYield(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    const totalFcf = this.results.projections.reduce((sum, p) => sum + p.free_cash_flow, 0);
    const marketCap = this.results.currentPrice * (this.results.numberOfShares || 1);
    
    return marketCap > 0 ? (totalFcf / marketCap) * 100 : 0;
  }

  getPeRatio(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    // Estimate earnings from EBIT after tax
    const currentYearEbit = this.results.projections[0]?.ebit_after_tax || 0;
    const earningsPerShare = currentYearEbit / (this.results.numberOfShares || 1);
    
    return earningsPerShare > 0 ? this.results.currentPrice / earningsPerShare : 0;
  }

  getAverageROIC(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    const validValues = this.results.projections
      .map(p => p.roic || 0)
      .filter(val => val > 0);
    
    if (validValues.length === 0) return 0;
    
    return (validValues.reduce((sum, val) => sum + val, 0) / validValues.length);
  }

  getAverageCostOfCapital(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    const validValues = this.results.projections
      .map(p => p.cost_of_capital || 0)
      .filter(val => val > 0);
    
    if (validValues.length === 0) return 0;
    
    return (validValues.reduce((sum, val) => sum + val, 0) / validValues.length);
  }

  getAverageSalesToCapital(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    const validValues = this.results.projections
      .map(p => p.sales_to_capital_ratio || 0)
      .filter(val => val > 0);
    
    if (validValues.length === 0) return 0;
    
    return validValues.reduce((sum, val) => sum + val, 0) / validValues.length;
  }

  // Use utility functions for formatting
  formatLargeNumber = formatLargeNumber;
  formatRatio = formatRatio;
  formatPercentage = formatPercentage;
  formatCompactCurrency = formatCompactCurrency;

  // Currency styling method
  formatCurrencyWithStyle(value: string): string {
    if (value === '—' || !value) return value;
    
    // Split the value to separate number and currency code
    const parts = value.split(' ');
    if (parts.length >= 2) {
      const numberPart = parts.slice(0, -1).join(' '); // Everything except last part
      const currencyPart = parts[parts.length - 1]; // Last part (currency code)
      
      // Return with styled currency code
      return `${numberPart} <span class="currency-code">${currencyPart}</span>`;
    }
    
    return value;
  }
}