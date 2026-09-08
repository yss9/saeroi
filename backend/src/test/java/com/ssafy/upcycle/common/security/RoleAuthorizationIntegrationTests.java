package com.ssafy.upcycle.common.security;

import static com.ssafy.upcycle.user.WalletTestSignatures.KEY_PAIR;
import static com.ssafy.upcycle.user.WalletTestSignatures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.repository.WalletNonceRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.crypto.Credentials;

/**
 * S15P21E104-288 — 로그인이 발급한 토큰의 {@code roles} 클레임이 <b>실제 인가에 쓰이는지</b>를
 * 통합 수준에서 고정한다.
 *
 * <p>단위 변환 테스트는 이미 있다 — {@code JwtTokenServiceTests}(발급 시 접두어 거부),
 * {@code JwtAuthorityConverterTests}(클레임 → {@code ROLE_*}), {@code SecurityConfigTests}
 * (손으로 만든 토큰의 통과·거부). 셋 다 <b>토큰을 테스트가 직접 만든다.</b>
 * 그래서 "로그인이 정말 사용자 역할을 넣는가" 와 "그 값으로 인가가 갈리는가" 사이의
 * 연결은 어느 테스트도 재지 않는다. 그 구간이 이 클래스다.
 *
 * <p>토큰을 <b>실제 지갑 서명 로그인</b>({@code POST /api/v1/auth/wallet/verify})으로 받는다.
 * {@code jwtTokenService.issueAccessToken(...)} 을 직접 부르면 발급부의 회귀를 놓친다 —
 * 로그인이 역할을 넣는 것을 멈춰도 테스트는 계속 통과한다.
 *
 * <p>이메일 로그인({@code EmailLoginService})도 같은 방식으로 발급하지만 여기서 다루지
 * 않는다. 다른 담당자 영역이라 이 브랜치에서 검증 계약을 고정하면 그쪽 변경을 막는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RoleAuthorizationIntegrationTests.RoleTestController.class)
class RoleAuthorizationIntegrationTests {

    private static final Credentials CREDENTIALS = Credentials.create(KEY_PAIR);
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

    private String walletAddress;

    @BeforeEach
    void setUp() {
        clearAll();
        walletAddress = CREDENTIALS.getAddress().toLowerCase(Locale.ROOT);
    }

    @AfterEach
    void tearDown() {
        // @SpringBootTest 는 롤백하지 않는다. 커밋된 행이 같은 인메모리 H2 를 쓰는
        // 다른 테스트로 새면 단독 실행만 통과하는 실패가 된다.
        clearAll();
    }

    private void clearAll() {
        walletNonceRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── 로그인 토큰 → hasRole ────────────────────────────────

    /**
     * 로그인으로 받은 토큰이 자기 역할의 {@code @PreAuthorize} 를 통과한다.
     *
     * <p>세 역할을 모두 도는 이유는, 한 역할만 확인하면 값이 <b>어딘가에 고정</b>돼 있어도
     * 통과하기 때문이다. 지갑 로그인 대상은 {@code canOwnWallet()} 인 3개뿐이라
     * {@code CONSUMER}·{@code ADMIN} 은 여기서 확인할 수 없다 (아래 별도 테스트).
     */
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"SUPPLIER", "BRAND", "PROCESSOR"})
    void aTokenFromWalletLoginPassesItsOwnRoleGate(UserRole role) throws Exception {
        String token = loginAs(role);

        mockMvc.perform(get(pathFor(role)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(role.name()));
    }

    /**
     * 같은 토큰이 <b>다른 역할</b>의 문에서는 403 으로 막힌다.
     *
     * <p>위 테스트만 있으면 "인가가 아예 안 걸려서 다 통과하는" 상태와 구별되지 않는다.
     */
    @Test
    void aSupplierTokenIsRejectedAtTheBrandGate() throws Exception {
        String token = loginAs(UserRole.SUPPLIER);

        mockMvc.perform(get("/test/roles/brand").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    /**
     * 401 과 403 이 구분되어야 한다.
     *
     * <p>둘을 같은 응답으로 내면 화면이 "다시 로그인" 과 "권한이 없음" 을 구별할 수 없고,
     * 사용자는 로그인을 반복하다 막힌다. 두 응답 모두 공통 {@code ApiResponse} 봉투다 —
     * {@code RestAuthenticationEntryPoint} 와 {@code RestAccessDeniedHandler} 가 낸다.
     */
    @Test
    void aMissingTokenIsUnauthorizedNotForbidden() throws Exception {
        mockMvc.perform(get("/test/roles/supplier"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    /** 로그인 토큰의 클레임이 접두어 없는 DB 역할 그대로여야 한다 — 붙이는 것은 읽는 쪽이다. */
    @Test
    void theLoginTokenCarriesTheDatabaseRoleWithoutAPrefix() throws Exception {
        String token = loginAs(UserRole.PROCESSOR);

        JsonNode claims = decodePayload(token);

        assertThat(claims.path("roles").isArray()).isTrue();
        assertThat(claims.path("roles").get(0).asText()).isEqualTo("PROCESSOR");
        assertThat(claims.path("roles").toString()).doesNotContain("ROLE_");
    }

    // ── 토큰과 DB 가 어긋날 때 ────────────────────────────────

    /**
     * <b>이 문서의 결정이 실제로 지켜지는지를 재는 테스트다.</b>
     *
     * <p>{@code docs/superpowers/specs/2026-09-08-role-permission-matrix.md} 는 인가의
     * 정본을 <b>서비스 계층의 DB 재조회</b>로 정했다. 근거는 토큰이 발급 시점의 스냅샷이라는
     * 것이다. 여기서는 그 상황을 직접 만든다 — 클레임은 {@code SUPPLIER} 인데 DB 는
     * {@code PROCESSOR} 인 토큰으로 소재 등록을 시도한다.
     *
     * <p>클레임만 보는 구현이라면 통과한다. 통과하면 안 된다.
     */
    @Test
    void theDatabaseRoleWinsOverTheTokenClaim() throws Exception {
        User processor = userRepository.save(User.register(
                "drift-processor@example.com", "{noop}x", "가공", "010-9999-9999", UserRole.PROCESSOR));
        // 역할이 바뀌기 전에 발급된 토큰과 같은 모양이다.
        String staleToken = jwtTokenService
                .issueAccessToken(String.valueOf(processor.getId()), List.of(UserRole.SUPPLIER.name()))
                .value();

        mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staleToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(materialBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATERIAL_ROLE_NOT_ALLOWED"));
    }

    /**
     * {@code CONSUMER}·{@code ADMIN} 토큰은 사업자 역할의 문을 통과하지 못한다.
     *
     * <p>둘은 지갑 로그인 대상이 아니라 로그인 경로로 토큰을 만들 수 없다. 그래도
     * 매트릭스가 두 역할에 대해 전부 거부를 적고 있으므로 그 경계를 확인해 둔다.
     */
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CONSUMER", "ADMIN"})
    void nonBusinessRolesAreRejectedAtEveryBusinessGate(UserRole role) throws Exception {
        String token = jwtTokenService.issueAccessToken("1", List.of(role.name())).value();

        for (UserRole gate : List.of(UserRole.SUPPLIER, UserRole.BRAND, UserRole.PROCESSOR)) {
            mockMvc.perform(get(pathFor(gate)).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    // ── 헬퍼 ────────────────────────────────────────────────

    /** 실제 지갑 서명 로그인을 끝까지 수행하고 accessToken 을 돌려준다. */
    private String loginAs(UserRole role) throws Exception {
        User user = userRepository.save(User.register(
                role.name().toLowerCase(Locale.ROOT) + "@example.com",
                "{noop}x",
                role.name(),
                "010-0000-0000",
                role));
        userWalletRepository.save(UserWallet.link(user.getId(), walletAddress, CHAIN_ID));

        JsonNode nonce = objectMapper
                .readTree(mockMvc.perform(post("/api/v1/auth/wallet/nonce")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        Map.of("walletAddress", walletAddress, "chainId", CHAIN_ID))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data");

        String verifyBody = objectMapper.writeValueAsString(Map.of(
                "walletAddress", walletAddress,
                "nonce", nonce.path("nonce").asText(),
                "signature", sign(nonce.path("message").asText(), KEY_PAIR)));

        return objectMapper
                .readTree(mockMvc.perform(post("/api/v1/auth/wallet/verify")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(verifyBody))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("accessToken")
                .asText();
    }

    private static String pathFor(UserRole role) {
        return "/test/roles/" + role.name().toLowerCase(Locale.ROOT);
    }

    private JsonNode decodePayload(String token) throws Exception {
        String payload = token.split("\\.")[1];
        return objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(payload));
    }

    private String materialBody() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("materialType", "DENIM");
        request.put("rootOrigin", "WASTE");
        request.put("name", "폐데님 원단");
        request.put("description", "수거된 청바지");
        request.put("grade", "A");
        request.put("quantity", 50_000);
        request.put("unit", "G");
        return objectMapper.writeValueAsString(request);
    }

    /**
     * 역할 문만 있는 시험용 엔드포인트.
     *
     * <p>실제 컨트롤러를 쓰지 않는 이유는, 실제 엔드포인트는 역할 외에도 상태·소유권·수량을
     * 보기 때문이다. 그걸로 재면 <b>왜 거절됐는지</b>가 흐려지고, 도메인 규칙이 바뀔 때
     * 인가 테스트가 같이 깨진다. 여기서 재는 것은 "클레임이 권한으로 바뀌는가" 하나다.
     */
    @RestController
    static class RoleTestController {

        @PreAuthorize("hasRole('SUPPLIER')")
        @GetMapping("/test/roles/supplier")
        ApiResponse<String> supplier() {
            return ApiResponse.success("SUPPLIER");
        }

        @PreAuthorize("hasRole('BRAND')")
        @GetMapping("/test/roles/brand")
        ApiResponse<String> brand() {
            return ApiResponse.success("BRAND");
        }

        @PreAuthorize("hasRole('PROCESSOR')")
        @GetMapping("/test/roles/processor")
        ApiResponse<String> processor() {
            return ApiResponse.success("PROCESSOR");
        }
    }
}
