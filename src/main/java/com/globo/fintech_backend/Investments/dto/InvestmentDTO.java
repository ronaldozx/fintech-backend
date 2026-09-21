package com.globo.fintech_backend.Investments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InvestmentDTO(
        String id,
        String name,
        String type,
        String typeLabel,
        String institution,
        String issuer,
        BigDecimal balance,
        BigDecimal invested,
        BigDecimal profit,
        BigDecimal profitPercent,
        LocalDate dueDate,
        Long daysToDue,
        BigDecimal rate,
        String rateType
) {}
