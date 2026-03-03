package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class CompanyDataDTO {
    
    @Override
    public String toString() {
        return "CompanyDataDTO [basicInfoDataDTO=" + basicInfoDataDTO + ", financialDataDTO=" + financialDataDTO
                + ", companyDriveDataDTO=" + companyDriveDataDTO + ", growthDto=" + growthDto 
                + ", dividendDataDTO=" + dividendDataDTO + "]";
    }
    
    public CompanyDataDTO(CompanyDataDTO other) {
        if (other != null) {
            this.basicInfoDataDTO = other.basicInfoDataDTO;          // shallow copy
            this.financialDataDTO = other.financialDataDTO;
            this.companyDriveDataDTO = other.companyDriveDataDTO;
            this.growthDto = other.growthDto;
            this.dividendDataDTO = other.dividendDataDTO;
        }
    }
    
    private BasicInfoDataDTO basicInfoDataDTO;
    private FinancialDataDTO financialDataDTO;
    private CompanyDriveDataDTO companyDriveDataDTO;
    private GrowthDto growthDto;
    
    /**
     * Dividend data for DDM (Dividend Discount Model) calculations.
     * Contains dividend rate, yield, payout ratio, and historical dividends.
     */
    private DividendDataDTO dividendDataDTO;
    
    /**
     * Check if company is suitable for Dividend Discount Model
     */
    public boolean isSuitableForDDM() {
        return dividendDataDTO != null && dividendDataDTO.isSuitableForDDM();
    }
    
    /**
     * Check if company pays dividends
     */
    public boolean isDividendPaying() {
        return dividendDataDTO != null && dividendDataDTO.isDividendPaying();
    }
}
