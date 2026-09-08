package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 등록·판매 상태 엔드포인트.
 *
 * <p>고정하는 계약이 넷이다 — <b>인증이 필요하다</b>, <b>{@code BRAND} 만 등록한다</b>,
 * <b>등록은 언제나 {@code DRAFT} 다</b>, <b>재고 없는 제품은 판매를 시작할 수 없다</b>.
 *
 * <p>세 번째가 이 티켓의 핵심이다. 요청이 상태를 지정할 수 있게 되면 개체가 하나도 없는
 * 제품이 {@code ON_SALE} 로 마켓에 뜨고, 들어온 손님은 살 수 없는 물건을 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductModelControllerTests {

    private static final String PATH = "/api/v1/product-models";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    private User brand;
    private String brandToken;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "product-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
    }

    // ── 인증·권한 ────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        // SecurityConfig 의 permitAll 목록에 이 경로가 들어가면 여기서 깨진다.
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsASupplierAccount() throws Exception {
        // 소재를 올릴 수 있다는 것과 제품을 팔 수 있다는 것은 별개 권한이다.
        User supplier = userRepository.save(User.register(
                "product-supplier@example.com", "{noop}x", "공급자", "010-1111-1111", UserRole.SUPPLIER));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(supplier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROLE_NOT_ALLOWED"));
    }

    @Test
    void rejectsAConsumerAccount() throws Exception {
        User consumer = userRepository.save(User.register(
                "product-consumer@example.com", "{noop}x", "소비자", "010-2222-2222", UserRole.CONSUMER));

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(consumer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_ROLE_NOT_ALLOWED"));
    }

    @Test
    void rejectsASuspendedBrand() throws Exception {
        // 토큰은 발급 시점의 스냅샷이다. 정지된 뒤에도 만료 전까지 등록할 수 있으면 안 된다.
        String tokenIssuedBeforeSuspension = tokenFor(brand);
        brand.suspend();
        userRepository.save(brand);

        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenIssuedBeforeSuspension)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCT_BRAND_SUSPENDED"));
    }

    // ── 등록 ────────────────────────────────────────────────

    @Test
    void registersAProductModelAsDraft() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productModelId").isNumber())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.stockQuantity").value(0));
    }

    @Test
    void takesTheBrandFromTheTokenNotTheBody() throws Exception {
        // 바디로 받으면 남의 이름으로 제품을 올릴 수 있다.
        User otherBrand = userRepository.save(User.register(
                "product-other-brand@example.com", "{noop}x", "다른브랜드", "010-3333-3333", UserRole.BRAND));
        Map<String, Object> request = validRequest();
        request.put("brandId", otherBrand.getId());

        Long modelId = registeredId(request);

        assertThat(productModelRepository.findById(modelId))
                .get()
                .extracting(ProductModel::getBrandId)
                .isEqualTo(brand.getId());
    }

    @Test
    void ignoresAStatusSentByTheRequest() throws Exception {
        // 이 티켓의 핵심. 요청이 상태를 정할 수 있으면 개체 없는 제품이 마켓에 뜬다.
        Map<String, Object> request = validRequest();
        request.put("status", "ON_SALE");

        Long modelId = registeredId(request);

        assertThat(productModelRepository.findById(modelId))
                .get()
                .extracting(ProductModel::getStatus)
                .isEqualTo(ProductModelStatus.DRAFT);
    }

    // ── 등록 검증 실패 ────────────────────────────────────────

    @Test
    void rejectsAMissingName() throws Exception {
        Map<String, Object> request = validRequest();
        request.remove("name");

        expectBadRequest(request);
    }

    @Test
    void rejectsAnUnknownCategory() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("category", "SHOES");

        expectBadRequest(request);
    }

    @Test
    void rejectsANegativePrice() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("price", -1);

        expectBadRequest(request);
    }

    @Test
    void rejectsAFractionalPrice() throws Exception {
        // price 를 Long 으로 둔 이유. 12000.5 가 12000 으로 조용히 버려지면 안 된다.
        Map<String, Object> request = validRequest();
        request.put("price", 12_000.5);

        expectBadRequest(request);
    }

    @Test
    void rejectsAMissingThumbnail() throws Exception {
        Map<String, Object> request = validRequest();
        request.remove("thumbnailUrl");

        expectBadRequest(request);
    }

    // ── 판매 상태 변경 ────────────────────────────────────────

    @Test
    void refusesToStartSellingWithoutStock() throws Exception {
        // 개체가 없는 제품을 올리면 손님이 살 수 없는 물건을 본다.
        Long modelId = registeredId(validRequest());

        changeStatus(brandToken, modelId, "ON_SALE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_HAS_NO_STOCK"));
    }

    @Test
    void startsSellingOnceItemsExist() throws Exception {
        Long modelId = registeredId(validRequest());
        produceItem(modelId, 1);

        changeStatus(brandToken, modelId, "ON_SALE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.stockQuantity").value(1));
    }

    @Test
    void discontinuesWithoutStock() throws Exception {
        // 단종은 재고와 무관하다. 팔던 물건이 없어도 내릴 수 있어야 한다.
        Long modelId = registeredId(validRequest());

        changeStatus(brandToken, modelId, "DISCONTINUE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISCONTINUED"));
    }

    @Test
    void refusesToReviveADiscontinuedModel() throws Exception {
        // 단종은 되돌릴 수 없다. 다시 팔려면 새 모델로 등록한다.
        Long modelId = registeredId(validRequest());
        produceItem(modelId, 1);
        changeStatus(brandToken, modelId, "DISCONTINUE").andExpect(status().isOk());

        changeStatus(brandToken, modelId, "ON_SALE")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_DISCONTINUED"));
    }

    @Test
    void refusesToTouchAModelOwnedByAnotherBrand() throws Exception {
        // 역할만 보면 브랜드 아무나 남의 제품을 단종시킬 수 있다.
        Long modelId = registeredId(validRequest());
        User otherBrand = userRepository.save(User.register(
                "product-intruder@example.com", "{noop}x", "침입자", "010-4444-4444", UserRole.BRAND));

        changeStatus(tokenFor(otherBrand), modelId, "DISCONTINUE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PRODUCT_MODEL_OWNER"));
    }

    @Test
    void rejectsAnUnknownModel() throws Exception {
        changeStatus(brandToken, 999_999L, "ON_SALE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    @Test
    void refusesToSetSoldOutByHand() throws Exception {
        // 품절은 개체를 세어 나오는 사실이라 손으로 지정할 수 없다.
        // 손으로 바꿀 수 있으면 재고가 있는데 품절인 상태가 만들어진다.
        Long modelId = registeredId(validRequest());

        changeStatus(brandToken, modelId, "SOLD_OUT")
                .andExpect(status().isBadRequest());
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG");
        request.put("category", "BAG");
        request.put("price", 129_000);
        request.put("description", "폐데님으로 만든 가방");
        request.put("thumbnailUrl", "https://cdn.example.com/rebag.jpg");
        return request;
    }

    /**
     * 개체를 하나 만들어 재고를 채운다.
     *
     * <p>{@code production_batch_id} 를 임의의 값으로 둔다. 개체는 배치를 연관관계가 아니라
     * FK 값으로 들고 있어서, 판매 상태 판정에 배치 행이 필요하지 않다.
     */
    private void produceItem(Long modelId, int serialNumber) {
        productItemRepository.save(ProductItem.produce(
                1L,
                modelId,
                serialNumber,
                "ITEM-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString().replace("-", "")));
    }

    private ResultActions changeStatus(String token, Long modelId, String action) throws Exception {
        return mockMvc.perform(patch(PATH + "/{productModelId}/status", modelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("action", action))));
    }

    private void expectBadRequest(Map<String, Object> request) throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Long registeredId(Map<String, Object> request) throws Exception {
        String response = mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productModelId").asLong();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
