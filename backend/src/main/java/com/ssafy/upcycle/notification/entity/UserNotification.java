package com.ssafy.upcycle.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(
        name = "user_notification",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_notification_idempotency",
                columnNames = "idempotency_key"),
        indexes = {
            @Index(
                    name = "idx_user_notification_recipient_created",
                    columnList = "recipient_id,created_at,id"),
            @Index(
                    name = "idx_user_notification_recipient_read",
                    columnList = "recipient_id,read_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotification {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int MESSAGE_MAX_LENGTH = 500;
    private static final int TARGET_PATH_MAX_LENGTH = 500;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 60)
    private NotificationType type;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(nullable = false, length = MESSAGE_MAX_LENGTH)
    private String message;

    @Column(name = "target_path", nullable = false, length = TARGET_PATH_MAX_LENGTH)
    private String targetPath;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_type", nullable = false, length = 40)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Column(name = "idempotency_key", nullable = false, length = IDEMPOTENCY_KEY_MAX_LENGTH)
    private String idempotencyKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static UserNotification create(
            Long recipientId,
            NotificationType type,
            String title,
            String message,
            String targetPath,
            NotificationReferenceType referenceType,
            Long referenceId,
            String idempotencyKey,
            LocalDateTime createdAt) {
        UserNotification notification = new UserNotification();
        notification.recipientId = requirePositive(recipientId, "recipient id");
        notification.type = requireNonNull(type, "type");
        notification.title = requireText(title, TITLE_MAX_LENGTH, "title");
        notification.message = requireText(message, MESSAGE_MAX_LENGTH, "message");
        notification.targetPath = requireInternalPath(targetPath);
        notification.referenceType = requireNonNull(referenceType, "reference type");
        notification.referenceId = requirePositive(referenceId, "reference id");
        notification.idempotencyKey =
                requireText(idempotencyKey, IDEMPOTENCY_KEY_MAX_LENGTH, "idempotency key");
        notification.createdAt = requireNonNull(createdAt, "created at");
        return notification;
    }

    public void markRead(LocalDateTime readAt) {
        if (this.readAt == null) {
            this.readAt = requireNonNull(readAt, "read at");
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    private static String requireInternalPath(String value) {
        String path = requireText(value, TARGET_PATH_MAX_LENGTH, "target path");
        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException("Notification target path must be an internal path");
        }
        return path;
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Notification " + field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Notification " + field + " must be 1.." + maxLength + " characters");
        }
        return value;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Notification " + field + " must not be null");
        }
        return value;
    }
}
