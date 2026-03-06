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
        <h3 class="section-title">
          <i class="pi pi-eye" aria-hidden="true"></i>
          Assumptions Transparency
        </h3>
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
        <div class="rationale-item" *ngIf="data.adjustmentRationales?.revenueGrowth">
          <span class="rationale-label">Revenue Growth</span>
          <div class="rationale-body" [innerHTML]="formatRationale(data.adjustmentRationales!.revenueGrowth!)"></div>
        </div>
        <div class="rationale-item" *ngIf="data.adjustmentRationales?.operatingMargin">
          <span class="rationale-label">Operating Margin</span>
          <div class="rationale-body" [innerHTML]="formatRationale(data.adjustmentRationales!.operatingMargin!)"></div>
        </div>
        <div class="rationale-item" *ngIf="data.adjustmentRationales?.salesToCapital">
          <span class="rationale-label">Sales to Capital</span>
          <div class="rationale-body" [innerHTML]="formatRationale(data.adjustmentRationales!.salesToCapital!)"></div>
        </div>
        <div class="rationale-item" *ngIf="data.adjustmentRationales?.costOfCapital">
          <span class="rationale-label">Cost of Capital</span>
          <div class="rationale-body" [innerHTML]="formatRationale(data.adjustmentRationales!.costOfCapital!)"></div>
        </div>
      </article>

      <article class="assumption-card growth-anchor-card" *ngIf="data.growthAnchor as anchor">
        <h3 class="card-title">
          <i class="pi pi-chart-line" aria-hidden="true"></i>
          Historical Growth Anchor
        </h3>
        <p class="anchor-meta">
          <span class="meta-chip" *ngIf="anchor.entityDisplay">{{ anchor.entityDisplay }}</span>
          <span class="meta-chip" *ngIf="anchor.region">{{ anchor.region }}</span>
          <span class="meta-chip" *ngIf="anchor.year">{{ anchor.year }}</span>
        </p>
        <dl class="metrics-grid">
          <div class="metric-row" *ngIf="anchor.fundamentalGrowth != null">
            <dt>Fundamental Growth (ROE x Reinvestment)</dt>
            <dd>{{ formatPercent(anchor.fundamentalGrowth) }}</dd>
          </div>
          <div class="metric-row" *ngIf="anchor.historicalGrowthProxy != null">
            <dt>Historical Growth Proxy</dt>
            <dd>{{ formatPercent(anchor.historicalGrowthProxy) }}</dd>
          </div>
          <div class="metric-row" *ngIf="anchor.expectedGrowthProxy != null">
            <dt>Expected Growth Proxy</dt>
            <dd>{{ formatPercent(anchor.expectedGrowthProxy) }}</dd>
          </div>
        </dl>
        <div class="growth-band" *ngIf="anchor.p25 != null || anchor.p50 != null || anchor.p75 != null">
          <h4 class="band-title">Growth Dispersion Band</h4>
          <div class="band-row">
            <span class="band-label">P25 (Conservative)</span>
            <span class="band-value">{{ formatPercent(anchor.p25) }}</span>
          </div>
          <div class="band-row highlight">
            <span class="band-label">P50 (Median)</span>
            <span class="band-value">{{ formatPercent(anchor.p50) }}</span>
          </div>
          <div class="band-row">
            <span class="band-label">P75 (Optimistic)</span>
            <span class="band-value">{{ formatPercent(anchor.p75) }}</span>
          </div>
        </div>
        <p class="source" *ngIf="anchor.numberOfFirms != null">
          <strong>Based on:</strong> {{ anchor.numberOfFirms | number:'1.0-0' }} firms in industry
        </p>
        <p class="source">
          <strong>Source:</strong> {{ anchor.source || 'Damodaran Historical Growth Rate in Earnings' }}
        </p>
      </article>

      <article class="assumption-card market-implied-card" *ngIf="hasMarketImplied(data)">
        <h3 class="card-title">
          <i class="pi pi-table" aria-hidden="true"></i>
          Market-Implied Expectations
        </h3>
        <p class="source" *ngIf="data.marketImpliedExpectations?.method">
          <strong>Method:</strong> {{ data.marketImpliedExpectations?.method }}
        </p>
        <div class="meta-row market-meta">
          <span class="meta-chip" *ngIf="data.marketImpliedExpectations?.marketPrice != null">
            Market Price: {{ data.marketImpliedExpectations?.marketPrice | number:'1.2-2' }}
          </span>
          <span class="meta-chip" *ngIf="data.marketImpliedExpectations?.modelIntrinsicValue != null">
            Model Value: {{ data.marketImpliedExpectations?.modelIntrinsicValue | number:'1.2-2' }}
          </span>
        </div>

        <div class="table-wrap">
          <table class="implied-table">
            <thead>
              <tr>
                <th>Lever</th>
                <th>Model</th>
                <th>Implied</th>
                <th>Gap</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let metric of data.marketImpliedExpectations?.metrics">
                <td>{{ metric.label || metric.key || 'N/A' }}</td>
                <td>{{ formatMetricValue(metric.modelValue, metric.unit) }}</td>
                <td>{{ formatMetricValue(metric.impliedValue, metric.unit) }}</td>
                <td>{{ formatGap(metric.gap, metric.unit) }}</td>
                <td>
                  <span class="status-chip" [class.status-ok]="metric.solved" [class.status-warn]="!metric.solved">
                    {{ metric.solved ? 'Solved' : 'Bounded' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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

  hasMarketImplied(data: NonNullable<ValuationResults['assumptionTransparency']>): boolean {
    return Boolean(data.marketImpliedExpectations?.metrics && data.marketImpliedExpectations.metrics.length > 0);
  }

  formatMetricValue(value: number | null | undefined, unit: string | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 'N/A';
    }
    const unitKey = String(unit || '').trim().toLowerCase();
    if (unitKey === 'multiple' || unitKey === 'x') {
      return this.formatMultiple(value);
    }
    return this.formatPercent(value);
  }

  formatGap(value: number | null | undefined, unit: string | null | undefined): string {
    if (value === null || value === undefined || Number.isNaN(value)) {
      return 'N/A';
    }
    const unitKey = String(unit || '').trim().toLowerCase();
    const prefix = value > 0 ? '+' : '';
    if (unitKey === 'multiple' || unitKey === 'x') {
      return `${prefix}${this.normalizeMultiple(value).toFixed(2)}x`;
    }
    return `${prefix}${this.normalizePercent(value).toFixed(2)}%`;
  }

  /**
   * Split a dense LLM rationale string into readable paragraphs.
   * Groups ~3 sentences per paragraph, same logic as the narrative section.
   */
  formatRationale(text: string): string {
    if (!text) return '';

    // Clean up dashes to commas
    let result = text
      .replace(/\s*---+\s*/g, ', ')
      .replace(/\s*--\s*/g, ', ')
      .replace(/\s*\u2014\s*/g, ', ');

    // If explicit newlines exist, honour them
    const explicit = result.split(/\n\s*\n/).map(s => s.trim()).filter(Boolean);
    if (explicit.length > 1) {
      return explicit.map(p => `<p>${p}</p>`).join('');
    }

    // Split at sentence boundaries
    const sentenceEndRe = /(?<![A-Z]\.|[Vv]s\.|[Ee]tc\.|[Ii]nc\.|[Cc]o\.|[Nn]o\.)([.!?])\s+(?=[A-Z])/g;
    const splits: number[] = [];
    let m: RegExpExecArray | null;
    sentenceEndRe.lastIndex = 0;
    while ((m = sentenceEndRe.exec(result)) !== null) {
      splits.push(m.index + m[1].length);
    }

    if (splits.length === 0) return `<p>${result}</p>`;

    // Group into ~3-sentence paragraphs
    const SENTENCES_PER_PARA = 3;
    const paragraphs: string[] = [];
    let start = 0;
    for (let i = SENTENCES_PER_PARA - 1; i < splits.length; i += SENTENCES_PER_PARA) {
      const chunk = result.slice(start, splits[i]).trim();
      if (chunk) paragraphs.push(chunk);
      start = splits[i];
    }
    const tail = result.slice(start).trim();
    if (tail) paragraphs.push(tail);

    // Merge a tiny orphan tail into the previous paragraph
    if (paragraphs.length > 1 && paragraphs[paragraphs.length - 1].length < 60) {
      paragraphs[paragraphs.length - 2] += ' ' + paragraphs.pop();
    }

    return paragraphs.map(p => `<p>${p.trim()}</p>`).join('');
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
