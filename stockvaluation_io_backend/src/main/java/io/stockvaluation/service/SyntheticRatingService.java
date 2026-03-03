package io.stockvaluation.service;

import io.stockvaluation.domain.CountryEquity;
import io.stockvaluation.domain.LargeBondSpread;
import io.stockvaluation.domain.SmallBondSpread;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.LeaseResultDTO;
import io.stockvaluation.dto.SyntheticResultDTO;
import io.stockvaluation.repository.CountryEquityRepository;
import io.stockvaluation.repository.LargeSpreadRepository;
import io.stockvaluation.repository.SmallSpreadRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class SyntheticRatingService {

    @Value("${yahoo.base.url}")
    private String yahooApiUrl;

    @Autowired
    SmallSpreadRepository smallSpreadRepository;

    @Autowired
    LargeSpreadRepository largeSpreadRepository;

    @Autowired
    CountryEquityRepository countryEquityRepository;

    @Autowired
    CommonService commonService;

    @Autowired
    RestTemplate restTemplate;

    DecimalFormat df = new DecimalFormat("0.00");

    double PRE_TAX_COST_OF_DEBT = 5.00; // in %

    public SyntheticResultDTO calculateSyntheticRating(String ticker, boolean requiredLeaseConvertor, @RequestParam(required = false) Double leaseExpenseCurrentYear, @RequestParam(required = false) Double[] commitments, @RequestParam(required = false) Double futureCommitment) {
        long defaultMarketCap = 5000000000L;
        long marketCap = 0;
        // Build the URL with query parameters
        String url = yahooApiUrl + "/info?ticker=" + ticker;
        
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        // Check if response contains the "marketCap" key
        if (response != null && response.containsKey("marketCap")) {
            // Extract the marketCap value and cast it to long
            marketCap = ((Number) response.get("marketCap")).longValue();
        }
        log.info("market capital is: {}", marketCap);

        String formattedTicker = ticker.toUpperCase();
        double ebit = 0;
        double interestExpense = 0;
        CompanyDataDTO companyDataDTO = commonService.getCompanyDtaFromYahooApi(formattedTicker);

        // calculating ebit
        if (requiredLeaseConvertor) {
            log.info("Operating lease is required");
            LeaseResultDTO leaseResultDTO = commonService.calculateOperatingLeaseConvertor();
            if (Objects.nonNull(leaseResultDTO)) {
                log.info("operating lease is not null");
                log.info("adjustment to operating earnings: {}", leaseResultDTO.getAdjustmentToOperatingEarnings());
                log.info("adjustment to total Debt: {}", leaseResultDTO.getAdjustmentToTotalDebt());
                ebit = companyDataDTO.getFinancialDataDTO().getOperatingIncomeLTM() + leaseResultDTO.getAdjustmentToOperatingEarnings();
                interestExpense = companyDataDTO.getFinancialDataDTO().getInterestExpenseTTM() + leaseResultDTO.getAdjustmentToTotalDebt() * PRE_TAX_COST_OF_DEBT / 100;
            }
        } else {
            log.info("operating lease is not required");
            ebit = companyDataDTO.getFinancialDataDTO().getOperatingIncomeTTM();
            interestExpense = companyDataDTO.getFinancialDataDTO().getInterestExpenseTTM();
        }

        double riskFreeRate = companyDataDTO.getCompanyDriveDataDTO().getRiskFreeRate();
        log.info("Ebit: {} , intrestExpense: {}, RiskFreeRate:{} ", ebit, interestExpense, riskFreeRate);

        // for firm type
        int firmType;
        if (marketCap > defaultMarketCap) {
            firmType = 1;
        } else {
            firmType = 2;
        }
        log.info("firmType: {} ", firmType);


        log.info("Calculating Synthetic Rating with the following inputs: ebit={}, interestExpense={}, riskFreeRate={}",
                ebit, interestExpense, riskFreeRate);

        // calculations
        double interestCoverageRatio;

        // calculating intrest coverage ratio
        if (interestExpense == 0) {
            interestCoverageRatio = 100000;
        } else if (ebit < 0) {
            interestCoverageRatio = -100000;
        } else {
            interestCoverageRatio = ebit / interestExpense;
        }

        log.info("Calculated Interest Coverage Ratio = {}", df.format(interestCoverageRatio));

        String estimatedBondRating = null;
        double estimatedCompanySpread = 0.0;
        double estimatedCountrySpread = 0.0;

        // Determine the company spread and rating based on firm type
        if (firmType == 1) {
            LargeBondSpread largeBondSpread = largeSpreadRepository.findRating(interestCoverageRatio);
            if (Objects.nonNull(largeBondSpread)) {
                estimatedBondRating = largeBondSpread.getRating();
                estimatedCompanySpread = largeBondSpread.getSpread();
                log.info("Firm Type 1: Found Large Bond Spread. Rating: {}, Spread: {}", estimatedBondRating, df.format(estimatedCompanySpread));

            }
        } else if (firmType == 2) {

            SmallBondSpread smallBondSpread = smallSpreadRepository.findRating(interestCoverageRatio);
            if (Objects.nonNull(smallBondSpread)) {
                estimatedBondRating = smallBondSpread.getRating();
                estimatedCompanySpread = smallBondSpread.getSpread();
                log.info("Firm Type 2: Found Small Bond Spread. Rating: {}, Spread: {}", estimatedBondRating, df.format(estimatedCompanySpread));
            }
        }

        // Get the country equity information
        CountryEquity countryEquity = countryEquityRepository.findDefaultSpread("United States");
        estimatedCountrySpread = countryEquity.getCountryRiskPremium();
        log.info("Retrieved Country Spread for 'United States': {}", df.format(estimatedCountrySpread));

        // Calculate the estimated cost of debt
        double estimatedCostDebt = riskFreeRate + estimatedCompanySpread + estimatedCountrySpread;
        log.info("Calculated Estimated Cost of Debt: {}", df.format(estimatedCostDebt));

        // Return the results encapsulated in a DTO
        return new SyntheticResultDTO(
                df.format(interestCoverageRatio),
                estimatedBondRating,
                df.format(estimatedCompanySpread),
                df.format(estimatedCountrySpread),
                df.format(estimatedCostDebt)
        );
    }

}
