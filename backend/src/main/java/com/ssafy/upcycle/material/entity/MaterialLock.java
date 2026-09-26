package com.ssafy.upcycle.material.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 같은 수량이 여러 작업에 동시에 쓰이는 것을 막는 예약 표시.
 *
 * <p><b>잠금마다 ID 를 부여한다.</b> balance 에 "잠긴 합계" 컬럼 하나만 두는 설계로는
 * 해제·만료·추적을 구현할 수 없다. 누가 왜 얼마를 잠갔는지 모르면 되돌릴 수가 없다.
 *
 * <p>도메인별 생성·해제 시점은 ERD 가 정해두었다.
 *
 * <pre>
 *   Marketplace  생성: 판매 등록    해제: 판매 취소       소비: 구매자 수령 확인
 *   Processing   생성: 요청 생성    해제: 요청 취소·거절·만료       소비: Processing 확정
 *   Production   생성: DRAFT 저장   해제: 배치 취소·실패  소비: Production 확정
 * </pre>
 *
 * <p>가공 요청은 업체 한 곳을 지정하며 생성 즉시 잠근다. 견적 선택 시 같은 잠금을
 * Job에 연결하고 배송 중에도 유지한다. 실제 보유 토큰 수량은 요청 단계에서 차감하지 않는다.
 *
 * <p>배송 중에도 잠금이 유지되는 것이 중요하다. 그러지 않으면 판매자가 배송 중인 20kg 을
 * 다른 곳에 또 팔 수 있다. Marketplace 잠금은 구매자 수령 확인까지 살아 있다가
 * 그 시점에 CONSUMED 로 바뀌며 보유량이 이전된다.
 */
