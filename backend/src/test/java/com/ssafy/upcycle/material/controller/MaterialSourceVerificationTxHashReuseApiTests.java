package com.ssafy.upcycle.material.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * {@code verifySource} 확정에서 <b>같은 txHash 가 서로 다른 소재에 재사용되는 경우</b>를 고정한다.
 *
 * <p>{@code MaterialSourceVerificationApiTests} 는 "같은 소재를 두 번 확정" 만 막는 것을
 * 검증한다({@code refusesToVerifyTheSameMaterialTwice}). 이 클래스는 그 옆 칸 —
 * <b>다른 소재를 같은 txHash 로</b> 확정하는 경우를 본다. 새 동작을 추가하지 않는다.
 * {@code MaterialSourceVerificationService.confirm}·{@code ChainTransactionRecorder.record}
 * 어디에도 txHash 가 이미 다른 소재에 쓰였는지 대조하는 코드가 없고,
 * {@code blockchain_transaction} 의 유일한 유니크 제약(schema.sql
 * {@code uk_blockchain_transaction_event}, {@code (chain_id, tx_hash, log_index)})은
 * 이 경로가 {@code log_index} 를 항상 NULL 로 남기기 때문에(MySQL 은 NULL 을 서로 다른 값으로
 * 취급) 걸리지 않는다 — 그래서 지금 동작을 실행으로 확인해 남긴다. 의도된 방지 로직이
 * 아니라 <b>현재 비어 있는 자리</b>이므로, 나중에 막기로 결정하면 이 테스트가 먼저 깨져야
 * 정상이다(회귀가 아니라 의도된 동작 변경으로 읽는다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.blockchain.chain-id=31221",
        "app.blockchain.material-token-address=0xbe98f5a9aa8d0480b09c689e38806accd41a0f66",
        "app.blockchain.marketplace-address=0x53fc91be37720fcb051004b7aab73639ca266853",
})
class MaterialSourceVerificationTxHashReuseApiTests {

    private static final String MINT_TX_1 = "0x" + "11".repeat(32);
    private static final String MINT_TX_2 = "0x" + "22".repeat(32);
    private static final String SHARED_VERIFY_TX = "0x" + "cd".repeat(32);
    private static final String SUPPLIER_WALLET = "0xaaaa000000000000000000000000000000000011";

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
        clean();
        User supplierUser = userRepository.save(User.register(
                "source-verify-reuse-supplier@test", "{noop}x", "공급자", "010-0000-0003", UserRole.SUPPLIER));
        supplierId = supplierUser.getId();
        walletRepository.save(UserWallet.link(supplierId, SUPPLIER_WALLET, 31221L));
        supplier = bearer(supplierUser);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    /**
     * 같은 {@code txHash} 를 서로 다른 두 소재의 확정에 그대로 다시 보내면 <b>지금은</b>
     * 둘 다 200 으로 끝나고 {@code blockchain_transaction} 에 행이 두 개 남는다.
     * 서비스도 DB 제약도 이 재사용을 막지 않는다는 사실을 실행으로 고정한다.
     */
    @Test
    void currentlyAllowsTheSameTxHashAcrossDifferentMaterials() throws Exception {
        long firstMaterialId = registerAndMintMaterial(MINT_TX_1, 7L);
        long secondMaterialId = registerAndMintMaterial(MINT_TX_2, 8L);

        confirmSourceVerification(firstMaterialId, SHARED_VERIFY_TX)
                .andExpect(status().isOk());
        confirmSourceVerification(secondMaterialId, SHARED_VERIFY_TX)
                .andExpect(status().isOk());

        var verifySourceRows = transactionRepository.findAll().stream()
                .filter(tx -> tx.getFunctionName().equals("verifySource"))
                .toList();
        assertThat(verifySourceRows).hasSize(2);
        assertThat(verifySourceRows)
                .allSatisfy(tx -> assertThat(tx.getTxHash()).isEqualTo(SHARED_VERIFY_TX));
        assertThat(verifySourceRows.stream().map(tx -> tx.getReferenceId()).distinct().toList())
                .containsExactlyInAnyOrder(firstMaterialId, secondMaterialId);
    }

    // ── helpers ─────────────────────────────────────────────

    private org.springframework.test.web.servlet.ResultActions confirmSourceVerification(
            long materialId, String txHash) throws Exception {
        return mockMvc.perform(post("/api/materials/{id}/source-verification", materialId)
                .header(HttpHeaders.AUTHORIZATION, supplier)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(txHash, null)));
    }

    private long registerAndMintMaterial(String mintTx, long tokenId) throws Exception {
        long materialId = registerMaterial();
        mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                        .header(HttpHeaders.AUTHORIZATION, supplier)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chainLinkBody(tokenId, mintTx)))
                .andExpect(status().isOk());
        return materialId;
    }

    private long registerMaterial() {
        return registrationService.register(supplierId, new MaterialRegisterRequest(
                MaterialType.DENIM, MaterialRootOrigin.WASTE, "출처 검증 재사용 테스트", null, "A",
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
