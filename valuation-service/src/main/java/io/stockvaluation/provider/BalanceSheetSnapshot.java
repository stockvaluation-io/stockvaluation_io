package io.stockvaluation.provider;

public record BalanceSheetSnapshot(
        Double bookValueEquity,
        Double totalDebt,
        Double cashAndShortTermInvestments,
        Double sharesOutstanding,
        Double minorityInterest) {

    public static BalanceSheetSnapshot empty() {
        return new BalanceSheetSnapshot(null, null, null, null, null);
    }
}
