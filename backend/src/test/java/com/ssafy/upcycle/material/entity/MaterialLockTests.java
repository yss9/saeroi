package com.ssafy.upcycle.material.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MaterialLockTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);

    @Test
    void lockStartsActive() {
        MaterialLock lock = marketplaceLock(30_000L);

        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
        assertThat(lock.getAmount()).isEqualTo(30_000L);
        assertThat(lock.getReleasedAt()).isNull();
    }

    @Test
    void activeLockCannotBeCreatedWithZeroAmount() {
        assertThatThrownBy(() -> marketplaceLock(0L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> marketplaceLock(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseMovesToReleasedAndStampsTheTime() {
        MaterialLock lock = marketplaceLock(30_000L);

        lock.release(NOW);

        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.RELEASED);
        assertThat(lock.getReleasedAt()).isEqualTo(NOW);
        assertThat(lock.getAmount()).isEqualTo(30_000L);
    }

    @Test
    void consumeMovesToConsumed() {
        MaterialLock lock = marketplaceLock(30_000L);

        lock.consume(NOW);

        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.CONSUMED);
        assertThat(lock.getReleasedAt()).isEqualTo(NOW);
    }

    @Test
    void onlyActiveLocksCanTransition() {
        MaterialLock released = marketplaceLock(30_000L);
        released.release(NOW);

        assertThatThrownBy(() -> released.consume(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> released.release(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> released.decreaseBy(1_000L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void partialTradeReducesTheLockedAmount() {
        MaterialLock lock = marketplaceLock(30_000L);

        lock.decreaseBy(10_000L, NOW);

        assertThat(lock.getAmount()).isEqualTo(20_000L);
        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
        assertThat(lock.getReleasedAt()).isNull();
    }

    @Test
    void reachingZeroFlipsTheLockToConsumed() {
        MaterialLock lock = marketplaceLock(30_000L);

        lock.decreaseBy(10_000L, NOW);
        lock.decreaseBy(20_000L, NOW);

        assertThat(lock.getAmount()).isZero();
        assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.CONSUMED);
        assertThat(lock.getReleasedAt()).isEqualTo(NOW);
    }

    @Test
    void cannotDecreaseBeyondTheLockedAmount() {
        MaterialLock lock = marketplaceLock(30_000L);

        assertThatThrownBy(() -> lock.decreaseBy(30_001L, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(lock.getAmount()).isEqualTo(30_000L);
    }

    @Test
    void expiryIsOptionalAndComparedAgainstTheGivenTime() {
        MaterialLock withoutExpiry = marketplaceLock(30_000L);
        assertThat(withoutExpiry.isExpiredAt(NOW)).isFalse();

        MaterialLock expiring = MaterialLock.create(
                1L,
                2L,
                30_000L,
                MaterialLockPurpose.PROCESSING,
                MaterialLockReferenceType.PROCESSING_JOB,
                7L,
                NOW);

        assertThat(expiring.isExpiredAt(NOW.minusSeconds(1))).isFalse();
        assertThat(expiring.isExpiredAt(NOW)).isTrue();
        assertThat(expiring.isExpiredAt(NOW.plusSeconds(1))).isTrue();
    }

    private MaterialLock marketplaceLock(long amount) {
        return MaterialLock.create(
                1L,
                2L,
                amount,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                3L,
                null);
    }
}
