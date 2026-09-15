package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.CanceledProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 제품 주문의 발송과 수령 확인.
 *
 * <pre>
 *   PAID ──(브랜드 발송)──▶ SHIPPED ──(소비자 수령 확인)──▶ DELIVERED
 * </pre>
 *
 * <p>여기서 지키는 것은 <b>"두 단계를 서로 다른 사람이 누른다"</b> 는 것이다. 브랜드의 발송
 * 만으로 주문이 완료되면 물건이 오지 않은 주문도 완료로 남고, 브랜드가 수령까지 대신 누를 수
 * 있으면 2-Track 을 둔 의미가 없다 — 받았다고 말할 수 있는 사람은 받은 사람뿐이다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductOrderDeliveryApiTests {

    private static final String BASE = "/api/v1/product-orders";
    private static final long PRICE = 79_000L;
    private static final String TID = "T1234567890abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGateway paymentGateway;

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
    private CanceledProductOrderRepository canceledOrderRepository;

    @Autowired
    private CanceledProductOrderItemRepository canceledOrderItemRepository;

    private Long consumerId;
    private Long otherConsumerId;
    private Long brandId;
    private Long otherBrandId;
    private Long modelId;
    private Long addressId;
    private Long itemId;

    @BeforeEach
    void setUp() {
        clean();
        when(paymentGateway.isLive()).thenReturn(true);
        when(paymentGateway.ready(any()))
                .thenReturn(new PaymentReady(TID, "https://kakao.test/pay"));
        when(paymentGateway.approve(any()))
                .thenReturn(new PaymentApproval(TID, PRICE, LocalDateTime.now()));

        brandId = saveUser("ship-brand@test.com", "김대표", "02-1234-5678", UserRole.BRAND);
        otherBrandId = saveUser("ship-other-brand@test.com", "박대표", "02-9999-8888", UserRole.BRAND);
        consumerId = saveUser("ship-consumer@test.com", "홍길동", "010-1111-2222", UserRole.CONSUMER);
        otherConsumerId = saveUser("ship-other@test.com", "김이웃", "010-3333-4444", UserRole.CONSUMER);
        addressId = saveAddress(consumerId);
        modelId = saveModel(brandId);
        itemId = saveItem(1);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 발송 ────────────────────────────────────────────────

    @Test
    void shippingMovesAPaidOrderAlongAndRecordsTheWaybill() throws Exception {
        Long orderId = paidOrder();

        mockMvc.perform(shipRequest(orderId, brandId, "CJ대한통운", "123456789012"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value(ProductOrderStatus.SHIPPED.name()))
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.trackingNumber").value("123456789012"));

        assertThat(productShippingRepository.findByProductOrderId(orderId).orElseThrow()
                .getShippedAt()).isNotNull();
    }

    @Test
    void aHandOverWithoutAWaybillIsStillAShipment() throws Exception {
        // 직접 전달이나 퀵처럼 송장이 없는 방식도 있다. 두 컬럼 모두 NULL 을 허용한다.
        Long orderId = paidOrder();

        mockMvc.perform(withJwt(patch(BASE + "/" + orderId + "/ship"), brandId, UserRole.BRAND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ProductOrderStatus.SHIPPED.name()))
                .andExpect(jsonPath("$.data.carrier").doesNotExist());
    }

    @Test
    void aTrackingNumberWithNoCarrierCannotBeLookedUp() throws Exception {
        Long orderId = paidOrder();

        mockMvc.perform(shipRequest(orderId, brandId, null, "123456789012"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PRODUCT_ORDER_TRACKING_WITHOUT_CARRIER"));

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.PAID);
    }

    @Test
    void anUnpaidOrderIsNotShippedYet() throws Exception {
        // 돈을 받기 전에 물건을 보내면 되돌릴 근거가 없다.
        Long orderId = placeOrder();

        mockMvc.perform(shipRequest(orderId, brandId, "CJ대한통운", "123456789012"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_SHIPPABLE"));
    }

    @Test
    void theSameOrderIsNotShippedTwice() throws Exception {
        Long orderId = paidOrder();
        mockMvc.perform(shipRequest(orderId, brandId, "CJ대한통운", "123456789012"))
                .andExpect(status().isOk());

        mockMvc.perform(shipRequest(orderId, brandId, "우체국택배", "999999999999"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ALREADY_SHIPPED"));

        // 첫 송장이 덮어써지지 않는다 — 고객이 조회하던 번호가 바뀌면 안 된다.
        assertThat(productShippingRepository.findByProductOrderId(orderId).orElseThrow()
                .getTrackingNumber()).isEqualTo("123456789012");
    }

    @Test
    void aBrandCannotShipAnotherBrandsOrder() throws Exception {
        // 역할만 보면 다른 브랜드의 주문을 발송 처리할 수 있게 된다.
        Long orderId = paidOrder();

        mockMvc.perform(shipRequest(orderId, otherBrandId, "CJ대한통운", "123456789012"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_SELLER"));
    }

    @Test
    void theBuyerCannotDeclareTheirOwnOrderShipped() throws Exception {
        Long orderId = paidOrder();

        mockMvc.perform(withJwt(patch(BASE + "/" + orderId + "/ship"), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.PAID);
    }

    // ── 수령 확인 ───────────────────────────────────────────

    @Test
    void confirmingReceiptFinishesTheOrder() throws Exception {
        // 프론트가 붙인 경로다. 주문 현황이 "배송 완료" 로 바뀌는 지점이다.
        Long orderId = paidOrder();
        ship(orderId);

        mockMvc.perform(confirmRequest(orderId, consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value(ProductOrderStatus.DELIVERED.name()))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty())
                // 발송 시각도 그대로 남는다 — 언제 떠나 언제 도착했는지 둘 다 필요하다.
                .andExpect(jsonPath("$.data.shippedAt").isNotEmpty());
    }

    @Test
    void theOrderSummaryCountsItAsDelivered() throws Exception {
        // 마이페이지 주문 현황이 읽는 값이다.
        Long orderId = paidOrder();
        ship(orderId);
        mockMvc.perform(confirmRequest(orderId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(withJwt(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get(BASE + "/me/summary"), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.delivered").value(1))
                .andExpect(jsonPath("$.data.shipped").value(0));
    }

    @Test
    void anOrderThatNeverShippedCannotBeReceived() throws Exception {
        // 발송되지 않은 주문을 받았다고 할 수는 없다.
        Long orderId = paidOrder();

        mockMvc.perform(confirmRequest(orderId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_DELIVERABLE"));
    }

    @Test
    void receiptIsConfirmedOnlyOnce() throws Exception {
        Long orderId = paidOrder();
        ship(orderId);
        mockMvc.perform(confirmRequest(orderId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(confirmRequest(orderId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_DELIVERABLE"));
    }

    @Test
    void theBrandCannotConfirmReceiptOnTheBuyersBehalf() throws Exception {
        // 이것이 열리면 2-Track 을 둔 의미가 없다. 브랜드가 보내자마자 완료시킬 수 있다.
        Long orderId = paidOrder();
        ship(orderId);

        mockMvc.perform(confirmRequest(orderId, brandId, UserRole.BRAND))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_FOUND"));

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.SHIPPED);
    }

    @Test
    void anotherConsumerCannotConfirmYourOrder() throws Exception {
        Long orderId = paidOrder();
        ship(orderId);

        mockMvc.perform(confirmRequest(orderId, otherConsumerId))
                .andExpect(status().isNotFound());

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.SHIPPED);
    }

    // ── 취소와의 경계 ───────────────────────────────────────

    @Test
    void aShippedOrderCanNoLongerBeCancelled() throws Exception {
        // 물건이 나갔는데 주문만 없애면 그 개체가 어디에도 속하지 않게 된다.
        Long orderId = paidOrder();
        ship(orderId);

        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/cancel"), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"변심\"}"))
                .andExpect(status().isConflict())
                // 프론트는 서버 문구를 그대로 띄운다. 결제 쪽 코드를 돌려주면
                // "지금 상태에서는 처리할 수 없습니다" 가 뜨고, 사용자는 왜 안 되는지 모른다.
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_CANCELABLE"))
                .andExpect(jsonPath("$.message").value("배송이 시작된 주문은 취소할 수 없습니다."));

        assertThat(productOrderRepository.findById(orderId)).isPresent();
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.SOLD);
    }

    @Test
    void deliveryNeverChangesWhetherTheItemWasSold() throws Exception {
        // 개체는 결제 승인 시점에 이미 SOLD 다. 발송·수령은 어디까지 갔는지를 적을 뿐이다.
        Long orderId = paidOrder();

        ship(orderId);
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.SOLD);

        mockMvc.perform(confirmRequest(orderId, consumerId)).andExpect(status().isOk());
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.SOLD);
    }

    // ── 인증 ────────────────────────────────────────────────

    @Test
    void bothStepsNeedALogin() throws Exception {
        mockMvc.perform(patch(BASE + "/1/ship")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/1/receive/confirm"))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────

    private Long placeOrder() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("productModelId", modelId);
        request.put("addressId", addressId);

        String response = mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("orderId").asLong();
    }

    private Long paidOrder() throws Exception {
        Long orderId = placeOrder();
        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/payment/ready"), consumerId,
                        UserRole.CONSUMER))
                .andExpect(status().isOk());
        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/payment/approve"), consumerId,
                        UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgToken\":\"pg_token_abcdef\"}"))
                .andExpect(status().isOk());
        return orderId;
    }

    private void ship(Long orderId) throws Exception {
        mockMvc.perform(shipRequest(orderId, brandId, "CJ대한통운", "123456789012"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder shipRequest(
            Long orderId, Long userId, String carrier, String trackingNumber) throws Exception {
        Map<String, Object> request = new HashMap<>();
        if (carrier != null) {
            request.put("carrier", carrier);
        }
        if (trackingNumber != null) {
            request.put("trackingNumber", trackingNumber);
        }
        return withJwt(patch(BASE + "/" + orderId + "/ship"), userId, UserRole.BRAND)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    private MockHttpServletRequestBuilder confirmRequest(Long orderId, Long userId) {
        return confirmRequest(orderId, userId, UserRole.CONSUMER);
    }

    private MockHttpServletRequestBuilder confirmRequest(
            Long orderId, Long userId, UserRole role) {
        return withJwt(post(BASE + "/" + orderId + "/receive/confirm"), userId, role);
    }

    private MockHttpServletRequestBuilder withJwt(
            MockHttpServletRequestBuilder builder, Long userId, UserRole role) {
        return builder.with(jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private Long saveUser(String email, String name, String phone, UserRole role) {
        return userRepository.save(User.register(email, "{noop}x", name, phone, role)).getId();
    }

    private Long saveAddress(Long userId) {
        ConsumerAddress address = ConsumerAddress.register(
                userId, "집", "홍길동", "010-1111-2222", "06236", "서울특별시 강남구 테헤란로 212", "5층");
        address.markDefault(true);
        return addressRepository.save(address).getId();
    }

    private Long saveModel(Long ownerId) {
        ProductModel model = ProductModel.register(
                "업사이클 토트백", ProductCategory.BAG, ownerId, PRICE, "폐데님으로 만든 가방",
                "product/thumb.png");
        model.putOnSale();
        return productModelRepository.saveAndFlush(model).getId();
    }

    private Long saveItem(int serialNumber) {
        return productItemRepository.saveAndFlush(ProductItem.produce(
                1L, modelId, serialNumber, "ITEM-" + serialNumber,
                String.format("%032d", serialNumber))).getId();
    }

    private void clean() {
        canceledOrderItemRepository.deleteAll();
        canceledOrderRepository.deleteAll();
        productShippingRepository.deleteAll();
        productOrderItemRepository.deleteAll();
        productOrderRepository.deleteAll();
        productItemRepository.deleteAll();
        productModelRepository.deleteAll();
        addressRepository.deleteAll();
        userRepository.deleteAll();
    }
}
