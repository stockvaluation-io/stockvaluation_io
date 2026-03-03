package io.stockvaluation.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class BasicInfoDataForm {

    private String companyName;

    private LocalDate dateOfValuation;

    private String countryOfIncorporation;

    private String industryUS;

    private String industryGlobal;
}
