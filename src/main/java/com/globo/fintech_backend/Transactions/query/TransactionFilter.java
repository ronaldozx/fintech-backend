package com.globo.fintech_backend.Transactions.query;

import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;

import java.time.LocalDate;

public record TransactionFilter(
        Long userId,
        LocalDate startDate,
        LocalDate endDate,
        String query,
        String category,
        TransactionType type,
        PaymentMethod paymentMethod,
        Boolean neutral
) {}
