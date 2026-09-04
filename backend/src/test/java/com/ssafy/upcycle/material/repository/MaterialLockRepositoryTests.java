package com.ssafy.upcycle.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class MaterialLockRepositoryTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);

    @Autowired
    private MaterialLockRepository materialLockRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistedLockGetsCreatedAt() {
        MaterialLock saved = materialLockRepository.save(lock(30_000L, 1L));
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
    }

    @Test
    void activeLocksAreListedForAvailableCalculation() {
        materialLockRepository.save(lock(30_000L, 1L));
        materialLockRepository.save(lock(10_000L, 2L));

        MaterialLock released = materialLockRepository.save(lock(5_000L, 3L));
        released.release(NOW);

        entityManager.flush();
        entityManager.clear();

        List<MaterialLock> active = materialLockRepository
                .findAllByMaterialIdAndOwnerIdAndStatus(1L, 10L, MaterialLockStatus.ACTIVE);

        assertThat(active)
                .hasSize(2)
                .extracting(MaterialLock::getAmount)
                .containsExactlyInAnyOrder(30_000L, 10_000L);
    }

    @Test
    void locksAreFoundByTheDomainThatCreatedThem() {
        materialLockRepository.save(lock(30_000L, 42L));
        entityManager.flush();
        entityManager.clear();

        assertThat(materialLockRepository.findAllByReferenceTypeAndReferenceId(
                        MaterialLockReferenceType.LISTING, 42L))
                .hasSize(1);
        assertThat(materialLockRepository.findAllByReferenceTypeAndReferenceId(
                        MaterialLockReferenceType.PROCESSING_JOB, 42L))
                .isEmpty();
    }

    @Test
    void stateTransitionsArePersisted() {
        MaterialLock saved = materialLockRepository.save(lock(30_000L, 1L));
        saved.decreaseBy(30_000L, NOW);
        entityManager.flush();
        entityManager.clear();

        MaterialLock reloaded = materialLockRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(MaterialLockStatus.CONSUMED);
        assertThat(reloaded.getAmount()).isEqualTo(30_000L);
        assertThat(reloaded.getReleasedAt()).isEqualTo(NOW);
    }

    private MaterialLock lock(long amount, Long referenceId) {
        return MaterialLock.create(
                1L,
                10L,
                amount,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                referenceId,
                null);
    }
}
