import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';

@Component({
    selector: 'app-profitability-analysis-section',
    imports: [CommonModule],
    template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-chart-pie" aria-hidden="true"></i>
          Profitability Analysis
        </h2>
        <p class="section-description">
          Margin analysis and operational efficiency metrics
        </p>
      </header>
      <div class="section-content">
        <p>Profitability analysis content will be implemented here.</p>
      </div>
    </section>
  `,
    styleUrls: ['../section-base.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProfitabilityAnalysisSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
}