package com.ssafy.upcycle.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 온체인이 정본일 때 <b>보유 행이 언제 생기는가.</b>
 *
 * <p>등록만으로 보유 행이 생기면, 지갑 팝업에서 <b>취소한 소재가 보유 목록에 남는다.</b>
 * 사용자는 "등록 안 했는데 왜 있지" 가 되고, 더 나쁘게는 그 소재를 팔거나 가공에 넣을 수
 * 있다 — 체인에는 없는 수량이 거래되는 것이다.
 *
 * <p>그래서 온체인 설정에서는 등록이 소재 행만 만들고, 보유는 mint 가 확정되어 서버에
 * 기록될 때 생긴다. 오프체인 설정({@code app.material.on-chain=false}, 기본값)은 체인이
 * 아예 없는 환경이라 등록이 보유까지 만든다 — 그쪽 동작은 기존 테스트가 지킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "app.material.on-chain=true",
        "app.blockchain.chain-id=31221",
        "app.blockchain.material-token-address=0xbe98f5a9aa8d0480b09c689e38806accd41a0f66",
        "app.blockchain.marketplace-address=0x53fc91be37720fcb051004b7aab73639ca266853",
})
class MaterialOnChainIssuanceTests {

    private static final String WALLET = "0xaaaa000000000000000000000000000000000011";
    private static final long CHAIN_ID = 31221L;
    private static final String TX_HASH = "0x" + "ab".repeat(32);

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
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    private User supplier;
    private String token;

    @BeforeEach
    void setUp() {
        supplier = userRepository.save(User.register(
                "onchain-supplier@example.com", "{noop}x", "공급자", "010-0000-0000",
                UserRole.SUPPLIER));
        token = tokenFor(supplier);
        walletRepository.save(UserWallet.link(supplier.getId(), WALLET, CHAIN_ID));
    }

    @Test
    void registrationAloneDoesNotGrantOwnership() throws Exception {
        // 이것이 이 수정의 핵심이다. 지갑 컨펌 전에는 보유 목록에 뜨면 안 된다.
        Long materialId = registered();

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(materialId, supplier.getId()))
                .isEmpty();
    }

    @Test
    void theInventoryScreenStaysEmptyUntilTheMintIsConfirmed() throws Exception {
        registered();

        mockMvc.perform(get("/api/materials/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void confirmingTheMintGrantsOwnership() throws Exception {
        Long materialId = registered();

        linkOnChain(materialId, 7L).andExpect(status().isOk());

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(materialId, supplier.getId()))
                .get()
                .satisfies(balance -> {
                    assertThat(balance.getQuantity()).isEqualTo(50_000L);
                    // 서명한 지갑이 보유 주소가 된다.
                    assertThat(balance.getWalletAddress()).isEqualTo(WALLET);
                });
    }

    @Test
    void theInventoryScreenShowsItAfterTheMint() throws Exception {
        Long materialId = registered();
        linkOnChain(materialId, 7L).andExpect(status().isOk());

        mockMvc.perform(get("/api/materials/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].materialId").value(materialId))
                .andExpect(jsonPath("$.data[0].quantity").value(50_000));
    }

    @Test
    void aSecondLinkAttemptDoesNotDuplicateTheHolding() throws Exception {
        // 두 번째 연결은 409 로 막히지만, 보유 행이 둘 생기지 않는 것도 함께 고정한다 —
        // 그러면 잔량 조회가 실제보다 많은 수량을 답한다.
        Long materialId = registered();
        linkOnChain(materialId, 7L).andExpect(status().isOk());

        linkOnChain(materialId, 8L).andExpect(status().isConflict());

        assertThat(balanceRepository.findAll())
                .filteredOn(balance -> balance.getMaterialId().equals(materialId))
                .hasSize(1)
                .first()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(50_000L);
    }

    // ── helpers ─────────────────────────────────────────────

    private Long registered() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("materialType", "DENIM");
        request.put("rootOrigin", "WASTE");
        request.put("name", "폐데님 원단");
        request.put("grade", "A");
        request.put("quantity", 50_000);
        request.put("unit", "G");

        String response = mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("materialId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions linkOnChain(
            Long materialId, long tokenId) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("onChainId", tokenId);
        request.put("txHash", TX_HASH);
        request.put("blockNumber", 1_234L);

        return mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
