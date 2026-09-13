package com.ssafy.upcycle.user.dto;

/** 이메일 로그인 성공 결과. 지갑 로그인과 동일한 응답 형태를 사용한다. */
public record EmailLoginResponse(
        String accessToken,
        String refreshToken,
        UserSummaryResponse user) {
}
