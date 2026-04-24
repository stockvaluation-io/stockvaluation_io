import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { formatPercentage, formatLargeNumber } from '../../../../utils/formatting.utils';

@Component({
    selector: 'app-terminal-value-section',
    imports: [CommonModule],
    template: `
    <div class="terminal-value-section">
      <h3 class="section-title">
        <i class="pi pi-infinity section-icon"></i>
        Terminal Value Analysis
      </h3>

      <div class="compact-terminal-container">
        <div class="terminal-summary-card">
          <div class="terminal-summary-main">
            <span class="summary-eyebrow">Terminal value share</span>
            <strong>{{ getTerminalValuePercentage() }}%</strong>
            <p>Long-term value contribution based on steady-state growth and discount assumptions.</p>
          </div>
          <div class="terminal-key-facts">
            <span>Growth {{ formatPercentage(results.terminalGrowthRate || 0) }}</span>
            <span>Cost of capital {{ formatPercentage(results.terminalCostOfCapital || 0) }}</span>
          </div>
        </div>

        <details class="terminal-details">
          <summary>Terminal assumptions</summary>
          <div class="terminal-metrics-strip">
            <div class="metric-item">
              <div class="metric-label">Growth Rate</div>
              <div class="metric-value">{{ formatPercentage(results.terminalGrowthRate || 0) }}</div>
            </div>

            <div class="metric-item">
              <div class="metric-label">Cost of Capital</div>
              <div class="metric-value">{{ formatPercentage(results.terminalCostOfCapital || 0) }}</div>
            </div>

            <div class="metric-item">
              <div class="metric-label">Return on Capital</div>
              <div class="metric-value">{{ formatPercentage(results.terminalReturnOnCapital || 0) }}</div>
            </div>

            <div class="metric-item">
              <div class="metric-label">Reinvestment Rate</div>
              <div class="metric-value">{{ formatPercentage(results.terminalReinvestmentRate || 0) }}</div>
            </div>
          </div>
        </details>
      </div>
    </div>
  `,
    styleUrls: ['./terminal-value-section.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class TerminalValueSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;


  // Use utility functions for formatting
  formatPercentage = formatPercentage;
  formatLargeNumber = formatLargeNumber;


  getTerminalValuePercentage(): string {
    if (!this.results.pvTerminalValue || !this.results.enterpriseValue) {
      return '--';
    }
    const percentage = (this.results.pvTerminalValue / this.results.enterpriseValue) * 100;
    return percentage.toFixed(0);
  }

}
