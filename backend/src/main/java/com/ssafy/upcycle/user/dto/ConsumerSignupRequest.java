package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 개인회원 가입 요청. 사용자 역할은 서버에서 CONSUMER로 고정한다. */
public record ConsumerSignupRequest(
        @NotBlank(message = "이름은 필수입니다.")
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
        String password) {
}
