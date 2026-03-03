package io.stockvaluation.provider;

import io.stockvaluation.dto.BasicInfoDataDTO;
import io.stockvaluation.dto.CompanyDataDTO;
import io.stockvaluation.dto.FinancialDataDTO;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * DataProvider interface — the abstraction boundary between the Java DCF
 * service
 * and any upstream data source (yfinance, Bloomberg, Alpha Vantage, etc.).
 *
 * <h2>DDD Context</h2>
 * This interface defines the "Anti-Corruption Layer" in Domain-Driven Design
 * terms.
 * The Java service speaks in terms of {@link CompanyDataDTO},
 * {@link FinancialDataDTO},
 * and {@link BasicInfoDataDTO}. The data provider is responsible for
 * translating
 * external API responses into these domain objects.
 *
 * <h2>Contract</h2>
 * Any implementation MUST satisfy all assertions in
 * {@code io.stockvaluation.provider.DataProviderContractTest}.
 *
 * <h2>Current Implementations</h2>
 * <ul>
 * <li>{@code YFinanceDataProvider} — calls the yfinance Flask service</li>
 * <li>{@code FixtureDataProvider} — reads from company-data-fixtures.json (for
 * tests)</li>
 * </ul>
 *
 * <h2>Future Implementations</h2>
 * <ul>
 * <li>{@code AlphaVantageDataProvider}</li>
 * <li>{@code BloombergDataProvider}</li>
 * </ul>
 */
public interface DataProvider {

    /**
     * Get full company data for valuation.
     *
     * @param ticker Stock ticker (e.g. "AAPL")
     * @return CompanyDataDTO with basicInfo, financialData, and driveData populated
     * @throws DataProviderException if the data source is unavailable or returns
     *                               invalid data
     */
    CompanyDataDTO getCompanyData(String ticker);

    /**
     * Get company info (subset of getCompanyData — used by SyntheticRatingService).
     *
     * @param ticker Stock ticker
     * @return Map of raw info fields (marketCap, country, industry, etc.)
     */
    Map<String, Object> getCompanyInfo(String ticker);

    /**
     * Get income statement data keyed by epoch milliseconds.
     *
     * @param ticker Stock ticker
     * @return Map where keys are epoch-ms timestamps and values are period
     *         financials
     */
    Map<String, Map<String, Object>> getIncomeStatement(String ticker);

    /**
     * Get income statement data with frequency selection.
     *
     * @param ticker Stock ticker
     * @param freq   "yearly" or "quarterly"
     * @return Map keyed by epoch-ms timestamps
     */
    default Map<String, Map<String, Object>> getIncomeStatement(String ticker, String freq) {
        return getIncomeStatement(ticker);
    }

    /**
     * Get provider-neutral income statement snapshots keyed by epoch milliseconds.
     */
    default Map<String, IncomeStatementSnapshot> getIncomeStatementSnapshots(String ticker) {
        return getIncomeStatementSnapshots(ticker, "yearly");
    }

    /**
     * Get provider-neutral income statement snapshots keyed by epoch milliseconds.
     */
    default Map<String, IncomeStatementSnapshot> getIncomeStatementSnapshots(String ticker, String freq) {
        return mapSnapshots(getIncomeStatement(ticker, freq), DataProvider::toIncomeStatementSnapshot);
    }

    /**
     * Get balance sheet data keyed by epoch milliseconds.
     *
     * @param ticker Stock ticker
     * @return Map where keys are epoch-ms timestamps and values are balance sheet
     *         items
     */
    Map<String, Map<String, Object>> getBalanceSheet(String ticker);

    /**
     * Get balance sheet data with frequency selection.
     *
     * @param ticker Stock ticker
     * @param freq   "yearly" or "quarterly"
     * @return Map keyed by epoch-ms timestamps
     */
    default Map<String, Map<String, Object>> getBalanceSheet(String ticker, String freq) {
        return getBalanceSheet(ticker);
    }

    /**
     * Get provider-neutral balance sheet snapshots keyed by epoch milliseconds.
     */
    default Map<String, BalanceSheetSnapshot> getBalanceSheetSnapshots(String ticker) {
        return getBalanceSheetSnapshots(ticker, "yearly");
    }

    /**
     * Get provider-neutral balance sheet snapshots keyed by epoch milliseconds.
     */
    default Map<String, BalanceSheetSnapshot> getBalanceSheetSnapshots(String ticker, String freq) {
        return mapSnapshots(getBalanceSheet(ticker, freq), DataProvider::toBalanceSheetSnapshot);
    }

    /**
     * Get revenue growth estimates.
     *
     * @param ticker Stock ticker
     * @return Growth estimates (may return null if unavailable)
     */
    Map<String, Object> getRevenueEstimate(String ticker);

