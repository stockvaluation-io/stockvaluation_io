import { Component, Input, ChangeDetectionStrategy, OnInit, OnChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { MonteCarloYearDistribution } from '../../../../models/api-response.interface';
import { createCurrencyContext, formatPrice } from '../../../../utils/formatting.utils';
import { ThemeService } from '../../../../../../core/services';

interface ParameterInsight {
  name: string;
  icon: string;
  p5: number;
  p50: number;
  p95: number;
  explanation: string | null;
  unit: string;
  expanded: boolean;
}

@Component({
  selector: 'app-monte-carlo-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="monte-carlo-section" *ngIf="results.monteCarloResult">
      <div class="section-header">
        <div class="header-left">
          <i class="pi pi-chart-scatter section-icon"></i>
          <div>
            <h3 class="section-title">Probabilistic Valuation</h3>
            <span class="section-subtitle">Based on {{ results.monteCarloResult.successfulPaths }} Monte Carlo simulation paths</span>
          </div>
        </div>
      </div>

      <!-- Value Range Gauge -->
      <div class="value-gauge-container">
        <div class="gauge-header">
          <span class="gauge-label">Valuation Range</span>
          <span class="gauge-confidence">90% Confidence Interval</span>
        </div>
        
        <div class="gauge-wrapper">
          <!-- The gauge track -->
          <div class="gauge-track">
            <!-- Filled range between P5 and P95 -->
            <div class="gauge-fill" [style.left.%]="gaugeLeftPercent" [style.width.%]="gaugeWidthPercent"></div>
            
            <!-- Current price marker -->
            <div class="current-price-marker" 
                 [style.left.%]="currentPricePercent"
                 [class.outside-left]="currentPricePercent < 0"
                 [class.outside-right]="currentPricePercent > 100">
              <div class="marker-line"></div>
              <div class="marker-label">Current: {{ formatPrice(results.currentPrice) }}</div>
            </div>
          </div>
          
          <!-- Scale labels -->
          <div class="gauge-labels">
            <div class="gauge-point bear">
              <span class="point-value">{{ formatPrice(results.monteCarloResult.p5) }}</span>
              <span class="point-label">Bear Case</span>
              <span class="point-upside" [ngClass]="getUpsideClass(results.monteCarloResult.p5)">
                {{ getUpsidePercentage(results.monteCarloResult.p5) }}
              </span>
            </div>
            <div class="gauge-point median">
              <span class="point-value">{{ formatPrice(results.monteCarloResult.p50) }}</span>
              <span class="point-label">Median</span>
              <span class="point-upside" [ngClass]="getUpsideClass(results.monteCarloResult.p50)">
                {{ getUpsidePercentage(results.monteCarloResult.p50) }}
              </span>
            </div>
            <div class="gauge-point bull">
              <span class="point-value">{{ formatPrice(results.monteCarloResult.p95) }}</span>
              <span class="point-label">Bull Case</span>
              <span class="point-upside" [ngClass]="getUpsideClass(results.monteCarloResult.p95)">
                {{ getUpsidePercentage(results.monteCarloResult.p95) }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Parameter Insights -->
      <div class="parameters-section" *ngIf="parameterInsights.length > 0">
        <h4 class="parameters-title">
          <i class="pi pi-sliders-h"></i>
          Key Assumptions (ML-Predicted)
        </h4>
        
        <div class="parameters-grid">
          <div class="parameter-card" 
               *ngFor="let param of parameterInsights"
               [class.expanded]="param.expanded"
               (click)="toggleParameter(param)">
            <div class="parameter-header">
              <div class="param-icon-name">
                <i [class]="'pi ' + param.icon"></i>
                <span class="param-name">{{ param.name }}</span>
              </div>
              <div class="param-range">
                <span class="range-low">{{ formatParamValue(param.p5, param.unit) }}</span>
                <span class="range-separator">→</span>
                <span class="range-mid">{{ formatParamValue(param.p50, param.unit) }}</span>
                <span class="range-separator">→</span>
                <span class="range-high">{{ formatParamValue(param.p95, param.unit) }}</span>
              </div>
              <i class="pi" [class.pi-chevron-down]="!param.expanded" [class.pi-chevron-up]="param.expanded"></i>
            </div>
            
            <div class="parameter-detail" *ngIf="param.expanded && param.explanation">
              <div class="explanation-box">
                <i class="pi pi-info-circle"></i>
                <p>{{ param.explanation }}</p>
              </div>
            </div>
            <div class="parameter-detail" *ngIf="param.expanded && !param.explanation">
              <div class="explanation-box no-data">
                <i class="pi pi-info-circle"></i>
                <p>No detailed explanation available for this parameter.</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['./monte-carlo-section.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MonteCarloSection implements OnInit, OnChanges {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  public themeService = inject(ThemeService);
  currencyCtx: any = null;
  parameterInsights: ParameterInsight[] = [];

  // Gauge calculations
  gaugeLeftPercent = 0;
  gaugeWidthPercent = 100;
  currentPricePercent = 50;

  ngOnInit(): void {
    this.currencyCtx = createCurrencyContext(this.results.currency, this.results.stockCurrency || this.results.currency);
    this.setupData();
  }

  ngOnChanges(): void {
    if (this.results?.monteCarloResult) {
      this.setupData();
    }
  }

  private setupData(): void {
    if (!this.results?.monteCarloResult) return;

    this.calculateGaugePositions();
    this.buildParameterInsights();
  }

  private calculateGaugePositions(): void {
    const mc = this.results.monteCarloResult;
    if (!mc) return;

    const p5 = mc.p5;
    const p95 = mc.p95;
    const currentPrice = this.results.currentPrice;

    // Calculate the range for the gauge
    const minVal = Math.min(p5, currentPrice) * 0.9;
    const maxVal = Math.max(p95, currentPrice) * 1.1;
    const range = maxVal - minVal;

    // Position the fill bar
    this.gaugeLeftPercent = ((p5 - minVal) / range) * 100;
    this.gaugeWidthPercent = ((p95 - p5) / range) * 100;

    // Position current price marker
    this.currentPricePercent = ((currentPrice - minVal) / range) * 100;
  }

  private buildParameterInsights(): void {
    const mc = this.results.monteCarloResult;
    if (!mc) return;

    this.parameterInsights = [];

    // Revenue Growth
    if (mc.revenueGrowthDistributions?.length) {
      const d = mc.revenueGrowthDistributions[0];
      this.parameterInsights.push({
        name: 'Revenue Growth',
        icon: 'pi-chart-line',
        p5: d.p5,
        p50: d.p50,
        p95: d.p95,
        explanation: d.explanation || null,
        unit: '%',
        expanded: false
      });
    }

    // Operating Margin
    if (mc.operatingMarginDistributions?.length) {
      const d = mc.operatingMarginDistributions[0];
      this.parameterInsights.push({
        name: 'Operating Margin',
        icon: 'pi-percentage',
        p5: d.p5,
        p50: d.p50,
        p95: d.p95,
        explanation: d.explanation || null,
        unit: '%',
        expanded: false
      });
    }

    // Cost of Capital
    if (mc.costOfCapitalDistributions?.length) {
      const d = mc.costOfCapitalDistributions[0];
      this.parameterInsights.push({
        name: 'Cost of Capital (WACC)',
        icon: 'pi-dollar',
        p5: d.p5,
        p50: d.p50,
        p95: d.p95,
        explanation: d.explanation || null,
        unit: '%',
        expanded: false
      });
    }

    // Sales to Capital
    if (mc.salesToCapitalDistributions?.length) {
      const d = mc.salesToCapitalDistributions[0];
      this.parameterInsights.push({
        name: 'Sales/Capital Efficiency',
        icon: 'pi-sync',
        p5: d.p5,
        p50: d.p50,
        p95: d.p95,
        explanation: d.explanation || null,
        unit: 'x',
        expanded: false
      });
    }
  }

  toggleParameter(param: ParameterInsight): void {
    param.expanded = !param.expanded;
  }

  formatPrice(price: number): string {
    return this.currencyCtx?.formatPrice(price) || formatPrice(price);
  }

  formatParamValue(value: number, unit: string): string {
    if (unit === '%') {
      return value.toFixed(1) + '%';
    } else if (unit === 'x') {
      return value.toFixed(2) + 'x';
    }
    return value.toFixed(2);
  }

  getUpsidePercentage(value: number): string {
    const currentPrice = this.results.currentPrice || 0;
    if (currentPrice === 0) return '0%';
    const upside = ((value / currentPrice) - 1) * 100;
    return (upside > 0 ? '+' : '') + upside.toFixed(0) + '%';
  }

  getUpsideClass(value: number): string {
    const upside = ((value / (this.results.currentPrice || 1)) - 1) * 100;
    if (upside > 15) return 'upside-strong';
    if (upside > 0) return 'upside';
    if (upside < -15) return 'downside-strong';
    return 'downside';
  }
}
