package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UnusualExpense(String description, String category, LocalDate date, BigDecimal amount, BigDecimal typicalAmount) {}
