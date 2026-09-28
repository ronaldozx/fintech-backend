package com.globo.fintech_backend.Budgets.dto;

import java.math.BigDecimal;

public record BudgetRequestDTO(String category, BigDecimal monthlyLimit) {}
