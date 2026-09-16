package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 상세 갤러리.
 *
 * <p>고정하는 계약이 넷이다.
 *
 * <ul>
 *   <li><b>보낸 목록이 곧 갤러리 전체다</b> — 순서는 배열의 순서이고, 다시 보내면 옛 행이
 *       남지 않는다. 두 사진의 자리를 맞바꾸는 요청이 유니크 키에 걸리지 않아야 한다
 *   <li><b>갤러리는 대표 이미지를 대체하지 않는다</b> — 비어 있어도 목록 카드는 그대로
 *       그려지고, 공개 상세는 {@code images: []} 로 나간다
 *   <li><b>사진은 저장 키가 아니라 이미지 id 로 나간다</b> — 시연 상품과 브랜드 상품이
 *       같은 주소 형식을 쓰고, 저장 방식이 화면에 새지 않는다
 *   <li><b>공개 이미지도 상세와 같은 관문을 지난다</b> — 판매 중이 아니면 404 다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductModelImageGalleryApiTests {

    private static final String MANAGEMENT_PATH = "/api/v1/product-models";
    private static final String PUBLIC_PATH = "/api/v1/public/product-models";
    private static final byte[] IMAGE = "gallery-image".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("product-image-gallery-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없다", e);
        }
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
    private FileStorageService fileStorageService;

    private User brand;
    private String brandToken;
    private String otherBrandToken;
    private ProductModel model;
    private String mainKey;
    private String angleKey;
    private String detailKey;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "gallery-brand@example.com", "{noop}x", "브랜드", "02-0000-0000", UserRole.BRAND));
        User otherBrand = userRepository.save(User.register(
                "gallery-other@example.com", "{noop}x", "다른브랜드", "02-1111-1111",
                UserRole.BRAND));
        brandToken = tokenFor(brand);
        otherBrandToken = tokenFor(otherBrand);

        mainKey = storeImage();
        angleKey = storeImage();
        detailKey = storeImage();
        model = saveOnSaleModel();
    }

    // ── 갤러리 교체 ─────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(MANAGEMENT_PATH + "/{id}/images", model.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(MANAGEMENT_PATH + "/{id}/images", model.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gallery(image(mainKey, "MAIN"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replacesTheGalleryInTheOrderSent() throws Exception {
        mockMvc.perform(replaceGallery(brandToken,
                        image(mainKey, "MAIN"),
                        image(angleKey, "ANGLE"),
                        image(detailKey, "DETAIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].type").value("MAIN"))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].type").value("ANGLE"))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data[2].type").value("DETAIL"))
                .andExpect(jsonPath("$.data[2].sortOrder").value(2));

        // 저장 키는 내보내지 않는다. 주소는 이미지 id 로 조립된다.
        mockMvc.perform(get(MANAGEMENT_PATH + "/{id}/images", model.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].storageKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].url")
                        .value(startsWith(MANAGEMENT_PATH + "/" + model.getId() + "/images/")));
    }

    /**
     * 두 사진의 자리를 맞바꾸는 요청이 흔하다. 살려 둘 행을 골라 순서만 고치는 방식이면
     * 중간 상태에서 같은 자리를 잠깐 공유하게 되고 유니크 키에 걸린다.
     */
    @Test
    void swapsTheOrderWithoutLeavingOldRows() throws Exception {
        mockMvc.perform(replaceGallery(brandToken,
                        image(mainKey, "MAIN"), image(angleKey, "ANGLE")))
                .andExpect(status().isOk());

        mockMvc.perform(replaceGallery(brandToken,
                        image(angleKey, "ANGLE"), image(mainKey, "MAIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("ANGLE"))
                .andExpect(jsonPath("$.data[1].type").value("MAIN"));
    }

    @Test
    void clearsTheGalleryWithAnEmptyList() throws Exception {
        mockMvc.perform(replaceGallery(brandToken, image(mainKey, "MAIN")))
                .andExpect(status().isOk());

        mockMvc.perform(replaceGallery(brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 갤러리를 비워도 대표 이미지는 그대로다 — 목록 카드가 그것을 쓴다.
        mockMvc.perform(get(PUBLIC_PATH + "/{id}", model.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(0))
                .andExpect(jsonPath("$.data.thumbnailUrl")
                        .value(PUBLIC_PATH + "/" + model.getId() + "/thumbnail"));
    }

    // ── 거절하는 요청 ───────────────────────────────────────

    @Test
    void rejectsAStorageKeyThatWasNeverUploaded() throws Exception {
        mockMvc.perform(replaceGallery(brandToken, image("PRODUCT_IMAGE/1/없는키.jpg", "MAIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_STORED"));
    }

    @Test
    void rejectsTheSameImageTwice() throws Exception {
        mockMvc.perform(replaceGallery(brandToken,
                        image(mainKey, "MAIN"), image(mainKey, "ANGLE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_DUPLICATED"));
    }

    @Test
    void rejectsTwoMainImages() throws Exception {
        mockMvc.perform(replaceGallery(brandToken,
                        image(mainKey, "MAIN"), image(angleKey, "MAIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_MAIN_NOT_UNIQUE"));
    }

    /** 대표가 없는 갤러리는 허용한다 — 첫 자리가 대표 자리다. */
    @Test
    void acceptsAGalleryWithoutAMainImage() throws Exception {
        mockMvc.perform(replaceGallery(brandToken,
                        image(angleKey, "ANGLE"), image(detailKey, "DETAIL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void rejectsMoreImagesThanTheLimit() throws Exception {
        List<Map<String, Object>> images = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            images.add(image("PRODUCT_IMAGE/1/" + index + ".jpg", "DETAIL"));
        }

        mockMvc.perform(put(MANAGEMENT_PATH + "/{id}/images", model.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("images", images))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_TOO_MANY"));
    }

    @Test
    void refusesToTouchAnotherBrandsGallery() throws Exception {
        mockMvc.perform(replaceGallery(otherBrandToken, image(mainKey, "MAIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PRODUCT_MODEL_OWNER"));

        mockMvc.perform(get(MANAGEMENT_PATH + "/{id}/images", model.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherBrandToken))
                .andExpect(status().isForbidden());
    }

    /** 더 팔지 않기로 한 제품의 사진을 바꾸면 그것을 산 사람의 이력이 달라진다. */
    @Test
    void refusesToTouchADiscontinuedProduct() throws Exception {
        model.discontinue();
        productModelRepository.saveAndFlush(model);

        mockMvc.perform(replaceGallery(brandToken, image(mainKey, "MAIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_DISCONTINUED"));
    }

    // ── 이미지 바이트 ───────────────────────────────────────

    @Test
    void servesGalleryImageBytesToConsumersAndTheBrand() throws Exception {
        long imageId = firstImageId(replaceAndRead(image(mainKey, "MAIN")));

        byte[] publicBytes = mockMvc.perform(
                        get(PUBLIC_PATH + "/{id}/images/{imageId}", model.getId(), imageId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=300"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(publicBytes).isEqualTo(IMAGE);

        mockMvc.perform(get(MANAGEMENT_PATH + "/{id}/images/{imageId}", model.getId(), imageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL, "private, max-age=0, no-store"));
    }

    /** 다른 제품의 주소로는 열리지 않는다. 열리면 그 주소가 그대로 공유된다. */
    @Test
    void refusesAnImageThatBelongsToAnotherProduct() throws Exception {
        long imageId = firstImageId(replaceAndRead(image(mainKey, "MAIN")));
        ProductModel another = saveOnSaleModel();

        mockMvc.perform(get(PUBLIC_PATH + "/{id}/images/{imageId}", another.getId(), imageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_NOT_FOUND"));
    }

    /**
     * 공개 상세가 404 인 제품의 사진도 열리면 안 된다. 이미지만 열어 두면 목록에서 사라진
     * 제품의 사진이 주소만으로 계속 나간다.
     */
    @Test
    void hidesGalleryImagesOfProductsThatAreNotOnSale() throws Exception {
        ProductModel draft = productModelRepository.saveAndFlush(ProductModel.register(
                "준비 중 상품", ProductCategory.BAG, brand.getId(), 1_000, "설명", mainKey));
        long imageId = firstImageId(replaceAndRead(image(mainKey, "MAIN")));

        mockMvc.perform(get(PUBLIC_PATH + "/{id}/images/{imageId}", draft.getId(), imageId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    // ── 소비자 응답 ─────────────────────────────────────────

    @Test
    void carriesTheGalleryInConsumerDetailAndList() throws Exception {
        long imageId = firstImageId(
                replaceAndRead(image(mainKey, "MAIN"), image(angleKey, "ANGLE")));

        mockMvc.perform(get(PUBLIC_PATH + "/{id}", model.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.images[0].productImageId").value(imageId))
                .andExpect(jsonPath("$.data.images[0].type").value("MAIN"))
                .andExpect(jsonPath("$.data.images[0].url")
                        .value(PUBLIC_PATH + "/" + model.getId() + "/images/" + imageId))
                .andExpect(jsonPath("$.data.images[1].type").value("ANGLE"));

        // 목록도 같은 모양을 싣는다. 상세에만 채우면 목록의 images 가 "없다" 인지
        // "안 실었다" 인지 구분되지 않는다.
        mockMvc.perform(get(PUBLIC_PATH).queryParam("brandId", String.valueOf(brand.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].images.length()").value(2));
    }

    // ── helpers ─────────────────────────────────────────────

    @SafeVarargs
    private String replaceAndRead(Map<String, Object>... images) throws Exception {
        return mockMvc.perform(replaceGallery(brandToken, images))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long firstImageId(String response) throws Exception {
        return objectMapper.readTree(response).path("data").get(0).path("productImageId").asLong();
    }

    @SafeVarargs
    private MockHttpServletRequestBuilder replaceGallery(
            String token, Map<String, Object>... images) throws Exception {
        return put(MANAGEMENT_PATH + "/{productModelId}/images", model.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(gallery(images));
    }

    @SafeVarargs
    private String gallery(Map<String, Object>... images) throws Exception {
        return objectMapper.writeValueAsString(Map.of("images", List.of(images)));
    }

    private Map<String, Object> image(String storageKey, String type) {
        return Map.of("storageKey", storageKey, "type", type);
    }

    private ProductModel saveOnSaleModel() {
        ProductModel saved = productModelRepository.save(ProductModel.register(
                "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000, "폐데님 가방", mainKey));
        saved.putOnSale();
        return productModelRepository.saveAndFlush(saved);
    }

    private String storeImage() {
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brand.getId()),
                        "gallery.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(IMAGE),
                        IMAGE.length)
                .storageKey();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
