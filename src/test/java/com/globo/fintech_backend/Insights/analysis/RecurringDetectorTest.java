package com.globo.fintech_backend.Insights.analysis;

import com.globo.fintech_backend.Insights.dto.RecurringCharge;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecurringDetectorTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    private static Expense expense(String description, String date, String amount) {
        return new Expense(LocalDate.parse(date), description, "Assinaturas", new BigDecimal(amount));
    }

    @Test
    void detectsAChargeThatRepeatsEveryMonthWithTheSameAmount() {
        List<Expense> expenses = List.of(
                expense("Netflix", "2026-07-05", "39.90"),
                expense("Netflix", "2026-08-05", "39.90"),
                expense("Netflix", "2026-09-05", "39.90"));

        List<RecurringCharge> charges = RecurringDetector.detect(expenses, SEPTEMBER);

        assertEquals(1, charges.size());
        assertEquals(3, charges.get(0).months());
        assertEquals(new BigDecimal("39.90"), charges.get(0).averageAmount());
        assertEquals(LocalDate.of(2026, 9, 5), charges.get(0).lastDate());
    }

    @Test
    void needsAtLeastThreeMonths() {
        List<Expense> expenses = List.of(
                expense("Netflix", "2026-08-05", "39.90"),
                expense("Netflix", "2026-09-05", "39.90"));

        assertTrue(RecurringDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void ignoresMerchantsWhoseMonthlyAmountVariesALot() {
        List<Expense> expenses = List.of(
                expense("Mercado Extra", "2026-06-10", "100.00"),
                expense("Mercado Extra", "2026-07-10", "250.00"),
                expense("Mercado Extra", "2026-08-10", "90.00"),
                expense("Mercado Extra", "2026-09-10", "300.00"));

        assertTrue(RecurringDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void toleratesSmallVariationsAroundTheMedian() {
        List<Expense> expenses = List.of(
                expense("Academia", "2026-07-02", "100.00"),
                expense("Academia", "2026-08-02", "108.00"),
                expense("Academia", "2026-09-02", "95.00"));

        assertEquals(1, RecurringDetector.detect(expenses, SEPTEMBER).size());
    }

    @Test
    void ignoresACancelledSubscription() {
        List<Expense> expenses = List.of(
                expense("Spotify", "2026-03-05", "21.90"),
                expense("Spotify", "2026-04-05", "21.90"),
                expense("Spotify", "2026-05-05", "21.90"),
                expense("Spotify", "2026-06-05", "21.90"));

        assertTrue(RecurringDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void stillCountsAChargeThatHasNotComeThisMonthYet() {
        List<Expense> expenses = List.of(
                expense("Spotify", "2026-06-25", "21.90"),
                expense("Spotify", "2026-07-25", "21.90"),
                expense("Spotify", "2026-08-25", "21.90"));

        assertEquals(1, RecurringDetector.detect(expenses, SEPTEMBER).size());
    }

    @Test
    void groupsDescriptionsThatDifferOnlyByNumbersAndCase() {
        List<Expense> expenses = List.of(
                expense("NETFLIX.COM 123", "2026-07-05", "39.90"),
                expense("Netflix.com 456", "2026-08-05", "39.90"),
                expense("netflix.com 789", "2026-09-05", "39.90"));

        assertEquals(1, RecurringDetector.detect(expenses, SEPTEMBER).size());
    }

    @Test
    void ignoresMerchantsChargedManyTimesInAMonth() {
        List<Expense> expenses = new ArrayList<>();
        for (String month : List.of("2026-07", "2026-08", "2026-09")) {
            for (String day : List.of("03", "12", "21")) {
                expenses.add(expense("Uber", month + "-" + day, "20.00"));
            }
        }

        assertTrue(RecurringDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void ignoresAMerchantVisitedTwiceAMonthEvenWhenTheMonthlyTotalIsSteady() {
        List<Expense> expenses = new ArrayList<>();
        for (String month : List.of("2026-07", "2026-08", "2026-09")) {
            expenses.add(expense("Mercado Extra", month + "-06", "100.00"));
            expenses.add(expense("Mercado Extra", month + "-16", "100.00"));
        }

        assertTrue(RecurringDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void ignoresExpensesWithoutAReadableName() {
        List<Expense> expenses = List.of(
                expense("123", "2026-07-05", "50.00"),
                expense("456", "2026-08-05", "50.00"),
                expense("789", "2026-09-05", "50.00"));

        assertTrue(RecurringDetector.detect(expenses, SEPTEMBER).isEmpty());
    }

    @Test
    void listsTheBiggestChargesFirst() {
        List<Expense> expenses = new ArrayList<>();
        for (String month : List.of("2026-07", "2026-08", "2026-09")) {
            expenses.add(expense("Spotify", month + "-05", "21.90"));
            expenses.add(expense("Aluguel", month + "-10", "1800.00"));
        }

        List<RecurringCharge> charges = RecurringDetector.detect(expenses, SEPTEMBER);

        assertEquals(2, charges.size());
        assertEquals("Aluguel", charges.get(0).description());
        assertEquals("Spotify", charges.get(1).description());
    }
}
