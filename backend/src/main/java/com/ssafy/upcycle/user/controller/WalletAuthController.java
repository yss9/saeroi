package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.WalletLoginResponse;
import com.ssafy.upcycle.user.dto.WalletNonceRequest;
import com.ssafy.upcycle.user.dto.WalletNonceResponse;
import com.ssafy.upcycle.user.dto.WalletVerifyRequest;
import com.ssafy.upcycle.user.service.RefreshTokenCookieWriter;
import com.ssafy.upcycle.user.service.WalletAuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지갑 서명 로그인 엔드포인트.
 *
 * <p>경로가 {@code /api/v1/auth/**} 아래인 것은 우연이 아니다. SecurityConfig 가 그 프리픽스만
 * 인증 없이 열어 두었으므로, 다른 경로로 옮기면 컨트롤러에 닿기도 전에 401 이 된다.
 * {@code WalletAuthControllerTests} 가 인증 헤더 없이 호출해 이 성질을 고정한다.
 *
 * <p>{@code ApiResponse} 를 직접 돌려준다 ({@code HealthController} 와 같은 방식).
 * 실패는 {@code BusinessException} 으로 던지고 {@code GlobalExceptionHandler} 가 상태코드를 정한다.
 */
@RestController
@RequestMapping("/api/v1/auth/wallet")
@RequiredArgsConstructor
public class WalletAuthController {

    private final WalletAuthService walletAuthService;
    private final RefreshTokenCookieWriter refreshTokenCookieWriter;

    @PostMapping("/nonce")
    public ApiResponse<WalletNonceResponse> issueNonce(@Valid @RequestBody WalletNonceRequest request) {
        return ApiResponse.success(walletAuthService.issueNonce(request));
    }

    @PostMapping("/verify")
    public ApiResponse<WalletLoginResponse> verify(
            @Valid @RequestBody WalletVerifyRequest request,
            HttpServletResponse response) {
        WalletLoginResponse result = walletAuthService.verify(request);
        refreshTokenCookieWriter.write(response, result.refreshToken());
        return ApiResponse.success(result);
    }
}
