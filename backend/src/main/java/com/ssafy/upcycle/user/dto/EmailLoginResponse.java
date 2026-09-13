package com.ssafy.upcycle.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 이메일 로그인 성공 결과. 지갑 로그인과 동일한 응답 형태를 사용한다. */
public record EmailLoginResponse(
        String accessToken,
        @JsonIgnore String refreshToken,
        UserSummaryResponse user) {
}
