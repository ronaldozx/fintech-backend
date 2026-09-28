package com.globo.fintech_backend.Notifications.dto;

import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;

import java.time.LocalDateTime;

public record NotificationDTO(
        Long id,
        String type,
        NotificationSeverity severity,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt
) {}
