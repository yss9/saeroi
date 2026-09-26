package com.ssafy.upcycle.product.repository;

import com.ssafy.upcycle.product.entity.ProductOrderGroup;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 주문 묶음 조회.
 *
 * <p>결제와 취소가 이 행을 기준으로 돌기 때문에 <b>잠그고 읽는 경로</b>를 함께 둔다 —
 * 주문이 {@code findByIdForUpdate} 를 갖는 것과 같은 이유다. 결제창에서 뒤로 가기로 복귀
 * URL 을 두 번 열면 승인 요청이 실제로 두 번 들어온다.
 */
public interface ProductOrderGroupRepository extends JpaRepository<ProductOrderGroup, Long> {

    Optional<ProductOrderGroup> findByGroupCode(String groupCode);

    List<ProductOrderGroup> findAllByBuyerId(Long buyerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from ProductOrderGroup g where g.id = :id")
    Optional<ProductOrderGroup> findByIdForUpdate(@Param("id") Long id);
}
