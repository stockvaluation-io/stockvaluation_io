/**
 * Intrinsic Pricing interfaces matching backend DTO structure
 * Peer-based valuation multiples using regression analysis
 */

export interface IntrinsicPricing {
  company: string;
  ticker: string;
  sector?: string;
  peersFound?: number;
  llmEnhanced?: boolean;
  multiples?: { [key: string]: MultipleResult };
  peerList?: string[];
  recommendedMultiple?: string;
  recommendationReason?: string;
  sectorRecommendation?: SectorRecommendation;
  timestamp?: string;
}

export interface MultipleResult {
  intrinsicValue?: number;
  marketValue?: number;
  mispricingPct?: number;
  conclusion?: string; // "Overvalued" | "Undervalued" | "Fair Value"
  confidenceInterval?: ConfidenceInterval;
  distribution?: DistributionStats;
  regression?: RegressionDetails;
  featureImportance?: FeatureImportance[];
  coefficientValidation?: { [key: string]: CoefficientValidation };
  peerComparison?: PeerComparison[];
  nPeersUsed?: number;
  error?: string; // For error cases
}

export interface ConfidenceInterval {
  lowerBound?: number;
  upperBound?: number;
  confidence?: number;
  intervalWidth?: number;
}

export interface DistributionStats {
  median?: number;
  mean?: number;
  std?: number;
  min?: number;
  max?: number;
  percentile25?: number;
  percentile75?: number;
  percentile10?: number;
  percentile90?: number;
  targetPercentile?: number;
  targetPosition?: string;
}

export interface RegressionDetails {
  modelType?: string; // "OLS" | "Ridge"
  rSquared?: number;
  nSamples?: number;
  features?: string[];
  coefficients?: { [key: string]: number };
  marketCapWeighted?: boolean;
  similarityWeighted?: boolean;
  vifScores?: { [key: string]: number }; // VIF scores for multicollinearity
  highVifWarning?: { [key: string]: number }; // Features with VIF > 10
}

export interface FeatureImportance {
  feature: string;
  coefficient: number;
  absCoefficient: number;
}

export interface CoefficientValidation {
  coefficient?: number;
  expectedSign?: string; // "positive" | "negative" | "varies"
  actualSign?: string; // "positive" | "negative"
  matchesTheory?: boolean;
  vif?: number; // VIF score if available
  highMulticollinearity?: boolean; // VIF > 10
  violationLikelyDueToMulticollinearity?: boolean;
}

export interface PeerComparison {
  ticker: string;
  companyName?: string;
  multipleValue?: number; // The actual multiple value for this peer
  features?: { [key: string]: number }; // Feature values used in regression
}

export interface SectorRecommendation {
  multiple?: string;
  sector?: string;
  rationale?: string;
  rSquared?: number;
}

