package com.globo.fintech_backend.Transactions.dto;

import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDTO(
        String description,
        BigDecimal amount,
        LocalDate date,
        TransactionType type,
        PaymentMethod paymentMethod,
        String category,
        Boolean neutral
) {}
