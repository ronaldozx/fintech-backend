package com.globo.fintech_backend.Transactions.dto;

import com.globo.fintech_backend.Transactions.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionCreateDTO(
        String description,
        BigDecimal amount,
        TransactionType type,
        LocalDate date,
        String category
) {}
