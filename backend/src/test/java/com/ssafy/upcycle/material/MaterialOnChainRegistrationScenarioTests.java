package com.ssafy.upcycle.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.blockchain.ScriptedReceiptClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.material.dto.MaterialRegisterRequest;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialRegistrationService;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 소재 등록에서 on-chain 발행까지 이어지는 한 번의 시나리오 (S15P21E104-440).
 *
 * <p>{@code ChainLinkApiTests}(mint 확정)와 {@code MaterialSourceVerificationApiTests}
 * (verifySource 확정)가 각 계층을 낱개로 이미 고정하고 있다. 이 클래스는 그 둘을
 * **한 서사**로 이어 붙여, 티켓이 요구한 완료 조건 — "소재 등록 → mint 확정 →
 * verifySource 확정" 한 흐름이 끝났을 때 상세 조회 하나가 tokenId·chainId·
 * contractAddress·verificationState·verification 을 전부 일관되게 보여주는지 —
 * 를 증명한다. 서비스·컨트롤러를 재구현하지 않고 기존 엔드포인트만 순서대로 호출한다.
 *
 * <p>커버하지 않는 것: evidence_hash·manifest_url 은 증빙 등록 경로가 없어 여전히
 * {@code null} 이다 (매니페스트 생성 경로 부재 — 이 티켓의 범위 밖,
 * {@code docs/superpowers/specs/2026-09-07-material-verification-state.md} 2026-09-09
 * 추가 절 참고).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.blockchain.chain-id=31221",
        "app.blockchain.material-token-address=0xbe98f5a9aa8d0480b09c689e38806accd41a0f66",
        "app.blockchain.marketplace-address=0x53fc91be37720fcb051004b7aab73639ca266853",
})
class MaterialOnChainRegistrationScenarioTests {

    private static final String MINT_TX = "0x" + "ab".repeat(32);
    private static final String VERIFY_TX = "0x" + "cd".repeat(32);
    private static final String SUPPLIER_WALLET = "0xaaaa000000000000000000000000000000000011";
    private static final long ON_CHAIN_TOKEN_ID = 42L;
    private static final long MINT_BLOCK_NUMBER = 12345L;
    private static final long CHAIN_ID = 31221L;
    private static final String CONTRACT_ADDRESS = "0xbe98f5a9aa8d0480b09c689e38806accd41a0f66";
    @Autowired private ScriptedReceiptClient chain;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private MaterialRegistrationService registrationService;

    @Autowired private UserRepository userRepository;
    @Autowired private UserWalletRepository walletRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialBalanceRepository balanceRepository;
    @Autowired private MaterialLockRepository lockRepository;
    @Autowired private MaterialListingRepository listingRepository;
    @Autowired private BlockchainTransactionRepository transactionRepository;

    private Long supplierId;
    private String supplier;

    @BeforeEach
    void setUp() {
        chain.expect("0xbe98f5a9aa8d0480b09c689e38806accd41a0f66", "0xaaaa000000000000000000000000000000000011");
        clean();
        User supplierUser = userRepository.save(User.register(
                "onchain-scenario-supplier@test", "{noop}x", "공급자", "010-0000-0001",
                UserRole.SUPPLIER));
        supplierId = supplierUser.getId();
        walletRepository.save(UserWallet.link(supplierId, SUPPLIER_WALLET, CHAIN_ID));
        supplier = bearer(supplierUser);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    /**
     * 티켓 완료 조건 그대로: 소재 등록 → mint 확정 → verifySource 확정 → 상세 조회 하나가
     * tokenId/chainId/contractAddress 와 SOURCE_VERIFIED + evidence 를 함께 보여준다.
     */
    @Test
    void registrationMintAndSourceVerificationLinkIntoOneConsistentDetailView() throws Exception {
        // 1. 등록 — 아직 온체인에 없다.
        long materialId = registrationService.register(supplierId, new MaterialRegisterRequest(
                        MaterialType.DENIM, MaterialRootOrigin.WASTE, "온체인 등록 시나리오", null,
                        "A", 30L, MaterialQuantityUnit.KG, null, null, null, null, List.of()))
                .materialId();

        mockMvc.perform(get("/api/materials/{id}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenId").doesNotExist())
                .andExpect(jsonPath("$.data.verificationState").value("REGISTERED"))
                .andExpect(jsonPath("$.data.verification").doesNotExist());

        // 2. mint 확정 — 사용자 지갑이 mintMaterial 을 이미 보냈고, 서버는 결과만 받아 적는다.
        mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chainLinkBody(ON_CHAIN_TOKEN_ID, MINT_TX, MINT_BLOCK_NUMBER)))
                .andExpect(status().isOk());

