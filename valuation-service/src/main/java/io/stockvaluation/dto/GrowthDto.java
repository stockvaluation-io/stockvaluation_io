package io.stockvaluation.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class GrowthDto {
    Double revenueMu;
    Double revenueSigma;
    Double revenueStdDev;
    Double marginMu;
    Double marginSigma;
    Double marginStdDev;

    Double marginMin;
    Double marginMax;
    Double revenueMarginCorrelation;

    List<Double> revenueGrowthRates; 
    List<Double> marginChanges;
}
