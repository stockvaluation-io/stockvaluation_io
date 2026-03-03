package io.stockvaluation.provider;

public record IncomeStatementSnapshot(
        Double totalRevenue,
        Double operatingIncome,
        Double specialIncomeCharges,
        Double interestExpense,
        Double taxProvision,
        Double pretaxIncome,
        Double researchAndDevelopment) {

    public static IncomeStatementSnapshot empty() {
        return new IncomeStatementSnapshot(null, null, null, null, null, null, null);
    }
}
