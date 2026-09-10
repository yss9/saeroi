package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * 온체인에 올릴 manifest 와 그 해시.
 *
 * <p><b>해시는 재현할 수 있어야 의미가 있다.</b> 나중에 누군가 이 배치가 진짜인지 확인하려면
 * 같은 내용으로 같은 해시가 다시 나와야 한다. 그래서 여기서 고정하는 것은 값 자체가 아니라
 * <b>같은 배치가 언제나 같은 해시를 낸다</b>는 성질이다.
 *
 * <p>화면이 해시를 만들면 필드 순서·공백·인코딩 차이로 브라우저마다 다른 값이 나올 수 있어
 * 서버가 만든다. 이 클래스가 그 계약을 지킨다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionManifestTests {

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

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "manifest-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial();
    }

    @Test
    void manifestCarriesWhatTheChainNeedsToProve() throws Exception {
        Long batchId = confirmedBatch();

        mockMvc.perform(get(PATH + "/{id}/chain/manifest", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manifest.batchCode").isNotEmpty())
                .andExpect(jsonPath("$.data.manifest.producedQuantity").value(2))
                .andExpect(jsonPath("$.data.manifest.producerWallet").value(WALLET))
                .andExpect(jsonPath("$.data.manifest.inputs.length()").value(1))
                // 소재를 코드로 적는다. DB id 는 이 서비스 안에서만 의미 있는 순번이다.
                .andExpect(jsonPath("$.data.manifest.inputs[0].materialCode").isNotEmpty())
                .andExpect(jsonPath("$.data.manifest.itemCodes.length()").value(2))
                // 증빙 파일 해시와 같은 형식이다.
                .andExpect(jsonPath("$.data.evidenceHash").value(
                        org.hamcrest.Matchers.matchesPattern("^0x[0-9a-f]{64}$")));
    }

    @Test
    void theSameBatchAlwaysHashesTheSame() throws Exception {
        // 재현할 수 없는 해시는 아무것도 증명하지 못한다.
        Long batchId = confirmedBatch();

        assertThat(manifestHash(batchId)).isEqualTo(manifestHash(batchId));
    }

    @Test
    void differentBatchesHashDifferently() throws Exception {
        Long first = confirmedBatch();
        Long second = confirmedBatch(heldMaterial());

        assertThat(manifestHash(first)).isNotEqualTo(manifestHash(second));
    }

    @Test
    void inputOrderDoesNotChangeTheHash() throws Exception {
        // 투입을 넣은 순서나 DB 조회 순서에 기대면, 드라이버가 바뀌는 것만으로 같은 배치의
        // 해시가 달라진다. 소재 코드로 정렬해 그 여지를 없앤다.
        Long a = heldMaterial();
        Long b = heldMaterial();
        Long forward = confirmedBatchWith(List.of(a, b));
        Long backward = confirmedBatchWith(List.of(b, a));

        String forwardCodes = inputCodes(forward);
        String backwardCodes = inputCodes(backward);
        assertThat(forwardCodes).isEqualTo(backwardCodes);
    }

    @Test
    void anUnconfirmedBatchHasNoManifest() throws Exception {
        // 개체 코드와 업사이클 비율은 확정이 만드는 값이다. 확정 전에 해시를 내면
        // 나중에 내용이 달라지고, 그때는 이미 체인에 옛 해시가 올라가 있다.
        Long batchId = openedBatch();

        mockMvc.perform(get(PATH + "/{id}/chain/manifest", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_CONFIRMED"));
    }

    @Test
    void anotherBrandCannotReadIt() throws Exception {
        Long batchId = confirmedBatch();
        User otherBrand = userRepository.save(User.register(
                "manifest-other@example.com", "{noop}x", "다른브랜드", "010-3333-3333",
                UserRole.BRAND));

        mockMvc.perform(get(PATH + "/{id}/chain/manifest", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor(otherBrand)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH + "/{id}/chain/manifest", 1L))
                .andExpect(status().isUnauthorized());
    }

    // ── helpers ─────────────────────────────────────────────

    private String manifestHash(Long batchId) throws Exception {
        return objectMapper.readTree(manifestJson(batchId)).path("data").path("evidenceHash")
                .asText();
    }

    private String inputCodes(Long batchId) throws Exception {
        return objectMapper.readTree(manifestJson(batchId))
                .path("data").path("manifest").path("inputs").toString();
    }

    private String manifestJson(Long batchId) throws Exception {
        return mockMvc.perform(get(PATH + "/{id}/chain/manifest", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private Long confirmedBatch() throws Exception {
        return confirmedBatchWith(List.of(materialId));
    }

    private Long confirmedBatch(Long other) throws Exception {
        return confirmedBatchWith(List.of(other));
    }

    private Long confirmedBatchWith(List<Long> materialIds) throws Exception {
        Long batchId = openedBatch();
        List<Map<String, Object>> inputs = new ArrayList<>();
        for (Long material : materialIds) {
            inputs.add(input(material));
        }
        mockMvc.perform(post(PATH + "/{id}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inputs", inputs))))
                .andExpect(status().isOk());
        mockMvc.perform(post(PATH + "/{id}/confirm", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());
        return batchId;
    }

    private Map<String, Object> input(Long material) {
        Map<String, Object> input = new HashMap<>();
        input.put("materialId", material);
        input.put("inputQuantity", 30_000L);
        input.put("productUsedQuantity", 30_000L);
        input.put("reusableScrapQuantity", 0L);
        input.put("wasteQuantity", 0L);
        return input;
    }

    private Long openedBatch() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 1차 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", 2);

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
