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
 * 계정에 연결된 지갑. <b>계정당 활성 1개, 지갑당 하나</b>({@code uk_user_wallet_address}).
 *
 * <p>교체하면 이전 행을 지우지 않고 {@code is_active = FALSE} 로 남긴다. 여러 테이블이
 * 서명 시점 주소를 스냅샷으로 들고 있어서(예: {@code material_trade.seller_wallet}),
 * 행을 지우면 그 주소가 누구 것이었는지 역추적할 수 없다.
 *
 * <p>활성 단일성은 DB 가 강제한다 — {@code active_user_id} 생성 컬럼에 걸린 UNIQUE 다
 * (schema.sql 주석 참고). <b>그 컬럼은 여기서 매핑하지 않는다</b> — DB 가 계산하는 값이므로
 * 엔티티가 쓰려 들면 INSERT 가 깨진다.
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

    /**
     * UNIQUE 를 걸지 않는다 — 계정당 여러 행이 존재하고 그중 활성만 하나다.
     * 활성 단일성은 schema.sql 의 {@code active_user_id} 생성 컬럼 UNIQUE 가 강제한다.
     *
     * <p>여기에 {@code unique = true} 를 두면 Hibernate 가 만드는 테이블
     * ({@code ddl-auto: create-drop} 인 테스트)에만 존재하는 제약이 생겨,
     * <b>이력 보존이 테스트에서만 실패하고 운영에서는 되는</b> 상태가 된다.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 언제나 소문자. {@link WalletAddresses} 참고. */
    @Column(name = "wallet_address", nullable = false, length = WalletAddresses.LENGTH, unique = true)
    private String walletAddress;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    /** 현재 사용 중인 지갑인가. 교체된 지갑은 {@code false} 로 남는다. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deactivated_at")
    private LocalDateTime deactivatedAt;

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
        this.active = true;
        this.verified = false;
    }

    /**
     * 계정에 지갑을 연결한다. 활성 상태로 시작하고, 아직 서명으로 증명되지 않은
     * 상태({@code verified = false})다.
     *
     * <p>같은 계정에 이미 활성 지갑이 있으면 <b>먼저 비활성화해야 한다</b> —
     * 그렇지 않으면 {@code uk_user_wallet_active_user} 위반으로 저장이 실패한다.
     */
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

    /**
     * 비활성 지갑을 다시 활성화한다. 같은 주소를 재등록하는 경로에서 쓴다.
     *
     * <p>{@code wallet_address} 가 전역 UNIQUE 라 예전 행이 자리를 차지하고 있으므로
     * 새 행을 만들 수 없다. 그래서 지우지 않고 되살린다 — 그 편이 이력 보존과도 맞는다.
     * 체인을 바꿔 재등록할 수 있으니 {@code chainId} 도 갱신한다.
     */
    public void reactivate(long chainId, LocalDateTime verifiedAt) {
        if (chainId <= 0) {
            throw new IllegalArgumentException("User wallet chain id must be positive");
        }
        this.chainId = chainId;
        this.active = true;
        this.deactivatedAt = null;
        markVerified(verifiedAt);
    }

    /**
     * 지갑을 비활성화한다. 행을 남겨 두어야 스냅샷 주소의 소유자를 역추적할 수 있다.
     *
     * <p>{@code verified} 는 되돌리지 않는다 — 과거에 소유를 증명했다는 사실 자체는
     * 비활성화로 사라지지 않는다.
     *
     * @return 이번 호출로 상태가 바뀌었으면 {@code true}. 이미 비활성이면 {@code false}
     */
    public boolean deactivate(LocalDateTime deactivatedAt) {
        if (deactivatedAt == null) {
            throw new IllegalArgumentException("User wallet deactivated at must not be null");
        }
        if (!active) {
            return false;
        }
        this.active = false;
        this.deactivatedAt = deactivatedAt;
        return true;
    }
}
