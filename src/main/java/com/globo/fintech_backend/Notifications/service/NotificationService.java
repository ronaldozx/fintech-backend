package com.globo.fintech_backend.Notifications.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Notifications.dto.NotificationDTO;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.dto.NotificationsDTO;
import com.globo.fintech_backend.Notifications.entity.Notification;
import com.globo.fintech_backend.Notifications.repository.NotificationRepository;
import com.globo.fintech_backend.Notifications.source.NotificationSource;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    static final Duration MIN_REFRESH_INTERVAL = Duration.ofSeconds(60);
    static final int RETENTION_DAYS = 90;
    static final int MAX_KEY_LENGTH = 200;

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final List<NotificationSource> sources;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Instant> lastRefresh = new ConcurrentHashMap<>();

    @Autowired
    public NotificationService(NotificationRepository repository,
                               UserRepository userRepository,
                               List<NotificationSource> sources) {
        this(repository, userRepository, sources, Clock.systemDefaultZone());
    }

    NotificationService(NotificationRepository repository,
                        UserRepository userRepository,
                        List<NotificationSource> sources,
                        Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.sources = sources;
        this.clock = clock;
    }

    public int refresh(Long userId) {
        synchronized (userLocks.computeIfAbsent(userId, id -> new Object())) {
            Instant now = clock.instant();
            Instant previous = lastRefresh.get(userId);
            if (previous != null && Duration.between(previous, now).compareTo(MIN_REFRESH_INTERVAL) < 0) {
                return 0;
            }
            lastRefresh.put(userId, now);

            Map<String, NotificationDraft> drafts = collect(userId, LocalDate.now(clock));
            int created = store(userId, drafts);

            repository.deleteOlderThan(userId, LocalDateTime.now(clock).minusDays(RETENTION_DAYS));
            return created;
        }
    }

    @Transactional(readOnly = true)
    public NotificationsDTO list(Long userId) {
        List<NotificationDTO> items = repository.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationService::toDto)
                .toList();

        return new NotificationsDTO(repository.countByUserIdAndReadAtIsNull(userId), items);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Aviso não encontrado"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now(clock));
            repository.save(notification);
        }
    }

    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, LocalDateTime.now(clock));
    }

    private Map<String, NotificationDraft> collect(Long userId, LocalDate today) {
        Map<String, NotificationDraft> byKey = new LinkedHashMap<>();

        for (NotificationSource source : sources) {
            try {
                for (NotificationDraft draft : source.drafts(userId, today)) {
                    byKey.putIfAbsent(limit(draft.dedupeKey()), draft);
                }
            } catch (RuntimeException e) {
                log.warn("Notification source {} failed for user {}: {}", source.getClass().getSimpleName(), userId, e.getMessage());
            }
        }

        return byKey;
    }

    private int store(Long userId, Map<String, NotificationDraft> drafts) {
        if (drafts.isEmpty()) {
            return 0;
        }

        Set<String> existing = repository.findExistingKeys(userId, drafts.keySet());
        List<Notification> fresh = new ArrayList<>();

        User user = null;
        for (Map.Entry<String, NotificationDraft> entry : drafts.entrySet()) {
            if (existing.contains(entry.getKey())) {
                continue;
            }
            if (user == null) {
                user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
            }
            fresh.add(toEntity(user, entry.getKey(), entry.getValue()));
        }

        repository.saveAll(fresh);
        return fresh.size();
    }

    private static Notification toEntity(User user, String key, NotificationDraft draft) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(draft.type());
        notification.setSeverity(draft.severity());
        notification.setTitle(draft.title().length() > 200 ? draft.title().substring(0, 200) : draft.title());
        notification.setMessage(draft.message().length() > 600 ? draft.message().substring(0, 600) : draft.message());
        notification.setLink(draft.link());
        notification.setDedupeKey(key);
        return notification;
    }

    private static NotificationDTO toDto(Notification notification) {
        return new NotificationDTO(
                notification.getId(),
                notification.getType(),
                notification.getSeverity(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getLink(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }

    private static String limit(String key) {
        return key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key;
    }
}
