import { Component, Input, Output, EventEmitter, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { CompanyData, ValuationResults } from '../../../../models';

export interface ModelAssumption {
  id: string;
  label: string;
  value: number;
  unit: string;
  description: string;
  category: 'growth' | 'profitability' | 'valuation' | 'financial';
  minValue?: number;
  maxValue?: number;
  step?: number;
}

@Component({
    selector: 'app-model-assumptions-section',
    imports: [CommonModule, FormsModule],
    template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-cog" aria-hidden="true"></i>
          Model Assumptions
        </h2>
        <p class="section-description">
          DCF parameters and methodology - adjust values to see impact on valuation
        </p>
        <div class="section-actions">
          <button 
            type="button" 
            class="reset-btn"
            (click)="resetToDefaults()"
            [disabled]="!hasChanges()">
            <i class="pi pi-refresh" aria-hidden="true"></i>
            Reset to Defaults
          </button>
        </div>
      </header>
      
      <div class="section-content">
        <!-- Assumptions Categories -->
        <div class="assumptions-grid">
          
          <!-- Growth Assumptions -->
          <div class="assumption-category">
            <h3 class="category-title">
              <i class="pi pi-chart-line" aria-hidden="true"></i>
              Growth Assumptions
            </h3>
            <div class="assumption-items">
              <div 
                *ngFor="let assumption of getAssumptionsByCategory('growth')"
                class="assumption-item">
                <div class="assumption-header">
                  <label [for]="assumption.id" class="assumption-label">
                    {{ assumption.label }}
                  </label>
                  <div class="assumption-value">
                    <input
                      [id]="assumption.id"
                      type="number"
                      class="value-input"
                      [(ngModel)]="assumption.value"
                      [min]="assumption.minValue ?? null"
                      [max]="assumption.maxValue ?? null"
                      [step]="assumption.step || 0.1"
                      (change)="onAssumptionChange(assumption)"
                      [attr.aria-label]="assumption.label + ' value'">
                    <span class="value-unit">{{ assumption.unit }}</span>
                  </div>
                </div>
                <p class="assumption-description">{{ assumption.description }}</p>
              </div>
            </div>
          </div>

          <!-- Profitability Assumptions -->
          <div class="assumption-category">
            <h3 class="category-title">
              <i class="pi pi-percentage" aria-hidden="true"></i>
              Profitability Assumptions
            </h3>
            <div class="assumption-items">
              <div 
                *ngFor="let assumption of getAssumptionsByCategory('profitability')"
                class="assumption-item">
                <div class="assumption-header">
                  <label [for]="assumption.id" class="assumption-label">
                    {{ assumption.label }}
                  </label>
                  <div class="assumption-value">
                    <input
                      [id]="assumption.id"
                      type="number"
                      class="value-input"
                      [(ngModel)]="assumption.value"
                      [min]="assumption.minValue ?? null"
                      [max]="assumption.maxValue ?? null"
                      [step]="assumption.step || 0.1"
                      (change)="onAssumptionChange(assumption)"
                      [attr.aria-label]="assumption.label + ' value'">
                    <span class="value-unit">{{ assumption.unit }}</span>
                  </div>
                </div>
                <p class="assumption-description">{{ assumption.description }}</p>
              </div>
            </div>
          </div>

          <!-- Valuation Assumptions -->
          <div class="assumption-category">
            <h3 class="category-title">
              <i class="pi pi-calculator" aria-hidden="true"></i>
              Valuation Assumptions
            </h3>
            <div class="assumption-items">
              <div 
                *ngFor="let assumption of getAssumptionsByCategory('valuation')"
                class="assumption-item">
                <div class="assumption-header">
                  <label [for]="assumption.id" class="assumption-label">
                    {{ assumption.label }}
                  </label>
                  <div class="assumption-value">
                    <input
                      [id]="assumption.id"
                      type="number"
                      class="value-input"
                      [(ngModel)]="assumption.value"
                      [min]="assumption.minValue ?? null"
                      [max]="assumption.maxValue ?? null"
                      [step]="assumption.step || 0.1"
                      (change)="onAssumptionChange(assumption)"
                      [attr.aria-label]="assumption.label + ' value'">
                    <span class="value-unit">{{ assumption.unit }}</span>
                  </div>
                </div>
                <p class="assumption-description">{{ assumption.description }}</p>
              </div>
            </div>
          </div>

          <!-- Financial Assumptions -->
          <div class="assumption-category">
            <h3 class="category-title">
              <i class="pi pi-dollar" aria-hidden="true"></i>
              Financial Assumptions
            </h3>
            <div class="assumption-items">
              <div 
                *ngFor="let assumption of getAssumptionsByCategory('financial')"
                class="assumption-item">
                <div class="assumption-header">
                  <label [for]="assumption.id" class="assumption-label">
                    {{ assumption.label }}
                  </label>
                  <div class="assumption-value">
                    <input
                      [id]="assumption.id"
                      type="number"
                      class="value-input"
                      [(ngModel)]="assumption.value"
                      [min]="assumption.minValue ?? null"
                      [max]="assumption.maxValue ?? null"
                      [step]="assumption.step || 0.1"
                      (change)="onAssumptionChange(assumption)"
                      [attr.aria-label]="assumption.label + ' value'">
                    <span class="value-unit">{{ assumption.unit }}</span>
                  </div>
                </div>
                <p class="assumption-description">{{ assumption.description }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- Model Summary -->
        <div class="model-summary">
          <h3 class="summary-title">Model Summary</h3>
          <div class="summary-grid">
            <div class="summary-item">
              <span class="summary-label">Projection Period</span>
              <span class="summary-value">10 Years</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">Analysis Date</span>
              <span class="summary-value">{{ formatDate(results.analysisDate) }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">Currency</span>
              <span class="summary-value">{{ results.currency }}</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">Terminal Value Method</span>
              <span class="summary-value">Perpetual Growth</span>
            </div>
            <div class="summary-item">
              <span class="summary-label">Methodology</span>
              <span class="summary-value">Unlevered Free Cash Flow</span>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
    styleUrls: ['./model-assumptions.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ModelAssumptionsSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
  
  @Output() assumptionChanged = new EventEmitter<{assumption: ModelAssumption, newValue: number}>();

  assumptions: ModelAssumption[] = [
    // Growth Assumptions
    {
      id: 'revenue-growth-rate',
      label: 'Revenue Growth Rate',
      value: 8.5,
      unit: '%',
      description: 'Expected annual revenue growth rate over the projection period',
      category: 'growth',
      minValue: -10,
      maxValue: 50,
      step: 0.5
    },
    {
      id: 'terminal-growth-rate',
      label: 'Terminal Growth Rate',
      value: 2.5,
      unit: '%',
      description: 'Long-term growth rate used for terminal value calculation',
      category: 'growth',
      minValue: 0,
      maxValue: 5,
      step: 0.1
    },
    {
      id: 'ebitda-growth-rate',
      label: 'EBITDA Growth Rate',
      value: 7.2,
      unit: '%',
      description: 'Expected annual EBITDA growth rate',
      category: 'growth',
      minValue: -20,
      maxValue: 40,
      step: 0.5
    },

    // Profitability Assumptions
    {
      id: 'ebitda-margin',
      label: 'EBITDA Margin',
      value: 22.5,
      unit: '%',
      description: 'Target EBITDA margin for mature years',
      category: 'profitability',
      minValue: 5,
      maxValue: 60,
      step: 0.5
    },
    {
      id: 'tax-rate',
      label: 'Tax Rate',
      value: 25.0,
      unit: '%',
      description: 'Corporate tax rate applied to earnings',
      category: 'profitability',
      minValue: 15,
      maxValue: 40,
      step: 0.5
    },
    {
      id: 'capex-revenue-ratio',
      label: 'CapEx / Revenue',
      value: 3.2,
      unit: '%',
      description: 'Capital expenditure as percentage of revenue',
      category: 'profitability',
      minValue: 1,
      maxValue: 15,
      step: 0.1
    },

    // Valuation Assumptions
    {
      id: 'discount-rate',
      label: 'Discount Rate (WACC)',
      value: 9.5,
      unit: '%',
      description: 'Weighted Average Cost of Capital used for discounting',
      category: 'valuation',
      minValue: 5,
      maxValue: 20,
      step: 0.1
    },
    {
      id: 'beta',
      label: 'Beta',
      value: 1.2,
      unit: '',
      description: 'Market risk factor relative to overall market',
      category: 'valuation',
      minValue: 0.5,
      maxValue: 3.0,
      step: 0.1
    },
    {
      id: 'risk-free-rate',
      label: 'Risk-Free Rate',
      value: 4.5,
      unit: '%',
      description: 'Government bond yield used as risk-free benchmark',
      category: 'valuation',
      minValue: 1,
      maxValue: 8,
      step: 0.1
    },

    // Financial Assumptions
    {
      id: 'working-capital-change',
      label: 'Working Capital Change',
      value: 1.5,
      unit: '% of Rev',
      description: 'Annual change in working capital as % of revenue',
      category: 'financial',
      minValue: -5,
      maxValue: 10,
      step: 0.1
    },
    {
      id: 'debt-equity-ratio',
      label: 'Debt/Equity Ratio',
      value: 0.3,
      unit: '',
      description: 'Target debt to equity ratio for capital structure',
      category: 'financial',
      minValue: 0,
      maxValue: 2,
      step: 0.1
    },
    {
      id: 'cost-of-debt',
      label: 'Cost of Debt',
      value: 5.5,
      unit: '%',
      description: 'Interest rate on company debt',
      category: 'financial',
      minValue: 2,
      maxValue: 15,
      step: 0.1
    }
  ];

  private defaultAssumptions: ModelAssumption[] = [];

  constructor() {
    // Store default values for reset functionality
    this.defaultAssumptions = JSON.parse(JSON.stringify(this.assumptions));
  }

  getAssumptionsByCategory(category: ModelAssumption['category']): ModelAssumption[] {
    return this.assumptions.filter(assumption => assumption.category === category);
  }

  onAssumptionChange(assumption: ModelAssumption): void {
    this.assumptionChanged.emit({
      assumption,
      newValue: assumption.value
    });
  }

  resetToDefaults(): void {
    this.assumptions = JSON.parse(JSON.stringify(this.defaultAssumptions));
  }

  hasChanges(): boolean {
    return this.assumptions.some((assumption, index) => 
      assumption.value !== this.defaultAssumptions[index].value
    );
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric'
    });
  }
}