package com.globo.fintech_backend.OpenFinance.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderAccount(
        String id,
        ProviderAccountType type,
        String name,
        BigDecimal balance,
        String currencyCode,
        String number,
        String marketingName,
        BigDecimal creditLimit,
        BigDecimal availableCredit,
        LocalDate dueDate,
        String brand,
        BigDecimal overdraftLimit,
        BigDecimal overdraftUsed
) {

    public ProviderAccount(String id, ProviderAccountType type, String name, BigDecimal balance,
                           String currencyCode, String number) {
        this(id, type, name, balance, currencyCode, number, null, null, null, null, null, null, null);
    }
}
