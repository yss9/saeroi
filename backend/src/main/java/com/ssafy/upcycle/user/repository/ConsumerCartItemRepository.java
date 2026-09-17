package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.ConsumerCartItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장바구니 조회·삭제.
 *
 * <p><b>모든 메서드가 {@code userId} 를 함께 받는다.</b> 항목 id 만으로 찾는 경로를 열어
 * 두면 남의 장바구니를 고치는 요청이 만들어질 수 있고, 그 검사를 서비스가 빠뜨리면 조용히
 * 통과한다. 조건에 넣어 두면 그런 요청이 아무것도 건드리지 못한다 — 찜과 같은 방식이다.
 *
 * <p>{@code uk_consumer_cart_item_user_product} 가 목록 범위 스캔과 항목 조회·삭제를 모두
 * 받친다. 항목 수가 상한(50)으로 묶여 있어 정렬 전용 인덱스는 두지 않았다.
 */
public interface ConsumerCartItemRepository extends JpaRepository<ConsumerCartItem, Long> {

    /**
     * 내 장바구니 전부. <b>담은 순서대로</b>.
     *
     * <p>{@code id} 하나로 정렬한다. PK 라 동률이 없고 AUTO_INCREMENT 라 담은 순서와 정확히
     * 일치해서, 정렬 키가 하나뿐인데도 순서가 흔들리지 않는다. 수량을 바꿔도 id 는 그대로라
     * 항목이 제자리에 남는다 — 검토하며 수량을 만지는 화면에서 그 고정이 중요하다.
     *
     * <p>페이지를 나누지 않는다. 장바구니는 합계를 보여 주는 화면이라 어차피 전부 필요하고,
     * 응답 크기는 항목 수 상한이 묶는다.
     */
    @Query("select item from ConsumerCartItem item "
            + "where item.userId = :userId order by item.id asc")
    List<ConsumerCartItem> findMine(@Param("userId") Long userId);

    Optional<ConsumerCartItem> findByUserIdAndProductModelId(Long userId, Long productModelId);

    long countByUserId(Long userId);

    long deleteByUserIdAndProductModelId(Long userId, Long productModelId);

    /**
     * 결제된 항목만 비운다.
     *
     * <p>사지 못한 항목과 고르지 않은 항목은 남아야 한다 — 사용자가 직접 담은 목록이라
     * 주문과 무관한 것까지 사라지면 무엇을 담았는지 다시 찾아야 한다.
     */
    long deleteByUserIdAndProductModelIdIn(Long userId, Collection<Long> productModelIds);

    long deleteByUserId(Long userId);
}
