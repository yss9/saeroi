package com.ssafy.upcycle.product.controller;

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
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
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
import java.util.HashMap;
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
 * 소비자 SHOP 이 쓰는 분류 · 노출 · 정렬.
 *
 * <p>고정하는 계약이 셋이다.
 *
 * <ul>
 *   <li><b>하위 분류는 선택이고, 상위 분류에 속해야 한다</b> — 필수로 만들면 하위 분류를
 *       보내지 않던 기존 브랜드 등록 화면이 그날로 400 을 받는다
 *   <li><b>{@code ETC} 는 소비자에게 보이지 않는다</b> — 목록 · 상세 · 썸네일이 같은 판단을
 *       쓴다. 목록에서만 빼면 id 를 아는 사람에게 구매 버튼이 달린 상세가 그대로 열린다
 *   <li><b>브랜드 관리 화면은 그대로다</b> — 소비자에게 감추는 것과 브랜드에게서 빼앗는
 *       것은 다르다
 *   <li><b>검색은 화면에 보이는 브랜드명으로 걸린다</b> — 회사명을 가진 브랜드가 대표자
 *       이름으로 검색되면 사용자가 보는 것과 결과가 어긋난다
 * </ul>
 *
 * <p>목록 조회는 브랜드를 지정해 다른 테스트가 넣어 둔 제품과 섞이지 않게 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsumerShopCatalogApiTests {

    private static final String PUBLIC_PATH = "/api/v1/public/product-models";
    private static final String MANAGEMENT_PATH = "/api/v1/product-models";
    private static final byte[] THUMBNAIL = "shop-thumbnail".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("consumer-shop-catalog-");
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
    private FileStorageService fileStorageService;

    private User brand;
    private String brandToken;
    private String thumbnailKey;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "consumer-shop-brand@example.com",
                "{noop}x",
                "대표자",
                "02-0000-0000",
                UserRole.BRAND));
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                "새로이 SHOP 브랜드",
                "123-45-67891",
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층",
                new GeoCoordinates(
                        new BigDecimal("37.5010000"), new BigDecimal("127.0396000"))));
        brandToken = jwtTokenService
                .issueAccessToken(String.valueOf(brand.getId()), List.of(UserRole.BRAND.name()))
                .value();
        thumbnailKey = fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brand.getId()),
                        "thumbnail.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(THUMBNAIL),
                        THUMBNAIL.length)
                .storageKey();
    }

    // ── 하위 분류 등록 · 수정 ───────────────────────────────

    @Test
    void registersAProductWithASubcategory() throws Exception {
        Map<String, Object> request = registerRequest();
        request.put("subcategory", "TOTE");

        mockMvc.perform(register(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("BAG"))
                .andExpect(jsonPath("$.data.subcategory").value("TOTE"));
    }

    /**
     * 하위 분류를 보내지 않던 기존 등록 화면이 그대로 동작해야 한다. 이 계약이 깨지면
     * 브랜드는 하위 분류가 추가된 날부터 제품을 등록하지 못한다.
     */
    @Test
    void keepsTheSubcategoryOptional() throws Exception {
        mockMvc.perform(register(registerRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("BAG"))
                .andExpect(jsonPath("$.data.subcategory").doesNotExist());
    }

    @Test
    void rejectsASubcategoryFromAnotherCategory() throws Exception {
        Map<String, Object> request = registerRequest();
        request.put("subcategory", "DRESS");

        mockMvc.perform(register(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_SUBCATEGORY_NOT_ALLOWED"));
    }

    /** 2단 메뉴가 없는 분류에는 어떤 하위 분류도 붙지 않는다. */
    @Test
    void rejectsASubcategoryOnACategoryThatHasNone() throws Exception {
        Map<String, Object> request = registerRequest();
        request.put("category", "WALLET");
        request.put("subcategory", "TOTE");

        mockMvc.perform(register(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_SUBCATEGORY_NOT_ALLOWED"));
    }

    /**
     * 하위 분류가 생기기 전에 등록된 제품이 SHOP 2단 메뉴에 들어오는 유일한 경로다.
     * 없으면 그 제품들은 상위 분류 목록에만 남는다.
     */
    @Test
    void assignsASubcategoryToAProductRegisteredWithoutOne() throws Exception {
        ProductModel model =
                saveModel("하위 분류 없는 가방", ProductCategory.BAG, null, 50_000, true);

        mockMvc.perform(updateSubcategory(model.getId(), "TOTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subcategory").value("TOTE"));
    }

    @Test
    void refusesToAssignASubcategoryFromAnotherCategory() throws Exception {
        ProductModel model = saveModel("가방", ProductCategory.BAG, null, 50_000, true);

        mockMvc.perform(updateSubcategory(model.getId(), "DRESS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_SUBCATEGORY_NOT_ALLOWED"));
    }

    // ── 소비자 목록 필터 ────────────────────────────────────

    @Test
    void filtersTheConsumerListBySubcategory() throws Exception {
        ProductModel tote =
                saveModel("토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 70_000, true);
        ProductModel backpack =
                saveModel("백팩", ProductCategory.BAG, ProductSubcategory.BACKPACK, 80_000, true);

        mockMvc.perform(publicList().queryParam("category", "BAG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        mockMvc.perform(publicList()
                        .queryParam("category", "BAG")
                        .queryParam("subcategory", "TOTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(tote.getId()))
                .andExpect(jsonPath("$.data.content[0].subcategory").value("TOTE"))
                .andExpect(jsonPath("$.data.content[?(@.productModelId == "
                        + backpack.getId() + ")]").isEmpty());
    }

    // ── 정렬 ────────────────────────────────────────────────

    @Test
    void sortsByPriceInBothDirectionsAndDefaultsToLatest() throws Exception {
        ProductModel cheap =
                saveModel("싼 가방", ProductCategory.BAG, ProductSubcategory.TOTE, 10_000, true);
        ProductModel pricey =
                saveModel("비싼 가방", ProductCategory.BAG, ProductSubcategory.TOTE, 90_000, true);

        mockMvc.perform(publicList().queryParam("sort", "PRICE_ASC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productModelId").value(cheap.getId()))
                .andExpect(jsonPath("$.data.content[1].productModelId").value(pricey.getId()));

        mockMvc.perform(publicList().queryParam("sort", "PRICE_DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productModelId").value(pricey.getId()))
                .andExpect(jsonPath("$.data.content[1].productModelId").value(cheap.getId()));

        // 기본은 최신순. 두 제품의 created_at 은 같은 초라 id 내림차순이 곧 등록 역순이다.
        mockMvc.perform(publicList())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productModelId").value(pricey.getId()))
                .andExpect(jsonPath("$.data.content[1].productModelId").value(cheap.getId()));
    }

    // ── ETC 미노출 ──────────────────────────────────────────

    @Test
    void hidesEtcFromTheConsumerList() throws Exception {
        ProductModel etc = saveModel("기타 상품", ProductCategory.ETC, null, 30_000, true);
        ProductModel bag =
                saveModel("가방", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000, true);

        // SHOP 의 ALL 에 해당하는 요청. 총 개수까지 함께 본다 — 받아 온 뒤 걸러내는 방식은
        // 목록만 맞고 totalElements 와 페이지 수가 어긋난다.
        mockMvc.perform(publicList())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(bag.getId()))
                .andExpect(jsonPath("$.data.content[?(@.productModelId == "
                        + etc.getId() + ")]").isEmpty());

        // 분류를 직접 지정해도 마찬가지다.
        mockMvc.perform(publicList().queryParam("category", "ETC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void hidesEtcDetailAndThumbnailFromConsumers() throws Exception {
        ProductModel etc = saveModel("기타 상품", ProductCategory.ETC, null, 30_000, true);

        mockMvc.perform(get(PUBLIC_PATH + "/{id}", etc.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));

        mockMvc.perform(get(PUBLIC_PATH + "/{id}/thumbnail", etc.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    /**
     * 브랜드 관리 화면은 {@code ETC} 를 계속 본다. 이미 그 분류로 등록하고 판매한 제품이
     * 있고, 그것이 관리 목록에서 사라지면 브랜드는 손댈 방법을 잃는다.
     */
    @Test
    void keepsEtcVisibleToTheBrandManagementEndpoints() throws Exception {
        ProductModel etc = saveModel("기타 상품", ProductCategory.ETC, null, 30_000, true);

        mockMvc.perform(get(MANAGEMENT_PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.productModelId == " + etc.getId() + ")]")
                        .exists());

        mockMvc.perform(get(MANAGEMENT_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .queryParam("brandId", String.valueOf(brand.getId()))
                        .queryParam("category", "ETC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(etc.getId()));

        mockMvc.perform(get(MANAGEMENT_PATH + "/{id}", etc.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.category").value("ETC"));
    }

    // ── 검색 ────────────────────────────────────────────────

    @Test
    void findsProductsByName() throws Exception {
        ProductModel bag =
                saveModel("데님 서클 백", ProductCategory.BAG, ProductSubcategory.TOTE, 70_000, true);
        saveModel("울 코트", ProductCategory.CLOTHING, ProductSubcategory.OUTER, 200_000, true);

        mockMvc.perform(publicList().queryParam("keyword", "서클"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(bag.getId()));
    }

    /**
     * 브랜드명 검색은 <b>화면에 보이는 이름</b>으로 걸려야 한다. 이 브랜드는 회사명을
     * 가지고 있으므로 목록에도 회사명이 나가고, 대표자 이름으로는 걸리지 않아야 한다 —
     * 걸리면 사용자가 보지도 못한 값으로 검색이 되는 셈이다.
     */
    @Test
    void findsProductsByTheBrandNameShownOnScreen() throws Exception {
        ProductModel bag =
                saveModel("토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 70_000, true);

        mockMvc.perform(publicList().queryParam("keyword", "SHOP 브랜드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(bag.getId()))
                .andExpect(jsonPath("$.data.content[0].brandName").value("새로이 SHOP 브랜드"));

        mockMvc.perform(publicList().queryParam("keyword", "대표자"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /** 회사 정보를 등록하지 않은 브랜드는 사용자 이름이 화면에 나가므로 그것으로 걸린다. */
    @Test
    void findsProductsByTheUserNameWhenTheBrandHasNoCompanyProfile() throws Exception {
        User indieBrand = userRepository.save(User.register(
                "consumer-shop-indie@example.com",
                "{noop}x",
                "독립공방",
                "02-2222-2222",
                UserRole.BRAND));
        ProductModel handmade = saveModelFor(
                indieBrand, "핸드메이드 파우치", ProductCategory.ACCESSORY, null, 30_000);

        mockMvc.perform(get(PUBLIC_PATH).queryParam("keyword", "독립공방"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(handmade.getId()))
                .andExpect(jsonPath("$.data.content[0].brandName").value("독립공방"));
    }

    /** 목록에서 감춘 분류가 검색으로 새어 나오면 감춘 의미가 없다. */
    @Test
    void hidesEtcFromSearchResults() throws Exception {
        saveModel("업사이클 기타 소품", ProductCategory.ETC, null, 30_000, true);
        ProductModel bag = saveModel(
                "업사이클 토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000, true);

        mockMvc.perform(publicList().queryParam("keyword", "업사이클"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(bag.getId()));
    }

    @Test
    void combinesTheKeywordWithTheOtherFilters() throws Exception {
        ProductModel bag = saveModel(
                "리사이클 토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000, true);
        saveModel("리사이클 코트", ProductCategory.CLOTHING, ProductSubcategory.OUTER, 90_000, true);

        mockMvc.perform(publicList()
                        .queryParam("keyword", "리사이클")
                        .queryParam("category", "BAG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(bag.getId()));
    }

    /**
     * 총 개수는 검색 조건이 걸린 뒤의 수여야 한다. 조회해 온 페이지를 나중에 걸러내는
     * 방식이면 여기가 어긋나 "3건" 이라면서 페이지를 넘길 수 없는 목록이 된다.
     */
    @Test
    void keepsTotalElementsConsistentWithTheSearchCondition() throws Exception {
        saveModel("캔버스 토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000, true);
        saveModel("캔버스 숄더백", ProductCategory.BAG, ProductSubcategory.SHOULDER, 50_000, true);
        saveModel("캔버스 백팩", ProductCategory.BAG, ProductSubcategory.BACKPACK, 60_000, true);
        saveModel("가죽 지갑", ProductCategory.WALLET, null, 80_000, true);

        mockMvc.perform(publicList()
                        .queryParam("keyword", "캔버스")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    /** 검색창을 비우고 누른 사용자가 받는 것은 오류가 아니라 평소 목록이다. */
    @Test
    void ignoresABlankKeyword() throws Exception {
        saveModel("토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000, true);
        saveModel("코트", ProductCategory.CLOTHING, ProductSubcategory.OUTER, 90_000, true);

        mockMvc.perform(publicList().queryParam("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    /** {@code %} 한 글자로 전체 목록이 나오면 검색이 아니라 와일드카드를 넘겨준 것이다. */
    @Test
    void treatsWildcardCharactersAsPlainText() throws Exception {
        saveModel("토트백", ProductCategory.BAG, ProductSubcategory.TOTE, 40_000, true);
        saveModel("코트", ProductCategory.CLOTHING, ProductSubcategory.OUTER, 90_000, true);

        mockMvc.perform(publicList().queryParam("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ── helpers ─────────────────────────────────────────────

    /** 공개 목록은 언제나 이 브랜드로 좁힌다 — 다른 테스트가 남긴 제품과 섞이지 않는다. */
    private MockHttpServletRequestBuilder publicList() {
        return get(PUBLIC_PATH).queryParam("brandId", String.valueOf(brand.getId()));
    }

    private MockHttpServletRequestBuilder register(Map<String, Object> request) throws Exception {
        return post(MANAGEMENT_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
    }

    private MockHttpServletRequestBuilder updateSubcategory(Long modelId, String subcategory)
            throws Exception {
        return patch(MANAGEMENT_PATH + "/{productModelId}", modelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("subcategory", subcategory)));
    }

    private Map<String, Object> registerRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG");
        request.put("category", "BAG");
        request.put("price", 129_000);
        request.put("description", "폐데님으로 만든 가방");
        request.put("thumbnailUrl", thumbnailKey);
        return request;
    }

    /**
     * 목록 표본은 리포지토리로 직접 넣는다. 등록 API 는 언제나 {@code DRAFT} 로 만들고
     * 판매 시작은 재고를 요구하므로, 공개 목록 표본을 만들려면 개체까지 찍어야 한다 —
     * 그 경로는 {@code ProductModelControllerTests} 가 이미 고정하고 있다.
     */
    private ProductModel saveModel(
            String name,
            ProductCategory category,
            ProductSubcategory subcategory,
            long price,
            boolean onSale) {
        ProductModel model = productModelRepository.save(ProductModel.register(
                name,
                category,
                subcategory,
                brand.getId(),
                price,
                "폐소재로 만든 상품",
                thumbnailKey));
        if (onSale) {
            model.putOnSale();
        }
        return productModelRepository.saveAndFlush(model);
    }

    /** 다른 브랜드의 판매 중 상품. 브랜드명 검색 표본을 만들 때 쓴다. */
    private ProductModel saveModelFor(
            User owner,
            String name,
            ProductCategory category,
            ProductSubcategory subcategory,
            long price) {
        ProductModel model = productModelRepository.save(ProductModel.register(
                name,
                category,
                subcategory,
                owner.getId(),
                price,
                "폐소재로 만든 상품",
                thumbnailKey));
        model.putOnSale();
        return productModelRepository.saveAndFlush(model);
    }
}
