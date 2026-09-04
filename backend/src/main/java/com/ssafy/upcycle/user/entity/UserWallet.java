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
 * 계정에 연결된 지갑. 계정당 하나({@code uk_user_wallet_user}), 지갑당 하나({@code uk_user_wallet_address}).
 *
 * <p><b>{@code BaseEntity} 를 상속하지 않는다</b> — {@code updated_at} 컬럼이 없다.
 * {@link User} 의 같은 주석 참고.
 *
 * <p><b>{@code user_id} 를 연관관계로 두지 않는다.</b> {@code Material.registrantId} 가 세운
 * 선례를 따른다. 이 도메인에서 필요한 건 "누구 것인가" 라는 식별자 하나뿐이고,
 * {@code @ManyToOne} 을 걸면 지갑을 볼 때마다 계정을 함께 끌고 오게 된다.
 *
 * <p>{@code verified} 는 "이 주소의 개인키를 실제로 쥐고 있음을 서명으로 증명했다" 는 뜻이다.
 * 연결(link)만 해 둔 상태와 구분하기 위해 존재한다.
 */
@Getter
@Entity
@Table(name = "user_wallet")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class UserWallet {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 언제나 소문자. {@link WalletAddresses} 참고. */
    @Column(name = "wallet_address", nullable = false, length = WalletAddresses.LENGTH, unique = true)
    private String walletAddress;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private UserWallet(Long userId, String walletAddress, long chainId) {
        if (userId == null) {
            throw new IllegalArgumentException("User wallet user id must not be null");
        }
        // schema.sql 의 chk_user_wallet_chain_id 를 그대로 미러링한다.
        if (chainId <= 0) {
            throw new IllegalArgumentException("User wallet chain id must be positive");
        }
        this.userId = userId;
        this.walletAddress = WalletAddresses.normalize(walletAddress);
        this.chainId = chainId;
        this.verified = false;
    }

    /** 계정에 지갑을 연결한다. 아직 서명으로 증명되지 않은 상태({@code verified = false})로 시작한다. */
    public static UserWallet link(Long userId, String walletAddress, long chainId) {
        return new UserWallet(userId, walletAddress, chainId);
    }

    /**
     * 서명 검증 성공을 기록한다. 재검증해도 안전하도록 멱등하게 두었다 —
     * 로그인할 때마다 불리므로 "이미 verified 면 무시" 가 아니라 시각을 갱신하는 편이
     * 마지막 증명 시점을 알려 준다.
     */
    public void markVerified(LocalDateTime verifiedAt) {
        if (verifiedAt == null) {
            throw new IllegalArgumentException("User wallet verified at must not be null");
        }
        this.verified = true;
        this.verifiedAt = verifiedAt;
    }
}
