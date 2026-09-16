package com.ssafy.upcycle.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicProductCatalogControllerTests {

    private static final String PUBLIC_PATH = "/api/v1/public/product-models";
    private static final String MANAGEMENT_PATH = "/api/v1/product-models";
    private static final byte[] THUMBNAIL = "public-product-thumbnail".getBytes(StandardCharsets.UTF_8);
    private static final Path STORAGE_ROOT = createStorageRoot();

    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("public-product-catalog-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없습니다.", e);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User brand;
    private String thumbnailKey;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "public-catalog-brand@example.com",
                "{noop}x",
                "대표자",
                "02-0000-0000",
                UserRole.BRAND));
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                "새로이 브랜드",
                "123-45-67890",
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층",
                new GeoCoordinates(
                        new BigDecimal("37.5010000"), new BigDecimal("127.0396000"))));
        thumbnailKey = fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brand.getId()),
                        "thumbnail.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(THUMBNAIL),
                        THUMBNAIL.length)
                .storageKey();
    }

    @Test
    void listsOnlyOnSaleProductsWithoutAuthentication() throws Exception {
        ProductModel draft = saveModel("공개 전 상품", false);
        ProductModel onSale = saveModel("판매 중 상품", true);

        mockMvc.perform(get(PUBLIC_PATH).queryParam("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].productModelId").value(onSale.getId()))
                .andExpect(jsonPath("$.data.content[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.content[0].brandId").value(brand.getId()))
                .andExpect(jsonPath("$.data.content[0].brandName").value("새로이 브랜드"))
                .andExpect(jsonPath("$.data.content[0].thumbnailUrl")
                        .value(PUBLIC_PATH + "/" + onSale.getId() + "/thumbnail"))
                .andExpect(jsonPath("$.data.content[?(@.productModelId == "
                        + draft.getId() + ")]").isEmpty());
    }

    @Test
    void servesOnlyOnSaleProductDetailsWithoutAuthentication() throws Exception {
        ProductModel draft = saveModel("공개 전 상품", false);
        ProductModel onSale = saveModel("판매 중 상품", true);

        mockMvc.perform(get(PUBLIC_PATH + "/{id}", onSale.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productModelId").value(onSale.getId()))
                .andExpect(jsonPath("$.data.brandName").value("새로이 브랜드"));

        mockMvc.perform(get(PUBLIC_PATH + "/{id}", draft.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    @Test
    void servesOnlyOnSaleThumbnailsWithoutAuthentication() throws Exception {
        ProductModel draft = saveModel("공개 전 상품", false);
        ProductModel onSale = saveModel("판매 중 상품", true);

        byte[] response = mockMvc.perform(get(PUBLIC_PATH + "/{id}/thumbnail", onSale.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=300"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(response).isEqualTo(THUMBNAIL);

        mockMvc.perform(get(PUBLIC_PATH + "/{id}/thumbnail", draft.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_NOT_FOUND"));
    }

    @Test
    void keepsProductManagementEndpointsAuthenticated() throws Exception {
        mockMvc.perform(get(MANAGEMENT_PATH + "/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(MANAGEMENT_PATH))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/product-images"))
                .andExpect(status().isUnauthorized());
    }

    private ProductModel saveModel(String name, boolean onSale) {
        ProductModel model = productModelRepository.save(ProductModel.register(
                name,
                ProductCategory.BAG,
                brand.getId(),
                129_000,
                "폐소재로 만든 상품",
                thumbnailKey));
        if (onSale) {
            model.putOnSale();
        }
        return productModelRepository.saveAndFlush(model);
    }
}
