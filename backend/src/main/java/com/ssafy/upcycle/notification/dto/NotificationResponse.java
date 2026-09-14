package com.ssafy.upcycle.notification.dto;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.entity.UserNotification;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        NotificationType type,
        String title,
        String message,
        String targetPath,
        NotificationReferenceType referenceType,
        Long referenceId,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetPath(),
                notification.getReferenceType(),
                notification.getReferenceId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
