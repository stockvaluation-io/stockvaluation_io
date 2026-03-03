import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { EquityWaterfallComponent } from '../../shared/components/equity-waterfall/equity-waterfall.component';

@Component({
    selector: 'app-valuation-breakdown-section',
    imports: [CommonModule, EquityWaterfallComponent],
    template: `
    <div class="valuation-breakdown-section" *ngIf="hasWaterfallData">
      <h3 class="section-title">
        <i class="pi pi-chart-line section-icon"></i>
        Equity Value Breakdown
      </h3>
      
      <!-- Equity Waterfall Table -->
      <app-equity-waterfall
        [pvTerminalValue]="results.pvTerminalValue || 0"
        [pvProjectedCashFlows]="results.fcfValue || 0"
        [valueOfOperatingAssets]="results.valueOfOperatingAssets || 0"
        [debt]="results.debt || 0"
        [minorityInterests]="results.minorityInterests || 0"
        [cash]="results.cash || 0"
        [valueOfEquity]="results.equityValue || 0"
        [numberOfShares]="results.numberOfShares || 0"
        [estimatedValuePerShare]="results.dcfValuePerShare || 0"
        [currentPrice]="results.currentPrice || 0"
        [currency]="results.currency"
        [stockCurrency]="results.stockCurrency">
      </app-equity-waterfall>
    </div>
  `,
    styleUrls: ['./valuation-breakdown.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValuationBreakdownSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  get hasWaterfallData(): boolean {
    return !!(
      this.results.valueOfOperatingAssets ||
      this.results.debt ||
      this.results.cash ||
      this.results.equityValue ||
      this.results.numberOfShares
    );
  }

  canShowValuationStatus(): boolean {
    // Only show over/undervaluation when stock currency matches DCF currency
    return this.getStockCurrency() === this.getDCFCurrency();
  }

  getStockCurrency(): string {
    return this.results.stockCurrency || this.results.currency || 'USD';
  }

  getDCFCurrency(): string {
    return this.results.currency || 'USD';
  }

  getCurrencySymbol(): string {
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
    
    return currencySymbols[this.getDCFCurrency()] || this.getDCFCurrency() || '$';
  }

  getStockCurrencySymbol(): string {
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
    
    return currencySymbols[this.getStockCurrency()] || this.getStockCurrency() || '$';
  }

  formatLargeNumber(value: number): string {
    if (value >= 1e12) return (value / 1e12).toFixed(1) + 'T';
    if (value >= 1e9) return (value / 1e9).toFixed(1) + 'B';
    if (value >= 1e6) return (value / 1e6).toFixed(1) + 'M';
    if (value >= 1e3) return (value / 1e3).toFixed(1) + 'K';
    return value.toFixed(0);
  }

  formatPrice(price: number): string {
    return price.toFixed(2);
  }

  formatPercentage(value: number): string {
    return value.toFixed(1) + '%';
  }

  formatShares(value: number): string {
    if (value >= 1e9) {
      return `${(value / 1e9).toFixed(2)}B`;
    } else if (value >= 1e6) {
      return `${(value / 1e6).toFixed(2)}M`;
    } else if (value >= 1e3) {
      return `${(value / 1e3).toFixed(2)}K`;
    }
    return value.toFixed(0);
  }

  getUpsideClass(): string {
    return (this.results.upside || 0) >= 0 ? 'positive' : 'negative';
  }
}