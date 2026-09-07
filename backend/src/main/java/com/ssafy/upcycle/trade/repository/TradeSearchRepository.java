package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 내 거래 목록·상세 조회.
 *
 * <p>{@link MaterialTradeRepository} 와 나눈 이유는 {@link ListingSearchQueryRepository} 와 같다 —
 * 그쪽은 인덱스에 대응하는 단순 조회, 여기는 화면이 필요로 하는 값을 모아 오는 조인 쿼리다.
 *
 * <p><b>거래 한 줄을 보여주려면 네 테이블이 필요하다.</b> 거래 자체(수량·금액·상태),
 * 소재(이름·코드), 판매 글(kg 단가), 그리고 상대방 이름(users). 거래만 읽고 나머지를
 * 행마다 다시 조회하면 목록 하나에 쿼리가 수십 개 나간다.
 *
 * <p>{@code material_listing} 을 조인해 단가를 가져오는 것이 안전한 이유는 <b>판매 글의
 * 단가가 수정되지 않기 때문</b>이다. 수정 API 가 없고(등록/취소만 있다) 그것이 의도된
 * 설계라, 지금 읽은 단가는 거래 시점의 단가와 같다. 수정이 생기면 이 값은 더 이상 과거를
 * 말하지 않으므로 {@code material_trade} 에 단가 컬럼을 추가해야 한다.
 */
public interface TradeSearchRepository extends Repository<MaterialTrade, Long> {

    /**
     * 내가 판 거래. {@code (seller_id, status)} 인덱스를 탄다.
     *
     * <p>{@code status} 가 {@code null} 이면 전체다. 판매/구매를 한 메서드로 합치지 않은
     * 이유는 조건이 {@code seller_id} 냐 {@code buyer_id} 냐로 갈려서, 하나로 만들면
     * {@code (:side = 'SELL' and t.sellerId = :userId) or ...} 같은 조건이 되어 두 인덱스를
     * 모두 못 타기 때문이다.
     *
     * @return {@code [MaterialTrade, Material, MaterialListing, 상대방 User, TradeShipping]}
     *         순서가 계약이다. 배송은 {@code left join} 이라 마지막 자리가 {@code null} 일 수 있다
     */
    @Query(value = """
            select t, m, l, buyer, s
            from MaterialTrade t
            join Material m on m.id = t.materialId
            join MaterialListing l on l.id = t.listingId
            join User buyer on buyer.id = t.buyerId
            left join TradeShipping s on s.tradeId = t.id
            where t.sellerId = :userId
              and (:status is null or t.status = :status)
            order by t.createdAt desc, t.id desc
            """,
            countQuery = """
            select count(t)
            from MaterialTrade t
            where t.sellerId = :userId
              and (:status is null or t.status = :status)
            """)
    Page<Object[]> findSoldBy(
            @Param("userId") Long userId,
            @Param("status") TradeStatus status,
            Pageable pageable);

    /** 내가 산 거래. {@code (buyer_id, status)} 인덱스를 탄다. 상대방은 판매자다. */
    @Query(value = """
            select t, m, l, seller, s
            from MaterialTrade t
            join Material m on m.id = t.materialId
            join MaterialListing l on l.id = t.listingId
            join User seller on seller.id = t.sellerId
            left join TradeShipping s on s.tradeId = t.id
            where t.buyerId = :userId
              and (:status is null or t.status = :status)
            order by t.createdAt desc, t.id desc
            """,
            countQuery = """
            select count(t)
            from MaterialTrade t
            where t.buyerId = :userId
              and (:status is null or t.status = :status)
            """)
    Page<Object[]> findBoughtBy(
            @Param("userId") Long userId,
            @Param("status") TradeStatus status,
            Pageable pageable);

    /**
     * 거래 단건을 화면에 필요한 것과 함께.
     *
     * <p>여기서는 양쪽 이름이 모두 필요하므로 {@code users} 를 두 번 조인한다 — 목록과 달리
     * 상세는 "누가 누구에게" 를 다 보여준다.
     *
     * @return {@code [MaterialTrade, Material, MaterialListing, 판매자, 구매자, TradeShipping]}
     */
    @Query("""
            select t, m, l, seller, buyer, s
            from MaterialTrade t
            join Material m on m.id = t.materialId
            join MaterialListing l on l.id = t.listingId
            join User seller on seller.id = t.sellerId
            join User buyer on buyer.id = t.buyerId
            left join TradeShipping s on s.tradeId = t.id
            where t.id = :tradeId
            """)
    List<Object[]> findDetailById(@Param("tradeId") Long tradeId);
}
