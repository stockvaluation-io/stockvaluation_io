import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

import { ValuationResults } from '../../../../models';

@Component({
  selector: 'app-assumptions-transparency-section',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="full-width-section-container transparency-section" *ngIf="results?.assumptionTransparency as data">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-eye" aria-hidden="true"></i>
          Assumptions Transparency
        </h2>
        <p class="section-description">
          Exact DCF assumptions used for this valuation and where each one came from.
        </p>
      </header>

      <div class="meta-row">
        <span class="meta-chip" *ngIf="data.valuationModel">Model: {{ data.valuationModel }}</span>
        <span class="meta-chip" *ngIf="data.industryGlobal">Industry: {{ data.industryGlobal }}</span>
        <span class="meta-chip" *ngIf="data.segmentCount !== undefined">Segments: {{ data.segmentCount }}</span>
      </div>

      <div class="cards-grid">
        <article class="assumption-card">
          <h3 class="card-title">Cost of Capital</h3>
          <dl class="metrics-grid">
            <div class="metric-row">
              <dt>Risk-Free Rate</dt>
              <dd>{{ formatPercent(data.discountRate?.riskFreeRate) }}</dd>
            </div>
            <div class="metric-row">
              <dt>Equity Risk Premium</dt>
              <dd>{{ formatPercent(data.discountRate?.equityRiskPremium) }}</dd>
            </div>
            <div class="metric-row">
              <dt>Initial Cost of Capital</dt>
              <dd>{{ formatPercent(data.discountRate?.initialCostOfCapital) }}</dd>
            </div>
            <div class="metric-row">
              <dt>Terminal Cost of Capital</dt>
              <dd>{{ formatPercent(data.discountRate?.terminalCostOfCapital) }}</dd>
            </div>
          </dl>
          <p class="formula">{{ data.discountRate?.costOfCapitalFormula || 'N/A' }}</p>
          <p class="source"><strong>Risk-Free Source:</strong> {{ data.discountRate?.riskFreeRateSource || 'N/A' }}</p>
          <p class="source"><strong>Risk Premium Source:</strong> {{ data.discountRate?.equityRiskPremiumSource || 'N/A' }}</p>
          <p class="source"><strong>Initial CoC Source:</strong> {{ data.discountRate?.initialCostOfCapitalSource || 'N/A' }}</p>
        </article>

        <article class="assumption-card">
          <h3 class="card-title">Operating Drivers</h3>
          <dl class="metrics-grid">
            <div class="metric-row">
              <dt>Revenue Growth (Years 2-5)</dt>
              <dd>{{ formatPercent(data.operatingAssumptions?.revenueGrowthRateYears2To5) }}</dd>
            </div>
            <div class="metric-row">
              <dt>Target Operating Margin</dt>
              <dd>{{ formatPercent(data.operatingAssumptions?.targetOperatingMargin) }}</dd>
            </div>
            <div class="metric-row">
              <dt>Sales to Capital (Years 1-5)</dt>
              <dd>{{ formatMultiple(data.operatingAssumptions?.salesToCapitalYears1To5) }}</dd>
            </div>
            <div class="metric-row">
              <dt>Sales to Capital (Years 6-10)</dt>
              <dd>{{ formatMultiple(data.operatingAssumptions?.salesToCapitalYears6To10) }}</dd>
            </div>
          </dl>
          <p class="source"><strong>Growth Source:</strong> {{ data.operatingAssumptions?.revenueGrowthSource || 'N/A' }}</p>
          <p class="source"><strong>Margin Source:</strong> {{ data.operatingAssumptions?.operatingMarginSource || 'N/A' }}</p>
          <p class="source"><strong>Sales/Capital Source:</strong> {{ data.operatingAssumptions?.salesToCapitalSource || 'N/A' }}</p>
        </article>
      </div>

      <article class="assumption-card rationale-card" *ngIf="hasAnyRationale(data)">
        <h3 class="card-title">Why These Inputs</h3>
        <p class="source" *ngIf="data.adjustmentRationales?.revenueGrowth">
          <strong>Revenue Growth:</strong> {{ data.adjustmentRationales?.revenueGrowth }}
        </p>
        <p class="source" *ngIf="data.adjustmentRationales?.operatingMargin">
          <strong>Operating Margin:</strong> {{ data.adjustmentRationales?.operatingMargin }}
        </p>
        <p class="source" *ngIf="data.adjustmentRationales?.salesToCapital">
          <strong>Sales to Capital:</strong> {{ data.adjustmentRationales?.salesToCapital }}
        </p>
        <p class="source" *ngIf="data.adjustmentRationales?.costOfCapital">
          <strong>Cost of Capital:</strong> {{ data.adjustmentRationales?.costOfCapital }}
        </p>
      </article>

      <article class="assumption-card notes-card" *ngIf="data.notes?.length">
        <h3 class="card-title">Notes</h3>
        <ul class="notes-list">
          <li *ngFor="let note of data.notes">{{ note }}</li>
        </ul>
      </article>
    </section>
  `,
  styleUrls: ['./assumptions-transparency.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AssumptionsTransparencySectionComponent {
  @Input() results!: ValuationResults;

  formatPercent(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 'N/A';
    }
    return `${this.normalizePercent(value).toFixed(2)}%`;
  }

  formatMultiple(value: number | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 'N/A';
    }
    return `${this.normalizeMultiple(value).toFixed(2)}x`;
  }

  hasAnyRationale(data: NonNullable<ValuationResults['assumptionTransparency']>): boolean {
    return Boolean(
      data.adjustmentRationales?.revenueGrowth ||
      data.adjustmentRationales?.operatingMargin ||
      data.adjustmentRationales?.salesToCapital ||
      data.adjustmentRationales?.costOfCapital
    );
  }

  private normalizePercent(value: number): number {
    let normalized = value;
    if (Math.abs(normalized) <= 1) {
      normalized = normalized * 100;
    } else if (Math.abs(normalized) > 100) {
      normalized = normalized / 100;
    }
    return normalized;
  }

  private normalizeMultiple(value: number): number {
    if (Math.abs(value) > 10) {
      return value / 100;
    }
    return value;
  }
}
