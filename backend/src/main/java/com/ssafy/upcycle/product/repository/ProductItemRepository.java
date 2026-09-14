package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 주문이 가져갈 개체 하나를 <b>잠근 채</b> 고른다. 없으면 품절이다.
     *
     * <p>{@code PESSIMISTIC_WRITE} 가 이 메서드의 존재 이유다. 잠그지 않으면 마지막 한 점을
     * 두 요청이 동시에 읽어 둘 다 {@code IN_STOCK} 으로 보고, 한쪽이
     * {@code uk_product_order_item_product} 위반으로 터진다 — 그것도 주문 행을 만든 뒤에.
     * {@code ProductItem#reserve} 의 상태 검사도 같은 이유로 이 잠금 없이는 통과해 버린다.
     *
     * <p>가장 오래된 개체부터 나간다. 같은 모델의 개체는 서로 구별되지 않으므로 어느 것을
     * 주든 상관없지만, 순서를 정해 두지 않으면 재고가 한쪽에 오래 남는다.
     *
     * <p>{@code Pageable} 로 한 건만 읽는다 — {@code findFirst...} 파생 메서드는 잠금 힌트와
     * 함께 쓸 때 구현체마다 동작이 갈려서, 쿼리를 눈에 보이게 적었다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from ProductItem item "
            + "where item.productModelId = :productModelId and item.status = :status "
            + "order by item.id asc")
    List<ProductItem> lockAvailable(
            @Param("productModelId") Long productModelId,
            @Param("status") ProductItemStatus status,
            Pageable pageable);
}
