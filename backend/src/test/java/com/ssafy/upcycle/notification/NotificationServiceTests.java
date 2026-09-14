package com.ssafy.upcycle.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.repository.UserNotificationRepository;
import com.ssafy.upcycle.notification.service.NotificationCommand;
import com.ssafy.upcycle.notification.service.NotificationService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificationServiceTests {

    @Autowired private NotificationService notificationService;
    @Autowired private UserNotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void clean() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void notifyIsIdempotentByBusinessEventKey() {
        User recipient = userRepository.save(User.register(
                "notification-idempotent@test.com",
                "{noop}x",
                "processor",
                "010-0000-0000",
                UserRole.PROCESSOR));
        NotificationCommand command = new NotificationCommand(
                recipient.getId(),
                NotificationType.PROCESSING_REQUEST_RECEIVED,
                "새 가공 요청",
                "가공 요청을 확인해 주세요.",
                "/processor/jobs",
                NotificationReferenceType.PROCESSING_REQUEST,
                41L,
                "PROCESSING_REQUEST_RECEIVED:41:" + recipient.getId());

        Long firstId = notificationService.notify(command).getId();
        Long secondId = notificationService.notify(command).getId();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(notificationRepository.count()).isOne();
    }
}
