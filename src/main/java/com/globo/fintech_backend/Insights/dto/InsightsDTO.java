package com.globo.fintech_backend.Insights.dto;

import java.util.List;

public record InsightsDTO(
        String month,
        CashFlowInsight cashFlow,
        CategoryMovers movers,
        RecurringInsight recurring,
        List<UnusualExpense> unusual,
        List<MerchantTotal> topMerchants,
        ProjectionInsight projection
) {}
