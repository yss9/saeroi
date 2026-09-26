package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소비자 BRANDS 카탈로그.
 *
 * <p>고정하는 계약이 넷이다.
 *
 * <ul>
 *   <li><b>팔 것이 있는 브랜드만 나온다</b> — 상품이 없거나 {@code ETC} 만 가진 브랜드가
 *       목록에 뜨면 눌렀을 때 빈 화면이 된다
 *   <li><b>상품 수가 목록과 같은 조건으로 세어진다</b> — 카드에 "12개" 라고 적어 놓고 11개만
 *       그리는 상태가 생기면 안 된다
 *   <li><b>사업자 정보가 나가지 않는다</b> — 사업자등록번호·주소·좌표는 공개 응답에 필드가
 *       아예 없어야 한다
 *   <li><b>로고는 우리가 받은 파일일 때만 주소가 나간다</b> — 업체 정보 폼에 손으로 입력된
 *       옛 자유 문자열이 소비자 화면의 img src 에 들어가면 안 된다
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicBrandCatalogApiTests {

    private static final String PUBLIC_PATH = "/api/v1/public/brands";
    private static final byte[] LOGO = "brand-logo-bytes".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("public-brand-catalog-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없다", e);
        }
    }

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
    private FileStorageService fileStorageService;

    private User selling;
    private User empty;
    private String thumbnailKey;

    @BeforeEach
    void setUp() {
        thumbnailKey = storeFile();
        selling = saveBrand("brand-selling@example.com", "대표자 김", "AURORA WORKS", "111-11-11111");
        empty = saveBrand("brand-empty@example.com", "대표자 이", "BLANK STUDIO", "222-22-22222");
        saveOnSaleProduct(selling, "판매 중 가방", ProductCategory.BAG, ProductSubcategory.TOTE);
    }

    // ── 목록 노출 기준 ──────────────────────────────────────

    @Test
    void listsOnlyBrandsThatHaveSomethingToSell() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).queryParam("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.brandId == " + selling.getId() + ")]")
                        .exists())
                .andExpect(jsonPath("$.data.content[?(@.brandId == " + empty.getId() + ")]")
                        .isEmpty());
    }

    /**
     * {@code ETC} 상품만 가진 브랜드는 소비자에게 팔 것이 없는 브랜드다. 상품 목록에서
     * 그 분류를 감추면서 브랜드는 남겨 두면 빈 상세로 들어가게 된다.
     */
    @Test
    void hidesBrandsThatOnlySellEtcProducts() throws Exception {
        User etcOnly = saveBrand("brand-etc@example.com", "대표자 박", "ETC ONLY", "333-33-33333");
        saveOnSaleProduct(etcOnly, "기타 상품", ProductCategory.ETC, null);

        mockMvc.perform(get(PUBLIC_PATH).queryParam("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.brandId == " + etcOnly.getId() + ")]")
                        .isEmpty());

        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", etcOnly.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBLIC_BRAND_NOT_FOUND"));
    }

    /** 카드의 상품 수가 목록과 다른 조건으로 세어지면 "12개" 라 쓰고 11개를 그리게 된다. */
    @Test
    void countsOnlyTheProductsConsumersCanSee() throws Exception {
        saveOnSaleProduct(selling, "판매 중 코트", ProductCategory.CLOTHING, ProductSubcategory.OUTER);
        saveOnSaleProduct(selling, "기타 상품", ProductCategory.ETC, null);
        saveDraftProduct(selling, "준비 중 상품");

        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCount").value(2));
    }

    // ── 검색·상세 ──────────────────────────────────────────

    @Test
    void findsBrandsByName() throws Exception {
        saveOnSaleProduct(empty, "판매 중 지갑", ProductCategory.WALLET, null);

        mockMvc.perform(get(PUBLIC_PATH).queryParam("keyword", "AURORA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].brandId").value(selling.getId()))
                .andExpect(jsonPath("$.data.content[0].brandName").value("AURORA WORKS"));

        // 회사명을 가진 브랜드가 대표자 이름으로 걸리면 사용자가 본 것과 결과가 어긋난다.
        mockMvc.perform(get(PUBLIC_PATH).queryParam("keyword", "대표자 김"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void servesBrandDetailWithoutAuthentication() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brandId").value(selling.getId()))
                .andExpect(jsonPath("$.data.brandName").value("AURORA WORKS"))
                .andExpect(jsonPath("$.data.productCount").value(1));
    }

    /** 소개글과 로고는 선택 항목이다. 서버가 기본 문구를 대신 채우지 않는다. */
    @Test
    void leavesTheIntroductionNullWhenTheBrandHasNotWrittenOne() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.introduction").doesNotExist())
                .andExpect(jsonPath("$.data.logoUrl").doesNotExist());
    }

    // ── 사업자 정보 미노출 ──────────────────────────────────

    @Test
    void neverExposesBusinessRegistrationDetails() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH).queryParam("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].businessNumber").doesNotExist())
                .andExpect(jsonPath("$.data.content[*].address").doesNotExist())
                .andExpect(jsonPath("$.data.content[*].addressDetail").doesNotExist())
                .andExpect(jsonPath("$.data.content[*].zipCode").doesNotExist())
                .andExpect(jsonPath("$.data.content[*].latitude").doesNotExist())
                .andExpect(jsonPath("$.data.content[*].longitude").doesNotExist())
                .andExpect(jsonPath("$.data.content[*].email").doesNotExist());

        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessNumber").doesNotExist())
                .andExpect(jsonPath("$.data.address").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist());
    }

    // ── 로고 ───────────────────────────────────────────────

    @Test
    void servesTheLogoOnlyAfterItWasUploadedAsAFile() throws Exception {
        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}/logo", selling.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRAND_LOGO_NOT_FOUND"));

        mockMvc.perform(uploadLogo(selling))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl")
                        .value(PUBLIC_PATH + "/" + selling.getId() + "/logo"));

        byte[] served = mockMvc.perform(get(PUBLIC_PATH + "/{brandId}/logo", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=300"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(served).isEqualTo(LOGO);

        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl")
                        .value(PUBLIC_PATH + "/" + selling.getId() + "/logo"));
    }

    /**
     * 업체 정보 폼에 손으로 넣은 옛 값은 저장 키가 아니다. 그대로 내려주면 소비자 화면이
     * 그 문자열을 img src 에 넣는다.
     */
    @Test
    void foldsALegacyFreeTextLogoValueToNull() throws Exception {
        CompanyProfile profile = companyProfileRepository.findById(selling.getId()).orElseThrow();
        profile.updateLogoUrl("https://example.com/somewhere/logo.png");
        companyProfileRepository.saveAndFlush(profile);

        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}", selling.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").doesNotExist());

        mockMvc.perform(get(PUBLIC_PATH + "/{brandId}/logo", selling.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesLogoUploadFromAccountsThatAreNotBrands() throws Exception {
        User consumer = userRepository.save(User.register(
                "brand-logo-consumer@example.com",
                "{noop}x",
                "소비자",
                "010-0000-0000",
                UserRole.CONSUMER));

        mockMvc.perform(uploadLogo(consumer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_FORBIDDEN"));

        mockMvc.perform(multipart("/api/v1/companies/me/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", LOGO)))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder uploadLogo(User owner) {
        return multipart("/api/v1/companies/me/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", LOGO))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(owner));
    }

    private User saveBrand(String email, String ownerName, String companyName, String bizNumber) {
        User brand = userRepository.save(User.register(
                email, "{noop}x", ownerName, "02-0000-0000", UserRole.BRAND));
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                companyName,
                bizNumber,
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층",
                new GeoCoordinates(
                        new BigDecimal("37.5010000"), new BigDecimal("127.0396000"))));
        return brand;
    }

    private void saveOnSaleProduct(
            User brand, String name, ProductCategory category, ProductSubcategory subcategory) {
        ProductModel model = productModelRepository.save(ProductModel.register(
                name, category, subcategory, brand.getId(), 50_000, "폐소재로 만든 상품",
                thumbnailKey));
        model.putOnSale();
        productModelRepository.saveAndFlush(model);
    }

    /** 준비 중인 상품은 공개 목록에 없으므로 브랜드의 상품 수에도 들어가면 안 된다. */
    private void saveDraftProduct(User brand, String name) {
        productModelRepository.saveAndFlush(ProductModel.register(
                name, ProductCategory.BAG, ProductSubcategory.TOTE, brand.getId(), 50_000,
                "폐소재로 만든 상품", thumbnailKey));
    }

    private String storeFile() {
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", 1L),
                        "thumbnail.png",
                        "image/png",
                        new ByteArrayInputStream(LOGO),
                        LOGO.length)
                .storageKey();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
