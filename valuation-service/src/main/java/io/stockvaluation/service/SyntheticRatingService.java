package io.stockvaluation.service;

import io.stockvaluation.config.SyntheticRatingProperties;
import io.stockvaluation.config.ValuationAssumptionProperties;
import io.stockvaluation.domain.CountryEquity;
import io.stockvaluation.domain.LargeBondSpread;
import io.stockvaluation.domain.SmallBondSpread;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.LeaseResultDTO;
import io.stockvaluation.dto.SyntheticResultDTO;
import io.stockvaluation.provider.DataProvider;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.LargeSpreadRepository;
import io.stockvaluation.repository.SmallSpreadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyntheticRatingService {

    private final SmallSpreadRepository smallSpreadRepository;
    private final LargeSpreadRepository largeSpreadRepository;
    private final CountryEquityRepository countryEquityRepository;
    private final CommonService commonService;
    private final DataProvider dataProvider;
    private final ValuationAssumptionProperties valuationAssumptionProperties;
    private final SyntheticRatingProperties syntheticRatingProperties;

    public SyntheticResultDTO calculateSyntheticRating(
            String ticker,
            boolean requiredLeaseConverter,
            Double leaseExpenseCurrentYear,
            Double[] commitments,
            Double futureCommitment
    ) {
        long marketCap = fetchMarketCap(ticker);
        log.info("market capital is: {}", marketCap);

        String formattedTicker = ticker.toUpperCase(Locale.ROOT);
        CompanyDataDTO companyDataDTO = commonService.getCompanyDataFromProvider(formattedTicker);
        OperatingMetrics operatingMetrics = calculateOperatingMetrics(companyDataDTO, requiredLeaseConverter);
        double ebit = operatingMetrics.ebit();
        double interestExpense = operatingMetrics.interestExpense();

        double riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate();
        log.info("Ebit: {} , intrestExpense: {}, RiskFreeRate:{} ", ebit, interestExpense, riskFreeRate);

        boolean largeFirm = marketCap > syntheticRatingProperties.getLargeCapThreshold();
        log.info("firmType: {} ", largeFirm ? "LARGE" : "SMALL");

        log.info("Calculating Synthetic Rating with the following inputs: ebit={}, interestExpense={}, riskFreeRate={}",
                ebit, interestExpense, riskFreeRate);

        double interestCoverageRatio = calculateInterestCoverageRatio(ebit, interestExpense);
        log.info("Calculated Interest Coverage Ratio = {}", format(interestCoverageRatio));

        BondSpreadEstimate bondSpreadEstimate = resolveBondSpread(largeFirm, interestCoverageRatio);
        String estimatedBondRating = bondSpreadEstimate.rating();
        double estimatedCompanySpread = bondSpreadEstimate.companySpread();

        // Get the country equity information
        String defaultCountry = syntheticRatingProperties.getDefaultCountry();
        CountryEquity countryEquity = countryEquityRepository.findDefaultSpread(defaultCountry);
        double estimatedCountrySpread = countryEquity.getCountryRiskPremium();
        log.info("Retrieved Country Spread for '{}': {}", defaultCountry, format(estimatedCountrySpread));

        // Calculate the estimated cost of debt
        double estimatedCostDebt = riskFreeRate + estimatedCompanySpread + estimatedCountrySpread;
        log.info("Calculated Estimated Cost of Debt: {}", format(estimatedCostDebt));

        // Return the results encapsulated in a DTO
        return new SyntheticResultDTO(
                format(interestCoverageRatio),
                estimatedBondRating,
                format(estimatedCompanySpread),
                format(estimatedCountrySpread),
                format(estimatedCostDebt)
        );
    }

    private long fetchMarketCap(String ticker) {
        Map<String, Object> response = dataProvider.getCompanyInfo(ticker);
        if (response != null && response.containsKey("marketCap")) {
            return ((Number) response.get("marketCap")).longValue();
        }
        return 0L;
    }

    private OperatingMetrics calculateOperatingMetrics(CompanyDataDTO companyDataDTO, boolean requiredLeaseConverter) {
        if (!requiredLeaseConverter) {
            log.info("operating lease is not required");
            return new OperatingMetrics(
                    companyDataDTO.getFinancialDataDTO().getOperatingIncomeTTM(),
                    companyDataDTO.getFinancialDataDTO().getInterestExpenseTTM()
            );
        }

        log.info("Operating lease is required");
        double ebit = companyDataDTO.getFinancialDataDTO().getOperatingIncomeTTM();
        double interestExpense = companyDataDTO.getFinancialDataDTO().getInterestExpenseTTM();
        LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConverter();
        if (Objects.nonNull(leaseResultDTO)) {
            log.info("operating lease is not null");
            log.info("adjustment to operating earnings: {}", leaseResultDTO.getAdjustmentToOperatingEarnings());
            log.info("adjustment to total Debt: {}", leaseResultDTO.getAdjustmentToTotalDebt());
            ebit = companyDataDTO.getFinancialDataDTO().getOperatingIncomeLTM() + leaseResultDTO.getAdjustmentToOperatingEarnings();
            interestExpense = companyDataDTO.getFinancialDataDTO().getInterestExpenseTTM()
                    + leaseResultDTO.getAdjustmentToTotalDebt() * valuationAssumptionProperties.getPreTaxCostOfDebt();
        }
        return new OperatingMetrics(ebit, interestExpense);
    }

    private double calculateInterestCoverageRatio(double ebit, double interestExpense) {
        if (interestExpense == 0) {
            return syntheticRatingProperties.getInterestCoverageCeiling();
        }
        if (ebit < 0) {
            return syntheticRatingProperties.getInterestCoverageFloor();
        }
        return ebit / interestExpense;
    }

    private BondSpreadEstimate resolveBondSpread(boolean largeFirm, double interestCoverageRatio) {
        if (largeFirm) {
            LargeBondSpread largeBondSpread = largeSpreadRepository.findRating(interestCoverageRatio);
            if (Objects.nonNull(largeBondSpread)) {
                log.info(
                        "Firm Type LARGE: Found Large Bond Spread. Rating: {}, Spread: {}",
                        largeBondSpread.getRating(),
                        format(largeBondSpread.getSpread())
                );
                return new BondSpreadEstimate(largeBondSpread.getRating(), largeBondSpread.getSpread());
            }
        } else {
            SmallBondSpread smallBondSpread = smallSpreadRepository.findRating(interestCoverageRatio);
            if (Objects.nonNull(smallBondSpread)) {
                log.info(
                        "Firm Type SMALL: Found Small Bond Spread. Rating: {}, Spread: {}",
                        smallBondSpread.getRating(),
                        format(smallBondSpread.getSpread())
                );
                return new BondSpreadEstimate(smallBondSpread.getRating(), smallBondSpread.getSpread());
            }
        }
        return new BondSpreadEstimate(null, 0.0);
    }

    private String format(double value) {
        return new DecimalFormat("0.00").format(value);
    }

    private record OperatingMetrics(double ebit, double interestExpense) {
    }

    private record BondSpreadEstimate(String rating, double companySpread) {
    }

}
