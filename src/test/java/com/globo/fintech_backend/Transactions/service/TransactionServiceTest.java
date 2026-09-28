package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Transactions.dto.CategorySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.DailySummaryDTO;
import com.globo.fintech_backend.Transactions.repository.DayTotal;
import com.globo.fintech_backend.Transactions.dto.MonthlySummaryDTO;
import com.globo.fintech_backend.Transactions.repository.CategoryTotal;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Mock
    private TransactionRepository repository;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(repository);
    }

    private static CategoryTotal category(String name, String total, long count) {
        return new CategoryTotal() {
            @Override
            public String getCategory() {
                return name;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }

            @Override
            public Long getTransactionCount() {
                return count;
            }
        };
    }

    private static MonthTotal month(int year, int month, String income, String expense) {
        return new MonthTotal() {
            @Override
            public Integer getPeriodYear() {
                return year;
            }

            @Override
            public Integer getPeriodMonth() {
                return month;
            }

            @Override
            public BigDecimal getIncome() {
                return income == null ? null : new BigDecimal(income);
            }

            @Override
            public BigDecimal getExpense() {
                return expense == null ? null : new BigDecimal(expense);
            }
        };
    }

    @Test
    void categoryTotalsAreReportedAsPositiveAmountsKeepingTheRepositoryOrder() {
        List<CategoryTotal> totals = List.of(
                category("Alimentação", "-500.00", 12),
                category("Transporte", "-120.50", 4)
        );
        when(repository.getExpensesByCategory(USER_ID, START, END)).thenReturn(totals);

        List<CategorySummaryDTO> result = service.getExpensesByCategory(USER_ID, START, END);

        assertEquals(2, result.size());
        assertEquals(new CategorySummaryDTO("Alimentação", new BigDecimal("500.00"), 12), result.get(0));
        assertEquals(new CategorySummaryDTO("Transporte", new BigDecimal("120.50"), 4), result.get(1));
    }

    @Test
    void monthlyTotalsAreFormattedAsYearMonthWithPositiveExpenses() {
        List<MonthTotal> totals = List.of(
                month(2026, 1, "3000.00", "-1200.00"),
                month(2026, 11, "0", "-50.00")
        );
        when(repository.getMonthlyTotals(USER_ID, START, END)).thenReturn(totals);

        List<MonthlySummaryDTO> result = service.getMonthlySummary(USER_ID, START, END);

        assertEquals("2026-01", result.get(0).month());
        assertEquals(new BigDecimal("3000.00"), result.get(0).income());
        assertEquals(new BigDecimal("1200.00"), result.get(0).expense());
        assertEquals("2026-11", result.get(1).month());
    }

    @Test
    void monthlyTotalsTreatMissingSumsAsZero() {
        List<MonthTotal> totals = List.of(month(2026, 3, null, null));
        when(repository.getMonthlyTotals(USER_ID, START, END)).thenReturn(totals);

        MonthlySummaryDTO result = service.getMonthlySummary(USER_ID, START, END).get(0);

        assertEquals(BigDecimal.ZERO, result.income());
        assertEquals(BigDecimal.ZERO, result.expense());
    }

    @Test
    void dailyTotalsKeepTheDateAndReportExpensesAsPositive() {
        DayTotal day = new DayTotal() {
            @Override
            public LocalDate getPeriodDate() {
                return LocalDate.of(2026, 9, 4);
            }

            @Override
            public BigDecimal getIncome() {
                return new BigDecimal("200.00");
            }

            @Override
            public BigDecimal getExpense() {
                return new BigDecimal("-980.00");
            }
        };
        List<DayTotal> totals = List.of(day);
        when(repository.getDailyTotals(USER_ID, START, END)).thenReturn(totals);

        DailySummaryDTO result = service.getDailySummary(USER_ID, START, END).get(0);

        assertEquals(LocalDate.of(2026, 9, 4), result.date());
        assertEquals(new BigDecimal("200.00"), result.income());
        assertEquals(new BigDecimal("980.00"), result.expense());
    }

    @Test
    void rejectsAnInvertedRangeOnEveryQuery() {
        LocalDate later = END.plusDays(1);

        assertThrows(BadRequestException.class, () -> service.getExpensesByCategory(USER_ID, later, END));
        assertThrows(BadRequestException.class, () -> service.getMonthlySummary(USER_ID, later, END));
        assertThrows(BadRequestException.class, () -> service.getDailySummary(USER_ID, later, END));
        assertThrows(BadRequestException.class, () -> service.getDashboardData(USER_ID, later, END, Pageable.unpaged()));
        verify(repository, never()).getExpensesByCategory(USER_ID, later, END);
    }
}
