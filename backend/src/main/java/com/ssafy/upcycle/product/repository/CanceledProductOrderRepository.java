package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.CanceledProductOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 취소 주문 이력 조회. schema.sql 의 인덱스가 {@code buyer_id} 와 {@code brand_id} 다.
 *
 * <p>구매자는 자기 취소 내역을, 브랜드는 자기 제품의 취소를 본다.
 */
public interface CanceledProductOrderRepository extends JpaRepository<CanceledProductOrder, Long> {

    List<CanceledProductOrder> findAllByBuyerId(Long buyerId);

    List<CanceledProductOrder> findAllByBrandId(Long brandId);

    /** 원본 주문이 실제로 취소됐는지 확인한다. 원본 행은 이미 지워져 여기서만 알 수 있다. */
    Optional<CanceledProductOrder> findByOriginalOrderId(Long originalOrderId);
}
