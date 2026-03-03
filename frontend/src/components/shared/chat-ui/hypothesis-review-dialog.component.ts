import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
  OnDestroy,
  ChangeDetectionStrategy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';

export interface Hypothesis {
  ticker: string;
  thesis_statement: string;
  key_assumptions: string[];
  timeframe: string;
  conviction: number;
  fair_value: number;
  current_price: number;
  catalysts?: string[];
  risks?: string[];
  // AI Analysis dimensions
  growth_thesis?: string;
  margin_thesis?: string;
  efficiency_thesis?: string;
  risk_thesis?: string;
  key_takeaways?: string;
}

@Component({
  selector: 'app-hypothesis-review-dialog',
  standalone: true,
  imports: [CommonModule, ButtonModule, DialogModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [style]="{ width: '90vw', maxWidth: '800px' }"
      [closable]="true"
      [draggable]="false"
      [resizable]="false"
      (onHide)="onClose()"
      [styleClass]="'hypothesis-review-dialog'">
      <ng-template pTemplate="header">
        <div class="dialog-header">
          <div class="header-icon">
            <i class="pi pi-lightbulb"></i>
          </div>
          <div class="header-content">
            <h3 class="dialog-title">Investment Hypothesis Summary</h3>
            <p class="dialog-subtitle">{{ hypothesis?.ticker || 'Stock' }}</p>
          </div>
        </div>
      </ng-template>
      
      <div class="dialog-content" *ngIf="hypothesis">
        <!-- Core Thesis -->
        <div class="section core-thesis">
          <h4 class="section-title">
            <i class="pi pi-bookmark"></i>
            Core Thesis
          </h4>
          <div class="thesis-statement">
            {{ hypothesis.thesis_statement }}
          </div>
        </div>
        
        <!-- Metrics Grid -->
        <div class="metrics-grid">
          <div class="metric-card">
            <div class="metric-label">Conviction</div>
            <div class="metric-value conviction">{{ getConviction() }}/10</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">Timeframe</div>
            <div class="metric-value">{{ getTimeframe() }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">Fair Value</div>
            <div class="metric-value fair-value">{{ getFairValue() }}</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">Current Price</div>
            <div class="metric-value">{{ getCurrentPrice() }}</div>
          </div>
        </div>
        
        <!-- Growth Thesis -->
        <div class="section" *ngIf="hypothesis.growth_thesis">
          <h4 class="section-title">
            <i class="pi pi-chart-line"></i>
            Growth Thesis
          </h4>
          <p class="narrative-text">{{ hypothesis.growth_thesis }}</p>
        </div>
        
        <!-- Margin Thesis -->
        <div class="section" *ngIf="hypothesis.margin_thesis">
          <h4 class="section-title">
            <i class="pi pi-percentage"></i>
            Margins & Profitability
          </h4>
          <p class="narrative-text">{{ hypothesis.margin_thesis }}</p>
        </div>
        
        <!-- Efficiency Thesis -->
        <div class="section" *ngIf="hypothesis.efficiency_thesis">
          <h4 class="section-title">
            <i class="pi pi-cog"></i>
            Investment Efficiency
          </h4>
          <p class="narrative-text">{{ hypothesis.efficiency_thesis }}</p>
        </div>
        
        <!-- Risk Thesis -->
        <div class="section" *ngIf="hypothesis.risk_thesis">
          <h4 class="section-title">
            <i class="pi pi-exclamation-triangle"></i>
            Risks
          </h4>
          <p class="narrative-text">{{ hypothesis.risk_thesis }}</p>
        </div>
        
        <!-- Key Takeaways -->
        <div class="section" *ngIf="hypothesis.key_takeaways">
          <h4 class="section-title">
            <i class="pi pi-star"></i>
            Key Takeaways
          </h4>
          <p class="narrative-text">{{ hypothesis.key_takeaways }}</p>
        </div>
      </div>
      
      <ng-template pTemplate="footer">
        <div class="dialog-footer">
          <p-button
            label="Needs Revision"
            icon="pi pi-pencil"
            [outlined]="true"
            severity="secondary"
            (onClick)="onRevise()"
            [disabled]="isProcessing">
          </p-button>
          <p-button
            label="Looks Good"
            icon="pi pi-check"
            severity="success"
            (onClick)="onApprove()"
            [disabled]="isProcessing || !isValuationDataAvailable()"
            [loading]="isProcessing">
          </p-button>
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep .hypothesis-review-dialog {
      .p-dialog {
        background: var(--color-bg-card);
        border-radius: 16px;
        box-shadow: 0 20px 25px -5px var(--color-bg-overlay), 0 10px 10px -5px var(--color-bg-overlay);
        border: 1px solid var(--color-border-primary);
        color: var(--color-text-primary);
      }
      
      .p-dialog-header {
        padding: var(--space-8);
        border-bottom: 1px solid var(--color-border-primary);
        background: var(--color-bg-card);
        border-radius: 16px 16px 0 0;
      }
      
      .p-dialog-content {
        padding: var(--space-8);
        max-height: 70vh;
        overflow-y: auto;
        background: var(--color-bg-card);
        
        /* Custom scrollbar */
        &::-webkit-scrollbar {
          width: 8px;
        }
        
        &::-webkit-scrollbar-track {
          background: var(--color-bg-tertiary);
        }
        
        &::-webkit-scrollbar-thumb {
          background: var(--color-primary-alpha);
          border-radius: 4px;
          
          &:hover {
            background: var(--color-primary);
          }
        }
      }
      
      .p-dialog-footer {
        padding: var(--space-6) var(--space-8);
        border-top: 1px solid var(--color-border-primary);
        background: var(--color-bg-secondary);
        border-radius: 0 0 16px 16px;
      }
    }
    
    .dialog-header {
      display: flex;
      align-items: center;
      gap: var(--space-4);
    }
    
    .header-icon {
      width: 56px;
      height: 56px;
      border-radius: 12px;
      background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-text-inverse);
      font-size: 28px;
      box-shadow: 0 4px 12px var(--color-primary-alpha);
    }
    
    .header-content {
      flex: 1;
    }
    
    .dialog-title {
      margin: 0;
      font-size: var(--font-size-2xl);
      font-weight: var(--font-weight-semibold);
      color: var(--color-text-primary);
      font-family: var(--font-primary);
    }
    
    .dialog-subtitle {
      margin: var(--space-1) 0 0 0;
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
      font-weight: var(--font-weight-medium);
      font-family: var(--font-primary);
    }
    
    .dialog-content {
      display: flex;
      flex-direction: column;
      gap: var(--space-6);
    }
    
    .section {
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      
      &.core-thesis {
        padding-bottom: var(--space-6);
        border-bottom: 2px solid var(--color-border-primary);
      }
    }
    
    .section-title {
      margin: 0;
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-secondary);
      text-transform: uppercase;
      letter-spacing: var(--tracking-wider);
      display: flex;
      align-items: center;
      gap: var(--space-2);
      font-family: var(--font-primary);
      
      i {
        font-size: var(--font-size-lg);
        color: var(--color-primary);
      }
    }
    
    .thesis-statement {
      padding: var(--space-5);
      background: var(--color-bg-success-alpha);
      border-left: 4px solid var(--color-primary);
      border-radius: 8px;
      line-height: var(--leading-relaxed);
      color: var(--color-text-primary);
      font-size: var(--font-size-base);
      font-family: var(--font-primary);
    }
    
    .narrative-text {
      margin: 0;
      padding: var(--space-4);
      background: var(--color-bg-secondary);
      border-radius: 8px;
      border: 1px solid var(--color-border-primary);
      line-height: var(--leading-relaxed);
      color: var(--color-text-primary);
      font-size: var(--font-size-sm);
      font-family: var(--font-primary);
    }
    
    .content-list {
      margin: 0;
      padding-left: var(--space-6);
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      
      li {
        padding: var(--space-2) 0;
        color: var(--color-text-primary);
        line-height: var(--leading-normal);
        font-size: var(--font-size-sm);
        font-family: var(--font-primary);
        
        &::marker {
          color: var(--color-primary);
          font-size: 1.1em;
        }
      }
    }
    
    .metrics-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: var(--space-4);
      margin: var(--space-2) 0;
    }
    
    .metric-card {
      padding: var(--space-5);
      background: var(--color-bg-card);
      border-radius: 12px;
      border: 2px solid var(--color-border-primary);
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
      
      &:hover {
        border-color: var(--color-primary);
        box-shadow: 0 4px 12px var(--color-primary-alpha);
        transform: translateY(-2px);
        background: var(--color-bg-hover);
      }
    }
    
    .metric-label {
      font-size: var(--font-size-xs);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-secondary);
      text-transform: uppercase;
      letter-spacing: var(--tracking-wider);
      font-family: var(--font-primary);
    }
    
    .metric-value {
      font-size: var(--font-size-2xl);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-primary);
      font-family: var(--font-primary);
      
      &.conviction {
        color: var(--color-primary);
        font-size: var(--font-size-3xl);
      }
      
      &.fair-value {
        color: var(--color-primary);
      }
    }
    
    .dialog-footer {
      display: flex;
      justify-content: flex-end;
      gap: var(--space-3);
    }
    
    /* Professional button styling matching app theme */
    :host ::ng-deep .hypothesis-review-dialog {
      .p-button {
        font-weight: var(--font-weight-semibold);
        border-radius: 8px;
        padding: var(--space-3) var(--space-6);
        transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
        font-size: var(--font-size-sm);
        min-height: 44px;
        font-family: var(--font-primary);
      }
      
      /* Needs Revision button - outlined */
      .p-button-outlined.p-button-secondary {
        background: var(--color-bg-card);
        color: var(--color-text-secondary);
        border: 2px solid var(--color-border-primary);
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
        
        &:hover:not(:disabled) {
          background: var(--color-bg-hover);
          color: var(--color-text-primary);
          border-color: var(--color-border-primary);
          transform: translateY(-1px);
          box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
        }
        
        &:active:not(:disabled) {
          transform: translateY(0);
        }
      }
      
      /* Looks Good button - primary gradient */
      .p-button-success {
        background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%) !important;
        border: none !important;
        color: var(--color-text-inverse) !important;
        box-shadow: 0 2px 8px var(--color-primary-alpha) !important;
        
        &:hover:not(:disabled) {
          background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%) !important;
          box-shadow: 0 6px 16px var(--color-primary-alpha) !important;
          transform: translateY(-1px);
        }
        
        &:active:not(:disabled) {
          background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%) !important;
          transform: translateY(0);
        }
        
        &:focus {
          box-shadow: 0 0 0 3px var(--color-primary-alpha) !important;
        }
        
        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
          transform: none;
        }
      }
    }
    
    /* Tablet responsive */
    @media (max-width: 1024px) {
      :host ::ng-deep .hypothesis-review-dialog {
        .p-dialog {
          width: 90vw !important;
          max-width: 700px !important;
        }
      }
    }
    
    /* Mobile responsive */
    @media (max-width: 768px) {
      :host ::ng-deep .hypothesis-review-dialog {
        .p-dialog {
          width: 95vw !important;
          max-width: none !important;
          margin: var(--space-2);
          max-height: 90vh !important;
        }
        
        .p-dialog-header,
        .p-dialog-content {
          padding: var(--space-4);
        }
        
        .p-dialog-footer {
          padding: var(--space-3) var(--space-4);
        }
      }
      
      .header-icon {
        width: 44px;
        height: 44px;
        font-size: 22px;
      }
      
      .dialog-title {
        font-size: var(--font-size-xl);
      }
      
      .dialog-subtitle {
        font-size: var(--font-size-xs);
      }
      
      .metrics-grid {
        grid-template-columns: 1fr;
        gap: var(--space-3);
      }
      
      .section {
        gap: var(--space-2);
      }
      
      .thesis-statement,
      .narrative-text {
        padding: var(--space-3);
        font-size: var(--font-size-sm);
      }
      
      .dialog-footer {
        flex-direction: column-reverse;
        gap: var(--space-2);
        
        ::ng-deep button {
          width: 100%;
          min-height: 48px;
          justify-content: center;
          font-size: var(--font-size-base);
        }
      }
    }
    
    /* Small mobile devices */
    @media (max-width: 480px) {
      :host ::ng-deep .hypothesis-review-dialog {
        .p-dialog {
          width: 100vw !important;
          height: 100vh !important;
          max-height: 100vh !important;
          margin: 0;
          border-radius: 0 !important;
          border: none !important;
        }
        
        .p-dialog-header {
          padding: var(--space-4);
          border-radius: 0 !important;
        }
        
        .p-dialog-content {
          padding: var(--space-3);
          max-height: calc(100vh - 180px) !important;
          overflow-y: auto;
        }
        
        .p-dialog-footer {
          padding: var(--space-3);
          border-radius: 0 !important;
        }
      }
      
      .header-icon {
        width: 40px;
        height: 40px;
        font-size: 20px;
      }
      
      .dialog-title {
        font-size: var(--font-size-lg);
      }
      
      .dialog-subtitle {
        font-size: var(--font-size-xs);
      }
      
      .section-title {
        font-size: var(--font-size-xs);
      }
      
      .thesis-statement {
        padding: var(--space-3);
        font-size: var(--font-size-sm);
        border-left-width: 3px;
      }
      
      .narrative-text {
        padding: var(--space-3);
        font-size: var(--font-size-sm);
      }
      
      .metric-card {
        padding: var(--space-3);
      }
      
      .metric-value {
        font-size: var(--font-size-xl);
        
        &.conviction {
          font-size: var(--font-size-2xl);
        }
      }
      
      .content-list {
        padding-left: var(--space-5);
        gap: var(--space-2);
        
        li {
          font-size: var(--font-size-xs);
        }
      }
    }
    
    /* Touch device optimizations */
    @media (hover: none) and (pointer: coarse) {
      .metric-card {
        &:hover {
          transform: none;
        }
        
        &:active {
          transform: scale(0.98);
          background: var(--color-bg-active);
        }
      }
      
      :host ::ng-deep .hypothesis-review-dialog {
        .p-button {
          min-height: 48px;
          
          &:active {
            transform: scale(0.98);
          }
        }
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HypothesisReviewDialogComponent implements OnInit, OnDestroy {
  @Input() hypothesis: Hypothesis | null = null;
  @Input() visible = false;
  @Input() hypothesisId: string | null = null;
  @Output() approved = new EventEmitter<string>();
  @Output() revised = new EventEmitter<string>();
  @Output() closed = new EventEmitter<void>();

  isProcessing = false;

  ngOnInit(): void {
    // Component initialization
    if (this.hypothesis) {
      console.log('Hypothesis Review Dialog - Data received:', {
        ticker: this.hypothesis.ticker,
        thesis_statement: this.hypothesis.thesis_statement?.substring(0, 50),
        has_growth_thesis: !!this.hypothesis.growth_thesis,
        has_margin_thesis: !!this.hypothesis.margin_thesis,
        has_efficiency_thesis: !!this.hypothesis.efficiency_thesis,
        has_risk_thesis: !!this.hypothesis.risk_thesis,
        has_key_takeaways: !!this.hypothesis.key_takeaways,
        full_data: this.hypothesis
      });
    }
  }

  ngOnDestroy(): void {
    // Cleanup if needed
  }

  formatTimeframe(timeframe: string): string {
    const mapping: Record<string, string> = {
      'short-term': 'Short Term',
      'medium-term': 'Medium Term',
      'long-term': 'Long Term',
      'indefinite': 'Indefinite',
    };
    return mapping[timeframe] || timeframe;
  }

  formatPrice(price: number): string {
    if (!price || price === 0) return '0.00';
    return price.toFixed(2);
  }

  getConviction(): number {
    return this.hypothesis?.conviction || 0;
  }

  getTimeframe(): string {
    return this.formatTimeframe(this.hypothesis?.timeframe || 'long-term');
  }

  getFairValue(): string {
    const value = this.hypothesis?.fair_value || 0;
    return value > 0 ? '$' + this.formatPrice(value) : 'Not yet calculated';
  }
  
  // Helper methods for AI dimension sections
  hasGrowthThesis(): boolean {
    return !!this.hypothesis?.growth_thesis && this.hypothesis.growth_thesis.trim().length > 0;
  }
  
  hasMarginThesis(): boolean {
    return !!this.hypothesis?.margin_thesis && this.hypothesis.margin_thesis.trim().length > 0;
  }
  
  hasEfficiencyThesis(): boolean {
    return !!this.hypothesis?.efficiency_thesis && this.hypothesis.efficiency_thesis.trim().length > 0;
  }
  
  hasRiskThesis(): boolean {
    return !!this.hypothesis?.risk_thesis && this.hypothesis.risk_thesis.trim().length > 0;
  }
  
  hasKeyTakeaways(): boolean {
    return !!this.hypothesis?.key_takeaways && this.hypothesis.key_takeaways.trim().length > 0;
  }

  getCurrentPrice(): string {
    const price = this.hypothesis?.current_price || 0;
    return price > 0 ? '$' + this.formatPrice(price) : 'Not yet calculated';
  }
  
  isValuationDataAvailable(): boolean {
    const fairValue = this.hypothesis?.fair_value || 0;
    const currentPrice = this.hypothesis?.current_price || 0;
    return fairValue > 0 && currentPrice > 0;
  }

  hasKeyAssumptions(): boolean {
    return !!(this.hypothesis?.key_assumptions && this.hypothesis.key_assumptions.length > 0);
  }

  getKeyAssumptions(): string[] {
    return this.hypothesis?.key_assumptions || [];
  }

  hasCatalysts(): boolean {
    return !!(this.hypothesis?.catalysts && this.hypothesis.catalysts.length > 0);
  }

  getCatalysts(): string[] {
    return this.hypothesis?.catalysts || [];
  }

  hasRisks(): boolean {
    return !!(this.hypothesis?.risks && this.hypothesis.risks.length > 0);
  }

  getRisks(): string[] {
    return this.hypothesis?.risks || [];
  }

  onApprove(): void {
    if (!this.hypothesis || !this.hypothesisId) return;
    this.isProcessing = true;
    this.approved.emit(this.hypothesisId);
    setTimeout(() => {
      this.isProcessing = false;
      this.visible = false;
    }, 500);
  }

  onRevise(): void {
    if (!this.hypothesis || !this.hypothesisId) return;
    this.revised.emit(this.hypothesisId);
    this.visible = false;
  }

  onClose(): void {
    this.closed.emit();
  }
}

