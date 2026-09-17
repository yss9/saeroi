package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.PasswordChangeRequest;
import com.ssafy.upcycle.user.service.PasswordChangeService;
import com.ssafy.upcycle.user.service.RefreshTokenCookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할과 무관한 내 계정 기능. <b>지금은 비밀번호 변경 하나다.</b>
 *
 * <p>소비자 개인정보({@code /api/v1/consumers/me})·사업자 업체정보
 * ({@code /api/v1/companies/me})와 경로를 나눈 이유는, 이름·연락처는 역할마다 의미와 형식이
 * 다르지만 비밀번호는 그렇지 않기 때문이다. 역할별로 한 벌씩 만들면 같은 코드가 둘이 된다.
 *
 * <p>대상 사용자는 JWT {@code sub} 에서만 얻는다 — 경로로 사람을 지정하게 하면 남의
 * 비밀번호를 바꾸는 요청이 만들어진다.
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserAccountController {

    private final PasswordChangeService passwordChangeService;
    private final RefreshTokenCookieWriter refreshTokenCookieWriter;

    /**
     * 비밀번호를 바꾼다. 현재 비밀번호를 함께 받는다.
     *
     * <p><b>성공하면 그 계정의 모든 세션이 끊긴다.</b> 다른 기기는 물론 지금 이 브라우저도
     * 다시 로그인해야 한다 — 계정 탈취가 의심되어 바꾸는 경우 침입자의 세션을 남기지 않기
     * 위해서다.
     *
     * <p>그래서 <b>쿠키도 함께 지운다.</b> 방금 죽은 Refresh Token 을 브라우저에 남겨 두면
     * 다음 재발급 요청이 {@code INVALID_REFRESH_TOKEN} 으로 튕기고, 화면은 그 이유를 설명할
     * 수 없다. 로그아웃과 같은 방식이다.
     *
     * <p>현재 비밀번호가 틀리면 <b>400</b>이다({@code CURRENT_PASSWORD_MISMATCH}). 401 이면
     * 프론트 공통 인터셉터가 세션 만료로 해석해 로그인 화면으로 보낸다.
     *
     * <p>응답 본문이 없다. 바뀐 값을 돌려줄 수 없고, 돌려줘서도 안 된다.
     */
    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request,
            HttpServletResponse response) {
        passwordChangeService.change(userId(jwt), request);
        refreshTokenCookieWriter.clear(response);
        return ApiResponse.success(null);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
