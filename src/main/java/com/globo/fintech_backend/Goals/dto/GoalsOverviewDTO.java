package com.globo.fintech_backend.Goals.dto;

import java.math.BigDecimal;
import java.util.List;

public record GoalsOverviewDTO(
        List<GoalDTO> goals,
        BigDecimal totalTarget,
        BigDecimal totalSaved,
        BigDecimal averageMonthlySavings
) {}
