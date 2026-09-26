package com.ssafy.upcycle.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.product.ProductErrorCode;
import com.ssafy.upcycle.product.dto.OutOfStockLine;
import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderGroupResponse;
import com.ssafy.upcycle.product.dto.ProductOrderLine;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderGroupRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
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
 * 여러 상품을 한 번에 주문한다 — {@link ProductOrderCheckoutService}.
 *
 * <p>여기서 지키는 것은 다섯이다. <b>브랜드 수만큼 주문이 생기고 묶음 하나가 그것을
 * 덮으며</b>, <b>수량만큼 서로 다른 개체를 잡고</b>, <b>하나라도 모자라면 전부 실패하며</b>,
 * <b>실패한 뒤 잡아 둔 개체가 남지 않고</b>, <b>기존 단일 주문은 묶이지 않는다</b>. 셋째가
 * 깨지면 사용자가 기대한 것과 다른 금액이 결제되고, 넷째가 깨지면 팔 수 있는 제품이 아무
 * 주문에도 속하지 않은 채 잠긴다.
 *
 * <p><b>컨트롤러가 아직 없어 서비스를 직접 부른다.</b> 장바구니에서 들어오는 입구와 묶음
 * 결제는 다음 작업이다.
 *
 * <p>클래스에 {@code @Transactional} 을 붙이지 않은 것이 <b>필수</b>다. 테스트가 트랜잭션을
 * 열고 있으면 서비스가 그 트랜잭션에 얹혀 롤백 여부를 확인할 수 없고, 경합 테스트의 두
 * 스레드도 서로의 커밋을 보지 못한다. 대신 앞뒤로 직접 정리한다
 * ({@code backend.md} "테스트 작성 시 주의" 참고).
 */
@SpringBootTest
class ProductOrderCheckoutServiceTests {

    private static final long BAG_PRICE = 50_000L;
    private static final long WALLET_PRICE = 20_000L;
    private static final long COAT_PRICE = 300_000L;

    @Autowired
    private ProductOrderCheckoutService checkoutService;

    @Autowired
    private ProductOrderCommandService commandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsumerAddressRepository addressRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private ProductOrderRepository productOrderRepository;

    @Autowired
    private ProductOrderItemRepository productOrderItemRepository;

    @Autowired
    private ProductShippingRepository productShippingRepository;

    @Autowired
    private ProductOrderGroupRepository productOrderGroupRepository;

    private Long consumerId;
    private Long otherConsumerId;
    private Long brandAId;
    private Long brandBId;
    private Long addressId;
    private Long otherAddressId;
    private Long bagId;
    private Long walletId;
    private Long coatId;

    private int serialNumber;

    @BeforeEach
    void setUp() {
        clean();
        serialNumber = 0;

        consumerId = saveUser("checkout-consumer@test.com", "홍길동", UserRole.CONSUMER);
        otherConsumerId = saveUser("checkout-other@test.com", "김이웃", UserRole.CONSUMER);
        brandAId = saveUser("checkout-brand-a@test.com", "리위브", UserRole.BRAND);
        brandBId = saveUser("checkout-brand-b@test.com", "더스티밀러", UserRole.BRAND);
        addressId = saveAddress(consumerId);
        otherAddressId = saveAddress(otherConsumerId);

        bagId = saveModel(brandAId, "업사이클 토트백", ProductCategory.BAG, BAG_PRICE);
        walletId = saveModel(brandAId, "데님 하프 월렛", ProductCategory.WALLET, WALLET_PRICE);
        coatId = saveModel(brandBId, "레더 패치 블루종", ProductCategory.CLOTHING, COAT_PRICE);

        saveItems(bagId, 3);
        saveItems(walletId, 2);
        saveItems(coatId, 1);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 브랜드 분할 ─────────────────────────────────────────

    @Test
    void brandsGetTheirOwnOrdersUnderOneGroup() {
        // 브랜드가 자기 주문만 관리하는 기존 구조를 그대로 두면서 결제는 한 번이어야 한다.
        ProductOrderGroupResponse group = checkoutService.placeAll(
                consumerId,
                List.of(
                        new ProductOrderLine(bagId, 2),
                        new ProductOrderLine(walletId, 1),
                        new ProductOrderLine(coatId, 1)),
                addressId);

        assertThat(group.orders()).hasSize(2);
        assertThat(group.orderGroupCode()).startsWith("ORG-").doesNotStartWith("TMP-");
        assertThat(group.totalPrice()).isEqualTo(BAG_PRICE * 2 + WALLET_PRICE + COAT_PRICE);
        assertThat(group.orders()).extracting(ProductOrderResponse::brandName)
                .containsExactlyInAnyOrder("리위브", "더스티밀러");

        List<ProductOrder> orders =
                productOrderRepository.findAllByOrderGroupIdOrderByIdAsc(group.orderGroupId());
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getBrandId)
                .containsExactlyInAnyOrder(brandAId, brandBId);
        assertThat(orders).extracting(ProductOrder::getTotalPrice)
                .containsExactlyInAnyOrder(BAG_PRICE * 2 + WALLET_PRICE, COAT_PRICE);
        // 묶음 금액이 주문 금액의 합과 다르면 결제창에 청구되는 금액이 어긋난다.
        assertThat(orders.stream().mapToLong(ProductOrder::getTotalPrice).sum())
                .isEqualTo(group.totalPrice());
    }

