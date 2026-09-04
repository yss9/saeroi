package com.ssafy.upcycle.user.controller;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.user.dto.WalletNonceResponse;
import com.ssafy.upcycle.user.dto.WalletRegisterNonceRequest;
import com.ssafy.upcycle.user.dto.WalletRegisterRequest;
import com.ssafy.upcycle.user.dto.WalletResponse;
import com.ssafy.upcycle.user.service.WalletRegistrationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지갑 등록·조회·비활성화. <b>인증이 필요하다.</b>
 *
 * <p>경로가 {@code /api/v1/wallets} 인 것이 의도적이다. {@code /api/v1/auth/**} 는
 * SecurityConfig 가 인증 없이 열어 둔 프리픽스이므로, 등록 경로를 그 아래 두면
 * <b>아무나 남의 계정에 지갑을 붙일 수 있게 된다.</b> 로그인
 * ({@link WalletAuthController})과 경로를 나눈 이유가 이것이다.
 *
 * <p>사용자는 JWT 의 {@code sub}(user id)에서 얻는다 — 요청 바디로 받으면 남의 계정을 지정할 수 있다.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletRegistrationService walletRegistrationService;

    /** 등록용 nonce 발급. 로그인용({@code /auth/wallet/nonce})과 달리 인증이 필요하다. */
    @PostMapping("/nonce")
    public ApiResponse<WalletNonceResponse> issueNonce(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WalletRegisterNonceRequest request) {
        return ApiResponse.success(walletRegistrationService.issueNonce(userId(jwt), request));
    }

    /** 서명 검증 후 등록. 이미 활성 지갑이 있으면 그 지갑이 비활성으로 밀린다. */
    @PostMapping
    public ApiResponse<WalletResponse> register(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody WalletRegisterRequest request) {
        return ApiResponse.success(walletRegistrationService.register(userId(jwt), request));
    }

    /** 내 지갑 목록. 활성·비활성 전부 최신순으로 내려간다. */
    @GetMapping("/me")
    public ApiResponse<List<WalletResponse>> findMyWallets(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(walletRegistrationService.findMyWallets(userId(jwt)));
    }

    /** 지갑 비활성화. 행은 남는다 — 서명 시점 주소의 소유자를 역추적할 수 있어야 한다. */
    @DeleteMapping("/{walletId}")
    public ApiResponse<WalletResponse> deactivate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable Long walletId) {
        return ApiResponse.success(walletRegistrationService.deactivate(userId(jwt), walletId));
    }

    /**
     * JWT {@code sub} 는 {@code JwtTokenService} 가 {@code String.valueOf(user.getId())} 로 넣는다.
     * 형식이 깨진 토큰은 여기 오기 전에 {@code JwtDecoder} 가 거부하므로 파싱 실패는 예상하지 않는다.
     */
    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
