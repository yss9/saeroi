package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.security.jwt.AccessToken;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.user.WalletAuthErrorCode;
import com.ssafy.upcycle.user.config.WalletAuthProperties;
import com.ssafy.upcycle.user.dto.UserSummaryResponse;
import com.ssafy.upcycle.user.dto.WalletLoginResponse;
import com.ssafy.upcycle.user.dto.WalletNonceRequest;
import com.ssafy.upcycle.user.dto.WalletNonceResponse;
import com.ssafy.upcycle.user.dto.WalletVerifyRequest;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.entity.WalletAddresses;
import com.ssafy.upcycle.user.entity.WalletNonce;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.WalletNonceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;

/**
 * 지갑 서명 로그인.
 *
 * <pre>
 *   nonce 발급  → 지갑이 서명 → verify → JWT
 * </pre>
 *
 * <p>두 엔드포인트 모두 인증 없이 열려 있다({@code /api/v1/auth/**}). 그래서 요청당 하는 일을
 * 싸게 유지하고, <b>모르는 주소에는 어떤 행도 만들지 않는다</b> — 등록되지 않은 지갑을 거부하는
 * 정책은 UX 결정이자 저장소를 부풀리지 못하게 하는 방어이기도 하다.
 */
@Service
@RequiredArgsConstructor
public class WalletAuthService {

    private final UserRepository userRepository;
    private final UserWalletRepository userWalletRepository;
    private final WalletNonceRepository walletNonceRepository;
    private final WalletSignatureVerifier signatureVerifier;
    private final WalletNonceGenerator nonceGenerator;
    private final JwtTokenService jwtTokenService;
    private final WalletAuthProperties properties;
    private final Clock clock;

