package io.stockvaluation.controller;

import io.stockvaluation.domain.IndustryAveragesGlobal;
import io.stockvaluation.domain.InputStatDistribution;
import io.stockvaluation.repository.IndustryAvgGloRepository;
import io.stockvaluation.repository.InputStatRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Internal API for industry averages lookup.
 * Protected by X-API-Key header for service-to-service communication.
 */
@Slf4j
@RestController
@RequestMapping("/api-s/industry")
public class IndustryController {

    @Value("${internal.api.key:bullbeargpt-internal-key}")
    private String internalApiKey;

    @Autowired
    private IndustryAvgGloRepository industryGloRepository;

    @Autowired
    private InputStatRepository inputStatRepository;

    /**
     * Get industry averages for a list of industries.
     * Protected by X-API-Key header.
     *
     * @param apiKey  X-API-Key header for authentication
     * @param request Request body containing list of industry names
     * @return Industry averages matching the requested industries
     */
    @Operation(summary = "Get Industry Averages (Internal API)", description = "Fetches industry averages for specified industries. Protected by X-API-Key header.")
    @PostMapping("/averages")
    public ResponseEntity<?> getIndustryAverages(
            @Parameter(description = "API key for authentication", required = true) @RequestHeader("X-API-Key") String apiKey,
            @RequestBody IndustryAveragesRequest request) {
        // Validate API key
        if (!internalApiKey.equals(apiKey)) {
            log.warn("Invalid API key attempt for /api-s/industry/averages");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid API key"));
        }

        if (request == null || request.getIndustries() == null || request.getIndustries().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "industries list is required"));
        }

        log.info("Fetching industry averages for {} industries: {}",
                request.getIndustries().size(), request.getIndustries());

        try {
            // Fetch global industry averages
            List<IndustryAveragesGlobal> globalAverages = industryGloRepository
                    .findByIndustryNameIn(request.getIndustries());

            // Build response with summary statistics for each industry
            List<IndustryAveragesSummary> summaries = globalAverages.stream()
                    .map(this::toSummary)
                    .collect(Collectors.toList());

            IndustryAveragesResponse response = new IndustryAveragesResponse();
            response.setIndustries(summaries);
            response.setCount(summaries.size());
            response.setRequestedCount(request.getIndustries().size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching industry averages: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch industry averages: " + e.getMessage()));
        }
    }

    /**
     * Get percentile distribution for an industry (useful for assumption
     * validation).
     */
    @Operation(summary = "Get Industry Percentile Distribution (Internal API)", description = "Fetches percentile distribution stats for an industry. Protected by X-API-Key header.")
    @GetMapping("/percentiles/{industryGroup}")
    public ResponseEntity<?> getIndustryPercentiles(
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String industryGroup) {
        // Validate API key
        if (!internalApiKey.equals(apiKey)) {
            log.warn("Invalid API key attempt for /api-s/industry/percentiles");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid API key"));
        }

        try {
            Optional<InputStatDistribution> stats = inputStatRepository
                    .findPreOperatingMarginByIndustryName(industryGroup);

            if (stats.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(stats.get());

        } catch (Exception e) {
            log.error("Error fetching industry percentiles: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch percentiles: " + e.getMessage()));
        }
    }

    /**
     * Convert entity to summary DTO with key metrics for AI consumption.
     */
    private IndustryAveragesSummary toSummary(IndustryAveragesGlobal avg) {
        IndustryAveragesSummary summary = new IndustryAveragesSummary();
        summary.setIndustryName(avg.getIndustryName());
        summary.setNumberOfFirms(avg.getNumberOfFirms());

        // Growth metrics
        summary.setAnnualRevenueGrowth(avg.getAnnualAverageRevenueGrowth());

        // Margin metrics
        summary.setPreTaxOperatingMargin(avg.getPreTaxOperatingMargin());
        summary.setNetMargin(avg.getAfterTaxRoc()); // Approximation

        // Cost of capital metrics
        summary.setCostOfCapital(avg.getCostOfCapital());
        summary.setCostOfEquity(avg.getCostOfEquity());
        summary.setPreTaxCostOfDebt(avg.getPreTaxCostOfDebt());

        // Risk metrics
        summary.setUnleveredBeta(avg.getUnleveredBeta());
        summary.setEquityBeta(avg.getEquityBeta());
        summary.setStdDeviation(avg.getStdDeviationInStockPrices());

        // Valuation multiples
        summary.setEvToSales(avg.getEvToSales());
        summary.setEvToEbitda(avg.getEvToEbitda());
        summary.setEvToEbit(avg.getEvToEbit());
        summary.setTrailingPe(avg.getTrailingPe());
        summary.setPriceToBook(avg.getPriceToBook());

        // Capital structure
        summary.setDebtToCapital(avg.getMarketDebtToCapital());
        summary.setSalesToCapital(avg.getSalesToCapital());
        summary.setReinvestmentRate(avg.getReinvestmentRate());

        return summary;
    }

    // Request/Response DTOs
    public static class IndustryAveragesRequest {
        private List<String> industries;

        public List<String> getIndustries() {
            return industries;
        }

        public void setIndustries(List<String> industries) {
            this.industries = industries;
        }
    }

    public static class IndustryAveragesResponse {
        private List<IndustryAveragesSummary> industries;
        private int count;
        private int requestedCount;

        public List<IndustryAveragesSummary> getIndustries() {
            return industries;
        }

        public void setIndustries(List<IndustryAveragesSummary> industries) {
            this.industries = industries;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public int getRequestedCount() {
            return requestedCount;
        }

        public void setRequestedCount(int requestedCount) {
            this.requestedCount = requestedCount;
        }
    }

    public static class IndustryAveragesSummary {
        private String industryName;
        private int numberOfFirms;

        // Growth
        private double annualRevenueGrowth;

        // Margins
        private double preTaxOperatingMargin;
        private double netMargin;

        // Cost of capital
        private double costOfCapital;
        private double costOfEquity;
        private double preTaxCostOfDebt;

        // Risk
        private double unleveredBeta;
        private double equityBeta;
        private double stdDeviation;

        // Valuation multiples
        private double evToSales;
        private double evToEbitda;
        private double evToEbit;
        private double trailingPe;
        private double priceToBook;

        // Capital structure
        private double debtToCapital;
        private double salesToCapital;
        private double reinvestmentRate;

        // Getters and setters
        public String getIndustryName() {
            return industryName;
        }

        public void setIndustryName(String industryName) {
            this.industryName = industryName;
        }

        public int getNumberOfFirms() {
            return numberOfFirms;
        }

        public void setNumberOfFirms(int numberOfFirms) {
            this.numberOfFirms = numberOfFirms;
        }

        public double getAnnualRevenueGrowth() {
            return annualRevenueGrowth;
        }

        public void setAnnualRevenueGrowth(double annualRevenueGrowth) {
            this.annualRevenueGrowth = annualRevenueGrowth;
        }

        public double getPreTaxOperatingMargin() {
            return preTaxOperatingMargin;
        }

        public void setPreTaxOperatingMargin(double preTaxOperatingMargin) {
            this.preTaxOperatingMargin = preTaxOperatingMargin;
        }

        public double getNetMargin() {
            return netMargin;
        }

        public void setNetMargin(double netMargin) {
            this.netMargin = netMargin;
        }

        public double getCostOfCapital() {
            return costOfCapital;
        }

        public void setCostOfCapital(double costOfCapital) {
            this.costOfCapital = costOfCapital;
        }

        public double getCostOfEquity() {
            return costOfEquity;
        }

        public void setCostOfEquity(double costOfEquity) {
            this.costOfEquity = costOfEquity;
        }

        public double getPreTaxCostOfDebt() {
            return preTaxCostOfDebt;
        }

        public void setPreTaxCostOfDebt(double preTaxCostOfDebt) {
            this.preTaxCostOfDebt = preTaxCostOfDebt;
        }

        public double getUnleveredBeta() {
            return unleveredBeta;
        }

        public void setUnleveredBeta(double unleveredBeta) {
            this.unleveredBeta = unleveredBeta;
        }

        public double getEquityBeta() {
            return equityBeta;
        }

        public void setEquityBeta(double equityBeta) {
            this.equityBeta = equityBeta;
        }

        public double getStdDeviation() {
            return stdDeviation;
        }

        public void setStdDeviation(double stdDeviation) {
            this.stdDeviation = stdDeviation;
        }

        public double getEvToSales() {
            return evToSales;
        }

        public void setEvToSales(double evToSales) {
            this.evToSales = evToSales;
        }

        public double getEvToEbitda() {
            return evToEbitda;
        }

        public void setEvToEbitda(double evToEbitda) {
            this.evToEbitda = evToEbitda;
        }

        public double getEvToEbit() {
            return evToEbit;
        }

        public void setEvToEbit(double evToEbit) {
            this.evToEbit = evToEbit;
        }

        public double getTrailingPe() {
            return trailingPe;
        }

        public void setTrailingPe(double trailingPe) {
            this.trailingPe = trailingPe;
        }

        public double getPriceToBook() {
            return priceToBook;
        }

        public void setPriceToBook(double priceToBook) {
            this.priceToBook = priceToBook;
        }

        public double getDebtToCapital() {
            return debtToCapital;
        }

        public void setDebtToCapital(double debtToCapital) {
            this.debtToCapital = debtToCapital;
        }

        public double getSalesToCapital() {
            return salesToCapital;
        }

        public void setSalesToCapital(double salesToCapital) {
            this.salesToCapital = salesToCapital;
        }

        public double getReinvestmentRate() {
            return reinvestmentRate;
        }

        public void setReinvestmentRate(double reinvestmentRate) {
            this.reinvestmentRate = reinvestmentRate;
        }
    }
}
