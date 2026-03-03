import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults, YearlyProjection } from '../../../../models';
import { MetricCardComponent, MetricCardData } from '../../shared/components/metric-card/metric-card.component';
import { ChartWrapperComponent, ChartData } from '../../shared/components/chart-wrapper/chart-wrapper.component';

interface GrowthDriver {
  category: string;
  impact: 'high' | 'medium' | 'low';
  trend: 'positive' | 'negative' | 'neutral';
  description: string;
  contribution: number; // percentage contribution to growth
}

@Component({
    selector: 'app-growth-analysis-section',
    imports: [
        CommonModule,
        MetricCardComponent,
        ChartWrapperComponent
    ],
    template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-trending-up" aria-hidden="true"></i>
          Growth Analysis
        </h2>
        <p class="section-description">
          Revenue growth drivers and sustainability analysis over the projection period
        </p>
      </header>
      
      <div class="section-content">
        <!-- Growth Metrics Overview -->
        <div class="metrics-grid">
          <app-metric-card 
            *ngFor="let metric of growthMetrics"
            [data]="metric"
            size="medium">
          </app-metric-card>
        </div>

        <!-- Growth Trends Chart -->
        <div class="growth-chart">
          <app-chart-wrapper
            type="line"
            [chartData]="growthTrendData"
            title="Revenue Growth Rate Trends"
            subtitle="Year-over-year growth rates and projections"
            size="large"
            [isPercentageChart]="true">
          </app-chart-wrapper>
        </div>

        <!-- Growth Drivers Analysis -->
        <div class="growth-drivers">
          <h3 class="drivers-title">
            <i class="pi pi-sitemap"></i>
            Key Growth Drivers
          </h3>
          
          <div class="drivers-grid">
            <div *ngFor="let driver of growthDrivers" 
                 class="driver-card"
                 [class]="getDriverClasses(driver)">
              <div class="driver-header">
                <h4 class="driver-category">{{ driver.category }}</h4>
                <div class="driver-badges">
                  <span class="impact-badge" [class]="'impact-' + driver.impact">
                    {{ driver.impact | titlecase }} Impact
                  </span>
                  <span class="trend-badge" [class]="'trend-' + driver.trend">
                    <i [class]="getTrendIcon(driver.trend)"></i>
                    {{ driver.trend | titlecase }}
                  </span>
                </div>
              </div>
              
              <p class="driver-description">{{ driver.description }}</p>
              
              <div class="driver-contribution">
                <div class="contribution-bar">
                  <div class="contribution-fill" 
                       [style.width.%]="driver.contribution"
                       [class]="'fill-' + driver.impact">
                  </div>
                </div>
                <span class="contribution-text">{{ driver.contribution }}% contribution</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Growth Scenarios Chart -->
        <div class="scenarios-chart">
          <app-chart-wrapper
            type="line"
            [chartData]="scenarioData"
            title="Growth Scenario Analysis"
            subtitle="Conservative, base case, and optimistic growth projections"
            size="medium">
          </app-chart-wrapper>
        </div>

        <!-- Growth Sustainability Analysis -->
        <div class="sustainability-analysis">
          <h3 class="sustainability-title">
            <i class="pi pi-shield"></i>
            Growth Sustainability Assessment
          </h3>
          
          <div class="sustainability-grid">
            <div class="sustainability-metric">
              <div class="metric-icon positive">
                <i class="pi pi-check"></i>
              </div>
              <div class="metric-content">
                <h4>Market Position</h4>
                <p>Strong competitive moat with {{ company.marketCap ? 'significant' : 'established' }} market presence</p>
              </div>
            </div>
            
            <div class="sustainability-metric">
              <div class="metric-icon" [class]="getCashGenerationClass()">
                <i class="pi pi-dollar"></i>
              </div>
              <div class="metric-content">
                <h4>Cash Generation</h4>
                <p>{{ getCashGenerationDescription() }}</p>
              </div>
            </div>
            
            <div class="sustainability-metric">
              <div class="metric-icon" [class]="getGrowthConsistencyClass()">
                <i class="pi pi-chart-line"></i>
              </div>
              <div class="metric-content">
                <h4>Growth Consistency</h4>
                <p>{{ getGrowthConsistencyDescription() }}</p>
              </div>
            </div>
            
            <div class="sustainability-metric">
              <div class="metric-icon warning">
                <i class="pi pi-exclamation-triangle"></i>
              </div>
              <div class="metric-content">
                <h4>Risk Factors</h4>
                <p>Market saturation and competitive pressures may impact long-term growth</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
    styleUrls: ['../section-base.scss', './growth-analysis.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class GrowthAnalysisSection implements OnInit {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  growthMetrics: MetricCardData[] = [];
  growthTrendData!: ChartData;
  scenarioData!: ChartData;
  growthDrivers: GrowthDriver[] = [];

  ngOnInit(): void {
    this.setupGrowthMetrics();
    this.setupGrowthCharts();
    this.setupGrowthDrivers();
  }

  private setupGrowthMetrics(): void {
    const projections = this.results.projections || [];
    const cagr = this.calculateCAGR(projections);
    const peakGrowth = this.calculatePeakGrowth(projections);
    const avgGrowth = this.calculateAverageGrowth(projections);
    const growthVolatility = this.calculateGrowthVolatility(projections);

    this.growthMetrics = [
      {
        title: 'Revenue CAGR',
        value: cagr,
        format: 'percentage',
        precision: 1,
        status: cagr > 10 ? 'positive' : cagr > 5 ? 'neutral' : 'negative',
        icon: 'trending-up',
        subtitle: '10-year compound growth',
        trend: cagr > 5 ? 'up' : 'down',
        trendValue: `${cagr > 5 ? 'Strong' : 'Moderate'}`
      },
      {
        title: 'Peak Growth Rate',
        value: peakGrowth,
        format: 'percentage',
        precision: 1,
        status: peakGrowth > 15 ? 'positive' : peakGrowth > 8 ? 'neutral' : 'warning',
        icon: 'arrow-up',
        subtitle: 'Highest projected year',
        trend: 'up'
      },
      {
        title: 'Average Growth',
        value: avgGrowth,
        format: 'percentage',
        precision: 1,
        status: avgGrowth > 8 ? 'positive' : avgGrowth > 3 ? 'neutral' : 'negative',
        icon: 'chart-bar',
        subtitle: 'Mean annual growth',
        trend: avgGrowth > 5 ? 'up' : avgGrowth > 0 ? 'neutral' : 'down'
      },
      {
        title: 'Growth Volatility',
        value: growthVolatility,
        format: 'percentage',
        precision: 1,
        status: growthVolatility < 5 ? 'positive' : growthVolatility < 10 ? 'neutral' : 'warning',
        icon: 'wave-sine',
        subtitle: 'Growth consistency',
        trend: growthVolatility < 5 ? 'up' : 'down'
      }
    ];
  }

  private setupGrowthCharts(): void {
    const projections = this.results.projections || [];
    
    // Calculate growth rates
    const growthRates = this.calculateGrowthRates(projections);
    
    this.growthTrendData = {
      labels: growthRates.map((_, i) => `Year ${i + 2}`), // Growth starts from year 2
      datasets: [
        {
          label: 'Revenue Growth Rate (%)',
          data: growthRates,
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          fill: true,
          tension: 0.3
        }
      ]
    };

    // Scenario analysis
    this.scenarioData = {
      labels: projections.map(p => `Year ${p.year}`),
      datasets: [
        {
          label: 'Conservative (-20%)',
          data: projections.map(p => p.revenue * 0.8),
          borderColor: '#ef4444',
          backgroundColor: 'rgba(239, 68, 68, 0.1)',
          fill: false,
          tension: 0.3
        },
        {
          label: 'Base Case',
          data: projections.map(p => p.revenue),
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          fill: false,
          tension: 0.3
        },
        {
          label: 'Optimistic (+20%)',
          data: projections.map(p => p.revenue * 1.2),
          borderColor: '#10b981',
          backgroundColor: 'rgba(16, 185, 129, 0.1)',
          fill: false,
          tension: 0.3
        }
      ]
    };
  }

  private setupGrowthDrivers(): void {
    // This would typically come from API data or analysis
    this.growthDrivers = [
      {
        category: 'Market Expansion',
        impact: 'high',
        trend: 'positive',
        description: 'Geographic expansion into emerging markets with growing demand for digital services',
        contribution: 35
      },
      {
        category: 'Product Innovation',
        impact: 'high',
        trend: 'positive',
        description: 'New product lines and technological advances driving customer adoption',
        contribution: 30
      },
      {
        category: 'Digital Transformation',
        impact: 'medium',
        trend: 'positive',
        description: 'Operational efficiency improvements and cost optimization initiatives',
        contribution: 20
      },
      {
        category: 'Market Share Gain',
        impact: 'medium',
        trend: 'neutral',
        description: 'Competitive positioning in mature markets with moderate consolidation potential',
        contribution: 15
      }
    ];
  }

  private calculateCAGR(projections: YearlyProjection[]): number {
    if (projections.length < 2) return 0;
    const first = projections[0].revenue;
    const last = projections[projections.length - 1].revenue;
    const years = projections.length - 1;
    return (Math.pow(last / first, 1 / years) - 1) * 100;
  }

  private calculatePeakGrowth(projections: YearlyProjection[]): number {
    const growthRates = this.calculateGrowthRates(projections);
    return Math.max(...growthRates);
  }

  private calculateAverageGrowth(projections: YearlyProjection[]): number {
    const growthRates = this.calculateGrowthRates(projections);
    return growthRates.reduce((sum, rate) => sum + rate, 0) / growthRates.length;
  }

  private calculateGrowthVolatility(projections: YearlyProjection[]): number {
    const growthRates = this.calculateGrowthRates(projections);
    const mean = this.calculateAverageGrowth(projections);
    const variance = growthRates.reduce((sum, rate) => sum + Math.pow(rate - mean, 2), 0) / growthRates.length;
    return Math.sqrt(variance);
  }

  private calculateGrowthRates(projections: YearlyProjection[]): number[] {
    const rates = [];
    for (let i = 1; i < projections.length; i++) {
      const rate = ((projections[i].revenue - projections[i-1].revenue) / projections[i-1].revenue) * 100;
      rates.push(rate);
    }
    return rates;
  }

  getDriverClasses(driver: GrowthDriver): string {
    return `impact-${driver.impact} trend-${driver.trend}`;
  }

  getTrendIcon(trend: string): string {
    switch (trend) {
      case 'positive': return 'pi pi-trending-up';
      case 'negative': return 'pi pi-trending-down';
      default: return 'pi pi-minus';
    }
  }

  getCashGenerationClass(): string {
    const totalFCF = this.results.projections?.reduce((sum, p) => sum + p.free_cash_flow, 0) || 0;
    return totalFCF > 0 ? 'positive' : 'negative';
  }

  getCashGenerationDescription(): string {
    const totalFCF = this.results.projections?.reduce((sum, p) => sum + p.free_cash_flow, 0) || 0;
    return totalFCF > 0 
      ? 'Strong free cash flow generation supports sustainable growth investments'
      : 'Cash flow challenges may limit growth investment capacity';
  }

  getGrowthConsistencyClass(): string {
    const volatility = this.calculateGrowthVolatility(this.results.projections || []);
    return volatility < 5 ? 'positive' : volatility < 10 ? 'neutral' : 'warning';
  }

  getGrowthConsistencyDescription(): string {
    const volatility = this.calculateGrowthVolatility(this.results.projections || []);
    if (volatility < 5) return 'Consistent growth pattern with low volatility';
    if (volatility < 10) return 'Moderate growth variability within acceptable range';
    return 'High growth volatility indicates execution risks';
  }
}