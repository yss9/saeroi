package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.dto.ProductionInputsRequest;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.production.repository.ProductionOutputRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 생산 배치와 소재 연결.
 *
 * <p>고정하는 계약이 다섯이다.
 *
 * <ol>
 *   <li><b>인증이 필요하고 {@code BRAND} 만 쓴다</b></li>
 *   <li><b>소재를 연결하면 그 수량이 잠긴다</b> — 잠그지 않으면 같은 소재를 두 배치가
 *       동시에 쓰고 잔량이 음수가 된다</li>
 *   <li><b>가공을 거치지 않은 폐소재는 투입할 수 없다</b> — 막지 않으면 가공 이력 없는
 *       제품이 업사이클로 팔린다</li>
 *   <li><b>가용량으로 판단한다</b> — 보유량만 보면 판매 글에 걸어 둔 수량을 생산에도 넣는다</li>
 *   <li><b>투입을 취소하면 잠금이 풀린다</b> — 안 풀면 소재가 영영 묶인다</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionBatchControllerTests {

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
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProductionInputRepository inputRepository;

    @Autowired
    private ProductionOutputRepository outputRepository;

    private User brand;
    private String brandToken;
    private Long productModelId;
    private Long materialId;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "production-brand@example.com", "{noop}x", "브랜드", "010-0000-0000", UserRole.BRAND));
        brandToken = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
        materialId = heldMaterial(MaterialRootOrigin.VIRGIN, HELD_QUANTITY);
    }

    // ── 인증·권한 ────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(openRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsASupplierAccount() throws Exception {
        User supplier = userRepository.save(User.register(
                "production-supplier@example.com", "{noop}x", "공급자", "010-1111-1111",
                UserRole.SUPPLIER));

        open(tokenFor(supplier), openRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_ROLE_NOT_ALLOWED"));
    }

    @Test
    void rejectsABrandWithoutAWallet() throws Exception {
        // producer_wallet 은 확정 때 서명할 주소다. 배치를 여는 시점에 고정하지 않으면
        // 나중에 지갑을 바꿨을 때 온체인 기록과 DB 가 어긋난다.
        User walletless = userRepository.save(User.register(
                "production-nowallet@example.com", "{noop}x", "무지갑", "010-2222-2222",
                UserRole.BRAND));

        open(tokenFor(walletless), openRequest())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_WALLET_NOT_LINKED"));
    }

    @Test
    void refusesToProduceAnotherBrandsModel() throws Exception {
        // 역할만 보면 브랜드 아무나 남의 제품 이름으로 배치를 열 수 있고,
        // 그 배치가 확정되면 남의 제품 재고가 늘어난다.
        User otherBrand = userRepository.save(User.register(
                "production-other@example.com", "{noop}x", "다른브랜드", "010-3333-3333",
                UserRole.BRAND));
        walletRepository.save(UserWallet.link(otherBrand.getId(), otherWallet(), CHAIN_ID));

        open(tokenFor(otherBrand), openRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_PRODUCT_MODEL_FORBIDDEN"));
    }

    // ── 배치 열기 ────────────────────────────────────────────

    @Test
    void opensBatchAsDraftWithASequentialCode() throws Exception {
        String response = open(brandToken, openRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.producerId").value(brand.getId()))
                // 서명 지갑이 등록된 활성 지갑에서 채워진다.
                .andExpect(jsonPath("$.data.producerWallet").value(WALLET))
                .andExpect(jsonPath("$.data.upcycleRatio").value(0))
                .andExpect(jsonPath("$.data.inputs").isArray())
                .andExpect(jsonPath("$.data.inputs.length()").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(response).path("data").path("batchCode").asText())
                .matches("^PB-\\d{6,}$");
    }

    @Test
    void neverLeavesATemporaryCodeBehind() throws Exception {
        // 임시 코드가 살아남으면 유일하지만 의미 없는 코드가 된다.
        open(brandToken, openRequest()).andExpect(status().isOk());

        assertThat(batchRepository.findAll())
                .extracting(ProductionBatch::getBatchCode)
                .noneMatch(code -> code.startsWith(ProductionBatch.TEMPORARY_CODE_PREFIX));
    }

    // ── 소재 연결 ────────────────────────────────────────────

    @Test
    void linkingAMaterialLocksThatQuantity() throws Exception {
        Long batchId = openedBatch();

        addInput(brandToken, batchId, inputRequest(materialId, 30_000L, 25_000L, 3_000L, 2_000L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs.length()").value(1))
                .andExpect(jsonPath("$.data.inputs[0].materialId").value(materialId))
                .andExpect(jsonPath("$.data.inputs[0].inputQuantity").value(30_000))
                .andExpect(jsonPath("$.data.inputs[0].materialLockId").isNumber());

        // 잠그지 않으면 같은 소재를 두 배치가 동시에 쓰고 잔량이 음수가 된다.
        assertThat(lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                        materialId, brand.getId(), MaterialLockStatus.ACTIVE))
                .singleElement()
                .extracting(MaterialLock::getAmount)
                .isEqualTo(30_000L);
    }

    @Test
    void scrapIsRecordedForReissueAtConfirmation() throws Exception {
        // 자투리가 남으면 확정 때 새 소재가 된다. 그 몫을 지금 예정해 둔다.
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 30_000L, 25_000L, 3_000L, 2_000L))
                .andExpect(status().isOk());

        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();
        assertThat(outputRepository.findAllByProductionInputId(inputId))
                .singleElement()
                .satisfies(output -> {
                    assertThat(output.getQuantity()).isEqualTo(3_000L);
                    // 소재는 확정 때 발행된다. null 은 오류가 아니라 상태다.
                    assertThat(output.isIssued()).isFalse();
                });
    }

    @Test
    void noScrapMeansNoOutputRow() throws Exception {
        // 아무것도 남지 않은 투입에 빈 산출 행을 남기면
        // "자투리가 있었는데 소재가 안 만들어졌다" 와 구분할 수 없다.
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 30_000L, 30_000L, 0L, 0L))
                .andExpect(status().isOk());

        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();
        assertThat(outputRepository.findAllByProductionInputId(inputId)).isEmpty();
    }

    @Test
    void quantitiesThatDoNotAddUpAreRejected() throws Exception {
        // ERD 가 "행마다 검증한다" 고 못 박은 식이다. 배치 합계로만 보면 한 소재를
        // 과다 입력하고 다른 소재를 과소 입력해도 통과한다.
        Long batchId = openedBatch();

        addInput(brandToken, batchId, inputRequest(materialId, 30_000L, 25_000L, 3_000L, 1_000L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INPUT_QUANTITY_MISMATCH"));
    }

    @Test
    void rawWasteMaterialCannotBeFedIntoProduction() throws Exception {
        // 수거한 그대로의 폐소재는 Processing 을 최소 1회 거쳐야 한다.
        // 막지 않으면 가공 이력 없는 제품이 업사이클로 팔린다.
        Long rawWaste = heldMaterial(MaterialRootOrigin.WASTE, HELD_QUANTITY);
        Long batchId = openedBatch();

        addInput(brandToken, batchId, inputRequest(rawWaste, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_ELIGIBLE"));
    }

    @Test
    void refusesToExceedAvailableQuantity() throws Exception {
        Long batchId = openedBatch();

        addInput(brandToken, batchId,
                        inputRequest(materialId, HELD_QUANTITY + 1, HELD_QUANTITY + 1, 0L, 0L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INSUFFICIENT_BALANCE"));
    }

    @Test
    void countsExistingLocksAgainstAvailability() throws Exception {
        // 보유량만 보면 이미 판매 글에 걸어 둔 수량을 생산에도 넣을 수 있다.
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 70_000L, 70_000L, 0L, 0L))
                .andExpect(status().isOk());

        Long secondBatchId = openedBatch();
        // 남은 가용량은 30,000g 이다. 보유량 100,000g 을 기준으로 판단하면 통과해 버린다.
        addInput(brandToken, secondBatchId, inputRequest(materialId, 40_000L, 40_000L, 0L, 0L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INSUFFICIENT_BALANCE"));
    }

    @Test
    void theSameMaterialCannotBeLinkedTwice() throws Exception {
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isOk());

        addInput(brandToken, batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_ALREADY_INPUT"));
    }

    @Test
    void refusesToTouchAnotherProducersBatch() throws Exception {
        Long batchId = openedBatch();
        User otherBrand = userRepository.save(User.register(
                "production-intruder@example.com", "{noop}x", "침입자", "010-4444-4444",
                UserRole.BRAND));
        walletRepository.save(UserWallet.link(otherBrand.getId(), otherWallet(), CHAIN_ID));

        addInput(tokenFor(otherBrand), batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRODUCTION_FORBIDDEN"));
    }

    @Test
    void refusesToChangeInputsOnceSigningStarted() throws Exception {
        // 서명한 내용과 저장된 내용이 달라지면 온체인 기록이 무엇을 증명하는지 알 수 없다.
        Long batchId = openedBatch();
        batchRepository.findById(batchId).orElseThrow().startSigning();

        addInput(brandToken, batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCTION_BATCH_NOT_EDITABLE"));
    }

    // ── 여러 소재 연결 ───────────────────────────────────────

    @Test
    void linksSeveralMaterialsInOneRequest() throws Exception {
        // 생산 화면은 소재 여러 개를 골라 한 번에 저장한다.
        Long second = heldMaterial(MaterialRootOrigin.VIRGIN, HELD_QUANTITY);
        Long batchId = openedBatch();

        addInputs(brandToken, batchId, List.of(
                        inputRequest(materialId, 30_000L, 30_000L, 0L, 0L),
                        inputRequest(second, 20_000L, 18_000L, 2_000L, 0L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs.length()").value(2));

        assertThat(lockRepository.findAllByReferenceTypeAndReferenceId(
                        MaterialLockReferenceType.PRODUCTION_BATCH, batchId))
                .hasSize(2);
    }

    @Test
    void oneRejectedEntryLocksNothingAtAll() throws Exception {
        // 한 건씩 처리하면 앞의 둘은 이미 잠긴 채 남고, 사용자는 화면에서 실패를 봤는데
        // 소재는 묶여 있는 상태가 된다. 이 테스트가 그 상태를 막는다.
        Long second = heldMaterial(MaterialRootOrigin.VIRGIN, HELD_QUANTITY);
        Long tooMuch = heldMaterial(MaterialRootOrigin.VIRGIN, 1_000L);
        Long batchId = openedBatch();

        addInputs(brandToken, batchId, List.of(
                        inputRequest(materialId, 30_000L, 30_000L, 0L, 0L),
                        inputRequest(second, 20_000L, 20_000L, 0L, 0L),
                        inputRequest(tooMuch, 5_000L, 5_000L, 0L, 0L)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INSUFFICIENT_BALANCE"));

        assertThat(inputRepository.findAllByProductionBatchId(batchId)).isEmpty();
        assertThat(lockRepository.findAllByReferenceTypeAndReferenceId(
                        MaterialLockReferenceType.PRODUCTION_BATCH, batchId))
                .isEmpty();
    }

    @Test
    void refusesTheSameMaterialTwiceInOneRequest() throws Exception {
        // DB 의 UNIQUE 는 두 번째 INSERT 에서야 걸리는데, 그때는 첫 잠금이 이미 만들어진 뒤다.
        Long batchId = openedBatch();

        addInputs(brandToken, batchId, List.of(
                        inputRequest(materialId, 10_000L, 10_000L, 0L, 0L),
                        inputRequest(materialId, 20_000L, 20_000L, 0L, 0L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCTION_DUPLICATE_MATERIAL_IN_REQUEST"));

        assertThat(inputRepository.findAllByProductionBatchId(batchId)).isEmpty();
    }

    @Test
    void refusesAnEmptyList() throws Exception {
        // 아무것도 넣지 않는 요청은 성공했다고 답할 것이 없다.
        Long batchId = openedBatch();

        mockMvc.perform(post(PATH + "/{batchId}/inputs", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("inputs", List.of()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void capsHowManyMaterialsOneRequestCanLock() throws Exception {
        // 상한이 없으면 한 번의 요청으로 소재 수천 건을 잠글 수 있다.
        Long batchId = openedBatch();
        List<Map<String, Object>> tooMany = new ArrayList<>();
        for (int i = 0; i <= ProductionInputsRequest.MAX_INPUTS; i++) {
            tooMany.add(inputRequest(materialId, 100L, 100L, 0L, 0L));
        }

        addInputs(brandToken, batchId, tooMany).andExpect(status().isBadRequest());
        assertThat(inputRepository.findAllByProductionBatchId(batchId)).isEmpty();
    }

    @Test
    void addsToABatchThatAlreadyHasInputs() throws Exception {
        // 나눠 부르는 것도 계속 된다. 두 번째 호출이 첫 번째를 덮어쓰지 않아야 한다.
        Long second = heldMaterial(MaterialRootOrigin.VIRGIN, HELD_QUANTITY);
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isOk());

        addInput(brandToken, batchId, inputRequest(second, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs.length()").value(2));
    }

    // ── 연결 해제 ────────────────────────────────────────────

    @Test
    void removingAnInputReleasesItsLock() throws Exception {
        // 잠금을 풀지 않으면 소재가 어떤 배치에도 쓰이지 않으면서 영영 묶인다.
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 30_000L, 27_000L, 3_000L, 0L))
                .andExpect(status().isOk());
        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();

        mockMvc.perform(delete(PATH + "/{batchId}/inputs/{inputId}", batchId, inputId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inputs.length()").value(0));

        assertThat(lockRepository.findAllByMaterialIdAndOwnerIdAndStatus(
                        materialId, brand.getId(), MaterialLockStatus.ACTIVE))
                .isEmpty();
        // 투입이 없어졌는데 자투리만 남으면 확정 로직이 부모 없는 산출을 만난다.
        assertThat(outputRepository.findAllByProductionInputId(inputId)).isEmpty();
    }

    @Test
    void removedQuantityBecomesAvailableAgain() throws Exception {
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, HELD_QUANTITY, HELD_QUANTITY, 0L, 0L))
                .andExpect(status().isOk());
        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();

        mockMvc.perform(delete(PATH + "/{batchId}/inputs/{inputId}", batchId, inputId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk());

        // 취소했는데 수량이 돌아오지 않으면 사용자는 이유를 알 수 없다.
        addInput(brandToken, batchId, inputRequest(materialId, HELD_QUANTITY, HELD_QUANTITY, 0L, 0L))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAnInputThatBelongsToAnotherBatch() throws Exception {
        Long batchId = openedBatch();
        Long otherBatchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isOk());
        Long inputId = inputRepository.findAllByProductionBatchId(batchId).get(0).getId();

        mockMvc.perform(delete(PATH + "/{batchId}/inputs/{inputId}", otherBatchId, inputId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCTION_INPUT_NOT_FOUND"));
    }

    // ── 조회 ────────────────────────────────────────────────

    @Test
    void detailCarriesBatchAndInputsTogether() throws Exception {
        // 나눠 두면 화면이 두 번 호출하고, 그 사이에 투입이 바뀌면 합계와 목록이 어긋난다.
        Long batchId = openedBatch();
        addInput(brandToken, batchId, inputRequest(materialId, 10_000L, 10_000L, 0L, 0L))
                .andExpect(status().isOk());

        mockMvc.perform(get(PATH + "/{batchId}", batchId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productionBatchId").value(batchId))
                .andExpect(jsonPath("$.data.inputs.length()").value(1));
    }

    @Test
    void doesNotBindMeAsABatchId() throws Exception {
        openedBatch();

        mockMvc.perform(get(PATH + "/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + brandToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    // ── helpers ─────────────────────────────────────────────

    private Map<String, Object> openRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG 1차 생산");
        request.put("productModelId", productModelId);
        request.put("producedQuantity", 50);
        return request;
    }

    private Map<String, Object> inputRequest(
            Long material, long input, long productUsed, long scrap, long waste) {
        Map<String, Object> request = new HashMap<>();
        request.put("materialId", material);
        request.put("inputQuantity", input);
        request.put("productUsedQuantity", productUsed);
        request.put("reusableScrapQuantity", scrap);
        request.put("wasteQuantity", waste);
        return request;
    }

    private ResultActions open(String token, Map<String, Object> request) throws Exception {
        return mockMvc.perform(post(PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(request)));
    }

    /** 투입 요청은 목록이다. 단건은 원소 하나짜리 목록으로 보낸다. */
    private ResultActions addInput(String token, Long batchId, Map<String, Object> request)
            throws Exception {
        return addInputs(token, batchId, List.of(request));
    }

    private ResultActions addInputs(
            String token, Long batchId, List<Map<String, Object>> requests) throws Exception {
        return mockMvc.perform(post(PATH + "/{batchId}/inputs", batchId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("inputs", requests))));
    }

    private Long openedBatch() throws Exception {
        String response = open(brandToken, openRequest())
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productionBatchId").asLong();
    }

    /**
     * 브랜드가 보유한 소재. {@code Material.register} 는 언제나 {@code INITIAL} 이므로
     * {@code VIRGIN} 이면 투입 가능하고 {@code WASTE} 면 투입 자격에 걸린다.
     */
    private Long heldMaterial(MaterialRootOrigin rootOrigin, long quantity) {
        Material material = materialRepository.save(Material.register(
                "MAT-" + UUID.randomUUID().toString().substring(0, 10),
                MaterialType.DENIM,
                rootOrigin,
                "원단",
                null,
                "A",
                quantity,
                null,
                null,
                brand.getId()));
        balanceRepository.save(
                MaterialBalance.of(material.getId(), brand.getId(), WALLET, quantity));
        return material.getId();
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
