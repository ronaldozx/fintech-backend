package com.globo.fintech_backend.Insights.service;

import com.globo.fintech_backend.Insights.analysis.Descriptions;
import com.globo.fintech_backend.Insights.analysis.Expense;
import com.globo.fintech_backend.Insights.analysis.RecurringDetector;
import com.globo.fintech_backend.Insights.analysis.UnusualDetector;
import com.globo.fintech_backend.Insights.dto.CashFlowInsight;
import com.globo.fintech_backend.Insights.dto.CategoryChange;
import com.globo.fintech_backend.Insights.dto.CategoryMovers;
import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Insights.dto.MerchantTotal;
import com.globo.fintech_backend.Insights.dto.ProjectionInsight;
import com.globo.fintech_backend.Insights.dto.RecurringCharge;
import com.globo.fintech_backend.Insights.dto.RecurringInsight;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.common.MonthParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InsightsService {

    static final int HISTORY_MONTHS = 6;
    static final int MIN_DAYS_FOR_PROJECTION = 5;
    private static final int MOVER_LIMIT = 3;
    private static final int MERCHANT_LIMIT = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;

    public InsightsService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public InsightsDTO insights(Long userId, String monthText, LocalDate today) {
        YearMonth month = MonthParser.parse(monthText, YearMonth.from(today));
        YearMonth previous = month.minusMonths(1);

        List<Expense> expenses = transactionRepository
                .findCountedExpensesBetween(userId, month.minusMonths(HISTORY_MONTHS).atDay(1), month.atEndOfMonth())
                .stream()
                .map(Expense::from)
                .toList();

        List<Expense> current = expenses.stream().filter(expense -> expense.month().equals(month)).toList();
        List<Expense> before = expenses.stream().filter(expense -> expense.month().equals(previous)).toList();

        return new InsightsDTO(
                month.toString(),
                cashFlow(userId, month, previous),
                movers(current, before),
                recurring(expenses, month),
                UnusualDetector.detect(expenses, month),
                merchants(current),
                projection(current, before, month, today));
    }

    private CashFlowInsight cashFlow(Long userId, YearMonth month, YearMonth previous) {
        List<MonthTotal> totals = transactionRepository.getMonthlyTotals(userId, previous.atDay(1), month.atEndOfMonth());

        MonthTotal current = find(totals, month);
        MonthTotal before = find(totals, previous);

        BigDecimal income = current == null ? BigDecimal.ZERO : current.getIncome();
        BigDecimal expense = current == null ? BigDecimal.ZERO : current.getExpense().abs();

        return new CashFlowInsight(
                income,
                expense,
                income.subtract(expense),
                savingsRate(current),
                savingsRate(before));
    }

    private static MonthTotal find(List<MonthTotal> totals, YearMonth month) {
        return totals.stream()
                .filter(total -> total.getPeriodYear() == month.getYear() && total.getPeriodMonth() == month.getMonthValue())
                .findFirst()
                .orElse(null);
    }

    private static Integer savingsRate(MonthTotal total) {
        if (total == null || total.getIncome().signum() <= 0) {
            return null;
        }
        BigDecimal saved = total.getIncome().subtract(total.getExpense().abs());
        return saved.multiply(HUNDRED).divide(total.getIncome(), 0, RoundingMode.HALF_UP).intValue();
    }

    private static CategoryMovers movers(List<Expense> current, List<Expense> before) {
        if (before.isEmpty()) {
            return new CategoryMovers(List.of(), List.of());
        }

        Map<String, BigDecimal> now = totalsByCategory(current);
        Map<String, BigDecimal> then = totalsByCategory(before);

        Set<String> categories = new HashSet<>(now.keySet());
        categories.addAll(then.keySet());

        List<CategoryChange> changes = categories.stream()
                .map(category -> {
                    BigDecimal amount = now.getOrDefault(category, BigDecimal.ZERO);
                    BigDecimal previousAmount = then.getOrDefault(category, BigDecimal.ZERO);
                    return new CategoryChange(category, amount, previousAmount, amount.subtract(previousAmount));
                })
                .toList();

        return new CategoryMovers(
                changes.stream()
                        .filter(change -> change.change().signum() > 0)
                        .sorted(Comparator.comparing(CategoryChange::change).reversed())
                        .limit(MOVER_LIMIT)
                        .toList(),
                changes.stream()
                        .filter(change -> change.change().signum() < 0)
                        .sorted(Comparator.comparing(CategoryChange::change))
                        .limit(MOVER_LIMIT)
                        .toList());
    }

    private static Map<String, BigDecimal> totalsByCategory(List<Expense> expenses) {
        return expenses.stream().collect(Collectors.toMap(Expense::category, Expense::amount, BigDecimal::add));
    }

    private static RecurringInsight recurring(List<Expense> expenses, YearMonth month) {
        List<RecurringCharge> charges = RecurringDetector.detect(expenses, month);
        BigDecimal total = charges.stream().map(RecurringCharge::averageAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new RecurringInsight(charges, total);
    }

    private static List<MerchantTotal> merchants(List<Expense> current) {
        Map<String, List<Expense>> byName = current.stream()
                .collect(Collectors.groupingBy(InsightsService::merchantKey));

        return byName.values().stream()
                .map(group -> new MerchantTotal(
                        group.stream().max(Comparator.comparing(Expense::date)).orElseThrow().description(),
                        group.stream().map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                        group.size()))
                .sorted(Comparator.comparing(MerchantTotal::total).reversed())
                .limit(MERCHANT_LIMIT)
                .toList();
    }

    private static String merchantKey(Expense expense) {
        String normalized = Descriptions.normalize(expense.description());
        return normalized.isEmpty() ? expense.description().toLowerCase() : normalized;
    }

    private static ProjectionInsight projection(List<Expense> current, List<Expense> before, YearMonth month, LocalDate today) {
        int elapsed = today.getDayOfMonth();
        if (!month.equals(YearMonth.from(today)) || elapsed < MIN_DAYS_FOR_PROJECTION) {
            return null;
        }

        BigDecimal spent = sum(current);
        BigDecimal projected = spent
                .multiply(BigDecimal.valueOf(month.lengthOfMonth()))
                .divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);

        return new ProjectionInsight(spent, projected, sum(before), elapsed, month.lengthOfMonth());
    }

    private static BigDecimal sum(List<Expense> expenses) {
        return expenses.stream().map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
