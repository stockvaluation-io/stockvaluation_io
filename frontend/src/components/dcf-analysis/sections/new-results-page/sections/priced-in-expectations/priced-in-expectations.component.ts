import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

import {
  PricedInFrontierPoint,
  PricedInGridPoint,
  PricedInScenario,
  ValuationResults
} from '../../../../models';

interface ToggleOption {
  key: string;
  label: string;
}

@Component({
  selector: 'app-priced-in-expectations',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="priced-in-section" *ngIf="hasData">
      <header class="section-header">
        <h3 class="section-title">
          <i class="pi pi-compass" aria-hidden="true"></i>
          Market Expectations
        </h3>
        <p class="section-description">
          Reverse DCF view of what today's stock price is already assuming.
        </p>
      </header>

      <div class="expectations-summary">
        <div class="takeaway-card">
          <p class="eyebrow">What the price implies</p>
          <h4>{{ selectedScenario?.headline || fallbackHeadline }}</h4>
          <p class="takeaway-copy">{{ simpleTakeaway }}</p>
        </div>
        <dl class="price-metrics">
          <div>
            <dt>Market Price</dt>
            <dd>{{ formatMoney(data?.marketPrice) }}</dd>
          </div>
          <div>
            <dt>Model Value</dt>
            <dd>{{ formatMoney(data?.modelIntrinsicValue) }}</dd>
          </div>
          <div>
            <dt>Base Gap</dt>
            <dd [class.positive]="(data?.baseCase?.gapToMarketPct || 0) >= 0" [class.negative]="(data?.baseCase?.gapToMarketPct || 0) < 0">
              {{ formatSignedPercent(data?.baseCase?.gapToMarketPct) }}
            </dd>
          </div>
        </dl>
      </div>

      <div class="scenario-strip">
        <div class="scenario-fact">
          <span class="fact-label">Scenario</span>
          <strong>{{ selectedScenario?.riskLabel || 'Base Risk' }} / {{ selectedScenario?.capitalEfficiencyLabel || 'Base Efficiency' }}</strong>
        </div>
        <div class="scenario-fact">
          <span class="fact-label">Scenario WACC</span>
          <strong>{{ formatPercent(selectedScenario?.initialCostOfCapital) }}</strong>
        </div>
        <div class="scenario-fact">
          <span class="fact-label">Sales to Capital</span>
          <strong>{{ formatMultiple(selectedScenario?.salesToCapital) }}</strong>
        </div>
      </div>

      <details class="scenario-picker" *ngIf="riskOptions.length > 1 || efficiencyOptions.length > 1">
        <summary>
          <span>Change scenario</span>
          <small>Risk and capital efficiency assumptions</small>
        </summary>
        <div class="control-row">
          <div class="segmented-group" *ngIf="riskOptions.length > 1">
            <span class="control-label" id="risk-scenario-label">Risk Scenario</span>
            <div class="segmented-options" role="group" aria-labelledby="risk-scenario-label">
              <button
                type="button"
                *ngFor="let option of riskOptions; trackBy: trackByKey"
                [class.active]="selectedRiskKey === option.key"
                [attr.aria-pressed]="selectedRiskKey === option.key"
                (click)="selectedRiskKey = option.key">
                {{ option.label }}
              </button>
            </div>
          </div>
          <div class="segmented-group" *ngIf="efficiencyOptions.length > 1">
            <span class="control-label" id="capital-scenario-label">Capital Scenario</span>
            <div class="segmented-options" role="group" aria-labelledby="capital-scenario-label">
              <button
                type="button"
                *ngFor="let option of efficiencyOptions; trackBy: trackByKey"
                [class.active]="selectedEfficiencyKey === option.key"
                [attr.aria-pressed]="selectedEfficiencyKey === option.key"
                (click)="selectedEfficiencyKey = option.key">
                {{ option.label }}
              </button>
            </div>
          </div>
        </div>
      </details>

      <article class="frontier-panel simplified-panel" [class.empty-state]="!hasSolvedFrontier">
        <div>
          <h4>{{ frontierTitle }}</h4>
          <p class="panel-copy">
            {{ frontierCopy }}
          </p>
        </div>
        <div class="frontier-list" *ngIf="hasSolvedFrontier">
          <div class="frontier-row" *ngFor="let point of solvedFrontier | slice:0:3; trackBy: trackByMargin">
            <span class="margin">{{ formatPercent(point.operatingMargin) }} margin</span>
            <span class="requires">{{ formatFrontierGrowth(point) }}</span>
          </div>
        </div>
        <div class="closest-case" *ngIf="closestGridPoint as closest">
          <span class="fact-label">Closest tested case</span>
          <strong>
            {{ formatPercent(closest.revenueGrowth) }} growth / {{ formatPercent(closest.operatingMargin) }} margin
          </strong>
          <span>
            {{ formatMoney(closest.intrinsicValue) }} value, {{ formatSignedPercent(closest.gapToMarketPct) }} vs market
          </span>
        </div>
      </article>

      <details class="advanced-matrix">
        <summary>
          <span>Show DCF value matrix</span>
          <small>Growth and margin sensitivity for the selected scenario</small>
        </summary>
        <article class="heatmap-panel">
          <div class="panel-heading-row">
            <div>
              <h4>DCF Value Matrix</h4>
              <p class="panel-copy compact">
                Columns are revenue growth. Rows are target operating margin. Cells show DCF value per share and gap to today's price.
              </p>
            </div>
            <div class="heatmap-legend">
              <span><i class="legend-box short"></i>Below market</span>
              <span><i class="legend-box supports"></i>Supports price</span>
            </div>
          </div>
          <div class="heatmap-wrap">
            <table class="heatmap-table">
              <thead>
                <tr>
                  <th>Target Margin</th>
                  <th *ngFor="let growth of growthAxis">{{ formatPercent(growth) }}</th>
                </tr>
              </thead>
              <tbody>
                <tr *ngFor="let margin of marginAxis">
                  <th>{{ formatPercent(margin) }}</th>
                  <td
                    *ngFor="let growth of growthAxis"
                    [class.supports]="cellFor(margin, growth)?.supportsMarketPrice"
                    [class.short]="cellFor(margin, growth)?.supportsMarketPrice === false"
                    [style.opacity]="cellOpacity(cellFor(margin, growth))"
                    [attr.title]="cellTitle(cellFor(margin, growth))">
                    <span class="cell-value">{{ formatCompactMoney(cellFor(margin, growth)?.intrinsicValue) }}</span>
                    <span class="cell-gap">{{ formatSignedPercent(cellFor(margin, growth)?.gapToMarketPct) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </article>
      </details>

      <p class="method-note" *ngIf="data?.method">{{ data?.method }}</p>
    </section>
  `,
  styleUrls: ['../section-base.scss', './priced-in-expectations.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PricedInExpectationsComponent {
  @Input() results!: ValuationResults;

  selectedRiskKey = 'base_risk';
  selectedEfficiencyKey = 'base_efficiency';

  get data() {
    return this.results?.assumptionTransparency?.pricedInExpectations;
  }

  get hasData(): boolean {
    return Boolean(this.data?.scenarios?.length);
  }

  get scenarios(): PricedInScenario[] {
    return this.data?.scenarios || [];
  }

  get selectedScenario(): PricedInScenario | undefined {
    return this.scenarios.find(s =>
      s.riskKey === this.selectedRiskKey &&
      s.capitalEfficiencyKey === this.selectedEfficiencyKey
    ) || this.scenarios.find(s => s.key === 'base_risk__base_efficiency') || this.scenarios[0];
  }

  get riskOptions(): ToggleOption[] {
    return this.uniqueOptions('riskKey', 'riskLabel');
  }

  get efficiencyOptions(): ToggleOption[] {
    return this.uniqueOptions('capitalEfficiencyKey', 'capitalEfficiencyLabel');
  }

  get grid(): PricedInGridPoint[] {
    return this.selectedScenario?.grid || this.data?.grid || [];
  }

  get frontier(): PricedInFrontierPoint[] {
    return this.selectedScenario?.frontier || this.data?.frontier || [];
  }

  get solvedFrontier(): PricedInFrontierPoint[] {
    return this.frontier.filter(point => point.solved);
  }

  get hasSolvedFrontier(): boolean {
    return this.frontier.some(point => point.solved);
  }

  get frontierTitle(): string {
    return this.hasSolvedFrontier
      ? "Combinations That Justify Today's Price"
      : "Market Price Outside Tested Range";
  }

  get closestGridPoint(): PricedInGridPoint | undefined {
    return this.grid
      .filter(point => this.isFiniteNumber(point.gapToMarketPct))
      .reduce<PricedInGridPoint | undefined>((closest, point) => {
        if (!closest || Math.abs(point.gapToMarketPct!) < Math.abs(closest.gapToMarketPct!)) {
          return point;
        }
        return closest;
      }, undefined);
  }

  get frontierCopy(): string {
    if (this.hasSolvedFrontier) {
      return 'Plain-English hurdle rates from the selected scenario.';
    }
    return "This scenario does not reach today's price in the sampled grid. The closest tested case shows the shortfall.";
  }

  get simpleTakeaway(): string {
    if (this.hasSolvedFrontier) {
      return "If you believe the company can clear these hurdle rates, today's price may be defensible. If not, the market is asking for too much.";
    }
    return "The market price is higher than every tested combination for this scenario. Treat the current price as demanding assumptions outside this grid.";
  }

  get growthAxis(): number[] {
    return this.uniqueNumbers(this.grid.map(point => point.revenueGrowth));
  }

  get marginAxis(): number[] {
    return this.uniqueNumbers(this.grid.map(point => point.operatingMargin)).sort((a, b) => b - a);
  }

  get fallbackHeadline(): string {
    const firstSolved = this.frontier.find(point => point.solved && point.impliedRevenueGrowth != null);
    if (!firstSolved) {
      return 'Current market price is outside the sampled market expectations range.';
    }
    return `At ${this.formatPercent(firstSolved.operatingMargin)} margin, today's market price needs about ${this.formatPercent(firstSolved.impliedRevenueGrowth)} revenue growth.`;
  }

  cellFor(margin: number, growth: number): PricedInGridPoint | undefined {
    return this.grid.find(point =>
      this.close(point.operatingMargin, margin) &&
      this.close(point.revenueGrowth, growth)
    );
  }

  cellOpacity(point: PricedInGridPoint | undefined): string {
    const gap = Math.abs(point?.gapToMarketPct ?? 0);
    return String(Math.max(0.45, Math.min(1, 0.45 + gap / 80)));
  }

  formatMoney(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'N/A';
    }
    const currency = this.results?.stockCurrency || this.results?.currency || 'USD';
    return `${currency} ${value.toFixed(2)}`;
  }

  formatCompactMoney(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'N/A';
    }
    const currency = this.results?.stockCurrency || this.results?.currency || 'USD';
    const decimals = Math.abs(value) >= 100 ? 0 : 2;
    return `${currency} ${value.toFixed(decimals)}`;
  }

  formatPercent(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'N/A';
    }
    return `${value.toFixed(2)}%`;
  }

  formatMultiple(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'N/A';
    }
    return `${value.toFixed(2)}x`;
  }

  formatSignedPercent(value: number | null | undefined): string {
    if (!this.isFiniteNumber(value)) {
      return 'N/A';
    }
    const prefix = value > 0 ? '+' : '';
    return `${prefix}${value.toFixed(2)}%`;
  }

  formatFrontierGrowth(point: PricedInFrontierPoint): string {
    const growth = this.formatPercent(point.impliedRevenueGrowth);
    return `${growth} growth required`;
  }

  cellTitle(point: PricedInGridPoint | undefined): string {
    if (!point) {
      return 'No sampled DCF value for this combination.';
    }
    return `${this.formatCompactMoney(point.intrinsicValue)} DCF value, ${this.formatSignedPercent(point.gapToMarketPct)} versus market price`;
  }

  trackByKey(_: number, option: ToggleOption): string {
    return option.key;
  }

  trackByMargin(_: number, point: PricedInFrontierPoint): string {
    return `${point.operatingMargin ?? 'na'}-${point.impliedRevenueGrowth ?? 'na'}`;
  }

  private uniqueOptions(keyField: keyof PricedInScenario, labelField: keyof PricedInScenario): ToggleOption[] {
    const seen = new Set<string>();
    const options: ToggleOption[] = [];
    for (const scenario of this.scenarios) {
      const key = String(scenario[keyField] || '').trim();
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      options.push({
        key,
        label: String(scenario[labelField] || key).trim()
      });
    }
    return options;
  }

  private uniqueNumbers(values: Array<number | null | undefined>): number[] {
    const result: number[] = [];
    for (const raw of values) {
      if (!this.isFiniteNumber(raw)) {
        continue;
      }
      const value = Number(raw.toFixed(2));
      if (!result.some(existing => this.close(existing, value))) {
        result.push(value);
      }
    }
    return result.sort((a, b) => a - b);
  }

  private close(left: number | null | undefined, right: number | null | undefined): boolean {
    if (!this.isFiniteNumber(left) || !this.isFiniteNumber(right)) {
      return false;
    }
    return Math.abs(left - right) < 0.01;
  }

  private isFiniteNumber(value: number | null | undefined): value is number {
    return value !== null && value !== undefined && Number.isFinite(value);
  }
}
