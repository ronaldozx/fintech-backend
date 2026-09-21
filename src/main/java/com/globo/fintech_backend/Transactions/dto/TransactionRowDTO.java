package com.globo.fintech_backend.Transactions.dto;

import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRowDTO(
        Long id,
        String description,
        BigDecimal amount,
        LocalDate date,
        TransactionType type,
        PaymentMethod paymentMethod,
        String category,
        boolean neutral,
        String neutralReason,
        boolean manual,
        boolean userEdited
) {
    public TransactionRowDTO(Long id, String description, BigDecimal amount, LocalDate date, TransactionType type,
                             PaymentMethod paymentMethod, String category, boolean neutral) {
        this(id, description, amount, date, type, paymentMethod, category, neutral, null, false, false);
    }
}
