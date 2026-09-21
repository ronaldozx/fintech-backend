package com.globo.fintech_backend.Agenda.dto;

import java.math.BigDecimal;
import java.util.List;

public record AgendaDTO(
        int days,
        List<AgendaItemDTO> items,
        BigDecimal billsTotal,
        BigDecimal recurringTotal,
        boolean accountsUnavailable
) {}
