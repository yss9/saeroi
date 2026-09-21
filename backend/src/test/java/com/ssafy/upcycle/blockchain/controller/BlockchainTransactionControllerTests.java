package com.ssafy.upcycle.blockchain.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.blockchain.dto.BlockchainTransactionResponse;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.service.BlockchainTransactionQueryService;
import com.ssafy.upcycle.common.exception.GlobalExceptionHandler;
import com.ssafy.upcycle.common.security.config.SecurityConfig;
import com.ssafy.upcycle.common.security.handler.RestAccessDeniedHandler;
import com.ssafy.upcycle.common.security.handler.RestAuthenticationEntryPoint;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/blockchain/transactions} 의 HTTP 표면.
 *
 * <p>판정 로직 자체는 {@code BlockchainTransactionQueryServiceTests} 가 이미 확인한다.
 * 여기서 고정하는 것은 <b>인증이 필요하다</b>는 것과, 쿼리 파라미터가 컨트롤러에
 * 실제로 바인딩되어 {@code ApiResponse} 봉투로 나가는가다 — {@code MaterialControllerTests}
 * 와 같은 관례.
 */
@WebMvcTest(BlockchainTransactionController.class)
@Import({
        SecurityConfig.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class
})
class BlockchainTransactionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockchainTransactionQueryService transactionQueryService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

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
        when(transactionQueryService.findAll(BlockchainReferenceType.MATERIAL, 999L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/blockchain/transactions")
                        .with(jwt())
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void returnsTheRecordedTransactionFields() throws Exception {
        when(transactionQueryService.findAll(BlockchainReferenceType.MATERIAL, 55L))
                .thenReturn(List.of(new BlockchainTransactionResponse(
                        "linkMaterialToken",
                        "0x" + "c".repeat(64),
                        100L,
                        "0x" + "b".repeat(40),
                        BlockchainTransactionStatus.SUCCESS,
                        LocalDateTime.of(2026, 9, 21, 12, 0))));

        mockMvc.perform(get("/api/blockchain/transactions")
                        .with(jwt())
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
                        .with(jwt())
                        .param("referenceType", "MATERIAL")
                        .param("referenceId", "0"))
                .andExpect(status().isBadRequest());
    }
}
