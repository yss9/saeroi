package com.ssafy.upcycle.trade.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.notification.repository.UserNotificationRepository;
import com.ssafy.upcycle.trade.dto.ListingRegisterRequest;
import com.ssafy.upcycle.trade.dto.TradeCreateRequest;
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 같은 판매글을 여러 구매자가 <b>동시에</b> 살 때 초과 판매가 일어나지 않는지.
 *
 * <p>행 잠금이 없으면 둘 다 "12kg 남았다" 를 읽고 각자 검사를 통과한 뒤 각자 차감해서
 * 잔여가 음수가 된다. {@code chk_material_listing_quantities} 위반으로 <b>나중 한 건만</b>
 * 설명할 수 없는 500 이 되고, 사용자에게는 아무 설명이 안 된다.
 * {@code ListingLockRepository#findByIdForUpdate} 가 그것을 막는다.
 *
 * <p><b>{@code MarketplaceApiTests} 와 분리한 이유.</b> 저쪽은 HTTP 로 관통시키는 테스트고
 * 여기는 스레드를 직접 다룬다. MockMvc 를 여러 스레드에서 동시에 쓰면 실패 원인이 잠금인지
 * MockMvc 인지 구분되지 않으므로 서비스를 직접 부른다. 트랜잭션 경계는 서비스의
 * {@code @Transactional} 이 그대로 잡아 준다.
 *
 * <p>클래스에 {@code @Transactional} 을 붙이지 않은 것이 <b>필수</b>다. 테스트가 트랜잭션을
 * 열고 있으면 다른 스레드가 그 트랜잭션이 아직 커밋하지 않은 데이터를 못 보고, 심지어 서로
 * 대기하다 타임아웃 난다. 커밋된 상태를 여러 스레드가 보게 해야 경합이 재현된다.
 * 대신 앞뒤로 직접 정리한다.
 */
@SpringBootTest
class ConcurrentPurchaseTests {

    private static final String SELLER_WALLET = "0xa000000000000000000000000000000000000001";

    /** 잔여 12kg 에 10kg 씩 두 건. 하나만 성공해야 한다. */
    private static final long LISTED_QUANTITY = 12_000L;
    private static final long PURCHASE_QUANTITY = 10_000L;
    private static final long PRICE_PER_KG = 1_000L;

    private static final int BUYERS = 2;

    @Autowired
    private ListingCommandService listingCommandService;

    @Autowired
    private TradeCommandService tradeCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository walletRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialListingRepository listingRepository;

    @Autowired
    private MaterialTradeRepository tradeRepository;

    @Autowired
    private UserNotificationRepository notificationRepository;

    private Long listingId;

    @BeforeEach
    void setUp() {
        clean();

        User seller = userRepository.save(User.register(
                "concurrent-seller@example.com", "{noop}x", "공급자", "010-9000-0000",
                UserRole.SUPPLIER));
        walletRepository.save(UserWallet.link(seller.getId(), SELLER_WALLET, 31221L));

        Material material = materialRepository.save(Material.register(
                "MAT-990001", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "동시 구매 시험용", null, "A", LISTED_QUANTITY, null, null, seller.getId()));
        balanceRepository.save(MaterialBalance.of(
                material.getId(), seller.getId(), SELLER_WALLET, LISTED_QUANTITY));

        listingId = listingCommandService
                .register(seller.getId(), new ListingRegisterRequest(
                        material.getId(), LISTED_QUANTITY, MaterialQuantityUnit.G, PRICE_PER_KG))
                .listingId();

        for (int i = 0; i < BUYERS; i++) {
            User buyer = userRepository.save(User.register(
                    "concurrent-buyer" + i + "@example.com", "{noop}x", "브랜드" + i,
                    "010-900" + i + "-0000", UserRole.BRAND));
            walletRepository.save(UserWallet.link(
                    buyer.getId(), buyerWallet(i), 31221L));
        }
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void twoBuyersCannotOversellTheSameListing() throws Exception {
        List<Long> buyerIds = userRepository.findAll().stream()
                .filter(user -> user.getRole() == UserRole.BRAND)
                .map(User::getId)
                .toList();
        assertThat(buyerIds).hasSize(BUYERS);

        // 두 스레드를 같은 순간에 출발시킨다. latch 없이 순차로 부르면 경합이 아니라
        // 그냥 두 번의 요청이고, 잠금이 없어도 통과한다.
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);

        List<Callable<Outcome>> attempts = new ArrayList<>();
        for (Long buyerId : buyerIds) {
            attempts.add(() -> {
                start.await();
                try {
                    tradeCommandService.create(buyerId, purchaseRequest());
                    return Outcome.SUCCEEDED;
                } catch (RuntimeException rejected) {
                    return Outcome.of(rejected);
                }
            });
        }

        List<Future<Outcome>> futures = new ArrayList<>();
        for (Callable<Outcome> attempt : attempts) {
            futures.add(pool.submit(attempt));
        }
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        // 1) 정확히 하나만 성공한다.
        assertThat(outcomes).filteredOn(Outcome.SUCCEEDED::equals).hasSize(1);

        // 2) 실패한 쪽은 도메인 오류다. 500 이나 CHECK 위반이면 잠금이 동작하지 않은 것이다.
        assertThat(outcomes).filteredOn(outcome -> outcome != Outcome.SUCCEEDED)
                .allSatisfy(outcome -> assertThat(outcome)
                        .as("잔여 수량 초과는 도메인 오류로 거절되어야 한다")
                        .isEqualTo(Outcome.REJECTED_AS_OVER_QUANTITY));

        // 3) 잔여 수량이 정확히 맞고 **음수가 아니다.** 이것이 이 테스트의 핵심 단언이다.
        MaterialListing listing = listingRepository.findById(listingId).orElseThrow();
        assertThat(listing.getAvailableQuantity())
                .isEqualTo(LISTED_QUANTITY - PURCHASE_QUANTITY);

        // 4) 거래도 하나만 생긴다.
        assertThat(tradeRepository.findAllByListingId(listingId)).hasSize(1);
    }

    private enum Outcome {
        SUCCEEDED,
        REJECTED_AS_OVER_QUANTITY,
        REJECTED_OTHERWISE;

        static Outcome of(RuntimeException failure) {
            String message = String.valueOf(failure.getMessage());
            return message.contains("판매 가능 수량")
                    ? REJECTED_AS_OVER_QUANTITY
                    : REJECTED_OTHERWISE;
        }
    }

    /**
     * 구매 요청. <b>배송 정보는 이 테스트의 관심사가 아니다</b> — 두 스레드가 같은 값을 써도
     * 경합 재현에 아무 영향이 없다. 그래서 호출부에 늘어놓지 않고 여기 모아 둔다.
     *
     * <p>다섯 칸 모두 {@code @NotBlank} 라 비워 둘 수 없다. 값은
     * {@code MarketplaceApiTests} 가 쓰는 것과 같게 맞췄다 — 같은 도메인의 테스트가
     * 서로 다른 가짜 주소를 쓰면 실패 로그를 볼 때 어느 쪽 데이터인지 헷갈린다.
     */
    private TradeCreateRequest purchaseRequest() {
        return new TradeCreateRequest(
                listingId,
                PURCHASE_QUANTITY,
                MaterialQuantityUnit.G,
                PaymentMethod.MOCK,
                "김구매",
                "010-1111-1111",
                "04766",
                "서울 성동구 아차산로 17길 48",
                "20층");
    }

    private static String buyerWallet(int index) {
        return "0xb00000000000000000000000000000000000000" + index;
    }

    private void clean() {
        notificationRepository.deleteAll();
        tradeRepository.deleteAll();
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
