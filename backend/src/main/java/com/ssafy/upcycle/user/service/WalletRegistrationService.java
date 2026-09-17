package com.ssafy.upcycle.user.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.WalletAuthErrorCode;
import com.ssafy.upcycle.user.config.WalletAuthProperties;
import com.ssafy.upcycle.user.dto.WalletNonceResponse;
import com.ssafy.upcycle.user.dto.WalletRegisterNonceRequest;
import com.ssafy.upcycle.user.dto.WalletRegisterRequest;
import com.ssafy.upcycle.user.dto.WalletResponse;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;

/**
 * 지갑 등록·교체·비활성화. <b>인증된 사용자</b>가 자기 지갑을 계정에 붙이는 경로다.
 *
 * <p>{@link WalletAuthService}(로그인)와 나눈 이유는 사용자를 알아내는 방향이 반대이기 때문이다.
 * 로그인은 <em>주소로 계정을 찾고</em>, 등록은 <em>계정이 주소를 주장한다.</em>
 * 그래서 로그인은 인증이 없고 등록은 인증이 필요하다.
 *
 * <p>서명 검증 자체는 {@link WalletSignatureVerifier} 와 {@link WalletLoginMessage} 를 그대로
 * 재사용한다 — 사용자가 MetaMask 에서 보는 문구가 로그인과 등록에서 달라야 할 이유가 없고,
 * 문구를 하나로 두면 계약이 한 곳에만 있다.
 */
@Service
@RequiredArgsConstructor
public class WalletRegistrationService {

    private final UserRepository userRepository;
    private final UserWalletRepository userWalletRepository;
    private final WalletNonceRepository walletNonceRepository;
    private final WalletSignatureVerifier signatureVerifier;
    private final WalletNonceGenerator nonceGenerator;
    private final WalletAuthProperties properties;
    private final Clock clock;

