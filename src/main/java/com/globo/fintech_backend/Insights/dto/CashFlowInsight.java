package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;

public record CashFlowInsight(
        BigDecimal income,
        BigDecimal expense,
        BigDecimal saved,
        Integer savingsRate,
        Integer previousSavingsRate
) {}
