package com.ssafy.upcycle.trade.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.blockchain.service.ChainOrderStatusReader;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.exception.ErrorCode;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.TradeShipRequest;
import com.ssafy.upcycle.trade.dto.TradeShipResponse;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class,
        MaterialEventRecorder.class,
        TradeShipmentService.class,
        TradeShipmentServiceTests.FixedClockConfig.class})
class TradeShipmentServiceTests {

    @MockitoBean
    private TradeNotificationService businessNotifications;

    /**
     * 발송은 <b>체인에 발송이 남아 있어야</b> 통과한다. 이 슬라이스에는 RPC 가 없으므로
     * 목으로 둔다 — 스텁은 거래를 만드는 헬퍼가 세운다.
     */
    @MockitoBean
    private ChainOrderStatusReader chainOrders;

    private static final long SELLER_ID = 10L;
    private static final long BUYER_ID = 11L;
    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";

    @Autowired
    private TradeShipmentService tradeShipmentService;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private TradeShippingRepository shippingRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shippingMovesTheTradeAndRecordsTheWaybill() {
        MaterialTrade trade = paidTradeWithShipping();

        TradeShipResponse response = tradeShipmentService.ship(
                SELLER_ID, trade.getId(), new TradeShipRequest("CJ대한통운", "123456789012"));

        assertThat(response.status()).isEqualTo(TradeStatus.SHIPPED);
        assertThat(response.carrier()).isEqualTo("CJ대한통운");
        assertThat(response.trackingNumber()).isEqualTo("123456789012");
        assertThat(response.shippedAt()).isNotNull();
    }

    /** 거래와 배송 정보가 각자 shipped_at 을 갖는다. 두 값이 어긋나면 안 된다. */
    @Test
    void tradeAndShippingShareTheSameShippedTime() {
        MaterialTrade trade = paidTradeWithShipping();

        tradeShipmentService.ship(
                SELLER_ID, trade.getId(), new TradeShipRequest("CJ대한통운", "123456789012"));
        entityManager.flush();
        entityManager.clear();

        MaterialTrade reloaded = tradeRepository.findById(trade.getId()).orElseThrow();
        TradeShipping shipping = shippingRepository.findByTradeId(trade.getId()).orElseThrow();

        assertThat(reloaded.getShippedAt()).isEqualTo(shipping.getShippedAt());
    }

    /** 직접 수령·화물처럼 송장이 없는 전달도 있다. */
    @Test
    void waybillIsOptional() {
        MaterialTrade trade = paidTradeWithShipping();

        TradeShipResponse response = tradeShipmentService.ship(
                SELLER_ID, trade.getId(), new TradeShipRequest(null, null));

        assertThat(response.status()).isEqualTo(TradeStatus.SHIPPED);
        assertThat(response.trackingNumber()).isNull();
    }

    /** 택배사 없는 운송장 번호는 조회할 수 없어 기록해도 쓸모가 없다. */
    @Test
    void trackingNumberWithoutCarrierIsRejected() {
        MaterialTrade trade = paidTradeWithShipping();

        assertThatFails(
                () -> tradeShipmentService.ship(
                        SELLER_ID, trade.getId(), new TradeShipRequest(null, "123456789012")),
                TradeErrorCode.TRADE_CARRIER_REQUIRED);
    }

    @Test
    void unknownTradeIsRejected() {
        assertThatFails(
                () -> tradeShipmentService.ship(SELLER_ID, 9_999L, waybill()),
                TradeErrorCode.TRADE_NOT_FOUND);
    }

    /** 보내는 쪽은 판매자다. 구매자가 발송 처리하면 배송 사실이 조작된다. */
    @Test
    void onlyTheSellerCanShip() {
        MaterialTrade trade = paidTradeWithShipping();

        assertThatFails(
                () -> tradeShipmentService.ship(BUYER_ID, trade.getId(), waybill()),
                TradeErrorCode.NOT_TRADE_SELLER);
    }

    /** 결제 전 발송은 대금 없이 물건이 나가는 경로다. */
    @Test
    void unpaidTradeCannotShip() {
        MaterialTrade trade = tradeRepository.save(trade());
        shippingRepository.save(shipping(trade.getId()));
        entityManager.flush();

        assertThatFails(
                () -> tradeShipmentService.ship(SELLER_ID, trade.getId(), waybill()),
                TradeErrorCode.TRADE_NOT_SHIPPABLE);
    }

