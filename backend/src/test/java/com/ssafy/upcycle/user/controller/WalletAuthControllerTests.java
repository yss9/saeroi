package com.ssafy.upcycle.user.controller;

import static com.ssafy.upcycle.user.WalletTestSignatures.KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.OTHER_KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.WalletNonceRepository;
import com.ssafy.upcycle.user.service.WalletLoginMessage;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;

@SpringBootTest
@AutoConfigureMockMvc
class WalletAuthControllerTests {

    private static final Credentials CREDENTIALS = Credentials.create(KEY_PAIR);
    private static final long CHAIN_ID = 1337L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository userWalletRepository;

    @Autowired
    private WalletNonceRepository walletNonceRepository;

    private String address;
    private Long userId;

    @BeforeEach
    void setUp() {
        walletNonceRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();

        address = CREDENTIALS.getAddress().toLowerCase(Locale.ROOT);
        User user = userRepository.save(
                User.register("supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        userId = user.getId();
        userWalletRepository.save(UserWallet.link(userId, address, CHAIN_ID));
    }

    @AfterEach
    void tearDown() {
        // @SpringBootTest 는 트랜잭션 롤백이 없어 여기서 만든 행이 그대로 커밋된다.
        // 같은 in-memory H2 를 쓰는 다른 테스트로 새지 않도록 직접 지운다.
        walletNonceRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void nonceEndpointIsReachableWithoutAnAuthorizationHeader() throws Exception {
        // SecurityConfig 가 /api/v1/auth/** 만 열어 둔다. 경로를 옮기면 컨트롤러에
        // 닿기도 전에 401 이 되므로, 그 회귀를 여기서 잡는다.
        mockMvc.perform(post("/api/v1/auth/wallet/nonce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody(address, CHAIN_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.nonce").isNotEmpty())
                .andExpect(jsonPath("$.data.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void verifyReturnsATokenAndTheUserProfileWithoutThePassword() throws Exception {
        JsonNode issued = issueNonce();
        String body = verifyBody(issued);

        String response = mockMvc.perform(post("/api/v1/auth/wallet/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(userId))
                .andExpect(jsonPath("$.data.user.email").value("supplier@example.com"))
                .andExpect(jsonPath("$.data.user.name").value("공급자"))
                .andExpect(jsonPath("$.data.user.phone").value("010-0000-0000"))
                .andExpect(jsonPath("$.data.user.role").value("SUPPLIER"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("{noop}x");
    }

    @Test
    void theIssuedTokenCarriesTheUserIdAndAnUnprefixedRole() throws Exception {
        JsonNode issued = issueNonce();

        String response = mockMvc.perform(post("/api/v1/auth/wallet/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(issued)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(response).path("data").path("accessToken").asText();

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("SUPPLIER"));
    }

    @Test
    void rejectsAnUnlinkedWalletWithTheDocumentedCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wallet/nonce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody("0x0000000000000000000000000000000000000001", CHAIN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("WALLET_NOT_LINKED"));
    }

    @Test
    void rejectsAMalformedBodyWithValidationErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/wallet/nonce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"walletAddress\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("walletAddress"));
    }

    @Test
    void aRejectedSignatureDoesNotLeakTheSignatureBack() throws Exception {
        JsonNode issued = issueNonce();
        // 길이·형식은 맞지만 다른 키로 만든 서명.
        String signature = sign(issued.path("message").asText(), OTHER_KEY_PAIR);

        String response = mockMvc.perform(post("/api/v1/auth/wallet/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(issued.path("nonce").asText(), signature)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNATURE_MISMATCH"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(signature);
        assertThat(response).doesNotContain(Keys.toChecksumAddress(address));
    }

    private JsonNode issueNonce() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/wallet/nonce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonceBody(address, CHAIN_ID)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private String nonceBody(String walletAddress, long chainId) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of("walletAddress", walletAddress, "chainId", chainId));
    }

    private String verifyBody(JsonNode issued) {
        String message = issued.path("message").asText();
        return verifyBody(issued.path("nonce").asText(), sign(message, KEY_PAIR));
    }

    private String verifyBody(String nonce, String signature) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "walletAddress", address, "nonce", nonce, "signature", signature));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** 서명 대상 메시지가 서버가 만든 정본과 같은지 확인해 둔다 (계약 회귀 방지). */
    @Test
    void theServerServesTheCanonicalMessageItWillLaterVerifyAgainst() throws Exception {
        JsonNode issued = issueNonce();

        assertThat(issued.path("message").asText())
                .isEqualTo(WalletLoginMessage.build(
                        Keys.toChecksumAddress(address), issued.path("nonce").asText()));
    }
}
