package com.globo.fintech_backend.Transactions.dto;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;

public record TransactionSearchDTO(
        Page<TransactionRowDTO> transactions,
        BigDecimal totalIncome,
        BigDecimal totalExpense
) {}
