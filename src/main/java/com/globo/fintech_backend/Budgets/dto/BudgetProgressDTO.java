package com.globo.fintech_backend.Budgets.dto;

import java.math.BigDecimal;

public record BudgetProgressDTO(
        Long id,
        String category,
        BigDecimal monthlyLimit,
        BigDecimal spent,
        BigDecimal remaining,
        int percent,
        BudgetStatus status
) {}
