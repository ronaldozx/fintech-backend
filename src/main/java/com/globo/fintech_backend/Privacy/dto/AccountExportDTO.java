package com.globo.fintech_backend.Privacy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AccountExportDTO(
        LocalDateTime exportedAt,
        Profile profile,
        List<Connection> connections,
        List<TransactionItem> transactions,
        List<Budget> budgets,
        List<Goal> goals
) {

    public record Profile(String email, String fullName, LocalDate birthDate, Double monthlyIncome, LocalDateTime createdAt) {}

    public record Connection(String institutionName, String status, LocalDateTime connectedAt, LocalDateTime lastSyncedAt) {}

    public record TransactionItem(
            LocalDate date,
            String description,
            BigDecimal amount,
            String type,
            String category,
            String paymentMethod,
            boolean countsInTotals,
            boolean manual
    ) {}

    public record Budget(String category, BigDecimal monthlyLimit) {}

    public record Contribution(LocalDate date, BigDecimal amount) {}

    public record Goal(String name, BigDecimal targetAmount, BigDecimal savedAmount, LocalDate targetDate, List<Contribution> contributions) {}
}
