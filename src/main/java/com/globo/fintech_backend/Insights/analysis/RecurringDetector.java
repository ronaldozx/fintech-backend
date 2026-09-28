package com.globo.fintech_backend.Insights.analysis;

import com.globo.fintech_backend.Insights.dto.RecurringCharge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class RecurringDetector {

    static final int MIN_MONTHS = 3;
    static final int MAX_CHARGES_PER_MONTH = 1;
    private static final BigDecimal TOLERANCE = new BigDecimal("0.15");

    private RecurringDetector() {
    }

    public static List<RecurringCharge> detect(List<Expense> expenses, YearMonth month) {
        Map<String, List<Expense>> byName = expenses.stream()
                .filter(expense -> !Descriptions.normalize(expense.description()).isEmpty())
                .collect(Collectors.groupingBy(expense -> Descriptions.normalize(expense.description())));

        return byName.values().stream()
                .map(group -> toCharge(group, month))
                .filter(charge -> charge != null)
                .sorted(Comparator.comparing(RecurringCharge::averageAmount).reversed())
                .toList();
    }

    private static RecurringCharge toCharge(List<Expense> group, YearMonth month) {
        Map<YearMonth, List<Expense>> byMonth = group.stream().collect(Collectors.groupingBy(Expense::month));
        Map<YearMonth, BigDecimal> totals = byMonth.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream().map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add)));

        BigDecimal median = median(totals.values().stream().sorted().toList());

        List<YearMonth> matching = totals.entrySet().stream()
                .filter(entry -> byMonth.get(entry.getKey()).size() <= MAX_CHARGES_PER_MONTH)
                .filter(entry -> entry.getValue().subtract(median).abs().compareTo(median.multiply(TOLERANCE)) <= 0)
                .map(Map.Entry::getKey)
                .toList();

        boolean stillActive = matching.contains(month) || matching.contains(month.minusMonths(1));
        if (matching.size() < MIN_MONTHS || !stillActive) {
            return null;
        }

        BigDecimal average = matching.stream()
                .map(totals::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(matching.size()), 2, RoundingMode.HALF_UP);

        Expense latest = group.stream().max(Comparator.comparing(Expense::date)).orElseThrow();

        return new RecurringCharge(latest.description(), average, matching.size(), latest.date());
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }
}
