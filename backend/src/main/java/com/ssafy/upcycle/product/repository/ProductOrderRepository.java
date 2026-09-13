package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 조회 경로는 schema.sql 의 인덱스를 따른다 —
 * {@code (buyer_id, status)} 와 {@code (brand_id, status)} 다.
 */
public interface ProductOrderRepository extends JpaRepository<ProductOrder, Long> {

    Optional<ProductOrder> findByOrderCode(String orderCode);

    List<ProductOrder> findAllByBuyerIdAndStatus(Long buyerId, ProductOrderStatus status);

    List<ProductOrder> findAllByBrandIdAndStatus(Long brandId, ProductOrderStatus status);

    Page<ProductOrder> findAllByBuyerId(Long buyerId, Pageable pageable);

    Page<ProductOrder> findAllByBuyerIdAndStatus(
            Long buyerId, ProductOrderStatus status, Pageable pageable);

    long countByBuyerIdAndStatus(Long buyerId, ProductOrderStatus status);

    /**
     * 결제 마감을 넘긴 미결제 주문. 배치가 이 목록을 정리하며 잡아 둔 개체를 재고로 되돌린다.
     *
     * <p>이 정리가 없으면 결제창을 닫은 주문마다 개체가 영구히 {@code RESERVED} 로 묶여
     * 재고가 조용히 줄어든다.
     */
    @Query("""
            select o from ProductOrder o
            where o.status = com.ssafy.upcycle.product.entity.ProductOrderStatus.CREATED
              and o.paymentDeadline is not null
              and o.paymentDeadline <= :at
            """)
    List<ProductOrder> findExpiredUnpaid(@Param("at") LocalDateTime at);

    /**
     * 취소·결제처럼 개체 상태를 함께 바꾸는 경로에서 쓴다.
     *
     * <p>같은 주문에 취소가 두 번 들어오면 둘 다 취소 가능 상태를 읽고 각자 이력을 만들며
     * 개체를 재고로 되돌린다. 상태 검사만으로는 두 트랜잭션이 같은 값을 보는 것을 막지 못한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProductOrder o where o.id = :id")
    Optional<ProductOrder> findByIdForUpdate(@Param("id") Long id);
}
