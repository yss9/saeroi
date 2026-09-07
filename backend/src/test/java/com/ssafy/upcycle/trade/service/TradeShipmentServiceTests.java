package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.exception.ErrorCode;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class,
        TradeShipmentService.class,
        TradeShipmentServiceTests.FixedClockConfig.class})
class TradeShipmentServiceTests {

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

    @Test
    void tradeWithoutShippingAddressIsRejected() {
        MaterialTrade trade = tradeRepository.save(trade());
        trade.markPaid("T1234", 200_000L, LocalDateTime.now());
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

    private MaterialTrade paidTradeWithShipping() {
        MaterialTrade trade = tradeRepository.save(trade());
        trade.markPaid("T1234", 200_000L, LocalDateTime.now());
        shippingRepository.save(shipping(trade.getId()));
        entityManager.flush();
        return trade;
    }

    private MaterialTrade trade() {
        return MaterialTrade.create(
                "TRD-000001", 1L, 2L, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK);
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
