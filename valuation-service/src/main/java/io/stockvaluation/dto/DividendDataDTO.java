package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for dividend data from Yahoo Finance.
 * Used for Dividend Discount Model (DDM) calculations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DividendDataDTO {
    
    /**
     * Annual dividend per share (current rate)
     */
    private Double dividendRate;
    
    /**
     * Current dividend yield as decimal (e.g., 0.025 for 2.5%)
     */
    private Double dividendYield;
    
    /**
     * Dividend payout ratio as decimal (dividends / earnings)
     */
    private Double payoutRatio;
    
    /**
     * Trailing twelve months dividend per share
     */
    private Double trailingAnnualDividendRate;
    
    /**
     * Trailing twelve months dividend yield as decimal
     */
    private Double trailingAnnualDividendYield;
    
    /**
     * Ex-dividend date as Unix timestamp
     */
    private Long exDividendDate;
    
    /**
     * Most recent dividend payment amount
     */
    private Double lastDividendValue;
    
    /**
     * Date of most recent dividend as Unix timestamp
     */
    private Long lastDividendDate;
    
    /**
     * Five-year average dividend yield as decimal
     */
    private Double fiveYearAvgDividendYield;
    
    /**
     * Historical dividend payments: date string -> amount
     */
    private Map<String, Double> dividendHistory;
    
    /**
     * Calculated compound annual growth rate of dividends
     * Computed from historical dividend data
     */
    private Double dividendGrowthRate;
    
    /**
     * Check if company pays dividends
     */
    public boolean isDividendPaying() {
        return dividendRate != null && dividendRate > 0;
    }
    
    /**
     * Check if company has sufficient dividend history for DDM
     * Requires at least some dividend history
     */
    public boolean hasSufficientHistory() {
        return dividendHistory != null && dividendHistory.size() >= 4;
    }
    
    /**
     * Check if company is suitable for Dividend Discount Model
     * Requirements:
     * - Pays dividends
     * - Payout ratio > 40% (committed to dividends)
     * - Dividend yield > 0
     */
    public boolean isSuitableForDDM() {
        if (!isDividendPaying()) {
            return false;
        }
        
        // Check payout ratio (DDM works best for high payout companies)
        if (payoutRatio != null && payoutRatio > 0.4) {
            return true;
        }
        
        // Alternatively, check dividend yield
        if (dividendYield != null && dividendYield > 0.01) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get the best available current dividend rate
     */
    public Double getCurrentDividend() {
        if (dividendRate != null && dividendRate > 0) {
            return dividendRate;
        }
        if (trailingAnnualDividendRate != null && trailingAnnualDividendRate > 0) {
            return trailingAnnualDividendRate;
        }
        return 0.0;
    }
    
    /**
     * Get estimated sustainable dividend growth rate
     * Uses calculated rate if available, otherwise estimates from retention ratio
     */
    public Double getEstimatedGrowthRate(Double returnOnEquity) {
        // If we have calculated growth rate, use it (capped at reasonable levels)
        if (dividendGrowthRate != null) {
            return Math.min(Math.max(dividendGrowthRate, -0.10), 0.15); // Cap between -10% and 15%
        }
        
        // Otherwise estimate using g = ROE * retention ratio
        if (payoutRatio != null && returnOnEquity != null) {
            double retentionRatio = 1 - payoutRatio;
            return returnOnEquity * retentionRatio;
        }
        
        // Default to a conservative 3%
        return 0.03;
    }
}

