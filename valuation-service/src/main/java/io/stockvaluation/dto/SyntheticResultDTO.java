package io.stockvaluation.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SyntheticResultDTO {
    private String interestCoverageRatio;
    private String estimatedBondRating;
    private String companyDefaultSpread;
    private String countryDefaultSpread;
    private String costOfDebt;

    public SyntheticResultDTO(String interestCoverageRatio, String estimatedBondRating, String companyDefaultSpread, String countryDefaultSpread, String costOfDebt) {
        this.interestCoverageRatio = interestCoverageRatio;
        this.estimatedBondRating = estimatedBondRating;
        this.companyDefaultSpread = companyDefaultSpread;
        this.countryDefaultSpread = countryDefaultSpread;
        this.costOfDebt = costOfDebt;
    }

}
