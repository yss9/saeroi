package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.blockchain.ScriptedReceiptClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
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
 * 생산 배치의 온체인 기록.
 *
 * <p>소재 발행·판매·거래는 이미 온체인에 남는데 <b>생산만 오프체인 DB 에만 있었다.</b>
 * 그러면 QR Passport 가 증명하는 범위가 소재와 거래에서 멈추고, "이 제품이 그 소재로
 * 만들어졌다" 는 마지막 한 칸은 서버 말을 믿어야 한다.
 *
 * <p>컨트랙트 주소를 {@code @TestPropertySource} 로 넣는다. 실제 배포분과 같을 필요는 없다 —
 * 여기서 보는 것은 <b>기록이 어느 주소로 남는가</b>이지 체인과의 통신이 아니다.
 *
 * <p><b>속성 목록이 {@code ChainLinkApiTests} 와 정확히 같아야 한다.</b> 그 목록이 스프링
 * 컨텍스트 캐시의 키라, 하나라도 다르면 컨텍스트가 새로 만들어진다. 캐시가 넘치면 오래된
 * 컨텍스트가 닫히고 {@code create-drop} 이 <b>공유 H2 의 스키마를 지운다</b> — 그러면
 * 관계없는 테스트가 {@code Table not found} 로 깨진다. 실제로 그 증상이 났다.
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
class ProductionChainLinkTests {

    static final String REGISTRY_ADDRESS = "0x65b4d0d9f9f0e0a4b0b0f9e8d7c6b5a4d3c2b1a0";

    private static final String PATH = "/api/v1/production-batches";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final String MATERIAL_TOKEN_ADDRESS =
            "0xbe98f5a9aa8d0480b09c689e38806accd41a0f66";
    private static final long HELD_QUANTITY = 100_000L;
    private static final String TX_HASH =
            "0x" + "a".repeat(64);
    private static final String EVIDENCE_HASH =
            "0x" + "b".repeat(64);

    @Autowired private ScriptedReceiptClient chain;

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
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProductionInputRepository inputRepository;

    @Autowired
    private BlockchainTransactionRepository transactionRepository;

