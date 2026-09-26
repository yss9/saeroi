package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 정보 수정. <b>보낸 필드만 바뀐다.</b>
 *
 * <p>수정 경로가 없으면 오타 하나 고치려고 제품을 새로 등록하게 되고, 그러면 단종된 제품이
 * 쌓이는 데다 이미 생산된 개체가 옛 제품에 붙어 이력이 갈라진다.
 *
 * <p>여기서 고정하는 계약이 넷이다 — <b>안 보낸 필드는 그대로</b>, <b>빈 값은 거절</b>,
 * <b>가격은 판매 중에도 바뀐다</b>, <b>단종된 제품은 못 고친다</b>.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductModelUpdateTests {

    private static final String PATH = "/api/v1/product-models";

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("product-model-update-");
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
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User brand;
    private String brandToken;
    private String thumbnailKey;
    private Long productModelId;

    @BeforeEach
    void setUp() throws Exception {
        brand = userRepository.save(User.register(
                "product-update-brand@example.com", "{noop}x", "브랜드", "010-0000-0000",
                UserRole.BRAND));
        brandToken = tokenFor(brand);
        thumbnailKey = storeImage();
        productModelId = registeredModel();
    }

    // ── 부분 수정 ────────────────────────────────────────────

    @Test
    void changesOnlyTheFieldsThatWereSent() throws Exception {
        // 오타 하나 고치는 데 전체를 다시 보내야 하면, 화면이 안 바꾼 값까지 실어 보내다가
        // 그 사이 다른 곳에서 바뀐 값을 덮어쓴다.
        update(Map.of("name", "RE:BAG 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("RE:BAG 2"))
                .andExpect(jsonPath("$.data.price").value(129_000))
                .andExpect(jsonPath("$.data.description").value("폐데님으로 만든 가방"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value(thumbnailKey));
    }

    @Test
    void changesSeveralFieldsAtOnce() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG Pro");
        request.put("price", 149_000);
        request.put("description", "새 설명");

        update(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("RE:BAG Pro"))
                .andExpect(jsonPath("$.data.price").value(149_000))
                .andExpect(jsonPath("$.data.description").value("새 설명"));
    }

    @Test
    void replacesTheThumbnailWithAnotherUploadedImage() throws Exception {
        String newKey = storeImage();

        update(Map.of("thumbnailUrl", newKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thumbnailUrl").value(newKey));
    }

    @Test
    void refusesAThumbnailThatWasNeverUploaded() throws Exception {
        // 등록과 같은 검사다. 없는 키를 넣으면 이미지가 깨진 제품이 되고,
        // 그건 마켓에 올라간 뒤에야 드러난다.
        update(Map.of("thumbnailUrl", "PRODUCT_IMAGE/1/2026/09/nope.jpg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_THUMBNAIL_NOT_STORED"));
    }

    // ── 가격 ────────────────────────────────────────────────

    @Test
    void priceCanChangeWhileTheProductIsOnSale() throws Exception {
        // 세일을 막으면 가격을 내리려고 제품을 새로 등록하게 되고,
        // 그러면 이미 만든 개체가 옛 제품에 붙는다.
        putOnSale();

        update(Map.of("price", 99_000))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.price").value(99_000));
    }

    @Test
    void refusesANegativePrice() throws Exception {
        update(Map.of("price", -1)).andExpect(status().isBadRequest());
    }

    @Test
    void refusesAFractionalPrice() throws Exception {
        // Jackson 은 기본적으로 129000.5 를 129000 으로 버린다.
        update(Map.of("price", 129_000.5)).andExpect(status().isBadRequest());
    }

    @Test
    void allowsAFreeProduct() throws Exception {
        update(Map.of("price", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(0));
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void refusesAnEmptyRequest() throws Exception {
        // 바꿀 것이 없으면 성공했다고 답할 것도 없다.
        update(Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOTHING_TO_UPDATE"));
    }

    @Test
    void refusesABlankValue() throws Exception {
        // "안 보냄"(null)과 "빈 값으로 바꿈"("")은 다르다.
        // 이름을 빈 문자열로 만드는 것은 실수이지 의도가 아니다.
        update(Map.of("name", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOTHING_TO_UPDATE"));
    }

    @Test
    void refusesToTouchADiscontinuedProduct() throws Exception {
        // 더 팔지 않기로 한 제품의 이름과 가격을 바꾸면, 그 제품을 산 사람이 나중에 보는
        // 이력이 실제로 산 것과 달라진다.
        discontinue();

        update(Map.of("name", "되살리기"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_DISCONTINUED"));
    }

    @Test
    void refusesToTouchAnotherBrandsProduct() throws Exception {
        User otherBrand = userRepository.save(User.register(
                "product-update-other@example.com", "{noop}x", "다른브랜드", "010-3333-3333",
                UserRole.BRAND));

        mockMvc.perform(patch(PATH + "/{id}", productModelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "가로채기"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PRODUCT_MODEL_OWNER"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(patch(PATH + "/{id}", productModelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "익명"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnUnknownProduct() throws Exception {
        mockMvc.perform(patch(PATH + "/{id}", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "없는 제품"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    // ── 바꿀 수 없는 것 ──────────────────────────────────────

    @Test
    void ignoresCategoryAndStatusEvenIfTheyAreSent() throws Exception {
        // 카테고리를 바꾸면 이미 그 분류로 팔린 제품의 이력이 흔들리고,
        // 상태는 재고를 봐야 정해지는 값이라 별도 경로가 맡는다.
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG 2");
        request.put("category", "WALLET");
        request.put("status", "ON_SALE");
        request.put("brandId", 999);

        update(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("BAG"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.brandId").value(brand.getId()));
    }

    @Test
    void keepsStockUntouched() throws Exception {
        // 수정은 개체를 건드리지 않는다. 재고는 개체를 세어 나오는 값이다.
        produceItem(1);
        produceItem(2);

        update(Map.of("name", "RE:BAG 2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stockQuantity").value(2));
    }

    @Test
    void updatedValuesSurviveAReload() throws Exception {
        update(Map.of("name", "RE:BAG 2", "price", 99_000)).andExpect(status().isOk());

        assertThat(productModelRepository.findById(productModelId))
                .get()
                .satisfies(model -> {
                    assertThat(model.getName()).isEqualTo("RE:BAG 2");
                    assertThat(model.getPrice()).isEqualTo(99_000L);
                });
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions update(Map<String, Object> request) throws Exception {
        return mockMvc.perform(patch(PATH + "/{id}", productModelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    private void putOnSale() throws Exception {
        produceItem(1);
        mockMvc.perform(patch(PATH + "/{id}/status", productModelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("action", "ON_SALE"))))
                .andExpect(status().isOk());
    }

    private void discontinue() throws Exception {
        mockMvc.perform(patch(PATH + "/{id}/status", productModelId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("action", "DISCONTINUE"))))
                .andExpect(status().isOk());
    }

    private void produceItem(int serialNumber) {
        productItemRepository.save(ProductItem.produce(
                1L,
                productModelId,
                serialNumber,
                "ITEM-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString().replace("-", "")));
    }

    private Long registeredModel() throws Exception {
        return productModelRepository.save(ProductModel.register(
                        "RE:BAG",
                        ProductCategory.BAG,
                        brand.getId(),
                        129_000L,
                        "폐데님으로 만든 가방",
                        thumbnailKey))
                .getId();
    }

    private String storeImage() {
        byte[] bytes = "photo-bytes".getBytes(StandardCharsets.UTF_8);
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brand.getId()),
                        "thumb.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(bytes),
                        bytes.length)
                .storageKey();
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
