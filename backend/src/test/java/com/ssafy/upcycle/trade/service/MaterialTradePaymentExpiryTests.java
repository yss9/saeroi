package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.common.jpa.config.JpaAuditingConfig;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.entity.TradeStatus;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 결제 마감을 넘긴 거래를 찾아내는 조회.
 *
 * <p><b>수량은 결제가 아니라 구매 신청 시점에 깎인다</b>({@code listing.reduceAvailable}).
 * 되돌리는 경로가 사람이 누르는 취소뿐이라, 결제창까지 가지 않고 이탈하면 그 수량이
 * 영원히 잠긴다. 이 조회가 배치의 입력이고, 여기서 한 건이라도 빠지면 그 판매 글은
 * 계속 품절로 보인다.
 *
 * <p>반대로 <b>넘치게 잡으면 더 나쁘다</b> — 결제를 마친 거래를 취소하면 돈은 나갔는데
 * 거래가 없는 상태가 된다. 그래서 상태와 마감을 모두 본다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class MaterialTradePaymentExpiryTests {

    private static final long MATERIAL_ID = 2L;
    private static final long SELLER_ID = 10L;
    private static final long BUYER_ID = 11L;
    private static final String SELLER_WALLET = "0x1111111111111111111111111111111111111111";
    private static final String BUYER_WALLET = "0x2222222222222222222222222222222222222222";
    private static final long LISTING_QUANTITY = 30_000L;
    private static final long TRADE_QUANTITY = 20_000L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 12, 0);

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private MaterialListingRepository listingRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private TestEntityManager entityManager;

    private MaterialListing listing;

    @BeforeEach
    void setUp() {
        MaterialLock lock = lockRepository.save(MaterialLock.create(
                MATERIAL_ID, SELLER_ID, LISTING_QUANTITY,
                MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, 1L, null));
        listing = listingRepository.save(MaterialListing.open(
                MATERIAL_ID, SELLER_ID, LISTING_QUANTITY, 10_000L, lock.getId()));
    }

    @Test
    void 마감을_넘긴_미결제_거래를_찾는다() {
        MaterialTrade expired = save("TRD-000001", NOW.minusMinutes(1));

        assertThat(tradeRepository.findExpiredUnpaid(NOW))
                .extracting(MaterialTrade::getId)
                .containsExactly(expired.getId());
    }

    @Test
    void 마감_정각도_지난_것으로_본다() {
        // 경계를 배제하면 배치 주기(60초) 사이에 정확히 걸린 거래가 다음 주기까지 밀린다.
        MaterialTrade onTime = save("TRD-000002", NOW);

        assertThat(tradeRepository.findExpiredUnpaid(NOW))
                .extracting(MaterialTrade::getId)
                .containsExactly(onTime.getId());
    }

    @Test
    void 아직_마감_전이면_건드리지_않는다() {
        save("TRD-000003", NOW.plusMinutes(1));

        assertThat(tradeRepository.findExpiredUnpaid(NOW)).isEmpty();
    }

    /**
     * 결제를 마친 거래는 마감이 지났어도 대상이 아니다.
     *
     * <p>여기서 잡히면 배치가 결제된 거래를 취소한다 — 돈은 나갔는데 거래가 사라지는,
     * 이 도메인에서 가장 나쁜 상태다.
     */
    @Test
    void 결제를_마친_거래는_마감이_지나도_대상이_아니다() {
        MaterialTrade paid = save("TRD-000004", NOW.minusHours(1));
        paid.markPaid("T1234", 200_000L, NOW.minusMinutes(30), PaymentMethod.MOCK);
        entityManager.flush();

        assertThat(tradeRepository.findExpiredUnpaid(NOW)).isEmpty();
    }

    /**
     * 마감이 비어 있는 행은 건드리지 않는다.
     *
     * <p>이 컬럼은 나중에 생겼다. 마이그레이션이 기존 CREATED 행에 마감을 소급해 넣지만,
     * 그 뒤에도 어떤 경로로든 비어 있는 행이 생기면 <b>기한을 모르는 거래를 취소하게
     * 된다</b> — 방금 만들어진 거래일 수도 있다.
     */
    @Test
    void 마감이_비어_있으면_건드리지_않는다() {
        MaterialTrade trade = save("TRD-000005", NOW.minusHours(1));
        entityManager.flush();
        entityManager.getEntityManager()
                .createQuery("update MaterialTrade t set t.paymentDeadline = null where t.id = :id")
                .setParameter("id", trade.getId())
                .executeUpdate();
        entityManager.clear();

        assertThat(tradeRepository.findExpiredUnpaid(NOW)).isEmpty();
    }

    @Test
    void 만료_판정은_상태와_마감을_함께_본다() {
        MaterialTrade trade = save("TRD-000006", NOW.minusMinutes(1));

        assertThat(trade.isPaymentExpiredAt(NOW)).isTrue();
        assertThat(trade.isPaymentExpiredAt(NOW.minusMinutes(2))).isFalse();

        trade.markPaid("T1234", 200_000L, NOW, PaymentMethod.MOCK);
        assertThat(trade.getStatus()).isEqualTo(TradeStatus.PAID);
        assertThat(trade.isPaymentExpiredAt(NOW)).isFalse();
    }

    private MaterialTrade save(String code, LocalDateTime deadline) {
        return tradeRepository.save(MaterialTrade.create(
                code, listing.getId(), MATERIAL_ID, SELLER_ID, BUYER_ID,
                SELLER_WALLET, BUYER_WALLET, TRADE_QUANTITY, 200_000L,
                PaymentMethod.MOCK, deadline));
    }
}
