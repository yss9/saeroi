package com.ssafy.upcycle.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.common.storage.FileReference;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품 등록 → 생산 → 판매 시작 → QR 조회가 <b>실제로 이어지는지</b> 확인한다.
 *
 * <p>각 단계는 자기 테스트가 있다. 여기서 고정하는 것은 <b>그 사이의 연결</b>이고, 그건
 * 단계별 테스트가 전부 통과해도 끊어질 수 있다.
 *
 * <p>이 티켓 전까지 이 흐름은 <b>중간이 비어 있었다</b>. 제품을 등록해도 개체를 만드는
 * 방법이 없어 재고가 영원히 0 이었고, 그래서 판매 시작은 언제나 422 였으며 QR 은 보여 줄
 * 개체가 없었다. 확정이 그 자리를 메운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionJourneyTests {

    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("production-journey-");
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
    private UserWalletRepository walletRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private FileStorageService fileStorageService;

    private User brand;
    private String brandToken;
    private Long materialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "journey-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        materialId = heldMaterial();
    }

    @Test
    void aProductBecomesSellableOnlyAfterABatchIsConfirmed() throws Exception {
        Long productModelId = registerProduct();

        // 1. 등록 직후에는 팔 개체가 없다. 판매 시작은 거절된다.
        startSelling(productModelId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_MODEL_HAS_NO_STOCK"));

        // 2. 배치를 열고 소재를 연결한 뒤 확정한다.
        Long batchId = openBatch(productModelId, 3);
        addInput(batchId, materialId, 30_000L, 27_000L, 3_000L, 0L);
        mockMvc.perform(post("/api/v1/production-batches/{id}/confirm", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // 3. 이제 재고가 생겼고 판매를 시작할 수 있다.
        startSelling(productModelId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.stockQuantity").value(3));

        // 4. 마켓 목록에도 뜬다.
        mockMvc.perform(get("/api/v1/product-models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].productModelId").value(productModelId))
                .andExpect(jsonPath("$.data.content[0].stockQuantity").value(3));
    }

    @Test
    void aConfirmedItemIsReadableByAnyoneThroughItsQrCode() throws Exception {
        // QR 은 비회원 공개 경로다. 확정이 개체를 만들기 전에는 보여 줄 것이 없었다.
        Long productModelId = registerProduct();
        Long batchId = openBatch(productModelId, 2);
        addInput(batchId, materialId, 20_000L, 20_000L, 0L, 0L);
        mockMvc.perform(post("/api/v1/production-batches/{id}/confirm", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());

        ProductItem item = productItemRepository.findAllByProductionBatchId(batchId).get(0);

        // 토큰만 있으면 로그인 없이 열린다.
        mockMvc.perform(get("/api/v1/qr/{qrToken}", item.getQrToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemCode").value(item.getItemCode()))
                .andExpect(jsonPath("$.data.serialNumber").value(item.getSerialNumber()))
                .andExpect(jsonPath("$.data.itemStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.data.product.name").value("RE:BAG"));
    }

    @Test
    void scrapFromOneBatchCanBeFedIntoTheNext() throws Exception {
        // 자투리가 새 소재로 되살아난다는 것은, 다음 생산에 실제로 쓸 수 있다는 뜻이다.
        // 쓸 수 없다면 그 소재는 잔량만 차지하는 행이다.
        Long productModelId = registerProduct();
        Long firstBatch = openBatch(productModelId, 1);
        addInput(firstBatch, materialId, 30_000L, 25_000L, 5_000L, 0L);
        mockMvc.perform(post("/api/v1/production-batches/{id}/confirm", firstBatch)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());

        Long scrapId = materialRepository.findAll().stream()
                .filter(material -> material.getName().endsWith("자투리"))
                .findFirst()
                .orElseThrow()
                .getId();

        Long secondBatch = openBatch(productModelId, 1);
        addInput(secondBatch, scrapId, 5_000L, 5_000L, 0L, 0L);
        mockMvc.perform(post("/api/v1/production-batches/{id}/confirm", secondBatch)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(scrapId, brand.getId()))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(0L);
    }

    // ── helpers ─────────────────────────────────────────────

    private Long registerProduct() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("name", "RE:BAG");
        request.put("category", "BAG");
        request.put("price", 129_000);
        request.put("description", "폐데님으로 만든 가방");
        request.put("thumbnailUrl", storeImage());

        String response = mockMvc.perform(post("/api/v1/product-models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productModelId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions startSelling(Long productModelId)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/product-models/{id}/status", productModelId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("action", "ON_SALE"))));
    }

    private Long openBatch(Long productModelId, int producedQuantity) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", producedQuantity);

        String response = mockMvc.perform(post("/api/v1/production-batches")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productionBatchId").asLong();
    }

    private void addInput(
            Long batchId, Long material, long input, long productUsed, long scrap, long waste)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", material);
        request.put("inputQuantity", input);
        request.put("productUsedQuantity", productUsed);
        request.put("reusableScrapQuantity", scrap);
        request.put("wasteQuantity", waste);

        mockMvc.perform(post("/api/v1/production-batches/{id}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk());
    }

    private Long heldMaterial() {
        Material material = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.VIRGIN,
                "원단",
                null,
                "A",
                HELD_QUANTITY,
                null,
                null,
                brand.getId()));
        balanceRepository.save(
                MaterialBalance.of(material.getId(), brand.getId(), WALLET, HELD_QUANTITY));
        return material.getId();
    }

    private String storeImage() {
        byte[] bytes = "photo-bytes".getBytes(StandardCharsets.UTF_8);
        return fileStorageService.store(
                        new FileReference("PRODUCT_IMAGE", brand.getId()),
                        "thumb.jpg",
                        "image/jpeg",
                        new ByteArrayInputStream(bytes),
                        bytes.length)
                .storageKey();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
