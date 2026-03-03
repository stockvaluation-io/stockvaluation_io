import { Component, Input, ChangeDetectionStrategy, OnInit, OnChanges, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { StoryCard } from '../../shared/components';
import { ChartWrapperComponent, ChartData } from '../../shared/components';
import { createCurrencyContext, formatPrice } from '../../../../utils/formatting.utils';
import { ThemeService } from '../../../../../../core/services';

@Component({
    selector: 'app-valuation-overview-section',
    imports: [CommonModule, ChartWrapperComponent],
    template: `
    <div class="valuation-overview-section">
      <h3 class="section-title">
        <i class="pi pi-chart-line section-icon"></i>
        Valuation Overview
      </h3>
      
      <!-- Story-Driven Content Layout -->
      <div class="story-content-layout">
        
        <!-- Valuation Overview Story Cards -->
        <div class="valuation-overview-stories">
        
        <div class="story-cards-grid">
          <!-- Growth Story Card -->
          <div class="story-card-compact" *ngIf="results.growthStory">
            <div class="story-content-top">
              <div class="story-header">
                <i class="pi pi-chart-line story-icon"></i>
                <h4 class="story-title">Growth Story</h4>
              </div>
              <p class="story-text">{{ results.growthStory }}</p>
            </div>
            <div class="story-chart-bottom">
              <app-chart-wrapper
                type="line"
                [chartData]="growthStoryChartData"
                title="Growth Trajectory"
                size="small"
                [currency]="results.currency"
                [isPercentageChart]="true"
                [theme]="themeService.currentTheme()">
              </app-chart-wrapper>
            </div>
          </div>

          <!-- Profitability Story Card -->
          <div class="story-card-compact" *ngIf="results.profitabilityStory">
            <div class="story-content-top">
              <div class="story-header">
                <i class="pi pi-percentage story-icon"></i>
                <h4 class="story-title">Profitability</h4>
              </div>
              <p class="story-text">{{ results.profitabilityStory }}</p>
            </div>
            <div class="story-chart-bottom">
              <app-chart-wrapper
                type="line"
                [chartData]="profitabilityStoryChartData"
                title="Profitability Trajectory"
                size="small"
                [currency]="results.currency"
                [isPercentageChart]="true"
                [theme]="themeService.currentTheme()">
              </app-chart-wrapper>
            </div>
          </div>

          <!-- Risk Story Card -->
          <div class="story-card-compact" *ngIf="results.riskStory">
            <div class="story-content-top">
              <div class="story-header">
                <i class="pi pi-shield story-icon"></i>
                <h4 class="story-title">Risk Profile</h4>
              </div>
              <p class="story-text">{{ results.riskStory }}</p>
            </div>
            <div class="story-chart-bottom">
              <app-chart-wrapper
                type="line"
                [chartData]="riskStoryChartData"
                title="Risk vs Return"
                size="small"
                [currency]="results.currency"
                [isPercentageChart]="true"
                [theme]="themeService.currentTheme()">
              </app-chart-wrapper>
            </div>
          </div>

          <!-- Competitive Advantages Card -->
          <div class="story-card-compact" *ngIf="results.competitiveAdvantages">
            <div class="story-content-top">
              <div class="story-header">
                <i class="pi pi-eye story-icon"></i>
                <h4 class="story-title">Market Expectations</h4>
              </div>
              <p class="story-text">{{ results.competitiveAdvantages }}</p>
            </div>
            <div class="story-chart-bottom">
              <app-chart-wrapper
                type="bar"
                [chartData]="competitiveAdvantagesChartData"
                title="Market Expectations vs Reality"
                size="small"
                [currency]="results.currency"
                [isPercentageChart]="true"
                [theme]="themeService.currentTheme()">
              </app-chart-wrapper>
            </div>
          </div>
        </div>
      </div>


    </div>
  `,
    styleUrls: ['./valuation-overview.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValuationOverviewSection implements OnInit, OnChanges {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  // Inject ThemeService
  public themeService = inject(ThemeService);

  // Chart data properties to avoid re-execution during change detection
  growthStoryChartData!: ChartData;
  profitabilityStoryChartData!: ChartData;
  riskStoryChartData!: ChartData;
  competitiveAdvantagesChartData!: ChartData;
  
  // Currency formatting context
  currencyCtx: ReturnType<typeof createCurrencyContext> | null = null;

  ngOnInit(): void {
    // Initialize currency context
    this.currencyCtx = createCurrencyContext(this.results.currency, this.results.stockCurrency);
    
    // Initialize chart data properties once
    this.setupChartData();
  }

  ngOnChanges(): void {
    // Regenerate chart data when inputs change (including theme changes)
    if (this.results && this.company) {
      this.setupChartData();
    }
  }

  private setupChartData(): void {
    this.growthStoryChartData = this.generateGrowthStoryChartData();
    this.profitabilityStoryChartData = this.generateProfitabilityStoryChartData();
    this.riskStoryChartData = this.generateRiskStoryChartData();
    this.competitiveAdvantagesChartData = this.generateCompetitiveAdvantagesChartData();
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

  Math = Math;

  get storyCards(): StoryCard[] {
    const cards: StoryCard[] = [];
    
    if (this.results.growthStory) {
      cards.push({
        name: 'Growth',
        content: this.results.growthStory
      });
    }
    
    if (this.results.profitabilityStory) {
      cards.push({
        name: 'Profitability',
        content: this.results.profitabilityStory
      });
    }
    
    if (this.results.riskStory) {
      cards.push({
        name: 'Risk',
        content: this.results.riskStory
      });
    }
    
    if (this.results.competitiveAdvantages) {
      cards.push({
        name: 'Market Expectation',
        content: this.results.competitiveAdvantages
      });
    }
    
    return cards;
  }



  // New methods for value analysis
  getValueInsight(): string {
    const upside = this.results.upside;
    
    if (upside >= 20) {
      return 'Our analysis suggests this stock may be significantly undervalued by the market';
    } else if (upside >= 10) {
      return 'The stock appears to be trading below its calculated fair value';
    } else if (upside >= 0) {
      return 'The current price is close to our calculated fair value';
    } else if (upside >= -10) {
      return 'The stock appears to be trading slightly above its calculated fair value';
    } else {
      return 'Our analysis suggests this stock may be overvalued by the market';
    }
  }

  formatPrice(price: number): string {
    return this.currencyCtx?.formatPrice(price) || formatPrice(price);
  }

  formatLargeNumber(value: number): string {
    if (value >= 1e12) return (value / 1e12).toFixed(2) + 'T';
    if (value >= 1e9) return (value / 1e9).toFixed(2) + 'B';
    if (value >= 1e6) return (value / 1e6).toFixed(2) + 'M';
    if (value >= 1e3) return (value / 1e3).toFixed(2) + 'K';
    return value.toFixed(2);
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


  getUpsideClass(): string {
    return this.results.upside >= 0 ? 'positive' : 'negative';
  }

  formatShares(value: number): string {
    if (value >= 1e9) {
      return `${(value / 1e9).toFixed(2)}B`;
    } else if (value >= 1e6) {
      return `${(value / 1e6).toFixed(2)}M`;
    } else if (value >= 1e3) {
      return `${(value / 1e3).toFixed(2)}K`;
    }
    return value.toFixed(2);
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
    
    return currencySymbols[this.results.currency] || this.results.currency || '$';
  }

  // Chart data methods for story visualization
  generateGrowthStoryChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);
    // API values are already in percentage form, no need to multiply by 100
    const companyGrowthRates = this.results.projections.map(p => p.revenue_growth_rate || 0);

    return {
      labels: years,
      datasets: [
        {
          label: `${this.company.name} Growth Rate (%)`,
          data: companyGrowthRates,
          borderColor: this.getThemeColors().primary,
          backgroundColor: this.getThemeColors().primary,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };
  }

  generateProfitabilityStoryChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);
    // API values are already in percentage form, no need to multiply by 100
    const companyOperatingMargins = this.results.projections.map(p => p.operating_margin || 0);

    return {
      labels: years,
      datasets: [
        {
          label: `${this.company.name} Operating Margin (%)`,
          data: companyOperatingMargins,
          borderColor: this.getThemeColors().warning,
          backgroundColor: this.getThemeColors().warning,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };
  }

  generateRiskStoryChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);
    // API values are already in percentage form, no need to multiply by 100
    const costOfCapital = this.results.projections.map(p => p.cost_of_capital || 0);
    const returnOnCapital = this.results.projections.map(p => p.roic || 0);

    return {
      labels: years,
      datasets: [
        {
          label: 'Required Return (Cost of Capital) (%)',
          data: costOfCapital,
          borderColor: this.getThemeColors().danger,
          backgroundColor: this.getThemeColors().danger,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        },
        {
          label: 'Return on Invested Capital (%)',
          data: returnOnCapital,
          borderColor: this.getThemeColors().secondary,
          backgroundColor: this.getThemeColors().secondary,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };
  }

  generateCompetitiveAdvantagesChartData(): ChartData {
    // Show market expectations vs company projections
    const metrics = ['Revenue Growth', 'Operating Margin'];
    
    // Market expectations from calibration (what market prices in)
    const marketExpectedGrowth = this.results.calibrationGrowth || 0;
    const marketExpectedMargin = this.results.calibrationMargin || 0;
    
    // Company's actual projections (average or first year)
    const actualGrowth = this.results.projections && this.results.projections.length > 0 
      ? (this.results.projections[1]?.revenue_growth_rate || 0) // First projection year
      : 0;
    const actualMargin = this.results.operatingMarginCompany || 0;

    return {
      labels: metrics,
      datasets: [
        {
          label: 'Market Expectations',
          data: [marketExpectedGrowth, marketExpectedMargin],
          backgroundColor: this.getThemeColors().danger,
          borderColor: this.getThemeColors().danger,
          borderWidth: 1
        },
        {
          label: `${this.company.name} Projections`,
          data: [actualGrowth, actualMargin],
          backgroundColor: this.getThemeColors().primary,
          borderColor: this.getThemeColors().primary,
          borderWidth: 1
        }
      ]
    };
  }

}