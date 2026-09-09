package com.ssafy.upcycle.production.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.entity.ProductionOutput;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * FK 무결성은 여기서 검증되지 않는다. 참조를 연관관계가 아닌 값으로 매핑했으므로
 * Hibernate 가 테스트 스키마에 FK 제약을 만들지 않는다. 실제 MySQL 에는 FK 가 있다.
 *
 * <p>UNIQUE 제약은 다르다 — {@code @Column(unique = true)} 와 {@code @Table} 의
 * {@code uniqueConstraints} 는 Hibernate 가 실제로 만들어 주므로 여기서 확인할 수 있고,
 * 그게 이 클래스가 잡는 사고다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class ProductionRepositoryTests {

    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long PRODUCT_MODEL_ID = 100L;
    private static final long PRODUCER_ID = 200L;

    @Autowired
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProductionInputRepository inputRepository;

    @Autowired
    private ProductionOutputRepository outputRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void batchIsFoundByItsCode() {
        batchRepository.save(batch("PB-000001"));
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRepository.findByBatchCode("PB-000001"))
                .get()
                .extracting(ProductionBatch::getBatchName)
                .isEqualTo("RE:BAG 1차 생산");
    }

    @Test
    void batchCodeIsUnique() {
        batchRepository.save(batch("PB-000001"));
        entityManager.flush();

        // 같은 코드가 둘이면 그 코드로 남긴 온체인 기록이 어느 배치인지 알 수 없다.
        // id 가 IDENTITY 라 save() 가 INSERT 를 즉시 실행한다 — 예외가 flush 가 아니라
        // save 에서 난다. 둘 다 감싸 두면 전략이 바뀌어도 이 테스트는 계속 맞는다.
        assertThatThrownBy(() -> {
                    batchRepository.save(batch("PB-000001"));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    void batchesAreListedByProducerAndStatus() {
        batchRepository.save(batch("PB-000001"));
        ProductionBatch signing = batchRepository.save(batch("PB-000002"));
        signing.startSigning();
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRepository.findAllByProducerIdAndStatus(
                        PRODUCER_ID, ProductionBatchStatus.DRAFT))
                .extracting(ProductionBatch::getBatchCode)
                .containsExactly("PB-000001");
        assertThat(batchRepository.findAllByProducerId(PRODUCER_ID)).hasSize(2);
        assertThat(batchRepository.findAllByProductModelId(PRODUCT_MODEL_ID)).hasSize(2);
    }

    @Test
    void batchIsReadableForUpdate() {
        // 확정은 되돌릴 수 없는 행위라 두 요청이 동시에 통과하면 개체가 두 배로 만들어진다.
        Long batchId = batchRepository.save(batch("PB-000001")).getId();
        entityManager.flush();
        entityManager.clear();

        assertThat(batchRepository.findByIdForUpdate(batchId)).isPresent();
    }

    // ── 투입 ────────────────────────────────────────────────

    @Test
    void theSameMaterialCannotBeFedTwiceIntoOneBatch() {
        // UNIQUE (production_batch_id, material_id). 두 번 넣으면 같은 소재를
        // 두 번 소비한 것이 되고 잔량이 실제보다 더 줄어든다.
        Long batchId = batchRepository.save(batch("PB-000001")).getId();
        inputRepository.save(input(batchId, 500L, 10_000L));
        entityManager.flush();

        assertThatThrownBy(() -> {
                    inputRepository.save(input(batchId, 500L, 3_000L));
                    entityManager.flush();
                })
                .isInstanceOf(Exception.class);
    }

    @Test
    void inputsAreFoundBothWays() {
        Long batchId = batchRepository.save(batch("PB-000001")).getId();
        inputRepository.save(input(batchId, 500L, 10_000L));
        inputRepository.save(input(batchId, 501L, 4_000L));
        entityManager.flush();
        entityManager.clear();

        assertThat(inputRepository.findAllByProductionBatchId(batchId)).hasSize(2);
        // Journey 역추적의 진입점 — 이 소재가 어떤 제품이 됐는가.
        assertThat(inputRepository.findAllByMaterialId(500L))
                .singleElement()
                .extracting(ProductionInput::getInputQuantity)
                .isEqualTo(10_000L);
        assertThat(inputRepository.existsByProductionBatchIdAndMaterialId(batchId, 500L)).isTrue();
        assertThat(inputRepository.existsByProductionBatchIdAndMaterialId(batchId, 999L)).isFalse();
    }

    // ── 산출 ────────────────────────────────────────────────

    @Test
    void outputsHangOffTheirInput() {
        Long batchId = batchRepository.save(batch("PB-000001")).getId();
        Long inputId = inputRepository.save(input(batchId, 500L, 10_000L)).getId();
        outputRepository.save(ProductionOutput.expect(inputId, 2_000L));
        entityManager.flush();
        entityManager.clear();

        assertThat(outputRepository.findAllByProductionInputId(inputId))
                .singleElement()
                .extracting(ProductionOutput::getQuantity)
                .isEqualTo(2_000L);
        assertThat(outputRepository.findAllByProductionInputIdIn(List.of(inputId))).hasSize(1);
    }

    @Test
    void issuedScrapMaterialIsTraceableBack() {
        Long batchId = batchRepository.save(batch("PB-000001")).getId();
        Long inputId = inputRepository.save(input(batchId, 500L, 10_000L)).getId();
        ProductionOutput output = outputRepository.save(ProductionOutput.expect(inputId, 2_000L));
        output.linkNewMaterial(777L);
        entityManager.flush();
        entityManager.clear();

        assertThat(outputRepository.findAllByNewMaterialId(777L)).hasSize(1);
        // 확정 전 산출은 소재가 없다. null 은 오류가 아니라 상태다.
        assertThat(outputRepository.findAllByNewMaterialId(778L)).isEmpty();
    }

    private ProductionBatch batch(String code) {
        return ProductionBatch.open(
                code, "RE:BAG 1차 생산", PRODUCT_MODEL_ID, 50, PRODUCER_ID, WALLET);
    }

    private ProductionInput input(Long batchId, Long materialId, long inputQuantity) {
        return ProductionInput.record(
                batchId, materialId, inputQuantity, inputQuantity, 0L, 0L, null, materialId);
    }
}
