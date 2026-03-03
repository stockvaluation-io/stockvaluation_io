import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { MetricCardComponent, MetricCardData } from '../../shared/components/metric-card/metric-card.component';
import { ChartWrapperComponent, ChartData } from '../../shared/components/chart-wrapper/chart-wrapper.component';

interface SensitivityVariable {
  name: string;
  displayName: string;
  currentValue: number;
  minValue: number;
  maxValue: number;
  unit: string;
  impact: 'high' | 'medium' | 'low';
}

interface HeatMapCell {
  row: number;
  col: number;
  value: number;
  color: string;
  percentage: number;
}

@Component({
  selector: 'app-sensitivity-analysis-section',
  imports: [
    CommonModule,
    MetricCardComponent,
    ChartWrapperComponent
  ],
  template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-sliders-h" aria-hidden="true"></i>
          Sensitivity Analysis
        </h2>
        <p class="section-description">
          Impact of key variable changes on intrinsic value estimation
        </p>
      </header>
      
      <div class="section-content">
        <!-- Sensitivity Metrics -->
        <div class="metrics-grid">
          <app-metric-card 
            *ngFor="let metric of sensitivityMetrics"
            [data]="metric"
            size="medium">
          </app-metric-card>
        </div>

        <!-- Scenario Cards -->
        <div *ngIf="scenarioData" class="scenario-cards-container">
          <h3 class="scenario-title">
            <i class="pi pi-chart-line"></i>
            Valuation Scenarios
          </h3>
          
          <div class="scenario-cards-grid">
            <!-- Optimistic Card -->
            <div class="scenario-card optimistic">
              <div class="scenario-header">
                <i class="pi pi-arrow-up"></i>
                <h4>Optimistic</h4>
              </div>
              <div class="scenario-value">
                {{ formatCurrency(scenarioData.scenarios.optimistic.intrinsic_value) }}
              </div>
              <div class="scenario-probability">
                Probability: {{ scenarioData.scenarios.optimistic.probability }}
              </div>
              <p class="scenario-description">
                {{ scenarioData.scenarios.optimistic.description }}
              </p>
              <div class="scenario-changes">
                <span *ngFor="let change of scenarioData.scenarios.optimistic.key_changes" 
                      class="change-tag">
                  {{ change }}
                </span>
              </div>
            </div>
            
            <!-- Base Case Card -->
            <div class="scenario-card base-case">
              <div class="scenario-header">
                <i class="pi pi-minus"></i>
                <h4>Base Case</h4>
              </div>
              <div class="scenario-value">
                {{ formatCurrency(scenarioData.scenarios.base.intrinsic_value) }}
              </div>
              <div class="scenario-probability">
                Probability: {{ scenarioData.scenarios.base.probability }}
              </div>
              <p class="scenario-description">
                {{ scenarioData.scenarios.base.description }}
              </p>
              <div class="scenario-changes">
                <span *ngFor="let change of scenarioData.scenarios.base.key_changes" 
                      class="change-tag">
                  {{ change }}
                </span>
              </div>
            </div>
            
            <!-- Pessimistic Card -->
            <div class="scenario-card pessimistic">
              <div class="scenario-header">
                <i class="pi pi-arrow-down"></i>
                <h4>Pessimistic</h4>
              </div>
              <div class="scenario-value">
                {{ formatCurrency(scenarioData.scenarios.pessimistic.intrinsic_value) }}
              </div>
              <div class="scenario-probability">
                Probability: {{ scenarioData.scenarios.pessimistic.probability }}
              </div>
              <p class="scenario-description">
                {{ scenarioData.scenarios.pessimistic.description }}
              </p>
              <div class="scenario-changes">
                <span *ngFor="let change of scenarioData.scenarios.pessimistic.key_changes" 
                      class="change-tag">
                  {{ change }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Variable Controls -->
        <div class="variable-controls">
          <h3 class="controls-title">
            <i class="pi pi-cog"></i>
            Key Variables Impact
          </h3>
          
          <div class="variables-grid">
            <div *ngFor="let variable of sensitivityVariables" 
                 class="variable-control"
                 [class]="'impact-' + variable.impact">
              <div class="variable-header">
                <div class="variable-info">
                  <h4 class="variable-name">{{ variable.displayName }}</h4>
                  <span class="variable-current">
                    Current: {{ formatVariableValue(variable) }}
                  </span>
                </div>
                <span class="impact-badge" [class]="'impact-' + variable.impact">
                  {{ variable.impact | titlecase }} Impact
                </span>
              </div>
              
              <div class="variable-range">
                <span class="range-min">{{ formatValue(variable.minValue, variable.unit) }}</span>
                <div class="range-bar">
                  <div class="range-fill" 
                       [style.left.%]="getRangePosition(variable)"
                       [class]="'impact-' + variable.impact">
                  </div>
                </div>
                <span class="range-max">{{ formatValue(variable.maxValue, variable.unit) }}</span>
              </div>
              
              <div class="impact-description">
                {{ getVariableImpactDescription(variable) }}
              </div>
            </div>
          </div>
        </div>

        <!-- Sensitivity Heat Map -->
        <div class="heatmap-container">
          <h3 class="heatmap-title">
            <i class="pi pi-th"></i>
            Two-Variable Sensitivity Matrix
          </h3>
          
          <div class="heatmap-wrapper">
            <div class="heatmap-labels">
              <div class="y-axis-label">
                <span>Revenue Growth Rate (%)</span>
              </div>
              <div class="heatmap-grid-container">
                <div class="x-axis-labels">
                  <span *ngFor="let label of heatmapXLabels">{{ label }}</span>
                </div>
                
                <div class="y-axis-labels">
                  <span *ngFor="let label of heatmapYLabels">{{ label }}</span>
                </div>
                
                <div class="heatmap-grid">
                  <div *ngFor="let cell of heatmapCells" 
                       class="heatmap-cell"
                       [style.background-color]="cell.color"
                       [title]="getHeatmapTooltip(cell)">
                    <span class="cell-value">{{ formatCurrency(cell.value) }}</span>
                    <span class="cell-percentage" 
                          [class]="cell.percentage > 0 ? 'positive' : 'negative'">
                      {{ cell.percentage > 0 ? '+' : '' }}{{ cell.percentage.toFixed(0) }}%
                    </span>
                  </div>
                </div>
              </div>
              <div class="x-axis-label">
                <span>Discount Rate (%)</span>
              </div>
            </div>
            
            <!-- Heat Map Legend -->
            <div class="heatmap-legend">
              <div class="legend-title">Intrinsic Value</div>
              <div class="legend-gradient">
                <div class="gradient-bar"></div>
                <div class="legend-labels">
                  <span>{{ formatCurrency(minHeatmapValue) }}</span>
                  <span>Base: {{ formatCurrency(results.intrinsicValue) }}</span>
                  <span>{{ formatCurrency(maxHeatmapValue) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Tornado Chart -->
        <div class="tornado-chart">
          <app-chart-wrapper
            type="bar"
            [chartData]="tornadoChartData"
            title="Variable Impact Analysis (Tornado Chart)"
            subtitle="Sensitivity of intrinsic value to ±20% changes in key variables"
            size="large">
          </app-chart-wrapper>
        </div>

        <!-- Scenario Summary -->
        <div class="scenario-summary">
          <h3 class="summary-title">
            <i class="pi pi-list"></i>
            Key Sensitivity Insights
          </h3>
          
          <div class="insights-grid">
            <div class="insight-card positive">
              <div class="insight-icon">
                <i class="pi pi-arrow-up"></i>
              </div>
              <div class="insight-content">
                <h4>Most Upside Scenario</h4>
                <p class="insight-value">{{ formatCurrency(bestCaseValue) }}</p>
                <p class="insight-description">High growth ({{bestCaseGrowth}}%) + Low discount rate ({{bestCaseDiscount}}%)</p>
              </div>
            </div>
            
            <div class="insight-card negative">
              <div class="insight-icon">
                <i class="pi pi-arrow-down"></i>
              </div>
              <div class="insight-content">
                <h4>Most Downside Scenario</h4>
                <p class="insight-value">{{ formatCurrency(worstCaseValue) }}</p>
                <p class="insight-description">Low growth ({{worstCaseGrowth}}%) + High discount rate ({{worstCaseDiscount}}%)</p>
              </div>
            </div>
            
            <div class="insight-card neutral">
              <div class="insight-icon">
                <i class="pi pi-info-circle"></i>
              </div>
              <div class="insight-content">
                <h4>Sensitivity Range</h4>
                <p class="insight-value">{{ formatPercentage(sensitivityRange) }}</p>
                <p class="insight-description">Total valuation range from worst to best case scenarios</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
  styleUrls: ['../section-base.scss', './sensitivity-analysis.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SensitivityAnalysisSection implements OnInit {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
  @Input() scenarioData?: any;

  sensitivityMetrics: MetricCardData[] = [];
  sensitivityVariables: SensitivityVariable[] = [];
  tornadoChartData!: ChartData;

  // Heat map data
  heatmapCells: HeatMapCell[] = [];
  heatmapXLabels: string[] = [];
  heatmapYLabels: string[] = [];
  minHeatmapValue = 0;
  maxHeatmapValue = 0;

  // Scenario values
  bestCaseValue = 0;
  worstCaseValue = 0;
  bestCaseGrowth = 0;
  worstCaseGrowth = 0;
  bestCaseDiscount = 0;
  worstCaseDiscount = 0;
  sensitivityRange = 0;

  ngOnInit(): void {
    this.setupSensitivityMetrics();
    this.setupSensitivityVariables();
    this.setupTornadoChart();
    this.setupHeatMap();
    this.calculateScenarios();
  }

  private setupSensitivityMetrics(): void {
    const baseValue = this.results.intrinsicValue;
    const highGrowthValue = baseValue * 1.25; // +25% for high growth scenario
    const lowGrowthValue = baseValue * 0.8;   // -20% for low growth scenario
    const volatility = ((highGrowthValue - lowGrowthValue) / baseValue) * 100;

    this.sensitivityMetrics = [
      {
        title: 'Base Case Value',
        value: baseValue,
        format: 'currency',
        precision: 0,
        status: 'neutral',
        icon: 'target',
        subtitle: 'Current assumptions'
      },
      {
        title: 'High Growth Scenario',
        value: highGrowthValue,
        format: 'currency',
        precision: 0,
        status: 'positive',
        icon: 'trending-up',
        subtitle: '+20% growth rate',
        trend: 'up',
        trendValue: '+25%'
      },
      {
        title: 'Low Growth Scenario',
        value: lowGrowthValue,
        format: 'currency',
        precision: 0,
        status: 'negative',
        icon: 'trending-down',
        subtitle: '-20% growth rate',
        trend: 'down',
        trendValue: '-20%'
      },
      {
        title: 'Sensitivity Range',
        value: volatility,
        format: 'percentage',
        precision: 0,
        status: volatility > 50 ? 'warning' : volatility > 30 ? 'neutral' : 'positive',
        icon: 'arrows-alt',
        subtitle: 'Valuation volatility'
      }
    ];
  }

  private setupSensitivityVariables(): void {
    this.sensitivityVariables = [
      {
        name: 'revenue_growth',
        displayName: 'Revenue Growth Rate',
        currentValue: 8.5,
        minValue: 2.0,
        maxValue: 15.0,
        unit: '%',
        impact: 'high'
      },
      {
        name: 'discount_rate',
        displayName: 'Discount Rate (WACC)',
        currentValue: 9.2,
        minValue: 6.0,
        maxValue: 12.0,
        unit: '%',
        impact: 'high'
      },
      {
        name: 'terminal_growth',
        displayName: 'Terminal Growth Rate',
        currentValue: 2.5,
        minValue: 1.0,
        maxValue: 4.0,
        unit: '%',
        impact: 'medium'
      },
      {
        name: 'ebitda_margin',
        displayName: 'EBITDA Margin',
        currentValue: 22.0,
        minValue: 15.0,
        maxValue: 30.0,
        unit: '%',
        impact: 'medium'
      }
    ];
  }

  private setupTornadoChart(): void {
    const baseValue = this.results.intrinsicValue;

    // Calculate impact of ±20% change in each variable
    const impacts = [
      { variable: 'Revenue Growth', low: baseValue * 0.75, high: baseValue * 1.30 },
      { variable: 'Discount Rate', low: baseValue * 1.25, high: baseValue * 0.80 },
      { variable: 'Terminal Growth', low: baseValue * 0.90, high: baseValue * 1.15 },
      { variable: 'EBITDA Margin', low: baseValue * 0.85, high: baseValue * 1.20 }
    ];

    this.tornadoChartData = {
      labels: impacts.map(i => i.variable),
      datasets: [
        {
          label: 'Downside (-20%)',
          data: impacts.map(i => i.low - baseValue),
          backgroundColor: '#ef4444',
          borderColor: '#ef4444',
          borderWidth: 1
        },
        {
          label: 'Upside (+20%)',
          data: impacts.map(i => i.high - baseValue),
          backgroundColor: '#10b981',
          borderColor: '#10b981',
          borderWidth: 1
        }
      ]
    };
  }

  private setupHeatMap(): void {
    if (this.scenarioData?.heatmap_data) {
      // Use real heat map data from backend
      const heatmap = this.scenarioData.heatmap_data;

      this.heatmapXLabels = heatmap.discount_rates.map((r: number) => `${r.toFixed(1)}%`);
      this.heatmapYLabels = heatmap.growth_rates.map((r: number) => `${r.toFixed(1)}%`);

      // Calculate min/max from valuations if not provided
      if (heatmap.min_value !== undefined && heatmap.max_value !== undefined) {
        this.minHeatmapValue = heatmap.min_value;
        this.maxHeatmapValue = heatmap.max_value;
      } else {
        // Calculate from valuations array
        const allValues = heatmap.valuations.flat();
        this.minHeatmapValue = Math.min(...allValues);
        this.maxHeatmapValue = Math.max(...allValues);
      }

      const baseValue = this.results.intrinsicValue;
      this.heatmapCells = [];

      heatmap.valuations.forEach((row: number[], i: number) => {
        row.forEach((value: number, j: number) => {
          const percentage = ((value - baseValue) / baseValue) * 100;
          this.heatmapCells.push({
            row: i,
            col: j,
            value,
            color: this.getHeatMapColor(value, this.minHeatmapValue, this.maxHeatmapValue),
            percentage
          });
        });
      });
    } else {
      // Fallback to mock data (existing logic)
      const growthRates = [4, 6, 8, 10, 12]; // Revenue growth rates
      const discountRates = [7, 8, 9, 10, 11]; // Discount rates

      this.heatmapXLabels = discountRates.map(r => `${r}%`);
      this.heatmapYLabels = growthRates.map(r => `${r}%`);

      const baseValue = this.results.intrinsicValue;
      const values: number[] = [];

      // Generate sensitivity matrix
      growthRates.forEach((growth, i) => {
        discountRates.forEach((discount, j) => {
          // Simplified sensitivity calculation
          const growthFactor = 1 + ((growth - 8) / 100); // Base growth is 8%
          const discountFactor = 1 - ((discount - 9) / 100); // Base discount is 9%
          const value = baseValue * growthFactor * discountFactor;
          values.push(value);
        });
      });

      this.minHeatmapValue = Math.min(...values);
      this.maxHeatmapValue = Math.max(...values);

      // Create heat map cells
      this.heatmapCells = [];
      growthRates.forEach((growth, i) => {
        discountRates.forEach((discount, j) => {
          const index = i * discountRates.length + j;
          const value = values[index];
          const percentage = ((value - baseValue) / baseValue) * 100;

          this.heatmapCells.push({
            row: i,
            col: j,
            value,
            color: this.getHeatMapColor(value, this.minHeatmapValue, this.maxHeatmapValue),
            percentage
          });
        });
      });
    }
  }

  private calculateScenarios(): void {
    const baseValue = this.results.intrinsicValue;

    // Best case: High growth + Low discount rate
    this.bestCaseGrowth = 12;
    this.bestCaseDiscount = 7;
    this.bestCaseValue = baseValue * 1.45; // +45%

    // Worst case: Low growth + High discount rate
    this.worstCaseGrowth = 4;
    this.worstCaseDiscount = 11;
    this.worstCaseValue = baseValue * 0.65; // -35%

    this.sensitivityRange = ((this.bestCaseValue - this.worstCaseValue) / baseValue) * 100;
  }

  private getHeatMapColor(value: number, min: number, max: number): string {
    const normalized = (value - min) / (max - min);

    if (normalized < 0.2) return '#ef4444'; // Red
    if (normalized < 0.4) return '#f97316'; // Orange
    if (normalized < 0.6) return '#eab308'; // Yellow
    if (normalized < 0.8) return '#84cc16'; // Light green
    return '#22c55e'; // Green
  }

  formatVariableValue(variable: SensitivityVariable): string {
    return this.formatValue(variable.currentValue, variable.unit);
  }

  formatValue(value: number, unit: string): string {
    return `${value.toFixed(1)}${unit}`;
  }

  formatCurrency(value: number): string {
    if (value >= 1e9) return `$${(value / 1e9).toFixed(1)}B`;
    if (value >= 1e6) return `$${(value / 1e6).toFixed(1)}M`;
    if (value >= 1e3) return `$${(value / 1e3).toFixed(1)}K`;
    return `$${value.toFixed(0)}`;
  }

  formatPercentage(value: number): string {
    return `${value.toFixed(0)}%`;
  }

  getRangePosition(variable: SensitivityVariable): number {
    const range = variable.maxValue - variable.minValue;
    const position = variable.currentValue - variable.minValue;
    return (position / range) * 100;
  }

  getVariableImpactDescription(variable: SensitivityVariable): string {
    const impacts = {
      'revenue_growth': 'Higher growth rates significantly increase intrinsic value through expanded cash flows',
      'discount_rate': 'Lower discount rates increase present value of future cash flows',
      'terminal_growth': 'Terminal growth assumptions affect long-term value significantly',
      'ebitda_margin': 'Margin improvements directly impact cash flow generation capacity'
    };
    return impacts[variable.name as keyof typeof impacts] || '';
  }

  getHeatmapTooltip(cell: HeatMapCell): string {
    return `Growth: ${this.heatmapYLabels[cell.row]}, Discount: ${this.heatmapXLabels[cell.col]}\nValue: ${this.formatCurrency(cell.value)} (${cell.percentage > 0 ? '+' : ''}${cell.percentage.toFixed(1)}%)`;
  }
}