    /**
     * 등록용 nonce 를 발급한다.
     *
     * <p>아직 계정에 붙지 않은 주소이므로 소유자를 주소로 찾을 수 없다 — 인증된 사용자 id 를 받는다.
     * 다른 계정이 이미 쓰는 주소면 여기서 거절한다. {@code wallet_address} 가 전역 UNIQUE 라
     * 등록 단계까지 가면 DB 제약 위반으로 실패하는데, 그건 사용자에게 설명할 수 없는 오류다.
     */
    @Transactional
    public WalletNonceResponse issueNonce(Long userId, WalletRegisterNonceRequest request) {
        requireWalletCapableUser(userId);
        String address = normalize(request.walletAddress());
        requireAddressNotTakenByAnotherUser(userId, address);

        // 이 사용자의 미사용 nonce 를 지운다. 로그인 쪽은 주소 기준으로 지우는데,
        // 등록은 주소가 아직 이 계정 것이 아니라 사용자 기준으로 정리하는 편이 맞다.
        walletNonceRepository.deleteByUserIdAndUsedAtIsNull(userId);

        LocalDateTime now = LocalDateTime.now(clock);
        WalletNonce issued = walletNonceRepository.save(WalletNonce.issue(
                userId,
                address,
                request.chainId(),
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
     * 서명을 검증하고 지갑을 등록한다. 이미 활성 지갑이 있으면 <b>같은 트랜잭션에서 비활성화</b>한다.
     *
     * <p>순서가 중요하다. 새 행을 먼저 넣으면 {@code uk_user_wallet_active_user} 위반이 난다 —
     * 활성 지갑은 계정당 하나이기 때문이다. 비활성화 후 명시적으로 flush 해서 DB 가 새 INSERT 를
     * 보기 전에 이전 행의 {@code active_user_id} 가 NULL 이 되도록 만든다.
     */
    @Transactional
    public WalletResponse register(Long userId, WalletRegisterRequest request) {
        requireWalletCapableUser(userId);
        String address = normalize(request.walletAddress());
        requireAddressNotTakenByAnotherUser(userId, address);

        LocalDateTime now = LocalDateTime.now(clock);
        WalletNonce nonce = consumeNonce(userId, address, request.nonce(), now);

        String checksummed = Keys.toChecksumAddress(address);
        String message = WalletLoginMessage.build(checksummed, nonce.getNonce());
        String recovered = signatureVerifier.recoverAddress(message, request.signature());
        if (!recovered.equalsIgnoreCase(address)) {
            throw new BusinessException(WalletAuthErrorCode.SIGNATURE_MISMATCH);
        }

        // 같은 주소를 재등록하는 경우(비활성 → 다시 활성)도 여기로 온다.
        Optional<UserWallet> existing = userWalletRepository.findByWalletAddress(address);
        if (existing.isPresent()) {
            UserWallet reactivated = existing.get();
            deactivateCurrentWallet(userId, now, reactivated.getId());
            reactivated.reactivate(request.chainId(), now);
            return WalletResponse.from(reactivated, checksummed);
        }

        deactivateCurrentWallet(userId, now, null);
        UserWallet wallet = userWalletRepository.save(UserWallet.link(userId, address, request.chainId()));
        wallet.markVerified(now);
        return WalletResponse.from(wallet, checksummed);
    }

    /** 활성·비활성 전부. 화면이 교체 이력을 보여줘야 하므로 걸러내지 않는다. */
    @Transactional(readOnly = true)
    public List<WalletResponse> findMyWallets(Long userId) {
        return userWalletRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(wallet -> WalletResponse.from(wallet, Keys.toChecksumAddress(wallet.getWalletAddress())))
                .toList();
    }

    /**
     * 지갑을 비활성화한다. 행은 남는다.
     *
     * <p>{@code findByIdAndUserId} 로 조회하므로 남의 지갑 id 를 넣으면 {@code WALLET_NOT_FOUND} 다 —
     * "권한 없음" 과 "없음" 을 구분해 주면 남의 지갑 id 를 훑어 존재 여부를 알아낼 수 있다.
     */
    @Transactional
    public WalletResponse deactivate(Long userId, Long walletId) {
        UserWallet wallet = userWalletRepository
                .findByIdAndUserId(walletId, userId)
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.WALLET_NOT_FOUND));

        if (!wallet.deactivate(LocalDateTime.now(clock))) {
            throw new BusinessException(WalletAuthErrorCode.WALLET_ALREADY_INACTIVE);
        }
        return WalletResponse.from(wallet, Keys.toChecksumAddress(wallet.getWalletAddress()));
    }

    // ── 내부 ─────────────────────────────────────────────────

    /** @param exceptWalletId 재활성화 대상 자신은 건드리지 않는다 (null 이면 전부 대상) */
    private void deactivateCurrentWallet(Long userId, LocalDateTime now, Long exceptWalletId) {
        userWalletRepository
                .findByUserIdAndActiveTrue(userId)
                .filter(current -> !current.getId().equals(exceptWalletId))
                .ifPresent(current -> {
                    current.deactivate(now);
                    // active_user_id UNIQUE 때문에 새 INSERT 전에 DB 에 반영돼야 한다.
                    userWalletRepository.flush();
                });
    }

    private WalletNonce consumeNonce(Long userId, String address, String nonce, LocalDateTime now) {
        WalletNonce found = walletNonceRepository
                .findByWalletAddressAndNonce(address, nonce)
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.NONCE_NOT_FOUND));
        // 남이 발급받은 nonce 로 자기 계정에 등록하는 경로를 막는다.
        if (!found.getUserId().equals(userId)) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_NOT_FOUND);
        }
        if (found.isUsed()) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_ALREADY_USED);
        }
        if (found.isExpiredAt(now)) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_EXPIRED);
        }
        if (walletNonceRepository.markUsed(found.getId(), now) != 1) {
            throw new BusinessException(WalletAuthErrorCode.NONCE_ALREADY_USED);
        }
        return found;
    }

    private void requireAddressNotTakenByAnotherUser(Long userId, String address) {
        userWalletRepository
                .findByWalletAddress(address)
                .filter(wallet -> !wallet.getUserId().equals(userId))
                .ifPresent(wallet -> {
                    throw new BusinessException(WalletAuthErrorCode.WALLET_ALREADY_LINKED);
                });
    }

    private User requireWalletCapableUser(Long userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new BusinessException(WalletAuthErrorCode.USER_NOT_FOUND));
        if (!user.getRole().canLinkWallet()) {
            throw new BusinessException(WalletAuthErrorCode.WALLET_ROLE_NOT_ALLOWED);
        }
        if (!user.isActive()) {
            throw new BusinessException(WalletAuthErrorCode.USER_SUSPENDED);
        }
        return user;
    }

    private static String normalize(String walletAddress) {
        try {
            return WalletAddresses.normalize(walletAddress);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(WalletAuthErrorCode.INVALID_WALLET_ADDRESS);
        }
    }
}
