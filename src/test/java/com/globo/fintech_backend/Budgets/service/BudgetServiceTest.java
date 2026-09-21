package com.globo.fintech_backend.Budgets.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Budgets.dto.BudgetDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetProgressDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetRequestDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetStatus;
import com.globo.fintech_backend.Budgets.dto.BudgetUpdateDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.entity.Budget;
import com.globo.fintech_backend.Budgets.repository.BudgetRepository;
import com.globo.fintech_backend.Transactions.repository.CategoryTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ConflictException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final Long USER_ID = 7L;
    private static final YearMonth TODAY = YearMonth.of(2026, 9);

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private BudgetService service;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepository, transactionRepository, userRepository);
    }

    private static Budget budget(long id, String category, String limit) {
        Budget budget = new Budget();
        budget.setId(id);
        budget.setCategory(category);
        budget.setMonthlyLimit(new BigDecimal(limit));
        return budget;
    }

    private static CategoryTotal spent(String category, String total) {
        CategoryTotal row = mock(CategoryTotal.class);
        when(row.getCategory()).thenReturn(category);
        when(row.getTotal()).thenReturn(new BigDecimal(total));
        return row;
    }

    private void spending(CategoryTotal... rows) {
        when(transactionRepository.getExpensesByCategory(USER_ID, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .thenReturn(List.of(rows));
    }

    @Test
    void statusFollowsTheWarningAndExceededThresholds() {
        assertEquals(BudgetStatus.OK, BudgetService.statusFor(79));
        assertEquals(BudgetStatus.WARNING, BudgetService.statusFor(80));
        assertEquals(BudgetStatus.WARNING, BudgetService.statusFor(99));
        assertEquals(BudgetStatus.EXCEEDED, BudgetService.statusFor(100));
        assertEquals(BudgetStatus.EXCEEDED, BudgetService.statusFor(250));
    }

    @Test
    void computesProgressFromTheMonthsCountedExpenses() {
        spending(spent("Mercado", "-450.00"), spent("Transporte", "-100.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(
                budget(1, "Mercado", "500.00"), budget(2, "Transporte", "400.00")));

        BudgetsOverviewDTO overview = service.overview(USER_ID, "2026-09", TODAY);

        BudgetProgressDTO market = overview.budgets().get(0);
        assertEquals("Mercado", market.category());
        assertEquals(90, market.percent());
        assertEquals(BudgetStatus.WARNING, market.status());
        assertEquals(0, new BigDecimal("50.00").compareTo(market.remaining()));

        BudgetProgressDTO transport = overview.budgets().get(1);
        assertEquals(25, transport.percent());
        assertEquals(BudgetStatus.OK, transport.status());
        assertEquals(0, new BigDecimal("550.00").compareTo(overview.totalSpent()));
    }

    @Test
    void marksABudgetAsExceededAndKeepsNegativeRemaining() {
        spending(spent("Lazer", "-300.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(budget(1, "Lazer", "200.00")));

        BudgetProgressDTO leisure = service.overview(USER_ID, "2026-09", TODAY).budgets().get(0);

        assertEquals(150, leisure.percent());
        assertEquals(BudgetStatus.EXCEEDED, leisure.status());
        assertEquals(0, new BigDecimal("-100.00").compareTo(leisure.remaining()));
    }

    @Test
    void theOverallBudgetUsesTheTotalSpentAndComesFirst() {
        spending(spent("Mercado", "-300.00"), spent("Lazer", "-100.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(
                budget(1, "Mercado", "300.00"), budget(2, null, "1000.00")));

        List<BudgetProgressDTO> budgets = service.overview(USER_ID, "2026-09", TODAY).budgets();

        assertNull(budgets.get(0).category());
        assertEquals(40, budgets.get(0).percent());
        assertEquals("Mercado", budgets.get(1).category());
    }

    @Test
    void categoriesWithoutABudgetAreListedBiggestFirst() {
        spending(spent("Mercado", "-300.00"), spent("Lazer", "-100.00"), spent("Saúde", "-250.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(budget(1, "mercado", "500.00")));

        BudgetsOverviewDTO overview = service.overview(USER_ID, "2026-09", TODAY);

        assertEquals(60, overview.budgets().get(0).percent());
        assertEquals(2, overview.unbudgeted().size());
        assertEquals("Saúde", overview.unbudgeted().get(0).category());
        assertEquals("Lazer", overview.unbudgeted().get(1).category());
    }

    @Test
    void aBudgetWithNoSpendingStartsAtZero() {
        spending();
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(budget(1, "Viagem", "800.00")));

        BudgetProgressDTO trip = service.overview(USER_ID, "2026-09", TODAY).budgets().get(0);

        assertEquals(0, trip.percent());
        assertEquals(BudgetStatus.OK, trip.status());
        assertEquals(0, BigDecimal.ZERO.compareTo(trip.spent()));
    }

    @Test
    void defaultsToTheCurrentMonthWhenNoneIsGiven() {
        spending();
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of());

        assertEquals("2026-09", service.overview(USER_ID, null, TODAY).month());
        assertEquals("2026-09", service.overview(USER_ID, "  ", TODAY).month());
    }

    @Test
    void rejectsAMalformedMonth() {
        assertThrows(BadRequestException.class, () -> service.overview(USER_ID, "setembro", TODAY));
        assertThrows(BadRequestException.class, () -> service.overview(USER_ID, "2026-13", TODAY));
    }

    @Test
    void capsAbsurdPercentagesInsteadOfOverflowing() {
        spending(spent("Mercado", "-99999999.00"));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(budget(1, "Mercado", "0.01")));

        assertEquals(999, service.overview(USER_ID, "2026-09", TODAY).budgets().get(0).percent());
    }

    @Test
    void createsABudgetTrimmingTheCategory() {
        when(budgetRepository.existsByUserIdAndCategory(USER_ID, "Mercado")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(call -> {
            Budget saved = call.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        BudgetDTO created = service.create(USER_ID, new BudgetRequestDTO("  Mercado ", new BigDecimal("500")));

        assertEquals(10L, created.id());
        assertEquals("Mercado", created.category());
        assertEquals(new BigDecimal("500.00"), created.monthlyLimit());
    }

    @Test
    void aBlankCategoryCreatesTheOverallBudget() {
        when(budgetRepository.existsByUserIdAndCategoryIsNull(USER_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(call -> call.getArgument(0));

        assertNull(service.create(USER_ID, new BudgetRequestDTO("   ", new BigDecimal("3000"))).category());
    }

    @Test
    void refusesADuplicatedCategory() {
        when(budgetRepository.existsByUserIdAndCategory(USER_ID, "Mercado")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.create(USER_ID, new BudgetRequestDTO("Mercado", new BigDecimal("500"))));
        verify(budgetRepository, never()).save(any());
    }

    @Test
    void refusesASecondOverallBudget() {
        when(budgetRepository.existsByUserIdAndCategoryIsNull(USER_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.create(USER_ID, new BudgetRequestDTO(null, new BigDecimal("500"))));
    }

    @Test
    void refusesMissingZeroOrNegativeLimits() {
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new BudgetRequestDTO("Mercado", null)));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new BudgetRequestDTO("Mercado", BigDecimal.ZERO)));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new BudgetRequestDTO("Mercado", new BigDecimal("-5"))));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, 1L, new BudgetUpdateDTO(BigDecimal.ZERO)));
    }

    @Test
    void updatesOnlyTheLimitOfAnOwnedBudget() {
        Budget existing = budget(1, "Mercado", "500.00");
        when(budgetRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        when(budgetRepository.save(existing)).thenReturn(existing);

        BudgetDTO updated = service.update(USER_ID, 1L, new BudgetUpdateDTO(new BigDecimal("650.5")));

        assertEquals("Mercado", updated.category());
        assertEquals(new BigDecimal("650.50"), updated.monthlyLimit());
    }

    @Test
    void cannotUpdateOrDeleteAnotherUsersBudget() {
        when(budgetRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(USER_ID, 99L, new BudgetUpdateDTO(new BigDecimal("10"))));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(USER_ID, 99L));
        verify(budgetRepository, never()).delete(any());
    }

    @Test
    void offersOnlyTheCategoriesOfCountedExpenses() {
        when(transactionRepository.findExpenseCategories(USER_ID)).thenReturn(List.of("Lazer", "Mercado"));

        assertEquals(List.of("Lazer", "Mercado"), service.categories(USER_ID));
    }

    @Test
    void deletesAnOwnedBudget() {
        Budget existing = budget(1, "Mercado", "500.00");
        when(budgetRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 1L);

        verify(budgetRepository).delete(existing);
    }
}
