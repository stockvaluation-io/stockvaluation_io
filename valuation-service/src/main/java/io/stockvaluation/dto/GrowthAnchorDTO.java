package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO for historical growth rate anchor data derived from Damodaran's
 * "Historical Growth Rate in Earnings" dataset. Used to provide
 * evidence-based growth priors for the valuation engine.
 *
 * Fields mirror the feature table produced by ETL-2
 * (historical_growth_industry_features).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class GrowthAnchorDTO {

    /** Damodaran entity key (e.g. "softwareinternet", "drugspharmaceutical"). */
    private String entity;

    /** Human-readable entity display name. */
    private String entityDisplay;

    /** Region the data belongs to (e.g. "United States", "Emerging Markets"). */
    private String region;

    /** Dataset year. */
    private Integer year;

    /** Number of firms in the Damodaran industry grouping. */
    private Double numberOfFirms;

    /** Return on equity (ROE). */
    private Double roe;

    /** Equity reinvestment rate. */
    private Double equityReinvestmentRate;

    /** Fundamental growth = ROE x Reinvestment Rate. */
    private Double fundamentalGrowth;

    /** Median of historical growth proxies (sales growth, CAGR net income). */
    private Double historicalGrowthProxy;

    /** Median of expected growth proxies (expected growth, EPS growth). */
    private Double expectedGrowthProxy;

    /** Heuristic confidence score [0,1]. */
    private Double confidenceScore;

    /** Growth distribution percentiles. */
    private Double p10;
    private Double p25;
    private Double p50;
    private Double p75;
    private Double p90;

    /** Individual growth rate fields. */
    private Double salesGrowthHistorical;
    private Double cagrNetIncome5y;
    private Double expectedGrowth;
    private Double expectedGrowthEps5y;
    private Double salesGrowthExpected;

    /** Risk metrics. */
    private Double deRatio;
    private Double taxRate;
    private Double leveredBeta;
    private Double stdDevInStock;
}
