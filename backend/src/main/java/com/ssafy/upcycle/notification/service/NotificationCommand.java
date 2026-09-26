package com.ssafy.upcycle.notification.service;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;

public record NotificationCommand(
        Long recipientId,
        NotificationType type,
        String title,
        String message,
        String targetPath,
        NotificationReferenceType referenceType,
        Long referenceId,
        String idempotencyKey) {}