@Getter
@Entity
@Table(name = "material_lock")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MaterialLock {

    /**
     * 참조 대상이 아직 저장되지 않았음을 뜻하는 자리값.
     *
     * <p><b>왜 필요한가.</b> {@code material_listing.material_lock_id} 에 FK 가 걸려 있어
     * 잠금 행이 먼저 INSERT 되어야 하는데, 그 잠금의 {@code reference_id} 는 아직 없는
     * 판매 글의 id 다. 순환이라 한쪽은 나중에 채울 수밖에 없다. 자리값으로 저장한 뒤
     * <b>같은 트랜잭션 안에서</b> {@link #assignReference} 로 교체한다.
     *
     * <p>{@code reference_id} 에는 DB FK 가 없어 0 을 넣어도 INSERT 는 통과한다.
     * 그래서 이 자리값이 커밋까지 살아남으면 조용히 잘못된 데이터가 된다 —
     * 채우는 것은 호출부의 책임이고, 실패하면 트랜잭션째 롤백되어야 한다.
     */
    public static final long PENDING_REFERENCE_ID = 0L;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chain_lock_id", unique = true)
    private Long chainLockId;

    @Column(name = "material_id", nullable = false)
    private Long materialId;

    /** 잠긴 주체. balance 의 owner 와 같아야 한다. */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 잠금 수량 (gram). */
    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private MaterialLockPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private MaterialLockReferenceType referenceType;

    /** {@link #referenceType} 이 가리키는 테이블의 PK. DB FK 가 아니므로 무결성은 호출부 책임. */
    @Column(name = "reference_id", nullable = false)
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MaterialLockStatus status;

    /** 만료 기준. null 이면 만료가 없다. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** ACTIVE 를 벗어난 시각. 해제든 소비든 기록한다. */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    private MaterialLock(
            Long materialId,
            Long ownerId,
            long amount,
            MaterialLockPurpose purpose,
            MaterialLockReferenceType referenceType,
            Long referenceId,
            LocalDateTime expiresAt) {
        if (materialId == null) {
            throw new IllegalArgumentException("Material lock material id must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("Material lock owner id must not be null");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("Material lock purpose must not be null");
        }
        if (referenceType == null) {
            throw new IllegalArgumentException("Material lock reference type must not be null");
        }
        if (referenceId == null) {
            throw new IllegalArgumentException("Material lock reference id must not be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Material lock amount must be positive");
        }
        this.materialId = materialId;
        this.ownerId = ownerId;
        this.amount = amount;
        this.purpose = purpose;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.expiresAt = expiresAt;
        this.status = MaterialLockStatus.ACTIVE;
    }

    /**
     * 잠금을 건다. 항상 ACTIVE 로 시작한다.
     *
     * <p>수량이 0 인 ACTIVE 잠금은 만들 수 없다. available 을 전혀 줄이지 않으면서
     * 자리만 차지하는 행이고, schema 의 CHECK 도 {@code amount > 0} 조건을 건다.
     */
    public static MaterialLock create(
            Long materialId,
            Long ownerId,
            long amount,
            MaterialLockPurpose purpose,
            MaterialLockReferenceType referenceType,
            Long referenceId,
            LocalDateTime expiresAt) {
        return new MaterialLock(
                materialId, ownerId, amount, purpose, referenceType, referenceId, expiresAt);
    }

    /**
     * 온체인 잠금 번호를 연결한다.
     *
     * <p>DB 잠금과 온체인 {@code MaterialLock} 은 <b>따로 만들어진다</b> — 서버가 DB 행을
     * 만들고, 화면이 지갑으로 {@code createMaterialLock} 을 보낸다. 그 둘을 잇는 값이
     * {@code chain_lock_id} 이고, 이것이 없으면 <b>어느 온체인 잠금이 이 투입에 대응하는지
     * 서버가 알 수 없다.</b> 소각({@code consumeLockedMaterial})이 온체인 {@code lockId} 를
     * 인자로 받으므로, 소비를 체인과 맞추려면 반드시 필요하다.
     *
     * <p>한 번만 연결한다. 이미 붙은 번호를 바꾸는 것은 다른 온체인 잠금을 이 투입의 것으로
     * 둔갑시키는 것과 같다 — {@link #assignReference} 가 같은 이유로 같은 검사를 한다.
     */
    public void linkChainLock(Long chainLockId) {
        if (this.chainLockId != null) {
            throw new IllegalStateException(
                    "Material lock is already linked to chain lock " + this.chainLockId);
        }
        if (chainLockId == null || chainLockId <= 0) {
            throw new IllegalArgumentException("Chain lock id must be positive");
        }
        this.chainLockId = chainLockId;
    }

    /**
     * 저장으로 확정된 참조 대상의 id 를 채운다.
     *
     * <p>{@link #PENDING_REFERENCE_ID} 로 만들어 둔 잠금에만 쓸 수 있다. 이미 실제 대상을
     * 가리키는 잠금의 참조를 바꾸는 것은 다른 거래의 잠금을 훔쳐오는 것과 같아서 막는다.
     */
    public void assignReference(Long referenceId) {
        if (this.referenceId == null || this.referenceId != PENDING_REFERENCE_ID) {
            throw new IllegalStateException(
                    "Material lock reference is already assigned: " + this.referenceId);
        }
        if (referenceId == null || referenceId <= 0) {
            throw new IllegalArgumentException("Material lock reference id must be positive");
        }
        this.referenceId = referenceId;
    }

    /** 취소·실패로 잠금을 푼다. 수량은 available 로 그대로 돌아간다. */
    public void release(LocalDateTime releasedAt) {
        requireActive("release");
        this.status = MaterialLockStatus.RELEASED;
        this.releasedAt = requireTimestamp(releasedAt);
    }

    /** Expiry releases availability without decreasing the owner's token balance. */
    public void expire(LocalDateTime now) {
        requireActive("expire");
        if (!isExpiredAt(now)) throw new IllegalStateException("Material lock is not due");
        status = MaterialLockStatus.EXPIRED;
        releasedAt = now;
    }

    /** Quote selection must verify Job ownership and call this within its Job creation transaction. */
    public void attachToProcessingJob(Long requestId, Long jobId) {
        attachToProcessingJob(requestId, jobId, null);
    }

    public void attachToProcessingJob(Long requestId, Long jobId, LocalDateTime paymentExpiresAt) {
        requireActive("attach to processing job");
        if (purpose != MaterialLockPurpose.PROCESSING
                || referenceType != MaterialLockReferenceType.PROCESSING_REQUEST
                || !referenceId.equals(requestId) || jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("An active lock of this processing request and a valid Job are required");
        }
        referenceType = MaterialLockReferenceType.PROCESSING_JOB;
        referenceId = jobId;
        // The request deadline is replaced by the selected Job's payment deadline.
        expiresAt = paymentExpiresAt;
    }

    /** Paid Jobs keep their reservation until the later processing lifecycle consumes/releases it. */
    public void clearExpiryForPaidProcessingJob(Long jobId) {
        requireActive("keep paid processing job lock");
        if (referenceType != MaterialLockReferenceType.PROCESSING_JOB || !referenceId.equals(jobId)) {
            throw new IllegalArgumentException("This lock does not belong to the processing Job");
        }
        expiresAt = null;
    }

    /**
     * 잠긴 수량이 실제로 소비되었다. 보유량 차감은 {@link MaterialBalance#decrease} 가
     * 따로 수행한다 — 두 테이블이라 한 메서드로 묶을 수 없고, 같은 트랜잭션 안에서
     * 함께 호출하는 것이 호출부의 책임이다.
     */
    public void consume(LocalDateTime consumedAt) {
        requireActive("consume");
        this.status = MaterialLockStatus.CONSUMED;
        this.releasedAt = requireTimestamp(consumedAt);
    }

    /**
     * 부분 거래가 성사되어 잠금 수량이 줄어든다.
     *
     * <pre>
     *   판매 등록 30kg -> 잠금 30kg
     *   10kg 거래 완료 -> 잠금 20kg
     * </pre>
     *
     * <p>남은 수량을 전부 소비하면 양수인 잠금 이력 수량을 보존하고 CONSUMED 로 넘긴다.
     * {@code amount = 0} 은 상태와 관계없이 schema 의 CHECK 를 위반한다.
     */
    public void decreaseBy(long consumedAmount, LocalDateTime consumedAt) {
        requireActive("decrease");
        if (consumedAmount <= 0) {
            throw new IllegalArgumentException("Material lock decrease amount must be positive");
        }
        if (this.amount < consumedAmount) {
            throw new IllegalArgumentException(
                    "Material lock cannot decrease beyond its amount: amount=" + this.amount
                            + ", requested=" + consumedAmount);
        }
        if (this.amount == consumedAmount) {
            this.status = MaterialLockStatus.CONSUMED;
            this.releasedAt = requireTimestamp(consumedAt);
            return;
        }
        this.amount -= consumedAmount;
    }

    /** 만료 시각이 지났는지. 만료된 잠금은 available 계산에서 빠져야 한다. */
    public boolean isExpiredAt(LocalDateTime at) {
        return expiresAt != null && !expiresAt.isAfter(requireTimestamp(at));
    }

    private void requireActive(String action) {
        if (this.status != MaterialLockStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Material lock can only " + action + " while ACTIVE but was " + this.status);
        }
    }

    private static LocalDateTime requireTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Material lock timestamp must not be null");
        }
        return timestamp;
    }
}
