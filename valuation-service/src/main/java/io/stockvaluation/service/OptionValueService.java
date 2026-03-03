package io.stockvaluation.service;

import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.OptionValueResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class OptionValueService {

    private final CommonService commonService;

    public OptionValueResultDTO calculateOptionValue(String ticker, Double strikePrice, Double avgMaturity, Double optionStanding, Double standardDeviation) {
        String formattedTicker = ticker.toUpperCase(Locale.ROOT);
        CompanyDataDTO companyDataDTO = commonService.getCompanyDataFromProvider(formattedTicker);
        if (Objects.isNull(companyDataDTO)) {
            log.info("There is no company data for the entered ticker");
            throw new RuntimeException("No company data found for the entered ticker");
        }
        // user inputs field
        double currentStockPrice = companyDataDTO.getFinancialDataDTO().getStockPrice();
        double riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate(); // in %

        double d1Res = calculateD1(currentStockPrice, strikePrice, riskFreeRate, standardDeviation, avgMaturity);
        double nd1 = calculateNd1(d1Res);
        double d2 = calculateD2(d1Res, standardDeviation, avgMaturity);
        double nd2 = calculateNd2(d2);

        log.info("d1 res is = {}", format(d1Res));
        log.info("nd1 res is = {}", format(nd1));
        log.info("d2 res is = {}", format(d2));
        log.info("nd2 res is = {}", format(nd2));

        double valuePerOption = calculateValuePerOption(
                currentStockPrice,
                strikePrice,
                avgMaturity,
                nd1,
                nd2,
                riskFreeRate / 100,
                0.0
        );
        log.info("value per option = {}", valuePerOption);

        double valueOfAllOptionsOutstanding = valuePerOption * optionStanding;
        log.info("valueOfAllOptionsOutstanding = {}", valueOfAllOptionsOutstanding);

        return new OptionValueResultDTO(valuePerOption, valueOfAllOptionsOutstanding);
    }

    // calculate value of d1
    public static double calculateD1(double currentStockPrice, double strikePrice, double riskFreeRate, double standardDeviation, double avgMaturity) {
        // formula =  double d1 = (Math.log(currentStockPrice/strikePrice) + (riskFreeRate + standardDeviation * standardDeviation/2) * avgMaturity) / (standardDeviation * Math.sqrt(avgMaturity));
        riskFreeRate = riskFreeRate / 100;
        standardDeviation = standardDeviation / 100;
        double result = (Math.log(currentStockPrice / strikePrice) + (riskFreeRate + standardDeviation * standardDeviation / 2) * avgMaturity) / (standardDeviation * Math.sqrt(avgMaturity));
        log.info("d1 = {}", result);

        return result;

    }


    public static double calculateD2(double d1, double standardDeviation, double avgMaturity) {
//      formula =   d1 - standardDeviation * Math.sqrt(avgMaturity);
        standardDeviation = standardDeviation / 100; // in % that's why devide by 100
        return d1 - standardDeviation * Math.sqrt(avgMaturity);

    }


    public double calculateNd1(double d1) {
        double result = cumulativeStandardNormal(d1);
        log.info("N(d1) = {}", result);
        return result;
    }

    public static double calculateNd2(double d2) {
        double result = cumulativeStandardNormal(d2);
        log.info("Nd2 : {} ", result);
        return result;
    }

    public static double calculateValuePerOption(double currentStockPrice, double strikePrice, double avgMaturity, double nd1, double nd2, double riskFreeRate, double annualizedDividendYield) {
        // Calculate the first exponential term: EXP((0-annualizedDividendYield) * expirationYears)
        double expTerm1 = Math.exp(-(annualizedDividendYield * avgMaturity));
        // Calculate the second exponential term: EXP((0-tBondRate) * expirationYears)
        double expTerm2 = Math.exp(-(riskFreeRate * avgMaturity));
        // Apply the formula: (EXP((0-D19)*B19) * B17 * B23) - (B18 * EXP((0-D17)*B19) * B26)
        return (expTerm1 * currentStockPrice * nd1) - (strikePrice * expTerm2 * nd2);
    }

    private static double cumulativeStandardNormal(double value) {
        return new NormalDistribution().cumulativeProbability(value);
    }

    private String format(double value) {
        return new DecimalFormat("0.##").format(value);
    }

}
