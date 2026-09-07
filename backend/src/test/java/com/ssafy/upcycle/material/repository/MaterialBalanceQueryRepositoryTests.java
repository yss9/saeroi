package com.ssafy.upcycle.material.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.common.jpa.config.QuerydslConfig;
import com.ssafy.upcycle.material.dto.MaterialBalanceResponse;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 잔량 집계. 여기서 지키는 계약은 <b>어떤 잠금을 빼는가</b> 하나다.
 *
 * <p>{@code RELEASED}·{@code CONSUMED} 까지 빼면 이미 끝난 거래가 사용 가능 수량을
 * 영구히 깎는다. 그건 화면에 "팔 수 있는 양이 줄었다" 로만 보이고 원인이 드러나지 않는
 * 조용한 버그라, 상태별 케이스를 하나씩 고정한다.
 *
 * <p>{@code @DataJpaTest} 는 롤백되지만 전역 {@code findAll()} 대신 특정 소재·보유자를
 * 단언한다 ({@code .harness/parts/backend.md} 경고).
 */
@DataJpaTest
@Import({QuerydslConfig.class, JpaAuditingConfig.class, MaterialBalanceQueryRepository.class})
class MaterialBalanceQueryRepositoryTests {

    private static final String OWNER_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String OTHER_ADDRESS = "0x2222222222222222222222222222222222222222";
    private static final long OWNER_ID = 1_001L;
    private static final long OTHER_OWNER_ID = 1_002L;

    @Autowired
    private MaterialBalanceQueryRepository queryRepository;

    @Autowired
    private EntityManager entityManager;

    private Long materialId;

    @BeforeEach
    void setUp() {
        materialId = persistMaterial("MAT-000001", "폐데님 원단").getId();
    }

    // ── available 계산 ───────────────────────────────────────

