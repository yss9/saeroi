package com.ssafy.upcycle.blockchain.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/blockchain/transactions} 의 HTTP 표면.
 *
 * <p>판정 로직 자체는 {@code BlockchainTransactionQueryServiceTests} 가 이미 확인한다.
 * 여기서 고정하는 것은 <b>인증이 필요하다</b>는 것과, 쿼리 파라미터가 컨트롤러에
 * 실제로 바인딩되어 {@code ApiResponse} 봉투로 나가는가다 — {@code MaterialControllerTests}
 * 와 같은 관례.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BlockchainTransactionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() {
        clean();
        User brand = userRepository.save(
                User.register("blockchain-tx-viewer@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        token = jwtTokenService
                .issueAccessToken(String.valueOf(brand.getId()), List.of(brand.getRole().name()))
                .value();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void requiresAuthentication() throws Exception {
        // SecurityConfig 의 permitAll 목록에 이 경로가 들어가면 이 테스트가 깨진다.
        mockMvc.perform(get("/api/blockchain/transactions")
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAnEmptyListWhenNothingIsRecorded() throws Exception {
        mockMvc.perform(get("/api/blockchain/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void returnsTheRecordedTransactionFields() throws Exception {
        insert("MATERIAL", 55L, "linkMaterialToken");

        mockMvc.perform(get("/api/blockchain/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", "55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].functionName").value("linkMaterialToken"))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].txHash").exists())
                .andExpect(jsonPath("$.data[0].blockNumber").value(100));
    }

    @Test
    void rejectsANonPositiveReferenceId() throws Exception {
        mockMvc.perform(get("/api/blockchain/transactions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", "0"))
                .andExpect(status().isBadRequest());
    }

    private void insert(String referenceType, long referenceId, String functionName) {
        jdbc.update(
                """
                INSERT INTO blockchain_transaction (
                    chain_id, contract_address, tx_hash, reference_type, reference_id,
                    function_name, from_wallet, status, block_number, log_index, created_at,
                    confirmed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                """,
                31221L,
                "0x" + "a".repeat(40),
                "0x" + "c".repeat(64),
                referenceType,
                referenceId,
                functionName,
                "0x" + "b".repeat(40),
                "SUCCESS",
                100L,
                0);
    }

    private void clean() {
        jdbc.update("DELETE FROM blockchain_transaction");
        userRepository.deleteAll();
    }
}
