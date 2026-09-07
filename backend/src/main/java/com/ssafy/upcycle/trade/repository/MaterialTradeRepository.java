package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 거래 목록은 판매자·구매자 양쪽에서 조회된다. schema.sql 이
 * {@code (seller_id, status)} 와 {@code (buyer_id, status)} 인덱스를 각각 두고 있는 이유다.
 */
public interface MaterialTradeRepository extends JpaRepository<MaterialTrade, Long> {

    Optional<MaterialTrade> findByTradeCode(String tradeCode);

    List<MaterialTrade> findAllBySellerIdAndStatus(Long sellerId, TradeStatus status);

    List<MaterialTrade> findAllByBuyerIdAndStatus(Long buyerId, TradeStatus status);

    List<MaterialTrade> findAllByListingId(Long listingId);
}
