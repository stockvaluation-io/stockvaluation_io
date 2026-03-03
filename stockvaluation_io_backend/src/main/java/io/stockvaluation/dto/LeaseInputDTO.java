package io.stockvaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class LeaseInputDTO {
    private double leaseExpenseCurrentYear;
    private double[] commitments;
    private double futureCommitment;
    private double costOfDebt;
}
