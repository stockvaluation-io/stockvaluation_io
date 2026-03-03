package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InputRequestDTO {
    private String dateOfValuation;
    private String companyName;
    private String ticker;
    private String currency;
    private String industryUS;
    private String industryGlo;
    private Double currentYearExpense;
    private Double totalRevenue;
    private Double operatingIncome;
    private Boolean hasRAndDExpensesToCapitalize;
    private Double marginalTaxRate;
    private List<PastExpenseRequestDTO> pastExpense;
}
