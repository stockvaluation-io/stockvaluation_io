import { Component, Input, OnInit, OnChanges, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults, YearlyProjection } from '../../../../models';
import { ChartWrapperComponent, ChartData } from '../../shared/components/chart-wrapper/chart-wrapper.component';
import { formatPercentage, formatCompactCurrency, formatRatio, createCurrencyContext, getCurrencyNote, getCurrencySymbol, formatLargeNumber } from '../../../../utils/formatting.utils';
import { ThemeService } from '../../../../../../core/services';

@Component({
    selector: 'app-financial-projections-section',
    imports: [CommonModule, ChartWrapperComponent],
    template: `
    <div class="financial-projections-section">
      <h3 class="section-title">
        <i class="pi pi-chart-line section-icon"></i>
        Financial Projections
      </h3>
      
      <!-- Key Metrics Strip -->
      <div class="key-metrics-strip">
        <div class="metric-item">
          <div class="metric-label">Total FCF Present Value</div>
          <div class="metric-value primary" [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatCompact(getTotalPresentValue()) || formatCompactCurrency(getTotalPresentValue()))"></div>
        </div>
        <div class="metric-item">
          <div class="metric-label">Avg Revenue Growth</div>
          <div class="metric-value">{{ formatPercentage(getAverageGrowth()) }}</div>
        </div>
        <div class="metric-item">
          <div class="metric-label">Avg Operating Margin</div>
          <div class="metric-value">{{ formatPercentage(getAverageOperatingMargin()) }}</div>
        </div>
        <div class="metric-item">
          <div class="metric-label">Avg ROIC</div>
          <div class="metric-value">{{ formatPercentage(getAverageROIC()) }}</div>
        </div>
      </div>
      
      <!-- Financial Projections Table -->
      <div class="compact-projections-container">
        <div class="projections-table-wrapper">
          <table class="compact-projections-table">
            <thead>
              <tr>
                <th class="year-header">Year</th>
                <th *ngFor="let projection of getProjectionYears(); let i = index" class="year-column">
                  {{ i === getProjectionYears().length - 1 ? 'Terminal Year' : projection.year }}
                </th>
              </tr>
            </thead>
            <tbody>
              <!-- Revenue Section -->
              <tr class="section-divider">
                <td colspan="100%">
                  <div class="section-label">REVENUE</div>
                </td>
              </tr>
              <tr class="data-row">
                <td class="metric-label">Revenue</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell revenue">
                  <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.revenue, false) || formatCompactCurrency(projection.revenue))"></span>
                </td>
              </tr>
              <!-- Sector-specific revenue rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell revenue sector-value">
                    <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.sectorData?.[sector]?.revenue || 0, false) || formatCompactCurrency(projection.sectorData?.[sector]?.revenue || 0))"></span>
                  </td>
                </tr>
              </ng-container>
              
              <tr class="data-row percentage-row">
                <td class="metric-label">Growth Rate</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell growth-rate">
                  {{ formatPercentage(projection.revenue_growth_rate) }}
                </td>
              </tr>
              <!-- Sector-specific growth rate rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row percentage-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell growth-rate sector-value">
                    {{ formatPercentage(projection.sectorData?.[sector]?.revenue_growth_rate) }}
                  </td>
                </tr>
              </ng-container>

              <!-- Profitability Section -->
              <tr class="section-divider">
                <td colspan="100%">
                  <div class="section-label">PROFITABILITY</div>
                </td>
              </tr>
              <tr class="data-row">
                <td class="metric-label">Operating Income</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell operating">
                  <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.operating_income, false) || formatCompactCurrency(projection.operating_income))"></span>
                </td>
              </tr>
              <!-- Sector-specific operating income rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell operating sector-value">
                    <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.sectorData?.[sector]?.operating_income || 0, false) || formatCompactCurrency(projection.sectorData?.[sector]?.operating_income || 0))"></span>
                  </td>
                </tr>
              </ng-container>
              
              <tr class="data-row percentage-row">
                <td class="metric-label">Operating Margin</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell margin">
                  {{ formatPercentage(projection.operating_margin) }}
                </td>
              </tr>
              <!-- Sector-specific operating margin rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row percentage-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell margin sector-value">
                    {{ formatPercentage(projection.sectorData?.[sector]?.operating_margin) }}
                  </td>
                </tr>
              </ng-container>
              
              <tr class="data-row">
                <td class="metric-label">EBIT (1-tax)</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell ebit">
                  <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.ebit_after_tax, false) || formatCompactCurrency(projection.ebit_after_tax))"></span>
                </td>
              </tr>
              <!-- Sector-specific EBIT rows -->
              <!--<ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell ebit sector-value">
                    <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.sectorData?.[sector]?.ebit_after_tax || 0, false) || formatCompactCurrency(projection.sectorData?.[sector]?.ebit_after_tax || 0))"></span>
                  </td>
                </tr>
              </ng-container>-->

              <!-- Cash Flow Section -->
              <tr class="section-divider">
                <td colspan="100%">
                  <div class="section-label">CASH FLOW</div>
                </td>
              </tr>
              <tr class="data-row">
                <td class="metric-label">Reinvestment</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell reinvestment">
                  <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.reinvestment, false) || formatCompactCurrency(projection.reinvestment))"></span>
                </td>
              </tr>
              <!-- Sector-specific reinvestment rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell reinvestment sector-value">
                    <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.sectorData?.[sector]?.reinvestment || 0, false) || formatCompactCurrency(projection.sectorData?.[sector]?.reinvestment || 0))"></span>
                  </td>
                </tr>
              </ng-container>
              
              <tr class="data-row">
                <td class="metric-label">Free Cash Flow</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell fcf">
                  <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.free_cash_flow, false) || formatCompactCurrency(projection.free_cash_flow))"></span>
                </td>
              </tr>
              <!-- Sector-specific FCF rows -->
              <!--<ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell fcf sector-value">
                    <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.sectorData?.[sector]?.free_cash_flow || 0, false) || formatCompactCurrency(projection.sectorData?.[sector]?.free_cash_flow || 0))"></span>
                  </td>
                </tr>
              </ng-container>-->
              
              <tr class="data-row highlight-row">
                <td class="metric-label">Terminal Value</td>
                <td *ngFor="let projection of getProjectionYears(); let i = index" class="value-cell terminal-value">
                  <span *ngIf="i === getProjectionYears().length - 1" [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatCompact(results.terminalValue) || formatCompactCurrency(results.terminalValue))"></span>
                  <span *ngIf="i !== getProjectionYears().length - 1">--</span>
                </td>
              </tr>
              <tr class="data-row">
                <td class="metric-label">Present Value</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell pv">
                  <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.present_value, false) || formatCompactCurrency(projection.present_value))"></span>
                </td>
              </tr>
              <!-- Sector-specific PV rows -->
              <!--<ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell pv sector-value">
                    <span [innerHTML]="formatCurrencyWithStyle(currencyCtx?.formatTable(projection.sectorData?.[sector]?.present_value || 0, false) || formatCompactCurrency(projection.sectorData?.[sector]?.present_value || 0))"></span>
                  </td>
                </tr>
              </ng-container>-->

              <!-- Efficiency Section -->
              <tr class="section-divider">
                <td colspan="100%">
                  <div class="section-label">EFFICIENCY</div>
                </td>
              </tr>
              <tr class="data-row percentage-row">
                <td class="metric-label">ROIC</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell roic">
                  {{ formatPercentage(projection.roic) }}
                </td>
              </tr>
              <!-- Sector-specific ROIC rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row percentage-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell roic sector-value">
                    {{ formatPercentage(projection.sectorData?.[sector]?.roic) }}
                  </td>
                </tr>
              </ng-container>
              
              <tr class="data-row percentage-row">
                <td class="metric-label">Cost of Capital</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell cost-capital">
                  {{ formatPercentage(projection.cost_of_capital) }}
                </td>
              </tr>
              <!-- Sector-specific cost of capital rows -->
              <!--<ng-container *ngIf="hasSectorData()">
                <tr class="data-row percentage-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell cost-capital sector-value">
                    {{ formatPercentage(projection.sectorData?.[sector]?.cost_of_capital) }}
                  </td>
                </tr>
              </ng-container>-->
              
              <tr class="data-row">
                <td class="metric-label">Sales/Capital</td>
                <td *ngFor="let projection of getProjectionYears()" class="value-cell sales-capital">
                  {{ formatRatio(projection.sales_to_capital_ratio) }}
                </td>
              </tr>
              <!-- Sector-specific sales/capital rows -->
              <ng-container *ngIf="hasSectorData()">
                <tr class="data-row sector-row" *ngFor="let sector of getSectorNames()">
                  <td class="metric-label sector-label">└─ {{ formatSectorName(sector) }}</td>
                  <td *ngFor="let projection of getProjectionYears()" class="value-cell sales-capital sector-value">
                    {{ formatRatio(projection.sectorData?.[sector]?.sales_to_capital_ratio) }}
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
        </div>
      </div>
      
      <!-- Financial Trends Charts -->
      <div class="charts-section">
        <div class="charts-grid">
          <!-- Profitability & Efficiency Chart -->
          <div class="chart-item">
            <app-chart-wrapper
              type="line"
              [chartData]="profitabilityChartData"
              title="Profitability & Efficiency"
              size="medium"
              [isPercentageChart]="true"
              [theme]="themeService.currentTheme()">
            </app-chart-wrapper>
          </div>
          
          <!-- Growth & Capital Chart -->
          <div class="chart-item">
            <app-chart-wrapper
              type="line"
              [chartData]="growthCapitalChartData"
              title="Value Creation Analysis"
              size="medium"
              [isPercentageChart]="true"
              [theme]="themeService.currentTheme()">
            </app-chart-wrapper>
          </div>
        </div>
        
      </div>
    </div>
  `,
    styleUrls: ['../section-base.scss', './financial-projections.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class FinancialProjectionsSection implements OnInit, OnChanges {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  // Inject ThemeService
  public themeService = inject(ThemeService);

  profitabilityChartData!: ChartData;
  growthCapitalChartData!: ChartData;
  cashFlowChartData!: ChartData;
  
  // Currency formatting context
  currencyCtx: ReturnType<typeof createCurrencyContext> | null = null;

  ngOnInit(): void {
    // Initialize currency context
    this.currencyCtx = createCurrencyContext(this.results.currency, this.results.stockCurrency);
    this.setupChartData();
  }

  ngOnChanges(): void {
    // Regenerate chart data when inputs change (including theme changes)
    if (this.results && this.company) {
      this.setupChartData();
    }
  }

  /**
   * Get theme-aware colors for charts
   */
  private getThemeColors() {
    const isDark = this.themeService.currentTheme() === 'dark';
    
    return {
      primary: isDark ? '#60A5FA' : '#2563eb',
      secondary: isDark ? '#34D399' : '#059669', 
      warning: isDark ? '#FBBF24' : '#d97706',
      danger: isDark ? '#F87171' : '#dc2626',
      purple: isDark ? '#A78BFA' : '#7c3aed',
      cyan: isDark ? '#22D3EE' : '#0891b2',
      orange: isDark ? '#FB923C' : '#ea580c',
      lime: isDark ? '#A3E635' : '#65a30d'
    };
  }

  private setupChartData(): void {
    const projections = this.getProjectionYears();
    const labels = projections.map(p => `${p.year}`);
    const colors = this.getThemeColors();
    
    // 1. Profitability & Efficiency Chart (Operating Margin, EBITDA Margin, ROIC)
    this.profitabilityChartData = {
      labels,
      datasets: [
        {
          label: 'Operating Margin (%)',
          data: projections.map(p => p.operating_margin || 0),
          borderColor: colors.secondary,
          backgroundColor: colors.secondary,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        },
        {
          label: 'ROIC (%)',
          data: projections.map(p => p.roic || 0),
          borderColor: colors.warning,
          backgroundColor: colors.warning,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };

    // 2. Value Creation Analysis Chart (ROIC vs Cost of Capital)
    this.growthCapitalChartData = {
      labels,
      datasets: [
        {
          label: 'ROIC (%)',
          data: projections.map(p => p.roic || 0),
          borderColor: colors.secondary,
          backgroundColor: colors.secondary,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        },
        {
          label: 'Cost of Capital (%)',
          data: projections.map(p => p.cost_of_capital || 0),
          borderColor: colors.danger,
          backgroundColor: colors.danger,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };

    // 3. Cash Flow Analysis Chart (Bar chart for operational cash flows)
    // Collect operational cash flow values to determine appropriate scale
    const fcfValues = projections.map(p => Math.abs(p.free_cash_flow || 0));
    const reinvestmentValues = projections.map(p => Math.abs(p.reinvestment || 0));
    const operationalCashFlowValues = [...fcfValues, ...reinvestmentValues];
    
    // Determine scale for operational cash flow data
    const cashFlowScale = this.getChartScale(operationalCashFlowValues);
    
    this.cashFlowChartData = {
      labels,
      datasets: [
        {
          label: `Free Cash Flow (${getCurrencySymbol(this.results.currency)}${cashFlowScale.suffix})`,
          data: projections.map(p => (p.free_cash_flow || 0) / cashFlowScale.factor),
          borderColor: colors.secondary,
          backgroundColor: colors.secondary,
          borderWidth: 1
        },
        {
          label: `Reinvestment (${getCurrencySymbol(this.results.currency)}${cashFlowScale.suffix})`,
          data: projections.map(p => (p.reinvestment || 0) / cashFlowScale.factor),
          borderColor: colors.danger,
          backgroundColor: colors.danger,
          borderWidth: 1
        }
      ]
    };
  }

  // Use utility functions for formatting (keeping for backward compatibility)
  formatCompactCurrency = formatCompactCurrency;
  formatPercentage = formatPercentage;
  formatRatio = formatRatio;

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

  /**
   * Determines the appropriate scale factor and suffix for chart data
   * based on the maximum value in the dataset
   */
  private getChartScale(values: number[]): { factor: number; suffix: string } {
    const maxValue = Math.max(...values.map(v => Math.abs(v)));
    
    if (maxValue >= 1e12) return { factor: 1e12, suffix: 'T' };
    if (maxValue >= 1e9) return { factor: 1e9, suffix: 'B' };
    if (maxValue >= 1e6) return { factor: 1e6, suffix: 'M' };
    if (maxValue >= 1e3) return { factor: 1e3, suffix: 'K' };
    return { factor: 1, suffix: '' };
  }

  // Get projections excluding the current year (base year)
  getProjectionYears(): YearlyProjection[] {
    return this.results.projections?.slice(1) || [];
  }

  // Summary calculation methods
  getTotalRevenue(): number {
    return this.results.projections?.reduce((sum, p) => sum + p.revenue, 0) || 0;
  }

  getAverageGrowth(): number {
    return this.calculateWeightedAverage(this.results.projections || [], 'revenue_growth_rate');
  }

  getTotalOperatingIncome(): number {
    return this.results.projections?.reduce((sum, p) => sum + (p.operating_income || 0), 0) || 0;
  }

  getAverageOperatingMargin(): number {
    return this.calculateWeightedAverage(this.results.projections || [], 'operating_margin');
  }

  getTotalEbitAfterTax(): number {
    return this.results.projections?.reduce((sum, p) => sum + (p.ebit_after_tax || 0), 0) || 0;
  }

  getTotalReinvestment(): number {
    return this.results.projections?.reduce((sum, p) => sum + (p.reinvestment || 0), 0) || 0;
  }

  getTotalFCF(): number {
    return this.results.projections?.reduce((sum, p) => sum + p.free_cash_flow, 0) || 0;
  }

  getTotalPresentValue(): number {
    return this.results.projections?.reduce((sum, p) => sum + p.present_value, 0) || 0;
  }

  getAverageROIC(): number {
    return this.calculateWeightedAverage(this.results.projections || [], 'roic');
  }

  getAverageCostOfCapital(): number {
    return this.calculateWeightedAverage(this.results.projections || [], 'cost_of_capital');
  }

  getAverageSalesToCapital(): number {
    return this.calculateWeightedAverage(this.results.projections || [], 'sales_to_capital_ratio');
  }

  private calculateWeightedAverage(projections: YearlyProjection[], field: keyof YearlyProjection): number {
    if (projections.length === 0) return 0;
    
    const validValues = projections
      .map(p => p[field] as number)
      .filter(val => val !== null && val !== undefined && !isNaN(val));
    
    if (validValues.length === 0) return 0;
    
    return validValues.reduce((sum, val) => sum + val, 0) / validValues.length;
  }

  private calculateAverageGrowth(projections: YearlyProjection[]): number {
    if (projections.length < 2) return 0;
    
    const growthRates = [];
    for (let i = 1; i < projections.length; i++) {
      const growth = ((projections[i].revenue - projections[i-1].revenue) / projections[i-1].revenue) * 100;
      growthRates.push(growth);
    }
    
    return growthRates.reduce((sum, rate) => sum + rate, 0) / growthRates.length;
  }

  private calculateAverageMargin(projections: YearlyProjection[]): number {
    if (projections.length === 0) return 0;
    return (projections.reduce((sum, p) => sum + p.ebitda_margin, 0) / projections.length) * 100;
  }

  /**
   * Check if sector-specific data is available
   */
  hasSectorData(): boolean {
    const projections = this.getProjectionYears();
    return projections.length > 0 && !!projections[0].sectorData && Object.keys(projections[0].sectorData).length > 0;
  }

  /**
   * Get list of sector names
   */
  getSectorNames(): string[] {
    const projections = this.getProjectionYears();
    if (projections.length === 0 || !projections[0].sectorData) return [];
    return Object.keys(projections[0].sectorData);
  }

  /**
   * Format sector name for display (capitalize and replace hyphens)
   */
  formatSectorName(sectorName: string): string {
    return sectorName
      .split('-')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
