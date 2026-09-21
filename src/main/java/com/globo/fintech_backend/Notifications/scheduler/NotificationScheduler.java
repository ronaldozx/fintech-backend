package com.globo.fintech_backend.Notifications.scheduler;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final UserRepository userRepository;
    private final NotificationService service;

    public NotificationScheduler(UserRepository userRepository, NotificationService service) {
        this.userRepository = userRepository;
        this.service = service;
    }

    @Scheduled(cron = "${notifications.cron:0 30 */6 * * *}")
    public void refreshAll() {
        for (User user : userRepository.findAll()) {
            try {
                int created = service.refresh(user.getId());
                log.info("Created {} notifications for user {}", created, user.getId());
            } catch (RuntimeException e) {
                log.warn("Notification refresh failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
    }
}