    @Test
    void theOrdersWaitForPaymentJustLikeASingleOrder() {
        // 여기서 파는 것이 아니다. 개체는 잡아 두고 파는 것은 결제 승인 시점이다.
        ProductOrderGroupResponse group = checkoutService.placeAll(
                consumerId, List.of(new ProductOrderLine(bagId, 1)), addressId);

        assertThat(productOrderRepository.findAllByOrderGroupIdOrderByIdAsc(group.orderGroupId()))
                .allSatisfy(order -> {
                    assertThat(order.getStatus()).isEqualTo(ProductOrderStatus.CREATED);
                    assertThat(order.getPaidAt()).isNull();
                    assertThat(order.getOrderCode()).startsWith("ORD-").doesNotStartWith("TMP-");
                });
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.RESERVED)).isEqualTo(1);
        // 브랜드가 따로 보내므로 배송지 스냅샷은 주문마다 하나씩이다.
        assertThat(productShippingRepository.count()).isEqualTo(1);
    }

    // ── 수량 ────────────────────────────────────────────────

    @Test
    void eachUnitOfQuantityTakesADifferentItem() {
        // 개체는 한 점씩이라 같은 것을 두 번 팔 수 없다. 수량 3 이면 개체도 3 개다.
        ProductOrderGroupResponse group = checkoutService.placeAll(
                consumerId, List.of(new ProductOrderLine(bagId, 3)), addressId);

        List<Long> itemIds = productOrderItemRepository
                .findAllByProductOrderId(group.orders().get(0).orderId()).stream()
                .map(ProductOrderItem::getProductItemId)
                .toList();
        assertThat(itemIds).hasSize(3).doesNotHaveDuplicates();
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isZero();
    }

    @Test
    void theSameProductSentTwiceIsMergedIntoOneLine() {
        // 합치지 않으면 같은 상품을 두 번 잠그려 하게 된다.
        ProductOrderGroupResponse group = checkoutService.placeAll(
                consumerId,
                List.of(new ProductOrderLine(bagId, 1), new ProductOrderLine(bagId, 2)),
                addressId);

        assertThat(group.orders()).hasSize(1);
        assertThat(group.totalPrice()).isEqualTo(BAG_PRICE * 3);
        assertThat(productOrderItemRepository
                .findAllByProductOrderId(group.orders().get(0).orderId())).hasSize(3);
    }

    @Test
    void anEmptyOrAbsurdQuantityIsRefusedBeforeAnythingIsLocked() {
        assertThatThrownBy(() -> checkoutService.placeAll(consumerId, List.of(), addressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(failure -> assertThat(errorCodeOf(failure))
                        .isEqualTo(ProductErrorCode.PRODUCT_ORDER_NO_ITEMS));

        for (int quantity : new int[] {0, -1, 100}) {
            assertThatThrownBy(() -> checkoutService.placeAll(
                    consumerId, List.of(new ProductOrderLine(bagId, quantity)), addressId))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(failure -> assertThat(errorCodeOf(failure))
                            .isEqualTo(ProductErrorCode.PRODUCT_ORDER_QUANTITY_INVALID));
        }

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(3);
    }

    // ── 전부 아니면 전무 ────────────────────────────────────

    @Test
    void oneShortLineFailsTheWholeOrderAndSaysWhichProduct() {
        // 되는 것만 주문하면 기대와 다른 금액이 결제되고 그건 분쟁이 된다. 어떤 상품이
        // 모자랐는지 함께 알려야 장바구니에서 무엇을 뺄지 알 수 있다.
        assertThatThrownBy(() -> checkoutService.placeAll(
                consumerId,
                List.of(new ProductOrderLine(bagId, 1), new ProductOrderLine(coatId, 2)),
                addressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(failure -> {
                    assertThat(errorCodeOf(failure))
                            .isEqualTo(ProductErrorCode.PRODUCT_ORDER_ITEMS_OUT_OF_STOCK);
                    assertThat(shortagesOf(failure)).singleElement().satisfies(line -> {
                        assertThat(line.productModelId()).isEqualTo(coatId);
                        assertThat(line.productName()).isEqualTo("레더 패치 블루종");
                        assertThat(line.requested()).isEqualTo(2);
                        assertThat(line.available()).isEqualTo(1);
                    });
                });
    }

    @Test
    void everyShortLineIsReportedAtOnce() {
        // 하나씩 알리면 사용자가 장바구니를 고치고 다시 누르는 일이 모자란 상품 수만큼 반복된다.
        assertThatThrownBy(() -> checkoutService.placeAll(
                consumerId,
                List.of(new ProductOrderLine(walletId, 5), new ProductOrderLine(coatId, 2)),
                addressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(failure -> assertThat(shortagesOf(failure))
                        .extracting(OutOfStockLine::productModelId)
                        .containsExactlyInAnyOrder(walletId, coatId));
    }

    @Test
    void aFailedOrderLeavesNothingLockedOrHalfBuilt() {
        // 되돌리는 코드를 따로 쓰지 않고 롤백이 잠금과 RESERVED 전환을 함께 되돌린다.
        assertThatThrownBy(() -> checkoutService.placeAll(
                consumerId,
                List.of(new ProductOrderLine(bagId, 1), new ProductOrderLine(coatId, 2)),
                addressId))
                .isInstanceOf(BusinessException.class);

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(3);
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.RESERVED)).isZero();
        assertThat(productOrderGroupRepository.count()).isZero();
        assertThat(productOrderRepository.count()).isZero();
        assertThat(productOrderItemRepository.count()).isZero();
        assertThat(productShippingRepository.count()).isZero();
    }

    // ── 구매자·배송지 ───────────────────────────────────────

    @Test
    void youCannotShipToSomeoneElsesAddress() {
        // 404 로 답한다 — 403 은 "그 id 는 존재하고 남의 것" 을 흘리고, 그 값은 남의 집 주소다.
        assertThatThrownBy(() -> checkoutService.placeAll(
                consumerId, List.of(new ProductOrderLine(bagId, 1)), otherAddressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(failure -> assertThat(errorCodeOf(failure))
                        .isEqualTo(ProductErrorCode.PRODUCT_ORDER_ADDRESS_NOT_FOUND));

        assertThat(productOrderGroupRepository.count()).isZero();
    }

    @Test
    void brandsDoNotBuyFromTheConsumerStore() {
        // 토큰의 역할 클레임이 아니라 DB 의 현재 역할로 판정한다 — 단일 주문과 같은 기준이다.
        assertThatThrownBy(() -> checkoutService.placeAll(
                brandAId, List.of(new ProductOrderLine(bagId, 1)), addressId))
                .isInstanceOf(BusinessException.class)
                .satisfies(failure -> assertThat(errorCodeOf(failure))
                        .isEqualTo(ProductErrorCode.PRODUCT_ORDER_ROLE_NOT_ALLOWED));
    }

    // ── 기존 경로 ───────────────────────────────────────────

    @Test
    void theSingleOrderPathStaysOutOfEveryGroup() {
        // 상품 상세의 "바로 구매" 는 그대로다. order_group_id 가 붙으면 그 주문은 다음
        // 작업에서 묶음 결제를 기다리게 되고, 지금 경로로는 영원히 결제되지 않는다.
        ProductOrderResponse response = commandService.place(
                consumerId, new ProductOrderCreateRequest(bagId, addressId));

        ProductOrder order = productOrderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getOrderGroupId()).isNull();
        assertThat(order.getOrderCode()).startsWith("ORD-");
        assertThat(productOrderGroupRepository.count()).isZero();
    }

    // ── 경합 ────────────────────────────────────────────────

    /**
     * 마지막 한 점을 두 구매자가 동시에 주문한다. <b>한쪽만 성공해야 한다.</b>
     *
     * <p>잠금이 없으면 둘 다 "1 개 남았다" 를 읽고 각자 통과한 뒤 같은 개체로 주문 항목을
     * 만든다. {@code product_order_item.product_item_id} 의 UNIQUE 가 최후 방어선이지만
     * 거기까지 가면 설명할 수 없는 500 이 된다.
     */
    @Test
    void twoBuyersRacingForTheLastItemDoNotBothWin() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Long[]> buyers = List.of(
                new Long[] {consumerId, addressId},
                new Long[] {otherConsumerId, otherAddressId});

        List<Future<Outcome>> futures = new ArrayList<>();
        for (Long[] buyer : buyers) {
            Callable<Outcome> attempt = () -> {
                // latch 없이 순차로 부르면 경합이 아니라 그냥 두 번의 요청이고, 잠금이
                // 없어도 통과한다.
                start.await();
                try {
                    checkoutService.placeAll(
                            buyer[0], List.of(new ProductOrderLine(coatId, 1)), buyer[1]);
                    return Outcome.SUCCEEDED;
                } catch (RuntimeException rejected) {
                    return Outcome.of(rejected);
                }
            };
            futures.add(pool.submit(attempt));
        }
        start.countDown();

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertThat(outcomes).filteredOn(Outcome.SUCCEEDED::equals).hasSize(1);
        // 진 쪽은 도메인 오류로 거절된다. 500 이나 UNIQUE 위반이면 잠금이 동작하지 않은 것이다.
        assertThat(outcomes).filteredOn(outcome -> outcome != Outcome.SUCCEEDED)
                .allSatisfy(outcome -> assertThat(outcome)
                        .as("재고 부족은 도메인 오류로 거절되어야 한다")
                        .isEqualTo(Outcome.REJECTED_AS_OUT_OF_STOCK));

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                coatId, ProductItemStatus.RESERVED)).isEqualTo(1);
        assertThat(productOrderGroupRepository.count()).isEqualTo(1);
        assertThat(productOrderRepository.count()).isEqualTo(1);
    }

    private enum Outcome {
        SUCCEEDED,
        REJECTED_AS_OUT_OF_STOCK,
        REJECTED_OTHERWISE;

        static Outcome of(RuntimeException failure) {
            return failure instanceof BusinessException business
                    && business.getErrorCode()
                            == ProductErrorCode.PRODUCT_ORDER_ITEMS_OUT_OF_STOCK
                    ? REJECTED_AS_OUT_OF_STOCK
                    : REJECTED_OTHERWISE;
        }
    }

    // ── helpers ─────────────────────────────────────────────

    private static Object errorCodeOf(Throwable failure) {
        return ((BusinessException) failure).getErrorCode();
    }

    @SuppressWarnings("unchecked")
    private static List<OutOfStockLine> shortagesOf(Throwable failure) {
        return (List<OutOfStockLine>) ((BusinessException) failure).getDetails();
    }

    private Long saveUser(String email, String name, UserRole role) {
        return userRepository.saveAndFlush(
                User.register(email, "{noop}x", name, "010-1111-2222", role)).getId();
    }

    private Long saveAddress(Long userId) {
        ConsumerAddress address = ConsumerAddress.register(
                userId, "집", "홍길동", "010-1111-2222", "06236",
                "서울특별시 강남구 테헤란로 212", "5층");
        address.markDefault(true);
        return addressRepository.saveAndFlush(address).getId();
    }

    private Long saveModel(Long brandId, String name, ProductCategory category, long price) {
        ProductModel model = ProductModel.register(
                name, category, brandId, price, "폐소재로 만든 제품", "product/thumb.png");
        model.putOnSale();
        return productModelRepository.saveAndFlush(model).getId();
    }

    /**
     * 개체를 만든다. {@code production_batch_id} 는 연관관계가 아니라 FK 값이라 배치 행 없이도
     * 만들 수 있다 ({@code ProductOrderCreateApiTests} 도 같은 방식이다).
     */
    private void saveItems(Long productModelId, int count) {
        for (int i = 0; i < count; i++) {
            serialNumber++;
            productItemRepository.saveAndFlush(ProductItem.produce(
                    1L,
                    productModelId,
                    serialNumber,
                    "CHECKOUT-ITEM-" + serialNumber,
                    String.format("%032d", 900_000 + serialNumber)));
        }
    }

    private void clean() {
        productShippingRepository.deleteAll();
        productOrderItemRepository.deleteAll();
        productOrderRepository.deleteAll();
        productOrderGroupRepository.deleteAll();
        productItemRepository.deleteAll();
        productModelRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}
