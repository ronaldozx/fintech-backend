package com.globo.fintech_backend.Goals.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalRequestDTO(String name, BigDecimal targetAmount, LocalDate targetDate) {}
