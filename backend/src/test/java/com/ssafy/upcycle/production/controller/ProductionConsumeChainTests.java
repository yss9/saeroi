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
 * 소각(3단계)의 온체인 결과를 DB 에 맞추는 경로.
 *
 * <p>확정은 소재를 <b>DB 에서만</b> 줄인다. 온체인 토큰은 그대로 남아 있어서, 체인만 보면
 * 브랜드가 여전히 그 소재를 갖고 있는 것처럼 보인다 — <b>같은 소재로 두 번 업사이클했다고
 * 주장할 수 있게 된다.</b>
 *
 * <p><b>잠금 번호는 이 요청이 받지 않는다.</b> 그 값은 2단계({@code submitPlanAndLock})
 * 영수증에서 나오고 {@code POST /{id}/chain} 이 이미 받아 두었다. 3단계까지 화면이 들고
 * 오게 하면 그 사이 새로고침 한 번에 잃어버린다 — {@code chainBatchId} 를 서버가 보관하기로
 * 한 것과 같은 문제다. 여기서는 그 값이 채워져 있는지만 확인한다.
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
    private static final String MATERIAL_TOKEN_ADDRESS =
            "0xbe98f5a9aa8d0480b09c689e38806accd41a0f66";
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

    private long tokenSequence = 1_000L;
    private long chainBatchSequence = 500L;
    private long chainLockSequence = 100L;

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
    void recordsTheFunctionTheBrandActuallySigned() throws Exception {
        Long batchId = readyForConsume();

        linkConsume(batchId).andExpect(status().isOk());

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
    void theChainLockIdComesFromStepTwoNotFromThisRequest() throws Exception {
        // 잠금 번호는 submitPlanAndLock 영수증에서만 나온다. 3단계까지 화면이 들고 오게 하면
        // 그 사이 새로고침 한 번에 잃어버린다 — chainBatchId 와 같은 문제다.
        Long batchId = readyForConsume();
        ProductionInput input = inputRepository.findAllByProductionBatchId(batchId).get(0);

        assertThat(lockRepository.findById(input.getMaterialLockId()))
                .get()
                .extracting(MaterialLock::getChainLockId)
                .isNotNull();

        linkConsume(batchId).andExpect(status().isOk());
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void refusesWhenTheChainLocksWereNeverLinked() throws Exception {
        // 2단계를 건너뛴 경우다. 그 값 없이 소각을 기록하면 어느 온체인 잠금이 소각됐는지
        // 확인할 방법이 없다.
        Long batchId = confirmedBatch();
        linkChainBatch(batchId);

        linkConsume(batchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CHAIN_LOCKS_NOT_LINKED"));
    }

    @Test
    void refusesToReportTwice() throws Exception {
        // 두 번 기록되면 같은 소재를 두 번 소각한 것처럼 보인다.
        Long batchId = readyForConsume();
        linkConsume(batchId).andExpect(status().isOk());

        linkConsume(batchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_CONSUME_ALREADY_RECORDED"));
    }

    @Test
    void refusesABatchThatIsNotConfirmedYet() throws Exception {
        // 소비 자체가 아직 일어나지 않았다. 소각할 잠금이 CONSUMED 도 아니다.
        Long batchId = openedBatchWithInput();

        linkConsume(batchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_CONFIRMED"));
    }

    @Test
    void refusesAnotherProducersBatch() throws Exception {
        Long batchId = readyForConsume();
        User otherBrand = userRepository.save(User.register(
                "consume-intruder@example.com", "{noop}x", "침입자", "010-4444-4444",
                UserRole.BRAND));
        walletRepository.save(UserWallet.link(otherBrand.getId(), otherWallet(), CHAIN_ID));

        mockMvc.perform(post(PATH + "/{id}/chain/consume", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void refusesAMalformedTransactionHash() throws Exception {
        Long batchId = readyForConsume();
        Map<String, Object> malformed = request();
        malformed.put("txHash", "0xnothex");

        mockMvc.perform(post(PATH + "/{id}/chain/consume", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(malformed)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(PATH + "/{id}/chain/consume", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leavesNothingBehindWhenTheReportIsRejected() throws Exception {
        Long batchId = confirmedBatch();
        linkChainBatch(batchId);

        linkConsume(batchId).andExpect(status().isConflict());

        assertThat(transactionRepository.findAll())
                .filteredOn(tx -> "confirmProduction".equals(tx.getFunctionName()))
                .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions linkConsume(Long batchId) throws Exception {
        return mockMvc.perform(post(PATH + "/{id}/chain/consume", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request())));
    }

    private Map<String, Object> request() {
        Map<String, Object> request = new HashMap<>();
        request.put("txHash", TX_HASH);
        request.put("blockNumber", 1_234L);
        return request;
    }

    /** 확정 → createBatch 연결 → submitPlanAndLock 보고까지 마친 배치. */
    private Long readyForConsume() throws Exception {
        Long batchId = confirmedBatch();
        linkChainBatch(batchId);
        linkPlan(batchId);
        return batchId;
    }

    private void linkChainBatch(Long batchId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chainBatchId", ++chainBatchSequence);
        request.put("txHash", "0x" + "e".repeat(64));

        mockMvc.perform(post(PATH + "/{id}/chain/batch", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk());
    }

    /** 2단계. 여기서 온체인 잠금 번호가 DB 에 붙는다. */
    private void linkPlan(Long batchId) throws Exception {
        String manifest = mockMvc.perform(get(PATH + "/{id}/chain/manifest", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Map<String, Object>> locked = new ArrayList<>();
        for (ProductionInput input : inputRepository.findAllByProductionBatchId(batchId)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("productionInputId", input.getId());
            entry.put("chainLockId", ++chainLockSequence);
            locked.add(entry);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("txHash", "0x" + "d".repeat(64));
        request.put("evidenceHash",
                objectMapper.readTree(manifest).path("data").path("evidenceHash").asText());
        request.put("lockedMaterials", locked);

        mockMvc.perform(post(PATH + "/{id}/chain", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk());
    }

    private Long confirmedBatch() throws Exception {
        Long batchId = openedBatchWithInput();
        mockMvc.perform(post(PATH + "/{id}/confirm", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());
        return batchId;
    }

    private Long openedBatchWithInput() throws Exception {
        Long batchId = openedBatch();
        Map<String, Object> input = new HashMap<>();
        input.put("materialId", materialId);
        input.put("inputQuantity", 30_000L);
        input.put("productUsedQuantity", 30_000L);
        input.put("reusableScrapQuantity", 0L);
        input.put("wasteQuantity", 0L);

        mockMvc.perform(post(PATH + "/{id}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inputs", List.of(input)))))
                .andExpect(status().isOk());
        return batchId;
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

    /**
     * 온체인 발행까지 마친 소재. {@code submitPlanAndLock} 이 {@code tokenIds[]} 를 인자로
     * 받으므로, 발행되지 않은 소재는 manifest 단계에서 거절된다.
     */
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
        material.linkOnChain(++tokenSequence, CHAIN_ID, MATERIAL_TOKEN_ADDRESS);
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
