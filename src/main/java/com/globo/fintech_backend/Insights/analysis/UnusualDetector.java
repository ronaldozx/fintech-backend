package com.globo.fintech_backend.Insights.analysis;

import com.globo.fintech_backend.Insights.dto.UnusualExpense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class UnusualDetector {

    static final int MIN_SAMPLES = 8;
    static final int LIMIT = 5;
    private static final double DEVIATIONS = 2.0;
    private static final double MIN_RATIO = 1.5;

    private UnusualDetector() {
    }

    public static List<UnusualExpense> detect(List<Expense> expenses, YearMonth month) {
        Map<String, List<Double>> baseline = expenses.stream()
                .filter(expense -> expense.month().isBefore(month))
                .collect(Collectors.groupingBy(Expense::category,
                        Collectors.mapping(expense -> expense.amount().doubleValue(), Collectors.toList())));

        return expenses.stream()
                .filter(expense -> expense.month().equals(month))
                .map(expense -> toUnusual(expense, baseline.get(expense.category())))
                .filter(unusual -> unusual != null)
                .sorted(Comparator.comparing(UnusualExpense::amount).reversed())
                .limit(LIMIT)
                .toList();
    }

    private static UnusualExpense toUnusual(Expense expense, List<Double> samples) {
        if (samples == null || samples.size() < MIN_SAMPLES) {
            return null;
        }

        double mean = samples.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = samples.stream().mapToDouble(sample -> (sample - mean) * (sample - mean)).average().orElse(0);
        double threshold = Math.max(mean + DEVIATIONS * Math.sqrt(variance), mean * MIN_RATIO);

        if (expense.amount().doubleValue() <= threshold) {
            return null;
        }

        return new UnusualExpense(
                expense.description(),
                expense.category(),
                expense.date(),
                expense.amount(),
                BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP));
    }
}