    @Test
    void availableEqualsQuantityWhenNothingIsLocked() {
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)))
                .satisfies(row -> {
                    assertThat(row.quantity()).isEqualTo(1_000L);
                    assertThat(row.lockedQuantity()).isZero();
                    assertThat(row.available()).isEqualTo(1_000L);
                });
    }

    @Test
    void subtractsActiveLocks() {
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        persistLock(materialId, OWNER_ID, 300L);
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)).available())
                .isEqualTo(700L);
    }

    @Test
    void sumsMultipleActiveLocks() {
        // 잠금이 2건 이상일 때 보유자 행이 불어나면 안 된다 (JOIN 폭발).
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        persistLock(materialId, OWNER_ID, 200L);
        persistLock(materialId, OWNER_ID, 200L);
        flush();

        List<MaterialBalanceResponse> rows = queryRepository.findBalancesByMaterialId(materialId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).available()).isEqualTo(600L);
    }

    @Test
    void doesNotSubtractReleasedLocks() {
        // RELEASED 는 이미 풀린 잠금이다. 빼면 사용 가능 수량이 영구히 줄어든다.
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        MaterialLock active = persistLock(materialId, OWNER_ID, 300L);
        MaterialLock released = persistLock(materialId, OWNER_ID, 200L);
        released.release(LocalDateTime.now());
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)).available())
                .isEqualTo(700L);
        assertThat(active.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void doesNotSubtractConsumedLocks() {
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        MaterialLock consumed = persistLock(materialId, OWNER_ID, 400L);
        consumed.consume(LocalDateTime.now());
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)).available())
                .isEqualTo(1_000L);
    }

    @Test
    void subtractsAnExpiredActiveLockOnPurpose() {
        // expires_at 이 지난 ACTIVE 도 그대로 뺀다. EXPIRED 로 전환해 주는 배치가 없어서다.
        // 과소 계산이 초과 판매보다 안전하다 — 이 단언이 그 결정을 고정한다.
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        persistLock(materialId, OWNER_ID, 300L, LocalDateTime.now().minusDays(1));
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)).available())
                .isEqualTo(700L);
    }

    @Test
    void doesNotBorrowLocksFromAnotherOwner() {
        // 상관 서브쿼리의 owner_id 조건이 빠지면 남의 잠금이 내 available 을 깎는다.
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        persistBalance(materialId, OTHER_OWNER_ID, OTHER_ADDRESS, 500L);
        persistLock(materialId, OTHER_OWNER_ID, 400L);
        flush();

        MaterialBalanceResponse mine = queryRepository.findBalancesByMaterialId(materialId).stream()
                .filter(row -> row.ownerId().equals(OWNER_ID))
                .findFirst()
                .orElseThrow();

        assertThat(mine.lockedQuantity()).isZero();
        assertThat(mine.available()).isEqualTo(1_000L);
    }

    @Test
    void doesNotBorrowLocksFromAnotherMaterial() {
        Material other = persistMaterial("MAT-000002", "다른 소재");
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        persistLock(other.getId(), OWNER_ID, 400L);
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)).lockedQuantity())
                .isZero();
    }

    // ── 목록 형태 ────────────────────────────────────────────

    @Test
    void keepsOwnersWithoutLocksInTheList() {
        // LEFT JOIN 대신 INNER JOIN 을 쓰면 잠금 없는 보유자가 목록에서 사라진다.
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        persistBalance(materialId, OTHER_OWNER_ID, OTHER_ADDRESS, 500L);
        persistLock(materialId, OWNER_ID, 100L);
        flush();

        assertThat(queryRepository.findBalancesByMaterialId(materialId))
                .extracting(MaterialBalanceResponse::ownerId)
                .containsExactlyInAnyOrder(OWNER_ID, OTHER_OWNER_ID);
    }

    @Test
    void returnsAnEmptyListWhenNobodyHoldsTheMaterial() {
        flush();

        assertThat(queryRepository.findBalancesByMaterialId(materialId)).isEmpty();
    }

    @Test
    void includesTheSyncTimestamp() {
        // 시각이 없으면 "보유자 없음" 과 "동기화 전" 을 구분할 수 없다.
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 1_000L);
        flush();

        assertThat(only(queryRepository.findBalancesByMaterialId(materialId)).syncedAt()).isNotNull();
    }

    // ── 내 보유 소재 ──────────────────────────────────────────

    @Test
    void myMaterialsCarryBothTotalAndOwnedQuantity() {
        // totalQuantity(소재 총 발행량)와 quantity(내 보유량)를 섞으면
        // "1200kg 을 갖고 있다" 는 거짓이 된다.
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 300L);
        persistLock(materialId, OWNER_ID, 100L);
        flush();

        List<MyMaterialResponse> rows = queryRepository.findMyMaterials(OWNER_ID);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).satisfies(row -> {
            assertThat(row.materialId()).isEqualTo(materialId);
            assertThat(row.materialCode()).isEqualTo("MAT-000001");
            assertThat(row.totalQuantity()).isEqualTo(1_000L);
            assertThat(row.quantity()).isEqualTo(300L);
            assertThat(row.lockedQuantity()).isEqualTo(100L);
            assertThat(row.available()).isEqualTo(200L);
        });
    }

    @Test
    void myMaterialsExcludeOtherPeoplesHoldings() {
        persistBalance(materialId, OTHER_OWNER_ID, OTHER_ADDRESS, 500L);
        flush();

        assertThat(queryRepository.findMyMaterials(OWNER_ID)).isEmpty();
    }

    @Test
    void myMaterialsListEveryMaterialIHold() {
        Material second = persistMaterial("MAT-000002", "폐면 원단");
        persistBalance(materialId, OWNER_ID, OWNER_ADDRESS, 300L);
        persistBalance(second.getId(), OWNER_ID, OWNER_ADDRESS, 700L);
        flush();

        assertThat(queryRepository.findMyMaterials(OWNER_ID))
                .extracting(MyMaterialResponse::materialCode)
                .containsExactlyInAnyOrder("MAT-000001", "MAT-000002");
    }

    // ── helpers ─────────────────────────────────────────────

    private Material persistMaterial(String code, String name) {
        Material material = Material.register(
                code,
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                name,
                null,
                "A",
                1_000L,
                null,
                null,
                9_001L);
        entityManager.persist(material);
        return material;
    }

    private void persistBalance(Long material, Long ownerId, String address, long quantity) {
        entityManager.persist(MaterialBalance.of(material, ownerId, address, quantity));
    }

    private MaterialLock persistLock(Long material, Long ownerId, long amount) {
        return persistLock(material, ownerId, amount, null);
    }

    private MaterialLock persistLock(
            Long material, Long ownerId, long amount, LocalDateTime expiresAt) {
        MaterialLock lock = MaterialLock.create(
                material,
                ownerId,
                amount,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                7_001L,
                expiresAt);
        entityManager.persist(lock);
        return lock;
    }

    /** 쿼리가 도는 것을 보려면 flush 가 필요하다 — persist 만으로는 SQL 이 나가지 않는다. */
    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private static MaterialBalanceResponse only(List<MaterialBalanceResponse> rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}
