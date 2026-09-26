package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 거래 목록은 판매자·구매자 양쪽에서 조회된다. schema.sql 이
 * {@code (seller_id, status)} 와 {@code (buyer_id, status)} 인덱스를 각각 두고 있는 이유다.
 */
public interface MaterialTradeRepository extends JpaRepository<MaterialTrade, Long> {

    Optional<MaterialTrade> findByTradeCode(String tradeCode);

    List<MaterialTrade> findAllBySellerIdAndStatus(Long sellerId, TradeStatus status);

    List<MaterialTrade> findAllByBuyerIdAndStatus(Long buyerId, TradeStatus status);

    List<MaterialTrade> findAllByListingId(Long listingId);

    /** 공개 Journey 가 현재 제품 브랜드로 들어온 소재의 마지막 이동을 찾을 때 쓴다. */
    Optional<MaterialTrade>
            findFirstByMaterialIdAndBuyerIdAndStatusOrderByReceivedAtDesc(
                    Long materialId, Long buyerId, TradeStatus status);

    /** 공개 Journey가 후보 소재의 완료 거래를 한 번에 불러와 실제 연결만 골라 낸다. */
    List<MaterialTrade> findAllByMaterialIdInAndStatus(
            Collection<Long> materialIds, TradeStatus status);

    /**
     * 이 소재에 구매자가 서명한 거래가 있는지. 소재의 검증 단계 판정에 쓴다.
     *
     * <p>거래를 다 읽어와 자바에서 거르지 않고 존재 여부만 묻는다. 한 소재에 거래가 수백
     * 건 쌓여도 비용이 같고, 판정에 필요한 것은 "하나라도 있는가" 뿐이다.
     */
    boolean existsByMaterialIdAndBuyerSignedAtIsNotNull(Long materialId);

    /**
     * 수령 확인처럼 보유량을 옮기는 경로에서 쓴다.
     *
     * <p>같은 거래에 수령 확인이 동시에 두 번 들어오면, 둘 다 SHIPPED 를 읽고 각자
     * 보유량을 옮겨 <b>거래 수량의 두 배가 이전된다.</b> 상태 검사만으로는 두 트랜잭션이
     * 같은 값을 보는 것을 막지 못하므로 행 잠금으로 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trade from MaterialTrade trade where trade.id = :id")
    Optional<MaterialTrade> findByIdForUpdate(@Param("id") Long id);

    /**
     * 결제 마감을 넘긴 미결제 거래. 배치가 이 목록을 취소하며 판매 글 수량을 되돌린다.
     *
     * <p>이 정리가 없으면 결제창까지 가지 않고 이탈한 거래마다 수량이 영구히 잠긴다 —
     * 남은 양을 전부 사려던 거래였다면 판매 글이 그대로 품절로 보인다.
     *
     * <p>{@code payment_deadline is not null} 을 함께 보는 이유는 이 컬럼이 나중에
     * 생겼기 때문이다. 마이그레이션이 기존 CREATED 행에 마감을 소급해 넣지만, 그 뒤에도
     * 어떤 경로로든 비어 있는 행이 생기면 배치가 건드리지 않아야 한다.
     */
    @Query("""
            select trade from MaterialTrade trade
            where trade.status = com.ssafy.upcycle.trade.entity.TradeStatus.CREATED
              and trade.paymentDeadline is not null
              and trade.paymentDeadline <= :at
            """)
    List<MaterialTrade> findExpiredUnpaid(@Param("at") LocalDateTime at);
}
