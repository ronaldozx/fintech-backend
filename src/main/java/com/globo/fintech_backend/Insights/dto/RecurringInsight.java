package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;
import java.util.List;

public record RecurringInsight(List<RecurringCharge> charges, BigDecimal monthlyTotal) {}
