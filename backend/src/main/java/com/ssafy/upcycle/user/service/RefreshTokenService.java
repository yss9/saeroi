package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.security.jwt.AccessToken;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.user.TokenRefreshErrorCode;
import com.ssafy.upcycle.user.config.RefreshTokenProperties;
import com.ssafy.upcycle.user.entity.RefreshToken;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.repository.RefreshTokenRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenCodec codec;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    /** 로그인에 성공한 사용자에게 Access Token과 Refresh Token을 함께 발급한다. */
    @Transactional
    public IssuedTokenPair issueFor(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Persisted user is required to issue tokens");
        }
        return issue(user, LocalDateTime.now(clock));
    }

    /** 기존 Refresh Token을 한 번만 소비하고 두 토큰을 모두 회전한다. */
    @Transactional
    public IssuedTokenPair refresh(String rawToken) {
        String tokenHash;
        try {
            tokenHash = codec.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN));
        LocalDateTime now = LocalDateTime.now(clock);

        if (stored.isExpiredAt(now)) {
            throw new BusinessException(TokenRefreshErrorCode.REFRESH_TOKEN_EXPIRED);
        }
        if (stored.isRevoked() || refreshTokenRepository.revokeIfActive(stored.getId(), now) != 1) {
            throw new BusinessException(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 조건부 UPDATE가 영속성 컨텍스트를 비우므로 사용자 조회는 반드시 그 뒤에 한다.
        User user = userRepository
                .findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(TokenRefreshErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(TokenRefreshErrorCode.USER_SUSPENDED);
        }
        return issue(user, now);
    }

    /** 현재 브라우저 세션의 Refresh Token을 무효화한다. 로그아웃은 반복 호출해도 성공한다. */
    @Transactional
    public void revoke(String rawToken) {
        String tokenHash;
        try {
            tokenHash = codec.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            return;
        }

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token ->
                refreshTokenRepository.revokeIfActive(token.getId(), LocalDateTime.now(clock)));
    }

    /**
     * 한 사용자의 모든 세션을 끊는다. 비밀번호 변경이 부른다.
     *
     * <p><b>본인의 현재 세션도 함께 끊긴다.</b> 바꾼 기기만 남기려면 "지금 쓰는 토큰" 을
     * 알아야 하는데, 그 값은 쿠키에 있고 비밀번호 변경 요청이 그것을 들고 다닐 이유가 없다.
     * 무엇보다 계정 탈취가 의심되는 상황에서 하나라도 남기는 판단이 더 위험하다.
     *
     * <p>액세스 토큰은 여기서 끊지 못한다. 서명만으로 검증되는 값이라 만료(기본 30분)까지는
     * 살아 있고, 그 뒤로는 갱신할 Refresh Token 이 없어 끊긴다.
     *
     * @return 실제로 폐기된 토큰 수
     */
    @Transactional
    public int revokeAllFor(Long userId) {
        return refreshTokenRepository.revokeAllActiveByUserId(userId, LocalDateTime.now(clock));
    }

    private IssuedTokenPair issue(User user, LocalDateTime now) {
        AccessToken accessToken = jwtTokenService.issueAccessToken(
                String.valueOf(user.getId()), List.of(user.getRole().name()));
        String rawRefreshToken = codec.generate();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(), codec.hash(rawRefreshToken), now.plus(properties.ttl())));
        return new IssuedTokenPair(accessToken.value(), rawRefreshToken);
    }
}
