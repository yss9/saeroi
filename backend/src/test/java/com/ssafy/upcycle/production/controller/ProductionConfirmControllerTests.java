package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialGenerationType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductItem;
import com.ssafy.upcycle.product.entity.ProductItemStatus;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.production.repository.ProductionOutputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생산 배치 확정. <b>이 프로젝트에서 가장 무거운 트랜잭션이다.</b>
 *
 * <p>확정 한 번에 다섯 가지가 일어나고, 그 하나하나를 여기서 고정한다.
 *
 * <ol>
 *   <li>잠금이 {@code CONSUMED} 로 바뀌고 보유량이 차감된다</li>
 *   <li>개체가 생산 개수만큼 만들어진다 — <b>여기서 처음 팔 수 있는 물건이 생긴다</b></li>
 *   <li>업사이클 비율이 계산되어 스냅샷으로 굳는다</li>
 *   <li>자투리가 새 소재로 발행된다 ({@code PRODUCTION_SCRAP})</li>
 *   <li>새 소재와 부모가 혈통으로 이어진다 — 없으면 QR 이력이 자투리에서 끊긴다</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionConfirmControllerTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;
    private static final int PRODUCED_QUANTITY = 3;

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
    private ProductModelRepository productModelRepository;

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialLineageRepository lineageRepository;

    @Autowired
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProductionInputRepository inputRepository;

    @Autowired
    private ProductionOutputRepository outputRepository;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long virginMaterialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "confirm-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        virginMaterialId = heldMaterial(MaterialRootOrigin.VIRGIN);
    }

    // ── 개체 생성 ────────────────────────────────────────────

    @Test
    void confirmationCreatesOneItemPerProducedUnit() throws Exception {
        // 지금까지 재고가 영원히 0 이라 판매를 시작할 방법이 없었다. 여기서 물건이 생긴다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);

        confirm(brandToken, batchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").exists());

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                        productModelId, ProductItemStatus.IN_STOCK))
                .isEqualTo(PRODUCED_QUANTITY);
    }

    @Test
    void everyItemGetsItsOwnUnguessableQrToken() throws Exception {
        // QR 조회는 인증 없이 열린 경로다. 값을 맞힐 수 있으면 남의 제품 이력을 열람할 수 있다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);
        confirm(brandToken, batchId).andExpect(status().isOk());

        List<ProductItem> items = productItemRepository.findAllByProductionBatchId(batchId);
        assertThat(items).hasSize(PRODUCED_QUANTITY);
        assertThat(items).extracting(ProductItem::getQrToken)
                .allMatch(token -> token.matches("^[0-9a-f]{32}$"))
                .doesNotHaveDuplicates();
        assertThat(items).extracting(ProductItem::getSerialNumber)
                .containsExactlyInAnyOrder(1, 2, 3);
        assertThat(items).extracting(ProductItem::getItemCode).doesNotHaveDuplicates();
    }

    // ── 소재 소비 ────────────────────────────────────────────

    @Test
    void confirmationConsumesTheLockAndDecreasesTheBalance() throws Exception {
        // 잠금만 소비하면 보유량이 그대로라 같은 소재를 또 쓸 수 있고,
        // 보유량만 줄이면 잠금이 남아 가용량이 두 번 깎인다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);

        confirm(brandToken, batchId).andExpect(status().isOk());

        assertThat(lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                        virginMaterialId, brand.getId(), MaterialLockStatus.ACTIVE))
                .isEmpty();
        assertThat(lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                        virginMaterialId, brand.getId(), MaterialLockStatus.CONSUMED))
                .hasSize(1);
        assertThat(balanceRepository.findByMaterialIdAndOwnerId(virginMaterialId, brand.getId()))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(HELD_QUANTITY - 30_000L);
    }

    // ── 업사이클 비율 ────────────────────────────────────────

    @Test
    void ratioCountsOnlyWhatWentIntoTheProduct() throws Exception {
        // 자투리와 폐기는 제품에 남아 있지 않으므로 비율에 셀 수 없다.
        Long wasteMaterialId = processedWasteMaterial();
        Long batchId = openedBatch();
        addInput(batchId, virginMaterialId, 40_000L, 30_000L, 5_000L, 5_000L);
        addInput(batchId, wasteMaterialId, 80_000L, 70_000L, 10_000L, 0L);

        confirm(brandToken, batchId)
                .andExpect(status().isOk())
                // virgin 30,000 / upcycled 70,000 → 70.00%
                .andExpect(jsonPath("$.data.virginUsedQuantity").value(30_000))
                .andExpect(jsonPath("$.data.upcycledUsedQuantity").value(70_000))
                .andExpect(jsonPath("$.data.totalUsedQuantity").value(100_000))
                .andExpect(jsonPath("$.data.upcycleRatio").value(70.00));
    }

    @Test
    void refusesABatchWhereNothingGoesIntoTheProduct() throws Exception {
        // 전량이 자투리·폐기면 제품을 만들지 못하고 비율도 0/0 이 된다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 0L, 20_000L, 10_000L);

        confirm(brandToken, batchId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_NO_PRODUCT_MATERIAL"));
    }

    // ── 자투리 재발행 ────────────────────────────────────────

    @Test
    void scrapBecomesANewMaterialThatInheritsItsOrigin() throws Exception {
        // 물려받지 않으면 폐데님에서 나온 자투리가 VIRGIN 이 되고,
        // 다음 생산의 업사이클 비율이 거짓이 된다.
        Long wasteMaterialId = processedWasteMaterial();
        Long batchId = openedBatch();
        addInput(batchId, wasteMaterialId, 30_000L, 25_000L, 5_000L, 0L);

        confirm(brandToken, batchId).andExpect(status().isOk());

        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();
        Long scrapId = outputRepository.findAllByProductionInputId(inputId).get(0).getNewMaterialId();
        assertThat(scrapId).isNotNull();
        assertThat(materialRepository.findById(scrapId))
                .get()
                .satisfies(scrap -> {
                    assertThat(scrap.getGenerationType())
                            .isEqualTo(MaterialGenerationType.PRODUCTION_SCRAP);
                    assertThat(scrap.getRootOrigin()).isEqualTo(MaterialRootOrigin.WASTE);
                    assertThat(scrap.getTotalQuantity()).isEqualTo(5_000L);
                    // 임시 코드가 살아남으면 유일하지만 의미 없는 코드가 된다.
                    assertThat(scrap.getMaterialCode()).matches("^MAT-\\d{6,}$");
                });
    }

    @Test
    void scrapIsHeldByTheProducerSoItCanBeUsedAgain() throws Exception {
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 25_000L, 5_000L, 0L);
        confirm(brandToken, batchId).andExpect(status().isOk());

        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();
        Long scrapId = outputRepository.findAllByProductionInputId(inputId).get(0).getNewMaterialId();

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(scrapId, brand.getId()))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(5_000L);
    }

    @Test
    void scrapIsLinkedToItsParentSoTheJourneyDoesNotBreak() throws Exception {
        // 간선이 없으면 QR 이력이 자투리에서 끊긴다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 25_000L, 5_000L, 0L);
        confirm(brandToken, batchId).andExpect(status().isOk());

        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();
        Long scrapId = outputRepository.findAllByProductionInputId(inputId).get(0).getNewMaterialId();

        assertThat(lineageRepository.findAllByChildMaterialId(scrapId))
                .singleElement()
                .satisfies(edge -> {
                    assertThat(edge.getParentMaterialId()).isEqualTo(virginMaterialId);
                    assertThat(edge.getReferenceId()).isEqualTo(batchId);
                });
    }

    @Test
    void noScrapMeansNoNewMaterial() throws Exception {
        long materialsBefore = materialRepository.count();
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);

        confirm(brandToken, batchId).andExpect(status().isOk());

        assertThat(materialRepository.count()).isEqualTo(materialsBefore);
    }

    // ── 되돌릴 수 없음 ──────────────────────────────────────

    @Test
    void aBatchCannotBeConfirmedTwice() throws Exception {
        // 두 번 확정되면 개체가 두 배로 만들어지고 소재는 두 번 차감된다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);
        confirm(brandToken, batchId).andExpect(status().isOk());

        confirm(brandToken, batchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_EDITABLE"));

        assertThat(productItemRepository.countByProductModelIdAndStatus(
                        productModelId, ProductItemStatus.IN_STOCK))
                .isEqualTo(PRODUCED_QUANTITY);
    }

    @Test
    void inputsCannotBeChangedAfterConfirmation() throws Exception {
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);
        confirm(brandToken, batchId).andExpect(status().isOk());

        Long another = heldMaterial(MaterialRootOrigin.VIRGIN);
        mockMvc.perform(post(PATH + "/{batchId}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "inputs", List.of(inputRequest(another, 1_000L, 1_000L, 0L, 0L))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_EDITABLE"));
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void refusesAnEmptyBatch() throws Exception {
        // 소재 없이 만들어진 제품은 QR 을 찍어도 보여 줄 이력이 없다.
        Long batchId = openedBatch();

        confirm(brandToken, batchId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_NO_INPUT"));
    }

    @Test
    void refusesAnotherProducersBatch() throws Exception {
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 30_000L, 0L, 0L);
        User otherBrand = userRepository.save(User.register(
                "confirm-intruder@example.com", "{noop}x", "침입자", "010-4444-4444", UserRole.BRAND));

        confirm(tokenFor(otherBrand), batchId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(PATH + "/{batchId}/confirm", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leavesNothingBehindWhenItRefuses() throws Exception {
        // 거절될 요청을 반쯤 처리하다 되돌리는 것과 시작 전에 거절하는 것은 다르다.
        Long batchId = batchWithInput(virginMaterialId, 30_000L, 0L, 30_000L, 0L);

        confirm(brandToken, batchId).andExpect(status().isUnprocessableEntity());

        assertThat(productItemRepository.findAllByProductionBatchId(batchId)).isEmpty();
        assertThat(lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                        virginMaterialId, brand.getId(), MaterialLockStatus.ACTIVE))
                .hasSize(1);
        assertThat(balanceRepository.findByMaterialIdAndOwnerId(virginMaterialId, brand.getId()))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(HELD_QUANTITY);
        assertThat(batchRepository.findById(batchId))
                .get()
                .extracting(batch -> batch.getStatus())
                .isEqualTo(ProductionBatchStatus.DRAFT);
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions confirm(String token, Long batchId) throws Exception {
        return mockMvc.perform(post(PATH + "/{batchId}/confirm", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long openedBatch() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 1차 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", PRODUCED_QUANTITY);

        String response = mockMvc.perform(post(PATH)
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
            Long batchId, Long materialId, long input, long productUsed, long scrap, long waste)
            throws Exception {
        mockMvc.perform(post(PATH + "/{batchId}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "inputs",
                                List.of(inputRequest(materialId, input, productUsed, scrap, waste))))))
                .andExpect(status().isOk());
    }

    private Long batchWithInput(
            Long materialId, long input, long productUsed, long scrap, long waste) throws Exception {
        Long batchId = openedBatch();
        addInput(batchId, materialId, input, productUsed, scrap, waste);
        return batchId;
    }

    private Map<String, Object> inputRequest(
            Long materialId, long input, long productUsed, long scrap, long waste) {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", materialId);
        request.put("inputQuantity", input);
        request.put("productUsedQuantity", productUsed);
        request.put("reusableScrapQuantity", scrap);
        request.put("wasteQuantity", waste);
        return request;
    }

    /** {@code Material.register} 는 언제나 {@code INITIAL} 이라 VIRGIN 이면 투입 가능하다. */
    private Long heldMaterial(MaterialRootOrigin rootOrigin) {
        Material material = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                rootOrigin,
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

    /**
     * 가공을 거친 폐소재. 투입 자격이 {@code WASTE + INITIAL} 을 막으므로 자투리로 만든다 —
     * {@code produceScrap} 이 부모의 {@code root_origin} 을 물려주면서
     * {@code generation_type} 만 {@code PRODUCTION_SCRAP} 으로 바꾼다.
     */
    private Long processedWasteMaterial() {
        Material parent = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님",
                null,
                "B",
                HELD_QUANTITY,
                null,
                null,
                brand.getId()));
        Material processed = materialRepository.save(Material.produceScrap(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                parent,
                HELD_QUANTITY,
                brand.getId()));
        balanceRepository.save(
                MaterialBalance.of(processed.getId(), brand.getId(), WALLET, HELD_QUANTITY));
        return processed.getId();
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
