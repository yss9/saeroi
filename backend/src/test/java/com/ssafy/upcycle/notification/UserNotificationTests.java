package com.ssafy.upcycle.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.entity.UserNotification;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserNotificationTests {

    private static final LocalDateTime CREATED_AT =
            LocalDateTime.of(2026, 9, 14, 10, 0);

    @Test
    void notificationValidatesAndMarksReadIdempotently() {
        UserNotification notification = UserNotification.create(
                7L,
                NotificationType.PROCESSING_REQUEST_RECEIVED,
                "새 가공 요청이 도착했어요",
                "300g 가공 요청을 확인해 주세요.",
                "/processor/jobs",
                NotificationReferenceType.PROCESSING_REQUEST,
                41L,
                "PROCESSING_REQUEST_RECEIVED:41:7",
                CREATED_AT);

        notification.markRead(CREATED_AT.plusMinutes(1));
        notification.markRead(CREATED_AT.plusMinutes(2));

        assertThat(notification.getReadAt()).isEqualTo(CREATED_AT.plusMinutes(1));
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void externalTargetPathIsRejected() {
        assertThatThrownBy(() -> UserNotification.create(
                7L,
                NotificationType.PROCESSING_REQUEST_RECEIVED,
                "title",
                "message",
                "https://outside.example",
                NotificationReferenceType.PROCESSING_REQUEST,
                41L,
                "key",
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidRequiredValuesAreRejected() {
        assertThatThrownBy(() -> UserNotification.create(
                0L,
                NotificationType.PROCESSING_REQUEST_RECEIVED,
                "",
                "message",
                "/processor/jobs",
                NotificationReferenceType.PROCESSING_REQUEST,
                41L,
                "key",
                CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
