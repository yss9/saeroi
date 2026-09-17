package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderGroupStatus;
import com.ssafy.upcycle.product.entity.ProductOrderStatus;
import com.ssafy.upcycle.product.repository.CanceledProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.CanceledProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderGroupRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.product.service.ProductOrderExpiryWorker;
import com.ssafy.upcycle.trade.payment.PaymentApproval;
import com.ssafy.upcycle.trade.payment.PaymentCancelCommand;
import com.ssafy.upcycle.trade.payment.PaymentGateway;
import com.ssafy.upcycle.trade.payment.PaymentReady;
import com.ssafy.upcycle.trade.payment.PaymentReadyCommand;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.ConsumerCartItem;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.repository.ConsumerCartItemRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 주문 묶음의 결제 — 준비 · 승인 · 취소 · 만료.
 *
 * <p>단일 주문 결제와 지켜야 할 것이 같다 — <b>돈과 재고가 어긋나지 않아야 한다.</b> 다른
 * 점은 결제의 단위가 주문이 아니라 묶음이라는 것이고, 그래서 여기서 확인하는 것은 <b>묶음
 * 하나의 승인이 속한 주문 전부에 반영되는가</b>, 그리고 <b>취소가 묶음 전체로만 일어나는가</b>
 * 다.
 *
 * <p>결제가 끝나면 <b>주문에 들어간 장바구니 항목만</b> 사라져야 한다. 고르지 않은 항목까지
 * 지워지면 사용자는 무엇을 담았는지 다시 찾아야 한다.
 *
 * <p>{@link PaymentGateway} 는 대역으로 바꾼다. 실제 카카오페이를 부를 수 없어서이기도
 * 하지만, 환불 요청이 실제로 나갔는지를 확인해야 하기 때문이다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductOrderGroupPaymentApiTests {

    private static final String CHECKOUT = "/api/v1/consumers/me/cart/checkout";
    private static final String BASE = "/api/v1/product-order-groups";
    private static final long BAG_PRICE = 50_000L;
    private static final long COAT_PRICE = 300_000L;
    private static final long WALLET_PRICE = 20_000L;

    /** 장바구니에서 고르는 두 상품의 합. 묶음이 결제창에 청구하는 금액이다. */
    private static final long GROUP_TOTAL = BAG_PRICE + COAT_PRICE;

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

    @Autowired
    private CanceledProductOrderRepository canceledOrderRepository;

    @Autowired
    private CanceledProductOrderItemRepository canceledOrderItemRepository;

    private Long consumerId;
    private Long otherConsumerId;
    private Long brandAId;
    private Long brandBId;
    private Long addressId;
    private Long bagId;
    private Long coatId;
    private Long walletId;

    private int serialNumber;

    @BeforeEach
    void setUp() {
        clean();
        serialNumber = 0;
        when(paymentGateway.isLive()).thenReturn(true);
        when(paymentGateway.ready(any()))
                .thenReturn(new PaymentReady(TID, "https://kakao.test/pay"));
        when(paymentGateway.approve(any()))
                .thenReturn(new PaymentApproval(TID, GROUP_TOTAL, LocalDateTime.now()));

        consumerId = saveUser("group-consumer@test.com", "홍길동", UserRole.CONSUMER);
        otherConsumerId = saveUser("group-other@test.com", "김이웃", UserRole.CONSUMER);
        brandAId = saveUser("group-brand-a@test.com", "리위브", UserRole.BRAND);
        brandBId = saveUser("group-brand-b@test.com", "더스티밀러", UserRole.BRAND);
        addressId = saveAddress(consumerId);

        bagId = saveModel(brandAId, "업사이클 토트백", ProductCategory.BAG, BAG_PRICE);
        coatId = saveModel(brandBId, "레더 패치 블루종", ProductCategory.CLOTHING, COAT_PRICE);
        walletId = saveModel(brandAId, "데님 하프 월렛", ProductCategory.WALLET, WALLET_PRICE);
        saveItems(bagId, 1);
        saveItems(coatId, 1);
        saveItems(walletId, 1);

        // 세 개를 담고 두 개만 고른다. 결제 뒤 남는 한 개가 "주문에 들어간 것만 비운다" 의
        // 확인 대상이다.
        addToCart(bagId, BAG_PRICE);
        addToCart(coatId, COAT_PRICE);
        addToCart(walletId, WALLET_PRICE);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 준비 ────────────────────────────────────────────────

    @Test
    void readyOpensOneCheckoutWindowForTheWholeGroup() throws Exception {
        Long groupId = checkout();

        mockMvc.perform(withJwt(post(BASE + "/" + groupId + "/payment/ready"), consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderGroupId").value(groupId))
                .andExpect(jsonPath("$.data.redirectUrl").value("https://kakao.test/pay"))
                // 브랜드마다 결제창이 뜨면 같은 장바구니를 사면서 카드를 두 번 꺼내게 된다.
                .andExpect(jsonPath("$.data.totalPrice").value(GROUP_TOTAL))
                .andExpect(jsonPath("$.data.live").value(true));

        verify(paymentGateway, never()).approve(any());
        assertThat(productOrderGroupRepository.findById(groupId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderGroupStatus.CREATED);
    }

    @Test
    void theGatewaySeesTheGroupCodeAndTheGroupReturnAddress() throws Exception {
        // 복귀 주소가 단일 주문 라우트면 화면이 주문 한 건을 찾다 실패한다.
        Long groupId = checkout();

        mockMvc.perform(withJwt(post(BASE + "/" + groupId + "/payment/ready"), consumerId))
                .andExpect(status().isOk());

        ArgumentCaptor<PaymentReadyCommand> captor =
                ArgumentCaptor.forClass(PaymentReadyCommand.class);
        verify(paymentGateway).ready(captor.capture());
        assertThat(captor.getValue().orderId()).startsWith("ORG-");
        assertThat(captor.getValue().totalPrice()).isEqualTo(GROUP_TOTAL);
        assertThat(captor.getValue().returnUrl())
                .endsWith("/order-groups/" + groupId + "/payment/return");
    }

    @Test
    void theServerRemembersTheTidSoTheClientNeverHasToSendIt() throws Exception {
        Long groupId = checkout();

        ready(groupId);

        assertThat(productOrderGroupRepository.findById(groupId).orElseThrow()
                .getPaymentReference()).isEqualTo(TID);
    }

    @Test
    void someoneElseCannotStartPayingForYourGroup() throws Exception {
        Long groupId = checkout();

        mockMvc.perform(withJwt(post(BASE + "/" + groupId + "/payment/ready"), otherConsumerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_GROUP_NOT_FOUND"));

        verify(paymentGateway, never()).ready(any());
    }

    // ── 승인 ────────────────────────────────────────────────

    @Test
    void approvingPaysEveryOrderInTheGroupAtOnce() throws Exception {
        Long groupId = checkout();
        ready(groupId);

        mockMvc.perform(approveRequest(groupId, consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ProductOrderGroupStatus.PAID.name()))
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                .andExpect(jsonPath("$.data.orders[0].status")
                        .value(ProductOrderStatus.PAID.name()))
                .andExpect(jsonPath("$.data.orders[1].status")
                        .value(ProductOrderStatus.PAID.name()));

        // 브랜드 정산은 주문 단위다. 주문별 결제 금액의 합이 승인 금액과 같아야 한다.
        List<ProductOrder> orders =
                productOrderRepository.findAllByOrderGroupIdOrderByIdAsc(groupId);
        assertThat(orders).allSatisfy(order -> {
            assertThat(order.getPaymentReference()).isEqualTo(TID);
            assertThat(order.getPaymentAmount()).isEqualTo(order.getTotalPrice());
        });
        assertThat(orders.stream().mapToLong(ProductOrder::getPaymentAmount).sum())
                .isEqualTo(GROUP_TOTAL);
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.SOLD)).isEqualTo(1);
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                coatId, ProductItemStatus.SOLD)).isEqualTo(1);
    }

    @Test
    void payingClearsOnlyTheItemsThatWereOrdered() throws Exception {
        Long groupId = checkout();
        ready(groupId);

        mockMvc.perform(approveRequest(groupId, consumerId)).andExpect(status().isOk());

        // 고르지 않은 항목은 남는다. 사용자가 직접 담은 목록이다.
        assertThat(cartItemRepository.findMine(consumerId))
                .singleElement()
                .satisfies(item -> assertThat(item.getProductModelId()).isEqualTo(walletId));
    }

    @Test
    void theSameCheckoutWindowCannotBeApprovedTwice() throws Exception {
        // 결제창에서 뒤로 가기로 복귀 URL 을 다시 열면 실제로 두 번 들어온다.
        Long groupId = checkout();
        ready(groupId);
        mockMvc.perform(approveRequest(groupId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(approveRequest(groupId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_STATE_INVALID"));
    }

    @Test
    void approvingWithoutPreparingIsRefused() throws Exception {
        Long groupId = checkout();

        mockMvc.perform(approveRequest(groupId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_PREPARED"));

        verify(paymentGateway, never()).approve(any());
    }

    @Test
    void anApprovalForADifferentAmountIsNotRecorded() throws Exception {
        // PG 응답을 그대로 믿고 기록하면 금액이 어긋난 승인이 정상 결제로 남는다.
        Long groupId = checkout();
        ready(groupId);
        when(paymentGateway.approve(any()))
                .thenReturn(new PaymentApproval(TID, GROUP_TOTAL - 1_000L, LocalDateTime.now()));

        mockMvc.perform(approveRequest(groupId, consumerId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(productOrderGroupRepository.findById(groupId).orElseThrow().getStatus())
                .isEqualTo(ProductOrderGroupStatus.CREATED);
        assertThat(productOrderRepository.findAllByOrderGroupIdOrderByIdAsc(groupId))
                .allSatisfy(order -> assertThat(order.getStatus())
                        .isEqualTo(ProductOrderStatus.CREATED));
    }

    // ── 취소 ────────────────────────────────────────────────

    @Test
    void cancellingBeforePayingReleasesEveryItemAndRemovesTheGroup() throws Exception {
        Long groupId = checkout();
        ready(groupId);

        mockMvc.perform(cancelRequest(groupId, consumerId)).andExpect(status().isOk());

        assertThat(productOrderGroupRepository.findById(groupId)).isEmpty();
        assertThat(productOrderRepository.count()).isZero();
        assertThat(productOrderItemRepository.count()).isZero();
        assertThat(productShippingRepository.count()).isZero();
        assertThat(canceledOrderRepository.count()).isEqualTo(2);
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(1);
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                coatId, ProductItemStatus.IN_STOCK)).isEqualTo(1);
        // 승인 전이라 청구가 없었다. 환불을 요청하면 PG 가 거절한다.
        verify(paymentGateway, never()).cancel(any());
    }

    @Test
    void cancellingAPaidGroupRefundsTheOneChargeThatWasMade() throws Exception {
        Long groupId = checkout();
        ready(groupId);
        mockMvc.perform(approveRequest(groupId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(cancelRequest(groupId, consumerId)).andExpect(status().isOk());

        ArgumentCaptor<PaymentCancelCommand> captor =
                ArgumentCaptor.forClass(PaymentCancelCommand.class);
        // 브랜드마다 환불을 요청하지 않는다. 청구가 한 번이었으므로 환불도 한 번이다.
        verify(paymentGateway).cancel(captor.capture());
        assertThat(captor.getValue().tid()).isEqualTo(TID);
        assertThat(captor.getValue().cancelAmount()).isEqualTo(GROUP_TOTAL);
        assertThat(productOrderGroupRepository.findById(groupId)).isEmpty();
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(1);
    }

    @Test
    void oneShippedOrderBlocksTheWholeGroupAndSaysWhichOne() throws Exception {
        // 부분 취소가 없다. 아직 발송되지 않은 다른 브랜드 주문도 함께 막힌다.
        Long groupId = checkout();
        ready(groupId);
        mockMvc.perform(approveRequest(groupId, consumerId)).andExpect(status().isOk());
        ProductOrder shipped = shipOneOrderOf(groupId, brandBId);

        mockMvc.perform(cancelRequest(groupId, consumerId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_GROUP_NOT_CANCELABLE"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].orderId").value(shipped.getId()))
                .andExpect(jsonPath("$.data[0].orderCode").value(shipped.getOrderCode()))
                // 브랜드 id 는 화면에 쓸 수 없다. 사용자가 아는 이름이어야 한다.
                .andExpect(jsonPath("$.data[0].brandName").value("더스티밀러"));

        // 아무것도 지워지지 않는다.
        assertThat(productOrderRepository.count()).isEqualTo(2);
        assertThat(productOrderGroupRepository.findById(groupId)).isPresent();
        verify(paymentGateway, never()).cancel(any());
    }

    @Test
    void theGroupResponseSaysWhetherCancellingIsStillPossible() throws Exception {
        Long groupId = checkout();
        ready(groupId);
        mockMvc.perform(approveRequest(groupId, consumerId)).andExpect(status().isOk());

        mockMvc.perform(withJwt(get(BASE + "/" + groupId), consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelable").value(true));

        shipOneOrderOf(groupId, brandBId);

        mockMvc.perform(withJwt(get(BASE + "/" + groupId), consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelable").value(false));
    }

    @Test
    void someoneElseCannotCancelYourGroup() throws Exception {
        Long groupId = checkout();

        mockMvc.perform(cancelRequest(groupId, otherConsumerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_GROUP_NOT_FOUND"));

        assertThat(productOrderGroupRepository.findById(groupId)).isPresent();
    }

    // ── 조회 ────────────────────────────────────────────────

    @Test
    void theGroupCarriesItsBrandOrdersAndTheirShipping() throws Exception {
        Long groupId = checkout();

        mockMvc.perform(withJwt(get(BASE + "/" + groupId), consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderGroupCode").value(
                        org.hamcrest.Matchers.startsWith("ORG-")))
                .andExpect(jsonPath("$.data.totalPrice").value(GROUP_TOTAL))
                .andExpect(jsonPath("$.data.paidAt").doesNotExist())
                .andExpect(jsonPath("$.data.orders.length()").value(2))
                // 브랜드가 따로 보내므로 배송지는 주문마다다.
                .andExpect(jsonPath("$.data.orders[0].shipping.receiverName").value("홍길동"));
    }

    @Test
    void someoneElsesGroupIsNotFound() throws Exception {
        Long groupId = checkout();

        mockMvc.perform(withJwt(get(BASE + "/" + groupId), otherConsumerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_GROUP_NOT_FOUND"));
    }

    @Test
    void theOrderListTellsGroupedOrdersApartFromSingleOnes() throws Exception {
        // 같은 순간에 생긴 주문 여러 건이 서로 무관해 보이면 안 된다. 단일 주문에는
        // 그 값이 없어야 하고, 그래야 화면이 묶어 그릴지 말지 정할 수 있다.
        checkout();
        placeSingleOrder(walletId);

        mockMvc.perform(withJwt(get("/api/v1/product-orders/me"), consumerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                // 최신순이라 방금 만든 단일 주문이 맨 앞이다.
                .andExpect(jsonPath("$.data.content[0].orderGroupCode").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].orderGroupCode").value(
                        org.hamcrest.Matchers.startsWith("ORG-")));
    }

    // ── 만료 ────────────────────────────────────────────────

    @Test
    void anAbandonedGroupCheckoutLeavesNoEmptyGroupBehind() throws Exception {
        // 사용자가 탭을 그냥 닫으면 화면이 취소를 불러 주지 못한다. 주문만 지우면 주문이
        // 0건인 묶음이 남아 조회에 잡힌다.
        Long groupId = checkout();
        ready(groupId);
        expirePaymentDeadline(groupId);

        expiryWorker.expireUnpaidOrders();

        assertThat(productOrderGroupRepository.findById(groupId)).isEmpty();
        assertThat(productOrderRepository.count()).isZero();
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                bagId, ProductItemStatus.IN_STOCK)).isEqualTo(1);
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                coatId, ProductItemStatus.IN_STOCK)).isEqualTo(1);
        verify(paymentGateway, never()).cancel(any());
    }

    @Test
    void theSweepLeavesPaidGroupsAlone() throws Exception {
        // 배치가 목록을 읽은 뒤 승인이 들어올 수 있다. 그때 정상 결제를 지우면 안 된다.
        Long groupId = checkout();
        ready(groupId);
        expirePaymentDeadline(groupId);
        mockMvc.perform(approveRequest(groupId, consumerId)).andExpect(status().isOk());

        expiryWorker.expireUnpaidOrders();

        assertThat(productOrderGroupRepository.findById(groupId)).isPresent();
        assertThat(productOrderRepository.count()).isEqualTo(2);
    }

    // ── helpers ─────────────────────────────────────────────

    /** 장바구니에서 두 브랜드 상품을 골라 묶음을 만든다. 지갑은 고르지 않는다. */
    private Long checkout() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("productModelIds", List.of(bagId, coatId));
        request.put("addressId", addressId);

        String response = mockMvc.perform(withJwt(post(CHECKOUT), consumerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("orderGroupId").asLong();
    }

    private void placeSingleOrder(Long productModelId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("productModelId", productModelId);
        request.put("addressId", addressId);

        mockMvc.perform(withJwt(post("/api/v1/product-orders"), consumerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private void ready(Long groupId) throws Exception {
        mockMvc.perform(withJwt(post(BASE + "/" + groupId + "/payment/ready"), consumerId))
                .andExpect(status().isOk());
    }

    /** 브랜드가 발송한 상태로 만든다. 발송 API 는 이 테스트의 관심사가 아니다. */
    private ProductOrder shipOneOrderOf(Long groupId, Long brandId) {
        ProductOrder order = productOrderRepository.findAllByOrderGroupIdOrderByIdAsc(groupId)
                .stream()
                .filter(found -> found.getBrandId().equals(brandId))
                .findFirst()
                .orElseThrow();
        order.ship(LocalDateTime.now());
        return productOrderRepository.saveAndFlush(order);
    }

    /**
     * 결제 마감을 과거로 옮긴다. 30분을 실제로 기다릴 수는 없다.
     *
     * <p>엔티티에 마감을 바꾸는 메서드를 만들지 않는다 — 운영 코드가 쓸 일이 없는 통로를
     * 테스트 편의로 열면 언젠가 실제 코드가 그걸 쓴다. 묶음과 주문이 같은 마감을 공유하므로
     * 둘 다 옮긴다.
     */
    private void expirePaymentDeadline(Long groupId) {
        LocalDateTime past = LocalDateTime.now().minusMinutes(1);
        productOrderGroupRepository.findById(groupId).ifPresent(group -> {
            new org.springframework.beans.DirectFieldAccessor(group)
                    .setPropertyValue("paymentDeadline", past);
            productOrderGroupRepository.saveAndFlush(group);
        });
        productOrderRepository.findAllByOrderGroupIdOrderByIdAsc(groupId).forEach(order -> {
            new org.springframework.beans.DirectFieldAccessor(order)
                    .setPropertyValue("paymentDeadline", past);
            productOrderRepository.saveAndFlush(order);
        });
    }

    private MockHttpServletRequestBuilder approveRequest(Long groupId, Long userId)
            throws Exception {
        return withJwt(post(BASE + "/" + groupId + "/payment/approve"), userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("pgToken", PG_TOKEN)));
    }

    private MockHttpServletRequestBuilder cancelRequest(Long groupId, Long userId)
            throws Exception {
        return withJwt(post(BASE + "/" + groupId + "/cancel"), userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("reason", "변심")));
    }

    private MockHttpServletRequestBuilder withJwt(
            MockHttpServletRequestBuilder builder, Long userId) {
        return builder.with(jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CONSUMER")));
    }

    private void addToCart(Long productModelId, long addedPrice) {
        cartItemRepository.saveAndFlush(
                ConsumerCartItem.of(consumerId, productModelId, 1, addedPrice));
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

    private void saveItems(Long productModelId, int count) {
        for (int i = 0; i < count; i++) {
            serialNumber++;
            productItemRepository.saveAndFlush(ProductItem.produce(
                    1L,
                    productModelId,
                    serialNumber,
                    "GROUP-ITEM-" + serialNumber,
                    String.format("%032d", 800_000 + serialNumber)));
        }
    }

    private void clean() {
        canceledOrderItemRepository.deleteAll();
        canceledOrderRepository.deleteAll();
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
