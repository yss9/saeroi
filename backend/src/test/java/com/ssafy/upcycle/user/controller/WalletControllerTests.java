package com.ssafy.upcycle.user.controller;

import static com.ssafy.upcycle.user.WalletTestSignatures.KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.OTHER_KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.WalletNonceRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;

/**
 * 지갑 등록·조회·비활성화. 로그인과 달리 <b>인증이 필요한</b> 경로다.
 *
 * <p>경로가 {@code /api/v1/auth/**} 밖에 있어야 하는 것이 핵심 계약이다 —
 * 그 프리픽스는 인증 없이 열려 있어서, 등록을 거기 두면 아무나 남의 계정에 지갑을 붙일 수 있다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerTests {

    private static final Credentials CREDENTIALS = Credentials.create(KEY_PAIR);
    private static final Credentials OTHER_CREDENTIALS = Credentials.create(OTHER_KEY_PAIR);
    private static final long CHAIN_ID = 1337L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository userWalletRepository;

    @Autowired
    private WalletNonceRepository walletNonceRepository;

    private String address;
    private Long supplierId;
    private String supplierToken;

    @BeforeEach
    void setUp() {
        clean();
        address = CREDENTIALS.getAddress().toLowerCase(Locale.ROOT);
        User supplier = userRepository.save(
                User.register("supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        supplierId = supplier.getId();
        supplierToken = tokenFor(supplier);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 인증·권한 ────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        // /api/v1/auth/** 밖에 있어야 한다는 계약. 안쪽으로 옮기면 이 테스트가 깨진다.
        mockMvc.perform(post("/api/v1/wallets/nonce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody(address)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/wallets/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAConsumerAccount() throws Exception {
        User consumer = userRepository.save(
                User.register("consumer@example.com", "{noop}x", "소비자", "010-1111-1111", UserRole.CONSUMER));

        mockMvc.perform(post("/api/v1/wallets/nonce")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(consumer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody(address)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WALLET_ROLE_NOT_ALLOWED"));
    }

    // ── 등록 ────────────────────────────────────────────────

    @Test
    void registersAWalletAndMarksItActiveAndVerified() throws Exception {
        JsonNode issued = issueNonce(supplierToken, address);

        mockMvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(address, issued)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.walletAddress").value(Keys.toChecksumAddress(address)))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.deactivatedAt").doesNotExist());

        assertThat(userWalletRepository.findByWalletAddressAndActiveTrue(address)).isPresent();
    }

    @Test
    void replacingAWalletKeepsThePreviousRowAsInactive() throws Exception {
        // 이 스키마의 존재 이유. 이전 주소가 사라지면 스냅샷 주소를 역추적할 수 없다.
        registerWallet(supplierToken, address);
        String next = OTHER_CREDENTIALS.getAddress().toLowerCase(Locale.ROOT);

        JsonNode issued = issueNonce(supplierToken, next);
        mockMvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(next, issued, OTHER_KEY_PAIR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        assertThat(userWalletRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(supplierId)).hasSize(2);
        assertThat(userWalletRepository.findByWalletAddress(address))
                .get()
                .satisfies(previous -> {
                    assertThat(previous.isActive()).isFalse();
                    assertThat(previous.getDeactivatedAt()).isNotNull();
                });
        assertThat(userWalletRepository.findByUserIdAndActiveTrue(supplierId))
                .get()
                .satisfies(active -> assertThat(active.getWalletAddress()).isEqualTo(next));
    }

    @Test
    void rejectsAnAddressAlreadyLinkedToAnotherAccount() throws Exception {
        User other = userRepository.save(
                User.register("brand@example.com", "{noop}x", "브랜드", "010-2222-2222", UserRole.BRAND));
        userWalletRepository.save(UserWallet.link(other.getId(), address, CHAIN_ID));

        mockMvc.perform(post("/api/v1/wallets/nonce")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody(address)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_LINKED"));
    }

    @Test
    void rejectsASignatureFromADifferentKey() throws Exception {
        JsonNode issued = issueNonce(supplierToken, address);

        mockMvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(address, issued, OTHER_KEY_PAIR)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNATURE_MISMATCH"));
    }

    // ── 목록·비활성화 ────────────────────────────────────────

    @Test
    void listsActiveAndInactiveWalletsNewestFirst() throws Exception {
        registerWallet(supplierToken, address);
        String next = OTHER_CREDENTIALS.getAddress().toLowerCase(Locale.ROOT);
        JsonNode issued = issueNonce(supplierToken, next);
        mockMvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(next, issued, OTHER_KEY_PAIR)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/wallets/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[1].active").value(false));
    }

    @Test
    void deactivatesMyWallet() throws Exception {
        Long walletId = registerWallet(supplierToken, address);

        mockMvc.perform(delete("/api/v1/wallets/" + walletId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.deactivatedAt").isNotEmpty());

        assertThat(userWalletRepository.findByUserIdAndActiveTrue(supplierId)).isEmpty();
        assertThat(userWalletRepository.findByWalletAddress(address))
                .as("행은 남는다")
                .isPresent();
    }

    @Test
    void hidesOtherUsersWalletsBehindNotFound() throws Exception {
        // "권한 없음" 과 "없음" 을 구분해 주면 id 를 훑어 존재 여부를 알아낼 수 있다.
        Long walletId = registerWallet(supplierToken, address);
        User other = userRepository.save(
                User.register("brand@example.com", "{noop}x", "브랜드", "010-2222-2222", UserRole.BRAND));

        mockMvc.perform(delete("/api/v1/wallets/" + walletId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    void rejectsDeactivatingAnAlreadyInactiveWallet() throws Exception {
        Long walletId = registerWallet(supplierToken, address);
        mockMvc.perform(delete("/api/v1/wallets/" + walletId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/wallets/" + walletId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_INACTIVE"));
    }

    // ── 로그인과의 연결 ──────────────────────────────────────

    @Test
    void aDeactivatedWalletCanNoLongerLogIn() throws Exception {
        Long walletId = registerWallet(supplierToken, address);
        mockMvc.perform(delete("/api/v1/wallets/" + walletId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/wallet/nonce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("walletAddress", address, "chainId", CHAIN_ID))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_LINKED"));
    }

    // ── helpers ──────────────────────────────────────────────

    private void clean() {
        walletNonceRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private JsonNode issueNonce(String token, String walletAddress) throws Exception {
        String response = mockMvc.perform(post("/api/v1/wallets/nonce")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody(walletAddress)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private Long registerWallet(String token, String walletAddress) throws Exception {
        JsonNode issued = issueNonce(token, walletAddress);
        String response = mockMvc.perform(post("/api/v1/wallets")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(walletAddress, issued)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private String nonceBody(String walletAddress) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("walletAddress", walletAddress, "chainId", CHAIN_ID));
    }

    private String registerBody(String walletAddress, JsonNode issued) throws Exception {
        return registerBody(walletAddress, issued, KEY_PAIR);
    }

    private String registerBody(String walletAddress, JsonNode issued, org.web3j.crypto.ECKeyPair keyPair)
            throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "walletAddress", walletAddress,
                "chainId", CHAIN_ID,
                "nonce", issued.path("nonce").asText(),
                "signature", sign(issued.path("message").asText(), keyPair)));
    }
}
