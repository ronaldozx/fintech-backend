package com.globo.fintech_backend.Goals.service;

import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.entity.Goal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class GoalMath {

    static final int PACE_TOLERANCE_POINTS = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GoalMath() {
    }

    public static GoalDTO toDto(Goal goal, LocalDate today, BigDecimal averageMonthlySavings) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal saved = goal.getSavedAmount();
        BigDecimal remaining = target.subtract(saved).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        GoalStatus status = status(goal, today);

        Integer monthsLeft = null;
        BigDecimal monthlyNeeded = null;
        if (goal.getTargetDate() != null && status != GoalStatus.ACHIEVED && status != GoalStatus.OVERDUE) {
            monthsLeft = monthsLeft(today, goal.getTargetDate());
            monthlyNeeded = remaining.divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
        }

        Integer monthsAtPace = null;
        if (status != GoalStatus.ACHIEVED && averageMonthlySavings != null && averageMonthlySavings.signum() > 0) {
            monthsAtPace = remaining.divide(averageMonthlySavings, 0, RoundingMode.CEILING).intValue();
        }

        return new GoalDTO(
                goal.getId(),
                goal.getName(),
                target,
                saved,
                remaining,
                percent(saved, target),
                goal.getTargetDate(),
                status,
                monthsLeft,
                monthlyNeeded,
                monthsAtPace);
    }

    static GoalStatus status(Goal goal, LocalDate today) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal saved = goal.getSavedAmount();

        if (saved.compareTo(target) >= 0) {
            return GoalStatus.ACHIEVED;
        }
        if (goal.getTargetDate() == null) {
            return GoalStatus.OPEN;
        }
        if (today.isAfter(goal.getTargetDate())) {
            return GoalStatus.OVERDUE;
        }

        LocalDate start = goal.getCreatedAt() == null ? today : goal.getCreatedAt().toLocalDate();
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(start, goal.getTargetDate()));
        long elapsedDays = Math.min(totalDays, Math.max(0, ChronoUnit.DAYS.between(start, today)));

        BigDecimal expected = BigDecimal.valueOf(elapsedDays).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalDays), 4, RoundingMode.HALF_UP);
        BigDecimal actual = saved.multiply(HUNDRED).divide(target, 4, RoundingMode.HALF_UP);

        return actual.add(BigDecimal.valueOf(PACE_TOLERANCE_POINTS)).compareTo(expected) < 0
                ? GoalStatus.BEHIND
                : GoalStatus.ON_TRACK;
    }

    static int monthsLeft(LocalDate today, LocalDate targetDate) {
        long full = ChronoUnit.MONTHS.between(today, targetDate);
        boolean hasRemainder = today.plusMonths(full).isBefore(targetDate);
        return (int) Math.max(1, full + (hasRemainder ? 1 : 0));
    }

    static int percent(BigDecimal saved, BigDecimal target) {
        return saved.multiply(HUNDRED).divide(target, 0, RoundingMode.DOWN).min(HUNDRED).max(BigDecimal.ZERO).intValue();
    }
}
