package com.ssafy.upcycle.material.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어떤 소유자가 어떤 소재를 얼마나 들고 있는지. 소재×소유자 조합마다 한 행이다
 * (UNIQUE {@code (material_id, owner_id)}).
 *
 * <p><b>available 을 컬럼으로 들고 있지 않는다.</b> ERD 가 명시하는 계산식은 이렇다.
 *
 * <pre>
 *   available = quantity - SUM(material_lock.amount WHERE status = 'ACTIVE')
 * </pre>
 *
 * 잠금 합계를 여기에 중복 저장하면 반드시 어긋난다. 잠금은 여러 도메인이 각자 만들고
 * 해제하는데, 합계 컬럼을 그때마다 정확히 맞추는 일은 결국 실패한다.
 *
 * <p><b>감사 컬럼이 없다.</b> schema.sql 의 material_balance 에는 created_at 도
 * updated_at 도 없다. 이 테이블은 "지금 얼마인가"만 답하는 현재 상태 스냅샷이고,
 * 변경 이력은 material_lock 과 blockchain_transaction 이 각자 남긴다.
 *
 * <p>소유자와 소재를 연관관계가 아니라 FK 값으로 들고 있는 이유는 {@link Material} 참고.
 */
@Getter
@Entity
@Table(name = "material_balance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialBalance {

    private static final Pattern WALLET_ADDRESS_PATTERN =
            Pattern.compile("^0x[0-9a-fA-F]{40}$");

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 조회 편의를 위한 스냅샷. 소유권의 근거는 owner_id 다. */
    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    /** 보유량 (gram). */
    @Column(name = "quantity", nullable = false)
    private long quantity;

    private MaterialBalance(Long materialId, Long ownerId, String walletAddress, long quantity) {
        if (materialId == null) {
            throw new IllegalArgumentException("Material balance material id must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Material balance owner id must not be null");
        }
        this.materialId = materialId;
        this.ownerId = ownerId;
        this.walletAddress = requireWalletAddress(walletAddress);
        if (quantity < 0) {
            throw new IllegalArgumentException("Material balance quantity must not be negative");
        }
        this.quantity = quantity;
    }

    /**
     * 소유 관계를 처음 만든다. 소재 등록 시 등록자에게, 거래 성사 시 매수자에게 생긴다.
     *
     * <p>수량 0 을 허용한다 — 전량을 넘긴 뒤에도 "이 소재를 다룬 적이 있다"는 행은
     * 남아야 하고, schema 의 CHECK 도 {@code quantity >= 0} 이다.
     */
    public static MaterialBalance of(
            Long materialId, Long ownerId, String walletAddress, long quantity) {
        return new MaterialBalance(materialId, ownerId, walletAddress, quantity);
    }

    /** 매입·가공 산출물 수령 등으로 보유량이 늘어난다. */
    public void increase(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Material balance increase amount must be positive");
        }
        this.quantity += amount;
    }

    /**
     * 매도·가공 투입 확정 등으로 보유량이 줄어든다.
     *
     * <p>available 이 아니라 quantity 를 기준으로 검사한다. 잠긴 수량을 실제로 소비하는
     * 경로에서 호출되므로, 잠금까지 빼고 보면 정당한 소비가 막힌다. 잠금 정합성은
     * 호출하는 쪽이 {@link MaterialLock} 과 함께 책임진다.
     */
    public void decrease(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Material balance decrease amount must be positive");
        }
        if (this.quantity < amount) {
            throw new IllegalArgumentException(
                    "Material balance cannot go negative: quantity=" + this.quantity
                            + ", requested=" + amount);
        }
        this.quantity -= amount;
    }

    private static String requireWalletAddress(String walletAddress) {
        if (walletAddress == null || walletAddress.isBlank()) {
            throw new IllegalArgumentException("Material balance wallet address must not be blank");
        }
        if (!WALLET_ADDRESS_PATTERN.matcher(walletAddress).matches()) {
            throw new IllegalArgumentException(
                    "Material balance wallet address must be a 0x-prefixed 40 digit hex string");
        }
        return walletAddress;
    }
}
