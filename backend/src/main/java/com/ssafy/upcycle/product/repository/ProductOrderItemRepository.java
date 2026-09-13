package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductOrderItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 주문 항목 조회.
 *
 * <p>{@link #findByProductItemId} 가 동시 구매 방어의 조회 쪽이다 — 이미 팔린 개체인지
 * 미리 확인한다. 실제로 막는 것은 {@code product_item_id} 의 UNIQUE 제약이고, 이 조회는
 * 사용자에게 결제 전에 알려주기 위한 것이다.
 */
public interface ProductOrderItemRepository extends JpaRepository<ProductOrderItem, Long> {

    List<ProductOrderItem> findAllByProductOrderId(Long productOrderId);

    List<ProductOrderItem> findAllByProductOrderIdInOrderByIdAsc(Collection<Long> productOrderIds);

    Optional<ProductOrderItem> findByProductItemId(Long productItemId);

    /** 주문 취소 시 원본을 지우는 경로. 지운 기록은 취소 이력 테이블이 받는다. */
    void deleteAllByProductOrderId(Long productOrderId);
}
