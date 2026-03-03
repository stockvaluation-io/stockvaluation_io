import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';

@Component({
    selector: 'app-performance-comparison-section',
    imports: [CommonModule],
    template: `
    <div class="performance-comparison-section" *ngIf="hasIndustryData">
      <h3 class="section-title">
        <i class="pi pi-chart-bar section-icon"></i>
        Performance vs Industry
      </h3>
      
      <div class="compact-comparison-container">
        <!-- Key Metrics Row -->
        <div class="comparison-row">
          <div class="metric-comparison">
            <div class="metric-header">
              <div class="metric-name">
                <i class="pi pi-chart-line"></i>
                Revenue Growth
              </div>
              <div class="difference-indicator" [class]="getDifferenceClass('growth')">
                {{ getPercentDifference('growth') }}
              </div>
            </div>
            <div class="values-row">
              <div class="company-value">
                <span class="value">{{ formatPercentage((results.revenueGrowthCompany || 0)) }}</span>
                <span class="label">Company</span>
              </div>
              <div class="vs-separator">vs</div>
              <div class="industry-value">
                <span class="value">{{ formatPercentage((results.revenueGrowthIndustry || 0)) }}</span>
                <span class="label">Industry</span>
              </div>
            </div>
            <div class="performance-indicator" [class]="getPerformanceClass('growth')">{{ getPerformanceText('growth') }}</div>
          </div>
          
          <div class="metric-comparison">
            <div class="metric-header">
              <div class="metric-name">
                <i class="pi pi-chart-pie"></i>
                Operating Margin
              </div>
              <div class="difference-indicator" [class]="getDifferenceClass('margin')">
                {{ getPercentDifference('margin') }}
              </div>
            </div>
            <div class="values-row">
              <div class="company-value">
                <span class="value">{{ formatPercentage((results.operatingMarginCompany || 0)) }}</span>
                <span class="label">Company</span>
              </div>
              <div class="vs-separator">vs</div>
              <div class="industry-value">
                <span class="value">{{ formatPercentage((results.operatingMarginIndustry || 0)) }}</span>
                <span class="label">Industry</span>
              </div>
            </div>
            <div class="performance-indicator" [class]="getPerformanceClass('margin')">{{ getPerformanceText('margin') }}</div>
          </div>
        </div>
        
        <!-- Summary Badge -->
        <div class="overall-performance">
          <span class="summary-text">{{ getIndustryContextSummary() }}</span>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./performance-comparison.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class PerformanceComparisonSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  get hasIndustryData(): boolean {
    return this.results.revenueGrowthCompany !== undefined &&
      this.results.revenueGrowthIndustry !== undefined &&
      this.results.operatingMarginCompany !== undefined &&
      this.results.operatingMarginIndustry !== undefined;
  }

  getAverageROIC(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    const validValues = this.results.projections
      .map(p => p.roic || 0)
      .filter(val => val > 0);
    
    if (validValues.length === 0) return 0;
    
    return (validValues.reduce((sum, val) => sum + val, 0) / validValues.length) * 100;
  }

  getAverageSalesToCapital(): number {
    if (!this.results.projections || this.results.projections.length === 0) return 0;
    
    const validValues = this.results.projections
      .map(p => p.sales_to_capital_ratio || 0)
      .filter(val => val > 0);
    
    if (validValues.length === 0) return 0;
    
    return validValues.reduce((sum, val) => sum + val, 0) / validValues.length;
  }

  getEstimatedIndustryROIC(): number {
    // Use reasonable industry averages based on company performance
    return this.getAverageROIC() * 0.8; // Assume company is 20% better than industry
  }

  getEstimatedIndustrySalesToCapital(): number {
    return this.getAverageSalesToCapital() * 0.9; // Assume company is 10% better than industry
  }

  getPerformanceClass(metric: 'growth' | 'margin' | 'roic' | 'efficiency'): string {
    let company: number, industry: number;
    
    switch (metric) {
      case 'growth':
        company = this.normalizePercent(this.results.revenueGrowthCompany || 0);
        industry = this.normalizePercent(this.results.revenueGrowthIndustry || 0);
        break;
      case 'margin':
        company = this.normalizePercent(this.results.operatingMarginCompany || 0);
        industry = this.normalizePercent(this.results.operatingMarginIndustry || 0);
        break;
      case 'roic':
        company = this.getAverageROIC() / 100;
        industry = this.getEstimatedIndustryROIC() / 100;
        break;
      case 'efficiency':
        company = this.getAverageSalesToCapital();
        industry = this.getEstimatedIndustrySalesToCapital();
        break;
      default:
        return 'performance-neutral';
    }
    
    if (company > industry * 1.1) return 'performance-good';
    if (company < industry * 0.9) return 'performance-poor';
    return 'performance-neutral';
  }

  getPerformanceText(metric: 'growth' | 'margin' | 'roic' | 'efficiency'): string {
    let company: number, industry: number;
    
    switch (metric) {
      case 'growth':
        company = this.normalizePercent(this.results.revenueGrowthCompany || 0);
        industry = this.normalizePercent(this.results.revenueGrowthIndustry || 0);
        break;
      case 'margin':
        company = this.normalizePercent(this.results.operatingMarginCompany || 0);
        industry = this.normalizePercent(this.results.operatingMarginIndustry || 0);
        break;
      case 'roic':
        company = this.getAverageROIC() / 100;
        industry = this.getEstimatedIndustryROIC() / 100;
        break;
      case 'efficiency':
        company = this.getAverageSalesToCapital();
        industry = this.getEstimatedIndustrySalesToCapital();
        break;
      default:
        return 'At Industry Level';
    }
    
    if (company > industry * 1.1) return 'Above Industry';
    if (company < industry * 0.9) return 'Below Industry';
    return 'At Industry Level';
  }


  formatPercentage(value: number): string {
    const normalized = this.normalizePercent(value);
    return (Math.floor(normalized * 100) / 100).toFixed(2) + '%';
  }

  formatRatio(value: number): string {
    return value > 0 ? value.toFixed(2) + 'x' : 'N/A';
  }

  getPercentDifference(metric: 'growth' | 'margin'): string {
    let company: number, industry: number;
    
    switch (metric) {
      case 'growth':
        company = this.normalizePercent(this.results.revenueGrowthCompany || 0);
        industry = this.normalizePercent(this.results.revenueGrowthIndustry || 0);
        break;
      case 'margin':
        company = this.normalizePercent(this.results.operatingMarginCompany || 0);
        industry = this.normalizePercent(this.results.operatingMarginIndustry || 0);
        break;
      default:
        return 'N/A';
    }
    
    const difference = company - industry;
    const sign = difference >= 0 ? '+' : '';
    return `${sign}${difference.toFixed(2)}%`;
  }

  getDifferenceClass(metric: 'growth' | 'margin'): string {
    let company: number, industry: number;
    
    switch (metric) {
      case 'growth':
        company = this.normalizePercent(this.results.revenueGrowthCompany || 0);
        industry = this.normalizePercent(this.results.revenueGrowthIndustry || 0);
        break;
      case 'margin':
        company = this.normalizePercent(this.results.operatingMarginCompany || 0);
        industry = this.normalizePercent(this.results.operatingMarginIndustry || 0);
        break;
      default:
        return 'difference-neutral';
    }
    
    const difference = company - industry;
    if (difference > 0) return 'difference-positive';
    if (difference < 0) return 'difference-negative';
    return 'difference-neutral';
  }

  private normalizePercent(value: number): number {
    let normalized = value;
    if (Math.abs(normalized) <= 1) {
      normalized = normalized * 100;
    } else if (Math.abs(normalized) > 100) {
      normalized = normalized / 100;
    }
    return normalized;
  }

  getIndustryContextSummary(): string {
    const growthPerformance = this.getPerformanceClass('growth');
    const marginPerformance = this.getPerformanceClass('margin');
    
    if (growthPerformance === 'performance-good' && marginPerformance === 'performance-good') {
      return `${this.company.name} demonstrates superior performance in both growth and profitability compared to industry peers, indicating strong competitive advantages and operational excellence.`;
    } else if (growthPerformance === 'performance-good') {
      return `${this.company.name} shows above-average growth compared to industry peers, suggesting strong market position and growth opportunities.`;
    } else if (marginPerformance === 'performance-good') {
      return `${this.company.name} demonstrates superior operational efficiency with margins above industry average, indicating effective cost management.`;
    } else if (growthPerformance === 'performance-poor' && marginPerformance === 'performance-poor') {
      return `${this.company.name} faces challenges in both growth and profitability compared to industry peers, which may indicate competitive pressures or operational inefficiencies.`;
    } else {
      return `${this.company.name} performs in line with industry averages, showing balanced competitive positioning within its sector.`;
    }
  }
}
