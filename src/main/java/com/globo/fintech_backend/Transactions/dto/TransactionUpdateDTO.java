package com.globo.fintech_backend.Transactions.dto;

import com.globo.fintech_backend.Transactions.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionUpdateDTO(
        String category,
        Boolean neutral,
        String description,
        BigDecimal amount,
        TransactionType type,
        LocalDate date
) {
    public boolean touchesManualOnlyFields() {
        return description != null || amount != null || type != null || date != null;
    }
}
