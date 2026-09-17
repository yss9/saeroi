package com.ssafy.upcycle.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.TokenRefreshErrorCode;
import com.ssafy.upcycle.user.entity.RefreshToken;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.RefreshTokenRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@SpringBootTest
class RefreshTokenServiceTests {

    @Autowired
    private RefreshTokenService service;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenCodec codec;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private Clock clock;

    private User user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        user = userRepository.save(User.register(
                "consumer@example.com", "{noop}x", "소비자", "010-0000-0000", UserRole.CONSUMER));
    }

    @AfterEach
    void tearDown() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void storesOnlyAHashWhenIssuingATokenPair() {
        IssuedTokenPair issued = service.issueFor(user);

        assertThat(issued.accessToken()).isNotBlank();
        assertThat(issued.refreshToken()).isNotBlank();
        assertThat(refreshTokenRepository.findAll())
                .singleElement()
                .satisfies(stored -> {
                    assertThat(stored.getTokenHash()).isEqualTo(codec.hash(issued.refreshToken()));
                    assertThat(stored.getTokenHash()).doesNotContain(issued.refreshToken());
                    assertThat(stored.isRevoked()).isFalse();
                });
    }

    @Test
    void rotatesTheRefreshTokenAndIssuesAnAccessTokenWithTheCurrentRole() {
        IssuedTokenPair first = service.issueFor(user);

        IssuedTokenPair rotated = service.refresh(first.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(first.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(rotated.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isFalse());
        Jwt accessToken = jwtDecoder.decode(rotated.accessToken());
        assertThat(accessToken.getSubject()).isEqualTo(String.valueOf(user.getId()));
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("CONSUMER");
    }

    @Test
    void rejectsARefreshTokenAfterItHasBeenRotated() {
        IssuedTokenPair first = service.issueFor(user);
        service.refresh(first.refreshToken());

        assertThatThrownBy(() -> service.refresh(first.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void rejectsAnExpiredRefreshToken() {
        String rawToken = "expired-token";
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                codec.hash(rawToken),
                LocalDateTime.now(clock).minusSeconds(1)));

        assertThatThrownBy(() -> service.refresh(rawToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TokenRefreshErrorCode.REFRESH_TOKEN_EXPIRED));
    }

    @Test
    void rejectsUnknownTokensWithoutPersistingAnything() {
        assertThatThrownBy(() -> service.refresh("unknown-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN));
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void revokesTheCurrentSessionsRefreshToken() {
        IssuedTokenPair issued = service.issueFor(user);

        service.revoke(issued.refreshToken());

        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(issued.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isTrue());
        assertThatThrownBy(() -> service.refresh(issued.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    void revokingAMissingOrUnknownTokenIsIdempotent() {
        service.revoke(null);
        service.revoke("unknown-token");
        service.revoke("unknown-token");

        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void revokingAnAlreadyRevokedTokenIsIdempotent() {
        IssuedTokenPair issued = service.issueFor(user);

        service.revoke(issued.refreshToken());
        service.revoke(issued.refreshToken());

        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(issued.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isTrue());
    }

    @Test
    void revokingEverySessionCutsAllOfThatUsersDevices() {
        // 비밀번호 변경이 부르는 경로다. 바꾼 기기만 정리하면 침입자의 세션은 살아 있다.
        IssuedTokenPair phone = service.issueFor(user);
        IssuedTokenPair laptop = service.issueFor(user);

        assertThat(service.revokeAllFor(user.getId())).isEqualTo(2);

        for (IssuedTokenPair session : new IssuedTokenPair[] {phone, laptop}) {
            assertThatThrownBy(() -> service.refresh(session.refreshToken()))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(TokenRefreshErrorCode.INVALID_REFRESH_TOKEN));
        }
    }

    @Test
    void revokingEverySessionLeavesOtherUsersAlone() {
        // 조건에서 user_id 가 빠지면 한 사람의 비밀번호 변경이 전원을 로그아웃시킨다.
        IssuedTokenPair mine = service.issueFor(user);
        User other = userRepository.save(User.register(
                "other@example.com", "{noop}x", "이웃", "010-2222-3333", UserRole.CONSUMER));
        IssuedTokenPair theirs = service.issueFor(other);

        service.revokeAllFor(user.getId());

        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(mine.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(theirs.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isFalse());
    }

    @Test
    void revokingEverySessionKeepsTheFirstRevocationTime() {
        // 이미 폐기된 행의 revoked_at 을 덮어쓰면 처음 폐기된 시각을 잃는다.
        IssuedTokenPair issued = service.issueFor(user);
        service.revoke(issued.refreshToken());
        LocalDateTime revokedAt = refreshTokenRepository
                .findByTokenHash(codec.hash(issued.refreshToken()))
                .orElseThrow()
                .getRevokedAt();

        assertThat(service.revokeAllFor(user.getId())).isZero();

        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(issued.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.getRevokedAt()).isEqualTo(revokedAt));
    }

    @Test
    void suspendedUsersCannotRefreshTheirSession() {
        IssuedTokenPair issued = service.issueFor(user);
        user.suspend();
        userRepository.saveAndFlush(user);

        assertThatThrownBy(() -> service.refresh(issued.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(TokenRefreshErrorCode.USER_SUSPENDED));
    }
}
