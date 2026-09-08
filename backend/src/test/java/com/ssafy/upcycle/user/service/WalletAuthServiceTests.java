package com.ssafy.upcycle.user.service;

import static com.ssafy.upcycle.user.WalletTestSignatures.KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.OTHER_KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.WalletAuthErrorCode;
import com.ssafy.upcycle.user.dto.WalletLoginResponse;
import com.ssafy.upcycle.user.dto.WalletNonceRequest;
import com.ssafy.upcycle.user.dto.WalletNonceResponse;
import com.ssafy.upcycle.user.dto.WalletVerifyRequest;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.WalletNonceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;

/**
 * 지갑 로그인 전 과정을 실제 서명과 실제 DB(H2)로 검증한다.
 *
 * <p>시계를 갈아 끼울 수 있게 {@link MutableClock} 을 {@code @Primary} 로 올린다.
 * 만료 검증에 {@code Thread.sleep} 을 쓰지 않기 위해서다.
 */
@SpringBootTest
@Import(WalletAuthServiceTests.FixedClockConfig.class)
class WalletAuthServiceTests {

    private static final Credentials CREDENTIALS = Credentials.create(KEY_PAIR);

    private static final long CHAIN_ID = 1337L;

    @Autowired
    private WalletAuthService walletAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository userWalletRepository;

    @Autowired
    private WalletNonceRepository walletNonceRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private MutableClock clock;

    private String address;