        var mintedMaterial = materialRepository.findById(materialId).orElseThrow();
        assertThat(mintedMaterial.getTokenId()).isEqualTo(ON_CHAIN_TOKEN_ID);
        assertThat(mintedMaterial.getChainId()).isEqualTo(CHAIN_ID);
        assertThat(mintedMaterial.getContractAddress()).isEqualTo(CONTRACT_ADDRESS);

        var mintTransaction = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFunctionName().equals("mintMaterial"))
                .findFirst().orElseThrow();
        assertThat(mintTransaction.getFromWallet()).isEqualTo(SUPPLIER_WALLET);
        assertThat(mintTransaction.getTxHash()).isEqualTo(MINT_TX);

        // mint 만 끝난 상태 — 아직 REGISTERED 다. verifySource 는 별개의 독립 서명이다.
        mockMvc.perform(get("/api/materials/{id}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier))
                .andExpect(jsonPath("$.data.tokenId").value(ON_CHAIN_TOKEN_ID))
                .andExpect(jsonPath("$.data.chainId").value(CHAIN_ID))
                .andExpect(jsonPath("$.data.contractAddress").value(CONTRACT_ADDRESS))
                .andExpect(jsonPath("$.data.verificationState").value("REGISTERED"))
                .andExpect(jsonPath("$.data.verification").doesNotExist());

        // 3. verifySource 확정 — 등록자 본인이 출처를 서명한다.
        mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sourceVerificationBody(VERIFY_TX)))
                .andExpect(status().isOk());

        var verifySourceTransaction = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFunctionName().equals("verifySource"))
                .findFirst().orElseThrow();
        assertThat(verifySourceTransaction.getFromWallet()).isEqualTo(SUPPLIER_WALLET);
        assertThat(verifySourceTransaction.getTxHash()).isEqualTo(VERIFY_TX);

        // 4. 한 번의 상세 조회로 등록 → mint → verifySource 전체가 일관되게 읽힌다.
        mockMvc.perform(get("/api/materials/{id}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenId").value(ON_CHAIN_TOKEN_ID))
                .andExpect(jsonPath("$.data.chainId").value(CHAIN_ID))
                .andExpect(jsonPath("$.data.contractAddress").value(CONTRACT_ADDRESS))
                .andExpect(jsonPath("$.data.verificationState").value("SOURCE_VERIFIED"))
                .andExpect(jsonPath("$.data.verification.state").value("SOURCE_VERIFIED"))
                .andExpect(jsonPath("$.data.verification.signerWallet").value(SUPPLIER_WALLET))
                .andExpect(jsonPath("$.data.verification.txHash").value(VERIFY_TX))
                .andExpect(jsonPath("$.data.verification.signedAt").exists())
                // evidence_hash/manifest_url 은 매니페스트 생성 경로가 없어 여전히 null —
                // 이 티켓의 범위 밖이다 (스펙 문서 2026-09-09 추가 절).
                .andExpect(jsonPath("$.data.evidenceHash").doesNotExist())
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist());
    }

    // ── helpers ─────────────────────────────────────────────

    private String chainLinkBody(Long onChainId, String txHash, Long blockNumber) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("onChainId", onChainId);
        payload.put("txHash", txHash);
        payload.put("blockNumber", blockNumber);
        return objectMapper.writeValueAsString(payload);
    }

    private String sourceVerificationBody(String txHash) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("txHash", txHash);
        payload.put("blockNumber", null);
        return objectMapper.writeValueAsString(payload);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private void clean() {
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        transactionRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
