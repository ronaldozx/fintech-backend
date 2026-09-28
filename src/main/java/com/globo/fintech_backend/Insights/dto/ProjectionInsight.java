package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;

public record ProjectionInsight(
        BigDecimal spentSoFar,
        BigDecimal projected,
        BigDecimal previousMonth,
        int daysElapsed,
        int daysInMonth
) {}
