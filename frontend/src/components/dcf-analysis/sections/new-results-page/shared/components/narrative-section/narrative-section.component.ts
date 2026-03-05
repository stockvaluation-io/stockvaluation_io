import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { createCurrencyContext, formatPrice } from '../../../../../utils/formatting.utils';
import { LearnTermsPanelService } from '../../../../../../../core/services/ui/learn-terms-panel.service';
import { LoggerService, ThemeService } from '../../../../../../../core/services';
import { ChartWrapperComponent, ChartData } from '../chart-wrapper/chart-wrapper.component';
import { CompanyData, ValuationResults } from '../../../../../models';
import { SensitivityHeatmapComponent, HeatMapData } from '../sensitivity-heatmap/sensitivity-heatmap.component';

export interface BullBearDebateRound {
  bear: string;
  bull: string;
  round: number;
}

export interface ScenarioAnalysisData {
  optimistic: {
    description: string;
    keyChanges: string[];
    valuationImpact: string;
    investmentThesis: string;
    intrinsicValue: number;
    adjustments: {
      revenueGrowthRate: (number | null)[];
      operatingMargin: number[];
      salesToCapitalRatio: (number | null)[];
      discountRate: (number | null)[];
    };
  };
  base_case: {
    description: string;
    keyChanges: string[];
    valuationImpact: string;
    investmentThesis: string;
    intrinsicValue: number;
    adjustments: {
      revenueGrowthRate: (number | null)[];
      operatingMargin: number[];
      salesToCapitalRatio: (number | null)[];
      discountRate: (number | null)[];
    };
  };
  pessimistic: {
    description: string;
    keyChanges: string[];
    valuationImpact: string;
    investmentThesis: string;
    intrinsicValue: number;
    adjustments: {
      revenueGrowthRate: (number | null)[];
      operatingMargin: number[];
      salesToCapitalRatio: (number | null)[];
      discountRate: (number | null)[];
    };
  };
}

export interface NarrativeData {
  growth?: {
    narrative?: string;
    title?: string;
  };
  margins?: {
    narrative?: string;
    title?: string;
  };
  investment_efficiency?: {
    narrative?: string;
    title?: string;
  };
  risks?: {
    narrative?: string;
    title?: string;
  };
  key_takeaways?: {
    narrative?: string;
    title?: string;
  };
  bull_bear_debate?: BullBearDebateRound[];
  scenarioAnalysis?: ScenarioAnalysisData;
  narrativeDTO?: {
    growth?: {
      narrative?: string;
      title?: string;

    };
    margins?: {
      narrative?: string;
      title?: string;
    };
    investment_efficiency?: {
      narrative?: string;
      title?: string;
    };
    risks?: {
      narrative?: string;
      title?: string;
    };
    key_takeaways?: {
      narrative?: string;
      title?: string;
    };
    scenarioAnalysis?: ScenarioAnalysisData;
  };
}

export interface GroupedTableScenario {
  assumption: string;
  value: number;
  isBase?: boolean;
}

export interface NarrativeMetric {
  label: string;
  value?: string | number;
  format?: 'percentage' | 'currency' | 'number' | 'grouped-table';
  scenarios?: GroupedTableScenario[];
}

export interface NarrativeSection {
  title?: string;
  content: string;
  icon: string;
  metrics?: NarrativeMetric[];
  type?: 'narrative' | 'bull-bear-debate' | 'sensitivity-analysis';
  debateRounds?: BullBearDebateRound[];
  heatMapData?: HeatMapData;
}

@Component({
  selector: 'app-narrative-section',
  standalone: true,
  imports: [CommonModule, FormsModule, ChartWrapperComponent],
  templateUrl: './narrative-section.component.html',
  styleUrls: ['./narrative-section.component.scss']
})
export class NarrativeSectionComponent implements OnInit {
  @Input() narrativeData: NarrativeData | null = null;
  @Input() ticker: string = '';
  @Input() currency: string = 'USD';
  @Input() stockCurrency: string = 'USD';
  @Input() company!: CompanyData;
  @Input() results!: ValuationResults;
  @Input() heatMapData: HeatMapData | null = null;

  // Inject ThemeService
  public themeService = inject(ThemeService);

