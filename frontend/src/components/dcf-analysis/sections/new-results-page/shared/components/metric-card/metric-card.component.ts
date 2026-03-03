import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface MetricCardData {
  title: string;
  value: string | number;
  subtitle?: string;
  trend?: 'up' | 'down' | 'neutral';
  trendValue?: string;
  status?: 'positive' | 'negative' | 'neutral' | 'warning';
  format?: 'currency' | 'percentage' | 'number' | 'text';
  precision?: number;
  icon?: string;
  isLoading?: boolean;
}

@Component({
    selector: 'app-metric-card',
    imports: [CommonModule],
    template: `
    <div class="metric-card" [class]="getCardClasses()">
      <div class="metric-header">
        <div class="metric-title">
          <i *ngIf="data.icon" [class]="'pi pi-' + data.icon + ' metric-icon'"></i>
          {{ data.title }}
        </div>
        <div *ngIf="data.trend" class="metric-trend" [class]="'trend-' + data.trend">
          <i [class]="getTrendIcon()"></i>
          <span *ngIf="data.trendValue">{{ data.trendValue }}</span>
        </div>
      </div>

      <div class="metric-content">
        <div *ngIf="!data.isLoading" class="metric-value">
          {{ getFormattedValue() }}
        </div>
        <div *ngIf="data.isLoading" class="metric-loading">
          <div class="loading-skeleton"></div>
        </div>
        
        <div *ngIf="data.subtitle && !data.isLoading" class="metric-subtitle">
          {{ data.subtitle }}
        </div>
      </div>
    </div>
  `,
    styleUrls: ['./metric-card.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class MetricCardComponent {
  @Input() data!: MetricCardData;
  @Input() size: 'small' | 'medium' | 'large' = 'medium';

  getCardClasses(): string {
    const classes = [`size-${this.size}`];
    if (this.data.status) {
      classes.push(`status-${this.data.status}`);
    }
    if (this.data.isLoading) {
      classes.push('loading');
    }
    return classes.join(' ');
  }

  getTrendIcon(): string {
    switch (this.data.trend) {
      case 'up': return 'pi pi-trending-up';
      case 'down': return 'pi pi-trending-down';
      default: return 'pi pi-minus';
    }
  }

  getFormattedValue(): string {
    if (typeof this.data.value === 'string') {
      return this.data.value;
    }

    const precision = this.data.precision ?? 2;
    const value = this.data.value as number;

    switch (this.data.format) {
      case 'currency':
        return this.formatCurrency(value, precision);
      case 'percentage':
        return `${value.toFixed(precision)}%`;
      case 'number':
        return this.formatNumber(value, precision);
      default:
        return value.toString();
    }
  }

  private formatCurrency(value: number, precision: number): string {
    if (value >= 1e9) {
      return `$${(value / 1e9).toFixed(precision)}B`;
    } else if (value >= 1e6) {
      return `$${(value / 1e6).toFixed(precision)}M`;
    } else if (value >= 1e3) {
      return `$${(value / 1e3).toFixed(precision)}K`;
    }
    return `$${value.toFixed(precision)}`;
  }

  private formatNumber(value: number, precision: number): string {
    if (value >= 1e9) {
      return `${(value / 1e9).toFixed(precision)}B`;
    } else if (value >= 1e6) {
      return `${(value / 1e6).toFixed(precision)}M`;
    } else if (value >= 1e3) {
      return `${(value / 1e3).toFixed(precision)}K`;
    }
    return value.toFixed(precision);
  }
}