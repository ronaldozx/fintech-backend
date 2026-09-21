package com.globo.fintech_backend.Goals.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalDTO(
        Long id,
        String name,
        BigDecimal targetAmount,
        BigDecimal savedAmount,
        BigDecimal remaining,
        int percent,
        LocalDate targetDate,
        GoalStatus status,
        Integer monthsLeft,
        BigDecimal monthlyNeeded,
        Integer monthsAtCurrentPace
) {}
