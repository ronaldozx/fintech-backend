package com.globo.fintech_backend.Notifications.dto;

import java.util.List;

public record NotificationsDTO(long unread, List<NotificationDTO> items) {}
