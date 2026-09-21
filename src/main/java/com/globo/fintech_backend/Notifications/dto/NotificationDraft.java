package com.globo.fintech_backend.Notifications.dto;

import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;

public record NotificationDraft(
        String type,
        NotificationSeverity severity,
        String title,
        String message,
        String link,
        String dedupeKey
) {}
