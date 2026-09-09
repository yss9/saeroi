package com.ssafy.upcycle.production.repository;

import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/**
 * 조회 경로는 schema.sql 이 선언한 인덱스를 따른다 —
 * {@code (producer_id, status)} 와 {@code product_model_id} 다.
 */
public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    Optional<ProductionBatch> findByBatchCode(String batchCode);

    List<ProductionBatch> findAllByProducerIdAndStatus(
            Long producerId, ProductionBatchStatus status);

    List<ProductionBatch> findAllByProducerId(Long producerId);

    /** 한 제품 모델의 생산 이력. 확정된 배치만 세면 그 모델의 총 생산량이 나온다. */
    List<ProductionBatch> findAllByProductModelId(Long productModelId);

    /**
     * 확정 처리용 조회. <b>비관적 잠금이 필요한 이유가 분명하다.</b>
     *
     * <p>확정은 소재를 소비하고 개체를 만들고 온체인 기록까지 남기는 되돌릴 수 없는 행위다.
     * 두 요청이 동시에 들어오면 상태 검사를 둘 다 통과한 뒤 각자 개체를 만들어 <b>재고가
     * 두 배</b>가 된다. 상태 컬럼만으로는 그 창을 막을 수 없다 — 읽고 쓰는 사이가 열려 있다.
     *
     * <p>거래 수령 확인이 같은 이유로 같은 잠금을 쓴다
     * ({@code MaterialTradeRepository.findByIdForUpdate}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from ProductionBatch b where b.id = :id")
    Optional<ProductionBatch> findByIdForUpdate(Long id);
}
