package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.exception.ErrorCode;
import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.trade.TradeErrorCode;
import com.ssafy.upcycle.trade.dto.TradeReceiptResponse;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeShipping;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.trade.repository.TradeShippingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
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
        TradeReceiptService.class,
        TradeReceiptServiceTests.ClockConfig.class})
class TradeReceiptServiceTests {

    private static final long MATERIAL_ID = 2L;
    private static final long SELLER_ID = 10L;
    private static final long BUYER_ID = 11L;
    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final long LISTING_QUANTITY = 30_000L;
    private static final long TRADE_QUANTITY = 20_000L;

    @Autowired
    private TradeReceiptService tradeReceiptService;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private MaterialListingRepository listingRepository;

    @Autowired
    private TradeShippingRepository shippingRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private TestEntityManager entityManager;

    private MaterialLock lock;
    private MaterialTrade trade;

    @BeforeEach
    void setUp() {
        lock = lockRepository.save(MaterialLock.create(
                MATERIAL_ID, SELLER_ID, LISTING_QUANTITY,
                MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, 1L, null));
        MaterialListing listing = listingRepository.save(MaterialListing.open(
                MATERIAL_ID, SELLER_ID, LISTING_QUANTITY, 10_000L, lock.getId()));
        balanceRepository.save(MaterialBalance.of(
                MATERIAL_ID, SELLER_ID, SELLER_WALLET, LISTING_QUANTITY));

        trade = tradeRepository.save(MaterialTrade.create(
                "TRD-000001", listing.getId(), MATERIAL_ID, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, TRADE_QUANTITY, 200_000L, PaymentMethod.MOCK));
        trade.markPaid("T1234", 200_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        trade.ship(LocalDateTime.now());
        shippingRepository.save(TradeShipping.of(
                        trade.getId(), "김구매", "010-0000-0000", "04766", "서울시 성동구", "101동"))
                .registerShipment("CJ대한통운", "123456789012", LocalDateTime.now());
        entityManager.flush();
    }

    @Test
    void receiptTransfersOwnershipAndCompletesTheTrade() {
        TradeReceiptResponse response = tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId());

        assertThat(response.status()).isEqualTo(TradeStatus.COMPLETED);
        assertThat(response.transferredQuantity()).isEqualTo(TRADE_QUANTITY);
        assertThat(response.buyerQuantity()).isEqualTo(TRADE_QUANTITY);
        assertThat(response.buyerSignedAt()).isNotNull();
    }

