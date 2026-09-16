package com.ssafy.upcycle.product.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.repository.ProductImageRepository;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.service.ProductOrderCommandService;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.user.entity.ConsumerAddress;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.ConsumerAddressRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.service.BrandLogoService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:demo-product-catalog-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.demo-catalog.enabled=true",
    "app.demo-catalog.stock-per-product=3"
})
@AutoConfigureMockMvc
@Transactional
class DemoProductCatalogSeederTests {

    private static final List<String> BRAND_KEYS =
            List.of("reweave", "recode", "dusty-miller", "ocean-loop");

    /** 데모 상품 하나당 최종 분류 하나. 어느 2단 메뉴를 눌러도 브랜드마다 하나씩 나온다. */
    private static final List<String> SHOP_SLOTS = List.of(
            "TOP", "OUTER", "PANTS", "SKIRT", "DRESS",
            "TOTE", "SHOULDER", "CROSSBODY", "BACKPACK",
            "WALLET", "ACCESSORY", "HOME");

    private static final Path STORAGE_ROOT = createStorageRoot();

    /**
     * 시더가 임시 이미지를 실제로 저장소에 올린다. 기본 저장 루트({@code /app/files})를
     * 그대로 두면 개발 장비에서 기동 자체가 실패한다.
     */
    @DynamicPropertySource
    static void storageRoot(DynamicPropertyRegistry registry) {
        registry.add("app.storage.root", STORAGE_ROOT::toString);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("demo-product-catalog-");
        } catch (IOException e) {
            throw new IllegalStateException("임시 저장 루트를 만들 수 없다", e);
        }
    }

    @Autowired
    private DemoProductCatalogSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private ConsumerAddressRepository consumerAddressRepository;

    @Autowired
    private ProductOrderCommandService productOrderCommandService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedsDedicatedBrandsAndOrderableProducts() {
        List<ProductModel> products = seededProducts();

        assertThat(products).hasSize(48);
        assertThat(products).allMatch(model -> model.getStatus() == ProductModelStatus.ON_SALE);

        for (ProductModel model : products) {
            assertThat(productItemRepository.countByProductModelIdAndStatus(
                            model.getId(), ProductItemStatus.IN_STOCK))
                    .isEqualTo(3L);
            assertThat(productionBatchRepository.findAllByProductModelId(model.getId()))
                    .singleElement()
                    .extracting(batch -> batch.getStatus())
                    .isEqualTo(ProductionBatchStatus.CONFIRMED);
        }
    }

    /**
     * 브랜드 4개가 최종 분류 12개를 하나씩 나눠 갖는다. 이 분포가 깨지면 SHOP 의 어떤 2단
     * 메뉴는 비고 어떤 메뉴에는 같은 브랜드가 둘 들어간다.
     */
    @Test
    void spreadsProductsAcrossEveryShopSlot() {
        Map<String, Integer> perSlot = new HashMap<>();
        for (ProductModel model : seededProducts()) {
            String slot = model.getSubcategory() == null
                    ? model.getCategory().name()
                    : model.getSubcategory().name();
            perSlot.merge(slot, 1, Integer::sum);
        }

        assertThat(perSlot).containsOnlyKeys(SHOP_SLOTS.toArray(String[]::new));
        assertThat(perSlot.values()).allMatch(count -> count == 4);

        for (String brandKey : BRAND_KEYS) {
            assertThat(productsOf(brandKey)).hasSize(12);
        }
    }

    /**
     * 대표 이미지는 저장소에 실제로 올라간 파일이다. 프론트 정적 경로에 기대면 배포 위치가
     * 바뀔 때마다 이미지가 통째로 깨지고, 진짜 사진으로 바꿀 경로도 따로 생긴다.
     */
    @Test
    void uploadsPlaceholderThumbnailsToTheFileStorage() {
        for (ProductModel model : seededProducts()) {
            assertThat(model.getThumbnailUrl()).doesNotStartWith("/images/");
            assertThat(fileStorageService.exists(model.getThumbnailUrl())).isTrue();
            // 임시 이미지 상태에서는 갤러리를 채우지 않는다 — 같은 그림을 여러 칸에
            // 늘어놓을 이유가 없고, 상세 화면은 대표 이미지 한 장으로 물러선다.
            assertThat(productImageRepository
                            .findAllByProductModelIdOrderBySortOrderAsc(model.getId()))
                    .isEmpty();
        }
    }

    @Test
    void exposesSeededProductsThroughThePublicCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/public/product-models").queryParam("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(48))
                .andExpect(jsonPath("$.data.content.length()").value(48))
                .andExpect(jsonPath("$.data.content[*].status", everyItem(is("ON_SALE"))))
                .andExpect(jsonPath(
                        "$.data.content[*].brandName", everyItem(not(emptyString()))))
                .andExpect(jsonPath(
                        "$.data.content[*].thumbnailUrl",
                        everyItem(startsWith("/api/v1/public/product-models/"))));
    }

    @Test
    void doesNotDuplicateDataWhenTheSeederRunsAgain() {
        seeder.run(new DefaultApplicationArguments(new String[0]));

        List<ProductModel> products = seededProducts();
        assertThat(products).hasSize(48);
        assertThat(productionBatchRepository.findAll()).hasSize(48);
        assertThat(productItemRepository.findAll()).hasSize(48 * 3);
    }

    /**
     * 평상시 재기동에서는 이미지를 다시 올리지 않는다. 매번 올리면 저장소에 아무도
     * 가리키지 않는 파일이 쌓이고 {@code thumbnail_url} 이 기동할 때마다 달라진다.
     */
    @Test
    void keepsThumbnailsUntouchedOnAPlainRestart() {
        Map<Long, String> before = new HashMap<>();
        for (ProductModel model : seededProducts()) {
            before.put(model.getId(), model.getThumbnailUrl());
        }

        seeder.run(new DefaultApplicationArguments(new String[0]));

        for (ProductModel model : seededProducts()) {
            assertThat(model.getThumbnailUrl()).isEqualTo(before.get(model.getId()));
        }
    }

    @Test
    void placesAnOrderWithSeededStock() {
        ProductModel product = seededProducts().get(0);
        User consumer = userRepository.saveAndFlush(User.register(
                "demo-catalog-consumer@test.com",
                "{noop}x",
                "데모 소비자",
                "010-1234-5678",
                UserRole.CONSUMER));
        ConsumerAddress address = consumerAddressRepository.save(ConsumerAddress.register(
                consumer.getId(),
                "집",
                consumer.getName(),
                consumer.getPhone(),
                "06236",
                "서울특별시 강남구 테헤란로 212",
                "5층"));

        ProductOrderResponse response = productOrderCommandService.place(
                consumer.getId(), new ProductOrderCreateRequest(product.getId(), address.getId()));

        assertThat(response.brandName()).isNotBlank();
        assertThat(response.totalPrice()).isEqualTo(product.getPrice());
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo(product.getName());
            // 데모 상품의 대표 이미지도 저장소 키라, 주문 응답은 브랜드 상품과 똑같이
            // 썸네일 API 주소를 내려 준다 — 프론트 정적 경로가 섞이지 않는다.
            assertThat(item.imageUrl())
                    .isEqualTo("/api/v1/product-models/" + product.getId() + "/thumbnail");
        });
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                        product.getId(), ProductItemStatus.IN_STOCK))
                .isEqualTo(2L);

        seeder.run(new DefaultApplicationArguments(new String[0]));
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                        product.getId(), ProductItemStatus.IN_STOCK))
                .isEqualTo(2L);
    }

    /**
     * BRANDS 화면이 전부 빈 소개글·빈 로고로 나오면 목록·정렬·검색이 도는지 눈으로 확인할
     * 수 없다. 로고는 저장 키여야 한다 — 자유 문자열은 공개 응답에서 null 로 접힌다.
     */
    @Test
    void seedsBrandIntroductionsAndLogos() {
        for (String brandKey : BRAND_KEYS) {
            CompanyProfile profile = profileOf(brandKey);
            assertThat(profile.getIntroduction()).isNotBlank();
            assertThat(BrandLogoService.isBrandLogoKey(profile.getLogoUrl())).isTrue();
            assertThat(fileStorageService.exists(profile.getLogoUrl())).isTrue();
        }
    }

    /** 브랜드가 고쳐 둔 소개글을 재기동이 덮어쓰면 그 글이 사라진다. */
    @Test
    void doesNotOverwriteAnIntroductionThatIsAlreadyWritten() {
        CompanyProfile profile = profileOf("reweave");
        profile.updateIntroduction("브랜드가 직접 쓴 소개글");
        companyProfileRepository.saveAndFlush(profile);

        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertThat(profileOf("reweave").getIntroduction()).isEqualTo("브랜드가 직접 쓴 소개글");
    }

    private CompanyProfile profileOf(String brandKey) {
        User brand = userRepository
                .findByEmail(DemoProductCatalogSeeder.DEMO_EMAIL_PREFIX
                        + brandKey
                        + DemoProductCatalogSeeder.DEMO_EMAIL_DOMAIN)
                .orElseThrow();
        return companyProfileRepository.findById(brand.getId()).orElseThrow();
    }

    private List<ProductModel> seededProducts() {
        List<ProductModel> products = new ArrayList<>();
        for (String brandKey : BRAND_KEYS) {
            products.addAll(productsOf(brandKey));
        }
        return products;
    }

    private List<ProductModel> productsOf(String brandKey) {
        User brand = userRepository
                .findByEmail(DemoProductCatalogSeeder.DEMO_EMAIL_PREFIX
                        + brandKey
                        + DemoProductCatalogSeeder.DEMO_EMAIL_DOMAIN)
                .orElseThrow();
        assertThat(companyProfileRepository.findById(brand.getId())).isPresent();
        return productModelRepository.findAllByBrandId(brand.getId());
    }
}
