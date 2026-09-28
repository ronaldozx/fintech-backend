package com.globo.fintech_backend.Investments.dto;

import java.math.BigDecimal;

public record EmergencyFundDTO(
        BigDecimal averageMonthlyExpenses,
        int referenceMonths,
        BigDecimal referenceAmount,
        BigDecimal availableAmount,
        boolean includesAccounts,
        BigDecimal coveredMonths
) {}
