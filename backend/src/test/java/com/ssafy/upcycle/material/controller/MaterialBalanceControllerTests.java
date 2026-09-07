package com.ssafy.upcycle.material.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
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
 * 잔량 조회 엔드포인트 2개.
 *
 * <p>여기서 고정하는 계약 중 가장 깨지기 쉬운 것은 <b>{@code /me} 가 경로 변수로
 * 잘못 잡히지 않는다</b>는 것이다. {@code GET /{materialId}} 와 {@code GET /me} 가
 * 같은 프리픽스를 쓰므로, 리터럴 우선순위가 어긋나면 {@code "me"} 를 {@code Long} 으로
 * 파싱하려다 400 이 난다. 그 실패는 "내 소재 목록이 안 나온다" 로만 보인다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaterialBalanceControllerTests {

    private static final String MY_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String OTHER_ADDRESS = "0x2222222222222222222222222222222222222222";

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

    @Autowired
    private MaterialLockRepository materialLockRepository;

    private String token;
    private Long myId;
    private Long otherId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        clean();
        User me = userRepository.save(User.register(
                "balance-me@example.com", "{noop}x", "보유자", "010-0000-0000", UserRole.SUPPLIER));
        User other = userRepository.save(User.register(
                "balance-other@example.com", "{noop}x", "타인", "010-1111-1111", UserRole.BRAND));
        myId = me.getId();
        otherId = other.getId();
        token = tokenFor(me);

        materialId = materialRepository
                .save(Material.register(
                        "MAT-000501",
                        MaterialType.DENIM,
                        MaterialRootOrigin.WASTE,
                        "폐데님 원단",
                        null,
                        "A",
                        1_000L,
                        null,
                        null,
                        myId))
                .getId();
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    // ── GET /api/materials/{materialId}/balance ──────────────

    @Test
    void balanceRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/balance"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void balanceReturnsNotFoundForAnUnknownMaterial() throws Exception {
        // 소재가 없는 것과 보유자가 없는 것은 다른 상황이다.
        mockMvc.perform(get("/api/materials/999999/balance").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void balanceReturnsAnEmptyArrayWhenNobodyHoldsIt() throws Exception {
        mockMvc.perform(get("/api/materials/" + materialId + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void balanceSubtractsOnlyActiveLocks() throws Exception {
        materialBalanceRepository.save(MaterialBalance.of(materialId, myId, MY_ADDRESS, 1_000L));
        materialLockRepository.save(lock(myId, 300L));

        mockMvc.perform(get("/api/materials/" + materialId + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(1_000))
                .andExpect(jsonPath("$.data[0].lockedQuantity").value(300))
                .andExpect(jsonPath("$.data[0].available").value(700))
                .andExpect(jsonPath("$.data[0].syncedAt").exists());
    }

    @Test
    void balanceIgnoresAnExpiredLock() throws Exception {
        // 도메인 규칙: EXPIRED 는 "전환 전이라도 available 계산에서 제외".
        // 상태 전환 배치가 없어 만료된 잠금이 ACTIVE 로 남아 있는데, 그걸 차감하면
        // 이미 끝난 거래가 판매 가능량을 계속 깎는다. 엔드포인트까지 통과해 확인한다.
        materialBalanceRepository.save(MaterialBalance.of(materialId, myId, MY_ADDRESS, 1_000L));
        materialLockRepository.save(expiringLock(myId, 300L, LocalDateTime.now().minusDays(1)));

        mockMvc.perform(get("/api/materials/" + materialId + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].lockedQuantity").value(0))
                .andExpect(jsonPath("$.data[0].available").value(1_000));
    }

    @Test
    void balanceListsEveryHolder() throws Exception {
        materialBalanceRepository.save(MaterialBalance.of(materialId, myId, MY_ADDRESS, 700L));
        materialBalanceRepository.save(MaterialBalance.of(materialId, otherId, OTHER_ADDRESS, 300L));

        mockMvc.perform(get("/api/materials/" + materialId + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                // 많이 가진 보유자가 앞이다 — 정렬이 없으면 화면 목록 순서가 매번 흔들린다.
                .andExpect(jsonPath("$.data[0].quantity").value(700))
                .andExpect(jsonPath("$.data[1].quantity").value(300));
    }

    @Test
    void balanceIsReadableByAnyAuthenticatedRole() throws Exception {
        // 소재 정보는 추적성이 목적이라 누가 보든 같은 값이어야 한다.
        User consumer = userRepository.save(User.register(
                "balance-consumer@example.com", "{noop}x", "소비자", "010-2222-2222", UserRole.CONSUMER));

        mockMvc.perform(get("/api/materials/" + materialId + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(consumer)))
                .andExpect(status().isOk());
    }

    // ── GET /api/materials/me ────────────────────────────────

    @Test
    void myMaterialsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/materials/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meIsNotParsedAsAMaterialId() throws Exception {
        // 이 테스트가 이 파일의 존재 이유 중 하나다. 리터럴 /me 가 /{materialId} 에
        // 먹히면 "me" 를 Long 으로 파싱하려다 400 이 나고, 원인이 드러나지 않는다.
        mockMvc.perform(get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void myMaterialsCarryBothTotalAndOwnedQuantity() throws Exception {
        materialBalanceRepository.save(MaterialBalance.of(materialId, myId, MY_ADDRESS, 300L));
        materialLockRepository.save(lock(myId, 100L));

        mockMvc.perform(get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].materialCode").value("MAT-000501"))
                // 총 발행량과 내 보유량은 다른 값이다. 섞으면 "1000g 을 갖고 있다" 는 거짓이 된다.
                .andExpect(jsonPath("$.data[0].totalQuantity").value(1_000))
                .andExpect(jsonPath("$.data[0].quantity").value(300))
                .andExpect(jsonPath("$.data[0].available").value(200));
    }

    @Test
    void myMaterialsExcludeOtherPeoplesHoldings() throws Exception {
        materialBalanceRepository.save(MaterialBalance.of(materialId, otherId, OTHER_ADDRESS, 500L));

        mockMvc.perform(get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void healthStillAnswers() throws Exception {
        // 배포 헬스체크가 쓰는 경로다. 회귀 확인.
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    // ── helpers ─────────────────────────────────────────────

    private MaterialLock lock(Long ownerId, long amount) {
        return expiringLock(ownerId, amount, null);
    }

    private MaterialLock expiringLock(Long ownerId, long amount, LocalDateTime expiresAt) {
        return MaterialLock.create(
                materialId,
                ownerId,
                amount,
                MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING,
                7_001L,
                expiresAt);
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private void clean() {
        materialLockRepository.deleteAll();
        materialBalanceRepository.deleteAll();
        materialRepository.deleteAll();
        userRepository.deleteAll();
    }
}
