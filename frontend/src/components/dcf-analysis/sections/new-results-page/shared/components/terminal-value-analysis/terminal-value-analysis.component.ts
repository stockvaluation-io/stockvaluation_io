import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface TerminalValueData {
  terminalValue: number;
  terminalCashFlow: number;
  pvTerminalValue: number;
  growthRate: number;
  costOfCapital: number;
  returnOnCapital: number;
  reinvestmentRate: number;
}

export interface IndustryComparisonData {
  revenueGrowthCompany: number;
  revenueGrowthIndustry: number;
  operatingMarginCompany: number;
  operatingMarginIndustry: number;
}

@Component({
  selector: 'app-terminal-value-analysis',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="terminal-value-container">
      <header class="section-header">
        <h3 class="section-title">
          <i class="pi pi-chart-line" aria-hidden="true"></i>
          Terminal Value Analysis
        </h3>
        <p class="section-description">
          Long-term valuation assumptions and industry benchmarking
        </p>
      </header>

      <div class="analysis-layout">
        
        <!-- Terminal Value Metrics -->
        <div class="metrics-section">
          <h4 class="subsection-title">
            <i class="pi pi-infinity" aria-hidden="true"></i>
            Terminal Value Breakdown
          </h4>
          
          <div class="metrics-grid">
            <div class="metric-card primary">
              <div class="metric-icon">
                <i class="pi pi-dollar" aria-hidden="true"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">Terminal Value</span>
                <span class="metric-value">{{ formatCurrency(terminalData?.terminalValue || 0) }}</span>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon">
                <i class="pi pi-arrow-up-right" aria-hidden="true"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">Terminal Growth Rate</span>
                <span class="metric-value">{{ formatPercentage(terminalData?.growthRate || 0) }}</span>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon">
                <i class="pi pi-percentage" aria-hidden="true"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">Terminal ROIC</span>
                <span class="metric-value">{{ formatPercentage(terminalData?.returnOnCapital || 0) }}</span>
              </div>
            </div>

            <div class="metric-card">
              <div class="metric-icon">
                <i class="pi pi-refresh" aria-hidden="true"></i>
              </div>
              <div class="metric-content">
                <span class="metric-label">Reinvestment Rate</span>
                <span class="metric-value">{{ formatPercentage(terminalData?.reinvestmentRate || 0) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Terminal Value Parameters Table -->
        <div class="parameters-section">
          <h4 class="subsection-title">
            <i class="pi pi-cog" aria-hidden="true"></i>
            Terminal Value Parameters
          </h4>
          
          <div class="table-container">
            <table class="parameters-table">
              <thead>
                <tr>
                  <th>Parameter</th>
                  <th>Value</th>
                  <th>Impact</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="parameter-name">Terminal Growth Rate</td>
                  <td class="parameter-value">{{ formatPercentage(terminalData?.growthRate || 0) }}</td>
                  <td class="impact-note">Long-term sustainable growth</td>
                </tr>
                <tr>
                  <td class="parameter-name">Cost of Capital (WACC)</td>
                  <td class="parameter-value">{{ formatPercentage(terminalData?.costOfCapital || 0) }}</td>
                  <td class="impact-note">Discount rate for terminal value</td>
                </tr>
                <tr>
                  <td class="parameter-name">Return on Capital</td>
                  <td class="parameter-value">{{ formatPercentage(terminalData?.returnOnCapital || 0) }}</td>
                  <td class="impact-note">Efficiency of capital deployment</td>
                </tr>
                <tr>
                  <td class="parameter-name">Reinvestment Rate</td>
                  <td class="parameter-value">{{ formatPercentage(terminalData?.reinvestmentRate || 0) }}</td>
                  <td class="impact-note">Capital required for growth</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Industry Comparison -->
        <div class="comparison-section" *ngIf="industryData">
          <h4 class="subsection-title">
            <i class="pi pi-chart-bar" aria-hidden="true"></i>
            Industry Comparison
          </h4>
          
          <div class="table-container">
            <table class="comparison-table">
              <thead>
                <tr>
                  <th>Metric</th>
                  <th>{{ companyName || 'Company' }}</th>
                  <th>Industry Average</th>
                  <th>Variance</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td class="metric-name">Revenue Growth</td>
                  <td class="company-value">{{ formatPercentage(industryData.revenueGrowthCompany) }}</td>
                  <td class="industry-value">{{ formatPercentage(industryData.revenueGrowthIndustry) }}</td>
                  <td class="variance-value" [class]="getVarianceClass(getVariance(industryData.revenueGrowthCompany, industryData.revenueGrowthIndustry))">
                    {{ formatVariance(getVariance(industryData.revenueGrowthCompany, industryData.revenueGrowthIndustry)) }}
                  </td>
                </tr>
                <tr>
                  <td class="metric-name">Operating Margin</td>
                  <td class="company-value">{{ formatPercentage(industryData.operatingMarginCompany) }}</td>
                  <td class="industry-value">{{ formatPercentage(industryData.operatingMarginIndustry) }}</td>
                  <td class="variance-value" [class]="getVarianceClass(getVariance(industryData.operatingMarginCompany, industryData.operatingMarginIndustry))">
                    {{ formatVariance(getVariance(industryData.operatingMarginCompany, industryData.operatingMarginIndustry)) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- Terminal Value Components -->
        <div class="components-section">
          <h4 class="subsection-title">
            <i class="pi pi-list" aria-hidden="true"></i>
            Terminal Value Components
          </h4>
          
          <div class="components-grid">
            <div class="component-item">
              <div class="component-label">Terminal Cash Flow</div>
              <div class="component-value">{{ formatCurrency(terminalData?.terminalCashFlow || 0) }}</div>
              <div class="component-note">Final year normalized FCF</div>
            </div>
            
            <div class="component-item">
              <div class="component-label">Present Value of Terminal</div>
              <div class="component-value primary">{{ formatCurrency(terminalData?.pvTerminalValue || 0) }}</div>
              <div class="component-note">Discounted to present value</div>
            </div>
            
            <div class="component-item">
              <div class="component-label">Terminal Value Multiple</div>
              <div class="component-value">{{ getTerminalMultiple() }}x</div>
              <div class="component-note">EV/FCF at terminal year</div>
            </div>
          </div>
        </div>

      </div>
    </div>
  `,
  styleUrls: ['./terminal-value-analysis.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TerminalValueAnalysisComponent {
  @Input() terminalData?: TerminalValueData;
  @Input() industryData?: IndustryComparisonData;
  @Input() companyName?: string;

  formatCurrency(value: number): string {
    if (value === null || value === undefined) return '--';
    
    const absValue = Math.abs(value);
    if (absValue >= 1e12) {
      return `$${(value / 1e12).toFixed(2).replace(/\.00$/, '')}T`;
    } else if (absValue >= 1e9) {
      return `$${(value / 1e9).toFixed(2).replace(/\.00$/, '')}B`;
    } else if (absValue >= 1e6) {
      return `$${(value / 1e6).toFixed(2).replace(/\.00$/, '')}M`;
    } else if (absValue >= 1e3) {
      return `$${(value / 1e3).toFixed(2).replace(/\.00$/, '')}K`;
    } else {
      return `$${value.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: 2 })}`;
    }
  }

  formatPercentage(value: number): string {
    if (value === null || value === undefined) return '--';
    return `${this.normalizePercent(value).toFixed(2)}%`;
  }

  getVariance(companyValue: number, industryValue: number): number {
    const normalizedCompany = this.normalizePercent(companyValue);
    const normalizedIndustry = this.normalizePercent(industryValue);
    if (!normalizedIndustry) return 0;
    return ((normalizedCompany - normalizedIndustry) / normalizedIndustry) * 100;
  }

  formatVariance(variance: number): string {
    const sign = variance >= 0 ? '+' : '';
    return `${sign}${variance.toFixed(1)}%`;
  }

  getVarianceClass(variance: number): string {
    if (variance > 10) return 'positive-large';
    if (variance > 0) return 'positive';
    if (variance < -10) return 'negative-large';
    if (variance < 0) return 'negative';
    return 'neutral';
  }

  getTerminalMultiple(): string {
    if (!this.terminalData?.terminalCashFlow || !this.terminalData?.terminalValue) {
      return '--';
    }
    const multiple = this.terminalData.terminalValue / this.terminalData.terminalCashFlow;
    return multiple.toFixed(1);
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
}
