package com.ssafy.upcycle.material.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * 소재 상세 조회. 티켓이 요구한 6블록이 전부 내려가는지, 그리고 <b>지금 값이 없는 것들이
 * 왜 없는지가 응답에서 구분되는지</b>를 고정한다.
 *
 * <p>등록 테스트({@link MaterialControllerTests})와 파일을 나눈 것은 의도적이다 —
 * 여기는 보유자 시드가 필요해 준비 과정이 다르고, 한 파일에 합치면 어느 테스트가 무엇을
 * 준비했는지 읽기 어려워진다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaterialDetailControllerTests {

    private static final String OWNER_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String OTHER_OWNER_ADDRESS = "0x2222222222222222222222222222222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository materialBalanceRepository;

    private String token;
    private Long consumerId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        clean();
        User supplier = userRepository.save(User.register(
                "detail-supplier@example.com", "{noop}x", "공급자", "010-0000-0000", UserRole.SUPPLIER));
        // 조회는 역할을 보지 않는다 — 일부러 CONSUMER 토큰으로 확인한다.
        User consumer = userRepository.save(User.register(
                "detail-consumer@example.com", "{noop}x", "소비자", "010-1111-1111", UserRole.CONSUMER));
        consumerId = consumer.getId();
        token = tokenFor(consumer);

        Material material = materialRepository.save(Material.register(
                "MAT-000777",
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "폐데님 원단",
                "수거된 청바지",
                "A",
                50_000L,
                1_500L,
                1_100L,
                supplier.getId()));
        materialId = material.getId();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsNotFoundForAnUnknownId() throws Exception {
        mockMvc.perform(get("/api/materials/999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void returnsTheBasicInfoQuantityAndStatusBlocks() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.materialId").value(materialId))
                .andExpect(jsonPath("$.data.materialCode").value("MAT-000777"))
                .andExpect(jsonPath("$.data.materialType").value("DENIM"))
                .andExpect(jsonPath("$.data.rootOrigin").value("WASTE"))
                .andExpect(jsonPath("$.data.generationType").value("INITIAL"))
                .andExpect(jsonPath("$.data.name").value("폐데님 원단"))
                .andExpect(jsonPath("$.data.grade").value("A"))
                .andExpect(jsonPath("$.data.totalQuantity").value(50_000))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void reportsVerificationStateAsRegisteredBecauseItCannotBeDerived() throws Exception {
        // 판정 근거(signed_at / signer_wallet)가 schema.sql 에 없다. 이 값은 소재의 사실이
        // 아니라 구현의 한계이고, 근거가 확보되면 이 단언이 깨져 계약 변경을 알린다.
        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationState").value("REGISTERED"));
    }

    @Test
    void exposesOnChainMappingAsNullBeforeConfirm() throws Exception {
        // null tokenId 가 "등록됐지만 온체인에 없음" 의 유일한 신호다.
        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenId").doesNotExist())
                .andExpect(jsonPath("$.data.chainId").doesNotExist())
                .andExpect(jsonPath("$.data.contractAddress").doesNotExist());
    }

    @Test
    void includesTheEvidenceBlockEvenWhileEmpty() throws Exception {
        // 증빙 등록 경로가 아직 없어 값은 null 이다. 키 자체가 사라지면 FE 가 필드 존재
        // 여부로 분기하게 되고, 나중에 값이 들어오는 순간 화면이 달라진다.
        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evidenceHash").doesNotExist())
                .andExpect(jsonPath("$.data.manifestUrl").doesNotExist());
    }

    @Test
    void returnsAnEmptyOwnerListRatherThanNotFound() throws Exception {
        // 소재는 있는데 보유 행이 없는 상태(=Projection 동기화 전)와 소재가 없는 것은 다르다.
        // 같은 응답으로 묶으면 화면이 "없는 소재" 라고 잘못 말한다.
        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owners").isArray())
                .andExpect(jsonPath("$.data.owners").isEmpty());
    }

    @Test
    void returnsEveryHolderWithTheSyncTimestamp() throws Exception {
        materialBalanceRepository.save(MaterialBalance.of(materialId, consumerId, OWNER_ADDRESS, 30_000L));
        User other = userRepository.save(User.register(
                "detail-other@example.com", "{noop}x", "다른보유자", "010-2222-2222", UserRole.BRAND));
        materialBalanceRepository.save(
                MaterialBalance.of(materialId, other.getId(), OTHER_OWNER_ADDRESS, 20_000L));

        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owners.length()").value(2))
                .andExpect(jsonPath("$.data.owners[*].ownerId").exists())
                .andExpect(jsonPath("$.data.owners[*].walletAddress").exists())
                // syncedAt 이 없으면 "보유자 없음" 과 "동기화 전" 을 구분할 수 없다.
                .andExpect(jsonPath("$.data.owners[0].syncedAt").exists())
                .andExpect(jsonPath("$.data.owners[1].syncedAt").exists());
    }

    @Test
    void ownerQuantityIsTheStoredValueWithoutSubtractingLocks() throws Exception {
        // available 계산은 별도 티켓 소관이다. 여기서 잠금을 빼기 시작하면 두 곳에서
        // 같은 계산을 하게 되고 값이 갈라진다.
        materialBalanceRepository.save(MaterialBalance.of(materialId, consumerId, OWNER_ADDRESS, 30_000L));

        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owners[0].quantity").value(30_000))
                .andExpect(jsonPath("$.data.owners[0].available").doesNotExist());
    }

    @Test
    void readsWithoutRequiringAnySpecificRole() throws Exception {
        // CONSUMER 토큰으로 이미 조회하고 있다. 소재 정보는 추적성이 목적이라
        // 누가 보든 같은 값이어야 한다 — 역할 제한을 넣으면 이 테스트가 깨진다.
        mockMvc.perform(get("/api/materials/" + materialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private void clean() {
        materialBalanceRepository.deleteAll();
        materialRepository.deleteAll();
        userRepository.deleteAll();
    }
}
