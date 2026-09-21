package com.globo.fintech_backend.Goals.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalRequestDTO;
import com.globo.fintech_backend.Goals.dto.GoalsOverviewDTO;
import com.globo.fintech_backend.Goals.entity.Goal;
import com.globo.fintech_backend.Goals.entity.GoalContribution;
import com.globo.fintech_backend.Goals.repository.GoalContributionRepository;
import com.globo.fintech_backend.Goals.repository.GoalRepository;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalContributionRepository contributionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository, contributionRepository, userRepository, transactionRepository);
    }

    private static Goal goal(long id, String target, String saved) {
        Goal goal = new Goal();
        goal.setId(id);
        goal.setName("Viagem");
        goal.setTargetAmount(new BigDecimal(target));
        goal.setSavedAmount(new BigDecimal(saved));
        return goal;
    }

    private static MonthTotal month(String income, String expense) {
        MonthTotal total = mock(MonthTotal.class);
        when(total.getIncome()).thenReturn(new BigDecimal(income));
        when(total.getExpense()).thenReturn(new BigDecimal(expense));
        return total;
    }

    private void savingHistory(MonthTotal... months) {
        when(transactionRepository.getMonthlyTotals(eq(USER_ID), any(), any())).thenReturn(List.of(months));
    }

    private void noHistory() {
        savingHistory();
    }

    private void savingGoal() {
        when(goalRepository.save(any(Goal.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void createsAGoalTrimmingTheNameAndStartingAtZero() {
        noHistory();
        savingGoal();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));

        GoalDTO created = service.create(USER_ID, new GoalRequestDTO("  Viagem  ", new BigDecimal("5000"), TODAY.plusMonths(6)), TODAY);

        assertEquals("Viagem", created.name());
        assertEquals(new BigDecimal("5000.00"), created.targetAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(created.savedAmount()));
    }

    @Test
    void refusesInvalidGoals() {
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("", new BigDecimal("10"), null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("x".repeat(GoalService.MAX_NAME + 1), new BigDecimal("10"), null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("A", null, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("A", BigDecimal.ZERO, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("A", new BigDecimal("-1"), null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("A", new BigDecimal("10"), TODAY.minusDays(1)), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID, new GoalRequestDTO("A", new BigDecimal("10"), TODAY.plusYears(80)), TODAY));
        verify(goalRepository, never()).save(any());
    }

    @Test
    void updateAllowsKeepingAnAlreadyPastDeadlineButNotSettingANewPastOne() {
        Goal existing = goal(1L, "1000", "100");
        existing.setTargetDate(TODAY.minusDays(10));
        when(goalRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        noHistory();
        savingGoal();

        GoalDTO kept = service.update(USER_ID, 1L, new GoalRequestDTO("Nova", new BigDecimal("2000"), TODAY.minusDays(10)), TODAY);
        assertEquals("Nova", kept.name());

        assertThrows(BadRequestException.class,
                () -> service.update(USER_ID, 1L, new GoalRequestDTO("Nova", new BigDecimal("2000"), TODAY.minusDays(3)), TODAY));
    }

    @Test
    void contributionsAddUpAndAreRecorded() {
        Goal existing = goal(1L, "1000", "100");
        when(goalRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        noHistory();
        savingGoal();

        GoalDTO updated = service.contribute(USER_ID, 1L, new BigDecimal("250.5"), TODAY);

        assertEquals(new BigDecimal("350.50"), updated.savedAmount());
        ArgumentCaptor<GoalContribution> saved = ArgumentCaptor.forClass(GoalContribution.class);
        verify(contributionRepository).save(saved.capture());
        assertEquals(new BigDecimal("250.50"), saved.getValue().getAmount());
        assertEquals(TODAY, saved.getValue().getDate());
    }

    @Test
    void aWithdrawalReducesTheSavedAmountButNeverBelowZero() {
        Goal existing = goal(1L, "1000", "300");
        when(goalRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));
        noHistory();
        savingGoal();

        assertEquals(new BigDecimal("200.00"), service.contribute(USER_ID, 1L, new BigDecimal("-100"), TODAY).savedAmount());
        assertThrows(BadRequestException.class, () -> service.contribute(USER_ID, 1L, new BigDecimal("-500"), TODAY));
    }

    @Test
    void aZeroOrMissingContributionIsRefused() {
        assertThrows(BadRequestException.class, () -> service.contribute(USER_ID, 1L, BigDecimal.ZERO, TODAY));
        assertThrows(BadRequestException.class, () -> service.contribute(USER_ID, 1L, null, TODAY));
        verify(contributionRepository, never()).save(any());
    }

    @Test
    void cannotTouchAnotherUsersGoal() {
        when(goalRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.contribute(USER_ID, 99L, BigDecimal.TEN, TODAY));
        assertThrows(ResourceNotFoundException.class,
                () -> service.update(USER_ID, 99L, new GoalRequestDTO("A", BigDecimal.TEN, null), TODAY));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(USER_ID, 99L));
    }

    @Test
    void deletingAGoalAlsoRemovesItsContributions() {
        Goal existing = goal(1L, "1000", "100");
        when(goalRepository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, 1L);

        verify(contributionRepository).deleteByGoalId(1L);
        verify(goalRepository).delete(existing);
    }

    @Test
    void theOverviewTotalsTheGoalsAndAveragesTheRecentSavings() {
        savingHistory(month("5000", "-3000"), month("4000", "-4500"));
        when(goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(USER_ID)).thenReturn(List.of(goal(1L, "1000", "200"), goal(2L, "500", "500")));

        GoalsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertEquals(2, overview.goals().size());
        assertEquals(0, new BigDecimal("1500").compareTo(overview.totalTarget()));
        assertEquals(0, new BigDecimal("700").compareTo(overview.totalSaved()));
        assertEquals(new BigDecimal("750.00"), overview.averageMonthlySavings());
    }

    @Test
    void withoutHistoryTheAverageSavingsIsZero() {
        noHistory();
        when(goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(USER_ID)).thenReturn(List.of());

        assertEquals(0, BigDecimal.ZERO.compareTo(service.overview(USER_ID, TODAY).averageMonthlySavings()));
    }
}
