package com.ssafy.upcycle.trade.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.trade.entity.ListingStatus;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * FK 무결성은 여기서 검증되지 않는다. 참조를 연관관계가 아닌 값으로 매핑했으므로
 * Hibernate 가 테스트 스키마에 FK 제약을 만들지 않는다. 실제 MySQL 에는 FK 가 있다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class TradeRepositoryTests {

    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 4, 12, 0);

    @Autowired
    private MaterialListingRepository listingRepository;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private TradeShippingRepository shippingRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void listingIsPersistedWithCreatedAt() {
        MaterialListing saved = listingRepository.save(
                MaterialListing.open(1L, 10L, 30_000L, 10_000L, 100L));
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ListingStatus.ON_SALE);
    }

    @Test
    void sellerListsOwnOnSaleListings() {
        listingRepository.save(MaterialListing.open(1L, 10L, 30_000L, 10_000L, 100L));
        listingRepository.save(MaterialListing.open(2L, 10L, 20_000L, 12_000L, 101L));
        listingRepository.save(MaterialListing.open(3L, 11L, 10_000L, 9_000L, 102L));
        entityManager.flush();
        entityManager.clear();

        assertThat(listingRepository.findAllBySellerIdAndStatus(10L, ListingStatus.ON_SALE))
                .hasSize(2);
    }

    @Test
    void tradeIsFoundByCode() {
        tradeRepository.save(trade("TRD-000001", 11L));
        entityManager.flush();
        entityManager.clear();

        assertThat(tradeRepository.findByTradeCode("TRD-000001"))
                .get()
                .extracting(MaterialTrade::getQuantity)
                .isEqualTo(20_000L);
    }

    @Test
    void tradesAreListedFromBothSides() {
        tradeRepository.save(trade("TRD-000001", 11L));
        tradeRepository.save(trade("TRD-000002", 12L));
        entityManager.flush();
        entityManager.clear();

        assertThat(tradeRepository.findAllBySellerIdAndStatus(10L, TradeStatus.CREATED))
                .hasSize(2);
        assertThat(tradeRepository.findAllByBuyerIdAndStatus(11L, TradeStatus.CREATED))
                .hasSize(1);
    }

    /** 상태 전이가 그대로 저장되는지 — 발송·수령·확정을 한 번에 태워 본다. */
    @Test
    void stateTransitionsArePersisted() {
        MaterialTrade saved = tradeRepository.save(trade("TRD-000001", 11L));
        saved.markPaid("T1234", 200_000L, NOW);
        saved.ship(NOW.plusDays(1));
        saved.confirmReceipt(NOW.plusDays(3), NOW.plusDays(3));
        saved.complete(NOW.plusDays(3));
        entityManager.flush();
        entityManager.clear();

        MaterialTrade reloaded = tradeRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getStatus()).isEqualTo(TradeStatus.COMPLETED);
        assertThat(reloaded.getBuyerSignedAt()).isEqualTo(NOW.plusDays(3));
        assertThat(reloaded.getPaymentReference()).isEqualTo("T1234");
    }

    @Test
    void shippingIsFoundByTrade() {
        MaterialTrade saved = tradeRepository.save(trade("TRD-000001", 11L));
        entityManager.flush();

        TradeShipping shipping = shippingRepository.save(TradeShipping.of(
                saved.getId(), "김구매", "010-0000-0000", "04766", "서울시 성동구", "101동"));
        shipping.registerShipment("CJ대한통운", "123456789012", NOW);
        entityManager.flush();
        entityManager.clear();

        assertThat(shippingRepository.findByTradeId(saved.getId()))
                .get()
                .extracting(TradeShipping::getTrackingNumber)
                .isEqualTo("123456789012");
    }

    private MaterialTrade trade(String tradeCode, Long buyerId) {
        return MaterialTrade.create(
                tradeCode,
                1L,
                2L,
                10L,
                buyerId,
                SELLER_WALLET,
                BUYER_WALLET,
                20_000L,
                200_000L,
                PaymentMethod.MOCK);
    }
}
