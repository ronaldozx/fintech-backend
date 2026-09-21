package com.globo.fintech_backend.Insights.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringCharge(String description, BigDecimal averageAmount, int months, LocalDate lastDate) {}
