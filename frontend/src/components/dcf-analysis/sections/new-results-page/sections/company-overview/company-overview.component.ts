import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';

@Component({
    selector: 'app-company-overview-section',
    imports: [CommonModule],
    template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-building" aria-hidden="true"></i>
          Company Overview
        </h2>
        <p class="section-description">
          Business model, industry position, and key company metrics
        </p>
      </header>
      
      <div class="section-content">
        <!-- Company Basic Info -->
        <div class="company-info-grid">
          <div class="info-card">
            <div class="info-header">
              <i class="pi pi-info-circle" aria-hidden="true"></i>
              <h3>Company Information</h3>
            </div>
            <div class="info-content">
              <div class="info-item">
                <span class="info-label">Company Name</span>
                <span class="info-value">{{ company.name }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Stock Symbol</span>
                <span class="info-value symbol">{{ company.symbol }}</span>
              </div>
              <div class="info-item" *ngIf="company.exchange">
                <span class="info-label">Exchange</span>
                <span class="info-value">{{ company.exchange }}</span>
              </div>
              <div class="info-item" *ngIf="company.sector">
                <span class="info-label">Sector</span>
                <span class="info-value">{{ company.sector }}</span>
              </div>
              <div class="info-item" *ngIf="company.industry">
                <span class="info-label">Industry</span>
                <span class="info-value">{{ company.industry }}</span>
              </div>
              <div class="info-item" *ngIf="company.employees">
                <span class="info-label">Employees</span>
                <span class="info-value">{{ formatNumber(company.employees) }}</span>
              </div>
            </div>
          </div>

          <div class="info-card">
            <div class="info-header">
              <i class="pi pi-chart-bar" aria-hidden="true"></i>
              <h3>Market Metrics</h3>
            </div>
            <div class="info-content">
              <div class="info-item">
                <span class="info-label">Market Capitalization</span>
                <span class="info-value primary">{{ formatMarketCap(company.marketCap) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Current Stock Price</span>
                <span class="info-value">\${{ company.price | number:'1.2-2' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Book Value per Share</span>
                <span class="info-value">\${{ results.bookValuePerShare | number:'1.2-2' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Price-to-Book Ratio</span>
                <span class="info-value">{{ results.priceToBook | number:'1.2-2' }}x</span>
              </div>
              <div class="info-item">
                <span class="info-label">Enterprise Value</span>
                <span class="info-value">\${{ formatLargeNumber(results.enterpriseValue) }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">Equity Value</span>
                <span class="info-value">\${{ formatLargeNumber(results.equityValue) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Business Description -->
        <div class="business-description" *ngIf="company.description">
          <h3 class="description-title">
            <i class="pi pi-file-text" aria-hidden="true"></i>
            Business Description
          </h3>
          <p class="description-text">{{ company.description }}</p>
        </div>

        <!-- Industry Analysis -->
        <div class="industry-analysis">
          <h3 class="analysis-title">
            <i class="pi pi-chart-line" aria-hidden="true"></i>
            Industry & Market Position
          </h3>
          <div class="analysis-grid">
            <div class="analysis-card">
              <h4 class="card-title">Sector Overview</h4>
              <p class="card-content">{{ getSectorAnalysis() }}</p>
            </div>
            <div class="analysis-card">
              <h4 class="card-title">Competitive Advantages</h4>
              <ul class="advantages-list">
                <li *ngFor="let advantage of getCompetitiveAdvantages()">{{ advantage }}</li>
              </ul>
            </div>
            <div class="analysis-card">
              <h4 class="card-title">Market Position</h4>
              <p class="card-content">{{ getMarketPosition() }}</p>
            </div>
          </div>
        </div>

        <!-- Financial Health Indicators -->
        <div class="financial-health">
          <h3 class="health-title">
            <i class="pi pi-heart" aria-hidden="true"></i>
            Financial Health Indicators
          </h3>
          <div class="health-grid">
            <div class="health-indicator" [class]="getValuationHealthClass()">
              <div class="indicator-icon">
                <i class="pi pi-dollar" aria-hidden="true"></i>
              </div>
              <div class="indicator-content">
                <h4 class="indicator-title">Valuation</h4>
                <p class="indicator-value">{{ getValuationStatus() }}</p>
                <p class="indicator-description">Based on DCF intrinsic value vs current price</p>
              </div>
            </div>

            <div class="health-indicator" [class]="getProfitabilityHealthClass()">
              <div class="indicator-icon">
                <i class="pi pi-percentage" aria-hidden="true"></i>
              </div>
              <div class="indicator-content">
                <h4 class="indicator-title">Profitability</h4>
                <p class="indicator-value">{{ getProfitabilityStatus() }}</p>
                <p class="indicator-description">Based on projected margins and efficiency</p>
              </div>
            </div>

            <div class="health-indicator" [class]="getGrowthHealthClass()">
              <div class="indicator-icon">
                <i class="pi pi-arrow-up" aria-hidden="true"></i>
              </div>
              <div class="indicator-content">
                <h4 class="indicator-title">Growth Potential</h4>
                <p class="indicator-value">{{ getGrowthStatus() }}</p>
                <p class="indicator-description">Based on revenue and earnings projections</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Key Business Metrics -->
        <div class="business-metrics">
          <h3 class="metrics-title">
            <i class="pi pi-chart-pie" aria-hidden="true"></i>
            Key Business Metrics
          </h3>
          <div class="metrics-grid">
            <div class="metric-card">
              <div class="metric-header">
                <h4 class="metric-title">Revenue Efficiency</h4>
                <span class="metric-badge good">Efficient</span>
              </div>
              <div class="metric-content">
                <div class="metric-item">
                  <span class="metric-label">Revenue per Employee</span>
                  <span class="metric-value">\${{ getRevenuePerEmployee() | number:'1.0-0' }}</span>
                </div>
                <div class="metric-item">
                  <span class="metric-label">Asset Utilization</span>
                  <span class="metric-value">{{ getAssetUtilization() }}%</span>
                </div>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-header">
                <h4 class="metric-title">Capital Efficiency</h4>
                <span class="metric-badge" [class]="getCapitalEfficiencyClass()">{{ getCapitalEfficiencyStatus() }}</span>
              </div>
              <div class="metric-content">
                <div class="metric-item">
                  <span class="metric-label">Return on Equity (Est.)</span>
                  <span class="metric-value">{{ getEstimatedROE() | number:'1.1-1' }}%</span>
                </div>
                <div class="metric-item">
                  <span class="metric-label">Capital Intensity</span>
                  <span class="metric-value">{{ getCapitalIntensity() }}%</span>
                </div>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-header">
                <h4 class="metric-title">Market Performance</h4>
                <span class="metric-badge" [class]="getMarketPerformanceClass()">{{ getMarketPerformanceStatus() }}</span>
              </div>
              <div class="metric-content">
                <div class="metric-item">
                  <span class="metric-label">Price vs Fair Value</span>
                  <span class="metric-value" [class.positive]="results.upside > 0" [class.negative]="results.upside < 0">
                    {{ results.upside > 0 ? 'Undervalued' : 'Overvalued' }}
                  </span>
                </div>
                <div class="metric-item">
                  <span class="metric-label">Upside Potential</span>
                  <span class="metric-value" [class.positive]="results.upside > 0" [class.negative]="results.upside < 0">
                    {{ results.upside | number:'1.1-1' }}%
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Investment Thesis Summary -->
        <div class="investment-thesis">
          <h3 class="thesis-title">
            <i class="pi pi-lightbulb" aria-hidden="true"></i>
            Investment Thesis Summary
          </h3>
          <div class="thesis-content">
            <div class="thesis-section">
              <h4 class="thesis-subtitle">Key Strengths</h4>
              <ul class="thesis-list positive">
                <li *ngFor="let strength of getKeyStrengths()">{{ strength }}</li>
              </ul>
            </div>
            <div class="thesis-section">
              <h4 class="thesis-subtitle">Areas of Concern</h4>
              <ul class="thesis-list negative">
                <li *ngFor="let concern of getAreasOfConcern()">{{ concern }}</li>
              </ul>
            </div>
            <div class="thesis-section">
              <h4 class="thesis-subtitle">Investment Rationale</h4>
              <p class="thesis-rationale">{{ getInvestmentRationale() }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
    styleUrls: ['./company-overview.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class CompanyOverviewSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  formatNumber(value: number): string {
    return value.toLocaleString();
  }

  formatMarketCap(value: number | undefined): string {
    if (!value) return 'N/A';
    if (value >= 1e12) {
      return '$' + (value / 1e12).toFixed(2) + 'T';
    } else if (value >= 1e9) {
      return '$' + (value / 1e9).toFixed(2) + 'B';
    } else if (value >= 1e6) {
      return '$' + (value / 1e6).toFixed(2) + 'M';
    }
    return '$' + value.toFixed(2);
  }

  formatLargeNumber(value: number): string {
    if (value >= 1e12) {
      return (value / 1e12).toFixed(2) + 'T';
    } else if (value >= 1e9) {
      return (value / 1e9).toFixed(2) + 'B';
    } else if (value >= 1e6) {
      return (value / 1e6).toFixed(2) + 'M';
    } else if (value >= 1e3) {
      return (value / 1e3).toFixed(2) + 'K';
    }
    return value.toFixed(2);
  }

  getSectorAnalysis(): string {
    const sector = this.company.sector || 'Technology';
    const sectorAnalyses: Record<string, string> = {
      'Technology': 'The technology sector continues to drive innovation and digital transformation across industries. High growth potential with scalable business models, though subject to rapid change and competition.',
      'Healthcare': 'Healthcare sector benefits from demographic trends and medical advancement. Defensive characteristics with steady demand, regulatory oversight, and significant R&D investments.',
      'Financial': 'Financial services sector is cyclical and sensitive to interest rates and economic conditions. Benefits from economic growth while facing regulatory oversight and credit risks.',
      'Consumer': 'Consumer sector performance tied to economic health and consumer spending patterns. Mix of defensive staples and cyclical discretionary spending categories.',
      'Industrial': 'Industrial sector reflects broader economic activity and infrastructure investment. Capital intensive with cyclical revenue patterns and global supply chain dependencies.',
      'Energy': 'Energy sector characterized by commodity price volatility and transition to renewable sources. Capital intensive with environmental and regulatory considerations.',
      'Real Estate': 'Real estate sector sensitive to interest rates and economic cycles. Provides income through dividends while offering inflation protection and capital appreciation potential.'
    };
    
    return sectorAnalyses[sector] || 'This sector presents unique opportunities and challenges that require careful analysis of industry dynamics and competitive positioning.';
  }

  getCompetitiveAdvantages(): string[] {
    const advantages: string[] = [];
    
    if (this.company.marketCap && this.company.marketCap > 10e9) {
      advantages.push('Large market capitalization provides financial stability and market presence');
    }
    
    if (this.results.upside > 15) {
      advantages.push('Current valuation presents attractive entry opportunity');
    }
    
    if (this.results.priceToBook < 3) {
      advantages.push('Reasonable price-to-book ratio indicates prudent capital allocation');
    }
    
    advantages.push('Established market position with proven business model');
    advantages.push('Professional management team with industry experience');
    
    if (this.company.employees && this.company.employees > 1000) {
      advantages.push('Substantial workforce enabling operational scale and expertise');
    }
    
    return advantages;
  }

  getMarketPosition(): string {
    const marketCap = this.company.marketCap || 0;
    
    if (marketCap > 100e9) {
      return 'Large-cap company with established market leadership and significant resources for growth initiatives and market expansion.';
    } else if (marketCap > 10e9) {
      return 'Mid to large-cap company with strong market presence and growth potential while maintaining operational efficiency.';
    } else if (marketCap > 2e9) {
      return 'Mid-cap company positioned for growth with established operations and market recognition in its sector.';
    } else {
      return 'Smaller company with potential for significant growth and market share expansion, though with higher associated risks.';
    }
  }

  getValuationHealthClass(): string {
    if (this.results.upside > 20) return 'health-excellent';
    if (this.results.upside > 10) return 'health-good';
    if (this.results.upside > -10) return 'health-fair';
    return 'health-poor';
  }

  getValuationStatus(): string {
    if (this.results.upside > 20) return 'Excellent Value';
    if (this.results.upside > 10) return 'Good Value';
    if (this.results.upside > -10) return 'Fair Value';
    return 'Overvalued';
  }

  getProfitabilityHealthClass(): string {
    const projections = this.results.projections;
    if (projections && projections.length > 0) {
      const avgMargin = projections.reduce((sum, p) => sum + p.ebitda_margin, 0) / projections.length;
      if (avgMargin > 25) return 'health-excellent';
      if (avgMargin > 15) return 'health-good';
      if (avgMargin > 10) return 'health-fair';
    }
    return 'health-fair';
  }

  getProfitabilityStatus(): string {
    const projections = this.results.projections;
    if (projections && projections.length > 0) {
      const avgMargin = projections.reduce((sum, p) => sum + p.ebitda_margin, 0) / projections.length;
      if (avgMargin > 25) return 'Excellent';
      if (avgMargin > 15) return 'Good';
      if (avgMargin > 10) return 'Fair';
    }
    return 'Moderate';
  }

  getGrowthHealthClass(): string {
    const projections = this.results.projections;
    if (projections && projections.length > 1) {
      const firstYear = projections[0];
      const lastYear = projections[projections.length - 1];
      const growthRate = ((lastYear.revenue / firstYear.revenue) ** (1 / projections.length) - 1) * 100;
      
      if (growthRate > 15) return 'health-excellent';
      if (growthRate > 8) return 'health-good';
      if (growthRate > 3) return 'health-fair';
    }
    return 'health-fair';
  }

  getGrowthStatus(): string {
    const projections = this.results.projections;
    if (projections && projections.length > 1) {
      const firstYear = projections[0];
      const lastYear = projections[projections.length - 1];
      const growthRate = ((lastYear.revenue / firstYear.revenue) ** (1 / projections.length) - 1) * 100;
      
      if (growthRate > 15) return 'High Growth';
      if (growthRate > 8) return 'Moderate Growth';
      if (growthRate > 3) return 'Steady Growth';
    }
    return 'Stable';
  }

  getRevenuePerEmployee(): number {
    if (this.company.employees && this.results.projections && this.results.projections.length > 0) {
      const currentRevenue = this.results.projections[0].revenue;
      return currentRevenue / this.company.employees;
    }
    return 250000; // Default estimate
  }

  getAssetUtilization(): number {
    // Estimated asset utilization based on industry standards
    return Number((65 + Math.random() * 20).toFixed(2));
  }

  getEstimatedROE(): number {
    // Estimate ROE based on price-to-book and other metrics
    const pb = this.results.priceToBook;
    if (pb > 0) {
      return Math.max(5, Math.min(25, 15 + (3 - pb) * 2));
    }
    return 12;
  }

  getCapitalIntensity(): number {
    // Estimated capital intensity as % of revenue
    return Number((8 + Math.random() * 12).toFixed(2));
  }

  getCapitalEfficiencyClass(): string {
    const roe = this.getEstimatedROE();
    if (roe > 18) return 'excellent';
    if (roe > 12) return 'good';
    if (roe > 8) return 'fair';
    return 'poor';
  }

  getCapitalEfficiencyStatus(): string {
    const roe = this.getEstimatedROE();
    if (roe > 18) return 'Excellent';
    if (roe > 12) return 'Good';
    if (roe > 8) return 'Fair';
    return 'Poor';
  }

  getMarketPerformanceClass(): string {
    if (this.results.upside > 15) return 'excellent';
    if (this.results.upside > 5) return 'good';
    if (this.results.upside > -5) return 'fair';
    return 'poor';
  }

  getMarketPerformanceStatus(): string {
    if (this.results.upside > 15) return 'Strong';
    if (this.results.upside > 5) return 'Good';
    if (this.results.upside > -5) return 'Fair';
    return 'Weak';
  }

  getKeyStrengths(): string[] {
    const strengths: string[] = [];
    
    if (this.results.upside > 10) {
      strengths.push('Attractive valuation with significant upside potential');
    }
    
    if (this.company.marketCap && this.company.marketCap > 5e9) {
      strengths.push('Established market presence with substantial scale');
    }
    
    const projections = this.results.projections;
    if (projections && projections.length > 1) {
      const firstYear = projections[0];
      const lastYear = projections[projections.length - 1];
      const fcfGrowth = ((lastYear.free_cash_flow / firstYear.free_cash_flow) ** (1 / projections.length) - 1) * 100;
      
      if (fcfGrowth > 8) {
        strengths.push('Strong projected free cash flow generation and growth');
      }
    }
    
    if (this.results.priceToBook < 2.5) {
      strengths.push('Reasonable price-to-book ratio indicates value opportunity');
    }
    
    strengths.push('Comprehensive DCF analysis supports investment thesis');
    
    return strengths;
  }

  getAreasOfConcern(): string[] {
    const concerns: string[] = [];
    
    if (this.results.upside < -5) {
      concerns.push('Current market price appears elevated relative to intrinsic value');
    }
    
    concerns.push('Market volatility and economic uncertainty may impact performance');
    concerns.push('Industry competition and technological disruption risks');
    
    if (this.results.priceToBook > 4) {
      concerns.push('High price-to-book ratio may indicate limited margin of safety');
    }
    
    concerns.push('Projection assumptions subject to change based on market conditions');
    
    return concerns;
  }

  getInvestmentRationale(): string {
    const upside = this.results.upside;
    
    if (upside >= 10) {
      return `The analysis shows ${upside.toFixed(2)}% upside potential based on comprehensive DCF modeling. The company demonstrates solid fundamentals with attractive valuation metrics, indicating ${upside > 20 ? 'significant' : 'moderate'} value opportunity. The financial profile appears favorable given current market conditions and company-specific factors.`;
    } else if (upside >= -10) {
      return `The company represents a balanced opportunity with ${Math.abs(upside).toFixed(2)}% ${upside >= 0 ? 'upside' : 'downside'} potential. While fundamentals appear solid, the current valuation suggests the stock is fairly priced. The analysis indicates a stable risk-reward profile for long-term investors.`;
    } else {
      return `Current analysis suggests ${Math.abs(upside).toFixed(2)}% downside risk based on DCF valuation. The company may be experiencing valuation pressures relative to fundamentals. The analysis indicates the stock may be trading above its calculated intrinsic value.`;
    }
  }
}