package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마켓 제품 목록.
 *
 * <p>고정하는 계약이 넷이다 — <b>인증이 필요하다</b>, <b>기본은 {@code ON_SALE} 만</b>,
 * <b>필터는 교집합</b>, <b>페이지 봉투가 다른 도메인과 같다</b>.
 *
 * <p>두 번째가 이 티켓의 핵심이다. 기본이 전체가 되면 브랜드가 준비 중인 {@code DRAFT}
 * 제품이 마켓에 뜨고, 손님은 아직 팔지 않는 물건을 본다.
 *
 * <p>목록을 등록 API 로 만들지 않고 리포지토리로 직접 넣는다. 등록은 언제나 {@code DRAFT}
 * 이고 판매 시작은 재고를 요구하므로, 상태별 표본을 만들려면 개체까지 찍어야 한다 —
 * 그 경로는 {@code ProductModelControllerTests} 가 이미 고정하고 있어 여기서 다시 거칠
 * 이유가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductModelSearchControllerTests {

    private static final String PATH = "/api/v1/product-models";

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("product-model-search-");
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
    private User otherBrand;
    private String token;
    private String thumbnailKey;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "product-search-brand@example.com", "{noop}x", "브랜드", "010-0000-0000",
                UserRole.BRAND));
        otherBrand = userRepository.save(User.register(
                "product-search-other@example.com", "{noop}x", "다른브랜드", "010-1111-1111",
                UserRole.BRAND));
        token = tokenFor(brand);
        thumbnailKey = storeImage(brand.getId());
    }

    // ── 인증 ────────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        // 제품은 로그인 뒤에만 보인다. permitAll 목록에 이 경로가 들어가면 여기서 깨진다.
        mockMvc.perform(get(PATH))
                .andExpect(status().isUnauthorized());
    }

    // ── 기본 동작 ────────────────────────────────────────────

    @Test
    void showsOnlyModelsOnSaleByDefault() throws Exception {
        // 기본이 전체가 되면 브랜드가 준비 중인 DRAFT 가 마켓에 뜬다.
        Long onSale = onSaleModel(brand, ProductCategory.BAG);
        draftModel(brand, ProductCategory.BAG);
        discontinuedModel(brand, ProductCategory.BAG);

        assertThat(idsOf(search(""))).containsExactly(onSale);
    }

    @Test
    void wrapsTheResultInTheSharedPageEnvelope() throws Exception {
        onSaleModel(brand, ProductCategory.BAG);

        mockMvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                // page 는 0 부터다. 한쪽에서 1 을 더하면 다른 쪽에서 빼야 한다.
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void keepsStockQuantityInTheListItems() throws Exception {
        // 목록의 재고를 모델마다 세면 N+1 이다. 한 번에 세면서도 값이 맞아야 한다.
        Long withStock = onSaleModel(brand, ProductCategory.BAG);
        Long withoutStock = onSaleModel(brand, ProductCategory.BAG);
        produceItem(withStock, 1);
        produceItem(withStock, 2);

        JsonNode content = search("");
        assertThat(stockOf(content, withStock)).isEqualTo(2);
        // 개체가 없는 모델은 집계 결과에 아예 없다. 0 으로 접히는지 본다.
        assertThat(stockOf(content, withoutStock)).isZero();
    }

    @Test
    void doesNotHideModelsThatRanOutOfStock() throws Exception {
        // 다 팔린 제품을 SOLD_OUT 으로 바꾸는 주문 처리가 아직 없다. 여기서 숨기면
        // 브랜드는 자기 제품이 마켓에서 사라진 이유를 알 수 없다.
        Long soldOut = onSaleModel(brand, ProductCategory.BAG);

        JsonNode content = search("");
        assertThat(idsOf(content)).containsExactly(soldOut);
        assertThat(stockOf(content, soldOut)).isZero();
    }

    // ── 필터 ────────────────────────────────────────────────

    @Test
    void filtersByCategory() throws Exception {
        Long bag = onSaleModel(brand, ProductCategory.BAG);
        onSaleModel(brand, ProductCategory.WALLET);

        assertThat(idsOf(search("?category=BAG"))).containsExactly(bag);
    }

    @Test
    void filtersByBrand() throws Exception {
        Long mine = onSaleModel(brand, ProductCategory.BAG);
        onSaleModel(otherBrand, ProductCategory.BAG);

        assertThat(idsOf(search("?brandId=" + brand.getId()))).containsExactly(mine);
    }

    @Test
    void filtersByStatus() throws Exception {
        onSaleModel(brand, ProductCategory.BAG);
        Long draft = draftModel(brand, ProductCategory.BAG);

        assertThat(idsOf(search("?status=DRAFT"))).containsExactly(draft);
    }

    @Test
    void combinesFiltersAsAnIntersection() throws Exception {
        Long match = onSaleModel(brand, ProductCategory.BAG);
        onSaleModel(brand, ProductCategory.WALLET);
        onSaleModel(otherBrand, ProductCategory.BAG);

        assertThat(idsOf(search("?category=BAG&brandId=" + brand.getId())))
                .containsExactly(match);
    }

    // ── 페이지·정렬 ──────────────────────────────────────────

    @Test
    void ordersByNewestFirst() throws Exception {
        Long first = onSaleModel(brand, ProductCategory.BAG);
        Long second = onSaleModel(brand, ProductCategory.BAG);
        Long third = onSaleModel(brand, ProductCategory.BAG);

        // created_at 이 같은 초에 몰리면 id 가 순서를 가른다. 그 2차 정렬이 없으면
        // 같은 제품이 1페이지와 2페이지에 동시에 보일 수 있다.
        assertThat(idsOf(search(""))).containsExactly(third, second, first);
    }

    @Test
    void splitsResultsIntoPages() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ids.add(onSaleModel(brand, ProductCategory.BAG));
        }

        mockMvc.perform(get(PATH + "?size=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        // 마지막 페이지에 남은 하나. 최신순이므로 가장 먼저 만든 것이다.
        assertThat(idsOf(search("?size=2&page=1"))).containsExactly(ids.get(0));
    }

    @Test
    void capsThePageSize() throws Exception {
        // size=100000 한 번으로 전체 테이블을 끌어올 수 있으면 안 된다.
        mockMvc.perform(get(PATH + "?size=100000")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));
    }

    @Test
    void foldsNonsensePagingIntoDefaults() throws Exception {
        // 목록 조회는 잘못된 값에 400 을 던지기보다 기본값으로 접는다.
        mockMvc.perform(get(PATH + "?page=-5&size=0")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(12));
    }

    // ── helpers ─────────────────────────────────────────────

    private JsonNode search(String query) throws Exception {
        String response = mockMvc.perform(get(PATH + query)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("content");
    }

    private List<Long> idsOf(JsonNode content) {
        List<Long> ids = new ArrayList<>();
        content.forEach(item -> ids.add(item.path("productModelId").asLong()));
        return ids;
    }

    private long stockOf(JsonNode content, Long productModelId) {
        for (JsonNode item : content) {
            if (item.path("productModelId").asLong() == productModelId) {
                return item.path("stockQuantity").asLong();
            }
        }
        throw new AssertionError("목록에 " + productModelId + " 가 없다");
    }

    private Long onSaleModel(User owner, ProductCategory category) {
        ProductModel model = saveModel(owner, category);
        model.putOnSale();
        return productModelRepository.save(model).getId();
    }

    private Long draftModel(User owner, ProductCategory category) {
        return saveModel(owner, category).getId();
    }

    private Long discontinuedModel(User owner, ProductCategory category) {
        ProductModel model = saveModel(owner, category);
        model.discontinue();
        return productModelRepository.save(model).getId();
    }

    private ProductModel saveModel(User owner, ProductCategory category) {
        return productModelRepository.save(ProductModel.register(
                "RE:BAG", category, owner.getId(), 129_000L, "폐데님으로 만든 가방", thumbnailKey));
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
