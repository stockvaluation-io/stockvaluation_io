package io.stockvaluation.dto.valuationoutput;


import java.util.HashMap;
import java.util.Map;

import io.stockvaluation.dto.ValuationTemplate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FinancialDTO {

    /**
     * Total array length = base year (0) + projection years + terminal year
     * Default is 12 for backward compatibility (base + 10 years + terminal)
     * Can be 7 (5-year model), 12 (10-year model), or 17 (15-year model)
     */
    private int arrayLength = 12;

    //     0 for base year , 1 - for year 1 and last index is for terminal year
    private Double[] revenueGrowthRate;
    private Double[] revenues;
    private Double[] ebitOperatingMargin;
    private Double[] ebitOperatingIncome;

    // Company-level aggregated metrics
    private Map<String, Double[]> revenueGrowthRateBySector = new HashMap<>();
    private Map<String, Double[]> revenuesBySector = new HashMap<>();
    private Map<String, Double[]> ebitOperatingMarginBySector = new HashMap<>();
    private Map<String, Double[]> ebitOperatingIncomeSector = new HashMap<>();

    private Double[] taxRate;
    private Double[] ebit1MinusTax;
    private Double[] reinvestment;
    private Double[] fcff;
    private Double[] nol;
    private Double[] costOfCapital;
    private Double[] comulatedDiscountedFactor;
    private Double[] pvFcff;
    private Double[] salesToCapitalRatio;
    private Double[] investedCapital;
    private Double[] roic;
    /**
     * Backward-compatible intrinsic value alias expected by integration clients.
     * Mirrors CompanyDTO.estimatedValuePerShare.
     */
    private Double intrinsicValue;

    // Sector-level detailed metrics (only populated when segments > 1)
    private Map<String, Double[]> ebit1MinusTaxBySector = new HashMap<>();
    private Map<String, Double[]> salesToCapitalRatioBySector = new HashMap<>();
    private Map<String, Double[]> reinvestmentBySector = new HashMap<>();
    private Map<String, Double[]> investedCapitalBySector = new HashMap<>();
    private Map<String, Double[]> fcffBySector = new HashMap<>();
    private Map<String, Double[]> roicBySector = new HashMap<>();
    private Map<String, Double[]> costOfCapitalBySector = new HashMap<>();
    private Map<String, Double[]> pvFcffBySector = new HashMap<>();

    /**
     * Default no-args constructor for backward compatibility
     */
    public FinancialDTO() {
        this.arrayLength = 12; // Default 10-year model
        initializeArrays();
    }

    /**
     * Constructor that accepts a ValuationTemplate to set array length dynamically
     */
    public FinancialDTO(ValuationTemplate template) {
        if (template != null) {
            this.arrayLength = template.getArrayLength();
        }
        initializeArrays();
    }
    /**
     * Initialize all arrays based on arrayLength
     */
    private void initializeArrays() {
        this.revenueGrowthRate = new Double[arrayLength];
        this.revenues = new Double[arrayLength];
        this.ebitOperatingMargin = new Double[arrayLength];
        this.ebitOperatingIncome = new Double[arrayLength];
        this.taxRate = new Double[arrayLength];
        this.ebit1MinusTax = new Double[arrayLength];
        this.reinvestment = new Double[arrayLength];
        this.fcff = new Double[arrayLength];
        this.nol = new Double[arrayLength];
        this.costOfCapital = new Double[arrayLength];
        this.comulatedDiscountedFactor = new Double[arrayLength];
        this.pvFcff = new Double[arrayLength];
        this.salesToCapitalRatio = new Double[arrayLength];
        this.investedCapital = new Double[arrayLength];
        this.roic = new Double[arrayLength];
    }

    /**
     * Helper method to get the array length
     */
    public int getArrayLength() {
        return arrayLength;
    }

    /**
     * Helper method to get projection years (excluding base and terminal)
     */
    public int getProjectionYears() {
        return arrayLength - 2; // base + projection years + terminal
    }

    /**
     * Helper method to get terminal year index
     */
    public int getTerminalYearIndex() {
        return arrayLength - 1;
    }

    /**
     * Helper method to get last projection year index (before terminal)
     */
    public int getLastProjectionYearIndex() {
        return arrayLength - 2;
    }

    
}
