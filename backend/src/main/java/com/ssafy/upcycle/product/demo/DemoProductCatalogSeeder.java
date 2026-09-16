package com.ssafy.upcycle.product.demo;

import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductModelStatus;
import com.ssafy.upcycle.product.entity.ProductSubcategory;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈·SHOP 시연 상품을 실제 주문 가능한 도메인 데이터로 준비한다.
 *
 * <p>{@code app.demo-catalog.enabled=true} 일 때만 빈으로 생성된다. 시연 브랜드 이메일,
 * 상품명, 배치 코드가 고정되어 있어 재기동해도 중복되지 않는다. 이미 판매된 개체는 다시
 * 만들지 않으며, 설정 수량보다 총 생산 개체가 적은 부분 초기화 상태만 보완한다.
 */
@Component
@ConditionalOnProperty(name = "app.demo-catalog.enabled", havingValue = "true")
public class DemoProductCatalogSeeder implements ApplicationRunner {

    public static final String DEMO_EMAIL_PREFIX = "demo-catalog-";
    public static final String DEMO_EMAIL_DOMAIN = "@saeroi.local";

    private static final Logger log = LoggerFactory.getLogger(DemoProductCatalogSeeder.class);
    private static final long USED_MATERIAL_GRAMS_PER_ITEM = 500L;

    private static final List<DemoBrand> BRANDS = List.of(
            new DemoBrand(
                    "reweave",
                    "ReWeave",
                    "101-81-00001",
                    "0x000000000000000000000000000000000000d001"),
            new DemoBrand(
                    "recode",
                    "RE:CODE",
                    "101-81-00002",
                    "0x000000000000000000000000000000000000d002"),
            new DemoBrand(
                    "dusty-miller",
                    "DUSTY MILLER",
                    "101-81-00003",
                    "0x000000000000000000000000000000000000d003"),
            new DemoBrand(
                    "ocean-loop",
                    "OCEAN LOOP",
                    "101-81-00004",
                    "0x000000000000000000000000000000000000d004"));

    private static final List<DemoProduct> PRODUCTS = List.of(
            new DemoProduct(
                    "denim-circle",
                    "reweave",
                    "데님 서클 백",
                    ProductCategory.BAG,
                    ProductSubcategory.SHOULDER,
                    129_000L,
                    "폐데님 조각의 곡선을 살려 완성한 원형 숄더백입니다. 소재마다 다른 색과 흔적이 하나뿐인 표정을 만듭니다.",
                    "/images/landing/product-denim-circle.webp",
                    "DEMO-PB-DENIM"),
            new DemoProduct(
                    "work-jacket",
                    "recode",
                    "패치워크 워크 자켓",
                    ProductCategory.CLOTHING,
                    ProductSubcategory.OUTER,
                    149_000L,
                    "남겨진 워크웨어 원단을 해체하고 다시 조합한 패치워크 자켓입니다. 원단의 마모와 봉제 흔적을 그대로 살렸습니다.",
                    "/images/landing/product-work-jacket.webp",
                    "DEMO-PB-JACKET"),
            new DemoProduct(
                    "saddle-bag",
                    "dusty-miller",
                    "새들 슬링 백",
                    ProductCategory.BAG,
                    ProductSubcategory.CROSSBODY,
                    99_000L,
                    "사용 후 회수된 원단을 유연한 곡선 형태로 재단한 슬링 백입니다. 가볍게 멜 수 있는 일상용 수납 구조를 담았습니다.",
                    "/images/landing/product-saddle-bag.webp",
                    "DEMO-PB-SADDLE"),
            new DemoProduct(
                    "bucket-hat",
                    "ocean-loop",
                    "패치워크 버킷 햇",
                    ProductCategory.ACCESSORY,
                    null,
                    59_000L,
                    "수거된 소재의 서로 다른 결을 이어 만든 버킷 햇입니다. 조각 배치가 제품마다 달라 같은 모양이 반복되지 않습니다.",
                    "/images/landing/product-bucket-hat.webp",
                    "DEMO-PB-HAT"),
            new DemoProduct(
                    "market-tote",
                    "recode",
                    "마켓 토트",
                    ProductCategory.BAG,
                    ProductSubcategory.TOTE,
                    79_000L,
                    "남은 원단을 넉넉한 크기로 다시 구성한 데일리 토트백입니다. 장보기와 일상 수납에 모두 사용할 수 있습니다.",
                    "/images/landing/product-market-tote.webp",
                    "DEMO-PB-TOTE"));

