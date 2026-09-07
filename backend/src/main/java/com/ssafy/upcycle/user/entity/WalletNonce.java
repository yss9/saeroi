package com.ssafy.upcycle.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 지갑 서명 로그인에 쓰는 1회용 nonce.
 *
 * <p>같은 서명을 두 번 쓰지 못하게 하는 것이 존재 이유다. nonce 가 서명 메시지 안에 들어가므로,
 * 한 번 소모된({@code used_at} 이 채워진) nonce 에 대한 서명은 재사용 가치가 없어진다.
 *
 * <p><b>{@code BaseEntity} 를 상속하지 않는다.</b> {@code wallet_nonce} 에는 {@code updated_at}
 * 이 없다 — nonce 는 발급되고 한 번 소모될 뿐 수정되는 행이 아니다. {@code BaseEntity} 를
 * 상속하면 없는 컬럼을 {@code nullable = false} 로 요구하게 되어 운영
 * ({@code ddl-auto: validate})에서 기동이 깨진다. 테스트는 {@code create-drop} 이라
 * 이 사고를 잡지 못하므로 {@code UserSchemaMappingTests} 가 대신 잡는다.
 */
@Getter
@Entity
@Table(name = "wallet_nonce")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WalletNonce {

    /** {@code CHAR(32)} — 16 bytes 를 hex 로 편 길이. {@code WalletAuthService} 가 생성한다. */
    public static final int NONCE_LENGTH = 32;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 언제나 소문자. {@link WalletAddresses} 참고. */
    @Column(name = "wallet_address", nullable = false, length = WalletAddresses.LENGTH)
    private String walletAddress;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "nonce", nullable = false, columnDefinition = "CHAR(32)")
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 채워지면 소모된 것. 재사용 방지의 근거이며 {@code WalletNonceRepository.markUsed} 가 조건부로 쓴다. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private WalletNonce(
            Long userId, String walletAddress, long chainId, String nonce, LocalDateTime expiresAt) {
        if (userId == null) {
            throw new IllegalArgumentException("Wallet nonce user id must not be null");
        }
        // schema.sql 의 chk_wallet_nonce_chain_id 를 그대로 미러링한다.
        if (chainId <= 0) {
            throw new IllegalArgumentException("Wallet nonce chain id must be positive");
        }
        if (nonce == null || nonce.length() != NONCE_LENGTH) {
            throw new IllegalArgumentException(
                    "Wallet nonce must be exactly " + NONCE_LENGTH + " characters");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("Wallet nonce expiry must not be null");
        }
        this.userId = userId;
        this.walletAddress = WalletAddresses.normalize(walletAddress);
        this.chainId = chainId;
        this.nonce = nonce;
        this.expiresAt = expiresAt;
    }

    public static WalletNonce issue(
            Long userId, String walletAddress, long chainId, String nonce, LocalDateTime expiresAt) {
        return new WalletNonce(userId, walletAddress, chainId, nonce, expiresAt);
    }

    /** 만료 시각 정각도 만료로 본다 — 경계에서 통과시켜 얻을 것이 없다. */
    public boolean isExpiredAt(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }
}