    private long tokenSequence = 1_000L;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        chain.expect("0x65b4d0d9f9f0e0a4b0b0f9e8d7c6b5a4d3c2b1a0", "0x1234567890123456789012345678901234567890");
        brand = userRepository.save(User.register(
                "chain-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial();
    }

    // ── 기록 ────────────────────────────────────────────────

    @Test
    void recordsTheTransactionAgainstTheConfiguredRegistry() throws Exception {
        Long batchId = confirmedBatch();

        link(batchId, request(batchId)).andExpect(status().isOk());

        // createBatch 기록도 같은 참조 타입이라 함수 이름으로 걸러야 한다.
        assertThat(transactionRepository.findAll())
                .filteredOn(tx -> tx.getReferenceType() == BlockchainReferenceType.PRODUCTION
                        && "submitPlanAndLock".equals(tx.getFunctionName()))
                .singleElement()
                .satisfies(tx -> {
                    assertThat(tx.getReferenceId()).isEqualTo(batchId);
                    // 컨트랙트에 실제로 있는 함수여야 한다. 없는 이름으로 기록하면 실제
                    // 트랜잭션과 대조할 때 전부 불일치가 된다.
                    assertThat(tx.getFunctionName()).isEqualTo("submitPlanAndLock");
                    // 주소는 서버 설정에서 온다. 화면이 보낸 값을 쓰면 자기가 배포한
                    // 컨트랙트를 가리키는 기록을 만들 수 있다.
                    assertThat(tx.getContractAddress()).isEqualTo(REGISTRY_ADDRESS);
                    assertThat(tx.getChainId()).isEqualTo(CHAIN_ID);
                    // 서명자 주소도 서버가 찾는다.
                    assertThat(tx.getFromWallet()).isEqualTo(WALLET);
                    assertThat(tx.getTxHash()).isEqualTo(TX_HASH);
                });
    }

    @Test
    void theEvidenceHashLandsOnTheBatch() throws Exception {
        // 해시가 붙어야 나중에 체인의 기록과 대조할 수 있다.
        Long batchId = confirmedBatch();

        link(batchId, request(batchId)).andExpect(status().isOk());

        assertThat(batchRepository.findById(batchId))
                .get()
                .satisfies(batch -> {
                    assertThat(batch.getEvidenceHash()).matches("^0x[0-9a-f]{64}$");
                    assertThat(batch.getManifestUrl()).isEqualTo("https://cdn.example.com/m.json");
                });
    }

    @Test
    void theBatchResponseTellsWhetherItIsOnChain() throws Exception {
        // 화면이 "온체인 기록" 버튼을 언제 보여줄지 이 값으로 정한다.
        Long batchId = confirmedBatch();

        mockMvc.perform(get(PATH + "/{id}", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evidenceHash").doesNotExist());

        String expectedHash = manifestHash(batchId);
        link(batchId, request(batchId)).andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/{id}", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evidenceHash").value(expectedHash));
    }

    @Test
    void manifestUrlIsOptional() throws Exception {
        Long batchId = confirmedBatch();
        Map<String, Object> request = request(batchId);
        request.remove("manifestUrl");

        link(batchId, request).andExpect(status().isOk());

        assertThat(batchRepository.findById(batchId))
                .get()
                .satisfies(batch -> assertThat(batch.getManifestUrl()).isNull());
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void refusesABatchThatIsNotConfirmedYet() throws Exception {
        // 투입이 더 바뀔 수 있다. 바뀐 뒤에도 체인에는 옛 내용의 해시가 남는다.
        // manifest 도 확정된 배치만 만들 수 있어, 여기서는 상수 해시로 두드린다.
        Long batchId = openedBatchWithInput();
        Map<String, Object> request = new HashMap<>();
        request.put("txHash", TX_HASH);
        request.put("evidenceHash", EVIDENCE_HASH);
        request.put("lockedMaterials", lockedMaterials(batchId));

        link(batchId, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_CONFIRMED"));
    }

    @Test
    void refusesToRecordTheSameBatchTwice() throws Exception {
        // 두 번 기록되면 어느 해시가 정본인지 알 수 없다.
        Long batchId = confirmedBatch();
        link(batchId, request(batchId)).andExpect(status().isOk());

        link(batchId, request(batchId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_ALREADY_ON_CHAIN"));
    }

    @Test
    void refusesAnotherProducersBatch() throws Exception {
        Long batchId = confirmedBatch();
        User otherBrand = userRepository.save(User.register(
                "chain-intruder@example.com", "{noop}x", "침입자", "010-4444-4444", UserRole.BRAND));
        walletRepository.save(UserWallet.link(otherBrand.getId(), otherWallet(), CHAIN_ID));

        mockMvc.perform(post(PATH + "/{id}/chain", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request(batchId)))) 
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void refusesAMalformedTransactionHash() throws Exception {
        Long batchId = confirmedBatch();
        Map<String, Object> request = request(batchId);
        request.put("txHash", "0xnothex");

        link(batchId, request).andExpect(status().isBadRequest());
    }

    @Test
    void refusesAMalformedEvidenceHash() throws Exception {
        Long batchId = confirmedBatch();
        Map<String, Object> request = request(batchId);
        request.put("evidenceHash", "not-a-hash");

        link(batchId, request).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        Map<String, Object> anonymous = new HashMap<>();
        anonymous.put("txHash", TX_HASH);
        anonymous.put("evidenceHash", EVIDENCE_HASH);

        mockMvc.perform(post(PATH + "/{id}/chain", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(anonymous)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leavesNothingBehindWhenTheHashIsRejected() throws Exception {
        // 거절될 요청에 배치만 먼저 바꿔 두면 "해시는 붙었는데 기록은 없는" 순간이 생긴다.
        Long batchId = confirmedBatch();
        Map<String, Object> request = request(batchId);
        request.put("txHash", "0xnothex");

        link(batchId, request).andExpect(status().isBadRequest());

        assertThat(batchRepository.findById(batchId))
                .get()
                .satisfies(batch -> assertThat(batch.getEvidenceHash()).isNull());
        assertThat(transactionRepository.findAll())
                .filteredOn(tx -> "submitPlanAndLock".equals(tx.getFunctionName()))
                .isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions link(Long batchId, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(PATH + "/{id}/chain", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    /**
     * 요청은 <b>서버가 준 해시</b>를 그대로 싣는다. 화면이 직접 계산하지 않는 것이 계약이라
     * 테스트도 같은 경로를 지난다.
     */
    private Map<String, Object> request(Long batchId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("txHash", TX_HASH);
        request.put("blockNumber", 1_234L);
        request.put("evidenceHash", manifestHash(batchId));
        request.put("manifestUrl", "https://cdn.example.com/m.json");
        request.put("lockedMaterials", lockedMaterials(batchId));
        return request;
    }

    /**
     * 투입마다 온체인 잠금 번호 하나. {@code submitPlanAndLock} 이 만든 값을 2단계 보고에서
     * 함께 받는다 — 3단계까지 화면이 들고 오게 하면 새로고침에 잃어버린다.
     */
    private List<Map<String, Object>> lockedMaterials(Long batchId) {
        List<Map<String, Object>> locked = new ArrayList<>();
        long chainLockId = 100L;
        for (ProductionInput input : inputRepository.findAllByProductionBatchId(batchId)) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("productionInputId", input.getId());
            entry.put("chainLockId", chainLockId++);
            locked.add(entry);
        }
        return locked;
    }

    private String manifestHash(Long batchId) throws Exception {
        String response = mockMvc.perform(get(PATH + "/{id}/chain/manifest", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("evidenceHash").asText();
    }

    /** {@code createBatch} 결과 연결. 이것 없이는 submitPlanAndLock 을 부를 인자가 없다. */
    private void linkChainBatch(Long batchId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("chainBatchId", 7L);
        request.put("txHash", "0x" + "e".repeat(64));
        request.put("blockNumber", 1_000L);

        mockMvc.perform(post(PATH + "/{id}/chain/batch", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk());
    }

    private Long confirmedBatch() throws Exception {
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
        mockMvc.perform(post(PATH + "/{id}/confirm", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());
        linkChainBatch(batchId);
        return batchId;
    }

    /** 투입까지만 넣고 확정하지 않은 배치. 상태 검사에 닿으려면 투입이 있어야 한다. */
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
        // submitPlanAndLock 이 tokenIds[] 를 인자로 받으므로, 온체인 발행이 끝난 소재만
        // manifest 에 담긴다. 발행 단계는 소재 도메인이 따로 검증하므로 여기서는 값만 채운다.
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
