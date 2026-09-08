package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.CanceledProductOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
