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

import com.ssafy.upcycle.product.dto.ProductOrderCreateRequest;
import com.ssafy.upcycle.product.dto.ProductOrderResponse;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
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
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
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

        assertThat(products).hasSize(5);
        assertThat(products).allMatch(model -> model.getStatus() == ProductModelStatus.ON_SALE);
        assertThat(products).allMatch(model ->
                model.getThumbnailUrl().startsWith("/images/landing/product-"));

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

    @Test
    void exposesSeededProductsThroughThePublicCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/public/product-models").queryParam("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.content[*].status", everyItem(is("ON_SALE"))))
                .andExpect(jsonPath(
                        "$.data.content[*].brandName", everyItem(not(emptyString()))))
                .andExpect(jsonPath(
                        "$.data.content[*].thumbnailUrl",
                        everyItem(startsWith("/images/landing/product-"))));
    }

    @Test
    void doesNotDuplicateDataWhenTheSeederRunsAgain() {
        seeder.run(new DefaultApplicationArguments(new String[0]));

        List<ProductModel> products = seededProducts();
        assertThat(products).hasSize(5);
        assertThat(productionBatchRepository.findAll()).hasSize(5);
        assertThat(productItemRepository.findAll()).hasSize(15);
    }

    @Test
    void placesAnOrderWithSeededStock() {
        ProductModel product = seededProducts().get(0);
        User consumer = userRepository.saveAndFlush(User.register(
                "demo-catalog-consumer@test.com",
                "{noop}x",
                "시연 소비자",
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
            assertThat(item.imageUrl())
                    .isEqualTo(product.getThumbnailUrl());
        });
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                        product.getId(), ProductItemStatus.IN_STOCK))
                .isEqualTo(2L);

        seeder.run(new DefaultApplicationArguments(new String[0]));
        assertThat(productItemRepository.countByProductModelIdAndStatus(
                        product.getId(), ProductItemStatus.IN_STOCK))
                .isEqualTo(2L);
    }

    private List<ProductModel> seededProducts() {
        List<ProductModel> products = new ArrayList<>();
        for (String brandKey : BRAND_KEYS) {
            User brand = userRepository
                    .findByEmail(DemoProductCatalogSeeder.DEMO_EMAIL_PREFIX
                            + brandKey
                            + DemoProductCatalogSeeder.DEMO_EMAIL_DOMAIN)
                    .orElseThrow();
            assertThat(companyProfileRepository.findById(brand.getId())).isPresent();
            products.addAll(productModelRepository.findAllByBrandId(brand.getId()));
        }
        return products;
    }
}
