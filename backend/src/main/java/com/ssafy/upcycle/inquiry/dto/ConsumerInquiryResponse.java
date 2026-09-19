package com.ssafy.upcycle.inquiry.dto;

import com.ssafy.upcycle.inquiry.entity.ConsumerInquiry;
import com.ssafy.upcycle.inquiry.entity.InquiryType;
import java.time.LocalDateTime;

/**
 * 문의 한 건. {@code answered} 는 {@code answer} 존재 여부로 계산한다 — 별도 상태 컬럼을
 * 두면 답변이 달릴 때 그 컬럼도 같이 바꿔야 하고, 둘이 어긋나는 경우가 생긴다.
 */
public record ConsumerInquiryResponse(
        Long inquiryId,
        InquiryType type,
        Long productOrderId,
        String targetLabel,
        String content,
        boolean answered,
        String answer,
        LocalDateTime answeredAt,
        LocalDateTime createdAt) {

    public static ConsumerInquiryResponse from(ConsumerInquiry inquiry) {
        return new ConsumerInquiryResponse(
                inquiry.getId(),
                inquiry.getType(),
                inquiry.getProductOrderId(),
                inquiry.getTargetLabel(),
                inquiry.getContent(),
                inquiry.isAnswered(),
                inquiry.getAnswer(),
                inquiry.getAnsweredAt(),
                inquiry.getCreatedAt());
    }
}