    private final UserRepository userRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final ProductModelRepository productModelRepository;
    private final ProductionBatchRepository productionBatchRepository;
    private final ProductItemRepository productItemRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int stockPerProduct;

    public DemoProductCatalogSeeder(
            UserRepository userRepository,
            CompanyProfileRepository companyProfileRepository,
            ProductModelRepository productModelRepository,
            ProductionBatchRepository productionBatchRepository,
            ProductItemRepository productItemRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${app.demo-catalog.stock-per-product:10}") int stockPerProduct) {
        if (stockPerProduct <= 0) {
            throw new IllegalArgumentException("Demo product stock must be positive");
        }
        this.userRepository = userRepository;
        this.companyProfileRepository = companyProfileRepository;
        this.productModelRepository = productModelRepository;
        this.productionBatchRepository = productionBatchRepository;
        this.productItemRepository = productItemRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.stockPerProduct = stockPerProduct;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, SeededBrand> brands = new HashMap<>();
        for (DemoBrand definition : BRANDS) {
            brands.put(definition.key(), seedBrand(definition));
        }
        for (DemoProduct definition : PRODUCTS) {
            seedProduct(definition, brands.get(definition.brandKey()));
        }
        log.info(
                "Demo product catalog ready: brands={} products={} initialStockPerProduct={}",
                BRANDS.size(),
                PRODUCTS.size(),
                stockPerProduct);
    }

    private SeededBrand seedBrand(DemoBrand definition) {
        String email = DEMO_EMAIL_PREFIX + definition.key() + DEMO_EMAIL_DOMAIN;
        User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.saveAndFlush(
                User.register(
                        email,
                        passwordEncoder.encode(UUID.randomUUID().toString()),
                        definition.companyName() + " 담당자",
                        "02-0000-0000",
                        UserRole.BRAND)));
        if (user.getRole() != UserRole.BRAND || !user.isActive()) {
            throw new IllegalStateException("Demo catalog account must be an active BRAND: " + email);
        }

