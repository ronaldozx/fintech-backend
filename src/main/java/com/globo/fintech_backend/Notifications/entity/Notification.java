package com.globo.fintech_backend.Notifications.entity;

import com.globo.fintech_backend.Auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "dedupe_key"}))
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 40)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 600)
    private String message;

    @Column(length = 100)
    private String link;

    @Column(name = "dedupe_key", nullable = false, length = 200)
    private String dedupeKey;

    private LocalDateTime readAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
