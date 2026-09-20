package com.globo.fintech_backend.Transactions.dto;

import java.math.BigDecimal;

public record MonthlySummaryDTO(String month, BigDecimal income, BigDecimal expense) {}
