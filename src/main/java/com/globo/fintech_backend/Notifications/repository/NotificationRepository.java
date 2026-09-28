package com.globo.fintech_backend.Notifications.repository;

import com.globo.fintech_backend.Notifications.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop50ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT n.dedupeKey FROM Notification n WHERE n.user.id = :userId AND n.dedupeKey IN :keys")
    Set<String> findExistingKeys(@Param("userId") Long userId, @Param("keys") Collection<String> keys);

    @Transactional
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.user.id = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.createdAt < :before")
    int deleteOlderThan(@Param("userId") Long userId, @Param("before") LocalDateTime before);
}
