package com.ssafy.upcycle.inquiry.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.inquiry.repository.ConsumerInquiryRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code /api/v1/consumers/me/inquiries} — 소비자 1:1 문의.
 *
 * <p>여기서 지키는 것은 두 가지다. <b>등록한 문의가 바로 목록에 보여야 하고</b>(지금까지
 * 프론트 목데이터라 반영되지 않던 증상), <b>남의 문의는 보이지도 만져지지도 않는다.</b>
 *
 * <p>{@code @Transactional} 로 각 테스트를 롤백한다 ({@code ProductOrderControllerTests} 와
 * 같은 방식) — 주문 픽스처까지 매번 손으로 지우면 테스트가 장황해진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsumerInquiryControllerTests {

    private static final String BASE = "/api/v1/consumers/me/inquiries";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsumerInquiryRepository inquiryRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private ProductOrderRepository productOrderRepository;

    @Autowired
    private ProductOrderItemRepository productOrderItemRepository;

    private Long consumerId;
    private Long otherConsumerId;
    private Long brandId;

    @BeforeEach
    void setUp() {
        consumerId = saveUser("inquiry-consumer@test.com", "홍길동", "010-1111-2222", UserRole.CONSUMER);
        otherConsumerId = saveUser("inquiry-other@test.com", "김이웃", "010-3333-4444", UserRole.CONSUMER);
        brandId = saveUser("inquiry-brand@test.com", "김대표", "02-1234-5678", UserRole.BRAND);
    }

    // ── 조회 ────────────────────────────────────────────────

    @Test
    void anAccountWithNoInquiryGetsAnEmptyListNotAnError() throws Exception {
        mockMvc.perform(withJwt(get(BASE), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aNewlySubmittedInquiryShowsUpInTheListRightAway() throws Exception {
        // 지금까지 겪던 증상 — 문의하기에서 등록해도 문의내역에 안 보였다.
        Map<String, Object> request = etcBody("세탁 방법이 궁금합니다.");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("세탁 방법이 궁금합니다."))
                .andExpect(jsonPath("$.data.answered").value(false));

        mockMvc.perform(withJwt(get(BASE), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("세탁 방법이 궁금합니다."));
    }

    @Test
    void oneConsumerNeverSeesAnothersInquiries() throws Exception {
        mockMvc.perform(withJwt(post(BASE), otherConsumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etcBody("남의 문의"))))
                .andExpect(status().isOk());

        mockMvc.perform(withJwt(get(BASE), consumerId, UserRole.CONSUMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ── 등록 ────────────────────────────────────────────────

    @Test
    void anEtcInquiryNeedsNoOrder() throws Exception {
        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etcBody("기타 문의입니다."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetLabel").doesNotExist());
    }

    @Test
    void aDeliveryInquiryWithoutAnOrderIsRejected() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "DELIVERY");
        request.put("content", "배송이 늦어지고 있습니다.");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INQUIRY_TARGET_REQUIRED"));
    }

    @Test
    void aBlankContentIsRejected() throws Exception {
        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etcBody("   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anOrderInquiryCapturesTheProductNameAsASnapshot() throws Exception {
        Long orderId = createOrderWithItem(consumerId, "홑겹 니트");

        Map<String, Object> request = new HashMap<>();
        request.put("type", "PRODUCT");
        request.put("productOrderId", orderId);
        request.put("content", "세탁 방법이 궁금합니다.");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetLabel").value("홑겹 니트"))
                .andExpect(jsonPath("$.data.productOrderId").value(orderId));
    }

    @Test
    void pointingAtSomeoneElsesOrderLooksExactlyLikeAMissingOrder() throws Exception {
        Long theirOrderId = createOrderWithItem(otherConsumerId, "남의 주문 상품");

        Map<String, Object> request = new HashMap<>();
        request.put("type", "PRODUCT");
        request.put("productOrderId", theirOrderId);
        request.put("content", "이 주문에 대해 문의합니다.");

        mockMvc.perform(withJwt(post(BASE), consumerId, UserRole.CONSUMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INQUIRY_ORDER_NOT_FOUND"));

        assertThat(inquiryRepository.findAllByUserIdOrderByIdDesc(consumerId)).isEmpty();
    }

    // ── 역할 ────────────────────────────────────────────────

    @Test
    void businessAccountsCannotFileInquiries() throws Exception {
        mockMvc.perform(withJwt(get(BASE), brandId, UserRole.BRAND))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSUMER_INQUIRY_FORBIDDEN"));
    }

    @Test
    void theInquiryBoxNeedsALogin() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder withJwt(
            MockHttpServletRequestBuilder builder, Long userId, UserRole role) {
        return builder.with(jwt()
                .jwt(token -> token.subject(String.valueOf(userId)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private Map<String, Object> etcBody(String content) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", "ETC");
        request.put("content", content);
        return request;
    }

    private Long createOrderWithItem(Long buyerId, String productName) {
        ProductModel model = productModelRepository.save(ProductModel.register(
                productName, ProductCategory.CLOTHING, brandId, 49_000, "설명", "product/thumbnail.jpg"));
        ProductItem item = productItemRepository.save(ProductItem.produce(
                1L,
                model.getId(),
                1,
                "ITEM-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString().replace("-", "")));
        item.reserve();

        LocalDateTime now = LocalDateTime.now();
        ProductOrder order = ProductOrder.place(
                "ORDER-" + UUID.randomUUID().toString().substring(0, 20),
                buyerId,
                brandId,
                49_000,
                PaymentMethod.KAKAO_PAY,
                now.plusMinutes(30));
        ProductOrder saved = productOrderRepository.save(order);
        productOrderItemRepository.save(
                ProductOrderItem.of(saved.getId(), item.getId(), model.getId(), 49_000));
        return saved.getId();
    }

    private Long saveUser(String email, String name, String phone, UserRole role) {
        return userRepository.save(User.register(email, "{noop}x", name, phone, role)).getId();
    }
}
