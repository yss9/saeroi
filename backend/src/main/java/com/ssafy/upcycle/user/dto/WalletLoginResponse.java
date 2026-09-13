package com.ssafy.upcycle.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 서명 검증 성공 결과. Frontend 의 {@code LoginResponse} 와 같은 모양이라
 * 이메일 로그인과 지갑 로그인이 같은 경로로 세션을 세운다.
 */
public record WalletLoginResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        UserSummaryResponse user) {
}
