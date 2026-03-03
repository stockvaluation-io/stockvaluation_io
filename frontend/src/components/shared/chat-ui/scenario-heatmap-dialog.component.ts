import {
  Component,
  Input,
  Output,
  EventEmitter,
  OnInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { trigger, state, style, transition, animate } from '@angular/animations';

export interface ScenarioData {
  ticker: string;
  scenarios: {
    optimistic: Scenario;
    base: Scenario;
    pessimistic: Scenario;
  };
  heatmap_data?: HeatMapData;
  summary?: string;
}

export interface Scenario {
  intrinsic_value: number;
  assumptions: string;
  probability: string;
  investment_thesis?: string;
  causal_chain?: any; // Keep for backward compatibility
  causal_reasoning?: {  // NEW
    growth: CausalNarrative;
    margins: CausalNarrative;
    risk: CausalNarrative;
    investment_efficiency: CausalNarrative;
    [key: string]: CausalNarrative; // Index signature for dynamic access
  };
}

export interface CausalNarrative {
  title: string;
  narrative: string;
  parameter_impact: string;
}

export interface HeatMapData {
  growth_rates: number[];
  discount_rates: number[];
  valuations: number[][];
  base_value?: number;
  min_value?: number;
  max_value?: number;
}

@Component({
  selector: 'app-scenario-heatmap-dialog',
  standalone: true,
  imports: [CommonModule, ButtonModule, DialogModule],
  animations: [
    trigger('expandCollapse', [
      transition(':enter', [
        style({ height: 0, opacity: 0 }),
        animate('300ms ease-out', style({ height: '*', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('200ms ease-in', style({ height: 0, opacity: 0 }))
      ])
    ])
  ],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [style]="{ width: '90vw', maxWidth: '1000px', maxHeight: '90vh' }"
      [closable]="true"
      [draggable]="false"
      [resizable]="false"
      (onHide)="onClose()"
      [styleClass]="'scenario-heatmap-dialog'">
      <ng-template pTemplate="header">
        <div class="dialog-header">
          <div class="header-icon">
            <i class="pi pi-sliders-h"></i>
          </div>
          <div class="header-content">
            <h3 class="dialog-title">Sensitivity Analysis</h3>
            <p class="dialog-subtitle">{{ data?.ticker || 'Stock' }}</p>
          </div>
        </div>
      </ng-template>
      
      <div class="dialog-content" *ngIf="data">
        <!--
        ============================================================
        SCENARIO ANALYSIS CARDS - COMMENTED OUT
        ============================================================
        Scenario analysis cards (optimistic/base/pessimistic with causal reasoning)
        have been moved to a separate component and are no longer displayed in this dialog.
        This dialog now only shows the Sensitivity Heatmap.
        The code is preserved for potential future use.
        ============================================================
        
        <!-- Three Scenarios -->
        <!--
        <div class="scenarios-grid">
          <div *ngFor="let scenarioKey of ['optimistic', 'base', 'pessimistic']" 
               class="scenario-card"
               [class.optimistic]="scenarioKey === 'optimistic'"
               [class.base]="scenarioKey === 'base'"
               [class.pessimistic]="scenarioKey === 'pessimistic'">
            
            <!-- Card Header (Always Visible) -->
            <!--
            <div class="scenario-header">
              <i class="pi"
                 [class.pi-arrow-up]="scenarioKey === 'optimistic'"
                 [class.pi-minus]="scenarioKey === 'base'"
                 [class.pi-arrow-down]="scenarioKey === 'pessimistic'"></i>
              <h4>{{ scenarioKey === 'base' ? 'Base Case' : (scenarioKey | titlecase) }}</h4>
            </div>
            
            <div class="scenario-value">
              \${{ getScenarioValue(scenarioKey)?.toFixed(2) || '0.00' }}
            </div>
            
            <div class="scenario-probability">
              {{ getScenarioProbability(scenarioKey) }}
            </div>
            
            <div class="scenario-assumptions">
              {{ getScenario(scenarioKey)?.assumptions || 'No data available' }}
            </div>
            
            <!-- Expandable Causal Reasoning Section -->
            <!--
            <div class="causal-reasoning-section">
              <button class="expand-toggle" 
                      (click)="toggleCausalReasoning(scenarioKey)"
                      [attr.aria-expanded]="expandedScenarios[scenarioKey]">
                <span>{{ expandedScenarios[scenarioKey] ? 'Hide' : 'Show' }} Causal Analysis</span>
                <i class="pi"
                   [class.pi-chevron-down]="!expandedScenarios[scenarioKey]"
                   [class.pi-chevron-up]="expandedScenarios[scenarioKey]"></i>
              </button>
              
              <div class="causal-content" 
                   *ngIf="expandedScenarios[scenarioKey]"
                   [@expandCollapse]>
                
                <!-- Growth Section -->
                <!--
                <div class="causal-pillar" *ngIf="getCausalNarrative(scenarioKey, 'growth')">
                  <div class="pillar-header">
                    <i class="pi pi-chart-line"></i>
                    <h5>{{ getCausalTitle(scenarioKey, 'growth') }}</h5>
                  </div>
                  <p class="pillar-narrative">{{ getCausalNarrative(scenarioKey, 'growth') }}</p>
                  <div class="parameter-impact" *ngIf="getCausalImpact(scenarioKey, 'growth')">
                    {{ getCausalImpact(scenarioKey, 'growth') }}
                  </div>
                </div>
                
                <!-- Margins Section -->
                <!--
                <div class="causal-pillar" *ngIf="getCausalNarrative(scenarioKey, 'margins')">
                  <div class="pillar-header">
                    <i class="pi pi-percentage"></i>
                    <h5>{{ getCausalTitle(scenarioKey, 'margins') }}</h5>
                  </div>
                  <p class="pillar-narrative">{{ getCausalNarrative(scenarioKey, 'margins') }}</p>
                  <div class="parameter-impact" *ngIf="getCausalImpact(scenarioKey, 'margins')">
                    {{ getCausalImpact(scenarioKey, 'margins') }}
                  </div>
                </div>
                
                <!-- Risk Section -->
                <!--
                <div class="causal-pillar" *ngIf="getCausalNarrative(scenarioKey, 'risk')">
                  <div class="pillar-header">
                    <i class="pi pi-exclamation-triangle"></i>
                    <h5>{{ getCausalTitle(scenarioKey, 'risk') }}</h5>
                  </div>
                  <p class="pillar-narrative">{{ getCausalNarrative(scenarioKey, 'risk') }}</p>
                  <div class="parameter-impact" *ngIf="getCausalImpact(scenarioKey, 'risk')">
                    {{ getCausalImpact(scenarioKey, 'risk') }}
                  </div>
                </div>
                
                <!-- Investment Efficiency Section -->
                <!--
                <div class="causal-pillar" *ngIf="getCausalNarrative(scenarioKey, 'investment_efficiency')">
                  <div class="pillar-header">
                    <i class="pi pi-cog"></i>
                    <h5>{{ getCausalTitle(scenarioKey, 'investment_efficiency') }}</h5>
                  </div>
                  <p class="pillar-narrative">{{ getCausalNarrative(scenarioKey, 'investment_efficiency') }}</p>
                  <div class="parameter-impact" *ngIf="getCausalImpact(scenarioKey, 'investment_efficiency')">
                    {{ getCausalImpact(scenarioKey, 'investment_efficiency') }}
                  </div>
                </div>
                
                <!-- Investment Thesis -->
                <!--
                <div class="investment-thesis-section" *ngIf="getScenario(scenarioKey)?.investment_thesis">
                  <h5><i class="pi pi-book"></i> Investment Thesis</h5>
                  <p>{{ getScenario(scenarioKey)?.investment_thesis }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        -->
        <!--
        ============================================================
        END SCENARIO ANALYSIS CARDS
        ============================================================
        -->
        
        <!-- Heat Map -->
        <div class="heatmap-section" *ngIf="hasValidHeatmap()">
          <h4 class="section-title">
            <i class="pi pi-th-large"></i>
            Sensitivity Heat Map
          </h4>
          <p class="heatmap-subtitle">How intrinsic value changes with growth rate and discount rate</p>
          
          <div class="heatmap-container">
            <div class="heatmap-y-label">Discount Rate (%)</div>
            <div class="heatmap-wrapper">
              <div class="heatmap-y-axis">
                <div *ngFor="let rate of data.heatmap_data!.discount_rates" class="axis-label">
                  {{ rate.toFixed(1) }}
                </div>
              </div>
              
              <div class="heatmap-grid-wrapper">
                <div class="heatmap-grid">
                  <div 
                    *ngFor="let row of data.heatmap_data!.valuations; let i = index"
                    class="heatmap-row">
                    <div 
                      *ngFor="let value of row; let j = index"
                      class="heatmap-cell"
                      [style.background-color]="getCellColor(value, data.heatmap_data!)"
                      [title]="'Growth: ' + data.heatmap_data!.growth_rates[j].toFixed(1) + '%, Discount: ' + data.heatmap_data!.discount_rates[i].toFixed(1) + '% → $' + value.toFixed(2)">
                      <span class="cell-value">\${{ value.toFixed(0) }}</span>
                    </div>
                  </div>
                </div>
                
                <div class="heatmap-x-axis">
                  <div *ngFor="let rate of data.heatmap_data!.growth_rates" class="axis-label">
                    {{ rate.toFixed(1) }}
                  </div>
                </div>
              </div>
            </div>
            <div class="heatmap-x-label">Growth Rate (%)</div>
          </div>
          
          <div class="heatmap-legend">
            <span class="legend-label">Lower</span>
            <div class="legend-gradient"></div>
            <span class="legend-label">Higher</span>
          </div>
        </div>
        
        <!-- Summary -->
        <div class="summary-section" *ngIf="data.summary">
          <p class="summary-text">{{ data.summary }}</p>
        </div>
      </div>
      
      <ng-template pTemplate="footer">
        <div class="dialog-footer">
          <p-button
            label="Close"
            icon="pi pi-times"
            (onClick)="onClose()"
            severity="secondary">
          </p-button>
        </div>
      </ng-template>
    </p-dialog>
  `,
  styles: [`
    :host ::ng-deep .scenario-heatmap-dialog {
      .p-dialog {
        background: var(--color-bg-card);
        border-radius: 16px;
        box-shadow: 0 20px 25px -5px var(--color-bg-overlay), 0 10px 10px -5px var(--color-bg-overlay);
        border: 1px solid var(--color-border-primary);
        color: var(--color-text-primary);
      }
      
      .p-dialog-header {
        background: var(--color-bg-card);
        border-bottom: 1px solid var(--color-border-primary);
        padding: var(--space-6);
      }
      
      .p-dialog-content {
        background: var(--color-bg-card);
        padding: var(--space-6);
        color: var(--color-text-primary);
        max-height: calc(90vh - 200px);
        overflow-y: auto;
      }
      
      .p-dialog-footer {
        background: var(--color-bg-card);
        border-top: 1px solid var(--color-border-primary);
        padding: var(--space-4) var(--space-6);
      }
    }
    
    .dialog-header {
      display: flex;
      align-items: center;
      gap: var(--space-4);
    }
    
    .header-icon {
      width: 48px;
      height: 48px;
      background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-dark) 100%);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: white;
      font-size: 1.5rem;
    }
    
    .header-content {
      flex: 1;
    }
    
    .dialog-title {
      margin: 0;
      font-size: var(--font-size-2xl);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-primary);
    }
    
    .dialog-subtitle {
      margin: var(--space-1) 0 0 0;
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
    }
    
    .dialog-content {
      display: flex;
      flex-direction: column;
      gap: var(--space-6);
    }
    
    .scenarios-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: var(--space-4);
      
      @media (max-width: 1024px) {
        grid-template-columns: 1fr;
      }
    }
    
    .scenario-card {
      background: var(--color-bg-secondary);
      border: 2px solid var(--color-border-primary);
      border-radius: 12px;
      padding: var(--space-4);
      display: flex;
      flex-direction: column;
      gap: var(--space-3);
      transition: all 0.3s ease;
      
      @media (max-width: 768px) {
        padding: var(--space-3);
      }
      
      &.optimistic {
        border-color: var(--color-success);
        background: linear-gradient(135deg, rgba(var(--success-rgb), 0.05) 0%, transparent 100%);
      }
      
      &.base {
        border-color: var(--color-primary);
        background: linear-gradient(135deg, rgba(var(--primary-rgb), 0.05) 0%, transparent 100%);
      }
      
      &.pessimistic {
        border-color: var(--color-danger);
        background: linear-gradient(135deg, rgba(var(--danger-rgb), 0.05) 0%, transparent 100%);
      }
    }
    
    .scenario-header {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      
      i {
        font-size: 1.25rem;
      }
      
      h4 {
        margin: 0;
        font-size: var(--font-size-lg);
        font-weight: var(--font-weight-semibold);
      }
      
      .optimistic & {
        color: var(--color-success);
      }
      
      .base & {
        color: var(--color-primary);
      }
      
      .pessimistic & {
        color: var(--color-danger);
      }
    }
    
    .scenario-value {
      font-size: var(--font-size-3xl);
      font-weight: var(--font-weight-bold);
      color: var(--color-text-primary);
    }
    
    .scenario-probability {
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
      font-weight: var(--font-weight-medium);
    }
    
    .scenario-assumptions {
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
      line-height: 1.5;
    }
    
    // Causal Reasoning Section Styles
    .causal-reasoning-section {
      margin-top: var(--space-2);
    }
    
    .expand-toggle {
      width: 100%;
      padding: var(--space-2) var(--space-3);
      background: var(--color-bg-tertiary);
      border: 1px solid var(--color-border-secondary);
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-medium);
      color: var(--color-text-secondary);
      cursor: pointer;
      transition: all 0.2s ease;
      
      &:hover {
        background: var(--color-bg-hover);
        border-color: var(--color-border-primary);
      }
      
      i {
        font-size: 0.875rem;
      }
    }
    
    .causal-content {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
      margin-top: var(--space-3);
      padding-top: var(--space-3);
      border-top: 1px solid var(--color-border-secondary);
    }
    
    .causal-pillar {
      background: var(--color-bg-card);
      border-left: 3px solid var(--color-primary);
      padding: var(--space-3);
      border-radius: 8px;
      
      .optimistic & {
        border-left-color: var(--color-success);
      }
      
      .pessimistic & {
        border-left-color: var(--color-danger);
      }
    }
    
    .pillar-header {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      margin-bottom: var(--space-2);
      
      i {
        font-size: 1rem;
        color: var(--color-primary);
        
        .optimistic & {
          color: var(--color-success);
        }
        
        .pessimistic & {
          color: var(--color-danger);
        }
      }
      
      h5 {
        margin: 0;
        font-size: var(--font-size-sm);
        font-weight: var(--font-weight-semibold);
        color: var(--color-text-primary);
      }
    }
    
    .pillar-narrative {
      font-size: var(--font-size-xs);
      line-height: 1.6;
      color: var(--color-text-secondary);
      margin: 0 0 var(--space-2) 0;
      
      @media (max-width: 768px) {
        font-size: 0.8rem;
      }
    }
    
    .parameter-impact {
      font-size: var(--font-size-xs);
      color: var(--color-text-tertiary);
      font-family: 'Monaco', 'Courier New', monospace;
      background: var(--color-bg-tertiary);
      padding: var(--space-1) var(--space-2);
      border-radius: 4px;
      font-weight: var(--font-weight-medium);
    }
    
    .investment-thesis-section {
      background: var(--color-bg-secondary);
      border: 1px solid var(--color-border-primary);
      padding: var(--space-3);
      border-radius: 8px;
      margin-top: var(--space-2);
      
      h5 {
        margin: 0 0 var(--space-2) 0;
        font-size: var(--font-size-sm);
        font-weight: var(--font-weight-semibold);
        color: var(--color-text-primary);
        display: flex;
        align-items: center;
        gap: var(--space-2);
        
        i {
          color: var(--color-primary);
        }
      }
      
      p {
        margin: 0;
        font-size: var(--font-size-xs);
        color: var(--color-text-secondary);
        font-style: italic;
        line-height: 1.5;
      }
    }
    
    .heatmap-section {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }
    
    .section-title {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      margin: 0;
      font-size: var(--font-size-xl);
      font-weight: var(--font-weight-semibold);
      color: var(--color-text-primary);
    }
    
    .heatmap-subtitle {
      margin: 0;
      font-size: var(--font-size-sm);
      color: var(--color-text-secondary);
    }
    
    .heatmap-container {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
      align-items: center;
    }
    
    .heatmap-y-label,
    .heatmap-x-label {
      font-size: var(--font-size-sm);
      font-weight: var(--font-weight-medium);
      color: var(--color-text-secondary);
      text-align: center;
    }
    
    .heatmap-wrapper {
      display: flex;
      gap: var(--space-2);
    }
    
    .heatmap-y-axis {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 20px 0;
    }
    
    .heatmap-grid-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--space-2);
    }
    
    .heatmap-grid {
      display: flex;
      flex-direction: column;
      gap: 2px;
      background: var(--color-border-primary);
      border-radius: 8px;
      overflow: hidden;
    }
    
    .heatmap-row {
      display: flex;
      gap: 2px;
    }
    
    .heatmap-cell {
      width: 80px;
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: var(--font-size-xs);
      font-weight: var(--font-weight-semibold);
      color: white;
      text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
      cursor: help;
      transition: transform 0.2s;
      
      &:hover {
        transform: scale(1.05);
        z-index: 1;
        box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);
      }
      
      @media (max-width: 768px) {
        width: 55px;
        height: 45px;
        font-size: 0.65rem;
      }
      
      @media (max-width: 480px) {
        width: 45px;
        height: 40px;
        font-size: 0.6rem;
      }
    }
    
    .heatmap-x-axis {
      display: flex;
      justify-content: space-around;
      gap: 2px;
      
      .axis-label {
        width: 80px;
        text-align: center;
        
        @media (max-width: 768px) {
          width: 55px;
        }
        
        @media (max-width: 480px) {
          width: 45px;
        }
      }
    }
    
    .axis-label {
      font-size: var(--font-size-xs);
      color: var(--color-text-secondary);
      font-weight: var(--font-weight-medium);
    }
    
    .heatmap-legend {
      display: flex;
      align-items: center;
      gap: var(--space-3);
      justify-content: center;
      margin-top: var(--space-2);
    }
    
    .legend-gradient {
      width: 200px;
      height: 20px;
      background: linear-gradient(90deg, #ef4444 0%, #f59e0b 25%, #84cc16 50%, #22c55e 75%, #10b981 100%);
      border-radius: 4px;
    }
    
    .legend-label {
      font-size: var(--font-size-xs);
      color: var(--color-text-secondary);
      font-weight: var(--font-weight-medium);
    }
    
    .summary-section {
      background: var(--color-bg-secondary);
      border-left: 4px solid var(--color-primary);
      padding: var(--space-4);
      border-radius: 8px;
    }
    
    .summary-text {
      margin: 0;
      font-size: var(--font-size-base);
      color: var(--color-text-primary);
      line-height: 1.6;
    }
    
    .dialog-footer {
      display: flex;
      justify-content: flex-end;
    }
    
    // Dialog Responsive Sizing
    :host ::ng-deep .scenario-heatmap-dialog .p-dialog {
      @media (max-width: 768px) {
        width: 95vw !important;
        max-width: 95vw !important;
        margin: 1rem;
      }
    }
    
    :host ::ng-deep .scenario-heatmap-dialog .p-dialog-content {
      @media (max-width: 768px) {
        padding: var(--space-4);
        max-height: calc(85vh - 150px);
      }
    }
  `],
})
export class ScenarioHeatmapDialogComponent {
  @Input() visible = false;
  @Input() set data(value: ScenarioData | null) {
    console.log('📊 [HEATMAP DIALOG] Received data:', value);
    console.log('📊 [HEATMAP DIALOG] Dialog visible:', this.visible);
    console.log('📊 [HEATMAP DIALOG] Has heatmap_data?', !!value?.heatmap_data);
    
    // Don't clear data if dialog is visible and we're receiving null (likely a timing issue)
    if (value === null && this.visible && this._data) {
      console.warn('📊 [HEATMAP DIALOG] Received null data while dialog is visible, keeping existing data');
      return; // Keep existing data
    }
    
    if (value?.heatmap_data) {
      console.log('📊 [HEATMAP DIALOG] Heatmap structure:', {
        growth_rates: value.heatmap_data.growth_rates,
        discount_rates: value.heatmap_data.discount_rates,
        valuations_length: value.heatmap_data.valuations?.length,
        min: value.heatmap_data.min_value,
        max: value.heatmap_data.max_value
      });
    }
    this._data = value;
  }
  get data(): ScenarioData | null {
    return this._data;
  }
  private _data: ScenarioData | null = null;
  
  // Track which scenarios have expanded causal reasoning
  expandedScenarios: { [key: string]: boolean } = {
    optimistic: false,
    base: false,
    pessimistic: false
  };
  
  @Output() closed = new EventEmitter<void>();

  onClose(): void {
    this.closed.emit();
  }
  
  toggleCausalReasoning(scenarioKey: string): void {
    this.expandedScenarios[scenarioKey] = !this.expandedScenarios[scenarioKey];
  }

  getScenario(key: string): Scenario | undefined {
    return (this.data?.scenarios as any)?.[key === 'base' ? 'base_case' : key];
  }

  getScenarioValue(key: string): number {
    return this.getScenario(key)?.intrinsic_value || 0;
  }

  getScenarioProbability(key: string): string {
    return this.getScenario(key)?.probability || 'N/A';
  }

  getCausalTitle(scenarioKey: string, pillar: string): string {
    const scenario = this.getScenario(scenarioKey);
    return scenario?.causal_reasoning?.[pillar]?.title || `${pillar.replace('_', ' ')} Analysis`;
  }

  getCausalNarrative(scenarioKey: string, pillar: string): string {
    const scenario = this.getScenario(scenarioKey);
    return scenario?.causal_reasoning?.[pillar]?.narrative || '';
  }

  getCausalImpact(scenarioKey: string, pillar: string): string {
    const scenario = this.getScenario(scenarioKey);
    return scenario?.causal_reasoning?.[pillar]?.parameter_impact || '';
  }
  
  hasValidHeatmap(): boolean {
    if (!this._data?.heatmap_data) {
      console.log('📊 [HEATMAP CHECK] No heatmap_data');
      return false;
    }
    const valuations = this._data.heatmap_data.valuations;
    const isValid = !!(valuations && valuations.length > 0);
    console.log('📊 [HEATMAP CHECK] Has valuations:', !!valuations, 'Length:', valuations?.length, 'Is valid:', isValid);
    return isValid;
  }
  
  getCellColor(value: number, heatmapData: HeatMapData): string {
    if (!heatmapData.min_value || !heatmapData.max_value) {
      return '#6b7280';
    }
    
    const min = heatmapData.min_value;
    const max = heatmapData.max_value;
    const range = max - min;
    const normalized = (value - min) / range; // 0 to 1
    
    // Color scale from red (low) to green (high)
    if (normalized < 0.2) {
      return '#ef4444'; // red
    } else if (normalized < 0.4) {
      return '#f59e0b'; // orange
    } else if (normalized < 0.6) {
      return '#84cc16'; // yellow-green
    } else if (normalized < 0.8) {
      return '#22c55e'; // green
    } else {
      return '#10b981'; // emerald
    }
  }
}

