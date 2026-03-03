package io.stockvaluation.service;

import io.stockvaluation.dto.FinancialDataDTO;
import io.stockvaluation.provider.BalanceSheetSnapshot;
import io.stockvaluation.provider.DataProvider;
import io.stockvaluation.provider.IncomeStatementSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompanyFinancialIngestionService {

    private final DataProvider dataProvider;

    public FinancialIngestionData ingest(String ticker, Map<String, Object> basicInfoMap) {
        Map<String, IncomeStatementSnapshot> quarterlyIncomeSnapshots =
                dataProvider.getIncomeStatementSnapshots(ticker, "quarterly");
        Map<String, IncomeStatementSnapshot> yearlyIncomeSnapshots =
                dataProvider.getIncomeStatementSnapshots(ticker, "yearly");

        Map<String, IncomeStatementSnapshot> recentQuarterlyIncome = getMostRecentPeriods(quarterlyIncomeSnapshots, 4);
        double totalRevenueTTM = calculateTotal(recentQuarterlyIncome, IncomeStatementSnapshot::totalRevenue);
        double operatingIncomeTTM = calculateTotal(recentQuarterlyIncome, IncomeStatementSnapshot::operatingIncome);
        double specialIncomeChargesTTM = calculateTotal(recentQuarterlyIncome, IncomeStatementSnapshot::specialIncomeCharges);
        double interestExpenseTTM = calculateTotal(recentQuarterlyIncome, IncomeStatementSnapshot::interestExpense);
        operatingIncomeTTM += Math.abs(specialIncomeChargesTTM);

        FinancialDataDTO financialDataDTO = new FinancialDataDTO();
        financialDataDTO.setResearchAndDevelopmentMap(
                mapResearchAndDevelopmentHistory(yearlyIncomeSnapshots, quarterlyIncomeSnapshots));

        List<Double> historicalRevenue = new ArrayList<>();
        List<Double> historicalMargins = new ArrayList<>();
        if (yearlyIncomeSnapshots.size() > 3) {
            Map<String, IncomeStatementSnapshot> sortedMap = new TreeMap<>(yearlyIncomeSnapshots);
            for (IncomeStatementSnapshot snapshot : sortedMap.values()) {
                if (snapshot == null
                        || snapshot.totalRevenue() == null
                        || snapshot.operatingIncome() == null
                        || snapshot.totalRevenue() == 0.0) {
                    continue;
                }
                historicalRevenue.add(snapshot.totalRevenue());
                historicalMargins.add(snapshot.operatingIncome() / snapshot.totalRevenue());
            }
        }

        IncomeStatementSnapshot previousYearIncomeSnapshot =
                findSnapshotByYear(yearlyIncomeSnapshots, targetYears(1, 2, 3),
                        snapshot -> snapshot.totalRevenue() != null,
                        IncomeStatementSnapshot.empty());

        Double revenueLTM = previousYearIncomeSnapshot.totalRevenue();
        if (Objects.equals(revenueLTM, totalRevenueTTM)) {
            IncomeStatementSnapshot olderIncomeSnapshot =
                    findSnapshotByYear(yearlyIncomeSnapshots, targetYears(2, 3),
                            snapshot -> snapshot.totalRevenue() != null,
                            IncomeStatementSnapshot.empty());
            revenueLTM = olderIncomeSnapshot.totalRevenue();
        }

        double revenueLtmValue = valueOrZero(revenueLTM);
        double operatingIncomeLtmValue = valueOrZero(previousYearIncomeSnapshot.operatingIncome())
                + Math.abs(valueOrZero(previousYearIncomeSnapshot.specialIncomeCharges()));
        double interestExpenseLtmValue = valueOrZero(previousYearIncomeSnapshot.interestExpense());
        double taxProvision = valueOrZero(previousYearIncomeSnapshot.taxProvision());
        Double preTaxIncome = previousYearIncomeSnapshot.pretaxIncome();

        Map<String, BalanceSheetSnapshot> quarterlyBalanceSnapshots =
                dataProvider.getBalanceSheetSnapshots(ticker, "quarterly");
        BalanceSheetSnapshot mostRecentQuarterlyBalance =
                getMostRecentSnapshot(quarterlyBalanceSnapshots, BalanceSheetSnapshot.empty());

        double bookValueEquityTTM = valueOrZero(mostRecentQuarterlyBalance.bookValueEquity());
        double bookValueDebtTTM = valueOrZero(mostRecentQuarterlyBalance.totalDebt());
        double cashAndMarketableTTM = valueOrZero(mostRecentQuarterlyBalance.cashAndShortTermInvestments());
        Double numberOfShareOutStanding = mostRecentQuarterlyBalance.sharesOutstanding();

        Map<String, BalanceSheetSnapshot> yearlyBalanceSnapshots =
                dataProvider.getBalanceSheetSnapshots(ticker, "yearly");
        BalanceSheetSnapshot recentYearlyBalanceSnapshot =
                findSnapshotByYear(yearlyBalanceSnapshots, targetYears(1, 2, 3),
                        snapshot -> true,
                        BalanceSheetSnapshot.empty());

        double bookValueEquityLTM = valueOrZero(recentYearlyBalanceSnapshot.bookValueEquity());
        double bookValueDebtLTM = valueOrZero(recentYearlyBalanceSnapshot.totalDebt());
        double cashAndMarketableLTM = valueOrZero(recentYearlyBalanceSnapshot.cashAndShortTermInvestments());
        if (numberOfShareOutStanding == null) {
            numberOfShareOutStanding = recentYearlyBalanceSnapshot.sharesOutstanding();
        }

        financialDataDTO.setRevenueTTM(totalRevenueTTM == 0.0 ? revenueLtmValue : totalRevenueTTM);
        financialDataDTO.setRevenueLTM(revenueLtmValue);

        financialDataDTO.setOperatingIncomeTTM(operatingIncomeTTM == 0.0 ? operatingIncomeLtmValue : operatingIncomeTTM);
        financialDataDTO.setOperatingIncomeLTM(operatingIncomeLtmValue);

        financialDataDTO.setInterestExpenseTTM(interestExpenseTTM == 0.0 ? interestExpenseLtmValue : interestExpenseTTM);
        financialDataDTO.setInterestExpenseLTM(interestExpenseLtmValue);

        financialDataDTO.setBookValueEqualityTTM(bookValueEquityTTM == 0.0 ? bookValueEquityLTM : bookValueEquityTTM);
        financialDataDTO.setBookValueEqualityLTM(bookValueEquityLTM);

        financialDataDTO.setBookValueDebtTTM(bookValueDebtTTM == 0.0 ? bookValueDebtLTM : bookValueDebtTTM);
        financialDataDTO.setBookValueDebtLTM(bookValueDebtLTM);

        financialDataDTO.setCashAndMarkablTTM(cashAndMarketableTTM == 0.0 ? cashAndMarketableLTM : cashAndMarketableTTM);
        financialDataDTO.setCashAndMarkablLTM(cashAndMarketableLTM);

        financialDataDTO.setNonOperatingAssetTTM(0.0);
        financialDataDTO.setNonOperatingAssetLTM(0.0);
        financialDataDTO.setMinorityInterestTTM(valueOrZero(recentYearlyBalanceSnapshot.minorityInterest()));
        financialDataDTO.setMinorityInterestLTM(0.0);
        financialDataDTO.setNoOfShareOutstanding(numberOfShareOutStanding);

        financialDataDTO.setHighestStockPrice(toDouble(basicInfoMap.get("dayHigh")));
        financialDataDTO.setPreviousDayStockPrice(toDouble(basicInfoMap.get("previousClose")));
        financialDataDTO.setLowestStockPrice(toDouble(basicInfoMap.get("dayLow")));
        financialDataDTO.setStockPrice(toDouble(basicInfoMap.get("currentPrice")));

        return new FinancialIngestionData(
                financialDataDTO,
                historicalRevenue,
                historicalMargins,
                taxProvision,
                preTaxIncome);
    }

    private static int[] targetYears(int... offsets) {
        int currentYear = LocalDate.now().getYear();
        int[] years = new int[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            years[i] = currentYear - offsets[i];
        }
        return years;
    }

    private static <T> T findSnapshotByYear(
            Map<String, T> snapshots,
            int[] targetYears,
            Predicate<T> acceptSnapshot,
            T fallbackValue) {
        for (int targetYear : targetYears) {
            for (Map.Entry<String, T> entry : snapshots.entrySet()) {
                if (extractYear(entry.getKey()) != targetYear) {
                    continue;
                }
                T snapshot = entry.getValue();
                if (snapshot != null && acceptSnapshot.test(snapshot)) {
                    return snapshot;
                }
            }
        }
        return fallbackValue;
    }

    private static <T> Map<String, T> getMostRecentPeriods(Map<String, T> snapshots, int periods) {
        return snapshots.entrySet().stream()
                .sorted(Map.Entry.<String, T>comparingByKey().reversed())
                .limit(periods)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static BalanceSheetSnapshot getMostRecentSnapshot(
            Map<String, BalanceSheetSnapshot> snapshots,
            BalanceSheetSnapshot fallbackValue) {
        return snapshots.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(fallbackValue);
    }

    private static int extractYear(String timestampMillis) {
        long timestamp = Long.parseLong(timestampMillis);
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .getYear();
    }

    private static double calculateTotal(
            Map<String, IncomeStatementSnapshot> snapshots,
            Function<IncomeStatementSnapshot, Double> extractor) {
        return snapshots.values().stream()
                .filter(Objects::nonNull)
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    private Map<String, Double> mapResearchAndDevelopmentHistory(
            Map<String, IncomeStatementSnapshot> yearlySnapshots,
            Map<String, IncomeStatementSnapshot> quarterlySnapshots) {
        Map<String, Double> researchAndDevelopmentMap = new TreeMap<>();

        int index = 1;
        for (IncomeStatementSnapshot snapshot : getMostRecentPeriods(yearlySnapshots, 4).values()) {
            researchAndDevelopmentMap.put("currentR&D" + (-index), valueOrZero(snapshot.researchAndDevelopment()));
            index++;
        }

        double currentResearchAndDevelopment = getMostRecentPeriods(quarterlySnapshots, 4)
                .values()
                .stream()
                .filter(Objects::nonNull)
                .map(IncomeStatementSnapshot::researchAndDevelopment)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        researchAndDevelopmentMap.put("currentR&D-0", currentResearchAndDevelopment);

        return researchAndDevelopmentMap;
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    public record FinancialIngestionData(
            FinancialDataDTO financialDataDTO,
            List<Double> historicalRevenue,
            List<Double> historicalMargins,
            Double taxProvision,
            Double preTaxIncome) {
    }
}
