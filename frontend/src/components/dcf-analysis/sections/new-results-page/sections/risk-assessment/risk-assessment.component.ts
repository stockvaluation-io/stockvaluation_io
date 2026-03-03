import { Component, Input, OnInit, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';

import { CompanyData, ValuationResults } from '../../../../models';
import { MetricCardComponent, MetricCardData } from '../../shared/components/metric-card/metric-card.component';
import { ChartWrapperComponent, ChartData } from '../../shared/components/chart-wrapper/chart-wrapper.component';

interface RiskFactor {
  category: string;
  level: 'low' | 'medium' | 'high' | 'critical';
  impact: number; // 1-10 scale
  probability: number; // 1-10 scale
  description: string;
  mitigation: string;
}

interface RiskScoreCard {
  title: string;
  score: number;
  maxScore: number;
  level: 'low' | 'medium' | 'high' | 'critical';
  factors: string[];
}

@Component({
    selector: 'app-risk-assessment-section',
    imports: [
        CommonModule,
        MetricCardComponent,
        ChartWrapperComponent
    ],
    template: `
    <section class="full-width-section-container">
      <header class="section-header">
        <h2 class="section-title">
          <i class="pi pi-shield" aria-hidden="true"></i>
          Risk Assessment
        </h2>
        <p class="section-description">
          Comprehensive investment risk analysis and volatility assessment
        </p>
      </header>
      
      <div class="section-content">
        <!-- Risk Metrics Overview -->
        <div class="metrics-grid">
          <app-metric-card 
            *ngFor="let metric of riskMetrics"
            [data]="metric"
            size="medium">
          </app-metric-card>
        </div>

        <!-- Overall Risk Score -->
        <div class="risk-score-container">
          <h3 class="score-title">
            <i class="pi pi-gauge"></i>
            Overall Investment Risk Score
          </h3>
          
          <div class="risk-gauge">
            <div class="gauge-container">
              <div class="gauge-arc">
                <div class="gauge-fill" 
                     [style.transform]="'rotate(' + getGaugeRotation() + 'deg)'"
                     [class]="'level-' + overallRiskLevel">
                </div>
              </div>
              <div class="gauge-center">
                <div class="gauge-score">{{ overallRiskScore }}</div>
                <div class="gauge-label">Risk Score</div>
              </div>
            </div>
            
            <div class="gauge-legend">
              <div class="legend-item" [class]="overallRiskLevel === 'low' ? 'active' : ''">
                <div class="legend-color low"></div>
                <span>Low (1-3)</span>
              </div>
              <div class="legend-item" [class]="overallRiskLevel === 'medium' ? 'active' : ''">
                <div class="legend-color medium"></div>
                <span>Medium (4-6)</span>
              </div>
              <div class="legend-item" [class]="overallRiskLevel === 'high' ? 'active' : ''">
                <div class="legend-color high"></div>
                <span>High (7-8)</span>
              </div>
              <div class="legend-item" [class]="overallRiskLevel === 'critical' ? 'active' : ''">
                <div class="legend-color critical"></div>
                <span>Critical (9-10)</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Risk Categories Chart -->
        <div class="risk-chart">
          <app-chart-wrapper
            type="radar"
            [chartData]="riskRadarData"
            title="Risk Profile Analysis"
            subtitle="Multi-dimensional risk assessment across key categories"
            size="large">
          </app-chart-wrapper>
        </div>

        <!-- Risk Factor Details -->
        <div class="risk-factors">
          <h3 class="factors-title">
            <i class="pi pi-exclamation-triangle"></i>
            Key Risk Factors
          </h3>
          
          <div class="factors-grid">
            <div *ngFor="let factor of riskFactors" 
                 class="risk-factor-card"
                 [class]="'level-' + factor.level">
              <div class="factor-header">
                <div class="factor-info">
                  <h4 class="factor-category">{{ factor.category }}</h4>
                  <span class="factor-level" [class]="'level-' + factor.level">
                    {{ factor.level | titlecase }} Risk
                  </span>
                </div>
                <div class="factor-scores">
                  <div class="score-item">
                    <span class="score-label">Impact</span>
                    <div class="score-bar">
                      <div class="score-fill" 
                           [style.width.%]="factor.impact * 10"
                           [class]="'level-' + factor.level">
                      </div>
                    </div>
                    <span class="score-value">{{ factor.impact }}/10</span>
                  </div>
                  <div class="score-item">
                    <span class="score-label">Probability</span>
                    <div class="score-bar">
                      <div class="score-fill" 
                           [style.width.%]="factor.probability * 10"
                           [class]="'level-' + factor.level">
                      </div>
                    </div>
                    <span class="score-value">{{ factor.probability }}/10</span>
                  </div>
                </div>
              </div>
              
              <div class="factor-content">
                <p class="factor-description">{{ factor.description }}</p>
                <div class="factor-mitigation">
                  <strong>Mitigation:</strong> {{ factor.mitigation }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Risk Score Cards -->
        <div class="risk-scorecards">
          <h3 class="scorecards-title">
            <i class="pi pi-th-large"></i>
            Risk Category Breakdown
          </h3>
          
          <div class="scorecards-grid">
            <div *ngFor="let scoreCard of riskScoreCards" 
                 class="risk-scorecard"
                 [class]="'level-' + scoreCard.level">
              <div class="scorecard-header">
                <h4 class="scorecard-title">{{ scoreCard.title }}</h4>
                <div class="scorecard-score">
                  <span class="score-current">{{ scoreCard.score }}</span>
                  <span class="score-separator">/</span>
                  <span class="score-max">{{ scoreCard.maxScore }}</span>
                </div>
              </div>
              
              <div class="scorecard-progress">
                <div class="progress-bar">
                  <div class="progress-fill" 
                       [style.width.%]="(scoreCard.score / scoreCard.maxScore) * 100"
                       [class]="'level-' + scoreCard.level">
                  </div>
                </div>
                <span class="progress-label" [class]="'level-' + scoreCard.level">
                  {{ scoreCard.level | titlecase }} Risk
                </span>
              </div>
              
              <div class="scorecard-factors">
                <div class="factors-label">Key Factors:</div>
                <ul class="factors-list">
                  <li *ngFor="let factor of scoreCard.factors">{{ factor }}</li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        <!-- Risk Mitigation Recommendations -->
        <div class="risk-recommendations">
          <h3 class="recommendations-title">
            <i class="pi pi-lightbulb"></i>
            Risk Mitigation Recommendations
          </h3>
          
          <div class="recommendations-grid">
            <div class="recommendation-card immediate">
              <div class="recommendation-header">
                <i class="pi pi-clock"></i>
                <h4>Immediate Actions</h4>
              </div>
              <ul class="recommendation-list">
                <li>Monitor key financial metrics quarterly</li>
                <li>Diversify revenue streams to reduce concentration risk</li>
                <li>Maintain strong liquidity buffers</li>
                <li>Implement robust risk management frameworks</li>
              </ul>
            </div>
            
            <div class="recommendation-card medium-term">
              <div class="recommendation-header">
                <i class="pi pi-calendar"></i>
                <h4>Medium-term Strategy</h4>
              </div>
              <ul class="recommendation-list">
                <li>Build competitive moats through innovation</li>
                <li>Expand into defensive market segments</li>
                <li>Strengthen balance sheet and reduce leverage</li>
                <li>Develop scenario planning capabilities</li>
              </ul>
            </div>
            
            <div class="recommendation-card long-term">
              <div class="recommendation-header">
                <i class="pi pi-flag"></i>
                <h4>Long-term Vision</h4>
              </div>
              <ul class="recommendation-list">
                <li>Build sustainable competitive advantages</li>
                <li>Create multiple growth vectors</li>
                <li>Develop crisis management protocols</li>
                <li>Foster adaptive organizational culture</li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </section>
  `,
    styleUrls: ['../section-base.scss', './risk-assessment.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class RiskAssessmentSection implements OnInit {
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;

  riskMetrics: MetricCardData[] = [];
  riskRadarData!: ChartData;
  riskFactors: RiskFactor[] = [];
  riskScoreCards: RiskScoreCard[] = [];
  overallRiskScore = 0;
  overallRiskLevel: 'low' | 'medium' | 'high' | 'critical' = 'medium';

  ngOnInit(): void {
    this.setupRiskMetrics();
    this.setupRiskFactors();
    this.setupRiskScoreCards();
    this.setupRiskRadarChart();
    this.calculateOverallRisk();
  }

  private setupRiskMetrics(): void {
    const volatility = this.calculateVolatility();
    const leverage = this.calculateLeverage();
    const liquidity = this.calculateLiquidity();
    const marketRisk = this.calculateMarketRisk();

    this.riskMetrics = [
      {
        title: 'Volatility Risk',
        value: volatility,
        format: 'percentage',
        precision: 1,
        status: volatility > 30 ? 'negative' : volatility > 20 ? 'warning' : 'positive',
        icon: 'chart-line',
        subtitle: 'Price volatility measure',
        trend: volatility > 25 ? 'down' : 'up'
      },
      {
        title: 'Financial Leverage',
        value: leverage,
        format: 'number',
        precision: 2,
        status: leverage > 3 ? 'negative' : leverage > 2 ? 'warning' : 'positive',
        icon: 'balance-scale',
        subtitle: 'Debt-to-equity ratio',
        trend: leverage > 2.5 ? 'down' : 'up'
      },
      {
        title: 'Liquidity Score',
        value: liquidity,
        format: 'number',
        precision: 1,
        status: liquidity > 2 ? 'positive' : liquidity > 1.5 ? 'neutral' : 'negative',
        icon: 'tint',
        subtitle: 'Current ratio measure',
        trend: liquidity > 2 ? 'up' : 'down'
      },
      {
        title: 'Market Risk Beta',
        value: marketRisk,
        format: 'number',
        precision: 2,
        status: marketRisk > 1.5 ? 'negative' : marketRisk > 1.2 ? 'warning' : 'positive',
        icon: 'globe',
        subtitle: 'Market correlation',
        trend: marketRisk > 1.3 ? 'down' : 'up'
      }
    ];
  }

  private setupRiskFactors(): void {
    this.riskFactors = [
      {
        category: 'Market Competition',
        level: 'high',
        impact: 8,
        probability: 7,
        description: 'Intense competition from established players and new market entrants poses significant revenue pressure.',
        mitigation: 'Focus on product differentiation, customer loyalty programs, and operational efficiency improvements.'
      },
      {
        category: 'Economic Downturn',
        level: 'medium',
        impact: 7,
        probability: 5,
        description: 'Economic recession could reduce consumer spending and demand for products/services.',
        mitigation: 'Diversify customer base, maintain flexible cost structure, and build cash reserves.'
      },
      {
        category: 'Regulatory Changes',
        level: 'medium',
        impact: 6,
        probability: 6,
        description: 'New regulations could increase compliance costs and operational complexity.',
        mitigation: 'Maintain active regulatory monitoring, engage with policymakers, and ensure compliance readiness.'
      },
      {
        category: 'Technology Disruption',
        level: 'high',
        impact: 9,
        probability: 6,
        description: 'Rapid technological changes could make current business model obsolete.',
        mitigation: 'Invest in R&D, form strategic partnerships, and maintain innovation pipeline.'
      },
      {
        category: 'Key Personnel Risk',
        level: 'medium',
        impact: 5,
        probability: 4,
        description: 'Loss of key management or technical personnel could disrupt operations.',
        mitigation: 'Implement succession planning, competitive compensation, and knowledge transfer programs.'
      },
      {
        category: 'Supply Chain Disruption',
        level: 'medium',
        impact: 6,
        probability: 5,
        description: 'Supply chain interruptions could impact production and increase costs.',
        mitigation: 'Diversify supplier base, maintain strategic inventory, and develop alternative sourcing options.'
      }
    ];
  }

  private setupRiskScoreCards(): void {
    this.riskScoreCards = [
      {
        title: 'Financial Risk',
        score: 6,
        maxScore: 10,
        level: 'medium',
        factors: ['Leverage ratio', 'Cash flow stability', 'Liquidity position', 'Interest coverage']
      },
      {
        title: 'Operational Risk',
        score: 7,
        maxScore: 10,
        level: 'high',
        factors: ['Supply chain', 'Key personnel', 'Process efficiency', 'Quality control']
      },
      {
        title: 'Market Risk',
        score: 8,
        maxScore: 10,
        level: 'high',
        factors: ['Competition intensity', 'Market saturation', 'Customer concentration', 'Pricing pressure']
      },
      {
        title: 'Strategic Risk',
        score: 5,
        maxScore: 10,
        level: 'medium',
        factors: ['Technology disruption', 'Innovation pipeline', 'Market positioning', 'Growth sustainability']
      }
    ];
  }

  private setupRiskRadarChart(): void {
    this.riskRadarData = {
      labels: ['Financial', 'Operational', 'Market', 'Strategic', 'Regulatory', 'Technology'],
      datasets: [
        {
          label: 'Current Risk Level',
          data: [6, 7, 8, 5, 4, 7],
          backgroundColor: 'rgba(239, 68, 68, 0.2)',
          borderColor: '#ef4444',
          borderWidth: 2
        },
        {
          label: 'Industry Average',
          data: [5, 6, 6, 6, 5, 6],
          backgroundColor: 'rgba(156, 163, 175, 0.2)',
          borderColor: '#9ca3af',
          borderWidth: 2
        }
      ]
    };
  }

  private calculateOverallRisk(): void {
    const weights = { financial: 0.3, operational: 0.25, market: 0.25, strategic: 0.2 };
    this.overallRiskScore = Number((
      this.riskScoreCards[0].score * weights.financial +
      this.riskScoreCards[1].score * weights.operational +
      this.riskScoreCards[2].score * weights.market +
      this.riskScoreCards[3].score * weights.strategic
    ).toFixed(2));

    if (this.overallRiskScore <= 3) this.overallRiskLevel = 'low';
    else if (this.overallRiskScore <= 6) this.overallRiskLevel = 'medium';
    else if (this.overallRiskScore <= 8) this.overallRiskLevel = 'high';
    else this.overallRiskLevel = 'critical';
  }

  private calculateVolatility(): number {
    // Simplified volatility calculation based on results
    const upside = Math.abs(this.results.upside);
    return Math.min(50, upside * 1.5); // Cap at 50%
  }

  private calculateLeverage(): number {
    // Mock calculation - would use actual financial data
    return 2.1 + Math.random() * 1.5;
  }

  private calculateLiquidity(): number {
    // Mock calculation - would use actual financial data
    return 1.8 + Math.random() * 1.0;
  }

  private calculateMarketRisk(): number {
    // Mock calculation - would use actual beta data
    return 1.1 + Math.random() * 0.6;
  }

  getGaugeRotation(): number {
    // Convert score (1-10) to degrees (-90 to 90)
    return -90 + (this.overallRiskScore / 10) * 180;
  }
}