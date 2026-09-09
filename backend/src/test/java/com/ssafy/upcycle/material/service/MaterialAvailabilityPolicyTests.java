package com.ssafy.upcycle.material.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 잔여량 규칙 자체. <b>이 프로젝트에서 이 판정은 한 곳뿐이어야 한다.</b>
 *
 * <p>정본은 {@code .harness/reference/02-domain-rules.md} 의 Lock 상태 표다. 여기서 고정하는
 * 것은 그 표의 각 줄이고, 표가 바뀌면 이 테스트가 먼저 깨져야 한다.
 */
class MaterialAvailabilityPolicyTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 12, 0);

    @Test
    void nothingLockedMeansEverythingAvailable() {
        assertThat(MaterialAvailabilityPolicy.available(100_000L, List.of(), NOW))
                .isEqualTo(100_000L);
    }

    @Test
    void activeLocksAreSubtracted() {
        assertThat(MaterialAvailabilityPolicy.available(
                        100_000L, List.of(activeLock(30_000L), activeLock(20_000L)), NOW))
                .isEqualTo(50_000L);
    }

    @Test
    void releasedLocksAreNotSubtracted() {
        // 취소·실패·반려로 풀린 잠금이다. 수량은 이미 돌아왔다.
        MaterialLock released = activeLock(30_000L);
        released.release(NOW);

        assertThat(MaterialAvailabilityPolicy.available(100_000L, List.of(released), NOW))
                .isEqualTo(100_000L);
    }

    @Test
    void consumedLocksAreNotSubtracted() {
        // 소비된 잠금은 보유량 자체가 이미 줄어 있다. 여기서 또 빼면 두 번 깎인다.
        MaterialLock consumed = activeLock(30_000L);
        consumed.consume(NOW);

        assertThat(MaterialAvailabilityPolicy.available(70_000L, List.of(consumed), NOW))
                .isEqualTo(70_000L);
    }

    @Test
    void expiredLocksAreNotSubtractedEvenWhileStillActive() {
        // 규칙의 핵심. 상태를 EXPIRED 로 바꾸는 배치가 아직 없어 기한이 지난 잠금이
        // DB 에는 ACTIVE 로 남아 있다. 그걸 세면 이미 풀린 수량을 영영 쓰지 못한다.
        MaterialLock expired = lockExpiringAt(30_000L, NOW.minusMinutes(1));

        assertThat(MaterialAvailabilityPolicy.available(100_000L, List.of(expired), NOW))
                .isEqualTo(100_000L);
    }

    @Test
    void aLockExpiringExactlyNowIsAlreadyExpired() {
        // isExpiredAt 은 "expires_at 이 지금보다 뒤가 아니면 만료" 다. 경계를 고정한다.
        assertThat(MaterialAvailabilityPolicy.available(
                        100_000L, List.of(lockExpiringAt(30_000L, NOW)), NOW))
                .isEqualTo(100_000L);
        assertThat(MaterialAvailabilityPolicy.available(
                        100_000L, List.of(lockExpiringAt(30_000L, NOW.plusSeconds(1))), NOW))
                .isEqualTo(70_000L);
    }

    @Test
    void aLockWithoutAnExpiryNeverExpires() {
        // 생산 투입 잠금이 그렇다. 기한이 지나 풀리면 확정 시점에 소재가 사라진 배치가 된다.
        assertThat(MaterialAvailabilityPolicy.available(100_000L, List.of(activeLock(30_000L)), NOW))
                .isEqualTo(70_000L);
    }

    @Test
    void everyPurposeCountsTheSame() {
        // 판매용이든 가공용이든 생산용이든 잠긴 수량은 다른 곳에 쓸 수 없다.
        // 목적별로 예외를 두면 같은 수량을 두 곳에 걸 수 있게 된다.
        List<MaterialLock> locks = List.of(
                lockFor(10_000L, MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING),
                lockFor(20_000L, MaterialLockPurpose.PROCESSING,
                        MaterialLockReferenceType.PROCESSING_JOB),
                lockFor(30_000L, MaterialLockPurpose.PRODUCTION,
                        MaterialLockReferenceType.PRODUCTION_BATCH));

        assertThat(MaterialAvailabilityPolicy.available(100_000L, locks, NOW)).isEqualTo(40_000L);
    }

    @Test
    void overLockingShowsUpAsANegativeNumber() {
        // 0 으로 접으면 잔액 동기화가 어긋났다는 신호가 사라진다.
        // GET /api/materials/me 도 같은 값을 음수로 노출한다.
        assertThat(MaterialAvailabilityPolicy.available(10_000L, List.of(activeLock(30_000L)), NOW))
                .isEqualTo(-20_000L);
    }

    @Test
    void aNullLockListIsTreatedAsNoLocks() {
        assertThat(MaterialAvailabilityPolicy.available(100_000L, null, NOW)).isEqualTo(100_000L);
    }

    // ── helpers ─────────────────────────────────────────────

    private MaterialLock activeLock(long amount) {
        return lockFor(amount, MaterialLockPurpose.PRODUCTION,
                MaterialLockReferenceType.PRODUCTION_BATCH);
    }

    private MaterialLock lockFor(
            long amount, MaterialLockPurpose purpose, MaterialLockReferenceType referenceType) {
        return MaterialLock.create(1L, 2L, amount, purpose, referenceType, 3L, null);
    }

    private MaterialLock lockExpiringAt(long amount, LocalDateTime expiresAt) {
        return MaterialLock.create(
                1L,
                2L,
                amount,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                3L,
                expiresAt);
    }
}
