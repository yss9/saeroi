package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
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
 * 배치 취소 · 개체 목록 · 투입의 소재 이름. 프론트가 요청한 세 건이다.
 *
 * <ol>
 *   <li><b>투입 응답에 소재 이름</b> — {@code materialId} 만 주면 화면이 "소재 #11" 로
 *       표시하는데, 확정된 배치의 소재는 소비되어 보유 목록에서 사라지므로 이름을 붙일
 *       방법이 없다</li>
 *   <li><b>개체 목록</b> — 확정이 개체를 만들지만 브랜드가 그것을 볼 경로가 없었다.
 *       QR 라벨을 인쇄하려면 토큰이 필요하다</li>
 *   <li><b>배치 취소</b> — {@code CANCELED} 가 상태에는 있는데 엔드포인트가 없어,
 *       잘못 연 배치가 목록에 영원히 쌓이고 소재도 계속 묶인다</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionBatchItemsTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;

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
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProductionInputRepository inputRepository;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "batch-items-brand@example.com", "{noop}x", "브랜드", "010-0000-0000",
                UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial("폐데님 원단");
    }

    // ── ① 투입 응답의 소재 이름 ──────────────────────────────

    @Test
    void inputsCarryTheMaterialNameSoTheScreenNeedNotLookItUp() throws Exception {
        Long batchId = openedBatch();

        addInput(batchId, materialId, 30_000L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs[0].materialName").value("폐데님 원단"))
                .andExpect(jsonPath("$.data.inputs[0].materialCode").isNotEmpty());
    }

    @Test
    void theNameSurvivesConfirmationWhenTheMaterialIsAlreadyConsumed() throws Exception {
        // 이력이 가장 필요한 시점이 여기다. 확정된 배치의 소재는 보유 목록에서 사라져
        // 프론트가 이름을 찾을 방법이 없다.
        Long batchId = openedBatch();
        addInput(batchId, materialId, HELD_QUANTITY).andExpect(status().isOk());
        confirm(batchId).andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/{batchId}", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs[0].materialName").value("폐데님 원단"));
    }

    @Test
    void namesStayMatchedWhenSeveralMaterialsAreInvolved() throws Exception {
        // 한 번에 읽어 붙이므로 순서가 어긋날 수 있다. 짝이 맞는지 본다.
        Long cotton = heldMaterial("폐면 원단");
        Long batchId = openedBatch();

        String response = addInputs(batchId, List.of(
                        inputRequest(materialId, 10_000L),
                        inputRequest(cotton, 20_000L)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<Long, String> names = new HashMap<>();
        objectMapper.readTree(response).path("data").path("inputs").forEach(input ->
                names.put(input.path("materialId").asLong(), input.path("materialName").asText()));
        assertThat(names).containsEntry(materialId, "폐데님 원단").containsEntry(cotton, "폐면 원단");
    }

    // ── ② 개체 목록 ─────────────────────────────────────────

    @Test
    void itemsAreListedAfterConfirmationForLabelPrinting() throws Exception {
        Long batchId = openedBatch();
        addInput(batchId, materialId, 30_000L).andExpect(status().isOk());
        confirm(batchId).andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/{batchId}/items", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                // 순번대로 나와야 라벨을 순서대로 인쇄한다.
                .andExpect(jsonPath("$.data[0].serialNumber").value(1))
                .andExpect(jsonPath("$.data[2].serialNumber").value(3))
                .andExpect(jsonPath("$.data[0].status").value("IN_STOCK"))
                .andExpect(jsonPath("$.data[0].itemCode").isNotEmpty())
                // QR 토큰이 실려야 인쇄할 수 있다.
                .andExpect(jsonPath("$.data[0].qrToken").isNotEmpty());
    }

    @Test
    void anUnconfirmedBatchHasNoItemsYet() throws Exception {
        // 오류가 아니라 사실이다. 개체는 확정이 만든다.
        Long batchId = openedBatch();

        mockMvc.perform(get(PATH + "/{batchId}/items", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void anotherBrandCannotReadTheQrTokens() throws Exception {
        // qrToken 은 인증 없이 열리는 조회 경로의 열쇠다. 남에게 주면
        // 남의 제품 이력을 전부 열람할 수 있다.
        Long batchId = openedBatch();
        addInput(batchId, materialId, 30_000L).andExpect(status().isOk());
        confirm(batchId).andExpect(status().isOk());
        User otherBrand = userRepository.save(User.register(
                "batch-items-other@example.com", "{noop}x", "다른브랜드", "010-3333-3333",
                UserRole.BRAND));

        mockMvc.perform(get(PATH + "/{batchId}/items", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void listingItemsRequiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH + "/{batchId}/items", 1L))
                .andExpect(status().isUnauthorized());
    }

    // ── ③ 배치 취소 ─────────────────────────────────────────

    @Test
    void cancelingADraftBatchReleasesItsLocks() throws Exception {
        // 취소가 없으면 잘못 연 배치가 잡은 소재가 계속 묶인다.
        Long batchId = openedBatch();
        addInput(batchId, materialId, 30_000L).andExpect(status().isOk());

        cancel(batchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        assertThat(lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                        materialId, brand.getId(), MaterialLockStatus.ACTIVE))
                .isEmpty();
        assertThat(lockRepository.findAllByReferenceTypeAndReferenceId(
                        MaterialLockReferenceType.PRODUCTION_BATCH, batchId))
                .singleElement()
                .extracting(lock -> lock.getStatus())
                .isEqualTo(MaterialLockStatus.RELEASED);
    }

    @Test
    void canceledQuantityCanBeUsedAgain() throws Exception {
        Long batchId = openedBatch();
        addInput(batchId, materialId, HELD_QUANTITY).andExpect(status().isOk());
        cancel(batchId).andExpect(status().isOk());

        // 취소했는데 수량이 돌아오지 않으면 사용자는 이유를 알 수 없다.
        Long another = openedBatch();
        addInput(another, materialId, HELD_QUANTITY).andExpect(status().isOk());
    }

    @Test
    void canceledBatchKeepsItsInputsAsHistory() throws Exception {
        // "이런 소재를 넣으려다 취소했다" 도 이력이다. 취소된 배치는 되살아나지 않으므로
        // 그 행이 다시 쓰이지도 않는다.
        Long batchId = openedBatch();
        addInput(batchId, materialId, 30_000L).andExpect(status().isOk());

        cancel(batchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs.length()").value(1));

        assertThat(inputRepository.findAllByProductionBatchId(batchId)).hasSize(1);
    }

    @Test
    void anEmptyBatchCanBeCanceled() throws Exception {
        Long batchId = openedBatch();

        cancel(batchId).andExpect(status().isOk());

        assertThat(batchRepository.findById(batchId))
                .get()
                .extracting(batch -> batch.getStatus())
                .isEqualTo(ProductionBatchStatus.CANCELED);
    }

    @Test
    void aConfirmedBatchCannotBeCanceled() throws Exception {
        // 소재는 이미 소비됐고 개체는 이미 만들어졌다.
        Long batchId = openedBatch();
        addInput(batchId, materialId, 30_000L).andExpect(status().isOk());
        confirm(batchId).andExpect(status().isOk());

        cancel(batchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_EDITABLE"));
    }

    @Test
    void aCanceledBatchCannotTakeNewInputs() throws Exception {
        Long batchId = openedBatch();
        cancel(batchId).andExpect(status().isOk());

        addInput(batchId, materialId, 10_000L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_EDITABLE"));
    }

    @Test
    void anotherBrandCannotCancelIt() throws Exception {
        Long batchId = openedBatch();
        User otherBrand = userRepository.save(User.register(
                "batch-items-intruder@example.com", "{noop}x", "침입자", "010-4444-4444",
                UserRole.BRAND));

        cancel(batchId, tokenFor(otherBrand))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions cancel(Long batchId) throws Exception {
        return cancel(batchId, brandToken);
    }

    private ResultActions cancel(Long batchId, String token) throws Exception {
        return mockMvc.perform(post(PATH + "/{batchId}/cancel", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions confirm(Long batchId) throws Exception {
        return mockMvc.perform(post(PATH + "/{batchId}/confirm", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken));
    }

    private ResultActions addInput(Long batchId, Long material, long quantity) throws Exception {
        return addInputs(batchId, List.of(inputRequest(material, quantity)));
    }

    private ResultActions addInputs(Long batchId, List<Map<String, Object>> inputs)
            throws Exception {
        return mockMvc.perform(post(PATH + "/{batchId}/inputs", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("inputs", inputs))));
    }

    private Map<String, Object> inputRequest(Long material, long quantity) {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", material);
        request.put("inputQuantity", quantity);
        request.put("productUsedQuantity", quantity);
        request.put("reusableScrapQuantity", 0L);
        request.put("wasteQuantity", 0L);
        return request;
    }

    private Long openedBatch() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 1차 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", 3);

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

    private Long heldMaterial(String name) {
        Material material = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.VIRGIN,
                name,
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

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
