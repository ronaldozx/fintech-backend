package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Notifications.dto.NotificationDraft;

import java.time.LocalDate;
import java.util.List;

public interface NotificationSource {

    List<NotificationDraft> drafts(Long userId, LocalDate today);
}
