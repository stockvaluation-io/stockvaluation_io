package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe container for segment-weighted parameters
 * This class holds the calculated weighted parameters from applySegmentWeightedParameters
 * and ensures they are used consistently throughout the valuation calculation process
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SegmentWeightedParameters {
    
    // Revenue growth parameters
    private Double weightedRevenueNextYear;
    private Double weightedCompoundAnnualGrowth2_5;
    
    // Operating margin parameters
    private Double weightedOperatingMarginNextYear;
    private Double weightedTargetPreTaxOperatingMargin;
    private Double convergenceYearMargin;
    
    // Sales to capital parameters
    private Double weightedSalesToCapitalYears1To5;
    private Double weightedSalesToCapitalYears6To10;
    
    // Cost of capital parameters
    private Double weightedInitialCostCapital;
    private Double riskFreeRate;
    
    // Industry information
    private String industry;
    
    // Flag to indicate if parameters were calculated from segments
    private boolean isSegmentWeighted = false;
    
    // Number of segments used in calculation
    private int segmentCount = 0;
    
    // Sector-specific parameters - each sector has its own set of parameters
    private Map<String, SectorParameters> sectorParameters = new HashMap<>();
    
    /**
     * Container for sector-specific parameters
     */
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class SectorParameters {
        private String sectorName;
        private Double revenueShare;
        
        // Revenue growth parameters for this sector
        private Double revenueNextYear;
        private Double compoundAnnualGrowth2_5;
        private Double terminalGrowthRate; // Should converge to risk-free rate
        
        // Operating margin parameters for this sector
        private Double operatingMarginNextYear;
        private Double targetPreTaxOperatingMargin;
        private Double convergenceYearMargin;
        
        // Sales to capital parameters for this sector
        private Double salesToCapitalYears1To5;
        private Double salesToCapitalYears6To10;
        
        // Cost of capital parameters for this sector
        private Double initialCostCapital;
        
        // Industry mapping for this sector
        private String industryAsPerExcel;
        
        @Override
        public String toString() {
            return String.format("SectorParameters{sector='%s', share=%.2f, revNext=%.2f, revGrowth2_5=%.2f, " +
                               "terminalGrowth=%.2f, targetMargin=%.2f, sales1_5=%.2f, sales6_10=%.2f, costOfCap=%.2f}",
                               sectorName, revenueShare, revenueNextYear, compoundAnnualGrowth2_5, 
                               terminalGrowthRate, targetPreTaxOperatingMargin, 
                               salesToCapitalYears1To5, salesToCapitalYears6To10, initialCostCapital);
        }
    }
    
    /**
     * Create a copy of this object for thread safety
     */
    public SegmentWeightedParameters copy() {
        SegmentWeightedParameters copy = new SegmentWeightedParameters();
        copy.weightedRevenueNextYear = this.weightedRevenueNextYear;
        copy.weightedCompoundAnnualGrowth2_5 = this.weightedCompoundAnnualGrowth2_5;
        copy.weightedOperatingMarginNextYear = this.weightedOperatingMarginNextYear;
        copy.weightedTargetPreTaxOperatingMargin = this.weightedTargetPreTaxOperatingMargin;
        copy.convergenceYearMargin = this.convergenceYearMargin;
        copy.weightedSalesToCapitalYears1To5 = this.weightedSalesToCapitalYears1To5;
        copy.weightedSalesToCapitalYears6To10 = this.weightedSalesToCapitalYears6To10;
        copy.weightedInitialCostCapital = this.weightedInitialCostCapital;
        copy.riskFreeRate = this.riskFreeRate;
        copy.industry = this.industry;
        copy.isSegmentWeighted = this.isSegmentWeighted;
        copy.segmentCount = this.segmentCount;
        
        // Deep copy sector parameters
        copy.sectorParameters = new HashMap<>();
        for (Map.Entry<String, SectorParameters> entry : this.sectorParameters.entrySet()) {
            SectorParameters original = entry.getValue();
            SectorParameters sectorCopy = new SectorParameters();
            sectorCopy.setSectorName(original.getSectorName());
            sectorCopy.setRevenueShare(original.getRevenueShare());
            sectorCopy.setRevenueNextYear(original.getRevenueNextYear());
            sectorCopy.setCompoundAnnualGrowth2_5(original.getCompoundAnnualGrowth2_5());
            sectorCopy.setTerminalGrowthRate(original.getTerminalGrowthRate());
            sectorCopy.setOperatingMarginNextYear(original.getOperatingMarginNextYear());
            sectorCopy.setTargetPreTaxOperatingMargin(original.getTargetPreTaxOperatingMargin());
            sectorCopy.setConvergenceYearMargin(original.getConvergenceYearMargin());
            sectorCopy.setSalesToCapitalYears1To5(original.getSalesToCapitalYears1To5());
            sectorCopy.setSalesToCapitalYears6To10(original.getSalesToCapitalYears6To10());
            sectorCopy.setInitialCostCapital(original.getInitialCostCapital());
            sectorCopy.setIndustryAsPerExcel(original.getIndustryAsPerExcel());
            copy.sectorParameters.put(entry.getKey(), sectorCopy);
        }
        
        return copy;
    }
    
    /**
     * Check if this contains valid segment-weighted parameters
     */
    public boolean hasValidParameters() {
        return isSegmentWeighted && 
               weightedRevenueNextYear != null && 
               weightedCompoundAnnualGrowth2_5 != null &&
               weightedTargetPreTaxOperatingMargin != null &&
               weightedSalesToCapitalYears1To5 != null &&
               weightedSalesToCapitalYears6To10 != null &&
               weightedInitialCostCapital != null;
    }
    
    /**
     * Get sector-specific parameters for a given sector
     * @param sectorName The name of the sector
     * @return SectorParameters for the sector, or null if not found
     */
    public SectorParameters getSectorParameters(String sectorName) {
        return sectorParameters.get(sectorName);
    }
    
    /**
     * Add or update sector-specific parameters
     * @param sectorName The name of the sector
     * @param parameters The sector parameters
     */
    public void setSectorParameters(String sectorName, SectorParameters parameters) {
        sectorParameters.put(sectorName, parameters);
    }
    
    /**
     * Check if sector-specific parameters are available
     * @return true if sector parameters exist
     */
    public boolean hasSectorParameters() {
        return !sectorParameters.isEmpty();
    }
    
    /**
     * Get all sector names
     * @return Set of sector names
     */
    public java.util.Set<String> getSectorNames() {
        return sectorParameters.keySet();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("SegmentWeightedParameters{segments=%d, weighted=%s, " +
                               "revNext=%.2f, revGrowth2_5=%.2f, targetMargin=%.2f, " +
                               "sales1_5=%.2f, sales6_10=%.2f, costOfCap=%.2f}",
                               segmentCount, isSegmentWeighted, 
                               weightedRevenueNextYear, weightedCompoundAnnualGrowth2_5, 
                               weightedTargetPreTaxOperatingMargin,
                               weightedSalesToCapitalYears1To5, weightedSalesToCapitalYears6To10,
                               weightedInitialCostCapital));
        
        if (hasSectorParameters()) {
            sb.append("\nSector Parameters:");
            for (SectorParameters sector : sectorParameters.values()) {
                sb.append("\n  ").append(sector.toString());
            }
        }
        
        return sb.toString();
    }
}
