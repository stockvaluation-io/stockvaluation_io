package io.stockvaluation.form;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDriveDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.dto.GrowthDto;
import io.stockvaluation.dto.OverrideAssumption;
import io.stockvaluation.dto.SegmentResponseDTO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class FinancialDataInput {

    public String toString() {
        return "FinancialDataInput [basicInfoDataDTO=" + basicInfoDataDTO + ", financialDataDTO=" + financialDataDTO
                + ", companyDriveDataDTO=" + companyDriveDataDTO + ", growthDto=" + growthDto
                + ", isExpensesCapitalize=" + isExpensesCapitalize + ", hasOperatingLease=" + hasOperatingLease
                + ", companyRiskLevel=" + companyRiskLevel + ", hasEmployeeOptions=" + hasEmployeeOptions
                + ", numberOfOptions=" + numberOfOptions + ", averageStrikePrice=" + averageStrikePrice
                + ", averageMaturity=" + averageMaturity + ", stockPriceStdDev=" + stockPriceStdDev
                + ", overrideAssumptionCostCapital=" + overrideAssumptionCostCapital
                + ", overrideAssumptionReturnOnCapital="
                + overrideAssumptionReturnOnCapital + ", overrideAssumptionProbabilityOfFailure="
                + overrideAssumptionProbabilityOfFailure
                + ", overrideAssumptionReinvestmentLag=" + overrideAssumptionReinvestmentLag
                + ", overrideAssumptionTaxRate="
                + overrideAssumptionTaxRate + ", overrideAssumptionNOL=" + overrideAssumptionNOL
                + ", overrideAssumptionRiskFreeRate="
                + overrideAssumptionRiskFreeRate + ", overrideAssumptionGrowthRate=" + overrideAssumptionGrowthRate
                + ", overrideAssumptionCashPosition=" + overrideAssumptionCashPosition + ", revenueNextYear="
                + revenueNextYear
                + ", operatingMarginNextYear=" + operatingMarginNextYear + ", compoundAnnualGrowth2_5="
                + compoundAnnualGrowth2_5
                + ", targetPreTaxOperatingMargin=" + targetPreTaxOperatingMargin + ", convergenceYearMargin="
                + convergenceYearMargin
                + ", salesToCapitalYears1To5=" + salesToCapitalYears1To5 + ", salesToCapitalYears6To10="
                + salesToCapitalYears6To10
                + ", riskFreeRate=" + riskFreeRate + ", initialCostCapital=" + initialCostCapital + ", industry="
                + industry + ", segments=" + segments + ", sectorOverrides=" + sectorOverrides + "]";
    }

    private BasicInfoDataDTO basicInfoDataDTO;
    private FinancialDataDTO financialDataDTO;
    private CompanyDriveDataDTO companyDriveDataDTO;
    private GrowthDto growthDto;
    private Boolean isExpensesCapitalize = false;
    private Boolean hasOperatingLease = false;
    private String companyRiskLevel;
    private Boolean hasEmployeeOptions = false;
    private Double numberOfOptions = 0.0;
    private Double averageStrikePrice = 0.0;
    private Double averageMaturity = 0.0;
    private Double stockPriceStdDev = 0.0;
    private OverrideAssumption overrideAssumptionCostCapital;
    private OverrideAssumption overrideAssumptionReturnOnCapital;
    private OverrideAssumption overrideAssumptionProbabilityOfFailure;
    private OverrideAssumption overrideAssumptionReinvestmentLag;
    private OverrideAssumption overrideAssumptionTaxRate;
    private OverrideAssumption overrideAssumptionNOL;
    private OverrideAssumption overrideAssumptionRiskFreeRate;
    private OverrideAssumption overrideAssumptionGrowthRate;
    private OverrideAssumption overrideAssumptionCashPosition;
    private Double revenueNextYear;
    @JsonAlias("initialOperatingMargin")
    private Double operatingMarginNextYear;
    private Double compoundAnnualGrowth2_5;
    private Double targetPreTaxOperatingMargin;
    private Double convergenceYearMargin;
    private Double salesToCapitalYears1To5;
    private Double salesToCapitalYears6To10;
    private Double riskFreeRate;
    @JsonAlias("wacc")
    private Double initialCostCapital;
    private Double terminalGrowthRate; // User override for terminal growth rate (%)
    private String industry;
    private SegmentResponseDTO segments;
    private List<SectorParameterOverride> sectorOverrides;

    public FinancialDataInput(FinancialDataInput financialDataInput) {
        if (financialDataInput != null) {
            this.basicInfoDataDTO = financialDataInput.basicInfoDataDTO;
            this.financialDataDTO = financialDataInput.financialDataDTO;
            this.companyDriveDataDTO = financialDataInput.companyDriveDataDTO;
            this.growthDto = financialDataInput.growthDto;
            this.isExpensesCapitalize = financialDataInput.isExpensesCapitalize;
            this.hasOperatingLease = financialDataInput.hasOperatingLease;
            this.companyRiskLevel = financialDataInput.companyRiskLevel;
            this.hasEmployeeOptions = financialDataInput.hasEmployeeOptions;
            this.numberOfOptions = financialDataInput.numberOfOptions;
            this.averageStrikePrice = financialDataInput.averageStrikePrice;
            this.averageMaturity = financialDataInput.averageMaturity;
            this.stockPriceStdDev = financialDataInput.stockPriceStdDev;
            this.overrideAssumptionCostCapital = financialDataInput.overrideAssumptionCostCapital;
            this.overrideAssumptionReturnOnCapital = financialDataInput.overrideAssumptionReturnOnCapital;
            this.overrideAssumptionProbabilityOfFailure = financialDataInput.overrideAssumptionProbabilityOfFailure;
            this.overrideAssumptionReinvestmentLag = financialDataInput.overrideAssumptionReinvestmentLag;
            this.overrideAssumptionTaxRate = financialDataInput.overrideAssumptionTaxRate;
            this.overrideAssumptionNOL = financialDataInput.overrideAssumptionNOL;
            this.overrideAssumptionRiskFreeRate = financialDataInput.overrideAssumptionRiskFreeRate;
            this.overrideAssumptionGrowthRate = financialDataInput.overrideAssumptionGrowthRate;
            this.overrideAssumptionCashPosition = financialDataInput.overrideAssumptionCashPosition;
            this.revenueNextYear = financialDataInput.revenueNextYear;
            this.operatingMarginNextYear = financialDataInput.operatingMarginNextYear;
            this.compoundAnnualGrowth2_5 = financialDataInput.compoundAnnualGrowth2_5;
            this.targetPreTaxOperatingMargin = financialDataInput.targetPreTaxOperatingMargin;
            this.convergenceYearMargin = financialDataInput.convergenceYearMargin;
            this.salesToCapitalYears1To5 = financialDataInput.salesToCapitalYears1To5;
            this.salesToCapitalYears6To10 = financialDataInput.salesToCapitalYears6To10;
            this.riskFreeRate = financialDataInput.riskFreeRate;
            this.initialCostCapital = financialDataInput.initialCostCapital;
            this.terminalGrowthRate = financialDataInput.terminalGrowthRate;
            this.industry = financialDataInput.industry;
            this.segments = financialDataInput.segments;
            this.sectorOverrides = financialDataInput.sectorOverrides != null
                    ? new ArrayList<>(financialDataInput.sectorOverrides)
                    : null;
        }
    }

    /**
     * Gets sector overrides, ensuring non-null return value
     * 
     * @return List of sector overrides (never null)
     */
    public List<SectorParameterOverride> getSectorOverrides() {
        return sectorOverrides != null ? sectorOverrides : new ArrayList<>();
    }

}
