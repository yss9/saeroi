package com.ssafy.upcycle.notification.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.notification.NotificationErrorCode;
import com.ssafy.upcycle.notification.dto.NotificationPageResponse;
import com.ssafy.upcycle.notification.dto.NotificationReadAllResponse;
import com.ssafy.upcycle.notification.dto.NotificationResponse;
import com.ssafy.upcycle.notification.entity.UserNotification;
import com.ssafy.upcycle.notification.repository.UserNotificationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final UserNotificationRepository notificationRepository;
    private final Clock clock;

    @Transactional
    public UserNotification notify(NotificationCommand command) {
        if (command.recipientId() == null) {
            return null;
        }
        return notificationRepository.findByIdempotencyKey(command.idempotencyKey())
                .orElseGet(() -> notificationRepository.save(UserNotification.create(
                        command.recipientId(),
                        command.type(),
                        command.title(),
                        command.message(),
                        command.targetPath(),
                        command.referenceType(),
                        command.referenceId(),
                        command.idempotencyKey(),
                        LocalDateTime.now(clock))));
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(Long recipientId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        Page<UserNotification> notifications = notificationRepository
                .findAllByRecipientIdOrderByCreatedAtDescIdDesc(
                        recipientId, PageRequest.of(safePage, safeSize));
        return new NotificationPageResponse(
                notifications.getContent().stream().map(NotificationResponse::from).toList(),
                safePage,
                safeSize,
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId));
    }

    public NotificationPageResponse list(Long recipientId) {
        return list(recipientId, 0, DEFAULT_SIZE);
    }

    @Transactional
    public NotificationResponse read(Long recipientId, Long notificationId) {
        UserNotification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new BusinessException(
                        NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead(LocalDateTime.now(clock));
        return NotificationResponse.from(notification);
    }

    @Transactional
    public NotificationReadAllResponse readAll(Long recipientId) {
        int updated = notificationRepository.markAllRead(recipientId, LocalDateTime.now(clock));
        return new NotificationReadAllResponse(updated);
    }
}
