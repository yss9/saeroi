package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 개체 조회. {@code (product_model_id, status)} 인덱스가 재고 계산을 받친다.
 *
 * <p><b>재고는 여기서 센다.</b> product_model 에 재고 컬럼이 없으므로
 * {@link #countByProductModelIdAndStatus} 가 "몇 개 살 수 있는가" 의 유일한 답이다.
 */
public interface ProductItemRepository extends JpaRepository<ProductItem, Long> {

    /**
     * QR 이력 조회의 진입점. 인증 없이 열리는 경로라 토큰 자체가 접근 통제다.
     */
    Optional<ProductItem> findByQrToken(String qrToken);

    Optional<ProductItem> findByItemCode(String itemCode);

    /** 모델의 재고. 컬럼으로 저장하지 않고 세는 것이 이 도메인의 규칙이다. */
    long countByProductModelIdAndStatus(Long productModelId, ProductItemStatus status);

    List<ProductItem> findAllByProductModelIdAndStatus(
            Long productModelId, ProductItemStatus status);

    /** 생산 배치가 만들어 낸 개체들. 배치 확정 결과를 되짚을 때 쓴다. */
    List<ProductItem> findAllByProductionBatchId(Long productionBatchId);
}
