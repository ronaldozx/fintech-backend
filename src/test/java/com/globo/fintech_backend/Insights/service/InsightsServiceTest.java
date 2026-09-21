package com.globo.fintech_backend.Insights.service;

import com.globo.fintech_backend.Insights.dto.CategoryChange;
import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightsServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate SEPTEMBER_10 = LocalDate.of(2026, 9, 10);
    private static final LocalDate SEPTEMBER_3 = LocalDate.of(2026, 9, 3);

    @Mock
    private TransactionRepository transactionRepository;

    private InsightsService service;

    @BeforeEach
    void setUp() {
        service = new InsightsService(transactionRepository);
    }

    private static Transaction expense(String date, String category, String description, String amount) {
        Transaction transaction = new Transaction();
        transaction.setDate(LocalDate.parse(date));
        transaction.setCategory(category);
        transaction.setDescription(description);
        transaction.setAmount(new BigDecimal(amount).negate());
        return transaction;
    }

    private static MonthTotal total(int year, int month, String income, String expense) {
        MonthTotal total = mock(MonthTotal.class);
        when(total.getPeriodYear()).thenReturn(year);
        when(total.getPeriodMonth()).thenReturn(month);
        when(total.getIncome()).thenReturn(new BigDecimal(income));
        when(total.getExpense()).thenReturn(new BigDecimal(expense).negate());
        return total;
    }

    private void data(List<Transaction> expenses, MonthTotal... totals) {
        when(transactionRepository.findCountedExpensesBetween(eq(USER_ID), any(), any())).thenReturn(expenses);
        when(transactionRepository.getMonthlyTotals(eq(USER_ID), any(), any())).thenReturn(List.of(totals));
    }

    @Test
    void computesTheSavingsRateAndComparesWithThePreviousMonth() {
        data(List.of(), total(2026, 8, "5000", "4000"), total(2026, 9, "5000", "3500"));

        InsightsDTO insights = service.insights(USER_ID, "2026-09", SEPTEMBER_10);

        assertEquals(0, new BigDecimal("1500").compareTo(insights.cashFlow().saved()));
        assertEquals(30, insights.cashFlow().savingsRate());
        assertEquals(20, insights.cashFlow().previousSavingsRate());
    }

    @Test
    void savingsRateIsUnknownWithoutIncome() {
        data(List.of(), total(2026, 9, "0", "800"));

        InsightsDTO insights = service.insights(USER_ID, "2026-09", SEPTEMBER_10);

        assertNull(insights.cashFlow().savingsRate());
        assertNull(insights.cashFlow().previousSavingsRate());
        assertEquals(0, new BigDecimal("-800").compareTo(insights.cashFlow().saved()));
    }

    @Test
    void aNegativeSavingsRateIsKept() {
        data(List.of(), total(2026, 9, "1000", "1500"));

        assertEquals(-50, service.insights(USER_ID, "2026-09", SEPTEMBER_10).cashFlow().savingsRate());
    }

    @Test
    void anEmptyMonthYieldsEmptySectionsAndZeroCashFlow() {
        data(List.of());

        InsightsDTO insights = service.insights(USER_ID, "2026-09", SEPTEMBER_10);

        assertEquals(0, BigDecimal.ZERO.compareTo(insights.cashFlow().income()));
        assertEquals(0, BigDecimal.ZERO.compareTo(insights.cashFlow().expense()));
        assertTrue(insights.movers().increases().isEmpty());
        assertTrue(insights.movers().decreases().isEmpty());
        assertTrue(insights.recurring().charges().isEmpty());
        assertTrue(insights.unusual().isEmpty());
        assertTrue(insights.topMerchants().isEmpty());
    }

    @Test
    void rankCategoriesByHowMuchTheyGrewAndShrankAgainstThePreviousMonth() {
        data(List.of(
                expense("2026-08-10", "Mercado", "Mercado A", "400"),
                expense("2026-08-11", "Lazer", "Cinema", "300"),
                expense("2026-08-12", "Transporte", "Uber", "100"),
                expense("2026-09-02", "Mercado", "Mercado A", "700"),
                expense("2026-09-03", "Lazer", "Cinema", "100"),
                expense("2026-09-04", "Saúde", "Farmácia", "250")));

        InsightsDTO insights = service.insights(USER_ID, "2026-09", SEPTEMBER_10);

        List<CategoryChange> increases = insights.movers().increases();
        assertEquals(List.of("Mercado", "Saúde"), increases.stream().map(CategoryChange::category).toList());
        assertEquals(0, new BigDecimal("300").compareTo(increases.get(0).change()));
        assertEquals(0, new BigDecimal("400").compareTo(increases.get(0).previous()));

        List<CategoryChange> decreases = insights.movers().decreases();
        assertEquals(List.of("Lazer", "Transporte"), decreases.stream().map(CategoryChange::category).toList());
        assertEquals(0, new BigDecimal("-200").compareTo(decreases.get(0).change()));
        assertEquals(0, new BigDecimal("-100").compareTo(decreases.get(1).change()));
    }

    @Test
    void keepsOnlyTheThreeBiggestMoversOfEachKind() {
        List<Transaction> expenses = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            expenses.add(expense("2026-09-0" + i, "Categoria " + i, "Compra " + i, String.valueOf(i * 100)));
        }
        data(expenses);

        List<String> names = service.insights(USER_ID, "2026-09", SEPTEMBER_10).movers().increases().stream()
                .map(CategoryChange::category).toList();

        assertEquals(List.of("Categoria 5", "Categoria 4", "Categoria 3"), names);
    }

    @Test
    void treatsUncategorizedExpensesAsOutros() {
        data(List.of(expense("2026-09-02", null, "Algo", "80")));

        assertEquals("Outros", service.insights(USER_ID, "2026-09", SEPTEMBER_10).movers().increases().get(0).category());
    }

    @Test
    void groupsTheTopMerchantsByNormalizedNameAndSortsByTotal() {
        data(List.of(
                expense("2026-09-01", "Mercado", "MERCADO EXTRA 101", "120"),
                expense("2026-09-05", "Mercado", "Mercado Extra 202", "80"),
                expense("2026-09-06", "Transporte", "Uber", "150"),
                expense("2026-09-07", "Lazer", "Cinema", "60")));

        var merchants = service.insights(USER_ID, "2026-09", SEPTEMBER_10).topMerchants();

        assertEquals(3, merchants.size());
        assertEquals("Mercado Extra 202", merchants.get(0).description());
        assertEquals(0, new BigDecimal("200").compareTo(merchants.get(0).total()));
        assertEquals(2, merchants.get(0).count());
        assertEquals("Uber", merchants.get(1).description());
    }

    @Test
    void listsAtMostFiveMerchants() {
        List<Transaction> expenses = new ArrayList<>();
        String[] names = {"Alfa", "Beta", "Gama", "Delta", "Epsilon", "Zeta", "Eta"};
        for (int i = 0; i < names.length; i++) {
            expenses.add(expense("2026-09-0" + (i + 1), "Outros", names[i], String.valueOf(10 * (i + 1))));
        }
        data(expenses);

        var merchants = service.insights(USER_ID, "2026-09", SEPTEMBER_10).topMerchants();

        assertEquals(5, merchants.size());
        assertEquals("Eta", merchants.get(0).description());
    }

    @Test
    void projectsTheMonthEndFromThePaceSoFar() {
        data(List.of(
                expense("2026-08-10", "Mercado", "Mercado", "500"),
                expense("2026-09-02", "Mercado", "Mercado", "200"),
                expense("2026-09-08", "Lazer", "Cinema", "100")));

        var projection = service.insights(USER_ID, "2026-09", SEPTEMBER_10).projection();

        assertEquals(10, projection.daysElapsed());
        assertEquals(30, projection.daysInMonth());
        assertEquals(0, new BigDecimal("300").compareTo(projection.spentSoFar()));
        assertEquals(0, new BigDecimal("900").compareTo(projection.projected()));
        assertEquals(0, new BigDecimal("500").compareTo(projection.previousMonth()));
    }

    @Test
    void doesNotProjectATooEarlyOrAFinishedMonth() {
        data(List.of(expense("2026-09-02", "Mercado", "Mercado", "200")));

        assertNull(service.insights(USER_ID, "2026-09", SEPTEMBER_3).projection());
        assertNull(service.insights(USER_ID, "2026-08", SEPTEMBER_10).projection());
    }

    @Test
    void defaultsToTheMonthOfToday() {
        data(List.of());

        assertEquals("2026-09", service.insights(USER_ID, null, SEPTEMBER_10).month());
    }

    @Test
    void rejectsAMalformedMonth() {
        assertThrows(BadRequestException.class, () -> service.insights(USER_ID, "setembro", SEPTEMBER_10));
    }
}
