package com.globo.fintech_backend.Budgets.dto;

import java.math.BigDecimal;
import java.util.List;

public record BudgetsOverviewDTO(
        String month,
        List<BudgetProgressDTO> budgets,
        List<CategorySpendDTO> unbudgeted,
        BigDecimal totalSpent
) {}
