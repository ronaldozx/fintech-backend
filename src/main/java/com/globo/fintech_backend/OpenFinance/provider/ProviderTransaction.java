package com.globo.fintech_backend.OpenFinance.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderTransaction(
        String id,
        String description,
        BigDecimal amount,
        LocalDate date,
        ProviderTransactionType type,
        boolean posted,
        String category
) {}
