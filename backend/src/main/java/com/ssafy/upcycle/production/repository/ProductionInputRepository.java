package com.ssafy.upcycle.production.repository;

import com.ssafy.upcycle.production.entity.ProductionInput;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 투입 조회. schema.sql 의 UNIQUE {@code (production_batch_id, material_id)} 와
 * INDEX {@code material_id} 를 그대로 따른다.
 *
 * <p>{@link #findAllByMaterialId} 가 <b>Journey 역추적의 진입점</b>이다 — "이 소재가 어떤
 * 제품이 됐나" 를 여기서 거슬러 올라간다. ERD 가 그 인덱스를 둔 이유가 이 조회다.
 */
public interface ProductionInputRepository extends JpaRepository<ProductionInput, Long> {

    List<ProductionInput> findAllByProductionBatchId(Long productionBatchId);

    /** 소재가 어떤 배치들에 들어갔는가. QR 이력이 소재까지 거슬러 올라갈 때 쓴다. */
    List<ProductionInput> findAllByMaterialId(Long materialId);

    /**
     * 같은 소재를 한 배치에 두 번 넣을 수 없다 (UNIQUE 제약).
     *
     * <p>DB 가 막아 주지만 그건 {@code DataIntegrityViolationException} 이라 사용자에게
     * 설명할 수 없다. 투입 서비스가 이 조회로 먼저 확인해 읽을 수 있는 오류를 만든다.
     */
    Optional<ProductionInput> findByProductionBatchIdAndMaterialId(
            Long productionBatchId, Long materialId);

    boolean existsByProductionBatchIdAndMaterialId(Long productionBatchId, Long materialId);
}
