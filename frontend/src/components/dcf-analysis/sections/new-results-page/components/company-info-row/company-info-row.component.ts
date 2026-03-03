import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { LearnTermsPanelService } from '../../../../../../core/services/ui/learn-terms-panel.service';

@Component({
    selector: 'app-company-info-row',
    imports: [CommonModule],
    template: `
    <div class="company-info-wrapper">
      <div class="company-info-row">
          <div class="company-identity">
            <div class="company-logo-section">
              <img 
                [src]="getCompanyLogoUrl()" 
                [alt]="company.name + ' logo'" 
                class="company-logo"
                (error)="onLogoError($event)"
                *ngIf="!logoError; else logoPlaceholder"
              >
              <ng-template #logoPlaceholder>
                <div class="company-logo-placeholder">
                  {{ getCompanyInitials() }}
                </div>
              </ng-template>
            </div>
            <div class="company-text-info">
              <h1 class="company-name">{{ company.name }}</h1>
              <div class="company-details">
                <span class="company-symbol">{{ company.symbol }}</span>
                <span class="separator" *ngIf="getCompanyLocation() || company.industry">•</span>
                <span class="company-location" *ngIf="getCompanyLocation()">{{ getCompanyLocation() }}</span>
                <span class="separator" *ngIf="getCompanyLocation() && company.industry">•</span>
                <span class="company-industry" *ngIf="company.industry">{{ company.industry }}</span>
              </div>
            </div>
          </div>

          <div class="primary-metrics">
            <div class="metric-card">
              <span class="metric-label">Current Price</span>
              <div class="metric-value-wrapper">
                <span class="metric-value">{{ company.price | number:'1.2-2' }}</span>
                <span class="currency-label">{{ getStockCurrency() }}</span>
              </div>
              <span class="metric-sublabel">per share</span>
            </div>
            
            <div class="metric-card">
              <span class="metric-label">Fair Value</span>
              <div class="metric-value-wrapper">
                <span class="metric-value primary">{{ results.intrinsicValue | number:'1.2-2' }}</span>
                <span class="currency-label">{{ getDCFCurrency() }}</span>
              </div>
            </div>
            
            <!-- Only show over/undervaluation when currencies match -->
            <div class="metric-card valuation-status" *ngIf="canShowValuationStatus()">
              <span class="metric-label" [class.undervalued]="isUndervalued()" [class.overvalued]="!isUndervalued()">
                {{ isUndervalued() ? 'Undervalued' : 'Overvalued' }}
              </span>
              <div class="metric-value-wrapper">
                <span class="metric-value">{{ Math.abs(getPriceAsPercentageOfValue()) | number:'1.1-1' }}%</span>
              </div>
            </div>
            
            <!-- Show currency mismatch notice when currencies don't match -->
            <div class="metric-card currency-mismatch" *ngIf="!canShowValuationStatus()">
              <span class="metric-label">Currency Notice</span>
              <div class="metric-value-wrapper">
                <span class="mismatch-text">Different currencies</span>
              </div>
              <span class="metric-sublabel">{{ getStockCurrency() }} vs {{ getDCFCurrency() }}</span>
            </div>
          </div>
          <!-- <div class="row-actions">
            <button type="button" class="learn-terms-btn" (click)="openLearnTerms()">
              <span class="emoji">📘</span>
              <span>Learn Valuation Terms</span>
            </button>
          </div> -->
      </div>
    </div>
  `,
    styleUrls: ['./company-info-row.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class CompanyInfoRowComponent {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  logoError = false;
  Math = Math; // Make Math available in template
  constructor(private learnTerms: LearnTermsPanelService) {}

  openLearnTerms(): void { this.learnTerms.open(); }

  // Logo handling methods (from company-card component)
  getCompanyLogoUrl(): string {
    const baseSymbol = this.company.symbol.toUpperCase();
    return `https://financialmodelingprep.com/image-stock/${baseSymbol}.png`;
  }

  onLogoError(event: any): void {
    this.logoError = true;
  }

  getCompanyInitials(): string {
    return this.company.name
      .split(' ')
      .map(word => word[0])
      .join('')
      .toUpperCase()
      .substring(0, 3);
  }

  // CORRECTED: Use API's priceAsPercentageOfValue directly
  getPriceAsPercentageOfValue(): number {
    // This should come from API response: results.priceAsPercentageOfValue
    // If not available, calculate: (currentPrice / fairValue - 1) * 100
    if ('priceAsPercentageOfValue' in this.results) {
      return (this.results as any).priceAsPercentageOfValue;
    }
    
    // Fallback calculation (API formula)
    if (this.company.price && this.results.intrinsicValue) {
      return ((this.company.price / this.results.intrinsicValue) - 1) * 100;
    }
    return 0;
  }

  isUndervalued(): boolean {
    return this.getPriceAsPercentageOfValue() < 0;
  }


  getUpsideDisplay(): string {
    const percentage = this.getPriceAsPercentageOfValue();
    const absPercentage = Math.abs(percentage);
    
    if (percentage < 0) {
      // Undervalued = positive upside potential
      return `+${absPercentage.toFixed(2)}%`;
    } else {
      // Overvalued = negative upside (downside risk)
      return `-${absPercentage.toFixed(2)}%`;
    }
  }

  // CORRECTED: Market cap calculation using current data
  getCalculatedMarketCap(): number {
    if (this.company.price && this.results.numberOfShares) {
      return this.company.price * this.results.numberOfShares;
    }
    return this.company.marketCap || 0;
  }

  formatMarketCap(value: number | undefined): string {
    if (!value) return 'N/A';
    if (value >= 1e12) {
      return (value / 1e12).toFixed(2) + 'T';
    } else if (value >= 1e9) {
      return (value / 1e9).toFixed(2) + 'B';
    } else if (value >= 1e6) {
      return (value / 1e6).toFixed(2) + 'M';
    }
    return value.toString();
  }

  formatRatio(value: number | undefined): string {
    if (!value || value <= 0) return 'N/A';
    return value.toFixed(2) + 'x';
  }

  getCompanyLocation(): string {
    // Map exchange short names to countries
    if (this.company.exchangeShortName) {
      const exchangeMap: {[key: string]: string} = {
        'NASDAQ': 'United States',
        'NYSE': 'United States',
        'AMEX': 'United States',
        'OTC': 'United States',
        'LSE': 'United Kingdom',
        'TSE': 'Japan',
        'HKEX': 'Hong Kong',
        'SSE': 'China',
        'SZSE': 'China',
        'BSE': 'India',
        'NSE': 'India',
        'ASX': 'Australia',
        'TSX': 'Canada',
        'TSXV': 'Canada',
        'XETRA': 'Germany',
        'EURONEXT': 'Europe',
        'SIX': 'Switzerland',
        'MOEX': 'Russia',
        'JSE': 'South Africa',
        'KRX': 'South Korea',
        'SGX': 'Singapore'
      };
      
      const country = exchangeMap[this.company.exchangeShortName];
      if (country) return country;
    }
    
    // Fallback to parsing exchange name
    if (this.company.exchange) {
      if (this.company.exchange.includes('NASDAQ') || this.company.exchange.includes('New York')) {
        return 'United States';
      }
      if (this.company.exchange.includes('London')) return 'United Kingdom';
      if (this.company.exchange.includes('Tokyo')) return 'Japan';
      if (this.company.exchange.includes('Hong Kong')) return 'Hong Kong';
      if (this.company.exchange.includes('Toronto')) return 'Canada';
    }
    
    return '';
  }

  // Currency handling methods
  getStockCurrency(): string {
    return this.results.stockCurrency || this.results.currency || 'USD';
  }

  getDCFCurrency(): string {
    return this.results.currency || 'USD';
  }

  canShowValuationStatus(): boolean {
    // Only show over/undervaluation when stock currency matches DCF currency
    return this.getStockCurrency() === this.getDCFCurrency();
  }
}