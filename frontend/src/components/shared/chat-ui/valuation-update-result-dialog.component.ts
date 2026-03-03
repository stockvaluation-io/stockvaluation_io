import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';

export interface ValuationUpdateResult {
  ticker: string;
  parameters_changed: { [key: string]: any };
  previous_intrinsic_value: number;
  new_intrinsic_value: number;
  change_percent: number;
  impact_summary: string;
  user_intent?: string;
}

@Component({
  selector: 'app-valuation-update-result-dialog',
  standalone: true,
  imports: [CommonModule, DialogModule, ButtonModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [closable]="true"
      [style]="{width: '650px', maxWidth: '95vw'}"
      [dismissableMask]="true"
      styleClass="valuation-result-dialog"
      (onHide)="onClose()">
      
      <ng-template pTemplate="header">
        <div class="dialog-header">
          <div class="header-icon success">
            <i class="pi pi-check-circle"></i>
          </div>
          <div class="header-content">
            <h3 class="dialog-title">Valuation Updated Successfully</h3>
            <p class="dialog-subtitle">{{ result?.ticker }} • DCF Model Recalculated</p>
          </div>
        </div>
      </ng-template>
      
      <div class="result-content" *ngIf="result">
        
        <!-- User Intent Section -->
        <div class="intent-section" *ngIf="result.user_intent">
          <div class="section-header-small">
            <i class="pi pi-comment"></i>
            <span>Your Intent</span>
          </div>
          <p class="intent-text">{{ result.user_intent }}</p>
        </div>
        
        <!-- Parameters Changed Section -->
        <div class="parameters-section">
          <div class="section-header-small">
            <i class="pi pi-sliders-h"></i>
            <span>Parameters Changed</span>
          </div>
          <div class="parameter-list">
            <div class="parameter-item" *ngFor="let param of getParametersList()">
              <span class="param-label">{{ param.label }}:</span>
              <span class="param-value">{{ param.value }}</span>
            </div>
          </div>
        </div>
        
        <!-- Valuation Impact Section -->
        <div class="impact-section">
          <div class="section-header-small">
            <i class="pi pi-chart-line"></i>
            <span>Valuation Impact</span>
          </div>
          
          <div class="impact-metrics">
            <div class="metric-row">
              <div class="metric-item previous">
                <span class="metric-label">Previous Intrinsic Value</span>
                <span class="metric-value">{{ result.previous_intrinsic_value | currency }}</span>
              </div>
              <div class="metric-arrow">
                <i class="pi" [ngClass]="result.change_percent >= 0 ? 'pi-arrow-right' : 'pi-arrow-down'"></i>
              </div>
              <div class="metric-item new">
                <span class="metric-label">New Intrinsic Value</span>
                <span class="metric-value highlight">{{ result.new_intrinsic_value | currency }}</span>
              </div>
            </div>
            
            <div class="change-badge" [class.positive]="result.change_percent >= 0" [class.negative]="result.change_percent < 0">
              <i class="pi" [ngClass]="result.change_percent >= 0 ? 'pi-arrow-up' : 'pi-arrow-down'"></i>
              <span>{{ result.change_percent >= 0 ? '+' : '' }}{{ result.change_percent.toFixed(1) }}%</span>
            </div>
          </div>
          
          <p class="impact-summary" *ngIf="result.impact_summary">
            {{ result.impact_summary }}
          </p>
        </div>
        
      </div>
      
      <ng-template pTemplate="footer">
        <div class="dialog-footer-enhanced">
          <div class="footer-right">
            <button
              pButton
              type="button"
              label="Close"
              severity="secondary"
              [outlined]="true"
              (click)="onClose()">
            </button>
          </div>
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    @use '../../../styles/_design-system-v2' as ds;
    
    :host ::ng-deep .valuation-result-dialog {
      .p-dialog-header {
        padding: 0;
        background: transparent;
        border-bottom: none;
      }
      
      .p-dialog-content {
        padding: 0 2rem 1.5rem 2rem;
        background: var(--color-bg-primary);
      }
      
      .p-dialog-footer {
        padding: 1.5rem 2rem;
        background: var(--color-bg-primary);
        border-top: 1px solid var(--color-border);
      }
    }
    
    // Header Styling
    .dialog-header {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 2rem 2rem 1.5rem 2rem;
      background: var(--color-bg-primary);
      border-bottom: 1px solid var(--color-border);
      
      .header-icon {
        width: 48px;
        height: 48px;
        border-radius: 12px;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 24px;
        flex-shrink: 0;
        
        &.success {
          background: linear-gradient(135deg, #10b981 0%, #059669 100%);
          color: white;
        }
      }
      
      .header-content {
        flex: 1;
        
        .dialog-title {
          margin: 0 0 0.25rem 0;
          font-size: 1.25rem;
          font-weight: 600;
          color: var(--color-text-primary);
          line-height: 1.4;
        }
        
        .dialog-subtitle {
          margin: 0;
          font-size: 0.875rem;
          color: var(--color-text-secondary);
          font-weight: 500;
        }
      }
    }
    
    // Content Sections
    .result-content {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    
    .section-header-small {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      font-weight: 600;
      text-transform: uppercase;
      color: var(--color-text-secondary);
      margin-bottom: 0.75rem;
      letter-spacing: 0.5px;
      
      i {
        font-size: 1rem;
        color: var(--color-primary);
      }
    }
    
    // Intent Section
    .intent-section {
      padding: 1rem;
      background: var(--color-bg-secondary);
      border-radius: 8px;
      border-left: 3px solid var(--color-primary);
      
      .intent-text {
        margin: 0;
        font-size: 0.9375rem;
        line-height: 1.6;
        color: var(--color-text-primary);
        font-style: italic;
      }
    }
    
    // Parameters Section
    .parameters-section {
      .parameter-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      
      .parameter-item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.75rem 1rem;
        background: var(--color-bg-secondary);
        border-radius: 6px;
        border: 1px solid var(--color-border);
        transition: all 0.2s ease;
        
        &:hover {
          background: var(--color-bg-tertiary);
          border-color: var(--color-primary-muted);
        }
        
        .param-label {
          font-size: 0.875rem;
          color: var(--color-text-secondary);
          font-weight: 500;
        }
        
        .param-value {
          font-size: 0.9375rem;
          font-weight: 600;
          color: var(--color-text-primary);
          font-family: ui-monospace, 'SF Mono', 'Monaco', 'Menlo', monospace;
        }
      }
    }
    
    // Impact Section
    .impact-section {
      .impact-metrics {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        padding: 1.25rem;
        background: var(--color-bg-secondary);
        border-radius: 8px;
        border: 1px solid var(--color-border);
      }
      
      .metric-row {
        display: flex;
        align-items: center;
        gap: 1rem;
        
        @include ds.mobile {
          flex-direction: column;
          gap: 0.75rem;
        }
      }
      
      .metric-item {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        
        .metric-label {
          font-size: 0.75rem;
          text-transform: uppercase;
          letter-spacing: 0.5px;
          color: var(--color-text-secondary);
          font-weight: 600;
        }
        
        .metric-value {
          font-size: 1.25rem;
          font-weight: 700;
          color: var(--color-text-primary);
          font-family: ui-monospace, 'SF Mono', 'Monaco', 'Menlo', monospace;
          
          &.highlight {
            color: var(--color-primary);
          }
        }
        
        &.previous {
          .metric-value {
            opacity: 0.7;
          }
        }
      }
      
      .metric-arrow {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        background: var(--color-bg-tertiary);
        border-radius: 50%;
        flex-shrink: 0;
        
        i {
          font-size: 1rem;
          color: var(--color-text-secondary);
        }
        
        @include ds.mobile {
          transform: rotate(90deg);
        }
      }
      
      .change-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.5rem 0.875rem;
        border-radius: 20px;
        font-size: 0.875rem;
        font-weight: 700;
        font-family: ui-monospace, 'SF Mono', 'Monaco', 'Menlo', monospace;
        align-self: flex-start;
        
        i {
          font-size: 0.75rem;
        }
        
        &.positive {
          background: rgba(16, 185, 129, 0.1);
          color: #10b981;
          border: 1px solid rgba(16, 185, 129, 0.2);
        }
        
        &.negative {
          background: rgba(239, 68, 68, 0.1);
          color: #ef4444;
          border: 1px solid rgba(239, 68, 68, 0.2);
        }
      }
      
      .impact-summary {
        margin: 0.75rem 0 0 0;
        padding-top: 0.75rem;
        border-top: 1px solid var(--color-border);
        font-size: 0.875rem;
        line-height: 1.6;
        color: var(--color-text-secondary);
      }
    }
    
    // Footer
    .dialog-footer-enhanced {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      
      .footer-right {
        display: flex;
        gap: 0.75rem;
      }
    }
    
    // Button Styling
    :host ::ng-deep {
      .p-button {
        font-weight: 600;
        padding: 0.625rem 1.25rem;
        border-radius: 8px;
        transition: all 0.2s ease;
        
        &.p-button-outlined {
          &.p-button-secondary {
            border-color: var(--color-border);
            color: var(--color-text-primary);
            
            &:hover {
              background: var(--color-bg-secondary);
              border-color: var(--color-primary-muted);
            }
          }
        }
      }
    }
    
    // Dark Mode Adjustments
    @media (prefers-color-scheme: dark) {
      .intent-section {
        background: rgba(99, 102, 241, 0.05);
      }
      
      .parameter-item {
        &:hover {
          background: rgba(255, 255, 255, 0.05);
        }
      }
    }
  `]
})
export class ValuationUpdateResultDialogComponent {
  @Input() visible = false;
  @Input() result: ValuationUpdateResult | null = null;
  
  @Output() closed = new EventEmitter<void>();
  
  getParametersList() {
    if (!this.result?.parameters_changed) return [];
    
    return Object.entries(this.result.parameters_changed).map(([key, value]) => ({
      label: this.formatParameterName(key),
      value: this.formatParameterValue(key, value)
    }));
  }
  
  formatParameterName(key: string): string {
    // Convert camelCase to Title Case
    return key
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, str => str.toUpperCase())
      .replace('Next Year', '')
      .replace(/\d+_\d+/g, (match) => match.replace('_', '-'))
      .trim();
  }
  
  formatParameterValue(key: string, value: any): string {
    if (typeof value === 'number') {
      // Check if it's a percentage-like parameter
      if (key.toLowerCase().includes('growth') || 
          key.toLowerCase().includes('margin') || 
          key.toLowerCase().includes('rate')) {
        return `${value}%`;
      }
      return value.toLocaleString();
    }
    return String(value);
  }
  
  onClose() {
    this.visible = false;
    this.closed.emit();
  }
}

