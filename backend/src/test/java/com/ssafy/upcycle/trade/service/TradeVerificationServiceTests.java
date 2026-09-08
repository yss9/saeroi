package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, TradeVerificationService.class})
class TradeVerificationServiceTests {

    private static final long MATERIAL_ID = 2L;
    private static final long OTHER_MATERIAL_ID = 3L;
    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 5, 12, 0);

    @Autowired
    private TradeVerificationService tradeVerificationService;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void materialWithNoTradeIsNotVerified() {
        assertThat(tradeVerificationService.hasBuyerSignedTrade(MATERIAL_ID)).isFalse();
    }

    /** 거래가 있어도 서명 전이면 검증된 것이 아니다. 배송 중인 거래가 여기 해당한다. */
    @Test
    void shippedButUnsignedTradeIsNotVerified() {
        MaterialTrade trade = tradeRepository.save(trade("TRD-000001", MATERIAL_ID));
        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        trade.ship(NOW);
        entityManager.flush();

        assertThat(tradeVerificationService.hasBuyerSignedTrade(MATERIAL_ID)).isFalse();
    }

    @Test
    void signedTradeVerifiesTheMaterial() {
        MaterialTrade trade = tradeRepository.save(trade("TRD-000001", MATERIAL_ID));
        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        trade.ship(NOW);
        trade.confirmReceipt(NOW, NOW);
        entityManager.flush();

        assertThat(tradeVerificationService.hasBuyerSignedTrade(MATERIAL_ID)).isTrue();
    }

    /** 다른 소재의 서명이 이 소재를 검증해주면 안 된다. */
    @Test
    void anotherMaterialsSignedTradeDoesNotLeakOver() {
        MaterialTrade trade = tradeRepository.save(trade("TRD-000001", OTHER_MATERIAL_ID));
        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        trade.ship(NOW);
        trade.confirmReceipt(NOW, NOW);
        entityManager.flush();

        assertThat(tradeVerificationService.hasBuyerSignedTrade(MATERIAL_ID)).isFalse();
        assertThat(tradeVerificationService.hasBuyerSignedTrade(OTHER_MATERIAL_ID)).isTrue();
    }

    /** 서명된 거래가 하나라도 있으면 된다. 이후 거래가 서명 전이어도 검증은 유지된다. */
    @Test
    void oneSignedTradeIsEnough() {
        MaterialTrade signed = tradeRepository.save(trade("TRD-000001", MATERIAL_ID));
        signed.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        signed.ship(NOW);
        signed.confirmReceipt(NOW, NOW);

        tradeRepository.save(trade("TRD-000002", MATERIAL_ID));
        entityManager.flush();

        assertThat(tradeVerificationService.hasBuyerSignedTrade(MATERIAL_ID)).isTrue();
    }

    @Test
    void nullMaterialIdIsNotVerified() {
        assertThat(tradeVerificationService.hasBuyerSignedTrade(null)).isFalse();
    }

    private MaterialTrade trade(String tradeCode, long materialId) {
        return MaterialTrade.create(
                tradeCode, 1L, materialId, 10L, 11L,
                SELLER_WALLET, BUYER_WALLET, 20_000L, 200_000L, PaymentMethod.MOCK);
    }
}
