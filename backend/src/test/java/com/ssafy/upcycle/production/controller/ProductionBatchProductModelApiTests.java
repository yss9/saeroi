package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * 생산할 제품을 <b>나중에</b> 정할 수 있다.
 *
 * <p>실제 작업 순서는 "배치 생성 → 소재 선택 → 사용량 입력 → Lock → … → 확정" 이다.
 * 맨 앞에서 제품을 요구하면 브랜드가 <b>무엇을 만들지를 소재도 고르기 전에</b> 확정해야
 * 한다. 그래서 여는 시점에는 비워 둘 수 있게 하고, 대신 <b>확정 전에는 반드시 채워지게</b>
 * 막는다 — 확정이 만드는 {@code product_item} 이 그 값을 그대로 쓰기 때문이다.
 *
 * <p>{@code @Transactional} 로 매 테스트를 롤백한다 — 이 클래스는 커밋된 상태를 확인하지
 * 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionBatchProductModelApiTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final long CHAIN_ID = 31221L;

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
    private ProductionBatchRepository batchRepository;

    private User brand;
    private String token;
    private Long productModelId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "pm-brand@example.com", "{noop}x", "브랜드", "02-1111-2222", UserRole.BRAND));
        token = tokenFor(brand);
        walletRepository.save(UserWallet.link(
                brand.getId(), "0x" + "aa".repeat(20), CHAIN_ID));
        productModelId = saveModel(brand.getId());
    }

    // ── 제품 없이 열기 ──────────────────────────────────────

    @Test
    void aBatchOpensWithoutDecidingWhatToMakeYet() throws Exception {
        // 이 변경의 핵심이다. 소재를 고르기 전에 제품을 확정하라고 강제하지 않는다.
        open(openRequest(null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productionBatchId").isNumber())
                .andExpect(jsonPath("$.data.productModelId").doesNotExist());
    }

    @Test
    void openingWithAProductStillWorks() throws Exception {
        // 기존 화면은 제품을 함께 보낸다. 그 경로가 깨지면 안 된다.
        open(openRequest(productModelId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productModelId").value(productModelId));
    }

    @Test
    void openingWithAnotherBrandsProductIsStillRefused() throws Exception {
        // 선택으로 바꿨다고 소유 검사가 느슨해지면 안 된다.
        User otherBrand = userRepository.save(User.register(
                "pm-other@example.com", "{noop}x", "다른브랜드", "02-9999-8888", UserRole.BRAND));
        Long othersModel = saveModel(otherBrand.getId());

        open(openRequest(othersModel))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_PRODUCT_MODEL_FORBIDDEN"));
    }

    @Test
    void aZeroProductIdIsStillAMistake() throws Exception {
        // "안 보냄"(null)과 "0 을 보냄"은 다른 일이다. 후자는 여전히 실수다.
        open(openRequest(0L)).andExpect(status().isBadRequest());
    }

    // ── 나중에 지정 ────────────────────────────────────────

    @Test
    void theProductCanBeChosenAfterTheBatchIsOpen() throws Exception {
        Long batchId = openedBatchId(null);

        assignProduct(token, batchId, productModelId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productionBatchId").value(batchId))
                .andExpect(jsonPath("$.data.productModelId").value(productModelId));

        assertThat(batchRepository.findById(batchId).orElseThrow().getProductModelId())
                .isEqualTo(productModelId);
    }

    @Test
    void aWrongChoiceCanBeCorrectedWhileStillADraft() throws Exception {
        // 고치려고 배치를 다시 여는 것은 이미 담아 둔 투입을 버리는 일이다.
        Long batchId = openedBatchId(productModelId);
        Long another = saveModel(brand.getId());

        assignProduct(token, batchId, another)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productModelId").value(another));
    }

    @Test
    void anotherBrandsProductCannotBeAttachedLaterEither() throws Exception {
        // 단계가 나뉘었다고 느슨해지면 남의 제품을 내 배치에 붙일 수 있게 된다.
        Long batchId = openedBatchId(null);
        User otherBrand = userRepository.save(User.register(
                "pm-other2@example.com", "{noop}x", "다른브랜드", "02-7777-6666", UserRole.BRAND));
        Long othersModel = saveModel(otherBrand.getId());

        assignProduct(token, batchId, othersModel)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_PRODUCT_MODEL_FORBIDDEN"));

        assertThat(batchRepository.findById(batchId).orElseThrow().getProductModelId()).isNull();
    }

    @Test
    void someoneElseCannotChooseTheProductForMyBatch() throws Exception {
        Long batchId = openedBatchId(null);
        User otherBrand = userRepository.save(User.register(
                "pm-intruder@example.com", "{noop}x", "침입자", "02-5555-4444", UserRole.BRAND));
        walletRepository.save(UserWallet.link(
                otherBrand.getId(), "0x" + "bb".repeat(20), CHAIN_ID));

        assignProduct(tokenFor(otherBrand), batchId, saveModel(otherBrand.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void theRequestMustActuallyNameAProduct() throws Exception {
        // 지정하러 부르는 경로에서 값을 비우는 것은 의도가 아니라 실수다. "지정 해제"는 없다.
        Long batchId = openedBatchId(null);

        mockMvc.perform(patch(PATH + "/" + batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aBatchThatIsNotThereCannotBeChanged() throws Exception {
        assignProduct(token, 999_999L, productModelId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_FOUND"));
    }

    @Test
    void choosingAProductNeedsALogin() throws Exception {
        Long batchId = openedBatchId(null);

        mockMvc.perform(patch(PATH + "/" + batchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("productModelId", productModelId))))
                .andExpect(status().isUnauthorized());
    }

    // ── 확정은 제품 없이도 된다 ───────────────────

    @Test
    void confirmingDoesNotRequireAProduct() throws Exception {
        // 브랜드의 실제 순서는 "생산을 끝내고 → 그 결과물에 제품을 붙인다" 다.
        // 제품이 없다는 이유로 확정을 막지 않는다 — 다음 검사(투입 없음)로 넘어간다.
        Long batchId = openedBatchId(null);

        mockMvc.perform(post(PATH + "/" + batchId + "/confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.code").value("PRODUCTION_NO_INPUT"));
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Object> openRequest(Long productModel) {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 1차 생산");
        request.put("producedQuantity", 50);
        if (productModel != null) {
            request.put("productModelId", productModel);
        }
        return request;
    }

    private ResultActions open(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    private Long openedBatchId(Long productModel) throws Exception {
        String response = open(openRequest(productModel))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productionBatchId").asLong();
    }

    private ResultActions assignProduct(String jwt, Long batchId, Long productModel)
            throws Exception {
        return mockMvc.perform(patch(PATH + "/" + batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("productModelId", productModel))));
    }

    private Long saveModel(Long ownerId) {
        ProductModel model = ProductModel.register(
                "업사이클 토트백", ProductCategory.BAG, ownerId, 79_000L, "폐데님으로 만든 가방",
                "product/thumb.png");
        return productModelRepository.saveAndFlush(model).getId();
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }

    private String body(Map<String, Object> request) throws Exception {
        return objectMapper.writeValueAsString(request);
    }
}