    @Test
    void alreadyShippedTradeIsRejected() {
        MaterialTrade trade = paidTradeWithShipping();
        tradeShipmentService.ship(SELLER_ID, trade.getId(), waybill());

        assertThatFails(
                () -> tradeShipmentService.ship(SELLER_ID, trade.getId(), waybill()),
                TradeErrorCode.TRADE_ALREADY_SHIPPED);
    }

    /**
     * 온체인 주문 없이 발송하면 서버만 앞서 나간다.
     *
     * <p>실제로 그렇게 새어 나갔다. 주문 서명을 건너뛴 거래가 발송·수령까지 끝나 DB 는
     * 소재를 넘겨줬는데 체인은 그 거래를 모르는 채로 굳었다. 온체인 기록은 {@code PAID}
     * 구간에서만 남길 수 있어 그 시점에는 되돌릴 방법이 없었고, 그 소재를 가공에 보낼 때
     * insufficient material 로 거절당해서야 드러났다.
     */
    /**
     * 체인에 발송이 남기 전에는 DB 발송이 기록되지 않는다.
     *
     * <p>서명을 끄면 DB 는 SHIPPED 인데 체인은 CREATED 로 남고, 그러면 구매자가 수령 서명을
     * 할 수 없다 — 컨트랙트가 체인이 SHIPPED 일 때만 수령을 받는다.
     */
    @Test
    void shippingBeforeTheChainSignatureIsRejected() {
        MaterialTrade trade = paidTradeWithShipping();
        when(chainOrders.alreadyShipped(anyLong())).thenReturn(false);

        assertThatFails(
                () -> tradeShipmentService.ship(SELLER_ID, trade.getId(), waybill()),
                TradeErrorCode.TRADE_CHAIN_SHIPMENT_REQUIRED);
    }

    @Test
    void shippingBeforeTheChainOrderIsRejected() {
        when(chainOrders.canRead()).thenReturn(true);
        MaterialTrade trade = tradeRepository.save(trade());
        trade.markPaid("T1234", 200_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        shippingRepository.save(shipping(trade.getId()));
        entityManager.flush();

        assertThatFails(
                () -> tradeShipmentService.ship(SELLER_ID, trade.getId(), waybill()),
                TradeErrorCode.TRADE_CHAIN_ORDER_REQUIRED);
    }

    @Test
    void tradeWithoutShippingAddressIsRejected() {
        when(chainOrders.canRead()).thenReturn(true);
        when(chainOrders.alreadyShipped(anyLong())).thenReturn(true);
        MaterialTrade trade = tradeRepository.save(trade());
        trade.markPaid("T1234", 200_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        trade.assignChainTradeId(42L);
        entityManager.flush();

        assertThatFails(
                () -> tradeShipmentService.ship(SELLER_ID, trade.getId(), waybill()),
                TradeErrorCode.TRADE_SHIPPING_NOT_FOUND);
    }

    private void assertThatFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()).isEqualTo(expected));
    }

    /**
     * 발송할 수 있는 거래.
     *
     * <p><b>온체인 주문까지 붙인다.</b> 컨트랙트의 {@code markShipped(orderId)} 는 그 번호
     * 없이는 부를 수도 없어서 발송의 선행 조건이다 — 없는 경우는
     * {@link #shippingBeforeTheChainOrderIsRejected} 가 따로 본다.
     */
    private MaterialTrade paidTradeWithShipping() {
        when(chainOrders.canRead()).thenReturn(true);
        when(chainOrders.alreadyShipped(anyLong())).thenReturn(true);
        MaterialTrade trade = tradeRepository.save(trade());
        trade.markPaid("T1234", 200_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        trade.assignChainTradeId(42L);
        shippingRepository.save(shipping(trade.getId()));
        entityManager.flush();
        return trade;
    }

    private MaterialTrade trade() {
        return MaterialTrade.create(
                "TRD-000001", 1L, 2L, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK, LocalDateTime.now().plusMinutes(30));
    }

    private TradeShipping shipping(Long tradeId) {
        return TradeShipping.of(
                tradeId, "김구매", "010-0000-0000", "04766", "서울시 성동구", "101동");
    }

    private TradeShipRequest waybill() {
        return new TradeShipRequest("CJ대한통운", "123456789012");
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        Clock clock() {
            // 운영과 같은 시계를 쓴다. systemUTC 를 두면 KST 로 만든 테스트 데이터와
            // 9시간 어긋나 여기서만 통과하는 테스트가 된다 (ClockConfig 참고).
            return Clock.systemDefaultZone();
        }
    }
}
