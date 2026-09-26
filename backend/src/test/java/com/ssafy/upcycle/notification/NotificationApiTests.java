package com.ssafy.upcycle.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.notification.entity.NotificationReferenceType;
import com.ssafy.upcycle.notification.entity.NotificationType;
import com.ssafy.upcycle.notification.entity.UserNotification;
import com.ssafy.upcycle.notification.repository.UserNotificationRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserNotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private Long userId;
    private Long otherUserId;

    @BeforeEach
    void setUp() {
        clean();
        userId = saveUser("notification-brand@test.com", UserRole.BRAND).getId();
        otherUserId = saveUser("notification-processor@test.com", UserRole.PROCESSOR).getId();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void listReturnsOnlyMyNotificationsNewestFirstAndUnreadCount() throws Exception {
        saveNotification(userId, "mine-old", null, LocalDateTime.of(2026, 9, 14, 9, 0));
        saveNotification(userId, "mine-new", LocalDateTime.of(2026, 9, 14, 10, 1),
                LocalDateTime.of(2026, 9, 14, 10, 0));
        saveNotification(otherUserId, "other", null, LocalDateTime.of(2026, 9, 14, 11, 0));

        mockMvc.perform(authenticated(get("/notifications?page=0&size=20"), userId, "BRAND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].message").value("mine-new"))
                .andExpect(jsonPath("$.data.items[0].read").value(true))
                .andExpect(jsonPath("$.data.items[1].message").value("mine-old"))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void readingMyNotificationIsIdempotent() throws Exception {
        UserNotification notification = saveNotification(
                userId, "mine", null, LocalDateTime.of(2026, 9, 14, 10, 0));

        mockMvc.perform(authenticated(
                        patch("/api/v1/notifications/{id}/read", notification.getId()),
                        userId,
                        "BRAND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));
        LocalDateTime firstReadAt = notificationRepository.findById(notification.getId())
                .orElseThrow().getReadAt();

        mockMvc.perform(authenticated(
                        patch("/api/v1/notifications/{id}/read", notification.getId()),
                        userId,
                        "BRAND"))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findById(notification.getId()).orElseThrow().getReadAt())
                .isEqualTo(firstReadAt);
    }

    @Test
    void cannotReadAnotherUsersNotification() throws Exception {
        UserNotification notification = saveNotification(
                otherUserId, "other", null, LocalDateTime.of(2026, 9, 14, 10, 0));

        mockMvc.perform(authenticated(
                        patch("/notifications/{id}/read", notification.getId()), userId, "BRAND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void readAllChangesOnlyMyUnreadNotifications() throws Exception {
        saveNotification(userId, "mine-1", null, LocalDateTime.of(2026, 9, 14, 9, 0));
        saveNotification(userId, "mine-2", null, LocalDateTime.of(2026, 9, 14, 10, 0));
        saveNotification(otherUserId, "other", null, LocalDateTime.of(2026, 9, 14, 11, 0));

        mockMvc.perform(authenticated(patch("/notifications/read-all"), userId, "BRAND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2));

        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(userId)).isZero();
        assertThat(notificationRepository.countByRecipientIdAndReadAtIsNull(otherUserId)).isOne();
    }

    @Test
    void notificationApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/notifications")).andExpect(status().isUnauthorized());
    }

    private User saveUser(String email, UserRole role) {
        return userRepository.save(User.register(email, "{noop}x", role.name(), "010-0000-0000", role));
    }

    private UserNotification saveNotification(
            Long recipientId, String message, LocalDateTime readAt, LocalDateTime createdAt) {
        UserNotification notification = UserNotification.create(
                recipientId,
                NotificationType.PROCESSING_REQUEST_RECEIVED,
                "알림",
                message,
                "/processor/jobs",
                NotificationReferenceType.PROCESSING_REQUEST,
                recipientId,
                message + ":" + recipientId,
                createdAt);
        if (readAt != null) {
            notification.markRead(readAt);
        }
        return notificationRepository.save(notification);
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request, Long id, String role) {
        return request.with(jwt().jwt(builder -> builder.subject(id.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private void clean() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();
    }
}
