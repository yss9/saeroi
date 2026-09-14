package com.ssafy.upcycle.passport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialComposition;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialCompositionRepository;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingRequestType;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.repository.ProcessingRequestTypeRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.entity.ProductOrder;
import com.ssafy.upcycle.product.entity.ProductOrderItem;
import com.ssafy.upcycle.product.entity.ProductShipping;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.product.repository.ProductOrderItemRepository;
import com.ssafy.upcycle.product.repository.ProductOrderRepository;
import com.ssafy.upcycle.product.repository.ProductShippingRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.trade.entity.MaterialTrade;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.repository.MaterialTradeRepository;
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
import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * QR Passport 공개 조회. 고정하는 계약이 셋이다 — <b>인증 없이 열린다</b>,
 * <b>거래 정보(브랜드 id·가격)를 담지 않는다</b>, <b>모르는 토큰은 404 다</b>.
 *
 * <p>두 번째가 이 기능의 핵심이다. §6-3(2026-09-08)이 "보유자·단가·거래 상대" 를
 * 공개 응답에서 빼기로 결정했는데, 여기서 값이 새면 그 결정이 코드에 반영되지 않은 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PassportControllerTests {

    private static final String PATH = "/api/v1/qr";

    /**
     * {@code ProductModelControllerTests} 와 같은 이유로 임시 디렉터리를 쓴다 — 기본
     * 저장 루트(`/app/files`)에 그대로 두면 테스트가 레포 밖에 파일을 쓴다.
     */
    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("passport-controller-");
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
    private UserRepository userRepository;

    @Autowired
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private ProductionBatchRepository productionBatchRepository;

    @Autowired
    private ProductionInputRepository productionInputRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialCompositionRepository materialCompositionRepository;

    @Autowired
    private MaterialLineageRepository materialLineageRepository;

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Autowired
    private ProcessingInputRepository processingInputRepository;

    @Autowired
    private ProcessingRequestTypeRepository processingRequestTypeRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private BlockchainTransactionRepository blockchainTransactionRepository;

    @Autowired
    private EvidenceFileRepository evidenceFileRepository;

    @Autowired
    private ProductOrderRepository productOrderRepository;

    @Autowired
    private ProductOrderItemRepository productOrderItemRepository;

    @Autowired
    private ProductShippingRepository productShippingRepository;

    @Autowired
    private MaterialTradeRepository materialTradeRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private ProductModel model;

    @BeforeEach
    void setUp() {
        User brand = userRepository.save(User.register(
                "passport-brand@example.com", "{noop}x", "브랜드", "010-5555-5555", UserRole.BRAND));
        String thumbnailKey = storeThumbnail(brand.getId());
        model = productModelRepository.save(ProductModel.register(
                "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000, "폐데님으로 만든 가방", thumbnailKey));
        companyProfileRepository.save(CompanyProfile.register(
                brand.getId(),
                "리위브",
                "123-45-67890",
                "04790",
                "서울특별시 성동구 연무장길 1",
                "3층",
                new GeoCoordinates(new BigDecimal("37.5446000"), new BigDecimal("127.0559000"))));
    }

    private String storeThumbnail(Long brandId) {
        byte[] bytes = "photo-bytes".getBytes(StandardCharsets.UTF_8);
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brandId),
                        "thumb.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(bytes),
                        bytes.length)
                .storageKey();
    }

    @Test
    void requiresNoAuthentication() throws Exception {
        // SecurityConfig 의 permitAll 에서 이 경로가 빠지면 여기서 401 로 깨진다.
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void returnsThePublicProductInfo() throws Exception {
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCode").value(item.getItemCode()))
                .andExpect(jsonPath("$.data.serialNumber").value(item.getSerialNumber()))
                .andExpect(jsonPath("$.data.itemStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.product.name").value("RE:BAG"))
                .andExpect(jsonPath("$.data.product.brandName").value("리위브"))
                .andExpect(jsonPath("$.data.product.imageUrl")
                        .value("/api/v1/qr/" + item.getQrToken() + "/thumbnail"))
                .andExpect(jsonPath("$.data.product.thumbnailUrl").doesNotExist())
                .andExpect(jsonPath("$.data.product.brandId").doesNotExist())
                .andExpect(jsonPath("$.data.product.price").doesNotExist())
                .andExpect(jsonPath("$.data.product.category").value("BAG"))
                .andExpect(jsonPath("$.data.traceabilityAvailable").value(false));
    }

    @Test
    void doesNotLeakTradeInformation() throws Exception {
        // brandId·price 는 §6-3 이 명시적으로 뺀 "거래 정보" 다. 응답 어디에도 있으면 안 된다.
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brandId").doesNotExist())
                .andExpect(jsonPath("$.data.price").doesNotExist())
                .andExpect(jsonPath("$.data.product.brandId").doesNotExist())
                .andExpect(jsonPath("$.data.product.price").doesNotExist())
                .andExpect(jsonPath("$.data.product.thumbnailUrl").doesNotExist())
                .andExpect(jsonPath("$.data.productItemId").doesNotExist())
                .andExpect(jsonPath("$.data.productModelId").doesNotExist());
    }

    @Test
    void rejectsAnUnknownQrToken() throws Exception {
        // 모르는 토큰과 "형식은 맞는데 없는 토큰" 을 구분해 알려주지 않는다 — 토큰 자체가
        // 접근 통제라 실패 사유가 자세하면 무작위 시도에 힌트를 준다.
        mockMvc.perform(get(PATH + "/{qrToken}", "0".repeat(32)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_TOKEN_NOT_FOUND"));
    }

    // ── 썸네일 ──────────────────────────────────────────────

    @Test
    void thumbnailRequiresNoAuthenticationEither() throws Exception {
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}/thumbnail", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
                .andExpect(content().bytes("photo-bytes".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void thumbnailIsPubliclyCacheable() throws Exception {
        // 로그인 사용자별 응답이 아니라 qrToken 하나에 항상 같은 바이트라, 마켓 썸네일과
        // 달리 공용 캐시가 저장해도 안전하다.
        ProductItem item = produceItem();

        mockMvc.perform(get(PATH + "/{qrToken}/thumbnail", item.getQrToken()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=3600"));
    }

    @Test
    void thumbnailForAnUnknownQrTokenIs404() throws Exception {
        mockMvc.perform(get(PATH + "/{qrToken}/thumbnail", "0".repeat(32)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QR_TOKEN_NOT_FOUND"));
    }

    @Test
    void returnsAJourneyWithMultipleSourcesAndPublicBlockchainProofs() throws Exception {
        ProductItem item = produceTraceableItem();

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceabilityAvailable").value(true))
                .andExpect(jsonPath("$.data.journey.stages.length()").value(5))
                .andExpect(jsonPath("$.data.journey.stages[0].stage").value("SOURCE"))
                .andExpect(jsonPath("$.data.journey.stages[1].stage").value("COLLECTION"))
                .andExpect(jsonPath("$.data.journey.stages[1].edgeIds.length()").value(2))
                .andExpect(jsonPath("$.data.journey.nodes[0].companyName").value("한빛섬유"))
                .andExpect(jsonPath("$.data.journey.nodes[0].locationName")
                        .value("경기도 용인시 처인구 남사읍 10 A동"))
                .andExpect(jsonPath("$.data.journey.nodes[0].latitude").value(37.1151000))
                .andExpect(jsonPath("$.data.journey.nodes[1].companyName").value("대성원단"))
                .andExpect(jsonPath("$.data.journey.nodes[2].companyName").value("그린프로세싱"))
                .andExpect(jsonPath("$.data.journey.nodes[2].processTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("$.data.journey.nodes[4].companyName").value("리위브"))
                .andExpect(jsonPath("$.data.journey.nodes[4].blockchainProofs[0].functionName")
                        .value("createBatch"))
                .andExpect(jsonPath("$.data.journey.nodes[4].evidenceSummary[0].evidenceType")
                        .value("PROCESS_PHOTO"))
                .andExpect(jsonPath("$.data.journey.materialComposition[0].fiberType")
                        .value("COTTON"))
                .andExpect(jsonPath("$.data.journey.materialComposition[0].percentage")
                        .value(50.00))
                .andExpect(jsonPath("$.data.journey.blockchainVerified").value(false))
                .andExpect(jsonPath("$.data.journey.stages[4].status").value("PENDING"))
                .andExpect(jsonPath("$.data.journey.nodes[4].blockchainProofs[0].fromWallet")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.journey.nodes[4].evidenceSummary[0].fileHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.journey.nodes[0].materials[0].materialId")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.journey.nodes[0].materials[0].batchQuantityGrams")
                        .value(6000))
                // 업체는 상호명·역할·주소·좌표까지 공개한다(A-1·B-1).
                .andExpect(jsonPath("$.data.journey.nodes[0].companyRole").value("SUPPLIER"))
                .andExpect(jsonPath("$.data.journey.nodes[2].companyRole").value("PROCESSOR"))
                .andExpect(jsonPath("$.data.journey.nodes[4].companyRole").value("BRAND"))
                .andExpect(jsonPath("$.data.journey.nodes[4].longitude").value(127.0559000))
                // 수거는 공급업체에서 출발하는 이동선이다 — 가상의 수거업체 노드를 두지 않는다.
                .andExpect(jsonPath("$.data.journey.stages[1].nodeIds.length()").value(0))
                .andExpect(jsonPath("$.data.journey.edges[0].stage").value("COLLECTION"))
                .andExpect(jsonPath("$.data.journey.edges[0].fromNodeId").value("source-1"))
                .andExpect(jsonPath("$.data.journey.edges[0].toNodeId").value("processing-1"))
                .andExpect(jsonPath("$.data.journey.edges[0].shippedAt")
                        .value("2026-09-08T10:02:00"))
                .andExpect(jsonPath("$.data.journey.edges[0].receivedAt")
                        .value("2026-09-08T10:03:00"))
                .andExpect(jsonPath("$.data.journey.edges[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.journey.stages[3].edgeIds.length()").value(2))
                // 소재 배치 수량은 공개하지만 거래 수량·단가·보유자는 어느 깊이에도 없다.
                .andExpect(jsonPath("$.data.journey.nodes[0].materials[0].quantity")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.journey.edges[0].quantity").doesNotExist())
                .andExpect(jsonPath("$.data.journey.edges[0].quantityUnit").doesNotExist())
                .andExpect(jsonPath("$.data.journey.nodes[0].companyId").doesNotExist())
                .andExpect(jsonPath("$.data.journey.nodes[0].holderName").doesNotExist());
    }

    @Test
    void collectionMovementUsesTheCompletedSupplierTrade() throws Exception {
        ProductItem item = produceTraceableItem();
        Material denim = materialRepository.findByMaterialCode("MAT-PASSPORT-A").orElseThrow();
        LocalDateTime tradedAt = LocalDateTime.of(2026, 9, 7, 9, 0);
        User unrelatedBrand = userRepository.save(User.register(
                "unrelated-passport-brand@example.com",
                "{noop}x",
                "다른 브랜드",
                "010-7777-7000",
                UserRole.BRAND));
        MaterialTrade unrelatedTrade = MaterialTrade.create(
                "TRADE-PASSPORT-UNRELATED",
                2L,
                denim.getId(),
                denim.getRegistrantId(),
                unrelatedBrand.getId(),
                "0x" + "a".repeat(40),
                "0x" + "c".repeat(40),
                1_000,
                20_000,
                PaymentMethod.MOCK);
        unrelatedTrade.markPaid(
                "trade-payment-unrelated",
                20_000,
                tradedAt.minusDays(3),
                PaymentMethod.MOCK);
        unrelatedTrade.ship(tradedAt.minusDays(2));
        unrelatedTrade.confirmReceipt(tradedAt.minusDays(1), tradedAt.minusDays(1));
        unrelatedTrade.complete(tradedAt.minusDays(1));
        materialTradeRepository.save(unrelatedTrade);

        MaterialTrade trade = MaterialTrade.create(
                "TRADE-PASSPORT-1",
                1L,
                denim.getId(),
                denim.getRegistrantId(),
                model.getBrandId(),
                "0x" + "a".repeat(40),
                "0x" + "b".repeat(40),
                5_000,
                120_000,
                PaymentMethod.MOCK);
        trade.markPaid("trade-payment-1", 120_000, tradedAt, PaymentMethod.MOCK);
        trade.ship(tradedAt.plusDays(1));
        trade.confirmReceipt(tradedAt.plusDays(2), tradedAt.plusDays(2));
        trade.complete(tradedAt.plusDays(2));
        materialTradeRepository.save(trade);

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journey.edges[0].fromNodeId").value("source-1"))
                .andExpect(jsonPath("$.data.journey.edges[0].shippedAt")
                        .value("2026-09-08T09:00:00"))
                .andExpect(jsonPath("$.data.journey.edges[0].receivedAt")
                        .value("2026-09-09T09:00:00"))
                .andExpect(jsonPath("$.data.journey.edges[0].status").value("COMPLETED"))
                // 거래 금액·수량·거래 번호는 이동선에 따라나오지 않는다.
                .andExpect(jsonPath("$.data.journey.edges[0].totalPrice").doesNotExist())
                .andExpect(jsonPath("$.data.journey.edges[0].tradeCode").doesNotExist());
    }

    @Test
    void exposesOnlyTheDeliveryRegionForACompletedOrder() throws Exception {
        ProductItem item = produceTraceableItem();
        item.reserve();
        item.markSold();
        productItemRepository.save(item);
        User consumer = userRepository.save(User.register(
                "passport-consumer@example.com",
                "{noop}x",
                "소비자",
                "010-7777-7777",
                UserRole.CONSUMER));
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 9, 0);
        ProductOrder order = productOrderRepository.save(ProductOrder.place(
                "ORDER-DELIVERY-1",
                consumer.getId(),
                model.getBrandId(),
                129_000,
                PaymentMethod.MOCK,
                now.plusMinutes(30)));
        order.markPaid("payment-1", 129_000, now.plusMinutes(1), PaymentMethod.MOCK);
        order.ship(now.plusDays(1));
        order.confirmDelivery(now.plusDays(2));
        productOrderItemRepository.save(ProductOrderItem.of(
                order.getId(), item.getId(), model.getId(), 129_000));
        productShippingRepository.save(ProductShipping.of(
                order.getId(),
                "소비자",
                "010-7777-7777",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101동 1001호"));

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journey.stages[4].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.journey.nodes[5].stage").value("DELIVERY"))
                .andExpect(jsonPath("$.data.journey.nodes[5].locationName").value("서울특별시"))
                .andExpect(jsonPath("$.data.journey.nodes[5].latitude").isEmpty())
                .andExpect(jsonPath("$.data.journey.nodes[5].receiverName").doesNotExist())
                .andExpect(jsonPath("$.data.journey.nodes[5].addressDetail").doesNotExist())
                .andExpect(jsonPath("$.data.journey.orderCode").doesNotExist())
                .andExpect(jsonPath("$.data.journey.price").doesNotExist())
                // 배송 구간도 이동선이다 — 발송일·수령일과 완료 상태를 동일하게 싫는다.
                .andExpect(jsonPath("$.data.journey.edges[4].stage").value("DELIVERY"))
                .andExpect(jsonPath("$.data.journey.edges[4].fromNodeId").value("brand-final"))
                .andExpect(jsonPath("$.data.journey.edges[4].toNodeId").value("delivery"))
                .andExpect(jsonPath("$.data.journey.edges[4].shippedAt")
                        .value("2026-09-12T09:00:00"))
                .andExpect(jsonPath("$.data.journey.edges[4].receivedAt")
                        .value("2026-09-13T09:00:00"))
                .andExpect(jsonPath("$.data.journey.edges[4].status").value("COMPLETED"));
    }

    @Test
    void marksTheDeliveryStageInProgressWhileTheOrderIsStillShipping() throws Exception {
        ProductItem item = produceTraceableItem();
        item.reserve();
        item.markSold();
        productItemRepository.save(item);
        User consumer = userRepository.save(User.register(
                "passport-consumer-2@example.com",
                "{noop}x",
                "소비자",
                "010-7777-7778",
                UserRole.CONSUMER));
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 9, 0);
        ProductOrder order = productOrderRepository.save(ProductOrder.place(
                "ORDER-DELIVERY-2",
                consumer.getId(),
                model.getBrandId(),
                129_000,
                PaymentMethod.MOCK,
                now.plusMinutes(30)));
        order.markPaid("payment-2", 129_000, now.plusMinutes(1), PaymentMethod.MOCK);
        order.ship(now.plusDays(1));
        productOrderItemRepository.save(ProductOrderItem.of(
                order.getId(), item.getId(), model.getId(), 129_000));
        productShippingRepository.save(ProductShipping.of(
                order.getId(),
                "소비자",
                "010-7777-7778",
                "06236",
                "서울특별시 강남구 테헤란로 123",
                "101동 1001호"));

        mockMvc.perform(get(PATH + "/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journey.stages[4].status").value("CURRENT"))
                .andExpect(jsonPath("$.data.journey.edges[4].shippedAt")
                        .value("2026-09-12T09:00:00"))
                .andExpect(jsonPath("$.data.journey.edges[4].receivedAt").isEmpty())
                .andExpect(jsonPath("$.data.journey.edges[4].status").value("CURRENT"));
    }

    private ProductItem produceItem() {
        String qrToken = UUID.randomUUID().toString().replace("-", "");
        String itemCode = "ITEM-" + UUID.randomUUID().toString().substring(0, 20);
        return productItemRepository.save(ProductItem.produce(1L, model.getId(), 1, itemCode, qrToken));
    }

    private ProductItem produceTraceableItem() {
        User firstSupplier = supplier(
                "source-a@example.com",
                "한빛섬유",
                "111-11-11111",
                "경기도 용인시 처인구 남사읍 10",
                "A동",
                "37.1151000",
                "127.2069000");
        User secondSupplier = supplier(
                "source-b@example.com",
                "대성원단",
                "222-22-22222",
                "대전광역시 대덕구 산업단지로 20",
                null,
                "36.4059000",
                "127.4068000");
        Material denim = materialRepository.save(Material.register(
                "MAT-PASSPORT-A",
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님",
                null,
                "A",
                6_000,
                null,
                null,
                firstSupplier.getId()));
        Material lining = materialRepository.save(Material.register(
                "MAT-PASSPORT-B",
                MaterialType.LINING,
                MaterialRootOrigin.WASTE,
                "재사용 안감",
                null,
                "A",
                4_000,
                null,
                null,
                secondSupplier.getId()));
        materialCompositionRepository.save(MaterialComposition.of(
                denim.getId(), "COTTON", new BigDecimal("100.00")));

        User firstProcessor = companyUser(
                "processor-a@example.com",
                "그린프로세싱",
                "333-33-33333",
                "인천광역시 서구 가좌로 30",
                null,
                "37.4895000",
                "126.6750000",
                UserRole.PROCESSOR);
        User secondProcessor = companyUser(
                "processor-b@example.com",
                "에코리사이클",
                "444-44-44444",
                "충청남도 아산시 산업로 40",
                "2공장",
                "36.7898000",
                "127.0018000",
                UserRole.PROCESSOR);
        Material processedDenim = processMaterial(
                denim, firstProcessor, "JOB-PASSPORT-A", 1L, ProcessType.CLEANING);
        Material processedLining = processMaterial(
                lining, secondProcessor, "JOB-PASSPORT-B", 2L, ProcessType.SORTING);
        materialCompositionRepository.save(MaterialComposition.of(
                processedDenim.getId(), "COTTON", new BigDecimal("100.00")));
        materialCompositionRepository.save(MaterialComposition.of(
                processedLining.getId(), "POLYESTER", new BigDecimal("100.00")));

        ProductionBatch batch = productionBatchRepository.save(ProductionBatch.open(
                "BATCH-PASSPORT-1",
                "RE:BAG 생산",
                model.getId(),
                1,
                model.getBrandId(),
                "0x1234567890123456789012345678901234567890"));
        productionInputRepository.save(ProductionInput.record(
                batch.getId(), processedDenim.getId(), 5_000, 5_000, 0, 0, null, 101L));
        productionInputRepository.save(ProductionInput.record(
                batch.getId(), processedLining.getId(), 5_000, 5_000, 0, 0, null, 102L));
        batch.startSigning();
        batch.confirm(0, 10_000, LocalDateTime.of(2026, 9, 10, 18, 0));

        blockchainTransactionRepository.save(BlockchainTransaction.confirmed(
                31_221L,
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "0x" + "1".repeat(64),
                BlockchainReferenceType.PRODUCTION,
                batch.getId(),
                "createBatch",
                "0x1234567890123456789012345678901234567890",
                123L,
                LocalDateTime.of(2026, 9, 10, 18, 1)));
        evidenceFileRepository.save(EvidenceFile.record(
                EvidenceReferenceType.PRODUCTION_BATCH,
                batch.getId(),
                "production/batch/process.jpg",
                "0x" + "2".repeat(64),
                EvidenceType.PROCESS_PHOTO,
                model.getBrandId()));

        String qrToken = UUID.randomUUID().toString().replace("-", "");
        String itemCode = "ITEM-" + UUID.randomUUID().toString().substring(0, 20);
        return productItemRepository.save(ProductItem.produce(
                batch.getId(), model.getId(), 1, itemCode, qrToken));
    }

    private Material processMaterial(
            Material parent,
            User processor,
            String jobCode,
            Long sequence,
            ProcessType processType) {
        LocalDateTime now = LocalDateTime.of(2026, 9, 8, 9, 0).plusHours(sequence);
        ProcessingJob job = processingJobRepository.save(ProcessingJob.create(
                jobCode,
                100L + sequence,
                200L + sequence,
                model.getBrandId(),
                processor.getId(),
                "0x1234567890123456789012345678901234567890",
                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                0,
                PaymentMethod.MOCK,
                now));
        processingInputRepository.save(new ProcessingInput(
                job.getId(), parent.getId(), 5_000, 300L + sequence));
        processingRequestTypeRepository.save(new ProcessingRequestType(
                job.getProcessingRequestId(), processType, 1));
        job.markPaid("payment-" + sequence, 0, now.plusMinutes(1));
        job.ship(now.plusMinutes(2));
        job.receive(now.plusMinutes(3));
        job.startProcessing();
        job.submitResult(
                5_000,
                5_000,
                "0x" + String.valueOf(sequence + 2).repeat(64),
                "https://example.com/manifest/" + sequence,
                now.plusMinutes(4));
        job.confirmResult(now.plusMinutes(5));

        Material output = materialRepository.save(Material.process(
                "MAT-PROCESSED-" + sequence,
                parent,
                parent.getMaterialType(),
                parent.getName() + " 가공",
                "A",
                5_000,
                processor.getId()));
        materialLineageRepository.save(MaterialLineage.of(
                output.getId(),
                parent.getId(),
                MaterialLineageRelationType.PROCESSING,
                job.getId()));
        return output;
    }

    private User supplier(
            String email,
            String companyName,
            String businessNumber,
            String address,
            String addressDetail,
            String latitude,
            String longitude) {
        return companyUser(
                email,
                companyName,
                businessNumber,
                address,
                addressDetail,
                latitude,
                longitude,
                UserRole.SUPPLIER);
    }

    private User companyUser(
            String email,
            String companyName,
            String businessNumber,
            String address,
            String addressDetail,
            String latitude,
            String longitude,
            UserRole role) {
        User supplier = userRepository.save(User.register(
                email, "{noop}x", companyName, "010-8888-8888", role));
        companyProfileRepository.save(CompanyProfile.register(
                supplier.getId(),
                companyName,
                businessNumber,
                "00000",
                address,
                addressDetail,
                new GeoCoordinates(new BigDecimal(latitude), new BigDecimal(longitude))));
        return supplier;
    }
}