    @BeforeEach
    void setUp() {
        walletNonceRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();
        clock.set(Instant.parse("2026-09-04T03:00:00Z"));

        address = CREDENTIALS.getAddress().toLowerCase(Locale.ROOT);
        User user = userRepository.save(
                User.register("supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        userWalletRepository.save(UserWallet.link(user.getId(), address, CHAIN_ID));
    }

    @AfterEach
    void tearDown() {
        // @SpringBootTest 는 트랜잭션 롤백이 없어 여기서 만든 행이 그대로 커밋된다.
        // 같은 in-memory H2 를 쓰는 다른 테스트로 새지 않도록 직접 지운다.
        walletNonceRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── nonce 발급 ────────────────────────────────────────────

    @Test
    void issuesThirtyTwoCharacterHexNonceWithTheCanonicalMessage() {
        WalletNonceResponse response = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));

        assertThat(response.nonce()).matches("^[0-9a-f]{32}$");
        // Credentials.getAddress() 는 소문자다. 응답은 EIP-55 체크섬이어야 한다.
        String checksummed = Keys.toChecksumAddress(address);
        assertThat(response.walletAddress()).isEqualTo(checksummed).isNotEqualTo(address);
        assertThat(response.message())
                .isEqualTo(WalletLoginMessage.build(checksummed, response.nonce()));
    }

    @Test
    void acceptsAChecksummedAddressAndStoresItLowerCase() {
        walletAuthService.issueNonce(new WalletNonceRequest(Keys.toChecksumAddress(address), CHAIN_ID));

        assertThat(walletNonceRepository.findAll())
                .singleElement()
                .satisfies(nonce -> assertThat(nonce.getWalletAddress()).isEqualTo(address));
    }

    @Test
    void reissuingReplacesThePreviousUnusedNonce() {
        String first = walletAuthService.issueNonce(nonceRequest(CHAIN_ID)).nonce();

        String second = walletAuthService.issueNonce(nonceRequest(CHAIN_ID)).nonce();

        assertThat(second).isNotEqualTo(first);
        assertThat(walletNonceRepository.findAll()).hasSize(1);
        assertThat(walletNonceRepository.findByWalletAddressAndNonce(address, first)).isEmpty();
    }

    @Test
    void omittedChainIdIsFilledFromTheRegisteredWallet() {
        walletAuthService.issueNonce(new WalletNonceRequest(address, null));

        assertThat(walletNonceRepository.findAll())
                .singleElement()
                .satisfies(nonce -> assertThat(nonce.getChainId()).isEqualTo(CHAIN_ID));
    }

    @Test
    void rejectsAWalletThatIsNotLinkedToAnyAccount() {
        WalletNonceRequest request =
                new WalletNonceRequest("0x0000000000000000000000000000000000000001", CHAIN_ID);

        assertThatThrownBy(() -> walletAuthService.issueNonce(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.WALLET_NOT_LINKED);
    }

    @Test
    void rejectsAChainIdThatDiffersFromTheRegisteredOne() {
        WalletNonceRequest request = nonceRequest(1L);

        assertThatThrownBy(() -> walletAuthService.issueNonce(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.WALLET_CHAIN_MISMATCH);
    }

    @Test
    void rejectsANonceRequestFromAConsumerOwnedWallet() {
        // user_wallet 은 SUPPLIER/BRAND/PROCESSOR 만 갖는다는 정책(FR-005)을 코드가 강제하는지.
        // DB 에는 이걸 막는 제약이 없어서 시드가 잘못되면 여기서만 걸린다.
        relinkWalletTo(UserRole.CONSUMER);
        WalletNonceRequest request = nonceRequest(CHAIN_ID);

        assertThatThrownBy(() -> walletAuthService.issueNonce(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.WALLET_ROLE_NOT_ALLOWED);
    }

    @Test
    void rejectsANonceRequestFromAnAdminOwnedWallet() {
        relinkWalletTo(UserRole.ADMIN);
        WalletNonceRequest request = nonceRequest(CHAIN_ID);

        assertThatThrownBy(() -> walletAuthService.issueNonce(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.WALLET_ROLE_NOT_ALLOWED);
    }

    @Test
    void rejectsASuspendedAccountBeforeTheWalletPopupAppears() {
        suspendOwner();
        WalletNonceRequest request = nonceRequest(CHAIN_ID);

        assertThatThrownBy(() -> walletAuthService.issueNonce(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.USER_SUSPENDED);
    }

    // ── verify ───────────────────────────────────────────────

    @Test
    void verifiesAValidSignatureAndIssuesAToken() {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));

        WalletLoginResponse response = walletAuthService.verify(verifyRequest(issued, KEY_PAIR));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("supplier@example.com");
        assertThat(response.user().role()).isEqualTo(UserRole.SUPPLIER);
    }

    /**
     * S15P21E104-288 — 발급된 토큰의 {@code roles} 클레임이 <b>DB 의 역할 그대로</b>여야 한다.
     *
     * <p>이미 확인하는 곳이 있다 — {@code WalletAuthControllerTests} 가 HTTP 응답의 토큰을
     * 디코드해 {@code roles} 를 본다. 그런데 <b>{@code SUPPLIER} 한 역할만</b> 본다.
     * 한 역할만 확인하면 발급부가 역할을 읽지 않고 어딘가에 <b>고정</b>된 값을 넣어도
     * 통과한다. 그래서 지갑을 가질 수 있는 세 역할({@code canOwnWallet()})을 모두 돈다.
     *
     * <p>{@code CONSUMER}·{@code ADMIN} 은 이 경로로 확인할 수 없다 — 지갑 로그인 자체가
     * {@code WALLET_ROLE_NOT_ALLOWED} 로 막힌다(위 두 테스트). 그쪽 경계는
     * {@code RoleAuthorizationIntegrationTests} 가 본다.
     *
     * <p>단언 세 개가 각각 다른 회귀를 잡는다 — <b>정확히 하나</b>(역할을 여러 개 싣기
     * 시작하면 {@code hasRole} 판정이 조용히 넓어진다), <b>DB 역할과 일치</b>(발급부가
     * 역할을 안 읽는 회귀), <b>{@code ROLE_} 접두어 없음</b>(붙이는 것은 읽는 쪽인
     * {@code JwtAuthorityConverter} 다. 발급 시 붙이면 권한이 {@code ROLE_ROLE_*} 가 되어
     * 모든 인가가 조용히 실패한다 — {@code JwtTokenService} 가 거부하는 이유다).
     */
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"SUPPLIER", "BRAND", "PROCESSOR"})
    void theIssuedTokenCarriesTheDatabaseRoleAsTheOnlyClaimValue(UserRole role) {
        // 이 클래스의 Clock 은 고정 시각(2026-09-04)이라 그대로 발급하면 토큰이 이미 만료된
        // 상태로 나온다. JwtDecoder 는 실제 시각으로 만료를 검증하므로 decode 가 실패한다.
        // 여기서 재는 것은 만료가 아니라 클레임 값이므로 시계를 현재로 맞춘다.
        clock.set(Instant.now());
        // setUp 이 만들어 둔 소유자가 SUPPLIER 다. 같은 이메일로 다시 만들면 UNIQUE 가 깨진다.
        if (role != UserRole.SUPPLIER) {
            relinkWalletTo(role);
        }
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));

        WalletLoginResponse response = walletAuthService.verify(verifyRequest(issued, KEY_PAIR));

        Jwt jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly(role.name());
        assertThat(response.user().role()).isEqualTo(role);
        assertThat(jwt.getClaimAsStringList("roles")).noneMatch(value -> value.startsWith("ROLE_"));
    }

