package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.common.validation.SingleLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 소비자 본인 개인정보 수정 요청.
 *
 * <p>검증 규칙은 {@link ConsumerSignupRequest} 와 같다. 가입에서 받은 값을 수정에서 거부하거나
 * 그 반대가 되면, 사용자는 자기 계정이 왜 그 상태인지 설명할 수 없다.
 *
 * <p><b>이메일은 받지 않는다.</b> 로그인 식별자이고 바꾸려면 새 주소의 소유 확인이 필요해,
 * 이름·연락처와 같은 요청에 섞을 수 없다.
 *
 * <p><b>배송지도 받지 않는다.</b> 계정 연락처({@code users.phone})와 배송지의 수령인
 * 연락처({@code consumer_address.receiver_phone})는 서로 다른 값이다 — 가족·회사 주소로
 * 받는 경우 받는 사람이 본인이 아니다. 배송지는 {@code /api/v1/consumers/me/addresses} 가
 * 맡는다.
 */
public record ConsumerProfileUpdateRequest(

        @NotBlank(message = "이름은 필수입니다.")
        @SingleLine
        @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "휴대전화 번호는 필수입니다.")
        @Pattern(
                regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                message = "휴대전화 번호 형식이 올바르지 않습니다.")
        String phone) {
}
