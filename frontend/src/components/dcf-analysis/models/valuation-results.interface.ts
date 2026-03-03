import { IntrinsicPricing } from './intrinsic-pricing.interface';
import { AssumptionTransparency } from './api-response.interface';

export interface ValuationResults {
  // Key Results
  intrinsicValue: number;
  currentPrice: number;
  upside: number;

  // Valuation Components
  equityValue: number;
  enterpriseValue: number;
  terminalValue: number;
  fcfValue: number;

  // Per Share Metrics
  dcfValuePerShare: number;
  bookValuePerShare: number;
  priceToBook: number;
  priceAsPercentageOfValue?: number; // From API: (currentPrice / fairValue - 1) * 100

  // Analysis Metadata
  analysisDate: string;
  currency: string;
  stockCurrency?: string; // Currency of the stock trading (can be different from DCF currency)
  valuation_id?: string; // UUID of the saved valuation in Supabase
  user_valuation_id?: string; // UUID of the user_valuations link record

  // Story Cards Data
  growthStory?: string;
  profitabilityStory?: string;
  riskStory?: string;
  competitiveAdvantages?: string;
  assumptionTransparency?: AssumptionTransparency;

  // Equity Waterfall Data
  valueOfOperatingAssets?: number;
  debt?: number;
  minorityInterests?: number;
  cash?: number;
  numberOfShares?: number;

  // Terminal Value Data
  terminalCashFlow?: number;
  terminalCostOfCapital?: number;
  pvTerminalValue?: number;
  terminalGrowthRate?: number;
  terminalReturnOnCapital?: number;
  terminalReinvestmentRate?: number;

  // Industry Comparison Data
  revenueGrowthCompany?: number;
  revenueGrowthIndustry?: number;
  operatingMarginCompany?: number;
  operatingMarginIndustry?: number;

  // Market Expectations (Calibration Data)
  calibrationGrowth?: number;
  calibrationMargin?: number;
  newsSources?: {
    title: string;
    url: string;
    source?: string;
    category?: string;
  }[];

  // Narrative Data (for storytelling)
  narratives?: {
    key_assumptions?: {
      narrative?: string;
      growth_rate?: {
        initial?: number;
        terminal?: number;
      };
      cost_of_capital?: {
        initial?: number;
        terminal?: number;
      };
      operating_margin?: {
        average?: number;
      };
      terminal_growth_rate?: number;
    };
    value_drivers?: {
      narrative?: string;
      terminal_value_contribution?: {
        percentage_of_total?: number;
      };
      explicit_period_pv?: {
        usd_billions?: number;
      };
      terminal_value_pv?: {
        usd_billions?: number;
      };
    };
    valuation_summary?: {
      narrative?: string;
      intrinsic_value_per_share?: {
        usd?: number;
      };
      current_market_price?: {
        usd?: number;
      };
      premium_to_intrinsic?: {
        percentage?: number;
      };
    };
    sensitivity_and_uncertainties?: {
      narrative?: string;
      sensitivity_examples?: {
        terminal_growth_rate?: {
          [key: string]: number;
        };
        wacc?: {
          [key: string]: number;
        };
      };
    };
    key_takeaways?: {
      narrative?: string;
    };
  };

  // Detailed Projections
  projections: YearlyProjection[];
  
  // Intrinsic Pricing (peer-based valuation multiples)
  intrinsicPricing?: IntrinsicPricing;
  intrinsicPricingV2?: IntrinsicPricing; // V2 version with improved R²

  // Monte Carlo simulation results (probabilistic DCF)
  monteCarloResult?: import('./api-response.interface').MonteCarloResult;

  // Real options analysis data (formerly optionalityPremium)
  realOptionAnalysis?: {
    company?: string;
    valuation_date?: string;
    base_company_value: number;
    total_real_options_value: number;
    enhanced_company_value: number;
    option_premium_percentage: number;
    value_per_share?: number;
    options?: Array<{
      option_type: string;
      confidence?: number;
      description?: string;
      underlying_value?: number;
      exercise_price?: number;
      time_to_expiry_years?: number;
      volatility?: number;
      pricing_methods?: {
        black_scholes?: number;
        american_binomial?: number;
        monte_carlo_lsm?: number;
      };
      final_option_value: number;
    }>;
  };

  // Base64-encoded Manim DCF animation video
  valuation_animation_base64?: string;
}

export interface YearlyProjection {
  year: number;
  revenue: number;
  revenue_growth_rate?: number;
  ebitda: number;
  ebitda_margin: number;
  operating_income?: number;
  operating_margin?: number;
  ebit_after_tax?: number;
  reinvestment?: number;
  capex: number;
  working_capital_change: number;
  free_cash_flow: number;
  cost_of_capital?: number;
  sales_to_capital_ratio?: number;
  roic?: number;
  discount_factor: number;
  present_value: number;
  // Sector-specific data (when available)
  sectorData?: {
    [sectorName: string]: SectorProjection;
  };
}

export interface SectorProjection {
  revenue?: number;
  revenue_growth_rate?: number;
  operating_income?: number;
  operating_margin?: number;
  ebit_after_tax?: number;
  reinvestment?: number;
  free_cash_flow?: number;
  cost_of_capital?: number;
  sales_to_capital_ratio?: number;
  roic?: number;
  present_value?: number;
  invested_capital?: number;
}

