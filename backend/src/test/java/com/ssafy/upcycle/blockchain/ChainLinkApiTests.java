package com.ssafy.upcycle.blockchain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.dto.MaterialQuantityUnit;
import com.ssafy.upcycle.material.dto.MaterialRegisterRequest;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialEventRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.service.MaterialRegistrationService;
import com.ssafy.upcycle.trade.dto.ListingRegisterRequest;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.trade.service.ListingCommandService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.List;
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
 * 온체인 결과를 서버에 알리는 경로.
 *
 * <p>서버는 트랜잭션을 보내지 않는다 — 서명은 사용자 지갑이 하고 서버는 <b>확정된 결과만</b>
 * 받아 적는다. 그래서 이 테스트가 검증하는 것은 체인 동작이 아니라 <b>받아 적는 규칙</b>이다:
 * 누가 부를 수 있는가, 두 번 부르면 어떻게 되는가, 설정이 없으면 어떻게 되는가.
 *
 * <p>컨트랙트 주소를 {@code @TestPropertySource} 로 넣는다. 실제 배포분과 같을 필요는 없다 —
 * 서버가 <b>요청 바디가 아니라 설정에서</b> 주소를 꺼낸다는 것이 확인 대상이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.blockchain.chain-id=31221",
        "app.blockchain.material-token-address=0xbe98f5a9aa8d0480b09c689e38806accd41a0f66",
        "app.blockchain.marketplace-address=0x53fc91be37720fcb051004b7aab73639ca266853",
        // 생산 레지스트리 주소를 여기 함께 둔다. 이 목록이 스프링 컨텍스트 캐시의 키라,
        // 생산 온체인 테스트가 다른 목록을 쓰면 컨텍스트가 하나 더 만들어진다. 캐시가 넘치면
        // 오래된 컨텍스트가 닫히면서 create-drop 이 공유 H2 의 스키마를 지우고, 그때
        // 관계없는 테스트가 "Table not found" 로 깨진다.
        "app.blockchain.production-registry-address=0x65b4d0d9f9f0e0a4b0b0f9e8d7c6b5a4d3c2b1a0",
})
class ChainLinkApiTests {

    private static final String TX = "0x" + "ab".repeat(32);
    private static final String OTHER_TX = "0x" + "cd".repeat(32);
    private static final String SELLER_WALLET = "0xaaaa000000000000000000000000000000000011";
    private static final String OTHER_WALLET = "0xbbbb000000000000000000000000000000000022";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private MaterialRegistrationService registrationService;
    @Autowired private ListingCommandService listingCommandService;

    @Autowired private UserRepository userRepository;
    @Autowired private UserWalletRepository walletRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private MaterialBalanceRepository balanceRepository;
    @Autowired private MaterialLockRepository lockRepository;
    @Autowired private MaterialListingRepository listingRepository;
    @Autowired private BlockchainTransactionRepository transactionRepository;
    @Autowired private MaterialEventRepository materialEventRepository;

    private Long sellerId;
    private String seller;
    private String other;