  // Chart data properties
  growthStoryChartData!: ChartData;
  profitabilityStoryChartData!: ChartData;
  riskStoryChartData!: ChartData;
  competitiveAdvantagesChartData!: ChartData;
  cashFlowChartData!: ChartData;

  narrativeSections: NarrativeSection[] = [];
  currencyCtx: ReturnType<typeof createCurrencyContext> | null = null;
  // Learn More slider state and data
  get isLearnOpen() { return this.learnPanel.isOpen; }
  learnSearch = '';
  learnTerms: Array<{ key: string; title: string; simple: string; analogy: string; technical?: string; link?: string; }> = [
    { key: 'wacc', title: 'WACC (Weighted Average Cost of Capital)', simple: 'The average annual return investors expect from this company for taking risk.', analogy: "Think of it as the 'interest rate' the company pays to use other people's money.", technical: 'WACC = (E/V)·Re + (D/V)·Rd·(1 – Tc) where E=equity, D=debt, V=E+D.', link: 'https://en.wikipedia.org/wiki/Weighted_average_cost_of_capital' },
    { key: 'terminal-growth', title: 'Terminal Growth Rate', simple: 'The steady growth rate a company is assumed to achieve far into the future.', analogy: 'Like a plant that matures—after early spurts, it grows slowly at a stable pace.', technical: 'Often capped near long‑term GDP/inflation; used in Gordon Growth formula.', link: 'https://en.wikipedia.org/wiki/Gordon_growth_model' },
    { key: 'fcff', title: 'FCFF (Free Cash Flow to Firm)', simple: 'Cash the business generates after operations and reinvestment, before financing.', analogy: 'Household cash left after bills and necessary upkeep, before paying the bank.', technical: 'FCFF = EBIT·(1–Tax) + Depreciation – Capex – ∆WorkingCapital.', link: 'https://en.wikipedia.org/wiki/Free_cash_flow' }
  ];

  constructor(private logger: LoggerService, public learnPanel: LearnTermsPanelService) { }

  ngOnInit(): void {
    // Initialize currency context
    this.currencyCtx = createCurrencyContext(this.currency, this.stockCurrency);

    // Initialize chart data if results are available
    if (this.results && this.company) {
      this.setupChartData();
    }

    // Only build narrative sections if we have valid narrative data
    if (this.narrativeData) {
      this.buildNarrativeSections();
    } else {
      // Ensure narrativeSections is empty when no data is available
      this.narrativeSections = [];
    }
  }

  get filteredLearnTerms() {
    const q = (this.learnSearch || '').toLowerCase();
    if (!q) return this.learnTerms;
    return this.learnTerms.filter(t =>
      t.title.toLowerCase().includes(q) ||
      t.simple.toLowerCase().includes(q) ||
      t.analogy.toLowerCase().includes(q) ||
      (t.technical || '').toLowerCase().includes(q)
    );
  }

  toggleLearnPanel(open?: boolean) { if (open === true) this.learnPanel.open(); else if (open === false) this.learnPanel.close(); else this.learnPanel.toggle(); }

