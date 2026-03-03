import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';

@Component({
  selector: 'app-company-info-sidebar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="company-info-sidebar">
      <div class="company-card">
        
        <!-- Company Header -->
        <div class="company-header">
          <div class="company-main">
            <h2 class="company-name">{{ company.name }}</h2>
            <span class="company-symbol">{{ company.symbol }}</span>
          </div>
          <div class="current-price">
            <span class="price-label">Current Price</span>
            <span class="price-value">\${{ formatPrice(results.currentPrice) }}</span>
            <span class="price-change" [class]="getPriceChangeClass()">
              {{ getPriceChangeText() }}
            </span>
          </div>
        </div>

        <!-- Quick Metrics -->
        <div class="quick-metrics">
          <div class="metric-row">
            <span class="metric-label">Intrinsic Value</span>
            <span class="metric-value primary">\${{ formatPrice(results.intrinsicValue) }}</span>
          </div>
          
          <div class="metric-row">
            <span class="metric-label">{{ results.upside >= 0 ? 'Upside' : 'Downside' }}</span>
            <span class="metric-value" [class]="getUpsideClass()">
              {{ results.upside >= 0 ? '+' : '' }}{{ results.upside.toFixed(2) }}%
            </span>
          </div>
          
          <div class="metric-row">
            <span class="metric-label">Valuation</span>
            <span class="metric-value valuation" [class]="getValuationClass()">
              {{ getValuationCategory() }}
            </span>
          </div>
        </div>

        <!-- Investment Gauge -->
        <div class="investment-gauge">
          <div class="gauge-header">
            <span class="gauge-label">Investment Score</span>
            <span class="gauge-value">{{ getInvestmentScore() }}/10</span>
          </div>
          <div class="gauge-bar">
            <div class="gauge-fill" 
                 [style.width.%]="getInvestmentScore() * 10"
                 [class]="getGaugeClass()">
            </div>
          </div>
        </div>

        <!-- Key Stats -->
        <div class="key-stats">
          <div class="stat-item">
            <span class="stat-label">P/B Ratio</span>
            <span class="stat-value">{{ formatRatio(results.priceToBook) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">Enterprise Value</span>
            <span class="stat-value">\${{ formatLargeNumber(results.enterpriseValue) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">Analysis Date</span>
            <span class="stat-value">{{ formatDate(results.analysisDate) }}</span>
          </div>
        </div>

      </div>
    </div>
  `,
  styleUrls: ['./company-info-sidebar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CompanyInfoSidebarComponent {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  formatPrice(price: number): string {
    return price.toFixed(2);
  }

  formatLargeNumber(value: number): string {
    if (value >= 1e12) return (value / 1e12).toFixed(2) + 'T';
    if (value >= 1e9) return (value / 1e9).toFixed(2) + 'B';
    if (value >= 1e6) return (value / 1e6).toFixed(2) + 'M';
    if (value >= 1e3) return (value / 1e3).toFixed(2) + 'K';
    return value.toFixed(2);
  }

  formatRatio(value: number): string {
    return value > 0 ? value.toFixed(2) + 'x' : 'N/A';
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric'
    });
  }

  getPriceChangeText(): string {
    const change = this.results.upside;
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)}%`;
  }

  getPriceChangeClass(): string {
    return this.results.upside >= 0 ? 'positive' : 'negative';
  }

  getUpsideClass(): string {
    return this.results.upside >= 0 ? 'positive' : 'negative';
  }

  getValuationCategory(): string {
    // Use server-provided priceAsPercentageOfValue if available
    const priceAsPercentageOfValue = this.results.priceAsPercentageOfValue;
    if (priceAsPercentageOfValue != null) {
      if (priceAsPercentageOfValue < -20) return 'Significantly Undervalued';
      if (priceAsPercentageOfValue < -10) return 'Undervalued';
      if (priceAsPercentageOfValue < 10) return 'Fair Value';
      if (priceAsPercentageOfValue < 20) return 'Overvalued';
      return 'Significantly Overvalued';
    }
    
    // Fallback to upside calculation
    const upside = this.results.upside;
    if (upside >= 20) return 'Significantly Undervalued';
    if (upside >= 10) return 'Undervalued';
    if (upside >= -10) return 'Fair Value';
    if (upside >= -20) return 'Overvalued';
    return 'Significantly Overvalued';
  }

  getValuationClass(): string {
    // Use server-provided priceAsPercentageOfValue if available
    const priceAsPercentageOfValue = this.results.priceAsPercentageOfValue;
    if (priceAsPercentageOfValue != null) {
      if (priceAsPercentageOfValue < -15) return 'significantly-undervalued';
      if (priceAsPercentageOfValue < -5) return 'undervalued';
      if (priceAsPercentageOfValue < 5) return 'fair-value';
      if (priceAsPercentageOfValue < 15) return 'overvalued';
      return 'significantly-overvalued';
    }
    
    // Fallback to upside calculation
    const upside = this.results.upside;
    if (upside >= 15) return 'significantly-undervalued';
    if (upside >= 5) return 'undervalued';
    if (upside >= -5) return 'fair-value';
    if (upside >= -15) return 'overvalued';
    return 'significantly-overvalued';
  }

  getInvestmentScore(): number {
    const upside = this.results.upside;
    // Convert upside percentage to a 1-10 scale
    const score = Math.max(1, Math.min(10, 5 + (upside / 10)));
    return Number(score.toFixed(2));
  }

  getGaugeClass(): string {
    const score = this.getInvestmentScore();
    if (score >= 8) return 'excellent';
    if (score >= 6) return 'good';
    if (score >= 4) return 'fair';
    return 'poor';
  }
}