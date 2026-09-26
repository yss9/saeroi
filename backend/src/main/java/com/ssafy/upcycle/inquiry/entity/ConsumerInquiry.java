package com.ssafy.upcycle.inquiry.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * CONSUMER 계정 본인의 1:1 문의.
 *
 * <p><b>{@code target_label} 은 등록 시점 스냅샷이다.</b> {@code product_order_id} 로 매번
 * 상품명을 다시 조회하지 않는다 — {@code product_order_item.price} 와 같은 이유로, 나중에
 * 주문이 취소되거나(행이 사라짐) 상품명이 바뀌어도 문의 내역에 적힌 "무엇에 대한 문의였는지"
 * 는 등록 당시 그대로 남아야 한다.
 *
 * <p><b>답변({@code answer})은 이 프로젝트에 아직 관리자/CS 도메인이 없어 API로 채울 방법이
 * 없다.</b> 컬럼은 나중에 그 도메인이 생겼을 때를 위해 미리 둔다 — 값이 없으면 항상
 * "답변 대기" 상태다.
 */
@Getter
@Entity
@Table(
        name = "consumer_inquiry",
        indexes = @Index(name = "idx_consumer_inquiry_user", columnList = "user_id,id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumerInquiry {

    private static final int TARGET_LABEL_MAX_LENGTH = 255;
    private static final int CONTENT_MAX_LENGTH = 2000;
    private static final int ANSWER_MAX_LENGTH = 2000;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "type", nullable = false, length = 30)
    private InquiryType type;

    /** 문의 대상 주문. {@link InquiryType#ETC}는 없어도 된다. */
    @Column(name = "product_order_id")
    private Long productOrderId;

    @Column(name = "target_label", length = TARGET_LABEL_MAX_LENGTH)
    private String targetLabel;

    @Column(name = "content", nullable = false, length = CONTENT_MAX_LENGTH)
    private String content;

    @Column(name = "answer", length = ANSWER_MAX_LENGTH)
    private String answer;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ConsumerInquiry(
            Long userId,
            InquiryType type,
            Long productOrderId,
            String targetLabel,
            String content,
            LocalDateTime createdAt) {
        this.userId = requireNonNull(userId, "user id");
        this.type = requireNonNull(type, "type");
        if (type.requiresOrder() && productOrderId == null) {
            throw new IllegalArgumentException(
                    "Consumer inquiry of type " + type + " requires a product order id");
        }
        this.productOrderId = productOrderId;
        this.targetLabel = requireLength(targetLabel, TARGET_LABEL_MAX_LENGTH, "target label");
        this.content = requireText(content, CONTENT_MAX_LENGTH, "content");
        this.createdAt = requireNonNull(createdAt, "created at");
    }

    public static ConsumerInquiry register(
            Long userId,
            InquiryType type,
            Long productOrderId,
            String targetLabel,
            String content,
            LocalDateTime createdAt) {
        return new ConsumerInquiry(userId, type, productOrderId, targetLabel, content, createdAt);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isAnswered() {
        return answer != null;
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Consumer inquiry " + field + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Consumer inquiry " + field + " must be 1.." + maxLength + " characters");
        }
        return value;
    }

    private static String requireLength(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "Consumer inquiry " + field + " must be 1.." + maxLength + " characters");
        }
        return value;
    }
}
