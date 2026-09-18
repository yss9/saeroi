package com.ssafy.upcycle.production.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.entity.MaterialRootOrigin;
import com.ssafy.upcycle.material.entity.MaterialType;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialLineageRepository;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.entity.ProcessType;
import com.ssafy.upcycle.processing.entity.ProcessingJob;
import com.ssafy.upcycle.processing.entity.ProcessingRequestType;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import com.ssafy.upcycle.processing.repository.ProcessingRequestTypeRepository;
import com.ssafy.upcycle.product.entity.ProductCategory;
import com.ssafy.upcycle.product.entity.ProductModel;
import com.ssafy.upcycle.product.repository.ProductItemRepository;
import com.ssafy.upcycle.product.repository.ProductModelRepository;
import com.ssafy.upcycle.production.entity.ProductionBatchStatus;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.time.LocalDateTime;
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
 * 폐소재는 <b>세척·해체·선별을 모두 거친 것만</b> 제품이 된다.
 *
 * <p>투입 자격({@code PRODUCTION_MATERIAL_NOT_ELIGIBLE})은 "가공을 한 번이라도 거쳤는가"
 * 한 칸만 본다. 그래서 재단만 한 폐데님도 통과하고, 씻지 않았고 분류되지도 않은 폐기물이
 * 업사이클 제품이 되어 팔린다 — 추적성 플랫폼에서 그건 근거 없는 주장을 파는 일이다.
 *
 * <p>화면도 같은 규칙으로 고를 수 없게 막지만, <b>화면만 막으면 규칙이 아니다.</b> 서버가
 * 마지막에 한 번 더 본다.
 *
 * <p>여기서 함께 고정하는 것이 셋이다.
 *
 * <ol>
 *   <li>신소재는 이 검사를 받지 않는다 — 새 원단에는 해체할 것도 선별할 것도 없다</li>
 *   <li>확정되지 않은 가공은 세지 않는다 — 요청만 넣으면 통과하는 규칙은 규칙이 아니다</li>
 *   <li>거절될 때 <b>소재가 소비되지 않는다</b> — 확정은 되돌릴 수 없다</li>
 * </ol>
 *
 * <p>{@code @Transactional} 로 매 테스트를 롤백한다 — 커밋된 상태를 확인하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductionRefinedMaterialTests {

    private static final String PATH = "/api/v1/production-batches";
    private static final String WALLET = "0x1234567890123456789012345678901234567890";
    private static final long CHAIN_ID = 31221L;
    private static final long HELD_QUANTITY = 100_000L;
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 18, 9, 0);

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
    private ProductItemRepository productItemRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialLineageRepository lineageRepository;

    @Autowired
    private ProductionBatchRepository batchRepository;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ProcessingRequestTypeRepository requestTypeRepository;

    private User brand;
    private User processor;
    private String token;
    private Long productModelId;
    private long sequence;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "refined-brand@example.com", "{noop}x", "브랜드", "02-1111-2222", UserRole.BRAND));
        processor = userRepository.save(User.register(
                "refined-processor@example.com", "{noop}x", "가공업체", "02-3333-4444",
                UserRole.PROCESSOR));
        token = tokenFor(brand);
        walletRepository.save(UserWallet.link(brand.getId(), WALLET, CHAIN_ID));
        productModelId = productModelRepository.save(ProductModel.register(
                        "RE:BAG", ProductCategory.BAG, brand.getId(), 129_000L, "설명", "key.jpg"))
                .getId();
    }

    // ── 통과 ────────────────────────────────────────────────

    @Test
    void wasteThatWasWashedTakenApartAndSortedBecomesAProduct() throws Exception {
        Long materialId = held(refinedWaste());

        confirm(batchWith(materialId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(ProductionBatchStatus.CONFIRMED.name()));
    }

    @Test
    void theThreeStepsMayBeSpreadAcrossSeveralJobs() throws Exception {
        // 한 업체가 셋을 다 하는 경우도, 세척만 맡기고 다른 곳에 선별을 맡기는 경우도 있다.
        // 규칙은 "한 작업이 셋을 다 했는가" 가 아니라 "혈통을 통틀어 셋이 있는가" 다.
        Material washed = processedFrom(rawWaste(), confirmedJob(ProcessType.CLEANING));
        Material dismantled = processedFrom(washed, confirmedJob(ProcessType.DISASSEMBLY));
        Long materialId = held(processedFrom(dismantled, confirmedJob(ProcessType.SORTING)));

        confirm(batchWith(materialId)).andExpect(status().isOk());
    }

    @Test
    void aVirginMaterialNeverNeededTakingApart() throws Exception {
        // 새 원단에는 해체할 것도 선별할 것도 없다. 이 규칙이 막으려는 것은 폐기물이
        // 정제 없이 제품이 되는 것이지, 모든 소재의 공정 이력이 아니다.
        Long materialId = held(materialRepository.save(Material.register(
                code(), MaterialType.DENIM, MaterialRootOrigin.VIRGIN, "새 원단", null, "A",
                HELD_QUANTITY, null, null, brand.getId())));

        confirm(batchWith(materialId)).andExpect(status().isOk());
    }

    @Test
    void scrapKeepsTheRefinementItInherited() throws Exception {
        // 자투리는 부모가 이미 거친 정제를 그대로 물려받는다. 여기서 끊으면 다시 쓰라고
        // 만든 자투리를 영영 쓸 수 없다.
        Material refined = refinedWaste();
        Material scrap = materialRepository.save(
                Material.produceScrap(code(), refined, HELD_QUANTITY, brand.getId()));
        lineageRepository.save(MaterialLineage.of(
                scrap.getId(), refined.getId(),
                MaterialLineageRelationType.PRODUCTION_SCRAP, 7_000L + ++sequence));

        confirm(batchWith(held(scrap))).andExpect(status().isOk());
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void wasteThatWasOnlyCutIsStillUnwashedAndUnsorted() throws Exception {
        // 투입 자격은 통과한다 — generation_type 이 PROCESSED 라서다. 그래서 여기서 막지
        // 않으면 씻지 않은 폐데님이 그대로 제품이 된다.
        Long materialId = held(processedFrom(rawWaste(), confirmedJob(ProcessType.CUTTING)));

        confirm(batchWith(materialId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_PROCESSED"));
    }

    @Test
    void missingJustOneOfTheThreeIsStillMissing() throws Exception {
        // 두 개까지 맞으면 통과시키고 싶은 유혹이 있지만, 그러면 규칙이 "대충 가공했는가" 가
        // 된다. 선별되지 않은 소재는 무엇이 섞였는지 아무도 모른다.
        Material washed = processedFrom(rawWaste(), confirmedJob(ProcessType.CLEANING));
        Long materialId = held(processedFrom(washed, confirmedJob(ProcessType.DISASSEMBLY)));

        confirm(batchWith(materialId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_PROCESSED"));
    }

    @Test
    void aJobThatWasNeverConfirmedProvesNothing() throws Exception {
        // processing_request_type 은 "해달라고 요청한 것" 이다. 승인 전에 세면 요청서만
        // 써 두고 아무것도 하지 않은 소재가 통과한다.
        Long materialId = held(processedFrom(rawWaste(), unconfirmedJob(
                ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING)));

        confirm(batchWith(materialId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_PROCESSED"));
    }

    @Test
    void oneUnrefinedInputStopsTheWholeBatch() throws Exception {
        // 확정은 배치 전체를 한꺼번에 소비한다. 통과한 소재만 골라 만들 수는 없다.
        Long refined = held(refinedWaste());
        Long unrefined = held(processedFrom(rawWaste(), confirmedJob(ProcessType.CUTTING)));
        Long batchId = openedBatch();
        addInput(batchId, refined);
        addInput(batchId, unrefined);

        confirm(batchId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_MATERIAL_NOT_PROCESSED"));
    }

    // ── 거절은 아무것도 바꾸지 않는다 ──────────────────────

    @Test
    void aRejectedConfirmationConsumesNothing() throws Exception {
        // 확정은 되돌릴 수 없다. 검사에 걸린 요청이 소재를 절반쯤 소비한 채 끝나면
        // 그 소재는 어디에도 쓰이지 않은 채 사라진다.
        Long materialId = held(processedFrom(rawWaste(), confirmedJob(ProcessType.CUTTING)));
        Long batchId = batchWith(materialId);

        confirm(batchId).andExpect(status().isUnprocessableEntity());

        assertThat(balanceRepository.findByMaterialIdAndOwnerId(materialId, brand.getId()))
                .get()
                .extracting(MaterialBalance::getQuantity)
                .isEqualTo(HELD_QUANTITY);
        assertThat(lockRepository.findAll())
                .allSatisfy(lock -> assertThat(lock.getStatus())
                        .isEqualTo(MaterialLockStatus.ACTIVE));
        assertThat(productItemRepository.findAll()).isEmpty();
        assertThat(batchRepository.findById(batchId))
                .get()
                .extracting(batch -> batch.getStatus())
                .isEqualTo(ProductionBatchStatus.DRAFT);
    }

    @Test
    void anEmptyBatchStillFailsOnItsMissingInputsFirst() throws Exception {
        // 투입이 없으면 검사할 소재도 없다. 새 규칙이 앞선 안내를 가로채면 사용자는
        // 넣지도 않은 소재를 가공하러 간다.
        confirm(openedBatch())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCTION_NO_INPUT"));
    }

    // ── helpers ─────────────────────────────────────────────

    private ResultActions confirm(Long batchId) throws Exception {
        return mockMvc.perform(post(PATH + "/" + batchId + "/confirm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private Long batchWith(Long materialId) throws Exception {
        Long batchId = openedBatch();
        addInput(batchId, materialId);
        return batchId;
    }

    private Long openedBatch() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("batchName", "RE:BAG " + ++sequence + "차 생산");
        request.put("producedQuantity", 2);
        request.put("productModelId", productModelId);
        String response = mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("productionBatchId").asLong();
    }

    private void addInput(Long batchId, Long materialId) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("materialId", materialId);
        input.put("inputQuantity", 30_000L);
        input.put("productUsedQuantity", 30_000L);
        input.put("reusableScrapQuantity", 0L);
        input.put("wasteQuantity", 0L);
        mockMvc.perform(post(PATH + "/" + batchId + "/inputs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("inputs", List.of(input)))))
                .andExpect(status().isOk());
    }

    /** 세척·해체·선별을 한 작업에서 모두 마친 폐소재. */
    private Material refinedWaste() {
        return processedFrom(rawWaste(), confirmedJob(
                ProcessType.CLEANING, ProcessType.DISASSEMBLY, ProcessType.SORTING));
    }

    private Material rawWaste() {
        return materialRepository.save(Material.register(
                code(), MaterialType.DENIM, MaterialRootOrigin.WASTE, "폐데님", null, "B",
                HELD_QUANTITY, null, null, brand.getId()));
    }

    private Material processedFrom(Material parent, ProcessingJob job) {
        Material child = materialRepository.save(Material.process(
                code(), parent, parent.getMaterialType(), "가공 " + sequence, "A", HELD_QUANTITY,
                processor.getId()));
        lineageRepository.save(MaterialLineage.of(
                child.getId(), parent.getId(),
                MaterialLineageRelationType.PROCESSING, job.getId()));
        return child;
    }

    private ProcessingJob confirmedJob(ProcessType... processTypes) {
        ProcessingJob job = jobRepository.save(newJob(processTypes));
        LocalDateTime at = BASE.plusHours(sequence);
        job.markPaid("refined-payment-" + sequence, 0, at.plusMinutes(1));
        job.ship(at.plusMinutes(2));
        job.receive(at.plusMinutes(3));
        job.start(at.plusMinutes(4));
        job.submitResult(
                30_000, 30_000, txHash(), "https://example.com/m/" + sequence, at.plusMinutes(5));
        job.confirmResult(at.plusMinutes(6));
        return job;
    }

    private ProcessingJob unconfirmedJob(ProcessType... processTypes) {
        return jobRepository.save(newJob(processTypes));
    }

    private ProcessingJob newJob(ProcessType... processTypes) {
        long id = ++sequence;
        long requestId = 9_000L + id;
        for (int i = 0; i < processTypes.length; i++) {
            requestTypeRepository.save(
                    new ProcessingRequestType(requestId, processTypes[i], i + 1));
        }
        return ProcessingJob.create(
                "JOB-REFINED-" + id, requestId, 8_000L + id, brand.getId(), processor.getId(),
                WALLET, WALLET, 0, PaymentMethod.MOCK, BASE.plusHours(id));
    }

    private Long held(Material material) {
        balanceRepository.save(MaterialBalance.of(
                material.getId(), brand.getId(), WALLET, material.getTotalQuantity()));
        return material.getId();
    }

    private String code() {
        return String.format("MAT-%06d", 200_000 + ++sequence);
    }

    private String txHash() {
        return "0x" + String.valueOf(sequence % 10).repeat(64);
    }

    private String tokenFor(User user) {
        return jwtTokenService
                .issueAccessToken(String.valueOf(user.getId()), List.of(user.getRole().name()))
                .value();
    }
}
