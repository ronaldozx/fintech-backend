package com.globo.fintech_backend.Insights.analysis;

import com.globo.fintech_backend.Insights.dto.UnusualExpense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnusualDetectorTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    private static Expense expense(String category, String date, String amount) {
        return new Expense(LocalDate.parse(date), "Compra " + amount, category, new BigDecimal(amount));
    }

    private static List<Expense> baseline(String category, int count, String amount) {
        List<Expense> expenses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            expenses.add(expense(category, "2026-0" + (3 + i % 5) + "-1" + (i % 9), amount));
        }
        return expenses;
    }

    @Test
    void flagsAnExpenseFarAboveWhatTheCategoryUsuallyCosts() {
        List<Expense> expenses = new ArrayList<>(baseline("Mercado", 10, "100.00"));
        expenses.add(expense("Mercado", "2026-09-04", "480.00"));

        List<UnusualExpense> unusual = UnusualDetector.detect(expenses, SEPTEMBER);

        assertEquals(1, unusual.size());
        assertEquals(new BigDecimal("480.00"), unusual.get(0).amount());
        assertEquals(new BigDecimal("100.00"), unusual.get(0).typicalAmount());
    }

    @Test
    void needsEnoughHistoryToJudge() {
        List<Expense> expenses = new ArrayList<>(baseline("Mercado", UnusualDetector.MIN_SAMPLES - 1, "100.00"));
        expenses.add(expense("Mercado", "2026-09-04", "900.00"));

        assertTrue(UnusualDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void ignoresAModestIncreaseEvenWhenHistoryIsVeryUniform() {
        List<Expense> expenses = new ArrayList<>(baseline("Mercado", 10, "100.00"));
        expenses.add(expense("Mercado", "2026-09-04", "130.00"));

        assertTrue(UnusualDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void doesNotFlagTheUsualSpreadOfAVariableCategory() {
        List<Expense> expenses = new ArrayList<>();
        String[] amounts = {"40.00", "180.00", "90.00", "220.00", "60.00", "150.00", "300.00", "120.00"};
        for (int i = 0; i < amounts.length; i++) {
            expenses.add(expense("Restaurantes", "2026-0" + (2 + i % 6) + "-1" + i, amounts[i]));
        }
        expenses.add(expense("Restaurantes", "2026-09-04", "260.00"));

        assertTrue(UnusualDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void judgesEachCategoryAgainstItsOwnHistory() {
        List<Expense> expenses = new ArrayList<>(baseline("Mercado", 10, "100.00"));
        expenses.addAll(baseline("Moradia", 10, "1800.00"));
        expenses.add(expense("Moradia", "2026-09-04", "1800.00"));

        assertTrue(UnusualDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void neverCountsTheMonthBeingJudgedInItsOwnBaseline() {
        List<Expense> expenses = new ArrayList<>(baseline("Mercado", 7, "100.00"));
        expenses.add(expense("Mercado", "2026-09-04", "900.00"));
        expenses.add(expense("Mercado", "2026-09-05", "900.00"));

        assertTrue(UnusualDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void listsAtMostFiveBiggestFirst() {
        List<Expense> expenses = new ArrayList<>(baseline("Mercado", 10, "100.00"));
        for (int i = 1; i <= 7; i++) {
            expenses.add(expense("Mercado", "2026-09-0" + i, String.valueOf(400 + i * 10) + ".00"));
        }

        List<UnusualExpense> unusual = UnusualDetector.detect(expenses, SEPTEMBER);

        assertEquals(UnusualDetector.LIMIT, unusual.size());
        assertEquals(new BigDecimal("470.00"), unusual.get(0).amount());
        assertEquals(new BigDecimal("430.00"), unusual.get(4).amount());
    }
}
