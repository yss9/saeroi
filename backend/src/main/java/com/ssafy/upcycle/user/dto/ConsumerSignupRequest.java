package com.ssafy.upcycle.user.dto;

import com.ssafy.upcycle.common.validation.SingleLine;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 개인회원 가입 요청. 사용자 역할은 서버에서 CONSUMER로 고정한다.
 *
 * <p>주소 3분할은 {@code BusinessSignupRequest} 와 같은 이름을 쓴다 — 같은 카카오 우편번호
 * 위젯이 채우는 값이라 화면 쪽 코드가 한 벌이면 된다.
 *
 * <p>다만 <b>사업자와 달리 좌표로 변환하지 않는다</b>. 업체 주소는 지도에 찍히지만 배송지는
 * 택배 송장에 인쇄될 뿐이라 위경도가 필요 없다. 덕분에 개인회원 가입은 카카오 REST API 키
 * 없이도 동작한다.
 */
public record ConsumerSignupRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @SingleLine
        @Size(min = 2, max = 100, message = "이름은 2자 이상 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다.")
        String email,

        @NotBlank(message = "휴대전화 번호는 필수입니다.")
        @Pattern(
                regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$",
                message = "휴대전화 번호 형식이 올바르지 않습니다.")
        String phone,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,255}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
        String password,

        @NotBlank(message = "우편번호는 필수입니다.")
        @Size(max = 20, message = "우편번호는 20자를 초과할 수 없습니다.")
        String zipCode,

        @NotBlank(message = "주소는 필수입니다.")
        @SingleLine
        @Size(max = 255, message = "주소는 255자를 초과할 수 없습니다.")
        String address,

        /** 동·호수. 빈 문자열로 와도 되고, 그때는 저장하지 않는다. */
        @SingleLine
        @Size(max = 255, message = "상세 주소는 255자를 초과할 수 없습니다.")
        String addressDetail) {
}
