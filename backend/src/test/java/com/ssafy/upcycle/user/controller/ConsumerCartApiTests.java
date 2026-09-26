package com.ssafy.upcycle.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ssafy.upcycle.product.entity.ProductItemStatus;
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
 * 소비자의 장바구니.
 *
 * <p>고정하는 계약이 다섯이다.
 *
 * <ul>
 *   <li><b>같은 상품을 다시 담으면 수량이 쌓인다</b> — 같은 상품이 두 줄로 보이면 안 된다
 *   <li><b>담기는 예약이 아니지만 뻔한 초과는 막는다</b> — 남은 재고를 넘길 수 없다
 *   <li><b>서버가 목록을 줄이지 않는다</b> — 단종·품절·감춘 분류도 남고 purchasable 로 알린다
 *   <li><b>담은 뒤 값이 바뀌면 알린다</b> — 담을 때 가격과 현재가를 함께 준다
 *   <li><b>남의 장바구니에 닿지 않는다</b> — 경로에 사용자 id 가 없고 조건에 늘 내 id 가 든다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsumerCartApiTests {

    private static final String PATH = "/api/v1/consumers/me/cart";
    private static final byte[] THUMBNAIL = "cart-thumb".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("consumer-cart-");
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
        consumerToken = tokenFor(saveUser("cart-consumer@example.com", "소비자", UserRole.CONSUMER));
        otherConsumerToken =
                tokenFor(saveUser("cart-other@example.com", "다른소비자", UserRole.CONSUMER));
        brand = saveUser("cart-brand@example.com", "대표자", UserRole.BRAND);
        brandToken = tokenFor(brand);
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                "새로이 장바구니 브랜드",
                "123-45-67890",
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층",
                new GeoCoordinates(
                        new BigDecimal("37.5010000"), new BigDecimal("127.0396000"))));
        thumbnailKey = storeThumbnail();
        onSale = saveProduct("판매 중 가방", ProductCategory.BAG, ProductSubcategory.TOTE, 50_000);
        produceItems(onSale, 5);
        putOnSale(onSale);
    }

    // ── 접근 통제 ───────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody(onSale.getId(), 1)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH + "/{id}", onSale.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete(PATH)).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAccountsThatAreNotConsumers() throws Exception {
        mockMvc.perform(add(brandToken, onSale.getId(), 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CART_ROLE_NOT_ALLOWED"));

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isForbidden());
    }

    /** 경로에 사용자 id 가 없으므로 남의 장바구니를 가리킬 방법이 없다. */
    @Test
    void doesNotTouchAnotherConsumersCart() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 2)).andExpect(status().isOk());

        mockMvc.perform(cart(otherConsumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0));

        // 남의 항목을 빼려는 요청은 아무 행도 건드리지 못한다.
        mockMvc.perform(delete(PATH + "/{id}", onSale.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherConsumerToken))
                .andExpect(status().isOk());
        mockMvc.perform(cart(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test
    void rejectsAProductThatDoesNotExist() throws Exception {
        mockMvc.perform(add(consumerToken, 999_999L, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_PRODUCT_NOT_FOUND"));
    }

    // ── 담기·수량·제거 ─────────────────────────────────────

    @Test
    void addsAProductWithQuantity() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].product.productModelId")
                        .value(onSale.getId()))
                .andExpect(jsonPath("$.data.items[0].product.brandName")
                        .value("새로이 장바구니 브랜드"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].addedPrice").value(50_000))
                .andExpect(jsonPath("$.data.items[0].priceChanged").value(false))
                .andExpect(jsonPath("$.data.items[0].purchasable").value(true))
                .andExpect(jsonPath("$.data.items[0].product.stockQuantity").value(5))
                .andExpect(jsonPath("$.data.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.totalPrice").value(100_000));
    }

    /** 상세에서 "담기" 를 두 번 누르면 2개가 된다. 같은 상품이 두 줄로 보이면 안 된다. */
    @Test
    void accumulatesQuantityWhenTheSameProductIsAddedAgain() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 1)).andExpect(status().isOk());
        mockMvc.perform(add(consumerToken, onSale.getId(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3));
    }

    @Test
    void changesQuantityToTheGivenValue() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 3)).andExpect(status().isOk());

        mockMvc.perform(changeQuantity(consumerToken, onSale.getId(), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(1))
                .andExpect(jsonPath("$.data.totalPrice").value(50_000));
    }

    @Test
    void refusesQuantityOutsideTheAllowedRange() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_OUT_OF_RANGE"));
        mockMvc.perform(add(consumerToken, onSale.getId(), -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_OUT_OF_RANGE"));
        mockMvc.perform(add(consumerToken, onSale.getId(), 100))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_OUT_OF_RANGE"));

        mockMvc.perform(add(consumerToken, onSale.getId(), 1)).andExpect(status().isOk());
        mockMvc.perform(changeQuantity(consumerToken, onSale.getId(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_OUT_OF_RANGE"));
    }

    /** 담기는 예약이 아니지만 뻔한 초과는 미리 막는다 — 결제 화면에서야 실패하면 늦다. */
    @Test
    void refusesMoreThanTheRemainingStock() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_EXCEEDS_STOCK"));

        // 누적 결과도 재고를 넘길 수 없다.
        mockMvc.perform(add(consumerToken, onSale.getId(), 4)).andExpect(status().isOk());
        mockMvc.perform(add(consumerToken, onSale.getId(), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_EXCEEDS_STOCK"));
        mockMvc.perform(changeQuantity(consumerToken, onSale.getId(), 6))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CART_QUANTITY_EXCEEDS_STOCK"));
    }

    @Test
    void refusesChangingTheQuantityOfAProductThatIsNotInTheCart() throws Exception {
        mockMvc.perform(changeQuantity(consumerToken, onSale.getId(), 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_ITEM_NOT_FOUND"));
    }

    @Test
    void removesIdempotentlyAndClearsEverything() throws Exception {
        ProductModel second = saveProduct("두 번째", ProductCategory.WALLET, null, 30_000);
        produceItems(second, 2);
        putOnSale(second);
        mockMvc.perform(add(consumerToken, onSale.getId(), 1)).andExpect(status().isOk());
        mockMvc.perform(add(consumerToken, second.getId(), 1)).andExpect(status().isOk());

        mockMvc.perform(remove(consumerToken, onSale.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));
        // 담겨 있지 않아도 성공이다 — 결과 상태가 같다.
        mockMvc.perform(remove(consumerToken, onSale.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1));

        mockMvc.perform(delete(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalPrice").value(0));
    }

    // ── 목록이 줄어들지 않는다 ──────────────────────────────

    /**
     * SHOP 은 공개 조건에 맞지 않는 상품을 아예 빼지만 장바구니는 남긴다. 공개 목록 조건을
     * 그대로 가져다 쓰면 담아 둔 상품이 소리 없이 사라진다.
     */
    @Test
    void keepsProductsThatLeftThePublicCatalog() throws Exception {
        ProductModel soldOut =
                saveProduct("품절 가방", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000);
        produceItems(soldOut, 1);
        putOnSale(soldOut);
        ProductModel discontinued =
                saveProduct("단종 가방", ProductCategory.BAG, ProductSubcategory.SHOULDER, 60_000);
        produceItems(discontinued, 1);
        putOnSale(discontinued);
        ProductModel hidden = saveProduct("감춘 분류", ProductCategory.ETC, null, 20_000);
        produceItems(hidden, 1);
        putOnSale(hidden);

        for (ProductModel product : List.of(soldOut, discontinued, hidden)) {
            mockMvc.perform(add(consumerToken, product.getId(), 1)).andExpect(status().isOk());
        }

        // 담은 뒤에 상태가 바뀐다.
        soldOutAllItems(soldOut);
        discontinued.discontinue();
        productModelRepository.saveAndFlush(discontinued);

        // 담은 순서 그대로 남는다.
        mockMvc.perform(cart(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].product.productModelId")
                        .value(soldOut.getId()))
                .andExpect(jsonPath("$.data.items[1].product.productModelId")
                        .value(discontinued.getId()))
                .andExpect(jsonPath("$.data.items[2].product.productModelId")
                        .value(hidden.getId()))
                .andExpect(jsonPath("$.data.items[0].purchasable").value(false))
                .andExpect(jsonPath("$.data.items[1].purchasable").value(false))
                .andExpect(jsonPath("$.data.items[2].purchasable").value(false))
                .andExpect(jsonPath("$.data.items[0].product.stockQuantity").value(0))
                .andExpect(jsonPath("$.data.items[1].product.status").value("DISCONTINUED"))
                .andExpect(jsonPath("$.data.items[2].product.category").value("ETC"))
                // 합계는 살 수 있는 항목만 센다.
                .andExpect(jsonPath("$.data.totalQuantity").value(0))
                .andExpect(jsonPath("$.data.totalPrice").value(0));
    }

    /**
     * 장바구니 이미지는 인증 경로다. 공개 썸네일은 판매 중인 상품만 열려 있어 단종된 항목의
     * 사진이 깨진다.
     */
    @Test
    void usesImagePathsThatStayOpenAfterAProductLeavesTheCatalog() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 1)).andExpect(status().isOk());

        mockMvc.perform(cart(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].product.thumbnailUrl")
                        .value("/api/v1/product-models/" + onSale.getId() + "/thumbnail"));
    }

    // ── 가격 변동 ──────────────────────────────────────────

    /** 담은 뒤 값이 바뀌면 두 값을 함께 준다. 결제 기준은 현재가다. */
    @Test
    void reportsAPriceChangeAfterTheItemWasAdded() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 2)).andExpect(status().isOk());

        onSale.update(null, 70_000L, null, null);
        productModelRepository.saveAndFlush(onSale);

        mockMvc.perform(cart(consumerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].addedPrice").value(50_000))
                .andExpect(jsonPath("$.data.items[0].product.price").value(70_000))
                .andExpect(jsonPath("$.data.items[0].priceChanged").value(true))
                // 합계는 현재가 기준이다.
                .andExpect(jsonPath("$.data.totalPrice").value(140_000));
    }

    /** 수량만 만진 것은 상품 화면을 다시 본 것이 아니라 기준 가격이 그대로다. */
    @Test
    void keepsTheAddedPriceWhenOnlyTheQuantityChanges() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 1)).andExpect(status().isOk());
        onSale.update(null, 70_000L, null, null);
        productModelRepository.saveAndFlush(onSale);

        mockMvc.perform(changeQuantity(consumerToken, onSale.getId(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].addedPrice").value(50_000))
                .andExpect(jsonPath("$.data.items[0].priceChanged").value(true));
    }

    /** 다시 담는 것은 사용자가 그 가격을 보고 한 행동이라 기준이 그 시점으로 옮겨 간다. */
    @Test
    void movesTheAddedPriceForwardWhenTheProductIsAddedAgain() throws Exception {
        mockMvc.perform(add(consumerToken, onSale.getId(), 1)).andExpect(status().isOk());
        onSale.update(null, 70_000L, null, null);
        productModelRepository.saveAndFlush(onSale);

        mockMvc.perform(add(consumerToken, onSale.getId(), 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].addedPrice").value(70_000))
                .andExpect(jsonPath("$.data.items[0].priceChanged").value(false));
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder cart(String token) {
        return get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder add(String token, Long productModelId, int quantity)
            throws Exception {
        return post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(addBody(productModelId, quantity));
    }

    private MockHttpServletRequestBuilder changeQuantity(
            String token, Long productModelId, int quantity) throws Exception {
        return patch(PATH + "/{productModelId}", productModelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("quantity", quantity)));
    }

    private MockHttpServletRequestBuilder remove(String token, Long productModelId) {
        return delete(PATH + "/{productModelId}", productModelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String addBody(Long productModelId, int quantity) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("productModelId", productModelId, "quantity", quantity));
    }

    private User saveUser(String email, String name, UserRole role) {
        return userRepository.save(
                User.register(email, "{noop}x", name, "02-0000-0000", role));
    }

    private ProductModel saveProduct(
            String name, ProductCategory category, ProductSubcategory subcategory, long price) {
        return productModelRepository.saveAndFlush(ProductModel.register(
                name, category, subcategory, brand.getId(), price, "폐소재로 만든 상품",
                thumbnailKey));
    }

    private void putOnSale(ProductModel model) {
        model.putOnSale();
        productModelRepository.saveAndFlush(model);
    }

    /** 재고를 채운다. 배치 행이 없어도 재고 계산에는 영향이 없다. */
    private void produceItems(ProductModel model, int count) {
        for (int serial = 1; serial <= count; serial++) {
            productItemRepository.saveAndFlush(ProductItem.produce(
                    1L,
                    model.getId(),
                    serial,
                    "CART-" + UUID.randomUUID().toString().substring(0, 20),
                    UUID.randomUUID().toString().replace("-", "")));
        }
    }

    /** 담아 둔 사이에 다 팔린 상황. 상태는 ON_SALE 이지만 재고가 0 이다. */
    private void soldOutAllItems(ProductModel model) {
        productItemRepository.deleteAll(productItemRepository.findAllByProductModelIdAndStatus(
                model.getId(), ProductItemStatus.IN_STOCK));
        productItemRepository.flush();
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
