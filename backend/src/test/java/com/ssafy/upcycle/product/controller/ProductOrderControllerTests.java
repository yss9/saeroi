package com.ssafy.upcycle.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 소비자 본인 주문 목록과 마이페이지 상태 요약 API. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductOrderControllerTests {

    private static final String PATH = "/api/v1/product-orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

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

    private User consumer;
    private User brand;
    private ProductModel model;
    private String consumerToken;

    @BeforeEach
    void setUp() {
        consumer = userRepository.save(User.register(
                "order-consumer@example.com",
                "{noop}x",
                "소비자",
                "010-1111-1111",
                UserRole.CONSUMER));
        brand = userRepository.save(User.register(
                "order-brand@example.com",
                "{noop}x",
                "브랜드 대표",
                "010-2222-2222",
                UserRole.BRAND));
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                "리위브",
                "123-45-67890",
                "04790",
                "서울특별시 성동구 연무장길 1",
                "3층",
                new GeoCoordinates(
                        new BigDecimal("37.5446000"), new BigDecimal("127.0559000"))));
        model = productModelRepository.save(ProductModel.register(
                "데님 서클 백",
                ProductCategory.BAG,
                brand.getId(),
                129_000,
                "폐데님으로 만든 가방",
                "product/thumbnail.jpg"));
        consumerToken = tokenFor(consumer);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyConsumersCanReadConsumerOrders() throws Exception {
        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(brand)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ROLE_NOT_ALLOWED"));
    }

    @Test
    void suspendedConsumersCannotReadOrders() throws Exception {
        consumer.suspend();
        userRepository.flush();

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_BUYER_SUSPENDED"));
    }

    @Test
    void returnsOnlyTheAuthenticatedConsumersOrders() throws Exception {
        ProductOrder mine = createOrder(consumer, "ORDER-MINE", OrderState.PAID);
        User otherConsumer = userRepository.save(User.register(
                "other-order-consumer@example.com",
                "{noop}x",
                "다른 소비자",
                "010-3333-3333",
                UserRole.CONSUMER));
        createOrder(otherConsumer, "ORDER-OTHER", OrderState.PAID);

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderCode").value(mine.getOrderCode()))
                // 목록에서 바로 결제를 이어가려면 여기서도 orderId 를 얻을 수 있어야 한다.
                .andExpect(jsonPath("$.data.content[0].orderId").value(mine.getId()));
    }

    @Test
    void returnsProductAndJourneyInformationWithoutInternalIds() throws Exception {
        ProductOrder order = createOrder(consumer, "ORDER-JOURNEY", OrderState.SHIPPED);
        ProductOrderItem orderItem = productOrderItemRepository
                .findAllByProductOrderId(order.getId())
                .getFirst();
        ProductItem item = productItemRepository.findById(orderItem.getProductItemId()).orElseThrow();

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].brandName").value("리위브"))
                .andExpect(jsonPath("$.data.content[0].totalPrice").value(129_000))
                .andExpect(jsonPath("$.data.content[0].status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.content[0].orderedAt").exists())
                .andExpect(jsonPath("$.data.content[0].items[0].itemCode")
                        .value(item.getItemCode()))
                .andExpect(jsonPath("$.data.content[0].items[0].serialNumber").value(1))
                .andExpect(jsonPath("$.data.content[0].items[0].productName")
                        .value("데님 서클 백"))
                .andExpect(jsonPath("$.data.content[0].items[0].category").value("BAG"))
                .andExpect(jsonPath("$.data.content[0].items[0].price").value(129_000))
                .andExpect(jsonPath("$.data.content[0].items[0].imageUrl")
                        .value("/api/v1/product-models/" + model.getId() + "/thumbnail"))
                .andExpect(jsonPath("$.data.content[0].items[0].journeyUrl")
                        .value("https://upcycle.test/qr/" + item.getQrToken()))
                .andExpect(jsonPath("$.data.content[0].items[0].productItemId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].items[0].qrToken").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].buyerId").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].paymentReference").doesNotExist());
    }

    @Test
    void doesNotExposeTheJourneyBeforePayment() throws Exception {
        createOrder(consumer, "ORDER-UNPAID", OrderState.CREATED);

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].items[0].journeyUrl").isEmpty());
    }

    @Test
    void filtersByOrderStatus() throws Exception {
        createOrder(consumer, "ORDER-PAID", OrderState.PAID);
        createOrder(consumer, "ORDER-SHIPPED", OrderState.SHIPPED);

        mockMvc.perform(get(PATH + "/me")
                        .queryParam("status", "PAID")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].orderCode").value("ORDER-PAID"));
    }

    @Test
    void returnsStatusCountsIndependentlyOfListPaging() throws Exception {
        createOrder(consumer, "ORDER-CREATED", OrderState.CREATED);
        createOrder(consumer, "ORDER-PAID", OrderState.PAID);
        createOrder(consumer, "ORDER-SHIPPED", OrderState.SHIPPED);
        createOrder(consumer, "ORDER-DELIVERED", OrderState.DELIVERED);

        mockMvc.perform(get(PATH + "/me/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.paid").value(1))
                .andExpect(jsonPath("$.data.shipped").value(1))
                .andExpect(jsonPath("$.data.delivered").value(1));
    }

    // ── 단건 조회 ───────────────────────────────────────────

    @Test
    void oneOrderCanBeReadByItsOwnId() throws Exception {
        // 결제창에서 돌아온 화면이 그 주문을 다시 읽는 경로다. 이 경로가 없으면 화면은
        // 주문 목록을 페이지마다 훑으며 같은 id 를 찾아야 한다.
        ProductOrder order = createOrder(consumer, "ORDER-ONE", OrderState.PAID);

        mockMvc.perform(get(PATH + "/" + order.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(order.getId()))
                .andExpect(jsonPath("$.data.orderCode").value("ORDER-ONE"))
                // 목록과 같은 모양이어야 화면이 타입을 한 벌만 다룬다.
                .andExpect(jsonPath("$.data.brandName").value("리위브"))
                .andExpect(jsonPath("$.data.items[0].productName").value("데님 서클 백"))
                .andExpect(jsonPath("$.data.shipping").exists())
                .andExpect(jsonPath("$.data.buyerId").doesNotExist())
                .andExpect(jsonPath("$.data.paymentReference").doesNotExist());
    }

    @Test
    void someoneElsesOrderIsNotFoundRatherThanForbidden() throws Exception {
        // 403 은 그 id 의 주문이 존재한다는 사실을 흘린다. 주문 id 는 순번이라 하나씩
        // 올려 보며 남의 주문 유무를 셀 수 있다.
        User otherConsumer = userRepository.save(User.register(
                "one-order-other@example.com",
                "{noop}x",
                "다른 소비자",
                "010-4444-4444",
                UserRole.CONSUMER));
        ProductOrder theirs = createOrder(otherConsumer, "ORDER-THEIRS", OrderState.PAID);

        mockMvc.perform(get(PATH + "/" + theirs.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_FOUND"));
    }

    @Test
    void anOrderThatDoesNotExistIsNotFoundTheSameWay() throws Exception {
        mockMvc.perform(get(PATH + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_NOT_FOUND"));
    }

    @Test
    void readingOneOrderNeedsALoginAndAConsumerAccount() throws Exception {
        ProductOrder order = createOrder(consumer, "ORDER-AUTH", OrderState.PAID);

        mockMvc.perform(get(PATH + "/" + order.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/" + order.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(brand)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ORDER_ROLE_NOT_ALLOWED"));
    }

    @Test
    void theListPathIsNotSwallowedByTheSingleOrderPath() throws Exception {
        // `/me` 는 리터럴이라 `/{orderId}` 보다 먼저 매칭되어야 한다. 순서가 뒤집히면
        // 주문 목록이 "me 라는 id 의 주문" 조회가 된다.
        createOrder(consumer, "ORDER-LIST", OrderState.PAID);

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderCode").value("ORDER-LIST"));
    }

    @Test
    void aSingleOrderCarriesNoGroupIdOrCode() throws Exception {
        // 묶음에 속하지 않은 주문이다. 둘 다 없어야 화면이 묶음 경로로 보내지 않는다.
        ProductOrder order = createOrder(consumer, "ORDER-SINGLE", OrderState.PAID);

        mockMvc.perform(get(PATH + "/" + order.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderGroupId").doesNotExist())
                .andExpect(jsonPath("$.data.orderGroupCode").doesNotExist());
    }

    private ProductOrder createOrder(User buyer, String orderCode, OrderState state) {
        ProductItem item = productItemRepository.save(ProductItem.produce(
                1L,
                model.getId(),
                1,
                "ITEM-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString().replace("-", "")));
        item.reserve();

        LocalDateTime now = LocalDateTime.now();
        ProductOrder order = ProductOrder.place(
                orderCode,
                buyer.getId(),
                brand.getId(),
                129_000,
                PaymentMethod.KAKAO_PAY,
                now.plusMinutes(30));
        if (state != OrderState.CREATED) {
            item.markSold();
            order.markPaid("PAY-" + orderCode, 129_000, now, PaymentMethod.MOCK);
        }
        if (state == OrderState.SHIPPED || state == OrderState.DELIVERED) {
            order.ship(now.plusDays(1));
        }
        if (state == OrderState.DELIVERED) {
            order.confirmDelivery(now.plusDays(2));
        }

        productItemRepository.save(item);
        ProductOrder saved = productOrderRepository.save(order);
        productOrderItemRepository.save(ProductOrderItem.of(
                saved.getId(), item.getId(), model.getId(), 129_000));

        // 주문은 항상 배송지와 함께 만들어진다(ProductOrderCommandService.place).
        // 그 단계를 건너뛰면 어디로 보낼지 모르는 주문이 된다.
        ProductShipping shipping = ProductShipping.of(
                saved.getId(), "홍길동", "010-1111-2222",
                "06236", "서울특별시 강남구 테헤란로 212", "5층");
        if (state == OrderState.SHIPPED || state == OrderState.DELIVERED) {
            shipping.registerShipment("CJ대한통운", "123456789012", now.plusDays(1));
        }
        productShippingRepository.save(shipping);
        return saved;
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private enum OrderState {
        CREATED,
        PAID,
        SHIPPED,
        DELIVERED
    }
}
