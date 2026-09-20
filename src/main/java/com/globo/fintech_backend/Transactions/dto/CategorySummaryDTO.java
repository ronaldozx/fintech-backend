package com.globo.fintech_backend.Transactions.dto;

import java.math.BigDecimal;

public record CategorySummaryDTO(String category, BigDecimal total, long count) {}
