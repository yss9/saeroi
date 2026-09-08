package com.ssafy.upcycle.material.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * 소재 등록 엔드포인트.
 *
 * <p>고정하는 계약이 셋이다 — <b>경로에 {@code /v1} 이 없다</b>,
 * <b>인증이 필요하다</b>, <b>{@code SUPPLIER}·{@code BRAND} 만 등록한다</b>.
 * 경로를 {@code /api/v1/auth/**} 아래로 옮기면 인증이 사라지므로 첫 테스트가 그걸 잡는다.
 *
 * <p>{@code @SpringBootTest} 는 트랜잭션 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다.
 * 전체 스위트에서만 깨지는 실패를 만들지 않으려고 {@code @BeforeEach}·{@code @AfterEach}
 * 양쪽에서 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaterialControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository materialBalanceRepository;

    @Autowired
    private UserWalletRepository userWalletRepository;

    private static final long SSAFY_CHAIN_ID = 31221L;

    private String supplierToken;

    @BeforeEach
    void setUp() {
        clean();
        User supplier = userRepository.save(User.register(
                "material-supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        // 등록은 등록자에게 전량을 발행하고, 보유 행은 활성 지갑 주소를 스냅샷으로 든다
        // (material_balance.wallet_address 는 NOT NULL). 지갑 없는 공급자는 소재를 소유할 수
        // 없으므로, 지갑을 붙이지 않은 setUp 은 실재하지 않는 사용자를 모델링하는 것이 된다.
        userWalletRepository.save(UserWallet.link(
                supplier.getId(), "0xaaaa000000000000000000000000000000000001", SSAFY_CHAIN_ID));
        supplierToken = tokenFor(supplier);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── 인증·권한 ────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        // SecurityConfig 의 permitAll 목록에 /api/materials 가 들어가면 이 테스트가 깨진다.
        mockMvc.perform(post("/api/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAProcessorAccount() throws Exception {
        // 가공업체가 만드는 소재는 가공 확정의 산출물이다. 등록을 열어 주면
        // 가공 이력 없는 소재를 가공업체 이름으로 올릴 수 있다.
        User processor = userRepository.save(User.register(
                "material-processor@example.com", "{noop}x", "가공", "010-1111-1111", UserRole.PROCESSOR));

        mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(processor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATERIAL_ROLE_NOT_ALLOWED"));
    }

    @Test
    void rejectsAConsumerAccount() throws Exception {
        User consumer = userRepository.save(User.register(
                "material-consumer@example.com", "{noop}x", "소비자", "010-2222-2222", UserRole.CONSUMER));

        mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(consumer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MATERIAL_ROLE_NOT_ALLOWED"));
    }

    // ── 등록 ────────────────────────────────────────────────

    @Test
    void registersAMaterialAndReturnsTheDerivedCode() throws Exception {
        mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.materialId").isNumber())
                .andExpect(jsonPath("$.data.materialCode").value(org.hamcrest.Matchers.matchesPattern("^MAT-\\d{6,}$")));
    }

    @Test
    void convertsKilogramsToGrams() throws Exception {
        // 저장은 항상 gram 정수다. 여기가 틀리면 수량이 1000배 어긋난 채 온체인까지 간다.
        Map<String, Object> request = validRequest();
        request.put("quantity", 50);
        request.put("unit", "KG");

        String response = mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long materialId = objectMapper.readTree(response).path("data").path("materialId").asLong();
        assertThat(materialRepository.findById(materialId))
                .get()
                .extracting(Material::getTotalQuantity)
                .isEqualTo(50_000L);
    }

    @Test
    void neverLeavesATemporaryCodeBehind() throws Exception {
        // 임시 코드가 살아남으면 유일하지만 의미 없는 코드가 된다.
        // 코드 부여가 같은 트랜잭션 안에서 끝나는 것이 계약이고, 이 테스트가 그걸 고정한다.
        mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(validRequest())))
                .andExpect(status().isOk());

        assertThat(materialRepository.findAll())
                .extracting(Material::getMaterialCode)
                .noneMatch(code -> code.startsWith(Material.TEMPORARY_CODE_PREFIX));
    }

    @Test
    void givesDifferentCodesToTwoIdenticalRequests() throws Exception {
        String first = registeredCode(validRequest());
        String second = registeredCode(validRequest());

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void fixesGenerationTypeToInitial() throws Exception {
        // 요청이 generationType 을 지정할 수 없어야 한다 — 가공을 거치지 않은 소재를
        // "가공됨" 으로 올릴 수 있게 되면 신뢰 모델의 전제가 무너진다.
        Map<String, Object> request = validRequest();
        request.put("generationType", "PROCESSED");

        String response = mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long materialId = objectMapper.readTree(response).path("data").path("materialId").asLong();
        assertThat(materialRepository.findById(materialId))
                .get()
                .extracting(material -> material.getGenerationType().name())
                .isEqualTo("INITIAL");
    }

    @Test
    void leavesOnChainColumnsNullUntilConfirm() throws Exception {
        // 등록은 DB 만 채운다. null tokenId 가 "등록됐지만 온체인에 없음" 의 유일한 신호다.
        Long materialId = Long.valueOf(
                objectMapper.readTree(rawRegister(validRequest())).path("data").path("materialId").asText());

        assertThat(materialRepository.findById(materialId))
                .get()
                .satisfies(material -> {
                    assertThat(material.getTokenId()).isNull();
                    assertThat(material.getChainId()).isNull();
                    assertThat(material.getContractAddress()).isNull();
                });
    }

    // ── 검증 실패 ────────────────────────────────────────────

    @Test
    void rejectsAnUnsupportedUnit() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("unit", "TON");

        expectBadRequest(request);
    }

    @Test
    void rejectsAFractionalQuantity() throws Exception {
        // quantity 를 Long 으로 둔 이유. 50.5 가 50 으로 조용히 버려지면 안 된다.
        Map<String, Object> request = validRequest();
        request.put("quantity", 50.5);

        expectBadRequest(request);
    }

    @Test
    void rejectsANonPositiveQuantity() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("quantity", 0);

        expectBadRequest(request);
    }

    @Test
    void rejectsAMissingName() throws Exception {
        Map<String, Object> request = validRequest();
        request.remove("name");

        expectBadRequest(request);
    }

    @Test
    void rejectsAMissingUnit() throws Exception {
        Map<String, Object> request = validRequest();
        request.remove("unit");

        expectBadRequest(request);
    }

    @Test
    void rejectsAnUnknownMaterialType() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("materialType", "SILK");

        expectBadRequest(request);
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("materialType", "DENIM");
        request.put("rootOrigin", "WASTE");
        request.put("name", "폐데님 원단");
        request.put("description", "수거된 청바지");
        request.put("grade", "A");
        request.put("quantity", 50_000);
        request.put("unit", "G");
        return request;
    }

    private void expectBadRequest(Map<String, Object> request) throws Exception {
        mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String rawRegister(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/materials")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String registeredCode(Map<String, Object> request) throws Exception {
        return objectMapper.readTree(rawRegister(request)).path("data").path("materialCode").asText();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private void clean() {
        materialBalanceRepository.deleteAll();
        materialRepository.deleteAll();
        userWalletRepository.deleteAll();
        userRepository.deleteAll();
    }
}
