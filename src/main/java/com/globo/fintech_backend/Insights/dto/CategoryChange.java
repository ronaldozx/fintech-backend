package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;

public record CategoryChange(String category, BigDecimal current, BigDecimal previous, BigDecimal change) {}
