package io.stockvaluation.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class YearlyCalculation {

    private String year;
    private Double rdExpense;
    private Double unamortizedPortion;
    private Double amortizationThisYear;

}
