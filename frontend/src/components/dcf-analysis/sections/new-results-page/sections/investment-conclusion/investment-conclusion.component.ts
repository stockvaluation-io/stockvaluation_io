import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';

export interface ActionItem {
  title: string;
  description: string;
  timeline?: string;
}

@Component({
    selector: 'app-investment-conclusion-section',
    imports: [CommonModule],
    template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-check-circle" aria-hidden="true"></i>
          Valuation Summary
        </h2>
        <p class="section-description">
          Summary of DCF analysis and valuation assessment
        </p>
      </header>
      
      <div class="section-content">
        <!-- Valuation Assessment Card -->
        <div class="recommendation-card">
          <div class="recommendation-header">
            <div class="analysis-badge" [class]="getAnalysisClass()">
              <i [class]="getAnalysisIcon()" aria-hidden="true"></i>
              <span class="analysis-text">{{ getAnalysisText() }}</span>
            </div>
            <div class="price-targets">
              <div class="current-price">
                <span class="price-label">Current Price</span>
                <span class="price-value">\${{ results.currentPrice | number:'1.2-2' }}</span>
              </div>
              <div class="target-price">
                <span class="price-label">Fair Value</span>
                <span class="price-value primary">\${{ results.intrinsicValue | number:'1.2-2' }}</span>
              </div>
              <div class="upside-potential" [class.positive]="results.upside >= 0" [class.negative]="results.upside < 0">
                <span class="price-label">Upside Potential</span>
                <span class="price-value">{{ results.upside | number:'1.1-1' }}%</span>
              </div>
            </div>
          </div>
          
          <div class="recommendation-summary">
            <p class="summary-text">{{ getAnalysisSummary() }}</p>
          </div>
        </div>

        <!-- Key Valuation Points -->
        <div class="investment-highlights">
          <h3 class="highlights-title">
            <i class="pi pi-star" aria-hidden="true"></i>
            Key Valuation Highlights
          </h3>
          <div class="highlights-grid">
            <div class="highlight-item positive" *ngFor="let highlight of getPositiveHighlights()">
              <i class="pi pi-check-circle" aria-hidden="true"></i>
              <span>{{ highlight }}</span>
            </div>
          </div>
        </div>

        <!-- Risk Considerations -->
        <div class="risk-considerations">
          <h3 class="risks-title">
            <i class="pi pi-exclamation-triangle" aria-hidden="true"></i>
            Risk Considerations
          </h3>
          <div class="risks-grid">
            <div class="risk-item" *ngFor="let risk of getRiskConsiderations()">
              <i class="pi pi-minus-circle" aria-hidden="true"></i>
              <span>{{ risk }}</span>
            </div>
          </div>
        </div>

        <!-- Action Items -->
        <div class="action-items">
          <h3 class="actions-title">
            <i class="pi pi-list-check" aria-hidden="true"></i>
            Recommended Actions
          </h3>
          <div class="actions-list">
            <div class="action-item" *ngFor="let action of getActionItems(); let i = index">
              <div class="action-number">{{ i + 1 }}</div>
              <div class="action-content">
                <h4 class="action-title">{{ action.title }}</h4>
                <p class="action-description">{{ action.description }}</p>
                <div class="action-timeline" *ngIf="action.timeline">
                  <i class="pi pi-clock" aria-hidden="true"></i>
                  <span>{{ action.timeline }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Valuation Summary -->
        <div class="valuation-summary">
          <h3 class="summary-title">
            <i class="pi pi-chart-pie" aria-hidden="true"></i>
            Valuation Summary
          </h3>
          <div class="summary-metrics">
            <div class="metric-row">
              <span class="metric-label">DCF Intrinsic Value</span>
              <span class="metric-value primary">\${{ results.intrinsicValue | number:'1.2-2' }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">Enterprise Value</span>
              <span class="metric-value">\${{ formatLargeNumber(results.enterpriseValue) }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">Equity Value</span>
              <span class="metric-value">\${{ formatLargeNumber(results.equityValue) }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">Terminal Value</span>
              <span class="metric-value">\${{ formatLargeNumber(results.terminalValue) }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">FCF Value</span>
              <span class="metric-value">\${{ formatLargeNumber(results.fcfValue) }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">P/B Ratio</span>
              <span class="metric-value">{{ results.priceToBook | number:'1.1-1' }}x</span>
            </div>
          </div>
        </div>

        <!-- Disclaimer -->
        <div class="disclaimer">
          <h4 class="disclaimer-title">
            <i class="pi pi-info-circle" aria-hidden="true"></i>
            Important Disclaimer
          </h4>
          <p class="disclaimer-text">
            This analysis is for informational purposes only and should not be considered as financial advice. 
            Investment decisions should be based on your own research, risk tolerance, and consultation with 
            qualified financial advisors. Past performance does not guarantee future results, and all investments 
            carry inherent risks including potential loss of principal.
          </p>
        </div>
      </div>
    </section>
  `,
    styleUrls: ['./investment-conclusion.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class InvestmentConclusionSection {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  getAnalysisClass(): string {
    const upside = this.results.upside;
    if (upside >= 20) return 'analysis-strong-positive';
    if (upside >= 10) return 'analysis-positive';
    if (upside >= -10) return 'analysis-neutral';
    if (upside >= -20) return 'analysis-negative';
    return 'analysis-strong-negative';
  }

  getAnalysisIcon(): string {
    const upside = this.results.upside;
    if (upside >= 20) return 'pi pi-arrow-up-right';
    if (upside >= 10) return 'pi pi-arrow-up';
    if (upside >= -10) return 'pi pi-minus';
    if (upside >= -20) return 'pi pi-arrow-down';
    return 'pi pi-arrow-down-left';
  }

  getAnalysisText(): string {
    const upside = this.results.upside;
    if (upside >= 20) return 'Significant Upside';
    if (upside >= 10) return 'Moderate Upside';
    if (upside >= -10) return 'Fair Value';
    if (upside >= -20) return 'Overvalued';
    return 'Significantly Overvalued';
  }


  getAnalysisSummary(): string {
    const upside = this.results.upside;
    
    if (upside >= 20) {
      return `Significant valuation gap of ${upside.toFixed(2)}% identified through DCF analysis. The stock appears significantly undervalued based on fundamental financial metrics and cash flow projections.`;
    } else if (upside >= 10) {
      return `Moderate valuation gap of ${upside.toFixed(2)}% identified. The DCF analysis indicates the stock is trading below its calculated intrinsic value based on projected cash flows.`;
    } else if (upside >= -10) {
      return `Fair value assessment with ${Math.abs(upside).toFixed(2)}% ${upside >= 0 ? 'discount' : 'premium'} to intrinsic value. The stock appears fairly valued at current levels based on DCF analysis.`;
    } else if (upside >= -20) {
      return `Overvaluation identified with ${Math.abs(upside).toFixed(2)}% premium to intrinsic value. The DCF analysis suggests the stock is trading above its calculated fair value.`;
    } else {
      return `Significant overvaluation with ${Math.abs(upside).toFixed(2)}% premium to intrinsic value. The stock appears substantially overvalued based on DCF analysis and projected cash flows.`;
    }
  }

  getPositiveHighlights(): string[] {
    const highlights: string[] = [];
    
    if (this.results.upside > 20) {
      highlights.push('Significant upside potential based on DCF valuation');
    }
    
    if (this.results.priceToBook < 2) {
      highlights.push('Attractive price-to-book ratio indicating value opportunity');
    }
    
    if (this.company.marketCap && this.company.marketCap > 1e9) {
      highlights.push('Established company with substantial market capitalization');
    }
    
    highlights.push('Comprehensive financial projections support investment thesis');
    highlights.push('Detailed sensitivity analysis validates valuation robustness');
    
    return highlights;
  }

  getRiskConsiderations(): string[] {
    const risks: string[] = [];
    
    if (this.results.upside < -10) {
      risks.push('Current market price significantly above fair value estimate');
    }
    
    risks.push('Market volatility and economic conditions may impact performance');
    risks.push('Company-specific risks including competitive pressures');
    risks.push('Interest rate changes may affect discount rate assumptions');
    risks.push('Projection uncertainty increases with longer time horizons');
    
    if (this.company.sector) {
      risks.push(`Sector-specific risks related to ${this.company.sector} industry dynamics`);
    }
    
    return risks;
  }

  getActionItems(): ActionItem[] {
    const actions: ActionItem[] = [];
    
    if (this.results.upside >= 10) {
      actions.push({
        title: 'Consider Position Initiation',
        description: 'Evaluate portfolio allocation and consider establishing or increasing position based on risk tolerance and investment objectives.',
        timeline: 'Near-term (1-4 weeks)'
      });
      
      actions.push({
        title: 'Monitor Key Metrics',
        description: 'Track quarterly earnings, revenue growth, and margin trends to validate investment thesis and DCF assumptions.',
        timeline: 'Ongoing'
      });
      
      actions.push({
        title: 'Set Price Targets',
        description: 'Establish entry points and profit-taking levels based on fair value estimate and risk management strategy.',
        timeline: 'Immediate'
      });
    } else if (this.results.upside >= -10) {
      actions.push({
        title: 'Maintain Current Position',
        description: 'Hold existing position while monitoring for catalyst events or significant price movements that may create better opportunities.',
        timeline: 'Medium-term (3-6 months)'
      });
      
      actions.push({
        title: 'Review Quarterly Results',
        description: 'Assess company performance against projections and adjust investment thesis as needed.',
        timeline: 'Quarterly'
      });
    } else {
      actions.push({
        title: 'Consider Position Reduction',
        description: 'Evaluate exit strategy and consider reducing or eliminating position based on unfavorable risk-reward profile.',
        timeline: 'Near-term (2-6 weeks)'
      });
      
      actions.push({
        title: 'Implement Stop-Loss',
        description: 'Set protective stop-loss levels to limit downside risk and preserve capital.',
        timeline: 'Immediate'
      });
    }
    
    actions.push({
      title: 'Stay Updated on Industry Trends',
      description: 'Monitor sector developments, regulatory changes, and competitive landscape that may impact company prospects.',
      timeline: 'Ongoing'
    });
    
    actions.push({
      title: 'Reassess Model Assumptions',
      description: 'Periodically review and update DCF assumptions based on new information and changing market conditions.',
      timeline: 'Semi-annually'
    });
    
    return actions;
  }

  formatLargeNumber(value: number): string {
    if (value >= 1e12) {
      return (value / 1e12).toFixed(2) + 'T';
    } else if (value >= 1e9) {
      return (value / 1e9).toFixed(2) + 'B';
    } else if (value >= 1e6) {
      return (value / 1e6).toFixed(2) + 'M';
    } else if (value >= 1e3) {
      return (value / 1e3).toFixed(2) + 'K';
    }
    return value.toFixed(2);
  }
}