    /**
     * 판매자 차감은 응답이 아니라 저장된 값으로 확인한다. 응답에서 판매자 보유량을 뺐지만
     * 차감 자체는 여전히 일어나야 한다.
     */
    @Test
    void sellerBalanceIsReducedEvenThoughItIsNotReported() {
        tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(MATERIAL_ID, SELLER_ID))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(LISTING_QUANTITY - TRADE_QUANTITY);
    }

    /** 처음 사는 소재라 보유 행이 없다. 거래 시점 스냅샷 주소로 만들어져야 한다. */
    @Test
    void buyerBalanceIsCreatedWithTheTradeSnapshotWallet() {
        tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId());
        entityManager.flush();
        entityManager.clear();

        MaterialBalance buyerBalance = balanceRepository
                .findByMaterialIdAndOwnerId(MATERIAL_ID, BUYER_ID).orElseThrow();

        assertThat(buyerBalance.getWalletAddress()).isEqualTo(BUYER_WALLET);
        assertThat(buyerBalance.getQuantity()).isEqualTo(TRADE_QUANTITY);
    }

    /** 부분 거래라 잠금이 남는다. 남은 수량은 여전히 판매자가 다른 데 쓸 수 없다. */
    @Test
    void partialTradeLeavesTheLockActive() {
        tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId());
        entityManager.flush();
        entityManager.clear();

        MaterialLock reloaded = lockRepository.findById(lock.getId()).orElseThrow();

        assertThat(reloaded.getAmount()).isEqualTo(LISTING_QUANTITY - TRADE_QUANTITY);
        assertThat(reloaded.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
    }

    @Test
    void consumingTheWholeLockClosesIt() {
        MaterialTrade wholeTrade = tradeRepository.save(MaterialTrade.create(
                "TRD-000002", trade.getListingId(), MATERIAL_ID, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, LISTING_QUANTITY, 300_000L, PaymentMethod.MOCK));
        wholeTrade.markPaid("T9999", 300_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        wholeTrade.ship(LocalDateTime.now());
        shippingRepository.save(TradeShipping.of(
                        wholeTrade.getId(), "김구매", "010-0000-0000", "04766", "서울시 성동구", null))
                .registerShipment(null, null, LocalDateTime.now());
        entityManager.flush();

        tradeReceiptService.confirmReceipt(BUYER_ID, wholeTrade.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(lockRepository.findById(lock.getId()).orElseThrow().getStatus())
                .isEqualTo(MaterialLockStatus.CONSUMED);
    }

    @Test
    void deliveryTimeIsRecordedOnTheShipping() {
        tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(shippingRepository.findByTradeId(trade.getId()).orElseThrow().getDeliveredAt())
                .isNotNull();
    }

    /** 받는 쪽은 구매자다. 판매자가 대신 확인하면 2-Track 이 성립하지 않는다. */
    @Test
    void onlyTheBuyerCanConfirm() {
        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(SELLER_ID, trade.getId()),
                TradeErrorCode.NOT_TRADE_BUYER);
    }

    @Test
    void unknownTradeIsRejected() {
        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(BUYER_ID, 9_999L),
                TradeErrorCode.TRADE_NOT_FOUND);
    }

    /** 배송하지 않은 건을 수령 확인하면 소유권이 근거 없이 넘어간다. */
    @Test
    void unshippedTradeCannotBeConfirmed() {
        MaterialTrade paidOnly = tradeRepository.save(MaterialTrade.create(
                "TRD-000003", trade.getListingId(), MATERIAL_ID, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, 1_000L, 10_000L, PaymentMethod.MOCK));
        paidOnly.markPaid("T5555", 10_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        entityManager.flush();

        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(BUYER_ID, paidOnly.getId()),
                TradeErrorCode.TRADE_NOT_RECEIVABLE);
    }

    /** 두 번 확인하면 거래 수량의 두 배가 이전된다. */
    @Test
    void confirmingTwiceIsRejected() {
        tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId());

        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId()),
                TradeErrorCode.TRADE_ALREADY_RECEIVED);
    }

    /** 판매까지 간 소재에 보유 내역이 없는 것은 데이터가 어긋난 상태다. */
    @Test
    void missingSellerBalanceIsRejected() {
        balanceRepository.deleteAll();
        entityManager.flush();

        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId()),
                TradeErrorCode.SELLER_BALANCE_NOT_FOUND);
    }

    @Test
    void releasedLockIsRejected() {
        lock.release(LocalDateTime.now());
        entityManager.flush();

        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(BUYER_ID, trade.getId()),
                TradeErrorCode.TRADE_LOCK_NOT_ACTIVE);
    }

    /** 보유량은 남아 있는데 잠금이 모자란 경우. 어느 쪽이든 부족하면 이전하지 않는다. */
    @Test
    void insufficientLockAmountIsRejected() {
        MaterialLock smallLock = lockRepository.save(MaterialLock.create(
                MATERIAL_ID, SELLER_ID, 1_000L,
                MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, 2L, null));
        MaterialListing smallListing = listingRepository.save(MaterialListing.open(
                MATERIAL_ID, SELLER_ID, 1_000L, 10_000L, smallLock.getId()));
        MaterialTrade oversized = tradeRepository.save(MaterialTrade.create(
                "TRD-000004", smallListing.getId(), MATERIAL_ID, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, 5_000L, 50_000L, PaymentMethod.MOCK));
        oversized.markPaid("T7777", 50_000L, LocalDateTime.now(), PaymentMethod.MOCK);
        oversized.ship(LocalDateTime.now());
        shippingRepository.save(TradeShipping.of(
                        oversized.getId(), "김구매", "010-0000-0000", "04766", "서울시 성동구", null))
                .registerShipment(null, null, LocalDateTime.now());
        entityManager.flush();

        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(BUYER_ID, oversized.getId()),
                TradeErrorCode.TRADE_LOCK_AMOUNT_INSUFFICIENT);
    }

    /** 실패했으면 아무것도 옮기지 않아야 한다. */
    @Test
    void failedConfirmationLeavesBalancesUntouched() {
        assertThatFails(
                () -> tradeReceiptService.confirmReceipt(SELLER_ID, trade.getId()),
                TradeErrorCode.NOT_TRADE_BUYER);

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(MATERIAL_ID, SELLER_ID))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(LISTING_QUANTITY);
        assertThat(balanceRepository.findByMaterialIdAndOwnerId(MATERIAL_ID, BUYER_ID)).isEmpty();
    }

    private void assertThatFails(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()).isEqualTo(expected));
    }

    @TestConfiguration
    static class ClockConfig {

        @Bean
        Clock clock() {
            // 운영과 같은 시계를 쓴다. systemUTC 를 두면 KST 로 만든 테스트 데이터와
            // 9시간 어긋나 여기서만 통과하는 테스트가 된다 (ClockConfig 참고).
            return Clock.systemDefaultZone();
        }
    }
}
