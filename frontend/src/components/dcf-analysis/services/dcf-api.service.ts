import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { LoggerService } from '../../../core/services';

import { config } from '../../../config/config';
import {
  ApiResponse,
  AssumptionTransparency,
  RDConverterResponse,
  DCFValuationResponse,
  NewsSource,
  DCFCalculationRequest,
  ValuationSegment
} from '../models/api-response.interface';

interface ValuationAgentQuickResponse {
  ticker: string;
  company_name?: string;
  valuation_id?: string;
  user_valuation_id?: string;
  dcf?: DCFValuationResponse;
  agent_analysis?: any;
  applied_overrides?: Record<string, any>;
  segments?: Array<Record<string, any>>;
  news_sources?: NewsSource[];
}

@Injectable({
  providedIn: 'root'
})
export class DCFApiService {
  constructor(
    private http: HttpClient,
    private logger: LoggerService
  ) { }

  /**
   * Get baseline valuation directly from valuation-service.
   * Uses POST with empty overrides (minimal override pattern).
   */
  getBaselineValuation(ticker: string): Observable<DCFValuationResponse> {
    const url = `${config.outputAuto}/${ticker}/valuation`;
    return this.http.post<ApiResponse<DCFValuationResponse>>(url, {}).pipe(
      map(response => response.data),
      catchError(error => {
        this.logger.error('Baseline valuation fetch error', error, 'DCFApiService');
        throw error;
      })
    );
  }

  /**
   * Get R&D converter data for industry
   */
  getRDConverterData(
    industry: string,
    marginalTaxRate: number,
    requireRdConverter: boolean = true
  ): Observable<RDConverterResponse> {
    const params = new URLSearchParams({
      industry,
      marginalTaxRate: marginalTaxRate.toString(),
      requireRdConverter: requireRdConverter.toString()
    });

    const url = `${config.calculate}?${params.toString()}`;

    return this.http.get<ApiResponse<RDConverterResponse>>(url).pipe(
      map(response => response.data),
      catchError(error => {
        this.logger.error('R&D converter data fetch error', error, 'DCFApiService');
        throw error;
      })
    );
  }

  /**
   * Calculate DCF valuation
   */
  calculateDCFValuation(
    ticker: string,
    calculationRequest: DCFCalculationRequest
  ): Observable<DCFValuationResponse> {
    const url = `${config.output}/${ticker}/valuation`;


    return this.http.post<ApiResponse<DCFValuationResponse>>(url, calculationRequest).pipe(
      map(response => {
        return response.data;
      }),
      catchError(error => {
        this.logger.error('DCF calculation error', error, 'DCFApiService');
        throw error;
      })
    );
  }

  /**
   * Get automated DCF analysis (quick analysis)
   */
  getAutomatedDCFAnalysis(ticker: string): Observable<DCFValuationResponse> {
    const url = config.outputAutoAgent;

    return this.http.post<ValuationAgentQuickResponse>(url, { ticker }).pipe(
      map(response => this.mapValuationAgentResponseToDCF(response)),
      catchError(error => {
        this.logger.error('Automated DCF analysis error', error, 'DCFApiService');
        throw error;
      })
    );
  }

  private mapValuationAgentResponseToDCF(response: ValuationAgentQuickResponse): DCFValuationResponse {
    const javaDcf = (response?.dcf || {}) as DCFValuationResponse;
    const derivedNarrativeDTO = javaDcf.narrativeDTO || this.buildNarrativeDTO(response);
    const normalizedSegments = this.normalizeSegments(response.segments);

    return {
      ...javaDcf,
      valuation_id: response.valuation_id ?? javaDcf.valuation_id,
      user_valuation_id: response.user_valuation_id ?? javaDcf.user_valuation_id,
      narrativeDTO: (derivedNarrativeDTO as any) ?? null,
      story: javaDcf.story ?? null,
      assumptionTransparency: this.mergeAssumptionTransparency(javaDcf, response),
      newsSources: Array.isArray(response.news_sources) ? response.news_sources : (javaDcf.newsSources || []),
      segments: normalizedSegments.length > 0 ? normalizedSegments : javaDcf.segments,
    };
  }

