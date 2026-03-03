import { Component, Input, OnInit, OnChanges, ChangeDetectionStrategy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

import { IntrinsicPricing, MultipleResult } from '../../../../models';
import { formatPercentage, formatCompactCurrency, createCurrencyContext } from '../../../../utils/formatting.utils';
import { ThemeService } from '../../../../../../core/services';

@Component({
  selector: 'app-intrinsic-pricing-section',
  imports: [CommonModule],
  template: `
    <div class="intrinsic-pricing-section" id="intrinsic-pricing">
      <h3 class="section-title">
        <i class="pi pi-users section-icon"></i>
        Peer Comparison - Intrinsic Valuation
      </h3>
      
      <div class="section-description" *ngIf="intrinsicPricing?.recommendedMultiple">
        Recommended Multiple: <strong>{{ getMultipleDisplayName(intrinsicPricing.recommendedMultiple || '') }}</strong>
        <span *ngIf="intrinsicPricing.recommendationReason"> ({{ intrinsicPricing.recommendationReason }})</span>
      </div>
      
      <!-- Summary Card -->
      <div class="summary-card" *ngIf="intrinsicPricing?.peersFound">
        <div class="summary-item">
          <div class="summary-label">Peers Analyzed</div>
          <div class="summary-value">{{ intrinsicPricing.peersFound }}</div>
        </div>
        <div class="summary-item" *ngIf="intrinsicPricing.llmEnhanced">
          <div class="summary-label">LLM Enhanced</div>
          <div class="summary-value success">✓</div>
        </div>
        <div class="summary-item" *ngIf="intrinsicPricing.sector">
          <div class="summary-label">Sector</div>
          <div class="summary-value">{{ intrinsicPricing.sector }}</div>
        </div>
      </div>
      
      <!-- Multiples Table -->
      <div class="multiples-table-container" *ngIf="intrinsicPricing?.multiples">
        <div class="table-wrapper">
          <table class="multiples-table">
            <thead>
              <tr>
                <th class="col-multiple">Multiple</th>
                <th class="col-intrinsic">Intrinsic Value</th>
                <th class="col-market">Market Value</th>
                <th class="col-mispricing">Mispricing</th>
                <th class="col-conclusion">Conclusion</th>
                <th class="col-r2">R²</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let entry of getMultipleEntries()" 
                  [class.recommended]="entry.key === intrinsicPricing.recommendedMultiple"
                  [class.has-error]="entry.value.error">
                <td class="col-multiple">
                  <span class="multiple-name">{{ getMultipleDisplayName(entry.key) }}</span>
                  <span class="recommended-badge" *ngIf="entry.key === intrinsicPricing?.recommendedMultiple">Recommended</span>
                </td>
                <td class="col-intrinsic">
                  <span *ngIf="entry.value.intrinsicValue !== null && entry.value.intrinsicValue !== undefined">
                    {{ formatValue(entry.value.intrinsicValue) }}
                  </span>
                  <span *ngIf="entry.value.error" class="error-text">{{ entry.value.error }}</span>
                </td>
                <td class="col-market">
                  <span *ngIf="entry.value.marketValue !== null && entry.value.marketValue !== undefined">
                    {{ formatValue(entry.value.marketValue) }}
                  </span>
                </td>
                <td class="col-mispricing">
                  <span *ngIf="entry.value.mispricingPct !== null && entry.value.mispricingPct !== undefined"
                        [class.positive]="entry.value.mispricingPct > 0"
                        [class.negative]="entry.value.mispricingPct < 0">
                    {{ formatMispricingPercentage(entry.value.mispricingPct) }}
                  </span>
                </td>
                <td class="col-conclusion">
                  <span class="conclusion-badge" 
                        [class.overvalued]="entry.value.conclusion === 'Overvalued'"
                        [class.undervalued]="entry.value.conclusion === 'Undervalued'"
                        [class.fair-value]="entry.value.conclusion === 'Fair Value'">
                    {{ entry.value.conclusion || 'N/A' }}
                  </span>
                </td>
                <td class="col-r2">
                  <span *ngIf="entry.value.regression?.rSquared !== null && entry.value.regression?.rSquared !== undefined">
                    <span class="r2-value">{{ formatR2(entry.value.regression?.rSquared ?? 0) }}</span>
                    <span class="r2-bar">
                      <span class="r2-fill" [style.width.%]="((entry.value.regression?.rSquared ?? 0) * 100)"></span>
                    </span>
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      
      <!-- Peer List -->
      <div class="peer-list-section" *ngIf="intrinsicPricing?.peerList && (intrinsicPricing.peerList?.length ?? 0) > 0">
        <h4 class="peer-list-title">
          <i class="pi pi-building"></i>
          Peer Companies ({{ intrinsicPricing.peerList?.length || 0 }})
        </h4>
        <div class="peer-chips">
          <a *ngFor="let peer of (intrinsicPricing.peerList || [])" 
             [href]="getPeerLink(peer)"
             target="_blank"
             rel="noopener noreferrer"
             class="peer-chip">
            {{ peer }}
            <i class="pi pi-external-link"></i>
          </a>
        </div>
      </div>
      
      <!-- Sector Recommendation -->
      <div class="sector-recommendation" *ngIf="intrinsicPricing?.sectorRecommendation">
        <div class="recommendation-card">
          <h4>Sector Recommendation</h4>
          <p><strong>{{ getMultipleDisplayName(intrinsicPricing.sectorRecommendation?.multiple || '') }}</strong></p>
          <p class="recommendation-rationale" *ngIf="intrinsicPricing.sectorRecommendation?.rationale">
            {{ intrinsicPricing.sectorRecommendation?.rationale }}
          </p>
          <p class="recommendation-r2" *ngIf="intrinsicPricing.sectorRecommendation?.rSquared !== null && intrinsicPricing.sectorRecommendation?.rSquared !== undefined">
            R²: {{ formatR2(intrinsicPricing.sectorRecommendation?.rSquared ?? 0) }}
          </p>
        </div>
      </div>
    </div>
  `,
  styleUrls: ['../section-base.scss', './intrinsic-pricing.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IntrinsicPricingSection implements OnInit, OnChanges {
  @Input() intrinsicPricing!: IntrinsicPricing;
  @Input() ticker!: string;

  themeService = inject(ThemeService);
  currencyCtx = createCurrencyContext('USD');

  ngOnInit(): void {
    // Component initialization
  }

  ngOnChanges(): void {
    // Handle changes
  }

  getMultipleEntries(): Array<{ key: string, value: MultipleResult }> {
    if (!this.intrinsicPricing?.multiples) {
      return [];
    }
    return Object.entries(this.intrinsicPricing.multiples).map(([key, value]) => ({ key, value }));
  }

  getMultipleDisplayName(multipleKey: string): string {
    const displayNames: { [key: string]: string } = {
      'PE': 'P/E',
      'PE_Forward': 'Forward P/E',
      'PBV': 'P/B',
      'PEG': 'PEG',
      'EV_Sales': 'EV/Sales',
      'EV_EBITDA': 'EV/EBITDA',
      'EV_EBIT': 'EV/EBIT',
      'EV_IC': 'EV/Invested Capital',
      'EV_FCFF': 'EV/FCFF'
    };
    return displayNames[multipleKey] || multipleKey;
  }

  formatValue(value: number): string {
    if (value === null || value === undefined || isNaN(value)) {
      return 'N/A';
    }
    // For multiples, show 2 decimal places
    return value.toFixed(2);
  }

  formatR2(r2: number | null | undefined): string {
    if (r2 === null || r2 === undefined || isNaN(r2)) {
      return 'N/A';
    }
    return (r2 * 100).toFixed(1) + '%';
  }

  formatMispricingPercentage(mispricingPct: number | null | undefined): string {
    if (mispricingPct === null || mispricingPct === undefined || isNaN(mispricingPct)) {
      return 'N/A';
    }
    // mispricingPct is already a percentage (e.g., 36.8 for 36.8%)
    return formatPercentage(mispricingPct / 100);
  }

  getPeerLink(peerTicker: string): string {
    return `/automated-dcf-analysis/${peerTicker}/valuation`;
  }
}
