package com.globo.fintech_backend.Investments.dto;

import java.math.BigDecimal;

public record AllocationDTO(String label, BigDecimal total, int percent) {}
