package com.globo.fintech_backend.Transactions.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySummaryDTO(LocalDate date, BigDecimal income, BigDecimal expense) {}
