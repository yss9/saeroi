package com.ssafy.upcycle.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.dto.MyMaterialResponse;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceQueryRepository;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.trade.service.MaterialAvailability;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>"이 소재 얼마나 쓸 수 있나" 를 묻는 세 번째 경로가 앞의 둘과 같은 답을 하는지.</b>
 *
 * <pre>
 *   화면  GET /api/materials/me
 *         → MaterialBalanceQueryRepository#findMyMaterials   (SQL 집계)
 *
 *   판매  POST /api/v1/listings
 *         → MaterialAvailability#availableQuantity            (자바, 잠금 없는 읽기)
 *
 *   생산  POST /api/v1/production-batches/{id}/inputs
 *         → ProductionBatchService#requireAvailable           (자바, 비관적 잠금 읽기)
 * </pre>
 *
 * <p>{@code MaterialAvailabilityConsistencyTests} 가 앞의 둘을 묶어 두었는데, 생산 경로가
 * 뒤에 생기면서 <b>그 안전망 밖에 있었다.</b> 세 번째 구현이 조용히 다른 답을 하면
 * 화면에는 쓸 수 있다고 나오는데 투입만 거절된다.
 *
 * <p>규칙 자체는 {@link com.ssafy.upcycle.material.service.MaterialAvailabilityPolicy} 로
 * 모았고 {@code MaterialAvailabilityPolicyTests} 가 그 규칙을 고정한다. 이 클래스가 보는
 * 것은 <b>세 경로가 실제로 그 규칙을 쓰는가</b>다 — 규칙을 모아 두어도 어느 한쪽이 다시
 * 직접 계산하면 갈라진다.
 *
 * <p>생산 경로의 답은 숫자로 꺼낼 수 없다. 그래서 <b>경계에서 두 번 두드린다</b> —
 * 정확히 가용량만큼은 통과하고 1g 더는 거절되면, 그 경로가 생각하는 가용량이 그 값이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionAvailabilityConsistencyTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;

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
    private ProductModelRepository productModelRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialBalanceQueryRepository balanceQueryRepository;

    @Autowired
    private MaterialAvailability materialAvailability;

    @Autowired
    private Clock clock;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "availability-brand@example.com", "{noop}x", "브랜드", "010-0000-0000",
                UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial();
    }

    @Test
    void allThreePathsAgreeWhenNothingIsLocked() throws Exception {
        assertThreePathsAgreeOn(HELD_QUANTITY);
    }

    @Test
    void allThreePathsAgreeWhenAMarketplaceLockHoldsPartOfIt() throws Exception {
        // 판매 글에 걸어 둔 수량은 생산에 쓸 수 없다. 세 경로가 같은 값을 빼야 한다.
        lock(30_000L, MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, null);

        assertThreePathsAgreeOn(HELD_QUANTITY - 30_000L);
    }

    @Test
    void allThreePathsIgnoreAnExpiredLock() {
        // 규칙의 가장 미묘한 줄이다. 기한이 지난 잠금은 상태가 아직 ACTIVE 여도 세지 않는다.
        // 한쪽만 이 예외를 빠뜨리면 이미 풀린 수량을 영영 쓰지 못한다.
        lock(30_000L, MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING,
                LocalDateTime.now(clock).minusMinutes(1));

        assertThat(screenAvailable()).isEqualTo(HELD_QUANTITY);
        assertThat(materialAvailability.availableQuantity(materialId, brand.getId()))
                .isEqualTo(HELD_QUANTITY);
    }

    @Test
    void theProductionPathAlsoIgnoresAnExpiredLock() throws Exception {
        lock(30_000L, MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING,
                LocalDateTime.now(clock).minusMinutes(1));

        assertProductionAcceptsExactly(HELD_QUANTITY);
    }

    @Test
    void aProductionInputItselfReducesWhatIsLeft() throws Exception {
        // 앞선 투입도 잠금이다. 자기 배치의 잠금을 빼먹으면 같은 소재를 두 번 넣을 수 있다.
        Long batchId = openedBatch();
        addInput(batchId, 40_000L).andExpect(status().isOk());

        assertThat(screenAvailable()).isEqualTo(HELD_QUANTITY - 40_000L);
        assertThat(materialAvailability.availableQuantity(materialId, brand.getId()))
                .isEqualTo(HELD_QUANTITY - 40_000L);
        assertProductionAcceptsExactly(HELD_QUANTITY - 40_000L);
    }

    @Test
    void mixedPurposeLocksAllCountTheSame() throws Exception {
        // 목적별로 예외를 두면 같은 수량을 두 곳에 걸 수 있게 된다.
        lock(10_000L, MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, null);
        lock(20_000L, MaterialLockPurpose.PROCESSING,
                MaterialLockReferenceType.PROCESSING_JOB, null);

        assertThreePathsAgreeOn(HELD_QUANTITY - 30_000L);
    }

    @Test
    void aReleasedLockGivesTheQuantityBackEverywhere() throws Exception {
        MaterialLock released = lock(
                30_000L, MaterialLockPurpose.MARKETPLACE, MaterialLockReferenceType.LISTING, null);
        released.release(LocalDateTime.now(clock));

        assertThreePathsAgreeOn(HELD_QUANTITY);
    }

    // ── helpers ─────────────────────────────────────────────

    /** 세 경로가 같은 값을 답하는지. 생산 경로는 경계에서 두 번 두드려 확인한다. */
    private void assertThreePathsAgreeOn(long expected) throws Exception {
        assertThat(screenAvailable()).as("화면 SQL 집계").isEqualTo(expected);
        assertThat(materialAvailability.availableQuantity(materialId, brand.getId()))
                .as("판매 등록 검증")
                .isEqualTo(expected);
        assertProductionAcceptsExactly(expected);
    }

    /**
     * 생산 투입이 생각하는 가용량이 {@code expected} 인지.
     *
     * <p>딱 그만큼은 받고 1g 더는 거절해야 한다. 배치를 둘로 나눠 쓰는 이유는 한 배치에
     * 같은 소재를 두 번 넣을 수 없어서다.
     */
    private void assertProductionAcceptsExactly(long expected) throws Exception {
        addInput(openedBatch(), expected + 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INSUFFICIENT_BALANCE"));
        addInput(openedBatch(), expected).andExpect(status().isOk());
    }

    private long screenAvailable() {
        return balanceQueryRepository.findMyMaterials(brand.getId()).stream()
                .filter(row -> row.materialId().equals(materialId))
                .findFirst()
                .map(MyMaterialResponse::available)
                .orElseThrow();
    }

    private MaterialLock lock(
            long amount,
            MaterialLockPurpose purpose,
            MaterialLockReferenceType referenceType,
            LocalDateTime expiresAt) {
        return lockRepository.save(MaterialLock.create(
                materialId, brand.getId(), amount, purpose, referenceType, 1L, expiresAt));
    }

    private ResultActions addInput(Long batchId, long quantity) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", materialId);
        request.put("inputQuantity", quantity);
        request.put("productUsedQuantity", quantity);
        request.put("reusableScrapQuantity", 0L);
        request.put("wasteQuantity", 0L);

        return mockMvc.perform(post(PATH + "/{batchId}/inputs", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    private Long openedBatch() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", 1);

        String response = mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productionBatchId").asLong();
    }

    private Long heldMaterial() {
        Material material = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.VIRGIN,
                "원단",
                null,
                "A",
                HELD_QUANTITY,
                null,
                null,
                brand.getId()));
        balanceRepository.save(
                MaterialBalance.of(material.getId(), brand.getId(), WALLET, HELD_QUANTITY));
        return material.getId();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