        companyProfileRepository.findById(user.getId()).orElseGet(() ->
                companyProfileRepository.save(CompanyProfile.register(
                        user.getId(),
                        definition.companyName(),
                        definition.businessNumber(),
                        "04782",
                        "서울특별시 성동구 연무장길 1",
                        "SAEROI DEMO",
                        new GeoCoordinates(
                                new BigDecimal("37.5446000"),
                                new BigDecimal("127.0559000")))));
        return new SeededBrand(user, definition.walletAddress());
    }

    private void seedProduct(DemoProduct definition, SeededBrand brand) {
        ProductModel model = findDemoModel(brand.user().getId(), definition.name());
        if (model == null) {
            model = productModelRepository.saveAndFlush(ProductModel.register(
                    definition.name(),
                    definition.category(),
                    definition.subcategory(),
                    brand.user().getId(),
                    definition.price(),
                    definition.description(),
                    definition.thumbnailUrl()));
        } else {
            requireCategory(model, definition);
            if (model.getStatus() == ProductModelStatus.DISCONTINUED) {
                log.warn("Skipping discontinued demo product: {}", definition.name());
                return;
            }
            model.update(
                    definition.name(),
                    definition.price(),
                    definition.description(),
                    definition.thumbnailUrl());
            // 하위 분류가 생기기 전에 시드된 행은 값이 비어 있다. 재기동 때 채워야 SHOP
            // 2단 메뉴에 들어온다 — 브랜드가 손으로 고쳐 줄 데이터가 아니다.
            if (definition.subcategory() != null
                    && model.getSubcategory() != definition.subcategory()) {
                model.assignSubcategory(definition.subcategory());
            }
        }

        ProductionBatch batch = seedBatch(definition, brand, model);
        int addedStock = seedItems(definition, model, batch);
        if (model.getStatus() == ProductModelStatus.DRAFT) {
            model.putOnSale();
        } else if (model.getStatus() == ProductModelStatus.SOLD_OUT && addedStock > 0) {
            model.restock();
        }
        productModelRepository.save(model);
    }

    private ProductModel findDemoModel(Long brandId, String name) {
        List<ProductModel> matches = productModelRepository.findAllByBrandId(brandId).stream()
                .filter(model -> model.getName().equals(name))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("Duplicate demo product model: " + name);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    private ProductionBatch seedBatch(
            DemoProduct definition, SeededBrand brand, ProductModel model) {
        ProductionBatch batch = productionBatchRepository
                .findByBatchCode(definition.batchCode())
                .orElseGet(() -> productionBatchRepository.saveAndFlush(ProductionBatch.open(
                        definition.batchCode(),
                        definition.name() + " 시연 생산",
                        model.getId(),
                        stockPerProduct,
                        brand.user().getId(),
                        brand.walletAddress())));
        if (!batch.getProductModelId().equals(model.getId())
                || !batch.getProducerId().equals(brand.user().getId())) {
            throw new IllegalStateException(
                    "Demo batch belongs to a different product or brand: " + definition.batchCode());
        }
        if (batch.getProducedQuantity() != stockPerProduct) {
            throw new IllegalStateException(
                    "Demo stock setting differs from the existing batch: "
                            + definition.batchCode());
        }

        ProductionBatchStatus status = batch.getStatus();
        if (status == ProductionBatchStatus.FAILED) {
            batch.reopen();
            status = batch.getStatus();
        }
        if (status == ProductionBatchStatus.DRAFT) {
            batch.startSigning();
            status = batch.getStatus();
        }
        if (status == ProductionBatchStatus.SIGNING) {
            batch.confirm(
                    0L,
                    stockPerProduct * USED_MATERIAL_GRAMS_PER_ITEM,
                    LocalDateTime.now(clock));
        } else if (status != ProductionBatchStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Demo batch cannot seed stock while " + status + ": " + definition.batchCode());
        }
        return productionBatchRepository.save(batch);
    }

    private int seedItems(
            DemoProduct definition, ProductModel model, ProductionBatch batch) {
        List<ProductItem> existing = productItemRepository.findAllByProductionBatchId(batch.getId());
        Set<Integer> existingSerials = new HashSet<>();
        for (ProductItem item : existing) {
            existingSerials.add(item.getSerialNumber());
        }

        int added = 0;
        for (int serial = 1; serial <= stockPerProduct; serial++) {
            if (existingSerials.contains(serial)) {
                continue;
            }
            String seed = definition.key() + ":" + serial;
            String qrToken = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8))
                    .toString()
                    .replace("-", "");
            productItemRepository.save(ProductItem.produce(
                    batch.getId(),
                    model.getId(),
                    serial,
                    "DEMO-" + definition.key().toUpperCase() + "-" + "%03d".formatted(serial),
                    qrToken));
            added++;
        }
        return added;
    }

    private void requireCategory(ProductModel model, DemoProduct definition) {
        if (model.getCategory() != definition.category()) {
            throw new IllegalStateException(
                    "Demo product category mismatch: " + definition.name());
        }
    }

    private record DemoBrand(
            String key,
            String companyName,
            String businessNumber,
            String walletAddress) {}

    private record SeededBrand(User user, String walletAddress) {}

    private record DemoProduct(
            String key,
            String brandKey,
            String name,
            ProductCategory category,
            /** 2단 메뉴가 없는 분류(ACCESSORY 등)는 {@code null} 이다. */
            ProductSubcategory subcategory,
            long price,
            String description,
            String thumbnailUrl,
            String batchCode) {}
}
