package com.globo.fintech_backend.Goals.service;

import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.entity.Goal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GoalMathTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private static Goal goal(String target, String saved, LocalDate targetDate, LocalDate created) {
        Goal goal = new Goal();
        goal.setId(1L);
        goal.setName("Viagem");
        goal.setTargetAmount(new BigDecimal(target));
        goal.setSavedAmount(new BigDecimal(saved));
        goal.setTargetDate(targetDate);
        goal.setCreatedAt(created == null ? null : created.atStartOfDay());
        return goal;
    }

    @Test
    void aGoalWithoutDeadlineIsOpenAndHasNoMonthlyNeed() {
        GoalDTO dto = GoalMath.toDto(goal("1000", "250", null, TODAY), TODAY, BigDecimal.ZERO);

        assertEquals(GoalStatus.OPEN, dto.status());
        assertEquals(25, dto.percent());
        assertEquals(new BigDecimal("750.00"), dto.remaining());
        assertNull(dto.monthsLeft());
        assertNull(dto.monthlyNeeded());
    }

    @Test
    void reachingTheTargetAchievesTheGoalAndCapsThePercentage() {
        GoalDTO dto = GoalMath.toDto(goal("1000", "1300", LocalDate.of(2027, 1, 1), TODAY), TODAY, new BigDecimal("500"));

        assertEquals(GoalStatus.ACHIEVED, dto.status());
        assertEquals(100, dto.percent());
        assertEquals(new BigDecimal("0.00"), dto.remaining().setScale(2));
        assertNull(dto.monthlyNeeded());
        assertNull(dto.monthsAtCurrentPace());
    }

    @Test
    void aMissedDeadlineIsOverdue() {
        GoalDTO dto = GoalMath.toDto(goal("1000", "100", TODAY.minusDays(1), TODAY.minusMonths(6)), TODAY, BigDecimal.ZERO);

        assertEquals(GoalStatus.OVERDUE, dto.status());
        assertNull(dto.monthlyNeeded());
    }

    @Test
    void fallingWellBehindTheExpectedPaceIsBehind() {
        Goal goal = goal("1000", "300", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertEquals(GoalStatus.BEHIND, GoalMath.status(goal, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void beingCloseToTheExpectedPaceIsOnTrack() {
        Goal goal = goal("1000", "450", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 1, 1));

        assertEquals(GoalStatus.ON_TRACK, GoalMath.status(goal, LocalDate.of(2026, 7, 1)));
    }

    @Test
    void aBrandNewGoalIsOnTrack() {
        Goal goal = goal("1000", "0", TODAY.plusMonths(6), TODAY);

        assertEquals(GoalStatus.ON_TRACK, GoalMath.status(goal, TODAY));
    }

    @Test
    void countsRemainingMonthsRoundingUpAndNeverBelowOne() {
        assertEquals(3, GoalMath.monthsLeft(TODAY, TODAY.plusMonths(3)));
        assertEquals(4, GoalMath.monthsLeft(TODAY, TODAY.plusMonths(3).plusDays(1)));
        assertEquals(1, GoalMath.monthsLeft(TODAY, TODAY.plusDays(4)));
        assertEquals(1, GoalMath.monthsLeft(TODAY, TODAY));
    }

    @Test
    void monthlyNeedSplitsTheRemainingAmountRoundingUp() {
        GoalDTO dto = GoalMath.toDto(goal("1000", "100", TODAY.plusMonths(3), TODAY), TODAY, BigDecimal.ZERO);

        assertEquals(3, dto.monthsLeft());
        assertEquals(new BigDecimal("300.00"), dto.monthlyNeeded());

        GoalDTO odd = GoalMath.toDto(goal("1000", "0", TODAY.plusMonths(3), TODAY), TODAY, BigDecimal.ZERO);
        assertEquals(new BigDecimal("333.34"), odd.monthlyNeeded());
    }

    @Test
    void projectsTheMonthsAtTheCurrentSavingsPace() {
        GoalDTO dto = GoalMath.toDto(goal("1000", "100", null, TODAY), TODAY, new BigDecimal("400"));

        assertEquals(3, dto.monthsAtCurrentPace());
    }

    @Test
    void noProjectionWhenTheUserIsNotSaving() {
        assertNull(GoalMath.toDto(goal("1000", "100", null, TODAY), TODAY, new BigDecimal("-50")).monthsAtCurrentPace());
        assertNull(GoalMath.toDto(goal("1000", "100", null, TODAY), TODAY, BigDecimal.ZERO).monthsAtCurrentPace());
    }

    @Test
    void percentageRoundsDownSoItNeverClaimsMoreThanSaved() {
        assertEquals(33, GoalMath.percent(new BigDecimal("333.33"), new BigDecimal("1000")));
        assertEquals(99, GoalMath.percent(new BigDecimal("999.99"), new BigDecimal("1000")));
    }
}
