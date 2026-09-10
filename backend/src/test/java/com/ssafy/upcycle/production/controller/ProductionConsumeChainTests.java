package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.ArrayList;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 소각의 온체인 결과를 DB 에 맞추는 경로.
 *
 * <p>확정은 소재를 <b>DB 에서만</b> 줄인다. 온체인 토큰은 그대로 남아 있어서, 체인만 보면
 * 브랜드가 여전히 그 소재를 갖고 있는 것처럼 보인다 — <b>같은 소재로 두 번 업사이클했다고
 * 주장할 수 있게 된다.</b>
 *
 * <p>여기서 고정하는 계약이 넷이다 — <b>투입 전부를 보고해야 한다</b>, <b>온체인 잠금
 * 번호가 DB 에 붙는다</b>, <b>확정된 배치만</b>, <b>한 번만</b>.
 *
 * <p>속성 목록이 {@code ChainLinkApiTests} 와 같아야 한다. 그 목록이 스프링 컨텍스트 캐시의
 * 키라, 다르면 컨텍스트가 하나 더 만들어진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.blockchain.chain-id=31221",
        "app.blockchain.material-token-address=0xbe98f5a9aa8d0480b09c689e38806accd41a0f66",
        "app.blockchain.marketplace-address=0x53fc91be37720fcb051004b7aab73639ca266853",
        "app.blockchain.production-registry-address=0x65b4d0d9f9f0e0a4b0b0f9e8d7c6b5a4d3c2b1a0",
})
class ProductionConsumeChainTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final String PRODUCTION_REGISTRY = "0x65b4d0d9f9f0e0a4b0b0f9e8d7c6b5a4d3c2b1a0";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;
    private static final String TX_HASH = "0x" + "c".repeat(64);

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
    private ProductionInputRepository inputRepository;

    @Autowired
    private BlockchainTransactionRepository transactionRepository;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "consume-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial();
    }

    // ── 기록 ────────────────────────────────────────────────

    @Test
    void linksTheChainLockIdThatNobodyFilledBefore() throws Exception {
        // material_lock.chain_lock_id 는 컬럼만 있고 채우는 경로가 없었다.
        // 소각 함수가 온체인 lockId 를 인자로 받으므로, 이 순간이 두 번호가 만나는 자리다.
        Long batchId = confirmedBatch();
        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);
        assertThat(lockRepository.findById(input.getMaterialLockId()))
                .get()
                .extracting(MaterialLock::getChainLockId)
                .isNull();

        linkConsume(batchId, List.of(consumed(input.getId(), 42L))).andExpect(status().isOk());

        assertThat(lockRepository.findById(input.getMaterialLockId()))
                .get()
                .extracting(MaterialLock::getChainLockId)
                .isEqualTo(42L);
    }

    @Test
    void recordsTheFunctionTheBrandActuallySigned() throws Exception {
        Long batchId = confirmedBatch();
        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);

        linkConsume(batchId, List.of(consumed(input.getId(), 42L))).andExpect(status().isOk());

        assertThat(transactionRepository.findAll())
                .filteredOn(tx -> "confirmProduction".equals(tx.getFunctionName()))
                .singleElement()
                .satisfies(tx -> {
                    assertThat(tx.getReferenceType()).isEqualTo(BlockchainReferenceType.MATERIAL);
                    assertThat(tx.getReferenceId()).isEqualTo(batchId);
                    // 소각은 MaterialToken 이 하지만 브랜드가 서명하는 것은 레지스트리의
                    // confirmProduction 이다. consumeLockedMaterial 은 레지스트리가 내부에서
                    // 부르는 호출이라 msg.sender 가 브랜드가 아니다.
                    assertThat(tx.getContractAddress()).isEqualTo(PRODUCTION_REGISTRY);
                    assertThat(tx.getFromWallet()).isEqualTo(WALLET);
                    assertThat(tx.getTxHash()).isEqualTo(TX_HASH);
                });
    }

    @Test
    void theInputResponseShowsWhetherTheBurnWasReported() throws Exception {
        // 화면이 "온체인 소각" 버튼을 언제 보여줄지 이 값으로 정한다.
        Long batchId = confirmedBatch();
        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);

        mockMvc.perform(get(PATH + "/{id}", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs[0].chainLockId").doesNotExist());

        linkConsume(batchId, List.of(consumed(input.getId(), 42L))).andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/{id}", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs[0].chainLockId").value(42));
    }

    @Test
    void reportsEveryInputOfTheBatchAtOnce() throws Exception {
        Long second = heldMaterial();
        Long batchId = confirmedBatch(second);
        List<ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId);

        List<Map<String, Object>> consumed = new ArrayList<>();
        consumed.add(consumed(inputs.get(0).getId(), 42L));
        consumed.add(consumed(inputs.get(1).getId(), 43L));

        linkConsume(batchId, consumed).andExpect(status().isOk());

        assertThat(lockRepository.findAllById(inputs.stream()
                        .map(ProductionInput::getMaterialLockId)
                        .toList()))
                .extracting(MaterialLock::getChainLockId)
                .containsExactlyInAnyOrder(42L, 43L);
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void refusesAPartialReport() throws Exception {
        // 일부만 소각하고 보고하면 DB 는 다 썼다고 하는데 체인에는 남아 있는 소재가 생기고,
        // 그 소재로 또 만들었다고 주장할 수 있다.
        Long second = heldMaterial();
        Long batchId = confirmedBatch(second);
        ProductionInput first = inputRepository.findAllByProductionBatchId(batchId).get(0);

        linkConsume(batchId, List.of(consumed(first.getId(), 42L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CONSUME_MISMATCH"));
    }

    @Test
    void refusesAnInputFromAnotherBatch() throws Exception {
        Long batchId = confirmedBatch();
        Long otherMaterial = heldMaterial();
        Long otherBatchId = confirmedBatch(otherMaterial, false);
        ProductionInput strangerInput =
                inputRepository.findAllByProductionBatchId(otherBatchId).get(0);

        linkConsume(batchId, List.of(consumed(strangerInput.getId(), 42L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CONSUME_MISMATCH"));
    }

    @Test
    void refusesTheSameInputTwiceInOneReport() throws Exception {
        Long second = heldMaterial();
        Long batchId = confirmedBatch(second);
        ProductionInput first = inputRepository.findAllByProductionBatchId(batchId).get(0);

        linkConsume(batchId, List.of(consumed(first.getId(), 42L), consumed(first.getId(), 43L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CONSUME_MISMATCH"));
    }

    @Test
    void refusesToReportTwice() throws Exception {
        // 두 번 기록되면 같은 소재를 두 번 소각한 것처럼 보인다.
        Long batchId = confirmedBatch();
        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);
        linkConsume(batchId, List.of(consumed(input.getId(), 42L))).andExpect(status().isOk());

        linkConsume(batchId, List.of(consumed(input.getId(), 44L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CONSUME_ALREADY_RECORDED"));
    }

    @Test
    void refusesABatchThatIsNotConfirmedYet() throws Exception {
        // 소비 자체가 아직 일어나지 않았다. 소각할 잠금이 CONSUMED 도 아니다.
        Long batchId = openedBatchWithInput();

        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);
        linkConsume(batchId, List.of(consumed(input.getId(), 42L)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_CONFIRMED"));
    }

    @Test
    void refusesAnotherProducersBatch() throws Exception {
        Long batchId = confirmedBatch();
        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);
        User otherBrand = userRepository.save(User.register(
                "consume-intruder@example.com", "{noop}x", "침입자", "010-4444-4444",
                UserRole.BRAND));
        walletRepository.save(UserWallet.link(otherBrand.getId(), otherWallet(), CHAIN_ID));

        mockMvc.perform(post(PATH + "/{id}/chain/consume", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request(List.of(consumed(input.getId(), 42L))))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void refusesAnEmptyReport() throws Exception {
        Long batchId = confirmedBatch();

        linkConsume(batchId, List.of()).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(PATH + "/{id}/chain/consume", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request(List.of(consumed(1L, 42L))))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leavesNothingBehindWhenTheReportIsRejected() throws Exception {
        // 절반만 연결된 상태 — 잠금 일부에만 온체인 번호가 붙고 기록은 없는 — 를
        // 만들지 않는다.
        Long second = heldMaterial();
        Long batchId = confirmedBatch(second);
        List<ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId);

        List<Map<String, Object>> consumed = new ArrayList<>();
        consumed.add(consumed(inputs.get(0).getId(), 42L));
        consumed.add(consumed(999_999L, 43L));

        linkConsume(batchId, consumed).andExpect(status().isBadRequest());

        assertThat(lockRepository.findAllById(inputs.stream()
                        .map(ProductionInput::getMaterialLockId)
                        .toList()))
                .extracting(MaterialLock::getChainLockId)
                .containsOnlyNulls();
        assertThat(transactionRepository.findAll())
                .filteredOn(tx -> "confirmProduction".equals(tx.getFunctionName()))
                .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions linkConsume(Long batchId, List<Map<String, Object>> consumedLocks)
            throws Exception {
        return mockMvc.perform(post(PATH + "/{id}/chain/consume", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request(consumedLocks))));
    }

    private Map<String, Object> request(List<Map<String, Object>> consumedLocks) {
        Map<String, Object> request = new HashMap<>();
        request.put("txHash", TX_HASH);
        request.put("blockNumber", 1_234L);
        request.put("consumedLocks", consumedLocks);
        return request;
    }

    private Map<String, Object> consumed(Long productionInputId, Long chainLockId) {
        Map<String, Object> consumed = new HashMap<>();
        consumed.put("productionInputId", productionInputId);
        consumed.put("chainLockId", chainLockId);
        return consumed;
    }

    private Long confirmedBatch() throws Exception {
        return confirmedBatch(null);
    }

    private Long confirmedBatch(Long extraMaterialId) throws Exception {
        return confirmedBatch(extraMaterialId, true);
    }

    /**
     * @param extraMaterialId 함께 투입할 두 번째 소재. {@code null} 이면 기본 소재만 넣는다
     * @param useDefaultMaterial 기본 소재를 넣을지. 다른 배치를 만들 때는 같은 소재를 두
     *     배치에 넣을 수 없어 끈다
     */
    private Long confirmedBatch(Long extraMaterialId, boolean useDefaultMaterial)
            throws Exception {
        Long batchId = openedBatch();
        List<Map<String, Object>> inputs = new ArrayList<>();
        if (useDefaultMaterial) {
            inputs.add(input(materialId));
        }
        if (extraMaterialId != null) {
            inputs.add(input(extraMaterialId));
        }
        mockMvc.perform(post(PATH + "/{id}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inputs", inputs))))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH + "/{id}/confirm", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());
        return batchId;
    }

    private Long openedBatchWithInput() throws Exception {
        Long batchId = openedBatch();
        mockMvc.perform(post(PATH + "/{id}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inputs", List.of(input(materialId))))))
                .andExpect(status().isOk());
        return batchId;
    }

    private Map<String, Object> input(Long material) {
        Map<String, Object> input = new HashMap<>();
        input.put("materialId", material);
        input.put("inputQuantity", 30_000L);
        input.put("productUsedQuantity", 30_000L);
        input.put("reusableScrapQuantity", 0L);
        input.put("wasteQuantity", 0L);
        return input;
    }

    private Long openedBatch() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 1차 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", 2);

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

    private String otherWallet() {
        String hex = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        return "0x" + hex.substring(0, 40);
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
