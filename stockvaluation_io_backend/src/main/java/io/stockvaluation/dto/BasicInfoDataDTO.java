package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@AllArgsConstructor

@NoArgsConstructor

@Setter
@Getter
public class BasicInfoDataDTO {

    @Override
    public String toString() {
        return "BasicInfoDataDTO [ticker=" + ticker + ", dateOfValuation=" + dateOfValuation + ", companyName="
                + companyName + ", countryOfIncorporation=" + countryOfIncorporation + ", industryUs=" + industryUs
                + ", industryGlobal=" + industryGlobal + ", currency=" + currency + ", stockCurrency=" + stockCurrency
                + ", summary=" + summary + ", compensationRisk=" + compensationRisk + ", marketCap=" + marketCap
                + ", heldPercentInstitutions=" + heldPercentInstitutions + ", heldPercentInsiders=" + heldPercentInsiders
                + ", firstTradeDateEpochUtc=" + firstTradeDateEpochUtc + ", debtToEquity=" + debtToEquity
                + ", timeZoneFullName=" + timeZoneFullName + ", beta=" + beta + "]";
    }
    private String ticker;

    private LocalDate dateOfValuation;

    private String companyName;

    private String website;
    
    private String countryOfIncorporation;

    private String industryUs;

    private String industryGlobal;

    private String currency;

    private String stockCurrency;

    private String summary;

    private Integer compensationRisk;

    private Long marketCap;

    private Double heldPercentInstitutions;

    private Double heldPercentInsiders;

    private Integer firstTradeDateEpochUtc;

    private Double debtToEquity;

    private String timeZoneFullName;

    private Double beta;
}
