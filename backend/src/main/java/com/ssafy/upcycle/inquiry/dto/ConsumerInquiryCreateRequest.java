package com.ssafy.upcycle.inquiry.dto;

import com.ssafy.upcycle.inquiry.entity.InquiryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 문의 등록 요청. {@code POST /api/v1/consumers/me/inquiries}.
 *
 * <p>{@code productOrderId} 는 {@code type}이 {@link InquiryType#ETC}가 아니면 필수다 —
 * 어느 주문에 대한 것인지 짚어야 무엇을 확인해야 할지 알 수 있다. 이 조건은 두 필드에 걸쳐
 * 있어 bean validation 애노테이션으로 표현하지 못하므로 서비스가 확인한다.
 */
public record ConsumerInquiryCreateRequest(
        @NotNull(message = "문의 유형은 필수입니다.") InquiryType type,
        @Positive(message = "문의 대상 주문 id는 양수여야 합니다.") Long productOrderId,
        @NotBlank(message = "문의 내용은 필수입니다.")
        @Size(max = 2000, message = "문의 내용은 2000자를 초과할 수 없습니다.")
        String content) {
}
