package com.ssafy.upcycle.material.controller;

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
 * 소재 출처 검증(verifySource) 확정 경로.
 *
 * <p>고정하는 계약이 넷이다 — <b>등록자 본인만</b>, <b>온체인에 없는 소재는 거절</b>,
 * <b>두 번 확정할 수 없다</b>, <b>확정되면 상세 조회의 {@code verificationState} 가
 * {@code SOURCE_VERIFIED} 로 바뀌고 근거가 함께 내려온다</b>.
 *
 * <p>세 번째·네 번째가 이 기능의 핵심이다. 이 서비스는 {@code material} 을 바꾸지 않고
 * {@code blockchain_transaction} 에 행 하나만 남긴다 — {@code MaterialVerificationStateResolver}
 * 가 조회 시점에 그 행을 보고 판정한다({@code docs/superpowers/specs/2026-09-07-material-verification-state.md}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.blockchain.chain-id=31221",
        "app.blockchain.material-token-address=0xbe98f5a9aa8d0480b09c689e38806accd41a0f66",
        "app.blockchain.marketplace-address=0x53fc91be37720fcb051004b7aab73639ca266853",
})
class MaterialSourceVerificationApiTests {

    private static final String MINT_TX = "0x" + "ab".repeat(32);
    private static final String VERIFY_TX = "0x" + "cd".repeat(32);
    private static final String OTHER_VERIFY_TX = "0x" + "ef".repeat(32);
    private static final String SUPPLIER_WALLET = "0xaaaa000000000000000000000000000000000011";
    private static final String OTHER_WALLET = "0xbbbb000000000000000000000000000000000022";
    private static final String MATERIAL_TOKEN = "0xbe98f5a9aa8d0480b09c689e38806accd41a0f66";
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
    private String other;

    @BeforeEach
    void setUp() {
        chain.expect("0xbe98f5a9aa8d0480b09c689e38806accd41a0f66", "0xaaaa000000000000000000000000000000000011");
        clean();
        User supplierUser = userRepository.save(User.register(
                "source-verify-supplier@test", "{noop}x", "공급자", "010-0000-0001", UserRole.SUPPLIER));
        supplierId = supplierUser.getId();
        walletRepository.save(UserWallet.link(supplierId, SUPPLIER_WALLET, 31221L));
        supplier = bearer(supplierUser);

        User stranger = userRepository.save(User.register(
                "source-verify-other@test", "{noop}x", "남", "010-0000-0002", UserRole.SUPPLIER));
        walletRepository.save(UserWallet.link(stranger.getId(), OTHER_WALLET, 31221L));
        other = bearer(stranger);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void confirmsSourceVerificationForTheMintedMaterial() throws Exception {
        // 요청이 보낸 999 와 다른 값을 체인이 답하게 해서, 기록이 어느 쪽을 따르는지 본다.
        chain.expect(MATERIAL_TOKEN, SUPPLIER_WALLET, 8_800_002L);
        long materialId = registerAndMintMaterial();

        mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VERIFY_TX, 999L)))
                .andExpect(status().isOk());

        var recorded = verifySourceTransactions();
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getFromWallet()).isEqualTo(SUPPLIER_WALLET);
        assertThat(recorded.get(0).getTxHash()).isEqualTo(VERIFY_TX);
        assertThat(recorded.get(0).getBlockNumber())
                .as("블록번호는 요청(999)이 아니라 영수증에서 온다")
                .isEqualTo(8_800_002L);
    }

    @Test
    void refusesAConfirmFromSomeoneElse() throws Exception {
        long materialId = registerAndMintMaterial();

        mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                        .header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VERIFY_TX, null)))
                .andExpect(status().isForbidden());

        // mint 확정 자체가 이미 mintMaterial 행 하나를 남겼다 — verifySource 행만 본다.
        assertThat(verifySourceTransactions()).isEmpty();
    }

    /** {@code verifySource(tokenId, ...)} 자체가 tokenId 를 요구하므로 mint 가 먼저다. */
    @Test
    void refusesAMaterialThatIsNotOnChainYet() throws Exception {
        long materialId = registerMaterial();

        mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(VERIFY_TX, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_ON_CHAIN"));
    }

    /** 두 번째 확정이 조용히 새 행을 쌓으면 "언제 검증됐는가" 의 근거가 흔들린다. */
    @Test
    void refusesToVerifyTheSameMaterialTwice() throws Exception {
        long materialId = registerAndMintMaterial();
        confirmSourceVerification(materialId, VERIFY_TX).andExpect(status().isOk());

        confirmSourceVerification(materialId, OTHER_VERIFY_TX)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_SOURCE_ALREADY_VERIFIED"));

        assertThat(verifySourceTransactions()).hasSize(1);
    }

    @Test
    void rejectsAMalformedTransactionHash() throws Exception {
        long materialId = registerAndMintMaterial();

        mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-a-hash", null)))
                .andExpect(status().isBadRequest());
    }

    // ── 상세 조회와의 통합 ───────────────────────────────────

    @Test
    void detailStaysRegisteredBeforeConfirmation() throws Exception {
        long materialId = registerAndMintMaterial();

        mockMvc.perform(get("/api/materials/{id}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier))
                .andExpect(jsonPath("$.data.verificationState").value("REGISTERED"))
                .andExpect(jsonPath("$.data.verification").doesNotExist());
    }

    @Test
    void detailShowsSourceVerifiedWithEvidenceAfterConfirmation() throws Exception {
        long materialId = registerAndMintMaterial();
        confirmSourceVerification(materialId, VERIFY_TX).andExpect(status().isOk());

        mockMvc.perform(get("/api/materials/{id}", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier))
                .andExpect(jsonPath("$.data.verificationState").value("SOURCE_VERIFIED"))
                .andExpect(jsonPath("$.data.verification.state").value("SOURCE_VERIFIED"))
                .andExpect(jsonPath("$.data.verification.signerWallet").value(SUPPLIER_WALLET))
                .andExpect(jsonPath("$.data.verification.txHash").value(VERIFY_TX))
                .andExpect(jsonPath("$.data.verification.signedAt").exists());
    }

    // ── helpers ─────────────────────────────────────────────

    /** mint 확정이 이미 남긴 mintMaterial 행과 섞이지 않게 verifySource 만 본다. */
    private List<com.ssafy.upcycle.blockchain.entity.BlockchainTransaction> verifySourceTransactions() {
        return transactionRepository.findAll().stream()
                .filter(tx -> tx.getFunctionName().equals("verifySource"))
                .toList();
    }

    private org.springframework.test.web.servlet.ResultActions confirmSourceVerification(
            long materialId, String txHash) throws Exception {
        return mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                .header(HttpHeaders.AUTHORIZATION, supplier)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(txHash, null)));
    }

    private long registerAndMintMaterial() throws Exception {
        long materialId = registerMaterial();
        mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chainLinkBody(7L, MINT_TX)))
                .andExpect(status().isOk());
        return materialId;
    }

    private long registerMaterial() {
        return registrationService.register(supplierId, new MaterialRegisterRequest(
                MaterialType.DENIM, MaterialRootOrigin.WASTE, "출처 검증용", null, "A",
                30L, MaterialQuantityUnit.KG, null, null, null, null, List.of())).materialId();
    }

    private String chainLinkBody(Long onChainId, String txHash) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("onChainId", onChainId);
        payload.put("txHash", txHash);
        payload.put("blockNumber", null);
        return objectMapper.writeValueAsString(payload);
    }

    private String body(String txHash, Long blockNumber) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("txHash", txHash);
        payload.put("blockNumber", blockNumber);
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
