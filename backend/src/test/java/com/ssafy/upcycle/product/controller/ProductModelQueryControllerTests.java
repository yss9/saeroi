package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 상세·본인 목록 조회.
 *
 * <p>고정하는 계약이 넷이다 — <b>인증이 필요하다</b>, <b>상세는 역할을 보지 않는다</b>,
 * <b>{@code /me} 는 본인 것만 준다</b>, <b>{@code /me} 는 {@code DRAFT} 를 포함한다</b>.
 *
 * <p>마지막 둘이 이 티켓의 핵심이다. {@code /me} 가 남의 제품을 섞어 주면 아직 공개하지
 * 않은 {@code DRAFT} 가 새고, 반대로 {@code DRAFT} 를 빼면 브랜드가 등록한 제품을 찾지
 * 못해 판매 시작을 누를 자리가 없어진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductModelQueryControllerTests {

    private static final String PATH = "/api/v1/product-models";

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("product-model-query-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없다", e);
        }
    }

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User brand;
    private String brandToken;
    private String thumbnailKey;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "product-query-brand@example.com", "{noop}x", "브랜드", "010-0000-0000",
                UserRole.BRAND));
        brandToken = tokenFor(brand);
        thumbnailKey = storeImage(brand.getId());
    }

    // ── 상세 ────────────────────────────────────────────────

    @Test
    void requiresAuthenticationToViewDetail() throws Exception {
        mockMvc.perform(get(PATH + "/{id}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheSameShapeAsRegistration() throws Exception {
        // 등록 직후와 새로고침 후에 다른 모양을 받으면 그 차이를 프론트가 흡수해야 한다.
        Long modelId = registerModel();

        mockMvc.perform(get(PATH + "/{id}", modelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productModelId").value(modelId))
                .andExpect(jsonPath("$.data.name").value("RE:BAG"))
                .andExpect(jsonPath("$.data.category").value("BAG"))
                .andExpect(jsonPath("$.data.brandId").value(brand.getId()))
                .andExpect(jsonPath("$.data.price").value(129_000))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void countsStockFromItemsNotFromAColumn() throws Exception {
        // product_model 에 재고 컬럼이 없다. 개체를 세는 것이 유일한 답이다.
        Long modelId = registerModel();
        produceItem(modelId, 1);
        produceItem(modelId, 2);

        mockMvc.perform(get(PATH + "/{id}", modelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(2));
    }

    @Test
    void letsAConsumerViewDetail() throws Exception {
        // 제품 정보는 마켓에 걸리는 공개 정보다. 역할로 가리면 소비자가 상품을 못 본다.
        Long modelId = registerModel();
        User consumer = userRepository.save(User.register(
                "product-query-consumer@example.com", "{noop}x", "소비자", "010-2222-2222",
                UserRole.CONSUMER));

        mockMvc.perform(get(PATH + "/{id}", modelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(consumer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productModelId").value(modelId));
    }

    @Test
    void rejectsAnUnknownModel() throws Exception {
        mockMvc.perform(get(PATH + "/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    // ── 본인 목록 ────────────────────────────────────────────

    @Test
    void requiresAuthenticationToListMyModels() throws Exception {
        mockMvc.perform(get(PATH + "/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotBindMeAsAProductModelId() throws Exception {
        // /me 가 경로 변수로 잘못 잡히면 400 이 된다. Spring 은 리터럴을 우선하지만
        // 그 동작에 기대는 대신 여기서 고정한다.
        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void includesDraftModels() throws Exception {
        // DRAFT 가 빠지면 브랜드가 방금 등록한 제품을 찾지 못하고,
        // 판매 시작 버튼을 놓을 화면이 없어진다.
        Long modelId = registerModel();

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].productModelId").value(modelId))
                .andExpect(jsonPath("$.data[0].status").value("DRAFT"));
    }

    @Test
    void neverShowsModelsOwnedByAnotherBrand() throws Exception {
        // 남의 목록에는 아직 공개하지 않은 DRAFT 가 들어 있다.
        registerModel();
        User otherBrand = userRepository.save(User.register(
                "product-query-other@example.com", "{noop}x", "다른브랜드", "010-3333-3333",
                UserRole.BRAND));

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void countsStockPerModelInTheList() throws Exception {
        // 목록의 재고를 모델마다 세면 N+1 이다. 한 번에 세면서도 값이 맞아야 한다.
        Long withStock = registerModel();
        Long withoutStock = registerModel();
        produceItem(withStock, 1);
        produceItem(withStock, 2);
        produceItem(withStock, 3);

        String response = mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<Long, Long> stocks = new HashMap<>();
        for (JsonNode item : objectMapper.readTree(response).path("data")) {
            stocks.put(item.path("productModelId").asLong(), item.path("stockQuantity").asLong());
        }
        assertThat(stocks).containsEntry(withStock, 3L);
        // 개체가 없는 모델은 집계 결과에 아예 없다. 0 으로 접히는지 본다.
        assertThat(stocks).containsEntry(withoutStock, 0L);
    }

    // ── helpers ─────────────────────────────────────────────

    private Long registerModel() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG");
        request.put("category", "BAG");
        request.put("price", 129_000);
        request.put("description", "폐데님으로 만든 가방");
        request.put("thumbnailUrl", thumbnailKey);

        String response = mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productModelId").asLong();
    }

    private void produceItem(Long modelId, int serialNumber) {
        productItemRepository.save(ProductItem.produce(
                1L,
                modelId,
                serialNumber,
                "ITEM-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString().replace("-", "")));
    }

    private String storeImage(Long brandId) {
        byte[] bytes = "photo-bytes".getBytes(StandardCharsets.UTF_8);
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brandId),
                        "thumb.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(bytes),
                        bytes.length)
                .storageKey();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
