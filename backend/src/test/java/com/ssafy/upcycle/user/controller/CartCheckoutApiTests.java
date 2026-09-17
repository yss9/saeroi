package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderGroupRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.ConsumerCartItem;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.ConsumerCartItemRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code POST /api/v1/consumers/me/cart/checkout} — 장바구니에서 고른 항목으로 주문.
 *
 * <p>여기서 지키는 것은 넷이다. <b>고른 것만 주문되고</b>, <b>브랜드 수만큼 주문이 나뉘며</b>,
 * <b>살 수 없는 항목이 섞이면 전부 거절하고</b>, <b>거절된 요청은 장바구니도 재고도 건드리지
 * 않는다</b>. 셋째가 깨지면 사용자가 화면에서 본 합계와 다른 금액이 결제되고, 넷째가 깨지면
 * 실패한 주문이 제품을 묶어 둔다.
 *
 * <p><b>장바구니는 여기서 비우지 않는다.</b> 비우는 것은 결제 승인 뒤다 — 결제창을 닫고
 * 돌아온 사용자의 장바구니가 사라지면 안 된다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartCheckoutApiTests {

    private static final String BASE = "/api/v1/consumers/me/cart/checkout";
    private static final long BAG_PRICE = 50_000L;
    private static final long WALLET_PRICE = 20_000L;
    private static final long COAT_PRICE = 300_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsumerAddressRepository addressRepository;

    @Autowired
    private ConsumerCartItemRepository cartItemRepository;

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

        bagId = saveModel(brandAId, "업사이클 토트백", ProductCategory.BAG, BAG_PRICE, true);
        walletId = saveModel(brandAId, "데님 하프 월렛", ProductCategory.WALLET, WALLET_PRICE, true);
        coatId = saveModel(brandBId, "레더 패치 블루종", ProductCategory.CLOTHING, COAT_PRICE, true);
        saveItems(bagId, 3);
        saveItems(walletId, 2);
        saveItems(coatId, 1);

        addToCart(consumerId, bagId, 2, BAG_PRICE);
        addToCart(consumerId, walletId, 1, WALLET_PRICE);
        addToCart(consumerId, coatId, 1, COAT_PRICE);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 주문 생성 ───────────────────────────────────────────

    @Test
    void checkoutSplitsTheChosenItemsByBrandUnderOneGroup() throws Exception {
        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, walletId, coatId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderGroupCode").value(
                        org.hamcrest.Matchers.startsWith("ORG-")))
                .andExpect(jsonPath("$.data.totalPrice").value(
                        BAG_PRICE * 2 + WALLET_PRICE + COAT_PRICE))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.cancelable").value(true))
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                // 묶음 안의 주문도 자기가 속한 묶음을 안다. 단일 주문은 이 값이 null 이다.
                .andExpect(jsonPath("$.data.orders[0].orderGroupCode").value(
                        org.hamcrest.Matchers.startsWith("ORG-")));

        assertThat(productOrderRepository.count()).isEqualTo(2);
        assertThat(productOrderRepository.findAll())
                .extracting(order -> order.getBrandId())
                .containsExactlyInAnyOrder(brandAId, brandBId);
        // 담은 수량만큼 개체를 잡는다.
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.RESERVED)).isEqualTo(2);
    }

    @Test
    void onlyTheChosenItemsAreOrdered() throws Exception {
        mockMvc.perform(checkoutRequest(consumerId, body(List.of(coatId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(COAT_PRICE))
                .andExpect(jsonPath("$.data.orders.length()").value(1));

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(3);
    }

    @Test
    void theCartIsNotEmptiedYet() throws Exception {
        // 비우는 것은 결제 승인 뒤다. 여기서 비우면 결제창을 닫고 돌아온 사용자의
        // 장바구니가 사라진다.
        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, walletId, coatId))))
                .andExpect(status().isOk());

        assertThat(cartItemRepository.findMine(consumerId)).hasSize(3);
    }

    @Test
    void theSameProductChosenTwiceCountsOnce() throws Exception {
        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, bagId))))
                .andExpect(status().isOk())
                // 수량은 장바구니가 들고 있다. 두 번 골랐다고 4개가 되지 않는다.
                .andExpect(jsonPath("$.data.totalPrice").value(BAG_PRICE * 2));
    }

    // ── 살 수 없는 항목 ─────────────────────────────────────

    @Test
    void aDiscontinuedProductStopsTheWholeCheckout() throws Exception {
        ProductModel bag = productModelRepository.findById(bagId).orElseThrow();
        bag.discontinue();
        productModelRepository.saveAndFlush(bag);

        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, coatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_ITEMS_UNAVAILABLE"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productModelId").value(bagId))
                .andExpect(jsonPath("$.data[0].productName").value("업사이클 토트백"))
                .andExpect(jsonPath("$.data[0].reason").value("NOT_ON_SALE"));

        // 살 수 있었던 항목까지 전부 그대로다.
        assertThat(productOrderRepository.count()).isZero();
        assertThat(productOrderGroupRepository.count()).isZero();
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                coatId, ProductItemStatus.IN_STOCK)).isEqualTo(1);
        assertThat(cartItemRepository.findMine(consumerId)).hasSize(3);
    }

    @Test
    void aProductMovedToAHiddenCategoryIsRefusedWithItsOwnReason() throws Exception {
        // 소비자 SHOP 에서 감추는 분류다. 화면에 보이지 않는 상품이 결제되면 안 된다.
        Long hidden = saveModel(brandAId, "자투리 원단", ProductCategory.ETC, 5_000L, true);
        saveItems(hidden, 1);
        addToCart(consumerId, hidden, 1, 5_000L);

        mockMvc.perform(checkoutRequest(consumerId, body(List.of(hidden))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_ITEMS_UNAVAILABLE"))
                .andExpect(jsonPath("$.data[0].reason").value("HIDDEN_CATEGORY"));
    }

    @Test
    void everyUnavailableItemIsReportedAtOnce() throws Exception {
        ProductModel bag = productModelRepository.findById(bagId).orElseThrow();
        bag.discontinue();
        productModelRepository.saveAndFlush(bag);
        Long hidden = saveModel(brandAId, "자투리 원단", ProductCategory.ETC, 5_000L, true);
        saveItems(hidden, 1);
        addToCart(consumerId, hidden, 1, 5_000L);

        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, hidden, coatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ── 재고 ────────────────────────────────────────────────

    @Test
    void oneShortItemFailsTheWholeCheckout() throws Exception {
        // 브랜치 1 의 전체 실패 규칙 그대로다. 몇 개 모자란지까지 주문 쪽이 답한다.
        cartItemRepository.findByUserIdAndProductModelId(consumerId, coatId)
                .ifPresent(item -> {
                    item.changeQuantity(2);
                    cartItemRepository.saveAndFlush(item);
                });

        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, coatId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ITEMS_OUT_OF_STOCK"))
                .andExpect(jsonPath("$.data[0].productModelId").value(coatId))
                .andExpect(jsonPath("$.data[0].requested").value(2))
                .andExpect(jsonPath("$.data[0].available").value(1));

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(3);
        assertThat(productOrderGroupRepository.count()).isZero();
    }

    // ── 금액 ────────────────────────────────────────────────

    @Test
    void aPriceThatMovedSinceTheCartIsChargedAtTheCurrentValue() throws Exception {
        // addedPrice 는 알림용이고 결제 기준이 아니다.
        changePrice(bagId, 60_000L);

        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(120_000L));
    }

    @Test
    void theOptionalExpectedTotalStopsACheckoutTheUserDidNotSee() throws Exception {
        // 담아 둔 사이에 값이 오른 것을 모르고 결제되는 경로를 화면이 막을 수 있어야 한다.
        changePrice(bagId, 60_000L);
        Map<String, Object> request = body(List.of(bagId));
        request.put("expectedTotalPrice", BAG_PRICE * 2);

        mockMvc.perform(checkoutRequest(consumerId, request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_CHECKOUT_PRICE_CHANGED"))
                .andExpect(jsonPath("$.data[0].productModelId").value(bagId))
                .andExpect(jsonPath("$.data[0].addedPrice").value(BAG_PRICE))
                .andExpect(jsonPath("$.data[0].currentPrice").value(60_000L));

        assertThat(productOrderGroupRepository.count()).isZero();
    }

    @Test
    void anExpectedTotalThatMatchesPassesThrough() throws Exception {
        Map<String, Object> request = body(List.of(bagId, coatId));
        request.put("expectedTotalPrice", BAG_PRICE * 2 + COAT_PRICE);

        mockMvc.perform(checkoutRequest(consumerId, request))
                .andExpect(status().isOk());
    }

    // ── 선택 항목 ───────────────────────────────────────────

    @Test
    void aProductThatIsNotInTheCartIsNamedInTheRefusal() throws Exception {
        // 화면과 서버의 장바구니가 어긋났다는 뜻이라, 어떤 id 가 빠졌는지 알려 준다.
        Long notInCart = saveModel(brandAId, "담지 않은 상품", ProductCategory.BAG, 10_000L, true);
        saveItems(notInCart, 1);

        mockMvc.perform(checkoutRequest(consumerId, body(List.of(bagId, notInCart))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(notInCart));
    }

    @Test
    void choosingNothingIsRefusedBeforeAnythingIsTouched() throws Exception {
        mockMvc.perform(checkoutRequest(consumerId, body(List.of())))
                .andExpect(status().isBadRequest());

        assertThat(productOrderGroupRepository.count()).isZero();
    }

    // ── 구매자·배송지 ───────────────────────────────────────

    @Test
    void youCannotShipToSomeoneElsesAddress() throws Exception {
        Map<String, Object> request = body(List.of(bagId));
        request.put("addressId", saveAddress(otherConsumerId));

        mockMvc.perform(checkoutRequest(consumerId, request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ADDRESS_NOT_FOUND"));

        assertThat(productOrderGroupRepository.count()).isZero();
    }

    @Test
    void checkoutNeedsALogin() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(List.of(bagId)))))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder checkoutRequest(Long userId, Map<String, Object> body)
            throws Exception {
        return post(BASE)
                .with(jwt()
                        .jwt(token -> token.subject(String.valueOf(userId)))
                        .authorities(new SimpleGrantedAuthority("ROLE_CONSUMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private Map<String, Object> body(List<Long> productModelIds) {
        Map<String, Object> request = new HashMap<>();
        request.put("productModelIds", productModelIds);
        request.put("addressId", addressId);
        return request;
    }

    private void changePrice(Long productModelId, long price) {
        ProductModel model = productModelRepository.findById(productModelId).orElseThrow();
        new org.springframework.beans.DirectFieldAccessor(model).setPropertyValue("price", price);
        productModelRepository.saveAndFlush(model);
    }

    private void addToCart(Long userId, Long productModelId, int quantity, long addedPrice) {
        cartItemRepository.saveAndFlush(
                ConsumerCartItem.of(userId, productModelId, quantity, addedPrice));
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

    private Long saveModel(
            Long brandId, String name, ProductCategory category, long price, boolean onSale) {
        ProductModel model = ProductModel.register(
                name, category, brandId, price, "폐소재로 만든 제품", "product/thumb.png");
        if (onSale) {
            model.putOnSale();
        }
        return productModelRepository.saveAndFlush(model).getId();
    }

    private void saveItems(Long productModelId, int count) {
        for (int i = 0; i < count; i++) {
            serialNumber++;
            productItemRepository.saveAndFlush(ProductItem.produce(
                    1L,
                    productModelId,
                    serialNumber,
                    "CART-ITEM-" + serialNumber,
                    String.format("%032d", 700_000 + serialNumber)));
        }
    }

    private void clean() {
        productShippingRepository.deleteAll();
        productOrderItemRepository.deleteAll();
        productOrderRepository.deleteAll();
        productOrderGroupRepository.deleteAll();
        cartItemRepository.deleteAll();
        productItemRepository.deleteAll();
        productModelRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}
