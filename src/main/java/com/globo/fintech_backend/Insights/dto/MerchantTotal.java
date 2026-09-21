package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;

public record MerchantTotal(String description, BigDecimal total, int count) {}
