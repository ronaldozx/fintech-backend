package com.globo.fintech_backend.Notifications.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.dto.NotificationsDTO;
import com.globo.fintech_backend.Notifications.entity.Notification;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.Notifications.repository.NotificationRepository;
import com.globo.fintech_backend.Notifications.source.NotificationSource;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private NotificationRepository repository;

    @Mock
    private UserRepository userRepository;

    private MutableClock clock;

    private static NotificationDraft draft(String key) {
        return new NotificationDraft("BUDGET", NotificationSeverity.WARNING, "Título " + key, "Mensagem " + key, "/orcamentos", key);
    }

    private NotificationService service(NotificationSource... sources) {
        return new NotificationService(repository, userRepository, List.of(sources), clock);
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-21T12:00:00Z"));
    }

    @Test
    void storesOnlyDraftsWhoseKeyIsNew() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(repository.findExistingKeys(eq(USER_ID), anyCollection())).thenReturn(Set.of("a"));

        int created = service((userId, today) -> List.of(draft("a"), draft("b"))).refresh(USER_ID);

        assertEquals(1, created);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertEquals("b", saved.getValue().get(0).getDedupeKey());
        assertEquals("Título b", saved.getValue().get(0).getTitle());
    }

    @Test
    void repeatedKeysInTheSameRunAreStoredOnce() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(repository.findExistingKeys(eq(USER_ID), anyCollection())).thenReturn(Set.of());

        int created = service((userId, today) -> List.of(draft("a"), draft("a"))).refresh(USER_ID);

        assertEquals(1, created);
    }

    @Test
    void oneFailingSourceDoesNotStopTheOthers() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(repository.findExistingKeys(eq(USER_ID), anyCollection())).thenReturn(Set.of());
        NotificationSource broken = (userId, today) -> {
            throw new IllegalStateException("Pluggy fora do ar");
        };

        int created = service(broken, (userId, today) -> List.of(draft("ok"))).refresh(USER_ID);

        assertEquals(1, created);
    }

    @Test
    void doesNotRefreshTwiceWithinTheMinimumInterval() {
        List<Integer> calls = new ArrayList<>();
        NotificationSource counting = (userId, today) -> {
            calls.add(1);
            return List.of();
        };
        NotificationService service = service(counting);

        service.refresh(USER_ID);
        assertEquals(0, service.refresh(USER_ID));
        assertEquals(1, calls.size());

        clock.advance(NotificationService.MIN_REFRESH_INTERVAL.plusSeconds(1));
        service.refresh(USER_ID);
        assertEquals(2, calls.size());
    }

    @Test
    void refreshesEachUserIndependently() {
        List<Long> users = new ArrayList<>();
        NotificationSource recording = (userId, today) -> {
            users.add(userId);
            return List.of();
        };
        NotificationService service = service(recording);

        service.refresh(1L);
        service.refresh(2L);

        assertEquals(List.of(1L, 2L), users);
    }

    @Test
    void savesNothingWhenThereIsNothingToSay() {
        service((userId, today) -> List.of()).refresh(USER_ID);

        verify(repository, never()).saveAll(any());
    }

    @Test
    void oldNotificationsArePurgedAfterTheRetentionPeriod() {
        service((userId, today) -> List.of()).refresh(USER_ID);

        verify(repository).deleteOlderThan(eq(USER_ID), eq(java.time.LocalDateTime.of(2026, 9, 21, 12, 0).minusDays(NotificationService.RETENTION_DAYS)));
    }

    @Test
    void veryLongKeysAreCutToTheColumnSize() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(repository.findExistingKeys(eq(USER_ID), anyCollection())).thenReturn(Set.of());

        service((userId, today) -> List.of(draft("k".repeat(500)))).refresh(USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertEquals(NotificationService.MAX_KEY_LENGTH, saved.getValue().get(0).getDedupeKey().length());
    }

    @Test
    void listsWithTheUnreadCount() {
        Notification unread = new Notification();
        unread.setId(1L);
        unread.setSeverity(NotificationSeverity.CRITICAL);
        unread.setTitle("A");
        unread.setMessage("m");
        Notification read = new Notification();
        read.setId(2L);
        read.setSeverity(NotificationSeverity.INFO);
        read.setTitle("B");
        read.setMessage("m");
        read.setReadAt(java.time.LocalDateTime.now());
        when(repository.findTop50ByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(unread, read));
        when(repository.countByUserIdAndReadAtIsNull(USER_ID)).thenReturn(1L);

        NotificationsDTO result = service().list(USER_ID);

        assertEquals(1, result.unread());
        assertEquals(2, result.items().size());
        assertTrue(!result.items().get(0).read());
        assertTrue(result.items().get(1).read());
    }

    @Test
    void marksOneAsReadOnlyOnceAndOnlyForTheOwner() {
        Notification notification = new Notification();
        notification.setId(9L);
        when(repository.findByIdAndUserId(9L, USER_ID)).thenReturn(Optional.of(notification));

        NotificationService service = service();
        service.markRead(USER_ID, 9L);
        assertNotNull(notification.getReadAt());
        java.time.LocalDateTime firstRead = notification.getReadAt();

        clock.advance(java.time.Duration.ofMinutes(5));
        service.markRead(USER_ID, 9L);
        assertEquals(firstRead, notification.getReadAt());

        when(repository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.markRead(USER_ID, 99L));
    }

    @Test
    void markAllReadDelegatesToTheRepository() {
        when(repository.markAllRead(eq(USER_ID), any())).thenReturn(3);

        assertEquals(3, service().markAllRead(USER_ID));
    }

    @Test
    void aNewNotificationStartsUnread() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        when(repository.findExistingKeys(eq(USER_ID), anyCollection())).thenReturn(Set.of());

        service((userId, today) -> List.of(draft("a"))).refresh(USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertNull(saved.getValue().get(0).getReadAt());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(java.time.Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
