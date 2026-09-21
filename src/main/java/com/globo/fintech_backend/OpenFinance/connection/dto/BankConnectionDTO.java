package com.globo.fintech_backend.OpenFinance.connection.dto;

import java.time.LocalDateTime;

public record BankConnectionDTO(
        Long id,
        String itemId,
        String institutionName,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastSyncedAt,
        LocalDateTime lastSyncAttemptAt,
        String lastSyncError
) {}
