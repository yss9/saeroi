package com.ssafy.upcycle.trade.service;

import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 마감을 넘긴 미결제 소재 거래를 정리하고 판매 글 수량을 되돌린다.
 *
 * <p><b>화면이 취소를 불러 주지 못하는 경우를 위한 것이다.</b> 결제창에서 이탈하면
 * {@code PaymentReturnPage} 가 {@code cancel} 을 부르게 되어 있지만, 결제창까지 가지 않고
 * 탭을 닫거나 브라우저가 죽으면 그 호출이 오지 않는다. 그러면 거래는 {@code CREATED} 로
 * 남고 판매 글의 잔여 수량은 깎인 채다.
 *
 * <p><b>수량은 결제가 아니라 구매 신청 시점에 깎인다</b>({@code TradeCommandService.create}
 * 의 {@code listing.reduceAvailable}). 그래서 결제를 포기할 때마다 팔 수 있는 수량이
 * 조용히 줄고, 남은 양을 전부 사려던 거래였다면 판매 글이 그대로 품절로 보인다.
 *
 * <p>거래마다 트랜잭션을 따로 연다. 한 건이 실패해도 나머지가 롤백되지 않아야 한다 —
 * {@link com.ssafy.upcycle.product.service.ProductOrderExpiryWorker} 와 같은 이유다.
 *
 * <p><b>체인의 예약은 여기서 풀지 못한다.</b> {@code Marketplace.cancelOrder} 가 당사자
 * 서명을 요구해 서버가 대신할 수 없다. 서버 수량만 돌아오고 체인에는 {@code
 * reservedQuantity} 가 남으므로, 구매자가 거래 상세에서 직접 해제해야 한다.
 */
@Component
@ConditionalOnProperty(
        name = "app.trade.payment-expiry.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class MaterialTradeExpiryWorker {

    private final MaterialTradeRepository trades;
    private final PaymentService paymentService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.trade.payment-expiry.interval-ms:60000}")
    public void expireUnpaidTrades() {
        for (MaterialTrade trade : trades.findExpiredUnpaid(LocalDateTime.now(clock))) {
            try {
                paymentService.expire(trade.getId());
            } catch (RuntimeException exception) {
                log.error("Failed to expire material trade {}", trade.getId(), exception);
            }
        }
    }
}
