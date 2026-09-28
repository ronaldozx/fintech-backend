package com.globo.fintech_backend.OpenFinance.accounts;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountDTO(
        Long connectionId,
        String institution,
        String name,
        String type,
        String maskedNumber,
        BigDecimal balance,
        String currency,
        BigDecimal creditLimit,
        BigDecimal availableCredit,
        LocalDate dueDate,
        BigDecimal overdraftLimit,
        BigDecimal overdraftUsed
) {}
