package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.common.validation.SingleLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 배송지 등록·수정 요청. {@code POST/PUT /api/v1/consumers/me/addresses} 가 함께 쓴다.
 *
 * <p><b>{@code isDefault} 가 없다.</b> 기본 배송지 지정은
 * {@code PATCH /{addressId}/default} 하나로만 한다 — 같은 일을 두 경로가 하면 "수정하면서
 * 기본도 바꿨는데 하나만 반영됐다" 같은 어긋남이 생긴다. 다만 주소록이 비어 있을 때
 * 등록하는 첫 배송지는 서버가 자동으로 기본이 되게 한다(선택지가 하나뿐이라 물을 필요가
 * 없다).
 */
public record ConsumerAddressRequest(
        @NotBlank(message = "배송지 이름은 필수입니다.")
        @SingleLine
        @Size(max = 100, message = "배송지 이름은 100자를 초과할 수 없습니다.")
        String alias,

        @NotBlank(message = "수령인 이름은 필수입니다.")
        @SingleLine
        @Size(min = 2, max = 100, message = "수령인 이름은 2자 이상 100자 이하여야 합니다.")
        String receiverName,

        @NotBlank(message = "수령인 연락처는 필수입니다.")
        @Pattern(
                regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                message = "수령인 연락처 형식이 올바르지 않습니다.")
        String receiverPhone,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 20, message = "우편번호는 20자를 초과할 수 없습니다.")
        String zipCode,

        @NotBlank(message = "주소는 필수입니다.")
        @SingleLine
        @Size(max = 255, message = "주소는 255자를 초과할 수 없습니다.")
        String address,

        @SingleLine
        @Size(max = 255, message = "상세 주소는 255자를 초과할 수 없습니다.")
        String addressDetail) {
}
