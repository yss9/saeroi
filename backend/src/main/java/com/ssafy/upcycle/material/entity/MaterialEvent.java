package com.ssafy.upcycle.material.entity;

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

/**
 * Material 하나에 발생한 불변 사건.
 *
 * <p>현재 상태의 정본은 각 도메인 테이블에 있다. 이 엔티티는 시간순 조회와 감사를 위한
 * append-only projection이며 변경 메서드와 삭제 API를 제공하지 않는다.
 */
@Getter
@Entity
@Table(
        name = "material_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_material_event_idempotency",
                columnNames = "idempotency_key"),
        indexes = {
            @Index(
                    name = "idx_material_event_timeline",
                    columnList = "material_id,occurred_at,id"),
            @Index(
                    name = "idx_material_event_reference",
                    columnList = "reference_type,reference_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialEvent {

    private static final int DETAIL_MAX_LENGTH = 500;
    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 60)
    private MaterialEventType eventType;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_type", length = 40)
    private MaterialEventReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "related_material_id")
    private Long relatedMaterialId;

    /** 사건이 다룬 수량. 단위는 항상 g이며 수량 개념이 없는 사건은 null이다. */
    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "detail", length = DETAIL_MAX_LENGTH)
    private String detail;

    @Column(name = "idempotency_key", nullable = false, length = IDEMPOTENCY_KEY_MAX_LENGTH)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    public static MaterialEvent record(
            Long materialId,
            MaterialEventType eventType,
            Long actorId,
            MaterialEventReferenceType referenceType,
            Long referenceId,
            Long relatedMaterialId,
            Long quantity,
            String detail,
            String idempotencyKey,
            LocalDateTime occurredAt) {
        MaterialEvent event = new MaterialEvent();
        event.materialId = requirePositive(materialId, "material id");
        event.eventType = requireNonNull(eventType, "event type");
        event.actorId = optionalPositive(actorId, "actor id");
        requireReferencePair(referenceType, referenceId);
        event.referenceType = referenceType;
        event.referenceId = referenceId;
        event.relatedMaterialId = optionalPositive(relatedMaterialId, "related material id");
        if (quantity != null && quantity < 0) {
            throw new IllegalArgumentException("Material event quantity must not be negative");
        }
        event.quantity = quantity;
        event.detail = optionalText(detail, DETAIL_MAX_LENGTH, "detail");
        event.idempotencyKey = requireText(
                idempotencyKey, IDEMPOTENCY_KEY_MAX_LENGTH, "idempotency key");
        event.occurredAt = requireNonNull(occurredAt, "occurred at");
        return event;
    }

    private static void requireReferencePair(
            MaterialEventReferenceType referenceType, Long referenceId) {
        if ((referenceType == null) != (referenceId == null)) {
            throw new IllegalArgumentException(
                    "Material event reference type and id must be supplied together");
        }
        optionalPositive(referenceId, "reference id");
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Material event " + field + " must be positive");
        }
        return value;
    }

    private static Long optionalPositive(Long value, String field) {
        return value == null ? null : requirePositive(value, field);
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Material event " + field + " must be 1.." + maxLength + " characters");
        }
        return value;
    }

    private static String optionalText(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireText(value, maxLength, field);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Material event " + field + " must not be null");
        }
        return value;
    }
}
