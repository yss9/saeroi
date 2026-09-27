package com.ssafy.upcycle.material.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialBalance;
import com.ssafy.upcycle.material.entity.MaterialLineage;
import com.ssafy.upcycle.material.entity.MaterialLineageRelationType;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockPurpose;
import com.ssafy.upcycle.material.entity.MaterialLockReferenceType;
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
import com.ssafy.upcycle.trade.entity.MaterialListing;
import com.ssafy.upcycle.trade.entity.PaymentMethod;
import com.ssafy.upcycle.trade.repository.MaterialListingRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재가 <b>지금까지 거친 가공 종류</b>({@code completedProcessTypes}).
 *
 * <p>화면은 이 값으로 "이 소재는 세척·해체까지 됐고 선별이 남았다" 를 보여주고, 생산에
 * 넣을 수 있는지도 같은 값으로 판단한다. 그래서 여기서 지키는 것은 둘이다 —
 * <b>혈통을 거슬러 누적된다</b>는 것과, <b>확정된 가공만 센다</b>는 것.
 *
 * <p>앞이 깨지면 가공을 두 번 거친 소재가 한 번만 거친 것으로 보여 멀쩡한 소재를 생산에
 * 넣지 못하고, 뒤가 깨지면 <b>요청만 넣어 두고 아직 아무 일도 일어나지 않은 가공</b>이
 * 완료된 이력으로 보인다 — 그 상태로 만든 제품은 근거 없이 업사이클을 주장하게 된다.
 *
 * <p>{@code @Transactional} 로 매 테스트를 롤백한다 — 커밋된 상태를 확인하지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaterialProcessHistoryApiTests {

    private static final String WALLET = "0x1111111111111111111111111111111111111111";
    private static final LocalDateTime BASE = LocalDateTime.of(2026, 9, 18, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private MaterialBalanceRepository balanceRepository;

    @Autowired
    private MaterialLineageRepository lineageRepository;

    @Autowired
    private ProcessingJobRepository jobRepository;

    @Autowired
    private ProcessingRequestTypeRepository requestTypeRepository;

    @Autowired
    private MaterialLockRepository lockRepository;

    @Autowired
    private MaterialListingRepository listingRepository;

    private User brand;
    private User processor;
    private String token;
    private long sequence;

    @BeforeEach
    void setUp() {
        brand = userRepository.save(User.register(
                "history-brand@example.com", "{noop}x", "브랜드", "02-1111-2222", UserRole.BRAND));
        processor = userRepository.save(User.register(
                "history-processor@example.com", "{noop}x", "가공업체", "02-3333-4444",
                UserRole.PROCESSOR));
        token = tokenFor(brand);
    }

    // ── 목록 ────────────────────────────────────────────────

    @Test
    void anUntouchedMaterialCarriesAnEmptyListRatherThanNull() throws Exception {
        // 빈 배열과 null 을 섞으면 화면이 "가공 없음" 과 "모름" 을 두 번 분기해야 한다.
        held(rawWaste());

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completedProcessTypes").isArray())
                .andExpect(jsonPath("$.data[0].completedProcessTypes.length()").value(0));
    }

    @Test
    void aProcessedMaterialCarriesWhatThatJobActuallyDid() throws Exception {
        Material raw = rawWaste();
        held(processedFrom(raw, confirmedJob(ProcessType.CLEANING, ProcessType.DISASSEMBLY)));

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completedProcessTypes.length()").value(2))
                .andExpect(jsonPath("$.data[0].completedProcessTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("$.data[0].completedProcessTypes[1]").value("DISASSEMBLY"));
    }

    @Test
    void theHistoryAccumulatesUpTheLineage() throws Exception {
        // 이것이 이 기능의 핵심이다. 두 번 가공된 소재는 두 번의 이력을 모두 갖는다 —
        // 마지막 가공만 보면 이미 씻은 소재를 다시 씻으라고 요구하게 된다.
        Material raw = rawWaste();
        Material washed = processedFrom(raw, confirmedJob(ProcessType.CLEANING));
        held(processedFrom(washed, confirmedJob(ProcessType.SORTING)));

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completedProcessTypes.length()").value(2))
                .andExpect(jsonPath("$.data[0].completedProcessTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("$.data[0].completedProcessTypes[1]").value("SORTING"));
    }

    @Test
    void theSameKindOfProcessingTwiceIsStillOneKind() throws Exception {
        // 두 번 씻었다고 "세척, 세척" 이 되면 화면 배지가 중복된다.
        Material raw = rawWaste();
        Material washed = processedFrom(raw, confirmedJob(ProcessType.CLEANING));
        held(processedFrom(washed, confirmedJob(ProcessType.CLEANING, ProcessType.SORTING)));

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completedProcessTypes.length()").value(2));
    }

    @Test
    void aJobThatIsNotConfirmedYetCountsForNothing() throws Exception {
        // processing_request_type 은 "무엇을 해달라고 요청했는가" 다. 브랜드 승인 전에 세면
        // 요청만 넣어 둔 가공이 완료된 이력으로 보인다.
        Material raw = rawWaste();
        held(processedFrom(raw, unconfirmedJob(ProcessType.CLEANING, ProcessType.SORTING)));

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completedProcessTypes.length()").value(0));
    }

    @Test
    void scrapInheritsWhatItsParentWentThrough() throws Exception {
        // 자투리는 부모가 거친 가공을 이미 거친 상태로 떨어져 나온 것이다. 여기서 끊으면
        // 소재가 자투리가 되는 순간 이력이 사라지고, 다시 가공하라고 요구하게 된다.
        Material raw = rawWaste();
        Material washed = processedFrom(raw, confirmedJob(ProcessType.CLEANING));
        Material scrap = materialRepository.save(Material.produceScrap(
                code(), washed, 5_000L, brand.getId()));
        lineageRepository.save(MaterialLineage.of(
                scrap.getId(), washed.getId(),
                MaterialLineageRelationType.PRODUCTION_SCRAP, 4_000L + ++sequence));
        held(scrap);

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].completedProcessTypes.length()").value(1))
                .andExpect(jsonPath("$.data[0].completedProcessTypes[0]").value("CLEANING"));
    }

    @Test
    void eachRowInTheListKeepsItsOwnHistory() throws Exception {
        // 목록은 한 번에 계산한다. 소재별 구분이 무너지면 남의 가공 이력이 내 소재에 붙는다.
        Material washed = processedFrom(rawWaste(), confirmedJob(ProcessType.CLEANING));
        held(washed);
        Material untouched = rawWaste();
        held(untouched);

        mockMvc.perform(myMaterials())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.materialId == " + washed.getId()
                        + ")].completedProcessTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("$.data[?(@.materialId == " + untouched.getId()
                        + ")].completedProcessTypes[0]").doesNotExist());
    }

    // ── 상세 ────────────────────────────────────────────────

    @Test
    void theDetailViewCarriesTheSameHistory() throws Exception {
        // 목록에서 보이던 배지가 상세에서 사라지면 사용자는 어느 쪽이 맞는지 알 수 없다.
        Material washed = processedFrom(rawWaste(), confirmedJob(ProcessType.CLEANING));
        Material sorted = processedFrom(washed, confirmedJob(ProcessType.SORTING));

        mockMvc.perform(detail(sorted.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedProcessTypes.length()").value(2))
                .andExpect(jsonPath("$.data.completedProcessTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("$.data.completedProcessTypes[1]").value("SORTING"));
    }

    @Test
    void aRawMaterialDetailCarriesAnEmptyList() throws Exception {
        mockMvc.perform(detail(rawWaste().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedProcessTypes").isArray())
                .andExpect(jsonPath("$.data.completedProcessTypes.length()").value(0));
    }

    // ── 판매 글 ─────────────────────────────────────────────

    /*
      가공을 거쳐도 소재 이름은 그대로다. 같은 이름의 판매 글이 여럿이면 구매자가 가를 수 있는
      것은 이 이력뿐이다 — 보유 목록과 상세에만 있고 마켓에 없으면, 사는 쪽이 모른다.
    */
    @Test
    void aListingCarriesTheHistoryOfItsMaterial() throws Exception {
        Material washed = processedFrom(rawWaste(), confirmedJob(ProcessType.CLEANING));
        Material sorted = processedFrom(washed, confirmedJob(ProcessType.SORTING));
        MaterialListing listing = listed(sorted);

        mockMvc.perform(get("/api/v1/listings/" + listing.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedProcessTypes.length()").value(2))
                .andExpect(jsonPath("$.data.completedProcessTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("$.data.completedProcessTypes[1]").value("SORTING"));
    }

    @Test
    void theListingPageCarriesHistoryPerListing() throws Exception {
        MaterialListing raw = listed(rawWaste());
        MaterialListing washed = listed(processedFrom(rawWaste(), confirmedJob(ProcessType.CLEANING)));

        mockMvc.perform(get("/api/v1/listings").param("size", "50")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.listingId == " + raw.getId() + ")].completedProcessTypes[0]")
                        .doesNotExist())
                .andExpect(jsonPath("$.data.content[?(@.listingId == " + washed.getId() + ")].completedProcessTypes[0]")
                        .value("CLEANING"));
    }

    // ── helpers ─────────────────────────────────────────────

    private MaterialListing listed(Material material) {
        MaterialLock lock = lockRepository.save(MaterialLock.create(
                material.getId(), brand.getId(), 10_000L, MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING, 70_000L + ++sequence, BASE.plusDays(30)));
        return listingRepository.save(
                MaterialListing.open(material.getId(), brand.getId(), 10_000L, 1_000L, lock.getId()));
    }

    private MockHttpServletRequestBuilder myMaterials() {
        return get("/api/materials/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder detail(Long materialId) {
        return get("/api/materials/" + materialId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private Material rawWaste() {
        return materialRepository.save(Material.register(
                code(), MaterialType.DENIM, MaterialRootOrigin.WASTE, "폐데님", null, "B",
                50_000L, null, null, brand.getId()));
    }

    private Material processedFrom(Material parent, ProcessingJob job) {
        Material child = materialRepository.save(Material.process(
                code(), parent, parent.getMaterialType(), "가공 " + sequence, "A", 30_000L,
                processor.getId()));
        lineageRepository.save(MaterialLineage.of(
                child.getId(), parent.getId(),
                MaterialLineageRelationType.PROCESSING, job.getId()));
        return child;
    }

    /** 브랜드 승인까지 끝난 가공. 혈통 간선은 실제로 이 시점에만 만들어진다. */
    private ProcessingJob confirmedJob(ProcessType... processTypes) {
        ProcessingJob job = jobRepository.save(newJob(processTypes));
        LocalDateTime at = BASE.plusHours(sequence);
        job.markPaid("history-payment-" + sequence, 0, at.plusMinutes(1));
        job.accept(at.plusMinutes(2));
        job.ship(at.plusMinutes(2));
        job.receive(at.plusMinutes(3));
        job.start(at.plusMinutes(4));
        job.submitResult(
                30_000, 30_000, txHash(), "https://example.com/m/" + sequence, at.plusMinutes(5));
        job.confirmResult(at.plusMinutes(6));
        return job;
    }

    /** 결제만 된 가공. 요청한 종류는 적혀 있지만 아직 아무 일도 일어나지 않았다. */
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
                "JOB-HISTORY-" + id, requestId, brand.getId(), processor.getId(),
                WALLET, WALLET, 0, PaymentMethod.MOCK, BASE.plusHours(id));
    }

    private void held(Material material) {
        balanceRepository.save(MaterialBalance.of(
                material.getId(), brand.getId(), WALLET, material.getTotalQuantity()));
    }

    private String code() {
        return String.format("MAT-%06d", 100_000 + ++sequence);
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