  private buildNarrativeSections(): void {
    this.narrativeSections = [];

    // Check for legacy narrative data first
    if (this.narrativeData?.growth?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.growth.title,
        content: this.narrativeData.growth.narrative,
        icon: 'pi-cog',
        type: 'narrative'
        //metrics: this.buildKeyAssumptionsMetrics()
      });
    }

    if (this.narrativeData?.margins?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.margins.title,
        content: this.narrativeData.margins.narrative,
        icon: 'pi-chart-line',
        type: 'narrative',
        //metrics: this.buildValueDriversMetrics()
      });
    }

    if (this.narrativeData?.investment_efficiency?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.investment_efficiency.title,
        content: this.narrativeData.investment_efficiency.narrative,
        icon: 'pi-calculator',
        type: 'narrative',
        //metrics: this.buildValuationSummaryMetrics()
      });
    }

    if (this.narrativeData?.risks?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.risks.title,
        content: this.narrativeData.risks.narrative,
        icon: 'pi-exclamation-triangle',
        type: 'narrative',
        //metrics: this.buildSensitivityMetrics()
      });
    }

    if (this.narrativeData?.key_takeaways?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.key_takeaways.title,
        content: this.narrativeData.key_takeaways.narrative,
        icon: 'pi-lightbulb',
        type: 'narrative'
      });
    }

    // Check for narrativeDTO data (new structure)
    if (this.narrativeData?.narrativeDTO?.growth?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.narrativeDTO.growth.title,
        content: this.narrativeData.narrativeDTO.growth.narrative,
        icon: 'pi-cog',
        type: 'narrative',
        //metrics: this.buildKeyAssumptionsMetricsFromDTO()
      });
    }

    if (this.narrativeData?.narrativeDTO?.margins?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.narrativeDTO.margins.title,
        content: this.narrativeData.narrativeDTO.margins.narrative,
        icon: 'pi-chart-line',
        type: 'narrative',
        //metrics: this.buildValueDriversMetricsFromDTO()
      });
    }

    if (this.narrativeData?.narrativeDTO?.investment_efficiency?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.narrativeDTO.investment_efficiency.title,
        content: this.narrativeData.narrativeDTO.investment_efficiency.narrative,
        icon: 'pi-calculator',
        type: 'narrative',
        //metrics: this.buildValuationSummaryMetricsFromDTO()
      });
    }

    if (this.narrativeData?.narrativeDTO?.risks?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.narrativeDTO.risks.title,
        content: this.narrativeData.narrativeDTO.risks.narrative,
        icon: 'pi-exclamation-triangle',
        type: 'narrative',
        //metrics: this.buildSensitivityMetricsFromDTO()
      });
    }

    if (this.narrativeData?.narrativeDTO?.key_takeaways?.narrative) {
      this.narrativeSections.push({
        title: this.narrativeData.narrativeDTO.key_takeaways.title,
        content: this.narrativeData.narrativeDTO.key_takeaways.narrative,
        icon: 'pi-lightbulb',
        type: 'narrative'
      });
    }

    // Add Sensitivity Analysis section (BEFORE Bull vs Bear Debate)
    if (this.heatMapData && this.hasValidHeatmap(this.heatMapData)) {
      this.narrativeSections.push({
        title: 'Sensitivity Analysis',
        content: 'How intrinsic value changes with growth rate and discount rate',
        icon: 'pi-th-large',
        type: 'sensitivity-analysis',
        heatMapData: this.heatMapData
      });
    }

    // Add Bull vs Bear Debate section
    const bullBearDebate = this.narrativeData?.bull_bear_debate

    if (bullBearDebate && bullBearDebate.length > 0) {
      this.narrativeSections.push({
        title: 'Bull vs Bear Debate',
        content: 'Bull vs Bear debate analysis with multiple rounds of arguments.',
        icon: 'pi-comments',
        type: 'bull-bear-debate',
        debateRounds: bullBearDebate
      });
    }

  }

  convertToBulletPoints(text: string): string[] {
    const sentences = text.split(/\.\s+/);
    return sentences
      .filter(sentence => sentence.trim().length > 0)
      .map(sentence => sentence.trim() + (sentence.endsWith('.') ? '' : '.'));
  }

  /**
   * Keep numbers exactly as the LLM wrote them — no reformatting.
   */
  formatNumbers(value: string): string {
    return value;
  }

  /**
   * Format and structure LLM narrative text for readability.
   *
   * Problems solved:
   *  1. LLM produces one dense wall of text — we split it into 2–4 readable paragraphs.
   *  2. Excessive em-dashes (— or --) are cleaned up.
   *  3. Dollar amounts, percentages and CAGR are subtly colour-highlighted.
   *
   * Paragraph-splitting strategy:
   *  - First honour any explicit \n\n paragraph breaks.
   *  - If none exist, split at sentence boundaries where the next sentence
   *    "pivots" to a new topic: identified by starting with a capitalised
   *    proper noun/entity OR after every ~3 sentences to keep chunks digestible.
   */
  highlightNumbers(text: string): string {
    if (!text) return '';

    let result = text;

    // ── Step 1: Normalise punctuation ────────────────────────────────────────
    // Convert em-dashes (and double-hyphens used as em-dashes) to a comma+space
    // so the text flows as natural prose rather than feeling like code comments.
    result = result.replace(/\s*---+\s*/g, ', ');
    result = result.replace(/\s*--\s*/g, ', ');
    result = result.replace(/\s*\u2014\s*/g, ', ');

    // Collapse tilde spacing: "~ 29 %" → "~29%"
    result = result.replace(/~\s+/g, '~');

    // ── Step 2: Colour-highlight numbers (before paragraph-splitting) ────────
    // Dollar values — keep the full phrase include unit (e.g. $305B, $1.4 trillion)
    const dollarRegex = /\$\s*[\d,.]+(?:\s*(?:billion|million|thousand|trillion|[KMBTkmbt]))?/gi;
    result = result.replace(dollarRegex, m =>
      m.includes('<span') ? m : `<span class="narrative-number narrative-number-currency">${m.trim()}</span>`
    );

    // Percentages
    const pctRegex = /~?\d+(?:\.\d+)?\s*%/gi;
    result = result.replace(pctRegex, m =>
      m.includes('<span') ? m : `<span class="narrative-number narrative-number-percentage">${m.trim()}</span>`
    );

    // CAGR / R²
    result = result.replace(/\b(CAGR|R²)\b/gi, m =>
      m.includes('<span') ? m : `<span class="narrative-number narrative-number-keyword">${m}</span>`
    );

    // ── Step 3: Paragraph segmentation ──────────────────────────────────────
    // 3a. If the LLM already used blank lines, honour them.
    const explicitParas = result.split(/\n\s*\n/).map(s => s.trim()).filter(Boolean);
    if (explicitParas.length > 1) {
      return explicitParas.map(p => `<p class="narrative-paragraph">${p}</p>`).join('');
    }

    // 3b. Single block — split at sentence boundaries into groups of ~3 sentences.
    //     A "sentence end" is a period/!/? that is:
    //       - not preceded by an abbreviation (e.g. "Mr.", "vs.", single caps)
    //       - followed by a space and an uppercase letter
    const sentenceEndRe = /(?<![A-Z]\.|[Vv]s\.|[Ee]tc\.|[Ii]nc\.|[Cc]o\.|[Nn]o\.)([.!?])\s+(?=[A-Z])/g;

    // Collect sentence-end positions
    const splits: number[] = [];
    let m: RegExpExecArray | null;
    // reset lastIndex
    sentenceEndRe.lastIndex = 0;
    while ((m = sentenceEndRe.exec(result)) !== null) {
      splits.push(m.index + m[1].length); // position right after the period
    }

    if (splits.length === 0) {
      // No splits found — single paragraph
      return `<p class="narrative-paragraph">${result}</p>`;
    }

    // Group into chunks of ~3 sentences (target 3 sentences per paragraph)
    const SENTENCES_PER_PARA = 3;
    const paragraphs: string[] = [];
    let start = 0;
    for (let i = SENTENCES_PER_PARA - 1; i < splits.length; i += SENTENCES_PER_PARA) {
      const splitAt = splits[i];
      const chunk = result.slice(start, splitAt).trim();
      if (chunk) paragraphs.push(chunk);
      start = splitAt;
    }
    // Remainder
    const tail = result.slice(start).trim();
    if (tail) paragraphs.push(tail);

    // Merge a very short last paragraph (< 60 chars) into the previous one
    if (paragraphs.length > 1 && paragraphs[paragraphs.length - 1].replace(/<[^>]+>/g, '').length < 60) {
      paragraphs[paragraphs.length - 2] += ' ' + paragraphs.pop();
    }

    return paragraphs.map(p => `<p class="narrative-paragraph">${p.trim()}</p>`).join('');
  }

  highlightNumbersAndBreakLines(text: string): string {
    // 1. Highlight and format numbers
    const numberRegex =
      /(\d+(?:\.\d+)?%?|\$\d+(?:\.\d+)?(?:[KMBTkmbtkMBT])?|\d+(?:\.\d+)?(?:\s*(?:billion|million|thousand|percent|%))?)/gi;

    let highlighted = text.replace(numberRegex, (match) => {
      const formatted = this.formatNumbers(match);
      return `<span class="narrative-number">${formatted}</span>`;
    });

    // 2. Break into sentences safely (same regex you had)
    const sentenceRegex = /(?<!\b\w)(?<!\b[A-Z][a-z]\.)(?<!\d)\.(?=\s+[A-Z]|$)/g;
    let broken = highlighted.replace(sentenceRegex, '.|SPLIT|');

    // 3. Split into array & wrap in <li>
    const sentences = broken
      .split('|SPLIT|')
      .map((s) => s.trim())
      .filter((s) => s.length > 0)
      .map((s) => `<li>${s}</li>`)
      .join('\n');

    // 4. Wrap in <ul>
    return `<ul class="custom-list">\n${sentences}\n</ul>`;
  }

  formatMetricValue(value: string | number, format?: 'percentage' | 'currency' | 'number' | 'grouped-table'): string {
    // Handle grouped-table format - this should not be called for grouped tables
    if (format === 'grouped-table') {
      this.logger.warn('formatMetricValue called with grouped-table format - this should be handled in template', undefined, 'NarrativeSectionComponent');
      return '';
    }

    if (typeof value === 'string') {
      // Check if it's a currency string with format like "X.XB INR"
      const currencyPattern = /^([\d.]+[KMBT]?)\s+([A-Z]{3})$/;
      const match = value.match(currencyPattern);
      if (match) {
        const [, amount, currency] = match;
        return `${amount} <span class="currency-code">${currency}</span>`;
      }
      return value;
    }

    switch (format) {
      case 'percentage':
        return `${value}%`;
      case 'currency':
        const formattedPrice = this.currencyCtx?.formatPrice(value) || formatPrice(value);
        // Split the formatted price to style the currency code
        const parts = formattedPrice.split(' ');
        if (parts.length >= 2) {
          const numberPart = parts.slice(0, -1).join(' ');
          const currencyPart = parts[parts.length - 1];
          return `${numberPart} <span class="currency-code">${currencyPart}</span>`;
        }
        return formattedPrice;
      case 'number':
        return value.toLocaleString();
      default:
        return value.toString();
    }
  }

  trackByTitle(index: number, section: NarrativeSection): string {
    return section.title ?? '';
  }

  shouldShowAdForSection(section: NarrativeSection): boolean {
    // Only show ad for the "Key Takeaways" section that doesn't have metrics
    return section.title === 'Key Takeaways' && (!section.metrics || section.metrics.length === 0);
  }

  Math = Math;

  isPercentageMetric(metric: NarrativeMetric): boolean {
    return metric.format === 'percentage' && typeof metric.value === 'number';
  }

  getProgressWidth(value: string | number): number {
    return typeof value === 'number' ? Math.min(value, 100) : 0;
  }

  // Chart setup and generation methods
  private setupChartData(): void {
    if (!this.results || !this.company) return;

    this.growthStoryChartData = this.generateGrowthStoryChartData();
    this.profitabilityStoryChartData = this.generateProfitabilityStoryChartData();
    this.riskStoryChartData = this.generateRiskStoryChartData();
    this.competitiveAdvantagesChartData = this.generateCompetitiveAdvantagesChartData();
    this.cashFlowChartData = this.generateCashFlowChartData();
  }

  /**
   * Get theme-aware colors for charts
   */
  private getThemeColors() {
    const isDark = this.themeService.currentTheme() === 'dark';

    return {
      primary: isDark ? '#60A5FA' : '#2563eb',
      secondary: isDark ? '#34D399' : '#059669',
      warning: isDark ? '#FBBF24' : '#d97706',
      danger: isDark ? '#F87171' : '#dc2626',
      purple: isDark ? '#A78BFA' : '#7c3aed',
      cyan: isDark ? '#22D3EE' : '#0891b2',
      orange: isDark ? '#FB923C' : '#ea580c',
      lime: isDark ? '#A3E635' : '#65a30d'
    };
  }

  generateGrowthStoryChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);
    const companyGrowthRates = this.results.projections.map(p => p.revenue_growth_rate || 0);

    return {
      labels: years,
      datasets: [
        {
          label: `${this.company.name} Growth Rate (%)`,
          data: companyGrowthRates,
          borderColor: this.getThemeColors().primary,
          backgroundColor: this.getThemeColors().primary,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };
  }

  generateProfitabilityStoryChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);
    const companyOperatingMargins = this.results.projections.map(p => p.operating_margin || 0);

    return {
      labels: years,
      datasets: [
        {
          label: `${this.company.name} Operating Margin (%)`,
          data: companyOperatingMargins,
          borderColor: this.getThemeColors().warning,
          backgroundColor: this.getThemeColors().warning,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };
  }

  generateRiskStoryChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);
    const costOfCapital = this.results.projections.map(p => p.cost_of_capital || 0);
    const returnOnCapital = this.results.projections.map(p => p.roic || 0);

    return {
      labels: years,
      datasets: [
        {
          label: 'Required Return (Cost of Capital) (%)',
          data: costOfCapital,
          borderColor: this.getThemeColors().danger,
          backgroundColor: this.getThemeColors().danger,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        },
        {
          label: 'Return on Invested Capital (%)',
          data: returnOnCapital,
          borderColor: this.getThemeColors().secondary,
          backgroundColor: this.getThemeColors().secondary,
          fill: false,
          tension: 0.3,
          borderWidth: 2
        }
      ]
    };
  }

  generateCompetitiveAdvantagesChartData(): ChartData {
    const metrics = ['Revenue Growth', 'Operating Margin'];

    const marketExpectedGrowth = this.results.calibrationGrowth || 0;
    const marketExpectedMargin = this.results.calibrationMargin || 0;

    const actualGrowth = this.results.projections && this.results.projections.length > 0
      ? (this.results.projections[1]?.revenue_growth_rate || 0)
      : 0;
    const actualMargin = this.results.operatingMarginCompany || 0;

    return {
      labels: metrics,
      datasets: [
        {
          label: 'Market Expectations',
          data: [marketExpectedGrowth, marketExpectedMargin],
          backgroundColor: this.getThemeColors().danger,
          borderColor: this.getThemeColors().danger,
          borderWidth: 1
        },
        {
          label: `${this.company.name} Projections`,
          data: [actualGrowth, actualMargin],
          backgroundColor: this.getThemeColors().primary,
          borderColor: this.getThemeColors().primary,
          borderWidth: 1
        }
      ]
    };
  }

  generateCashFlowChartData(): ChartData {
    if (!this.results.projections || this.results.projections.length === 0) {
      return { labels: [], datasets: [] };
    }

    const currentYear = new Date().getFullYear();
    const years = this.results.projections.map((_, index) => `${currentYear + index + 1}`);

    // Get currency symbol
    const getCurrencySymbol = (currency: string): string => {
      const symbols: { [key: string]: string } = {
        'USD': '$', 'EUR': '€', 'GBP': '£', 'JPY': '¥', 'INR': '₹'
      };
      return symbols[currency] || currency;
    };

    // Determine scale for cash flow data
    const fcfValues = this.results.projections.map(p => Math.abs(p.free_cash_flow || 0));
    const reinvestmentValues = this.results.projections.map(p => Math.abs(p.reinvestment || 0));
    const allValues = [...fcfValues, ...reinvestmentValues];
    const maxValue = Math.max(...allValues, 1);

    let factor = 1;
    let suffix = '';
    if (maxValue >= 1e12) {
      factor = 1e12;
      suffix = 'T';
    } else if (maxValue >= 1e9) {
      factor = 1e9;
      suffix = 'B';
    } else if (maxValue >= 1e6) {
      factor = 1e6;
      suffix = 'M';
    } else if (maxValue >= 1e3) {
      factor = 1e3;
      suffix = 'K';
    }

    return {
      labels: years,
      datasets: [
        {
          label: `Free Cash Flow (${getCurrencySymbol(this.results.currency)}${suffix})`,
          data: this.results.projections.map(p => (p.free_cash_flow || 0) / factor),
          backgroundColor: this.getThemeColors().secondary,
          borderColor: this.getThemeColors().secondary,
          borderWidth: 1
        },
        {
          label: `Reinvestment (${getCurrencySymbol(this.results.currency)}${suffix})`,
          data: this.results.projections.map(p => (p.reinvestment || 0) / factor),
          backgroundColor: this.getThemeColors().danger,
          borderColor: this.getThemeColors().danger,
          borderWidth: 1
        }
      ]
    };
  }

  hasValidHeatmap(data: HeatMapData): boolean {
    return !!(
      data &&
      data.growth_rates &&
      data.discount_rates &&
      data.valuations &&
      data.growth_rates.length > 0 &&
      data.discount_rates.length > 0 &&
      data.valuations.length > 0 &&
      data.valuations.every(row => row && row.length > 0)
    );
  }

}