    /**
     * Get revenue estimates with frequency selection.
     *
     * @param ticker Stock ticker
     * @param freq   "yearly" (default in yfinance service)
     * @return Growth estimates (may return null if unavailable)
     */
    default Map<String, Object> getRevenueEstimate(String ticker, String freq) {
        return getRevenueEstimate(ticker);
    }

    /**
     * Get dividend history.
     *
     * @param ticker Stock ticker
     * @return List of dividend records (may be empty for non-dividend payers)
     */
    java.util.List<Map<String, Object>> getDividendHistory(String ticker);

    /**
     * Get raw dividend payload (including metadata + history) from provider.
     * Default implementation reconstructs from history only.
     */
    default Map<String, Object> getDividendData(String ticker) {
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("dividendHistory", getDividendHistory(ticker));
        return result;
    }

    /**
     * Check if the provider is available and responsive.
     *
     * @return true if the data source is reachable
     */
    default boolean isHealthy() {
        return true;
    }

    /**
     * Get the provider name for logging/diagnostics.
     *
     * @return Provider name (e.g. "yfinance", "bloomberg", "fixture")
     */
    String getProviderName();

    /**
     * Provider-agnostic extraction for book value of common equity.
     * Implementations can override if their payload schema differs.
     */
    default Double extractBookValueEquity(Map<String, Object> balanceSheetData) {
        return toBalanceSheetSnapshot(balanceSheetData).bookValueEquity();
    }

    /**
     * Provider-agnostic extraction for total debt.
     */
    default Double extractTotalDebt(Map<String, Object> balanceSheetData) {
        return toBalanceSheetSnapshot(balanceSheetData).totalDebt();
    }

    /**
     * Provider-agnostic extraction for cash + short-term investments.
     */
    default Double extractCashAndShortTermInvestments(Map<String, Object> balanceSheetData) {
        return toBalanceSheetSnapshot(balanceSheetData).cashAndShortTermInvestments();
    }

    /**
     * Provider-agnostic extraction for shares outstanding.
     */
    default Double extractSharesOutstanding(Map<String, Object> balanceSheetData) {
        return toBalanceSheetSnapshot(balanceSheetData).sharesOutstanding();
    }

    private static IncomeStatementSnapshot toIncomeStatementSnapshot(Map<String, Object> payload) {
        return new IncomeStatementSnapshot(
                firstNumeric(payload, List.of(
                        "totalRevenue",
                        "TotalRevenue")),
                firstNumeric(payload, List.of(
                        "operatingIncome",
                        "EBIT",
                        "OperatingIncome")),
                firstNumeric(payload, List.of(
                        "specialIncomeCharges",
                        "SpecialIncomeCharges")),
                firstNumeric(payload, List.of(
                        "interestExpense",
                        "InterestExpense")),
                firstNumeric(payload, List.of(
                        "taxProvision",
                        "TaxProvision",
                        "IncomeTaxExpense")),
                firstNumeric(payload, List.of(
                        "pretaxIncome",
                        "PretaxIncome",
                        "IncomeBeforeTax")),
                firstNumeric(payload, List.of(
                        "researchAndDevelopment",
                        "ResearchAndDevelopment",
                        "ResearchAndDevelopmentExpense")));
    }

    private static BalanceSheetSnapshot toBalanceSheetSnapshot(Map<String, Object> payload) {
        return new BalanceSheetSnapshot(
                firstNumeric(payload, List.of(
                        "bookValueEquity",
                        "CommonStockEquity",
                        "StockholdersEquity",
                        "TotalEquityGrossMinorityInterest",
                        "TotalEquity")),
                firstNumeric(payload, List.of(
                        "totalDebt",
                        "TotalDebt",
                        "LongTermDebtAndCapitalLeaseObligation",
                        "TotalNonCurrentLiabilitiesNetMinorityInterest")),
                firstNumeric(payload, List.of(
                        "cashAndShortTermInvestments",
                        "CashCashEquivalentsAndShortTermInvestments")),
                firstNumeric(payload, List.of(
                        "sharesOutstanding",
                        "OrdinarySharesNumber")),
                firstNumeric(payload, List.of(
                        "minorityInterest",
                        "MinorityInterest",
                        "MinorityInterests")));
    }

    private static <T> Map<String, T> mapSnapshots(
            Map<String, Map<String, Object>> payload,
            Function<Map<String, Object>, T> mapper) {
        Map<String, T> snapshots = new java.util.HashMap<>();
        if (payload == null || payload.isEmpty()) {
            return snapshots;
        }
        for (Map.Entry<String, Map<String, Object>> entry : payload.entrySet()) {
            snapshots.put(entry.getKey(), mapper.apply(entry.getValue()));
        }
        return snapshots;
    }

    private static Double firstNumeric(Map<String, Object> payload, List<String> keys) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof Number number) {
                return number.doubleValue();
            }
        }
        return null;
    }
}
