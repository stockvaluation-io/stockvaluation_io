import { Component, Input, Output, EventEmitter, OnInit, OnDestroy, OnChanges, ChangeDetectionStrategy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ChartModule } from 'primeng/chart';

export interface ChartData {
  labels: string[];
  datasets: ChartDataset[];
}

export interface ChartDataset {
  label: string;
  data: number[];
  backgroundColor?: string | string[];
  borderColor?: string;
  borderWidth?: number;
  borderDash?: number[];
  fill?: boolean;
  tension?: number;
  type?: string;
}

export interface ChartOptions {
  responsive?: boolean;
  maintainAspectRatio?: boolean;
  plugins?: any;
  scales?: any;
  interaction?: any;
  elements?: any;
  layout?: any;
}

export type ChartType = 'line' | 'bar' | 'doughnut' | 'pie' | 'radar' | 'scatter';

@Component({
    selector: 'app-chart-wrapper',
    imports: [CommonModule, ChartModule],
    template: `
    <div class="chart-container" [class]="getContainerClasses()">
      <div *ngIf="title" class="chart-header">
        <h3 class="chart-title">{{ title }}</h3>
        <p *ngIf="subtitle" class="chart-subtitle">{{ subtitle }}</p>
      </div>

      <div class="chart-content" #chartContainer>
        <p-chart 
          *ngIf="!isLoading && chartData && chartData.datasets?.length"
          [type]="type"
          [data]="chartData"
          [options]="chartOptions"
          [width]="width"
          [height]="height"
          (onDataSelect)="onDataSelect($event)">
        </p-chart>

        <div *ngIf="isLoading" class="chart-loading">
          <div class="loading-content">
            <i class="pi pi-spin pi-spinner"></i>
            <span>Loading chart data...</span>
          </div>
        </div>

        <div *ngIf="!isLoading && (!chartData || !chartData.datasets?.length)" class="chart-empty">
          <i class="pi pi-chart-line"></i>
          <p>No data available for chart</p>
        </div>
      </div>

      <div *ngIf="showLegend && chartData && chartData.datasets && chartData.datasets.length > 1" class="chart-legend">
        <div class="legend-items">
          <div *ngFor="let dataset of chartData.datasets; let i = index" 
               class="legend-item"
               (click)="onLegendClick(i)">
            <div class="legend-color" [style.background-color]="getLegendColor(dataset, i)"></div>
            <span class="legend-label">{{ dataset.label }}</span>
          </div>
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./chart-wrapper.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartWrapperComponent implements OnInit, OnDestroy, OnChanges {
  @ViewChild('chartContainer', { static: false }) chartContainer!: ElementRef;

  @Input() type: ChartType = 'line';
  @Input() chartData!: ChartData;
  @Input() title?: string;
  @Input() subtitle?: string;
  @Input() width?: string;
  @Input() height?: string;
  @Input() isLoading = false;
  @Input() showLegend = true;
  @Input() theme: 'light' | 'dark' = 'dark';
  @Input() size: 'small' | 'medium' | 'large' = 'medium';
  @Input() currency?: string = 'USD'; // Currency for value formatting
  @Input() isPercentageChart = false; // Whether this chart shows percentage values

  @Output() dataSelect = new EventEmitter<any>();
  @Output() legendClick = new EventEmitter<number>();

  chartOptions: ChartOptions = {};

  ngOnInit(): void {
    this.setupChartOptions();
  }

  ngOnChanges(): void {
    // Reconfigure chart options when theme changes
    this.setupChartOptions();
  }

  ngOnDestroy(): void {
    // Cleanup if needed
  }

  getContainerClasses(): string {
    const classes = [`size-${this.size}`, `theme-${this.theme}`];
    if (this.isLoading) classes.push('loading');
    return classes.join(' ');
  }

  onDataSelect(event: any): void {
    this.dataSelect.emit(event);
  }

  onLegendClick(datasetIndex: number): void {
    this.legendClick.emit(datasetIndex);
  }

  getLegendColor(dataset: ChartDataset, index: number): string {
    if (dataset.backgroundColor && typeof dataset.backgroundColor === 'string') {
      return dataset.backgroundColor;
    }
    if (dataset.borderColor) {
      return dataset.borderColor;
    }
    return this.getDefaultColor(index);
  }

  private setupChartOptions(): void {
    const isDark = this.theme === 'dark';
    // Enhanced colors for better dark theme contrast
    const textColor = isDark ? '#E5E7EB' : '#374151';
    const gridColor = isDark ? 'rgba(107, 114, 128, 0.3)' : 'rgba(209, 213, 219, 0.8)';
    const tooltipBg = isDark ? '#1F2937' : '#FFFFFF';
    const tooltipBorder = isDark ? '#4B5563' : '#D1D5DB';

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      layout: {
        padding: {
          bottom: 10, // Add bottom padding to prevent label cutoff
          top: 5,
          left: 5,
          right: 5
        }
      },
      interaction: {
        intersect: false,
        mode: 'index'
      },
      plugins: {
        legend: {
          display: false // We use custom legend
        },
        tooltip: {
          backgroundColor: tooltipBg,
          titleColor: textColor,
          bodyColor: textColor,
          borderColor: tooltipBorder,
          borderWidth: 1,
          cornerRadius: 8,
          displayColors: true,
          callbacks: {
            title: (tooltipItems: any[]) => {
              return tooltipItems[0]?.label || '';
            },
            label: (context: any) => {
              const label = context.dataset.label || '';
              const value = this.formatTooltipValue(context.parsed.y || context.parsed);
              return `${label}: ${value}`;
            }
          }
        }
      },
      scales: this.getScalesConfig(textColor, gridColor),
      elements: {
        point: {
          radius: 4,
          hoverRadius: 6
        },
        line: {
          tension: 0.2
        }
      }
    };
  }

  private getScalesConfig(textColor: string, gridColor: string): any {
    const baseScale = {
      ticks: {
        color: textColor,
        font: {
          size: 11
        },
        maxRotation: 0, // Keep labels horizontal
        minRotation: 0
      },
      grid: {
        color: gridColor,
        lineWidth: 1
      }
    };

    if (this.type === 'doughnut' || this.type === 'pie' || this.type === 'radar') {
      return {}; // These chart types don't use x/y scales
    }

    return {
      x: {
        ...baseScale,
        title: {
          display: false
        },
        ticks: {
          ...baseScale.ticks,
          padding: 8 // Add padding to prevent cutoff
        }
      },
      y: {
        ...baseScale,
        title: {
          display: false
        },
        ticks: {
          ...baseScale.ticks,
          callback: (value: any) => this.formatAxisValue(value),
          padding: 8
        }
      }
    };
  }

  private formatTooltipValue(value: number): string {
    // For percentage charts, format as percentage without currency symbol
    if (this.isPercentageChart) {
      return `${(Math.floor(value * 100) / 100).toFixed(2)}%`;
    }
    
    // For currency charts, use currency symbol
    const currencySymbol = this.getCurrencySymbol();
    if (value >= 1e9) {
      return `${currencySymbol}${(Math.floor((value / 1e9) * 100) / 100).toFixed(2)}B`;
    } else if (value >= 1e6) {
      return `${currencySymbol}${(Math.floor((value / 1e6) * 100) / 100).toFixed(2)}M`;
    } else if (value >= 1e3) {
      return `${currencySymbol}${(Math.floor((value / 1e3) * 100) / 100).toFixed(2)}K`;
    }
    return `${currencySymbol}${(Math.floor(value * 100) / 100).toFixed(2)}`;
  }

  private formatAxisValue(value: number): string {
    // For percentage charts, format as percentage
    if (this.isPercentageChart) {
      return `${(Math.floor(value * 100) / 100).toFixed(1)}%`;
    }
    
    // For currency charts, use compact number format
    if (value >= 1e9) {
      return `${(Math.floor((value / 1e9) * 100) / 100).toFixed(2)}B`;
    } else if (value >= 1e6) {
      return `${(Math.floor((value / 1e6) * 100) / 100).toFixed(2)}M`;
    } else if (value >= 1e3) {
      return `${(Math.floor((value / 1e3) * 100) / 100).toFixed(2)}K`;
    }
    return (Math.floor(value * 100) / 100).toFixed(2);
  }

  private getDefaultColor(index: number): string {
    const isDark = this.theme === 'dark';
    const colors = isDark ? [
      '#3b82f6', // blue
      '#10b981', // green
      '#f59e0b', // yellow
      '#ef4444', // red
      '#8b5cf6', // purple
      '#06b6d4', // cyan
      '#f97316', // orange
      '#84cc16'  // lime
    ] : [
      '#2563eb', // darker blue for light theme
      '#059669', // darker green for light theme
      '#d97706', // darker yellow for light theme
      '#dc2626', // darker red for light theme
      '#7c3aed', // darker purple for light theme
      '#0891b2', // darker cyan for light theme
      '#ea580c', // darker orange for light theme
      '#65a30d'  // darker lime for light theme
    ];
    return colors[index % colors.length];
  }

  private getCurrencySymbol(): string {
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
    
    return currencySymbols[this.currency || 'USD'] || this.currency || '$';
  }

}