package com.ssafy.upcycle.notification.repository;

import com.ssafy.upcycle.notification.entity.UserNotification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    Page<UserNotification> findAllByRecipientIdOrderByCreatedAtDescIdDesc(
            Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    Optional<UserNotification> findByIdAndRecipientId(Long id, Long recipientId);

    Optional<UserNotification> findByIdempotencyKey(String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update UserNotification n set n.readAt = :readAt "
            + "where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(
            @Param("recipientId") Long recipientId,
            @Param("readAt") LocalDateTime readAt);
}