    @BeforeEach
    void setUp() {
        clean();
        User supplier = userRepository.save(User.register(
                "chain-seller@test", "{noop}x", "공급자", "010-0000-0001", UserRole.SUPPLIER));
        sellerId = supplier.getId();
        walletRepository.save(UserWallet.link(sellerId, SELLER_WALLET, 31221L));
        seller = bearer(supplier);

        User stranger = userRepository.save(User.register(
                "chain-other@test", "{noop}x", "남", "010-0000-0002", UserRole.SUPPLIER));
        walletRepository.save(UserWallet.link(stranger.getId(), OTHER_WALLET, 31221L));
        other = bearer(stranger);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void linksTheMintedTokenAndRecordsTheTransaction() throws Exception {
        long materialId = registerMaterial();

        mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                        .header(HttpHeaders.AUTHORIZATION, seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(7L, TX, 12345L)))
                .andExpect(status().isOk());

        var material = materialRepository.findById(materialId).orElseThrow();
        assertThat(material.getTokenId()).isEqualTo(7L);
        assertThat(material.getChainId()).isEqualTo(31221L);
        assertThat(material.getContractAddress())
                .as("컨트랙트 주소는 요청이 아니라 서버 설정에서 온다")
                .isEqualTo("0xbe98f5a9aa8d0480b09c689e38806accd41a0f66");

        var recorded = transactionRepository.findAll();
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getFunctionName()).isEqualTo("mintMaterial");
        assertThat(materialEventRepository.findAll())
                .extracting(event -> event.getEventType())
                .containsExactly(
                        MaterialEventType.MATERIAL_REGISTERED,
                        MaterialEventType.MATERIAL_MINTED);
        assertThat(recorded.get(0).getFromWallet())
                .as("서명자 주소도 서버가 찾는다 — 요청이 보낸 값이 아니다")
                .isEqualTo(SELLER_WALLET);
        assertThat(recorded.get(0).getBlockNumber()).isEqualTo(12345L);
    }

    /** 두 번 연결하면 앞의 토큰이 미아가 된다. 그래서 막는다. */
    @Test
    void refusesToRelinkAMaterial() throws Exception {
        long materialId = registerMaterial();
        link(seller, materialId, 7L, TX).andExpect(status().isOk());

        link(seller, materialId, 8L, OTHER_TX)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BLOCKCHAIN_ALREADY_LINKED"));

        assertThat(materialRepository.findById(materialId).orElseThrow().getTokenId())
                .as("거절된 요청이 앞의 값을 덮어쓰지 않아야 한다")
                .isEqualTo(7L);
    }

    /** 남의 소재에 아무 토큰이나 붙일 수 없다. 한 번 붙으면 되돌릴 수 없어서 더 중요하다. */
    @Test
    void refusesALinkFromSomeoneElse() throws Exception {
        long materialId = registerMaterial();

        link(other, materialId, 7L, TX).andExpect(status().isForbidden());

        assertThat(materialRepository.findById(materialId).orElseThrow().getTokenId()).isNull();
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    /** 형식이 깨진 해시는 체인에서 조회되지 않는다 — 기록이 있어도 검증할 수 없다. */
    @Test
    void refusesAMalformedTransactionHash() throws Exception {
        long materialId = registerMaterial();

        mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                        .header(HttpHeaders.AUTHORIZATION, seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(7L, "0xtooshort", null)))
                .andExpect(status().isBadRequest());

        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void linksTheListing() throws Exception {
        long materialId = registerMaterial();
        long listingId = listingCommandService
                .register(sellerId, new ListingRegisterRequest(
                        materialId, 30L, MaterialQuantityUnit.KG, 4_000L))
                .listingId();

        mockMvc.perform(post("/api/v1/listings/{id}/chain", listingId)
                        .header(HttpHeaders.AUTHORIZATION, seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(3L, TX, null)))
                .andExpect(status().isOk());

        assertThat(listingRepository.findById(listingId).orElseThrow().getChainListingId())
                .isEqualTo(3L);
        assertThat(transactionRepository.findAll().get(0).getFunctionName())
                .isEqualTo("createListing");
    }

    /** 판매자가 아니면 연결할 수 없다. */
    @Test
    void refusesAListingLinkFromSomeoneElse() throws Exception {
        long materialId = registerMaterial();
        long listingId = listingCommandService
                .register(sellerId, new ListingRegisterRequest(
                        materialId, 30L, MaterialQuantityUnit.KG, 4_000L))
                .listingId();

        mockMvc.perform(post("/api/v1/listings/{id}/chain", listingId)
                        .header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(3L, TX, null)))
                .andExpect(status().is4xxClientError());
    }

    // ── 거들기 ──────────────────────────────────────────────

    private long registerMaterial() {
        return registrationService.register(sellerId, new MaterialRegisterRequest(
                MaterialType.DENIM, MaterialRootOrigin.WASTE, "체인 연결용", null, "A",
                30L, MaterialQuantityUnit.KG, null, null, null, null, List.of())).materialId();
    }

    private org.springframework.test.web.servlet.ResultActions link(
            String token, long materialId, long onChainId, String txHash) throws Exception {
        return mockMvc.perform(post("/api/materials/{id}/chain", materialId)
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(onChainId, txHash, null)));
    }

    private String body(Long onChainId, String txHash, Long blockNumber) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("onChainId", onChainId);
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
        materialEventRepository.deleteAll();
        transactionRepository.deleteAll();
        listingRepository.deleteAll();
        lockRepository.deleteAll();
        balanceRepository.deleteAll();
        materialRepository.deleteAll();
        walletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
