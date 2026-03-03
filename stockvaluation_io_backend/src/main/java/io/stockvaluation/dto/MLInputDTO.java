package io.stockvaluation.dto;


import io.stockvaluation.form.FinancialDataInput;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MLInputDTO {

    private FinancialDataInput financials;
    private ValuationOutputDTO dcf;
}
