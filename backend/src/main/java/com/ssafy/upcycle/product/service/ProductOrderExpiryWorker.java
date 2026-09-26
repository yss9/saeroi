package com.ssafy.upcycle.product.service;

import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 마감을 넘긴 미결제 주문을 정리하고 개체를 재고로 되돌린다.
 *
 * <p><b>화면이 취소를 불러 주지 못하는 경우를 위한 것이다.</b> 결제창에서 이탈하면 화면이
 * {@code cancel} 을 부르게 되어 있지만, 사용자가 탭을 그냥 닫거나 브라우저가 죽으면 그
 * 호출이 오지 않는다. 그러면 주문은 {@code CREATED} 로 남고 개체는 {@code RESERVED} 로
 * 묶인 채다.
 *
 * <p>제품 개체는 한 점씩이라 <b>한 건이 묶이면 그 제품이 바로 품절로 보인다.</b> 소재 거래는
 * 수량 일부가 묶이는 것이어서 티가 덜 나지만 여기서는 즉시 드러난다 — 이 배치가 없으면
 * 결제를 포기할 때마다 팔 수 있는 제품이 하나씩 사라진다.
 *
 * <p>주문마다 트랜잭션을 따로 연다. 한 건이 실패해도 나머지가 롤백되지 않아야 한다 —
 * {@code ProcessingRequestExpiryWorker} 와 같은 이유다.
 */
@Component
@ConditionalOnProperty(
        name = "app.product.order-expiry.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ProductOrderExpiryWorker {

    private final ProductOrderRepository orders;
    private final ProductOrderPaymentService paymentService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.product.order-expiry.interval-ms:60000}")
    public void expireUnpaidOrders() {
        for (ProductOrder order : orders.findExpiredUnpaid(LocalDateTime.now(clock))) {
            try {
                paymentService.expire(order.getId());
            } catch (RuntimeException exception) {
                log.error("Failed to expire product order {}", order.getId(), exception);
            }
        }
    }
}
