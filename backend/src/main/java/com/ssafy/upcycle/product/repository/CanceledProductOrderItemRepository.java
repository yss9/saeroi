package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.CanceledProductOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

public interface CanceledProductOrderItemRepository
        extends JpaRepository<CanceledProductOrderItem, Long> {

    List<CanceledProductOrderItem> findAllByCanceledProductOrderId(Long canceledProductOrderId);

    /**
     * 이 개체가 취소된 이력.
     *
     * <p>UNIQUE 가 아니라 목록을 돌려준다 — 취소된 개체는 다시 팔리고 또 취소될 수 있어서
     * 같은 개체가 여러 번 들어온다.
     */
    List<CanceledProductOrderItem> findAllByProductItemId(Long productItemId);

    @Query(value = """
            SELECT COUNT(*) FROM canceled_product_order_item item
            JOIN canceled_product_order orders ON orders.id = item.canceled_product_order_id
            WHERE item.product_model_id = :productModelId
              AND orders.original_paid_at >= :startsAt AND orders.original_paid_at < :endsAt
            """, nativeQuery = true)
    long countOriginallyPaidItems(
            @Param("productModelId") Long productModelId,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt);

    @Query(value = """
            SELECT COUNT(*) FROM canceled_product_order_item item
            JOIN canceled_product_order orders ON orders.id = item.canceled_product_order_id
            WHERE item.product_model_id = :productModelId
              AND orders.original_paid_at >= :startsAt AND orders.original_paid_at < :endsAt
              AND orders.canceled_at < :endsAt
            """, nativeQuery = true)
    long countCanceledPaidItemsAsOf(
            @Param("productModelId") Long productModelId,
            @Param("startsAt") LocalDateTime startsAt,
            @Param("endsAt") LocalDateTime endsAt);
}
