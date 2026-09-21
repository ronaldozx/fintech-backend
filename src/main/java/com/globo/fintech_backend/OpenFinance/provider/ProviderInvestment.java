package com.globo.fintech_backend.OpenFinance.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderInvestment(
        String id,
        String name,
        String type,
        String subtype,
        BigDecimal balance,
        BigDecimal invested,
        BigDecimal profit,
        LocalDate dueDate,
        LocalDate purchaseDate,
        String issuer,
        BigDecimal rate,
        String rateType
) {}
