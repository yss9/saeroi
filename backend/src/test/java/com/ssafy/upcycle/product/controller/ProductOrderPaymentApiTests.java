package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.CanceledProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.product.service.ProductOrderExpiryWorker;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentApproveCommand;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
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
 * 제품 주문 결제 — 준비 · 승인 · 취소 · 만료.
 *
 * <p>여기서 지키는 것은 <b>"돈과 재고가 어긋나지 않는가"</b> 하나다. 승인 전에 팔린 것으로
 * 기록하면 돈이 오지 않은 주문이 확정되고, 취소나 만료에서 개체를 되돌리지 않으면 제품이
 * 한 점씩 영구히 사라진다. 제품 개체는 한 점씩이라 한 건만 묶여도 그 제품은 바로 품절이다.
 *
 * <p>{@link PaymentGateway} 는 대역으로 바꾼다. 실제 카카오페이를 부를 수 없어서이기도 하지만,
 * <b>금액이 어긋난 승인</b>처럼 진짜 PG 로는 만들 수 없는 응답을 확인해야 하기 때문이다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductOrderPaymentApiTests {

    private static final String BASE = "/api/v1/product-orders";
    private static final long PRICE = 79_000L;
    private static final String TID = "T1234567890abcdef";
    private static final String PG_TOKEN = "pg_token_abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentGateway paymentGateway;

    @Autowired
    private ProductOrderExpiryWorker expiryWorker;

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

        Long brandId = saveUser("pay-brand@test.com", "김대표", "02-1234-5678", UserRole.BRAND);
        consumerId = saveUser("pay-consumer@test.com", "홍길동", "010-1111-2222", UserRole.CONSUMER);
        otherConsumerId = saveUser("pay-other@test.com", "김이웃", "010-3333-4444", UserRole.CONSUMER);
        addressId = saveAddress(consumerId);
        modelId = saveModel(brandId);
        itemId = saveItem(1);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 준비 ────────────────────────────────────────────────

    @Test
    void readyHandsBackTheCheckoutWindowWithoutChargingAnything() throws Exception {
        Long orderId = placeOrder();

        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/payment/ready"), consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redirectUrl").value("https://kakao.test/pay"))
                .andExpect(jsonPath("$.data.totalPrice").value(PRICE))
                .andExpect(jsonPath("$.data.live").value(true));

        // 준비만으로는 아무것도 확정되지 않는다.
        verify(paymentGateway, never()).approve(any());
        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.CREATED);
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
    }

    @Test
    void theServerRemembersTheTidSoTheClientNeverHasToSendIt() throws Exception {
        // 화면이 tid 를 보내게 하면 남의 결제 번호로 승인을 시도할 수 있다.
        Long orderId = placeOrder();
        ready(orderId);

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getPaymentReference())
                .isEqualTo(TID);
    }

    @Test
    void reopeningTheCheckoutWindowIsAllowed() throws Exception {
        // 창을 닫았다가 다시 여는 것은 정상 행동이다. 새 tid 가 이전 것을 덮어쓴다.
        Long orderId = placeOrder();
        ready(orderId);

        when(paymentGateway.ready(any()))
                .thenReturn(new PaymentReady("T-second", "https://kakao.test/pay2"));
        ready(orderId);

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getPaymentReference())
                .isEqualTo("T-second");
    }

    @Test
    void someoneElseCannotStartPayingForYourOrder() throws Exception {
        Long orderId = placeOrder();

        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/payment/ready"), otherConsumerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_FOUND"));

        verify(paymentGateway, never()).ready(any());
    }

    // ── 승인 ────────────────────────────────────────────────

    @Test
    void approvingChargesTheBuyerAndSellsTheItem() throws Exception {
        Long orderId = placeOrder();
        ready(orderId);

        mockMvc.perform(approveRequest(orderId, consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ProductOrderStatus.PAID.name()))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty())
                // 결제가 끝나야 여정이 열린다.
                .andExpect(jsonPath("$.data.items[0].journeyUrl").isNotEmpty());

        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.SOLD);
    }

    @Test
    void approvingWithoutPreparingIsRefused() throws Exception {
        // 준비를 건너뛰면 승인에 쓸 tid 가 없다.
        Long orderId = placeOrder();

        mockMvc.perform(approveRequest(orderId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_PREPARED"));

        verify(paymentGateway, never()).approve(any());
    }

    @Test
    void anApprovalForTheWrongAmountIsNotRecordedAsAPayment() throws Exception {
        // PG 응답을 그대로 믿으면 금액이 어긋난 승인이 정상 결제로 남는다.
        Long orderId = placeOrder();
        ready(orderId);
        when(paymentGateway.approve(any()))
                .thenReturn(new PaymentApproval(TID, PRICE - 1_000L, LocalDateTime.now()));

        mockMvc.perform(approveRequest(orderId, consumerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.CREATED);
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
    }

    @Test
    void comingBackToTheReturnUrlTwiceDoesNotChargeTwice() throws Exception {
        // 결제창에서 뒤로 가기를 하면 복귀 URL 이 실제로 두 번 열린다.
        Long orderId = placeOrder();
        ready(orderId);
        mockMvc.perform(approveRequest(orderId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(approveRequest(orderId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_INVALID"));

        verify(paymentGateway, times(1)).approve(any());
    }

    @Test
    void theGatewayIsToldTheAmountTheServerCalculated() throws Exception {
        // 클라이언트가 금액을 말하게 두면 조작 경로가 된다.
        Long orderId = placeOrder();
        ready(orderId);
        mockMvc.perform(approveRequest(orderId, consumerId)).andExpect(status().isOk());

        org.mockito.ArgumentCaptor<PaymentApproveCommand> captor =
                org.mockito.ArgumentCaptor.forClass(PaymentApproveCommand.class);
        verify(paymentGateway).approve(captor.capture());
        assertThat(captor.getValue().expectedAmount()).isEqualTo(PRICE);
        assertThat(captor.getValue().tid()).isEqualTo(TID);
    }

    @Test
    void someoneElseCannotApproveYourOrder() throws Exception {
        Long orderId = placeOrder();
        ready(orderId);

        mockMvc.perform(approveRequest(orderId, otherConsumerId))
                .andExpect(status().isNotFound());

        verify(paymentGateway, never()).approve(any());
    }

    // ── 취소 ────────────────────────────────────────────────

    @Test
    void cancellingAnUnpaidOrderPutsTheItemBackOnTheShelf() throws Exception {
        // 이것이 없으면 결제를 포기할 때마다 제품이 한 점씩 영구히 사라진다.
        Long orderId = placeOrder();
        ready(orderId);

        mockMvc.perform(cancelRequest(orderId, consumerId))
                .andExpect(status().isOk());

        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.IN_STOCK);
        // 청구가 없었으므로 환불을 요청하지 않는다 — 하면 PG 가 거절한다.
        verify(paymentGateway, never()).cancel(any());
    }

    @Test
    void cancellingAPaidOrderRefundsBeforeItLetsTheOrderGo() throws Exception {
        Long orderId = placeOrder();
        ready(orderId);
        mockMvc.perform(approveRequest(orderId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(cancelRequest(orderId, consumerId)).andExpect(status().isOk());

        org.mockito.ArgumentCaptor<PaymentCancelCommand> captor =
                org.mockito.ArgumentCaptor.forClass(PaymentCancelCommand.class);
        verify(paymentGateway).cancel(captor.capture());
        assertThat(captor.getValue().tid()).isEqualTo(TID);
        assertThat(captor.getValue().cancelAmount()).isEqualTo(PRICE);
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.IN_STOCK);
    }

    @Test
    void aFailedRefundLeavesTheOrderExactlyWhereItWas() throws Exception {
        // 돈은 그대로인데 주문만 사라진 상태가 가장 나쁘다.
        Long orderId = placeOrder();
        ready(orderId);
        mockMvc.perform(approveRequest(orderId, consumerId)).andExpect(status().isOk());
        org.mockito.Mockito.doThrow(new IllegalStateException("PG down"))
                .when(paymentGateway).cancel(any());

        mockMvc.perform(cancelRequest(orderId, consumerId))
                .andExpect(status().is5xxServerError());

        assertThat(productOrderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderStatus.PAID);
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.SOLD);
        assertThat(canceledOrderRepository.count()).isZero();
    }

    @Test
    void aCancelledOrderMovesToTheHistoryTableInsteadOfChangingStatus() throws Exception {
        // 상태만 바꾸면 product_order_item 의 UNIQUE 때문에 그 개체를 다시 팔 수 없다.
        Long orderId = placeOrder();

        mockMvc.perform(cancelRequest(orderId, consumerId)).andExpect(status().isOk());

        assertThat(productOrderRepository.findById(orderId)).isEmpty();
        assertThat(productOrderItemRepository.count()).isZero();
        assertThat(productShippingRepository.count()).isZero();
        assertThat(canceledOrderRepository.findAll())
                .singleElement()
                .satisfies(archived -> {
                    assertThat(archived.getOriginalOrderId()).isEqualTo(orderId);
                    assertThat(archived.getCancelReason()).isEqualTo("변심");
                });
        assertThat(canceledOrderItemRepository.count()).isEqualTo(1);
    }

    @Test
    void theReleasedItemCanActuallyBeBoughtAgain() throws Exception {
        // 이력으로 옮기는 설계가 실제로 목적을 달성하는지 확인한다.
        Long orderId = placeOrder();
        mockMvc.perform(cancelRequest(orderId, consumerId)).andExpect(status().isOk());

        Long secondOrder = placeOrder();

        assertThat(secondOrder).isNotEqualTo(orderId);
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
    }

    @Test
    void someoneElseCannotCancelYourOrder() throws Exception {
        Long orderId = placeOrder();

        mockMvc.perform(cancelRequest(orderId, otherConsumerId))
                .andExpect(status().isNotFound());

        assertThat(productOrderRepository.findById(orderId)).isPresent();
    }

    // ── 만료 ────────────────────────────────────────────────

    @Test
    void anAbandonedCheckoutDoesNotHoldTheItemForever() throws Exception {
        // 사용자가 탭을 그냥 닫으면 화면이 취소를 불러 주지 못한다.
        Long orderId = placeOrder();
        ready(orderId);
        expirePaymentDeadline(orderId);

        expiryWorker.expireUnpaidOrders();

        assertThat(productOrderRepository.findById(orderId)).isEmpty();
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.IN_STOCK);
        // 승인 전이라 청구가 없었다.
        verify(paymentGateway, never()).cancel(any());
    }

    @Test
    void theSweepLeavesPaidOrdersAlone() throws Exception {
        // 배치가 목록을 읽은 뒤 승인이 들어올 수 있다. 그때 정상 결제를 지우면 안 된다.
        Long orderId = placeOrder();
        ready(orderId);
        expirePaymentDeadline(orderId);
        mockMvc.perform(approveRequest(orderId, consumerId)).andExpect(status().isOk());

        expiryWorker.expireUnpaidOrders();

        assertThat(productOrderRepository.findById(orderId)).isPresent();
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.SOLD);
    }

    @Test
    void theSweepDoesNotTouchOrdersStillInsideTheirWindow() throws Exception {
        Long orderId = placeOrder();
        ready(orderId);

        expiryWorker.expireUnpaidOrders();

        assertThat(productOrderRepository.findById(orderId)).isPresent();
        assertThat(productItemRepository.findById(itemId).orElseThrow().getStatus())
                .isEqualTo(ProductItemStatus.RESERVED);
    }

    // ── helpers ─────────────────────────────────────────────

    private Long placeOrder() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("productModelId", modelId);
        request.put("addressId", addressId);

        String response = mockMvc.perform(withJwt(post(BASE), consumerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderCode = objectMapper.readTree(response).path("data").path("orderCode").asText();
        return productOrderRepository.findByOrderCode(orderCode).orElseThrow().getId();
    }

    private void ready(Long orderId) throws Exception {
        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/payment/ready"), consumerId))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder approveRequest(Long orderId, Long userId)
            throws Exception {
        return withJwt(post(BASE + "/" + orderId + "/payment/approve"), userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pgToken", PG_TOKEN)));
    }

    private MockHttpServletRequestBuilder cancelRequest(Long orderId, Long userId)
            throws Exception {
        return withJwt(post(BASE + "/" + orderId + "/cancel"), userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "변심")));
    }

    /**
     * 결제 마감을 과거로 옮긴다. 30분을 실제로 기다릴 수는 없다.
     *
     * <p>엔티티에 마감을 바꾸는 메서드를 만들지 않는다 — 운영 코드가 쓸 일이 없는 통로를
     * 테스트 편의로 열면 언젠가 실제 코드가 그걸 쓴다. 대신 필드를 직접 고쳐 저장한다.
     */
    private void expirePaymentDeadline(Long orderId) {
        productOrderRepository.findById(orderId).ifPresent(order -> {
            new org.springframework.beans.DirectFieldAccessor(order)
                    .setPropertyValue("paymentDeadline", LocalDateTime.now().minusMinutes(1));
            productOrderRepository.saveAndFlush(order);
        });
    }

    private MockHttpServletRequestBuilder withJwt(
            MockHttpServletRequestBuilder builder, Long userId) {
        return builder.with(jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CONSUMER")));
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

    private Long saveModel(Long brandId) {
        ProductModel model = ProductModel.register(
                "업사이클 토트백", ProductCategory.BAG, brandId, PRICE, "폐데님으로 만든 가방",
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