    @Test
    void verifyConsumesTheNonce() {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));

        walletAuthService.verify(verifyRequest(issued, KEY_PAIR));

        assertThat(walletNonceRepository.findByWalletAddressAndNonce(address, issued.nonce()))
                .get()
                .satisfies(nonce -> assertThat(nonce.isUsed()).isTrue());
    }

    @Test
    void verifyMarksTheWalletVerified() {
        // markUsed 가 영속성 컨텍스트를 비우므로, 지갑을 그 전에 로드했다면 이 단언이 깨진다.
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));

        walletAuthService.verify(verifyRequest(issued, KEY_PAIR));

        assertThat(userWalletRepository.findByWalletAddress(address))
                .get()
                .satisfies(wallet -> {
                    assertThat(wallet.isVerified()).isTrue();
                    assertThat(wallet.getVerifiedAt()).isNotNull();
                });
    }

    @Test
    void rejectsTheSameNonceASecondTime() {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        WalletVerifyRequest request = verifyRequest(issued, KEY_PAIR);
        walletAuthService.verify(request);

        assertThatThrownBy(() -> walletAuthService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.NONCE_ALREADY_USED);
    }

    @Test
    void rejectsAnExpiredNonce() {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        WalletVerifyRequest request = verifyRequest(issued, KEY_PAIR);
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> walletAuthService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.NONCE_EXPIRED);
    }

    @Test
    void rejectsANonceThatWasNeverIssued() {
        WalletVerifyRequest request = new WalletVerifyRequest(
                address, "ffffffffffffffffffffffffffffffff", sign("anything", KEY_PAIR));

        assertThatThrownBy(() -> walletAuthService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.NONCE_NOT_FOUND);
    }

    @Test
    void rejectsASignatureFromADifferentKey() {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        WalletVerifyRequest request = verifyRequest(issued, OTHER_KEY_PAIR);

        assertThatThrownBy(() -> walletAuthService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.SIGNATURE_MISMATCH);
    }

    @Test
    void aFailedVerificationRollsBackSoTheOwnerCanRetryWithTheSameNonce() {
        // 검증 실패는 트랜잭션을 통째로 롤백시키므로 markUsed 도 되돌아간다. 의도한 동작이다 —
        // 서명은 개인키 없이 만들 수 없어 같은 nonce 를 두드려 봐야 얻을 것이 없고,
        // 정당한 사용자가 재시도할 길을 막을 이유도 없다. (WalletAuthService 의 주석 참고)
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        try {
            walletAuthService.verify(verifyRequest(issued, OTHER_KEY_PAIR));
        } catch (BusinessException ignored) {
            // 실패가 이 테스트의 전제다.
        }

        WalletLoginResponse retried = walletAuthService.verify(verifyRequest(issued, KEY_PAIR));

        assertThat(retried.accessToken()).isNotBlank();
    }

    @Test
    void rejectsAtVerifyEvenIfTheNonceWasAlreadyIssued() {
        // nonce 를 받아 둔 뒤 지갑이 CONSUMER 계정으로 옮겨간 경우에도 토큰을 주지 않는다.
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        relinkWalletTo(UserRole.CONSUMER);
        WalletVerifyRequest request = verifyRequest(issued, KEY_PAIR);

        assertThatThrownBy(() -> walletAuthService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.WALLET_ROLE_NOT_ALLOWED);
    }

    @Test
    void rejectsASuspendedAccountAtVerifyToo() {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        suspendOwner();
        WalletVerifyRequest request = verifyRequest(issued, KEY_PAIR);

        assertThatThrownBy(() -> walletAuthService.verify(request))
                .isInstanceOf(BusinessException.class)
                .extracting(WalletAuthServiceTests::codeOf)
                .isEqualTo(WalletAuthErrorCode.USER_SUSPENDED);
    }

    @Test
    void concurrentVerificationsOfTheSameNonceLetExactlyOneThrough() throws Exception {
        WalletNonceResponse issued = walletAuthService.issueNonce(nonceRequest(CHAIN_ID));
        WalletVerifyRequest request = verifyRequest(issued, KEY_PAIR);
        Callable<Boolean> attempt = () -> {
            try {
                walletAuthService.verify(request);
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = pool.invokeAll(List.of(attempt, attempt));
            long succeeded = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    succeeded++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ── helpers ──────────────────────────────────────────────

    private WalletNonceRequest nonceRequest(long chainId) {
        return new WalletNonceRequest(address, chainId);
    }

    private WalletVerifyRequest verifyRequest(WalletNonceResponse issued, ECKeyPair keyPair) {
        return new WalletVerifyRequest(address, issued.nonce(), sign(issued.message(), keyPair));
    }

    /**
     * 같은 지갑 주소를 지정한 역할의 새 계정으로 옮긴다. `wallet_address` 가 UNIQUE 라 기존 행을 지운다.
     *
     * <p>발급된 nonce 는 지우지 않는다 — verify 단계 검사를 확인하는 테스트가
     * 그 nonce 를 그대로 써야 하기 때문이다. `wallet_nonce.user_id` 가 가리키는 예전 계정은
     * 남겨 두므로 FK 도 깨지지 않는다.
     */
    private void relinkWalletTo(UserRole role) {
        userWalletRepository.deleteAll();
        User owner = userRepository.save(User.register(
                role.name().toLowerCase(Locale.ROOT) + "@example.com",
                "{noop}x",
                role.name(),
                "010-0000-0000",
                role));
        userWalletRepository.save(UserWallet.link(owner.getId(), address, CHAIN_ID));
    }

    private void suspendOwner() {
        User user = userRepository.findByEmail("supplier@example.com").orElseThrow();
        user.suspend();
        userRepository.save(user);
    }

    private static WalletAuthErrorCode codeOf(Throwable error) {
        return (WalletAuthErrorCode) ((BusinessException) error).getErrorCode();
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-09-04T03:00:00Z"));
        }
    }

    /** 테스트가 시간을 밀 수 있는 시계. {@code Thread.sleep} 없이 만료를 검증한다. */
    static class MutableClock extends Clock {

        private volatile Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            this.instant = value;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
