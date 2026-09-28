package com.globo.fintech_backend.Budgets.dto;

import java.math.BigDecimal;

public record BudgetDTO(Long id, String category, BigDecimal monthlyLimit) {}
