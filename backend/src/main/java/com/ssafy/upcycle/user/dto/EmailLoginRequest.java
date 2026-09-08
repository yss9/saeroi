package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이메일 로그인 요청. */
public record EmailLoginRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자를 초과할 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(max = 255, message = "비밀번호는 255자를 초과할 수 없습니다.")
        String password) {
}
