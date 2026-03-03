import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FinancialMetric } from './message-formatter.service';

@Component({
  selector: 'app-metric-viz',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="metric-viz" [class]="'metric-viz-' + metric.visualType">
      @switch (metric.visualType) {
        @case ('growth-arrow') {
          <div class="growth-viz">
            <div class="metric-label">{{ metric.label }}</div>
            <div class="metric-value-container">
              <span class="metric-value">{{ metric.value }}</span>
              @if (metric.change !== undefined) {
                <span class="growth-badge" [class]="'badge-' + metric.color">
                  <i class="pi" [class.pi-arrow-up]="metric.change! > 0" 
                     [class.pi-arrow-down]="metric.change! < 0"></i>
                  {{ metric.change! > 0 ? '+' : '' }}{{ metric.change!.toFixed(1) }}%
                </span>
              }
            </div>
          </div>
        }
        
        @case ('comparison-bar') {
          <div class="comparison-viz">
            <div class="metric-label">{{ metric.label }}</div>
            @if (metric.comparison) {
              <div class="comparison-text">{{ metric.comparison }}</div>
            }
            <div class="comparison-bar-container">
              <div class="comparison-bar" 
                   [class]="'bar-' + metric.color"
                   [style.width.%]="getBarWidth(metric.change)">
                <span class="bar-label">{{ metric.value }}</span>
              </div>
            </div>
          </div>
        }
        
        @case ('badge') {
          <div class="badge-viz">
            <span class="badge-label">{{ metric.label }}</span>
            <span class="badge-value" [class]="'badge-' + metric.color">
              {{ metric.value }}
            </span>
          </div>
        }
        
        @default {
          <div class="simple-metric">
            <span class="metric-label">{{ metric.label }}:</span>
            <span class="metric-value" [class]="'text-' + metric.color">{{ metric.value }}</span>
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .metric-viz {
      display: inline-flex;
      align-items: center;
      margin: 0.5rem 0.75rem 0.5rem 0;
      padding: 0.5rem 0.75rem;
      background: rgba(16, 185, 129, 0.05);
      border-radius: 8px;
      border: 1px solid rgba(16, 185, 129, 0.1);
      font-size: 0.875rem;
      
      .chat-ui-container[data-theme="dark"] & {
        background: rgba(16, 185, 129, 0.08);
        border-color: rgba(16, 185, 129, 0.15);
      }
    }
    
    .metric-label {
      font-weight: 600;
      color: #6b7280;
      margin-right: 0.5rem;
      font-size: 0.8125rem;
      
      .chat-ui-container[data-theme="dark"] & {
        color: #9ca3af;
      }
    }
    
    .metric-value {
      font-weight: 700;
      color: #10b981;
      font-size: 0.9375rem;
    }
    
    // Growth Arrow Visualization
    .growth-viz {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      
      .metric-value-container {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      
      .growth-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.125rem 0.5rem;
        border-radius: 12px;
        font-size: 0.75rem;
        font-weight: 700;
        
        &.badge-success {
          background: #d1fae5;
          color: #065f46;
          
          .chat-ui-container[data-theme="dark"] & {
            background: rgba(16, 185, 129, 0.2);
            color: #10b981;
          }
        }
        
        &.badge-danger {
          background: #fee2e2;
          color: #991b1b;
          
          .chat-ui-container[data-theme="dark"] & {
            background: rgba(239, 68, 68, 0.2);
            color: #ef4444;
          }
        }
        
        &.badge-info {
          background: #dbeafe;
          color: #1e3a8a;
          
          .chat-ui-container[data-theme="dark"] & {
            background: rgba(59, 130, 246, 0.2);
            color: #60a5fa;
          }
        }
        
        i {
          font-size: 0.625rem;
        }
      }
    }
    
    // Comparison Bar Visualization
    .comparison-viz {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      width: 100%;
      
      .comparison-text {
        font-size: 0.75rem;
        color: #6b7280;
        
        .chat-ui-container[data-theme="dark"] & {
          color: #9ca3af;
        }
      }
      
      .comparison-bar-container {
        width: 100%;
        height: 24px;
        background: #f3f4f6;
        border-radius: 4px;
        overflow: hidden;
        position: relative;
        
        .chat-ui-container[data-theme="dark"] & {
          background: #2d2d2d;
        }
      }
      
      .comparison-bar {
        height: 100%;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
        border-radius: 4px;
        min-width: 60px;
        
        &.bar-success {
          background: linear-gradient(90deg, #10b981, #059669);
        }
        
        &.bar-danger {
          background: linear-gradient(90deg, #ef4444, #dc2626);
        }
        
        &.bar-warning {
          background: linear-gradient(90deg, #f59e0b, #d97706);
        }
        
        &.bar-info {
          background: linear-gradient(90deg, #3b82f6, #2563eb);
        }
        
        .bar-label {
          color: white;
          font-weight: 700;
          font-size: 0.75rem;
        }
      }
    }
    
    // Badge Visualization
    .badge-viz {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      
      .badge-label {
        font-size: 0.8125rem;
        color: #6b7280;
        
        .chat-ui-container[data-theme="dark"] & {
          color: #9ca3af;
        }
      }
      
      .badge-value {
        padding: 0.25rem 0.75rem;
        border-radius: 12px;
        font-weight: 700;
        font-size: 0.875rem;
        
        &.badge-success {
          background: #d1fae5;
          color: #065f46;
          
          .chat-ui-container[data-theme="dark"] & {
            background: rgba(16, 185, 129, 0.2);
            color: #10b981;
          }
        }
        
        &.badge-info {
          background: #dbeafe;
          color: #1e3a8a;
          
          .chat-ui-container[data-theme="dark"] & {
            background: rgba(59, 130, 246, 0.2);
            color: #60a5fa;
          }
        }
        
        &.badge-warning {
          background: #fef3c7;
          color: #92400e;
          
          .chat-ui-container[data-theme="dark"] & {
            background: rgba(245, 158, 11, 0.2);
            color: #fbbf24;
          }
        }
      }
    }
    
    // Simple Metric
    .simple-metric {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      
      .text-success { color: #10b981; }
      .text-danger { color: #ef4444; }
      .text-warning { color: #f59e0b; }
      .text-info { color: #3b82f6; }
    }
    
    @media (max-width: 640px) {
      .metric-viz {
        width: 100%;
        margin: 0.25rem 0;
      }
      
      .comparison-viz {
        width: 100%;
      }
    }
  `]
})
export class MetricVisualizationComponent {
  @Input() metric!: FinancialMetric;
  
  getBarWidth(change: number | undefined): number {
    if (change === undefined) return 50;
    
    // Map change to 0-100% range
    // -50% to +50% → 0% to 100%
    const normalized = Math.max(0, Math.min(100, (change + 50)));
    return normalized;
  }
}

