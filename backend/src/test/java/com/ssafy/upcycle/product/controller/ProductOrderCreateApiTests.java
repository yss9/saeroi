package com.ssafy.upcycle.product.controller;

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
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.HashMap;
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
 * {@code POST /api/v1/product-orders} — 제품 구매.
 *
 * <p>여기서 지키는 것은 셋이다. <b>같은 개체가 두 번 팔리지 않고</b>, <b>주문이 만들어지면
 * 배송지 스냅샷까지 함께 생기며</b>, <b>남의 배송지로는 주문할 수 없다</b>. 첫째가 깨지면
 * 없는 물건을 팔고, 둘째가 깨지면 어디로 보낼지 모르는 주문이 남으며, 셋째가 깨지면 남의
 * 집 주소로 물건이 간다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductOrderCreateApiTests {

    private static final String BASE = "/api/v1/product-orders";
    private static final long PRICE = 79_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private Long consumerId;
    private Long otherConsumerId;
    private Long brandId;
    private Long modelId;
    private Long addressId;

    @BeforeEach
    void setUp() {
        clean();
        consumerId = saveUser("order-consumer@test.com", "홍길동", "010-1111-2222", UserRole.CONSUMER);
        otherConsumerId = saveUser("order-other@test.com", "김이웃", "010-3333-4444", UserRole.CONSUMER);
        brandId = saveUser("order-brand@test.com", "김대표", "02-1234-5678", UserRole.BRAND);
        addressId = saveAddress(consumerId, "집");
        modelId = saveModel(true);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 성공 ────────────────────────────────────────────────

    @Test
    void buyingHoldsOneItemAndRecordsWhereToSendIt() throws Exception {
        Long itemId = saveItem(1);

        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPrice").value(PRICE))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].price").value(PRICE));

        // 아직 팔린 것이 아니다. 결제 승인 전까지는 잡아만 둔다.
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
        assertThat(productOrderItemRepository.count()).isEqualTo(1);

        // 배송지가 없으면 결제된 주문을 어디로 보낼지 알 수 없다.
        assertThat(productShippingRepository.findAll())
                .singleElement()
                .satisfies(shipping -> {
                    assertThat(shipping.getReceiverName()).isEqualTo("홍길동");
                    assertThat(shipping.getZipCode()).isEqualTo("06236");
                    assertThat(shipping.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 212");
                });
    }

    @Test
    void theOrderWaitsForPaymentBeforeItCountsAsSold() throws Exception {
        // 카카오페이는 결제창에서 사용자가 인증해야 청구된다. 그 전에 PAID 로 적으면
        // 돈이 오지 않은 주문이 팔린 것으로 남는다.
        saveItem(1);

        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ProductOrderStatus.CREATED.name()))
                .andExpect(jsonPath("$.data.paidAt").doesNotExist())
                // 결제 전 주문은 여정을 보여주지 않는다 — 아직 그 사람의 물건이 아니다.
                .andExpect(jsonPath("$.data.items[0].journeyUrl").doesNotExist());
    }

    @Test
    void theResponseCarriesTheNumberTheNextCallNeeds() throws Exception {
        // 결제·취소 경로가 받는 것은 orderId 다. 이 값이 응답에 없으면 화면은 주문을 만든
        // 직후 결제를 이어갈 번호를 알 수 없다 — orderCode 는 그 경로가 받는 값이 아니다.
        saveItem(1);

        String response = mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        long orderId = objectMapper.readTree(response).path("data").path("orderId").asLong();
        assertThat(productOrderRepository.findById(orderId)).isPresent();
    }

    @Test
    void theOrderCodeIsSomethingAPersonCanReadOutLoud() throws Exception {
        // 고객이 문의할 때 대는 값이다. 임시 코드가 밖으로 새면 그 번호로는 아무것도 못 찾는다.
        saveItem(1);

        String response = mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderCode = objectMapper.readTree(response).path("data").path("orderCode").asText();
        assertThat(orderCode).startsWith("ORD-").doesNotStartWith("TMP-");
        assertThat(productOrderRepository.findAll())
                .singleElement()
                .satisfies(order -> assertThat(order.getOrderCode()).isEqualTo(orderCode));
    }

    @Test
    void theBuyerDoesNotChooseWhichItemArrives() throws Exception {
        // 화면이 개체를 지목하면 그 사이 남이 사 간 개체를 가리키게 된다. 서버가 고른다.
        Long oldest = saveItem(1);
        saveItem(2);

        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isOk());

        assertThat(productItemRepository.findById(oldest).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
    }

    // ── 재고 ────────────────────────────────────────────────

    @Test
    void buyingTheSameModelTwiceTakesTwoDifferentItems() throws Exception {
        // product_order_item.product_item_id 의 UNIQUE 가 최후 방어선이지만, 여기까지 오면
        // 이미 주문 행을 만든 뒤라 늦다.
        Long first = saveItem(1);
        Long second = saveItem(2);

        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId))).andExpect(status().isOk());
        mockMvc.perform(orderRequest(otherConsumerId, body(modelId, saveAddress(otherConsumerId, "집"))))
                .andExpect(status().isOk());

        assertThat(productItemRepository.findById(first).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
        assertThat(productItemRepository.findById(second).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
        assertThat(productOrderItemRepository.count()).isEqualTo(2);
    }

    @Test
    void thereIsNothingToSellWhenEveryItemIsGone() throws Exception {
        // 화면도 먼저 막지만 그 사이 팔릴 수 있다.
        saveItem(1);
        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId))).andExpect(status().isOk());

        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_OUT_OF_STOCK"));

        assertThat(productOrderRepository.count()).isEqualTo(1);
    }

    @Test
    void aModelWithNoItemsAtAllCannotBeBought() throws Exception {
        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_OUT_OF_STOCK"));
    }

    @Test
    void aFailedOrderLeavesNoHalfBuiltRowsBehind() throws Exception {
        // 주문 행만 남고 배송지가 없으면 어디로 보낼지 모르는 주문이 된다.
        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isConflict());

        assertThat(productOrderRepository.count()).isZero();
        assertThat(productOrderItemRepository.count()).isZero();
        assertThat(productShippingRepository.count()).isZero();
    }

    // ── 제품 상태 ───────────────────────────────────────────

    @Test
    void aDraftProductIsNotForSaleYet() throws Exception {
        Long draftModel = saveModel(false);
        saveItemFor(draftModel, 1);

        Map<String, Object> request = body(draftModel, addressId);
        mockMvc.perform(orderRequest(consumerId, request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_MODEL_NOT_ON_SALE"));
    }

    @Test
    void aDiscontinuedProductStaysUnbuyableEvenWithItemsLeft() throws Exception {
        // 브랜드가 내린 제품이다. 재고만 보고 통과시키면 내린 제품이 계속 팔린다.
        ProductModel model = productModelRepository.findById(modelId).orElseThrow();
        model.discontinue();
        productModelRepository.saveAndFlush(model);
        saveItem(1);

        mockMvc.perform(orderRequest(consumerId, body(modelId, addressId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_MODEL_NOT_ON_SALE"));
    }

    @Test
    void aProductThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(orderRequest(consumerId, body(999_999L, addressId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    // ── 배송지 ──────────────────────────────────────────────

    @Test
    void youCannotShipToSomeoneElsesAddress() throws Exception {
        // 404 로 답한다 — 403 은 "그 id 는 존재하고 남의 것" 을 흘리고, 그 값은 남의 집 주소다.
        saveItem(1);
        Long theirs = saveAddress(otherConsumerId, "남의 집");

        mockMvc.perform(orderRequest(consumerId, body(modelId, theirs)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ADDRESS_NOT_FOUND"));

        assertThat(productOrderRepository.count()).isZero();
    }

    @Test
    void anAddressThatIsNotThereIsRefusedTheSameWay() throws Exception {
        saveItem(1);

        mockMvc.perform(orderRequest(consumerId, body(modelId, 999_999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ADDRESS_NOT_FOUND"));
    }

    // ── 역할·인증 ───────────────────────────────────────────

    @Test
    void brandsDoNotBuyFromTheConsumerStore() throws Exception {
        saveItem(1);

        mockMvc.perform(orderRequest(brandId, body(modelId, addressId), UserRole.BRAND))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ROLE_NOT_ALLOWED"));
    }

    @Test
    void orderingNeedsALogin() throws Exception {
        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body(modelId, addressId))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOrderWithoutAnAddressIsRefusedBeforeItTouchesStock() throws Exception {
        Long itemId = saveItem(1);
        Map<String, Object> request = new HashMap<>();
        request.put("productModelId", modelId);

        mockMvc.perform(orderRequest(consumerId, request))
                .andExpect(status().isBadRequest());

        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.IN_STOCK);
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder orderRequest(Long userId, Map<String, Object> body)
            throws Exception {
        return orderRequest(userId, body, UserRole.CONSUMER);
    }

    private MockHttpServletRequestBuilder orderRequest(
            Long userId, Map<String, Object> body, UserRole role) throws Exception {
        return post(BASE)
                .with(jwt()
                        .jwt(token -> token.subject(String.valueOf(userId)))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private Map<String, Object> body(Long productModelId, Long addressId) {
        Map<String, Object> request = new HashMap<>();
        request.put("productModelId", productModelId);
        request.put("addressId", addressId);
        return request;
    }

    private Long saveUser(String email, String name, String phone, UserRole role) {
        return userRepository.save(User.register(email, "{noop}x", name, phone, role)).getId();
    }

    private Long saveAddress(Long userId, String alias) {
        ConsumerAddress address = ConsumerAddress.register(
                userId, alias, "홍길동", "010-1111-2222", "06236", "서울특별시 강남구 테헤란로 212", "5층");
        address.markDefault(true);
        return addressRepository.save(address).getId();
    }

    private Long saveModel(boolean onSale) {
        ProductModel model = ProductModel.register(
                "업사이클 토트백", ProductCategory.BAG, brandId, PRICE, "폐데님으로 만든 가방", "product/thumb.png");
        if (onSale) {
            model.putOnSale();
        }
        return productModelRepository.saveAndFlush(model).getId();
    }

    private Long saveItem(int serialNumber) {
        return saveItemFor(modelId, serialNumber);
    }

    private Long saveItemFor(Long productModelId, int serialNumber) {
        // production_batch_id 는 연관관계가 아니라 FK 값이라 배치 행 없이도 개체를 만들 수 있다
        // (ProductModelControllerTests 도 같은 방식이다). 주문은 배치를 보지 않는다.
        return productItemRepository.saveAndFlush(ProductItem.produce(
                1L,
                productModelId,
                serialNumber,
                "ITEM-" + productModelId + "-" + serialNumber,
                String.format("%032d", productModelId * 100 + serialNumber))).getId();
    }

    private void clean() {
        productShippingRepository.deleteAll();
        productOrderItemRepository.deleteAll();
        productOrderRepository.deleteAll();
        productItemRepository.deleteAll();
        productModelRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}