    /**
     * 1회용 nonce 를 발급하고, 서명할 정본 메시지를 함께 돌려준다.
     *
     * <p>계정 상태 검사를 여기서 먼저 하는 것이 의도적이다. 정지된 계정이나 미등록 지갑을
     * verify 단계에서 걸러도 결과는 같지만, 그러면 사용자가 MetaMask 팝업에 서명까지 하고 나서
     * 거부당한다.
     */
    @Transactional
    public WalletNonceResponse issueNonce(WalletNonceRequest request) {
        String address = normalize(request.walletAddress());
        UserWallet wallet = userWalletRepository
                .findByWalletAddressAndActiveTrue(address)
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.WALLET_NOT_LINKED));

        if (request.chainId() != null && request.chainId() != wallet.getChainId()) {
            throw new BusinessException(WalletAuthErrorCode.WALLET_CHAIN_MISMATCH);
        }
        requireWalletCapableUser(wallet.getUserId());

        // 지갑당 살아 있는 nonce 는 하나만 둔다. 남겨 두면 오래된 nonce 로도 로그인할 수 있고,
        // 만료 정리 작업이 따로 필요해진다.
        walletNonceRepository.deleteByWalletAddressAndUsedAtIsNull(address);

        LocalDateTime now = LocalDateTime.now(clock);
        WalletNonce issued = walletNonceRepository.save(WalletNonce.issue(
                wallet.getUserId(),
                address,
                wallet.getChainId(),
                nonceGenerator.generate(),
                now.plus(properties.nonceTtl())));

        String checksummed = Keys.toChecksumAddress(address);
        return new WalletNonceResponse(
                checksummed,
                issued.getNonce(),
                WalletLoginMessage.build(checksummed, issued.getNonce()),
                issued.getExpiresAt());
    }

    /**
     * 서명에서 주소를 복원해 요청 주소와 대조하고, 맞으면 액세스 토큰을 발급한다.
     */
    @Transactional
    public WalletLoginResponse verify(WalletVerifyRequest request) {
        String address = normalize(request.walletAddress());
        LocalDateTime now = LocalDateTime.now(clock);

        WalletNonce nonce = walletNonceRepository
                .findByWalletAddressAndNonce(address, request.nonce())
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.NONCE_NOT_FOUND));
        if (nonce.isUsed()) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_ALREADY_USED);
        }
        if (nonce.isExpiredAt(now)) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_EXPIRED);
        }

        // 암호 연산보다 먼저 nonce 를 소모한다. 조건부 UPDATE 의 영향 행 수가 곧
        // "내가 이겼는가" 이며, 이것이 1회용 보장의 근거다 — 동시에 들어온 두 요청 중
        // 하나만 1을 받는다 (WalletNonceRepositoryTests 가 SQL 레벨에서 확인한다).
        //
        // 검증이 실패하면 이 메서드가 던지고 트랜잭션이 통째로 롤백되므로 nonce 는 다시 살아난다.
        // 의도한 동작이다. "실패해도 태워 버리기" 를 하려면 별도 트랜잭션이 필요한데,
        // 그렇게까지 할 이유가 없다 — 서명은 개인키 없이 만들 수 없어 같은 nonce 로 값을
        // 바꿔 가며 시도해 봐야 얻을 것이 없고, 정당한 사용자는 그냥 재시도하면 된다.
        // 소모가 확정되는 것은 검증까지 통과해 커밋될 때다.
        if (walletNonceRepository.markUsed(nonce.getId(), now) != 1) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_ALREADY_USED);
        }

        String checksummed = Keys.toChecksumAddress(address);
        String message = WalletLoginMessage.build(checksummed, nonce.getNonce());
        String recovered = signatureVerifier.recoverAddress(message, request.signature());
        if (!recovered.equalsIgnoreCase(address)) {
            // 복원된 주소는 로그에도 응답에도 남기지 않는다.
            throw new BusinessException(WalletAuthErrorCode.SIGNATURE_MISMATCH);
        }

        // ⚠️ markUsed 가 영속성 컨텍스트를 비우므로(clearAutomatically) 지갑은 반드시
        // 그 호출 뒤에 로드해야 한다. 앞에서 읽어 두면 아래 markVerified 가 조용히 사라진다.
        //
        // 활성 지갑만 찾는다. nonce 를 받아 둔 뒤 지갑이 비활성화됐다면 로그인시키지 않는다 —
        // 비활성 지갑은 없는 것과 같게 취급한다.
        UserWallet wallet = userWalletRepository
                .findByWalletAddressAndActiveTrue(address)
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.WALLET_NOT_LINKED));
        wallet.markVerified(now);

        User user = requireWalletCapableUser(wallet.getUserId());
        AccessToken token =
                jwtTokenService.issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()));

        return new WalletLoginResponse(token.value(), UserSummaryResponse.from(user));
    }

    /**
     * 지갑 로그인이 가능한 계정인지 확인한다.
     *
     * <p>역할을 먼저 보고 상태를 나중에 본다 — 정지 여부는 되돌릴 수 있지만
     * 역할은 애초에 이 기능의 대상이 아니라는 뜻이라 더 근본적인 거절 사유다.
     *
     * <p>주소로 지갑을 먼저 찾은 뒤 그 소유자를 검사하므로, 등록되지 않은 주소는
     * 여기 오기 전에 {@code WALLET_NOT_LINKED} 로 걸러진다.
     */
    private User requireWalletCapableUser(Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.USER_NOT_FOUND));
        if (!user.getRole().canOwnWallet()) {
            throw new BusinessException(WalletAuthErrorCode.WALLET_ROLE_NOT_ALLOWED);
        }
        if (!user.isActive()) {
            throw new BusinessException(WalletAuthErrorCode.USER_SUSPENDED);
        }
        return user;
    }

    /**
     * Bean Validation 이 형식을 이미 걸렀지만, 서비스를 직접 부르는 경로(테스트·내부 호출)도
     * 같은 규칙을 지나게 한다. 형식 오류를 도메인 에러 코드로 바꿔 준다.
     */
    private static String normalize(String walletAddress) {
        try {
            return WalletAddresses.normalize(walletAddress);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(WalletAuthErrorCode.INVALID_WALLET_ADDRESS);
        }
    }
}
