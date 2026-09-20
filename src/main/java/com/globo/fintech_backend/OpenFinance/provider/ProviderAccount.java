package com.globo.fintech_backend.OpenFinance.provider;

import java.math.BigDecimal;

public record ProviderAccount(
        String id,
        ProviderAccountType type,
        String name,
        BigDecimal balance,
        String currencyCode
) {}
