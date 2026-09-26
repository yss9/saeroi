package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
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
 * {@code GET /api/v1/product-orders/brand/me} — 브랜드에게 들어온 주문.
 *
 * <p><b>이 목록이 없으면 발송 API 가 쓸모가 없다.</b> 발송은 주문 번호를 받는데 브랜드가 그
 * 번호를 알 길이 여기뿐이다. 그래서 이 테스트가 지키는 것은 두 가지다 — <b>보낼 주소가 실려
 * 나오는가</b>, 그리고 <b>남의 주문이 섞이지 않는가</b>. 후자가 깨지면 다른 브랜드의 고객
 * 집 주소가 그대로 노출된다.
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BrandProductOrderListApiTests {

    private static final String BASE = "/api/v1/product-orders";
    private static final String PATH = BASE + "/brand/me";
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

    private Long brandId;
    private Long otherBrandId;
    private Long consumerId;
    private Long modelId;
    private Long addressId;

    @BeforeEach
    void setUp() {
        clean();
        when(paymentGateway.isLive()).thenReturn(true);
        when(paymentGateway.ready(any()))
                .thenReturn(new PaymentReady(TID, "https://kakao.test/pay"));
        when(paymentGateway.approve(any()))
                .thenReturn(new PaymentApproval(TID, PRICE, LocalDateTime.now()));

        brandId = saveUser("brandlist-brand@test.com", "김대표", "02-1234-5678", UserRole.BRAND);
        otherBrandId = saveUser("brandlist-other@test.com", "박대표", "02-9999-8888", UserRole.BRAND);
        consumerId = saveUser("brandlist-buyer@test.com", "홍길동", "010-1111-2222", UserRole.CONSUMER);
        addressId = saveAddress(consumerId);
        modelId = saveModel(brandId);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 보낼 주소 ───────────────────────────────────────────

    @Test
    void theListCarriesEverythingNeededToPackAndSend() throws Exception {
        // 주소가 없으면 브랜드는 이 화면에서 아무것도 할 수 없다.
        saveItem(1);
        Long orderId = paidOrder();

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(orderId))
                .andExpect(jsonPath("$.data.content[0].orderCode").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].status").value(ProductOrderStatus.PAID.name()))
                .andExpect(jsonPath("$.data.content[0].totalPrice").value(PRICE))
                .andExpect(jsonPath("$.data.content[0].shipping.receiverName").value("홍수령"))
                .andExpect(jsonPath("$.data.content[0].shipping.receiverPhone").value("010-9999-0000"))
                .andExpect(jsonPath("$.data.content[0].shipping.zipCode").value("06236"))
                .andExpect(jsonPath("$.data.content[0].shipping.address")
                        .value("서울특별시 강남구 테헤란로 212"))
                .andExpect(jsonPath("$.data.content[0].shipping.addressDetail").value("5층"))
                // 아직 안 보낸 주문이다.
                .andExpect(jsonPath("$.data.content[0].shipping.shippedAt").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].shipping.trackingNumber").doesNotExist());
    }

    @Test
    void theReceiverIsShownNotTheBuyerWhenTheyDiffer() throws Exception {
        // 선물이나 가족 집으로 보내는 주문이다. 송장에 주문자 이름을 적으면 물건이 엉뚱한
        // 사람 앞으로 간다.
        saveItem(1);
        paidOrder();

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].buyerName").value("홍길동"))
                .andExpect(jsonPath("$.data.content[0].shipping.receiverName").value("홍수령"));
    }

    @Test
    void theItemCodeTellsWhichPieceToPick() throws Exception {
        // 같은 모델이라도 개체마다 다른 물건이다. 이름만 보고 집으면 QR 이 다른 제품이 나간다.
        Long itemId = saveItem(1);
        paidOrder();

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].items.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].items[0].productName").value("업사이클 토트백"))
                .andExpect(jsonPath("$.data.content[0].items[0].serialNumber").value(1))
                .andExpect(jsonPath("$.data.content[0].items[0].price").value(PRICE));

        assertThat(productItemRepository.findById(itemId)).isPresent();
    }

    // ── 범위 ────────────────────────────────────────────────

    @Test
    void aBrandNeverSeesAnotherBrandsOrders() throws Exception {
        // 깨지면 다른 브랜드의 고객 집 주소가 그대로 노출된다.
        saveItem(1);
        paidOrder();

        mockMvc.perform(withJwt(get(PATH), otherBrandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void consumersHaveNoUseForThisList() throws Exception {
        saveItem(1);
        paidOrder();

        mockMvc.perform(withJwt(get(PATH), consumerId, UserRole.CONSUMER))
                .andExpect(status().isForbidden());
    }

    @Test
    void theListNeedsALogin() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    // ── 거르기 ──────────────────────────────────────────────

    @Test
    void filteringByPaidShowsExactlyWhatIsWaitingToBeSent() throws Exception {
        // 발송 화면이 읽는 조건이다. 결제가 끝났고 아직 보내지 않은 주문이 정확히 그 상태다.
        saveItem(1);
        saveItem(2);
        saveItem(3);
        Long waiting = paidOrder();
        Long sent = paidOrder();
        ship(sent);
        placeOrder();  // 결제 전

        mockMvc.perform(withJwt(get(PATH + "?status=PAID"), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].orderId").value(waiting));
    }

    @Test
    void theCountIsReadableWithoutFetchingEveryRow() throws Exception {
        // "발송 대기 N건" 은 목록을 다 받아오지 않고도 알 수 있어야 한다.
        saveItem(1);
        saveItem(2);
        paidOrder();
        paidOrder();

        mockMvc.perform(withJwt(get(PATH + "?status=PAID&size=1"), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void withoutAFilterEveryStageIsThere() throws Exception {
        saveItem(1);
        saveItem(2);
        paidOrder();
        placeOrder();

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    @Test
    void theNewestOrderComesFirst() throws Exception {
        saveItem(1);
        saveItem(2);
        paidOrder();
        Long newest = paidOrder();

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderId").value(newest));
    }

    // ── 발송과 이어지는가 ───────────────────────────────────

    @Test
    void theOrderIdFromThisListIsWhatTheShipCallNeeds() throws Exception {
        // 브랜드 화면이 실제로 걷는 길이다. 목록에서 얻은 번호로 바로 발송할 수 있어야 한다.
        saveItem(1);
        paidOrder();

        String response = mockMvc.perform(withJwt(get(PATH + "?status=PAID"), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long orderId = objectMapper.readTree(response)
                .path("data").path("content").get(0).path("orderId").asLong();

        mockMvc.perform(withJwt(patch(BASE + "/" + orderId + "/ship"), brandId, UserRole.BRAND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aSentOrderShowsItsWaybill() throws Exception {
        // 고객 문의가 오면 브랜드가 이 화면에서 송장 번호를 읽는다.
        saveItem(1);
        Long orderId = paidOrder();
        ship(orderId);

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status")
                        .value(ProductOrderStatus.SHIPPED.name()))
                .andExpect(jsonPath("$.data.content[0].shipping.carrier").value("CJ대한통운"))
                .andExpect(jsonPath("$.data.content[0].shipping.trackingNumber")
                        .value("123456789012"))
                .andExpect(jsonPath("$.data.content[0].shipping.shippedAt").isNotEmpty());
    }

    @Test
    void aCancelledOrderLeavesTheListEntirely() throws Exception {
        // 취소는 행을 지운다. 목록에 남으면 브랜드가 없는 주문을 발송하려 든다.
        saveItem(1);
        Long orderId = paidOrder();

        mockMvc.perform(withJwt(post(BASE + "/" + orderId + "/cancel"), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        mockMvc.perform(withJwt(get(PATH), brandId, UserRole.BRAND))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
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
        mockMvc.perform(withJwt(patch(BASE + "/" + orderId + "/ship"), brandId, UserRole.BRAND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ대한통운\",\"trackingNumber\":\"123456789012\"}"))
                .andExpect(status().isOk());
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

    /** 수령인을 주문자와 다르게 둔다 — 브랜드가 어느 이름을 봐야 하는지 갈리는 자리다. */
    private Long saveAddress(Long userId) {
        ConsumerAddress address = ConsumerAddress.register(
                userId, "부모님 댁", "홍수령", "010-9999-0000",
                "06236", "서울특별시 강남구 테헤란로 212", "5층");
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
