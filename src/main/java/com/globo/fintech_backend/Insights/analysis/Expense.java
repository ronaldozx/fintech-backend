package com.globo.fintech_backend.Insights.analysis;

import com.globo.fintech_backend.Transactions.entity.Transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record Expense(LocalDate date, String description, String category, BigDecimal amount) {

    private static final String UNCATEGORIZED = "Outros";

    public static Expense from(Transaction transaction) {
        String category = transaction.getCategory() == null || transaction.getCategory().isBlank()
                ? UNCATEGORIZED
                : transaction.getCategory();
        String description = transaction.getDescription() == null ? "" : transaction.getDescription().trim();

        return new Expense(transaction.getDate(), description, category, transaction.getAmount().abs());
    }

    public YearMonth month() {
        return YearMonth.from(date);
    }
}
