package com.ssafy.upcycle.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 변경 요청.
 *
 * <p><b>현재 비밀번호를 함께 받는다.</b> 액세스 토큰만으로 바꾸게 하면, 잠깐 자리를 비운
 * 사이 열린 화면을 만진 사람이 계정을 통째로 가져갈 수 있다.
 *
 * <p>새 비밀번호 규칙은 {@link ConsumerSignupRequest} 와 같다. 변경이 더 느슨하면 가입 규칙이
 * 무의미해지고, 더 엄격하면 기존 사용자가 자기 비밀번호를 다시 입력조차 하지 못한다.
 *
 * <p>확인용 입력({@code newPasswordConfirm})은 받지 않는다. 두 칸이 같은지는 화면에서 즉시
 * 알려 줘야 하는 것이고, 서버까지 왕복해서 답할 문제가 아니다.
 */
public record PasswordChangeRequest(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d\\s]).{8,255}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다.")
        String newPassword) {
}