  private mergeAssumptionTransparency(
    javaDcf: DCFValuationResponse,
    response: ValuationAgentQuickResponse
  ): AssumptionTransparency | undefined {
    const base = javaDcf?.assumptionTransparency
      ? { ...javaDcf.assumptionTransparency }
      : {};

    const instructions = response?.agent_analysis?.dcf_analysis?.dcf_adjustment_instructions;
    if (!Array.isArray(instructions) || instructions.length === 0) {
      return Object.keys(base).length > 0 ? base : undefined;
    }

    const rationales = { ...(base.adjustmentRationales || {}) };
    for (const instruction of instructions) {
      if (!instruction || typeof instruction !== 'object') {
        continue;
      }
      const parameter = String(instruction.parameter || '').trim().toLowerCase();
      const rationale = String(instruction.rationale || '').trim();
      if (!rationale) {
        continue;
      }
      const adjustedValue = this.formatAdjustmentValue(instruction.new_value, instruction.unit);
      if (parameter === 'revenue_cagr') {
        if (!rationales.revenueGrowth) {
          rationales.revenueGrowth = adjustedValue ? `${rationale} (Adjusted to ${adjustedValue})` : rationale;
        }
      } else if (parameter === 'operating_margin') {
        if (!rationales.operatingMargin) {
          rationales.operatingMargin = adjustedValue ? `${rationale} (Adjusted to ${adjustedValue})` : rationale;
        }
      } else if (parameter === 'sales_to_capital' || parameter === 'sales_to_capital_ratio') {
        if (!rationales.salesToCapital) {
          rationales.salesToCapital = adjustedValue ? `${rationale} (Adjusted to ${adjustedValue})` : rationale;
        }
      } else if (parameter === 'wacc' || parameter === 'cost_of_capital') {
        if (!rationales.costOfCapital) {
          rationales.costOfCapital = adjustedValue ? `${rationale} (Adjusted to ${adjustedValue})` : rationale;
        }
      }
    }

    return {
      ...base,
      adjustmentRationales: Object.keys(rationales).length > 0 ? rationales : base.adjustmentRationales,
    };
  }

  private formatAdjustmentValue(rawValue: unknown, rawUnit: unknown): string | null {
    const value = Number(rawValue);
    if (!Number.isFinite(value)) {
      return null;
    }

    const unit = String(rawUnit || '').trim().toLowerCase();
    const rounded = value.toFixed(2);
    if (unit === '%' || unit === 'percent') {
      return `${rounded}%`;
    }
    if (unit === 'x' || unit === 'times' || unit === 'multiple') {
      return `${rounded}x`;
    }
    return rounded;
  }

  private normalizeSegments(segments: Array<Record<string, any>> | undefined): ValuationSegment[] {
    if (!Array.isArray(segments)) {
      return [];
    }

    return segments
      .filter(segment => segment && typeof segment === 'object' && typeof segment['sector'] === 'string')
      .map(segment => ({
        sector: segment['sector'],
        industry: typeof segment['industry'] === 'string' ? segment['industry'] : null,
        components: Array.isArray(segment['components']) ? segment['components'].map(String) : [],
        mappingScore: this.safeNumber(segment['mappingScore'] ?? segment['mapping_score']),
        revenueShare: this.safeNumber(segment['revenueShare'] ?? segment['revenue_share']),
        operatingMargin: this.safeNumber(segment['operatingMargin'] ?? segment['operating_margin']),
      }));
  }

  private safeNumber(value: any): number | null {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }

  private buildNarrativeDTO(response: ValuationAgentQuickResponse): any | null {
    const source = this.pickNarrativeSource(response);
    if (!source) {
      return null;
    }

    const growth = this.normalizeNarrativeBlock(source.growth, 'Growth');
    const margins = this.normalizeNarrativeBlock(source.margins, 'Margins');
    const investmentEfficiency = this.normalizeNarrativeBlock(
      source.investmentEfficiency ?? source.investment_efficiency,
      'Investment Efficiency'
    );
    const risks = this.normalizeNarrativeBlock(source.risks, 'Risks');
    const keyTakeaways = this.normalizeNarrativeBlock(
      source.keyTakeaways ?? source.key_takeaways,
      'Key Takeaways'
    );

    if (!growth && !margins && !investmentEfficiency && !risks && !keyTakeaways) {
      return null;
    }

    return {
      growth,
      margins,
      investmentEfficiency,
      risks,
      keyTakeaways,
      scenarioAnalysis: source.scenarioAnalysis ?? source.scenario_analysis,
      bullBearDebate: source.bullBearDebate ?? source.bull_bear_debate,
    };
  }

  private pickNarrativeSource(response: ValuationAgentQuickResponse): any | null {
    const agent = response?.agent_analysis;
    if (agent && typeof agent === 'object') {
      if (agent.growth || agent.margins || agent.investment_efficiency || agent.key_takeaways) {
        return agent;
      }
    }

    return null;
  }

  private normalizeNarrativeBlock(block: any, defaultTitle: string): any | undefined {
    if (!block || typeof block !== 'object') {
      return undefined;
    }

    const narrative = typeof block.narrative === 'string' ? block.narrative.trim() : '';
    if (!narrative) {
      return undefined;
    }

    return {
      narrative,
      title: (typeof block.title === 'string' && block.title.trim()) ? block.title.trim() : defaultTitle,
    };
  }
}
