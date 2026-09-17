package com.ssafy.upcycle.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자의 찜.
 *
 * <p>고정하는 계약이 넷이다.
 *
 * <ul>
 *   <li><b>서버가 목록을 줄이지 않는다</b> — 단종·품절·감춘 분류로 바뀐 상품도 남는다.
 *       SHOP 은 공개 조건에 맞지 않으면 아예 빼지만 찜은 사용자가 담은 목록이다
 *   <li><b>담기와 빼기가 멱등이다</b> — 하트 버튼은 연타되고 재시도도 흔하다
 *   <li><b>남의 찜에 닿지 않는다</b> — 경로에 사용자 id 가 없고 조건에 늘 내 id 가 들어간다
 *   <li><b>소비자만 쓴다</b> — 사업자 계정은 구매 흐름을 쓰지 않는다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsumerWishlistApiTests {

    private static final String PATH = "/api/v1/consumers/me/wishlist";
    private static final byte[] THUMBNAIL = "wishlist-thumb".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("consumer-wishlist-");
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
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private String consumerToken;
    private String otherConsumerToken;
    private User brand;
    private String brandToken;
    private String thumbnailKey;
    private ProductModel onSale;

    @BeforeEach
    void setUp() {
        consumerToken =
                tokenFor(saveUser("wishlist-consumer@example.com", "소비자", UserRole.CONSUMER));
        otherConsumerToken =
                tokenFor(saveUser("wishlist-other@example.com", "다른소비자", UserRole.CONSUMER));
        brand = saveUser("wishlist-brand@example.com", "대표자", UserRole.BRAND);
        brandToken = tokenFor(brand);
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                "새로이 찜 브랜드",
                "123-45-67890",
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층",
                new GeoCoordinates(
                        new BigDecimal("37.5010000"), new BigDecimal("127.0396000"))));
        thumbnailKey = storeThumbnail();
        onSale = saveProduct("판매 중 가방", ProductCategory.BAG, ProductSubcategory.TOTE);
        produceItem(onSale);
        putOnSale(onSale);
    }

    // ── 접근 통제 ───────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/product-ids")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(onSale.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH + "/{id}", onSale.getId()))
                .andExpect(status().isUnauthorized());
    }

    /** 사업자 계정은 구매 흐름을 쓰지 않는다. 역할은 토큰이 아니라 DB 에서 다시 읽는다. */
    @Test
    void refusesAccountsThatAreNotConsumers() throws Exception {
        mockMvc.perform(add(brandToken, onSale.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WISHLIST_ROLE_NOT_ALLOWED"));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isForbidden());
    }

    /** 경로에 사용자 id 가 없으므로 남의 찜을 가리킬 방법이 없다. */
    @Test
    void doesNotShowAnotherConsumersWishlist() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(list(otherConsumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 남의 찜을 빼려는 요청은 아무 행도 건드리지 못한다.
        mockMvc.perform(delete(PATH + "/{id}", onSale.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherConsumerToken))
                .andExpect(status().isOk());
        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void rejectsAProductThatDoesNotExist() throws Exception {
        mockMvc.perform(add(consumerToken, 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WISHLIST_PRODUCT_NOT_FOUND"));
    }

    // ── 담기·빼기 ──────────────────────────────────────────

    @Test
    void addsAndListsAProduct() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].product.productModelId")
                        .value(onSale.getId()))
                .andExpect(jsonPath("$.data.content[0].product.brandName").value("새로이 찜 브랜드"))
                .andExpect(jsonPath("$.data.content[0].product.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].product.stockQuantity").value(1))
                .andExpect(jsonPath("$.data.content[0].purchasable").value(true))
                .andExpect(jsonPath("$.data.content[0].wishedAt").exists());
    }

    /** 결과 상태가 같으므로 같은 답을 준다. 유니크 제약이 행을 하나로 지킨다. */
    @Test
    void treatsARepeatedAddAsSuccess() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void removesIdempotently() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(remove(consumerToken, onSale.getId())).andExpect(status().isOk());
        mockMvc.perform(remove(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── 목록이 줄어들지 않는다 ──────────────────────────────

    /**
     * SHOP 은 공개 조건에 맞지 않는 상품을 아예 빼지만 찜은 남긴다. 공개 목록 조건을 그대로
     * 가져다 쓰면 찜해 둔 상품이 소리 없이 사라진다.
     */
    @Test
    void keepsProductsThatLeftThePublicCatalog() throws Exception {
        ProductModel soldOut = saveProduct("품절 가방", ProductCategory.BAG, ProductSubcategory.TOTE);
        putOnSale(soldOut);

        ProductModel discontinued =
                saveProduct("단종 가방", ProductCategory.BAG, ProductSubcategory.SHOULDER);
        produceItem(discontinued);
        putOnSale(discontinued);
        discontinued.discontinue();
        productModelRepository.saveAndFlush(discontinued);

        ProductModel hidden = saveProduct("감춘 분류 상품", ProductCategory.ETC, null);
        produceItem(hidden);
        putOnSale(hidden);

        for (ProductModel product : List.of(soldOut, discontinued, hidden)) {
            mockMvc.perform(add(consumerToken, product.getId())).andExpect(status().isOk());
        }

        // 최근에 담은 것부터라 담은 역순으로 놓인다.
        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].product.productModelId")
                        .value(hidden.getId()))
                .andExpect(jsonPath("$.data.content[1].product.productModelId")
                        .value(discontinued.getId()))
                .andExpect(jsonPath("$.data.content[2].product.productModelId")
                        .value(soldOut.getId()))
                .andExpect(jsonPath("$.data.content[0].purchasable").value(false))
                .andExpect(jsonPath("$.data.content[1].purchasable").value(false))
                .andExpect(jsonPath("$.data.content[2].purchasable").value(false))
                // 왜 못 사는지는 상품의 상태와 재고로 구분한다. 감춘 분류와 품절은 여전히
                // ON_SALE 이고, 단종만 상태가 다르다.
                .andExpect(jsonPath("$.data.content[0].product.category").value("ETC"))
                .andExpect(jsonPath("$.data.content[1].product.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.data.content[2].product.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[2].product.stockQuantity").value(0));
    }

    /**
     * 찜 목록의 이미지는 인증 경로다. 공개 썸네일은 판매 중인 상품만 열려 있어 단종된 찜
     * 상품의 사진이 깨진다.
     */
    @Test
    void usesImagePathsThatStayOpenAfterAProductLeavesTheCatalog() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].product.thumbnailUrl")
                        .value("/api/v1/product-models/" + onSale.getId() + "/thumbnail"));
    }

    // ── 정렬과 id 목록 ─────────────────────────────────────

    @Test
    void listsTheMostRecentlyWishedFirst() throws Exception {
        ProductModel second = saveProduct("두 번째 가방", ProductCategory.BAG, ProductSubcategory.TOTE);
        putOnSale(second);
        ProductModel third = saveProduct("세 번째 가방", ProductCategory.BAG, ProductSubcategory.TOTE);
        putOnSale(third);

        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());
        mockMvc.perform(add(consumerToken, second.getId())).andExpect(status().isOk());
        mockMvc.perform(add(consumerToken, third.getId())).andExpect(status().isOk());

        mockMvc.perform(list(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].product.productModelId")
                        .value(third.getId()))
                .andExpect(jsonPath("$.data.content[1].product.productModelId")
                        .value(second.getId()))
                .andExpect(jsonPath("$.data.content[2].product.productModelId")
                        .value(onSale.getId()));
    }

    @Test
    void returnsWishedProductIdsForHeartMarking() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId())).andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/product-ids")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0]").value(onSale.getId()));
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder list(String token) {
        return get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder add(String token, Long productModelId) throws Exception {
        return post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(productModelId));
    }

    private MockHttpServletRequestBuilder remove(String token, Long productModelId) {
        return delete(PATH + "/{productModelId}", productModelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String addBody(Long productModelId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("productModelId", productModelId));
    }

    private User saveUser(String email, String name, UserRole role) {
        return userRepository.save(
                User.register(email, "{noop}x", name, "02-0000-0000", role));
    }

    private ProductModel saveProduct(
            String name, ProductCategory category, ProductSubcategory subcategory) {
        return productModelRepository.saveAndFlush(ProductModel.register(
                name, category, subcategory, brand.getId(), 50_000, "폐소재로 만든 상품",
                thumbnailKey));
    }

    private void putOnSale(ProductModel model) {
        model.putOnSale();
        productModelRepository.saveAndFlush(model);
    }

    /** 재고를 한 점 만든다. 배치 행이 없어도 재고 계산에는 영향이 없다. */
    private void produceItem(ProductModel model) {
        productItemRepository.saveAndFlush(ProductItem.produce(
                1L,
                model.getId(),
                1,
                "WISH-" + UUID.randomUUID().toString().substring(0, 20),
                UUID.randomUUID().toString().replace("-", "")));
    }

    private String storeThumbnail() {
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brand.getId()),
                        "thumbnail.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(THUMBNAIL),
                        THUMBNAIL.length)
                .storageKey();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
