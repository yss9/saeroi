package com.ssafy.upcycle.trade.repository;

import com.ssafy.upcycle.trade.entity.TradeShipping;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 거래당 배송 정보는 한 건이므로({@code trade_id} UNIQUE) 조회도 단건이다.
 */
public interface TradeShippingRepository extends JpaRepository<TradeShipping, Long> {

    Optional<TradeShipping> findByTradeId(Long tradeId);
}
