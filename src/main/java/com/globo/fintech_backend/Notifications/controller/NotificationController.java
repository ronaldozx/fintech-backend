package com.globo.fintech_backend.Notifications.controller;

import com.globo.fintech_backend.Notifications.dto.NotificationsDTO;
import com.globo.fintech_backend.Notifications.service.NotificationService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<NotificationsDTO> list() {
        return ResponseEntity.ok(service.list(SecurityUtils.getLoggedUserId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<NotificationsDTO> refresh() {
        Long userId = SecurityUtils.getLoggedUserId();
        service.refresh(userId);
        return ResponseEntity.ok(service.list(userId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<NotificationsDTO> readAll() {
        Long userId = SecurityUtils.getLoggedUserId();
        service.markAllRead(userId);
        return ResponseEntity.ok(service.list(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationsDTO> read(@PathVariable Long id) {
        Long userId = SecurityUtils.getLoggedUserId();
        service.markRead(userId, id);
        return ResponseEntity.ok(service.list(userId));
    }
}
