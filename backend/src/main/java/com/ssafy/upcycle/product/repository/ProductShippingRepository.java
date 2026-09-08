package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductShipping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 주문당 배송 정보는 한 건이므로({@code product_order_id} UNIQUE) 조회도 단건이다. */
public interface ProductShippingRepository extends JpaRepository<ProductShipping, Long> {

    Optional<ProductShipping> findByProductOrderId(Long productOrderId);

    /** 주문이 지워질 때 함께 지운다. 배송지는 취소 이력에 남기지 않는다 — 개인정보다. */
    void deleteByProductOrderId(Long productOrderId);
}
