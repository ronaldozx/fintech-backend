package com.globo.fintech_backend.Agenda.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AgendaItemDTO(
        AgendaItemType type,
        String title,
        String subtitle,
        LocalDate date,
        long daysUntil,
        BigDecimal amount
) {}
