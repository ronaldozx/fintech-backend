package com.globo.fintech_backend.Budgets.dto;

import java.math.BigDecimal;

public record CategorySpendDTO(String category, BigDecimal spent) {}
