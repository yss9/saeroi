package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
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
 * 투입 소재의 소유권 검증.
 *
 * <p><b>보유하지 않음과 수량 부족은 다른 답이다.</b> 하나로 합치면 "수량이 부족하다" 는
 * 안내를 받은 사용자가 수량을 줄여 가며 다시 시도하는데, 가진 적이 없는 소재는 아무리
 * 줄여도 통과하지 못한다.
 *
 * <p>소유의 근거는 {@code material_balance} 행이다 — 소재의 <b>등록자가 아니다.</b> 소재는
 * 거래로 주인이 바뀌므로 {@code registrant_id} 는 지금 누가 가졌는지 답하지 못한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionOwnershipTests {

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
    private ProductionInputRepository inputRepository;

    private User brand;
    private User supplier;
    private String brandToken;
    private Long productModelId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "ownership-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        supplier = userRepository.save(User.register(
                "ownership-supplier@example.com", "{noop}x", "공급자", "010-1111-1111",
                UserRole.SUPPLIER));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
    }

    @Test
    void refusesAMaterialHeldBySomeoneElse() throws Exception {
        // 공급자가 가진 소재다. 브랜드는 이걸 산 적이 없다.
        Long theirMaterial = materialHeldBy(supplier, HELD_QUANTITY);
        Long batchId = openedBatch();

        addInput(batchId, theirMaterial, 10_000L)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_OWNED"));
    }

    @Test
    void refusesAMaterialNobodyHolds() throws Exception {
        // 등록만 되고 보유 행이 만들어지지 않은 소재. 발행 전 상태다.
        Material orphan = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.VIRGIN,
                "발행 전 원단",
                null,
                "A",
                HELD_QUANTITY,
                null,
                null,
                brand.getId()));
        Long batchId = openedBatch();

        addInput(batchId, orphan.getId(), 10_000L)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_OWNED"));
    }

    @Test
    void registeringAMaterialIsNotHoldingIt() throws Exception {
        // 소재는 거래로 주인이 바뀐다. 등록자를 보면 이미 판 소재를 계속 쓸 수 있다.
        Material sold = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.VIRGIN,
                "판매한 원단",
                null,
                "A",
                HELD_QUANTITY,
                null,
                null,
                brand.getId()));
        // 등록자는 브랜드지만 보유자는 공급자다.
        balanceRepository.save(MaterialBalance.of(
                sold.getId(), supplier.getId(), otherWallet(), HELD_QUANTITY));
        Long batchId = openedBatch();

        addInput(batchId, sold.getId(), 10_000L)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_OWNED"));
    }

    @Test
    void distinguishesAnEmptyHoldingFromNoHolding() throws Exception {
        // 전량을 소비한 소재는 보유 행이 남아 있다. 그때의 정직한 답은 "수량 부족" 이다 —
        // 다시 사 오면 쓸 수 있기 때문이고, "보유하지 않음" 은 그 사실을 지운다.
        Long emptied = materialHeldBy(brand, 0L);
        Long batchId = openedBatch();

        addInput(batchId, emptied, 10_000L)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INSUFFICIENT_BALANCE"));
    }

    @Test
    void ownedMaterialWithTooLittleLeftIsAQuantityProblem() throws Exception {
        Long mine = materialHeldBy(brand, 5_000L);
        Long batchId = openedBatch();

        addInput(batchId, mine, 10_000L)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INSUFFICIENT_BALANCE"));
    }

    @Test
    void ownershipIsCheckedBeforeEligibility() throws Exception {
        // 가진 적도 없는 소재에게 "가공을 거쳐야 한다" 고 답하면,
        // 그 소재를 가공하면 쓸 수 있다는 뜻으로 읽힌다.
        Material rawWasteOfSomeoneElse = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                MaterialRootOrigin.WASTE,
                "남의 폐데님",
                null,
                "B",
                HELD_QUANTITY,
                null,
                null,
                supplier.getId()));
        balanceRepository.save(MaterialBalance.of(
                rawWasteOfSomeoneElse.getId(), supplier.getId(), otherWallet(), HELD_QUANTITY));
        Long batchId = openedBatch();

        // 자격(WASTE + INITIAL)에도 걸리고 소유에도 걸린다. 소유가 먼저 나온다.
        addInput(batchId, rawWasteOfSomeoneElse.getId(), 10_000L)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_OWNED"));
    }

    @Test
    void aMissingMaterialIsStillANotFound() throws Exception {
        // 존재 여부를 감추지 않는다. GET /api/materials/{id} 가 인증만 되면 누구에게나
        // 열려 있어 감출 것이 없고, 감추면 오타를 낸 사용자의 안내만 나빠진다.
        Long batchId = openedBatch();

        addInput(batchId, 999_999L, 10_000L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_FOUND"));
    }

    @Test
    void refusedInputLeavesNoTrace() throws Exception {
        Long theirMaterial = materialHeldBy(supplier, HELD_QUANTITY);
        Long batchId = openedBatch();

        addInput(batchId, theirMaterial, 10_000L).andExpect(status().isForbidden());

        // 잠금을 먼저 걸고 나중에 거절하면 아무도 쓰지 않는 잠금이 남아 소재가 영영 묶인다.
        assertThat(inputRepository.findAllByProductionBatchId(batchId)).isEmpty();
        assertThat(inputRepository.findAllByMaterialId(theirMaterial)).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────

    private Long materialHeldBy(User owner, long quantity) {
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
                owner.getId()));
        balanceRepository.save(MaterialBalance.of(
                material.getId(),
                owner.getId(),
                owner.equals(brand) ? WALLET : otherWallet(),
                quantity));
        return material.getId();
    }

    private ResultActions addInput(Long batchId, Long materialId, long quantity) throws Exception {
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
        request.put("batchName", "RE:BAG 1차 생산");
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

    /** 지갑 주소는 0x + 40 hex 다. UUID 하나는 32자라 둘을 이어 붙인다. */
    private String otherWallet() {
        String hex = (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
        return "0x" + hex.substring(0, 40);
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
