package com.ssafy.upcycle.processing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static com.ssafy.upcycle.user.WalletTestSignatures.*;

import com.ssafy.upcycle.blockchain.ScriptedReceiptClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier.Action;
import com.ssafy.upcycle.blockchain.service.ProcessingReceiptVerifier.VerifiedReceipt;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.evidence.entity.*;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.processing.entity.ProcessingInput;
import com.ssafy.upcycle.processing.repository.ProcessingOutputRepository;
import com.ssafy.upcycle.processing.repository.ProcessingInputRepository;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import com.ssafy.upcycle.user.entity.UserWallet;
import java.util.List;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.web3j.crypto.Credentials;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"app.processing.expiry.enabled=false",
        "app.blockchain.chain-id=31337",
        "app.blockchain.material-token-address=0x1111111111111111111111111111111111111111",
        "app.blockchain.marketplace-address=0x2222222222222222222222222222222222222222",
        "app.blockchain.processing-registry-address=0x3333333333333333333333333333333333333333",
        "spring.datasource.url=jdbc:h2:mem:processing-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ProcessingRequestTests {
    private static final String PROCESSING_REGISTRY = "0x3333333333333333333333333333333333333333";
    @Autowired ScriptedReceiptClient chain;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired MaterialRepository materials;
    @Autowired MaterialBalanceRepository balances;
    @Autowired MaterialLockRepository locks;
    @Autowired ProcessingInputRepository inputs;
    @Autowired MaterialEventRepository events;
    @Autowired MaterialLineageRepository lineages;
    @Autowired ProcessingOutputRepository outputs;
    @Autowired EvidenceFileRepository evidenceFiles;
    @Autowired com.ssafy.upcycle.processing.service.ProcessingRequestService service;
    @Autowired com.ssafy.upcycle.processing.service.ProcessingJobService jobService;
    @Autowired com.ssafy.upcycle.processing.repository.ProcessingRequestRepository requests;
    @Autowired com.ssafy.upcycle.processing.repository.ProcessingJobRepository jobs;
    @Autowired java.time.Clock clock;
    @Autowired com.ssafy.upcycle.material.service.MaterialVerificationStateResolver verificationStates;
    @MockitoBean ProcessingReceiptVerifier receiptVerifier;
    Long brandId;
    Long processorId;
    Long materialId;

    @BeforeEach
    void fixture() {
        chain.expect(PROCESSING_REGISTRY, Credentials.create(KEY_PAIR).getAddress());
        // Real persisted fixtures: missing reservation writes and transaction errors are observable.
        for (String table : List.of("blockchain_transaction", "user_notification", "processing_signature_challenge", "evidence_file", "material_lineage", "processing_output",
                "material_event", "processing_input", "processing_job", "material_lock", "processing_request_type", "processing_request_input",
                "processing_request", "processor_capability", "processor_profile", "material_balance", "material", "user_wallet", "company_profile", "users")) {
            jdbc.update("delete from " + table);
        }
        brandId = users.save(User.register("brand@test.com", "hash", "brand", "010", UserRole.BRAND)).getId();
        processorId = users.save(User.register("processor@test.com", "hash", "processor", "010", UserRole.PROCESSOR)).getId();
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address,role_approval_status) values (?,?,?,?,?,'APPROVED')",
                brandId, "brand", "brand-business", "00000", "Seoul");
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address,role_approval_status) values (?,?,?,?,?,'APPROVED')",
                processorId, "processor", "processor-business", "00000", "Seoul");
        materialId = materials.save(Material.register("M1", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "denim", null, "A", 1000, null, null, brandId)).getId();
        balances.save(MaterialBalance.of(materialId, brandId, "0x" + "a".repeat(40), 1000));
        jdbc.update("insert into processor_profile(user_id, region) values (?, 'Seoul')", processorId);
        // 하루 1000g 씩, kg 당 1000원. 요청 1000g 이면 공정마다 1일 · 1000원이다.
        capability("DENIM", "CLEANING", 1000, 1000);
        capability("DENIM", "CUTTING", 1000, 1000);
        // 요청이 곧 작업을 만들고, 작업은 양쪽 지갑을 고정한다 — 지갑이 없으면 요청 자체가 막힌다.
        linkWallets("0x" + "a".repeat(40), "0x" + "b".repeat(40));
        when(receiptVerifier.verifyReceipt(anyString(), anyLong(), anyString(), any(), nullable(String.class), anyInt()))
                .thenAnswer(invocation -> {
                    Action action = invocation.getArgument(3);
                    long jobId = invocation.getArgument(1);
                    int count = invocation.getArgument(5);
                    if (action == Action.RECEIVE) {
                        return new VerifiedReceipt(2, 2000 + jobId, null, List.of());
                    }
                    if (action == Action.APPROVE) {
                        java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
                        for (int index = 0; index < count; index++) ids.add(9001L + index);
                        return new VerifiedReceipt(4, null, null, ids);
                    }
                    return new VerifiedReceipt(3, null, invocation.getArgument(4), List.of());
                });
    }

    void capability(String material, String purpose, long dailyCapacity, long pricePerKg) {
        jdbc.update("insert into processor_capability(processor_profile_id, material_type, process_type, daily_capacity, price_per_kg) values (?, ?, ?, ?, ?)",
                processorId, material, purpose, dailyCapacity, pricePerKg);
    }

    /** 지갑을 통째로 갈아 끼운다. 활성 지갑은 사용자당 하나라 지우고 다시 건다. */
    void linkWallets(String brandWallet, String processorWallet) {
        jdbc.update("delete from user_wallet");
        wallets.save(UserWallet.link(brandId, brandWallet, 31337));
        wallets.save(UserWallet.link(processorId, processorWallet, 31337));
    }

    String body(String quantity) {
        return "{\"materialId\":" + materialId + ",\"processorId\":" + processorId
                + ",\"quantity\":" + quantity + ",\"processTypes\":[\"CLEANING\",\"CUTTING\"]}";
    }

    MockHttpServletRequestBuilder request(String path, Long user, String role, String body) {
        return post(path).with(jwt().jwt(j -> j.subject(user.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    MockHttpServletRequestBuilder listRequests(Long user, String role) {
        return get("/processing-requests").with(jwt().jwt(j -> j.subject(user.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    long create(long amount) throws Exception {
        var result = mvc.perform(request("/processing-requests", brandId, "BRAND", body("" + amount)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("data").path("requestId").asLong();
    }

    @Test
    void createsRequestAndJobAndLockWithoutDecreasingBalance() throws Exception {
        long id = create(700);
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, id))
                .isEqualTo("REQUESTED");
        // 기한은 결제창과 같다. 요청이 먼저 죽으면 살아 있는 결제창이 남는다.
        var dates = jdbc.queryForMap("select created_at, expires_at from processing_request where id=?", id);
        assertThat(((java.sql.Timestamp) dates.get("expires_at")).toLocalDateTime())
                .isEqualTo(((java.sql.Timestamp) dates.get("created_at")).toLocalDateTime().plusMinutes(15));
        // 요청이 곧 작업을 만든다 — 견적을 고르는 단계가 없어졌다.
        Long jobId = jdbc.queryForObject(
                "select id from processing_job where processing_request_id=?", Long.class, id);
        assertThat(jobId).isNotNull();
        assertThat(locks.findAll()).singleElement().satisfies(lock -> {
            assertThat(lock.getAmount()).isEqualTo(700);
            assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
            // 잠금은 요청으로 걸렸다가 곧바로 작업으로 옮겨 붙는다.
            assertThat(lock.getReferenceType().name()).isEqualTo("PROCESSING_JOB");
            assertThat(lock.getReferenceId()).isEqualTo(jobId);
        });
        assertThat(balances.findByMaterialIdAndOwnerId(materialId, brandId).orElseThrow().getQuantity()).isEqualTo(1000);
        assertThat(jdbc.queryForObject("select count(*) from processing_request_type", Long.class)).isEqualTo(2);
        assertThat(events.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                        materialId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getEventType()).isEqualTo(MaterialEventType.PROCESSING_REQUESTED);
                    assertThat(event.getQuantity()).isEqualTo(700L);
                    assertThat(event.getReferenceId()).isEqualTo(id);
                });
        assertNotification(
                "PROCESSING_REQUEST_RECEIVED", processorId,
                "PROCESSING_REQUEST", id, "/processor/jobs");
    }

    @Test
    void subtractsLocksFromOtherDomainsAndRollsBackRejectedRequest() throws Exception {
        locks.save(MaterialLock.create(materialId, brandId, 400, MaterialLockPurpose.MARKETPLACE,
                MaterialLockReferenceType.LISTING, 123L, null));
        mvc.perform(request("/processing-requests", brandId, "BRAND", body("601")))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("PROCESSING_INSUFFICIENT_BALANCE"));
        assertThat(jdbc.queryForObject("select count(*) from processing_request", Long.class)).isZero();
        create(600);
    }

    @Test
    void failedLockInsertRollsBackRequestInputsAndPurposes() throws Exception {
        // Force a failure after request/input/purpose inserts, proving the transaction boundary.
        jdbc.execute("alter table material_lock add constraint test_processing_insert_failure check (amount < 500)");
        try {
            mvc.perform(request("/processing-requests", brandId, "BRAND", body("600")))
                    .andExpect(status().isInternalServerError());
            for (String table : List.of("processing_request", "processing_request_input", "processing_request_type", "material_lock")) {
                assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class)).isZero();
            }
        } finally {
            jdbc.execute("alter table material_lock drop constraint test_processing_insert_failure");
        }
    }

    @Test
    void requiresEveryPurposeOnTheRightMaterialButNoLongerAQuantityRange() throws Exception {
        // 요청한 공정 중 하나라도 그 소재에 등록돼 있지 않으면 거절한다. 반쯤 맡기면
        // 브랜드는 나머지를 어디에 맡겨야 하는지 모른 채 돈을 낸다.
        jdbc.update("delete from processor_capability where process_type='CUTTING'");
        capability("COTTON", "CUTTING", 1000, 1000);
        mvc.perform(request("/processing-requests", brandId, "BRAND", body("100")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSING_CAPABILITY_MISMATCH"));

        // 수량 구간은 없앴다 — 적게 맡기든 많이 맡기든 받는다. 다를 것은 금액과 기간뿐이다.
        capability("DENIM", "CUTTING", 1000, 1000);
        for (String amount : List.of("1", "100", "1000")) {
            mvc.perform(request("/processing-requests", brandId, "BRAND", body(amount)))
                    .andExpect(status().isCreated());
            jdbc.update("delete from material_lock");
        }
    }

    @Test
    void theFeeIsTheSumOfEachProcessAndTheClientNeverSendsIt() throws Exception {
        // 화면이 금액을 보내지 않는다. 서버가 가공사의 단가로 계산해 확정한다 —
        // 보내게 두면 서버는 그 값을 믿을지 말지를 정해야 하고, 믿지 않는다면 받을 이유가 없다.
        jdbc.update("update processor_capability set price_per_kg=2000 where process_type='CUTTING'");
        var created = mvc.perform(request("/processing-requests", brandId, "BRAND", body("500")))
                .andExpect(status().isCreated())
                // 세척 1000원/kg + 재단 2000원/kg, 0.5kg 이면 500 + 1000 이다.
                .andExpect(jsonPath("data.fee").value(1500))
                .andExpect(jsonPath("data.jobId").isNumber())
                .andExpect(jsonPath("data.paymentExpiresAt").isNotEmpty())
                .andReturn();
        long jobId = mapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("jobId").asLong();
        assertThat(jdbc.queryForObject("select fee from processing_job where id=?", Long.class, jobId))
                .isEqualTo(1500);
    }

    @Test
    void rejectsNonPositiveOverflowMissingAndDuplicatePurposes() throws Exception {
        for (String amount : List.of("0", "-1", "null", "9223372036854775808", "\"1\"", "1.0")) {
            mvc.perform(request("/processing-requests", brandId, "BRAND", body(amount))).andExpect(status().isBadRequest());
        }
        for (String purposes : List.of("[]", "[\"CLEANING\",\"CLEANING\"]", "[null]")) {
            mvc.perform(request("/processing-requests", brandId, "BRAND",
                    body("1").replace("[\"CLEANING\",\"CUTTING\"]", purposes))).andExpect(status().isBadRequest());
        }
        assertThat(locks.findAll()).isEmpty();
    }

    @Test
    void requiresActiveBrandAndOwnBalance() throws Exception {
        mvc.perform(post("/processing-requests").contentType(MediaType.APPLICATION_JSON).content(body("1")))
                .andExpect(status().isUnauthorized());
        mvc.perform(request("/processing-requests", processorId, "PROCESSOR", body("1"))).andExpect(status().isForbidden());
        mvc.perform(request("/processing-requests", processorId, "BRAND", body("1"))).andExpect(status().isForbidden());
        Long other = users.save(User.register("other@test.com", "hash", "other", "010", UserRole.BRAND)).getId();
        mvc.perform(request("/processing-requests", other, "BRAND", body("1"))).andExpect(status().isConflict());
        var brand = users.findById(brandId).orElseThrow();
        brand.suspend();
        users.save(brand);
        mvc.perform(request("/processing-requests", brandId, "BRAND", body("1"))).andExpect(status().isForbidden());
    }

    @Test
    void concurrentRequestsCannotReserveMoreThanTheBalance() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> call = () -> {
                start.await();
                return mvc.perform(request("/processing-requests", brandId, "BRAND", body("700")))
                        .andReturn().getResponse().getStatus();
            };
            var a = executor.submit(call);
            var b = executor.submit(call);
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(locks.findAll()).singleElement().extracting(MaterialLock::getAmount).isEqualTo(700L);
    }

    @Test
    void cancellationAndRejectionReleaseOnlyTheirOwnLocks() throws Exception {
        long first = create(400);
        long second = create(300);
        mvc.perform(request("/processing-requests/" + first + "/cancel", processorId, "PROCESSOR", ""))
                .andExpect(status().isForbidden());
        mvc.perform(request("/processing-requests/" + first + "/cancel", brandId, "BRAND", ""))
                .andExpect(status().isOk());
        mvc.perform(request("/processing-requests/" + first + "/cancel", brandId, "BRAND", ""))
                .andExpect(status().isOk());
        assertThat(locks.findAllByMaterialIdAndOwnerIdAndStatus(materialId, brandId, MaterialLockStatus.ACTIVE))
                .singleElement().extracting(MaterialLock::getAmount).isEqualTo(300L);
        mvc.perform(request("/processing-requests/" + second + "/reject", processorId, "PROCESSOR", ""))
                .andExpect(status().isOk());
        create(1000);
    }

    @Test
    void expiryReleasesLocksAndIsIdempotentWhileFutureRequestsRemainReserved() throws Exception {
        long due = create(700);
        long future = create(200);
        LocalDateTime past = LocalDateTime.now(clock).minusSeconds(1);
        jdbc.update("update processing_request set expires_at=? where id=?", past, due);
        long dueJob = jdbc.queryForObject(
                "select id from processing_job where processing_request_id=?", Long.class, due);
        jdbc.update("update material_lock set expires_at=? where reference_id=?", past, dueJob);
        var worker = new com.ssafy.upcycle.processing.service.ProcessingRequestExpiryWorker(requests, service, clock);
        worker.expireDueRequests();
        worker.expireDueRequests();
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, due)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from material_lock where reference_id=?", String.class, dueJob)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, future)).isEqualTo("REQUESTED");
        create(800);
    }

    @Test
    void activeReservationsRemainCountedUntilExpiryIsCommitted() throws Exception {
        long due = create(1000);
        LocalDateTime past = LocalDateTime.now(clock).minusSeconds(1);
        jdbc.update("update processing_request set expires_at=? where id=?", past, due);
        jdbc.update("update material_lock set expires_at=? where reference_id=?", past, due);
        mvc.perform(request("/processing-requests", brandId, "BRAND", body("1000")))
                .andExpect(status().isConflict());
        service.expire(due);
        create(1000);
        assertThat(locks.findAllByMaterialIdAndOwnerIdAndStatus(materialId, brandId, MaterialLockStatus.ACTIVE))
                .singleElement().extracting(MaterialLock::getAmount).isEqualTo(1000L);
    }

    @Test
    void legacyUnassignedProcessorIsForbiddenInsteadOfServerError() throws Exception {
        long id = create(100);
        jdbc.execute("alter table processing_request alter column processor_id drop not null");
        try {
            jdbc.update("update processing_request set processor_id=null where id=?", id);
            mvc.perform(request("/processing-requests/" + id + "/reject", processorId, "PROCESSOR", ""))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("update processing_request set processor_id=? where id=?", processorId, id);
            jdbc.execute("alter table processing_request alter column processor_id set not null");
        }
    }

    @Test
    void theReservationFollowsTheJobFromTheMomentItIsCreated() throws Exception {
        // 예전에는 견적을 고를 때 잠금이 요청에서 작업으로 옮겨 붙었다. 지금은 요청이 곧
        // 작업이라 그 이동이 생성 트랜잭션 안에서 일어나고, 밖에서는 보이지 않는다.
        long id = create(700);
        long jobId = jdbc.queryForObject(
                "select id from processing_job where processing_request_id=?", Long.class, id);
        var persisted = locks.findAll().getFirst();
        assertThat(persisted.getAmount()).isEqualTo(700);
        assertThat(persisted.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
        assertThat(persisted.getReferenceType()).isEqualTo(MaterialLockReferenceType.PROCESSING_JOB);
        assertThat(persisted.getReferenceId()).isEqualTo(jobId);
        // 기한은 결제창을 따라간다. 결제되면 풀린다 (clearExpiryForPaidProcessingJob).
        assertThat(persisted.getExpiresAt()).isNotNull();
    }

    @Test
    void processorSearchCarriesThePriceAndDaysTheBrandWillActuallyPay() throws Exception {
        // 견적이 사라진 자리를 이 값들이 메운다. 화면은 곱하지 않고 이대로 보여 준다 —
        // 화면이 계산하면 올림이 어긋나는 순간 목록 금액과 결제 금액이 달라진다.
        jdbc.update("update processor_capability set daily_capacity=500,price_per_kg=2000 where process_type='CUTTING'");

        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType", "DENIM").param("quantity", "500")
                        .param("processTypes", "CLEANING", "CUTTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.items[0].processorId").value(processorId))
                .andExpect(jsonPath("data.items[0].region").value("Seoul"))
                // 단가는 두 공정의 합, 하루 처리량은 느린 쪽(병목)이다.
                .andExpect(jsonPath("data.items[0].pricePerKg").value(3000))
                .andExpect(jsonPath("data.items[0].dailyCapacity").value(500))
                // 500g → 세척 500원(1일) + 재단 1000원(1일)
                .andExpect(jsonPath("data.items[0].estimatedPrice").value(1500))
                .andExpect(jsonPath("data.items[0].estimatedDays").value(2))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].processType").value("CLEANING"))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].leadDays").value(1))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].processType").value("CUTTING"))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].leadDays").value(1));
    }

    @Test
    void askingForMoreTakesLongerRatherThanFindingNothing() throws Exception {
        // 수량 상한을 없앤 결정이 여기 있다. 예전에는 max_quantity 를 넘으면 업체가 아예
        // 사라져서, 브랜드는 "왜 아무도 안 나오지" 만 보고 이유를 알 수 없었다.
        search("100").andExpect(jsonPath("data.items[0].estimatedDays").value(2))
                .andExpect(jsonPath("data.items[0].estimatedPrice").value(200));
        search("2500").andExpect(jsonPath("data.items[0].estimatedDays").value(6))
                .andExpect(jsonPath("data.items[0].estimatedPrice").value(5000));

        // 다만 요청한 공정을 다 못 하는 업체는 여전히 빠진다 — 반쯤 맡길 수는 없다.
        jdbc.update("delete from processor_capability where process_type='CUTTING'");
        search("100").andExpect(jsonPath("data.items").isEmpty()).andExpect(jsonPath("data.total").value(0));
    }

    @Test
    void aCapabilityWithNoDailyCapacityCannotBeScheduled() throws Exception {
        // 하루 처리량이 0 이면 소요일의 분모가 0 이다. 계산이 성립하지 않는 업체를
        // 목록에 남기면 화면이 "0일" 이나 무한대를 보여 준다.
        jdbc.update("update processor_capability set daily_capacity=0 where process_type='CUTTING'");
        search("100").andExpect(jsonPath("data.items").isEmpty());
    }
    org.springframework.test.web.servlet.ResultActions search(String amount) throws Exception {
        return mvc.perform(get("/api/v1/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                .param("materialType", "DENIM").param("quantity", amount)
                .param("processTypes", "CLEANING,CUTTING"));
    }

    @Test
    void searchValidatesInputsAndActiveBrandPermission() throws Exception {
        for (String amount : List.of("0", "-1", "1.2", "9223372036854775808")) search(amount).andExpect(status().isBadRequest());
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .param("materialType", "DENIM").param("quantity", "100").param("processTypes", "CLEANING"))
                .andExpect(status().isForbidden());
        var processor = users.findById(processorId).orElseThrow(); processor.suspend(); users.save(processor);
        search("100").andExpect(jsonPath("data.items").isEmpty());
    }

    @Test
    void searchPaginationIsStableForCompletelyRegisteredProcessors() throws Exception {
        Long second=users.save(User.register("second@test.com","hash","second","010",UserRole.PROCESSOR)).getId();
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address,role_approval_status) values (?,?,?,?,?,'APPROVED')",
                second, "second", "second-business", "00000", "Busan");
        jdbc.update("insert into processor_profile(user_id,region) values (?,'Busan')",second);
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,daily_capacity,price_per_kg) values (?,'DENIM','CLEANING',500,1000),(?,'DENIM','CUTTING',250,1000)",second,second);
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType","DENIM").param("quantity","100").param("processTypes","CLEANING,CUTTING")
                        .param("page","1").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(2))
                .andExpect(jsonPath("data.items.length()").value(1))
                .andExpect(jsonPath("data.items[0].processorId").value(second));
    }

    @Test
    void searchRejectsDuplicatePurposesAndExcessivePageSize() throws Exception {
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType","DENIM").param("quantity","100").param("processTypes","CLEANING,CLEANING"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType","DENIM").param("quantity","100").param("processTypes","CLEANING").param("size","51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownedMaterialsReturnOnlyOwnBalanceAndSubtractAllActiveLocks() throws Exception {
        locks.save(MaterialLock.create(materialId,brandId,300,MaterialLockPurpose.MARKETPLACE,MaterialLockReferenceType.LISTING,1L,null));
        mvc.perform(get("/api/v1/processing-requests/materials").with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data[0].materialId").value(materialId))
                .andExpect(jsonPath("data[0].quantity").value(1000))
                .andExpect(jsonPath("data[0].lockedQuantity").value(300))
                .andExpect(jsonPath("data[0].availableQuantity").value(700));
        Long other = users.save(User.register("empty@test.com","hash","other","010",UserRole.BRAND)).getId();
        mvc.perform(get("/api/v1/processing-requests/materials").with(jwt().jwt(j -> j.subject(other.toString()))))
                .andExpect(jsonPath("data").isEmpty());
    }

    @Test
    void rejectsFractionalGramsInsteadOfSilentlyTruncatingThem() throws Exception {
        mvc.perform(post("/processing-requests")
                        .with(jwt().jwt(j -> j.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_BRAND")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialId":1,"processorId":2,"quantity":1.5,"processTypes":["CLEANING"]}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processorListsOnlyRequestsAssignedToItWithMaterialBrandAndPurposes() throws Exception {
        long assigned = create(300);
        long notAssigned = create(200);
        Long otherProcessor = users.save(User.register(
                "other-processor@test.com", "hash", "other processor", "010", UserRole.PROCESSOR)).getId();
        jdbc.update("update processing_request set processor_id=? where id=?", otherProcessor, notAssigned);
        jdbc.update("update processing_request set created_at=dateadd('SECOND', -1, created_at) where id=?", assigned);

        mvc.perform(listRequests(processorId, "PROCESSOR").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.total").value(1))
                .andExpect(jsonPath("data.page").value(0))
                .andExpect(jsonPath("data.size").value(20))
                .andExpect(jsonPath("data.items.length()").value(1))
                .andExpect(jsonPath("data.items[0].requestId").value(assigned))
                .andExpect(jsonPath("data.items[0].brandId").value(brandId))
                .andExpect(jsonPath("data.items[0].brandName").value("brand"))
                .andExpect(jsonPath("data.items[0].processorId").value(processorId))
                .andExpect(jsonPath("data.items[0].processorName").value("processor"))
                .andExpect(jsonPath("data.items[0].materialId").value(materialId))
                .andExpect(jsonPath("data.items[0].materialCode").value("M1"))
                .andExpect(jsonPath("data.items[0].materialName").value("denim"))
                .andExpect(jsonPath("data.items[0].materialType").value("DENIM"))
                .andExpect(jsonPath("data.items[0].quantity").value(300))
                .andExpect(jsonPath("data.items[0].processTypes[0]").value("CLEANING"))
                .andExpect(jsonPath("data.items[0].processTypes[1]").value("CUTTING"))
                .andExpect(jsonPath("data.items[0].status").value("REQUESTED"))
                .andExpect(jsonPath("data.items[0].createdAt").exists())
                .andExpect(jsonPath("data.items[0].expiresAt").exists());
    }

    @Test
    void authenticatedBrandListsItsOwnRequestsAndCanFilterStatus() throws Exception {
        long canceled = create(100);
        create(200);
        mvc.perform(request("/processing-requests/" + canceled + "/cancel", brandId, "BRAND", ""))
                .andExpect(status().isOk());

        mvc.perform(listRequests(brandId, "BRAND").param("status", "CANCELED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.total").value(1))
                .andExpect(jsonPath("data.items[0].requestId").value(canceled))
                .andExpect(jsonPath("data.items[0].status").value("CANCELED"));
    }

    @Test
    void requestListRequiresActiveAuthenticationAndValidFilters() throws Exception {
        mvc.perform(get("/processing-requests")).andExpect(status().isUnauthorized());
        Long supplier = users.save(User.register(
                "supplier@test.com", "hash", "supplier", "010", UserRole.SUPPLIER)).getId();
        mvc.perform(listRequests(supplier, "SUPPLIER")).andExpect(status().isOk())
                .andExpect(jsonPath("data.items").isEmpty())
                .andExpect(jsonPath("data.total").value(0));
        mvc.perform(listRequests(processorId, "PROCESSOR").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(listRequests(processorId, "PROCESSOR").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(listRequests(processorId, "PROCESSOR").param("size", "51"))
                .andExpect(status().isBadRequest());

        var processor = users.findById(processorId).orElseThrow();
        processor.suspend();
        users.save(processor);
        mvc.perform(listRequests(processorId, "PROCESSOR")).andExpect(status().isForbidden());
    }

    @Test
    void requestPaymentAndAcceptanceKeepTheSameLockAndStayPartyScoped() throws Exception {
        linkWallets("0x" + "1".repeat(40), "0x" + "2".repeat(40));
        // 세척·재단 각 1000원/kg, 0.3kg → 300 + 300.
        var created = mvc.perform(request("/processing-requests", brandId, "BRAND", body("300")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.fee").value(600))
                .andExpect(jsonPath("data.status").value("REQUESTED"))
                .andReturn();
        long requestId = mapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("requestId").asLong();
        long jobId = mapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("jobId").asLong();

        mvc.perform(get("/processing-requests/{id}", requestId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.quantity").value(300));
        Long stranger = users.save(User.register("stranger@test.com", "hash", "stranger", "010", UserRole.BRAND)).getId();
        mvc.perform(get("/processing-requests/{id}", requestId)
                        .with(jwt().jwt(j -> j.subject(stranger.toString())))).andExpect(status().isForbidden());

        var timestamps = jdbc.queryForMap("select created_at,payment_expires_at from processing_job where id=?", jobId);
        assertThat(((java.sql.Timestamp) timestamps.get("payment_expires_at")).toLocalDateTime())
                .isEqualTo(((java.sql.Timestamp) timestamps.get("created_at")).toLocalDateTime().plusMinutes(15));
        MaterialLock reserved = locks.findAll().getFirst();
        assertThat(reserved.getReferenceType()).isEqualTo(MaterialLockReferenceType.PROCESSING_JOB);
        assertThat(reserved.getReferenceId()).isEqualTo(jobId);
        assertThat(reserved.getExpiresAt()).isNotNull();

        mvc.perform(get("/processing-jobs/{id}", jobId).with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.fee").value(600));
        mvc.perform(get("/processing-jobs/{id}", jobId).with(jwt().jwt(j -> j.subject(stranger.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/processing-jobs/{id}/payment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.amount").value(600))
                .andExpect(jsonPath("data.live").value(false));
        mvc.perform(post("/processing-jobs/{id}/payment/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"mock-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("PAID"))
                // 결제와 동시에 수락 기한이 걸린다. 없으면 만료 배치가 이 작업을 못 찾는다.
                .andExpect(jsonPath("data.acceptExpiresAt").isNotEmpty());
        assertThat(locks.findAll().getFirst().getExpiresAt()).isNull();

        // 결제만으로는 못 보낸다 — 가공사가 받겠다고 답한 뒤여야 한다.
        mvc.perform(post("/processing-jobs/{id}/shipment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isConflict());
        // 수락은 온체인 등록을 전제로 한다 — 먼저 체인에 올린 뒤에야 받을 수 있다.
        linkChainJob(jobId);
        acceptJob(jobId);
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, requestId))
                .isEqualTo("ACCEPTED");

        assertThat(events.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                        materialId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(event -> event.getEventType())
                .containsExactly(
                        MaterialEventType.PROCESSING_REQUESTED,
                        MaterialEventType.PROCESSING_JOB_PAID,
                        MaterialEventType.PROCESSING_JOB_ACCEPTED);
    }

    @Test
    void aDeclinedRequestRefundsTheBrandAndReleasesTheReservation() throws Exception {
        // 요청 시 결제로 정했기 때문에 반드시 있어야 하는 경로다. 가공사가 거절했는데
        // 돈이 돌아가지 않으면 거절이 곧 갈취가 된다.
        long jobId = createJob(300);
        payJob(jobId);

        mvc.perform(patch("/processing-jobs/{id}/reject", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_PROCESSOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"설비 점검 중입니다\"}"))
                .andExpect(status().isOk());

        var job = jdbc.queryForMap("select status,decline_reason,refunded_at from processing_job where id=?", jobId);
        assertThat(job.get("status")).isEqualTo("DECLINED");
        assertThat(job.get("decline_reason")).isEqualTo("설비 점검 중입니다");
        assertThat(job.get("refunded_at")).isNotNull();
        assertThat(jdbc.queryForObject(
                "select status from processing_request where id=(select processing_request_id from processing_job where id=?)",
                String.class, jobId)).isEqualTo("REJECTED");
        // 소재가 다시 쓸 수 있게 풀렸는가. 안 풀리면 브랜드의 소재가 영영 묶인다.
        assertThat(locks.findAll().getFirst().getStatus()).isEqualTo(MaterialLockStatus.RELEASED);
    }

    @Test
    void aProcessorWhoNeverAnswersLosesTheJobAndTheBrandGetsItsMoneyBack() throws Exception {
        long jobId = createJob(300);
        payJob(jobId);
        jdbc.update("update processing_job set accept_expires_at=? where id=?",
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusSeconds(1)), jobId);

        new com.ssafy.upcycle.processing.service.ProcessingAcceptanceExpiryWorker(
                jobs, jobService, clock).expireUnansweredJobs();

        var job = jdbc.queryForMap("select status,decline_reason,refunded_at from processing_job where id=?", jobId);
        assertThat(job.get("status")).isEqualTo("EXPIRED");
        // 아무도 적지 않았으므로 사유는 비어 있다. 서버가 그럴듯한 문장을 지어내지 않는다.
        assertThat(job.get("decline_reason")).isNull();
        assertThat(job.get("refunded_at")).isNotNull();
        // 결제가 끝난 잠금은 기한이 풀려 있어 "만료" 가 아니라 "해제" 로 돌아간다 —
        // 잠금 자체가 시간을 넘긴 것이 아니라 상위 작업이 끝났기 때문이다.
        assertThat(locks.findAll().getFirst().getStatus()).isEqualTo(MaterialLockStatus.RELEASED);
    }

    @Test
    void completesSignedWorkflowConsumesInputAndCreatesBrandOwnedChildMaterial() throws Exception {
        String brandWallet = Credentials.create(KEY_PAIR).getAddress();
        String processorWallet = Credentials.create(OTHER_KEY_PAIR).getAddress();
        linkWallets(brandWallet, processorWallet);
        long jobId = createJob(300);
        payJob(jobId);
        // 수락은 온체인 등록을 전제로 한다 — 먼저 체인에 올린 뒤에야 받을 수 있다.
        linkChainJob(jobId);
        acceptJob(jobId);
        mvc.perform(patch("/processing-jobs/{id}/ship", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ\",\"trackingNumber\":\"1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("SHIPPED"));
        mvc.perform(patch("/processing-jobs/{id}/receive", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(receiveBody(jobId)))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("RECEIVED"));
        ProcessingInput custodyInput = inputs.findByProcessingJobId(jobId).orElseThrow();
        assertThat(custodyInput.getBrandMaterialLockId()).isNotEqualTo(custodyInput.getProcessorMaterialLockId());
        assertThat(locks.findById(custodyInput.getBrandMaterialLockId()).orElseThrow().getStatus())
                .isEqualTo(MaterialLockStatus.CONSUMED);
        assertThat(locks.findById(custodyInput.getProcessorMaterialLockId()).orElseThrow())
                .satisfies(lock -> {
                    assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
                    assertThat(lock.getOwnerId()).isEqualTo(processorId);
                    assertThat(lock.getAmount()).isEqualTo(300);
                });
        mvc.perform(patch("/processing-jobs/{id}/start", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("PROCESSING"));
        mvc.perform(post("/processing-jobs/{id}/results", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossQuantity\":100,\"outputs\":[{\"materialType\":\"DENIM\",\"grade\":\"A\",\"gradeReason\":\"오염과 손상이 없음\",\"name\":\"재생 데님\",\"quantity\":200}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.resultRevision").value(1));
        evidenceFiles.save(EvidenceFile.record(EvidenceReferenceType.PROCESSING_JOB, jobId,
                "processing/result.jpg", "0x" + "a".repeat(64), EvidenceType.AFTER_PHOTO, processorId));

        var submitChallenge = mvc.perform(post("/processing-jobs/{id}/signature-challenge", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RESULT_SUBMIT\"}"))
                .andExpect(status().isOk()).andReturn();
        var submitData = mapper.readTree(submitChallenge.getResponse().getContentAsString()).path("data");
        mvc.perform(post("/processing-jobs/{id}/results/submit", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "challengeId", submitData.path("challengeId").asLong(),
                                "signature", sign(submitData.path("message").asText(), OTHER_KEY_PAIR)))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("RESULT_SUBMITTED"));

        mvc.perform(patch("/processing-jobs/{id}/return/ship", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(returnShipBody(jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("RETURN_SHIPPED"))
                .andExpect(jsonPath("data.returnShippingEvidenceHash").value("0x" + "a".repeat(64)));
        mvc.perform(post("/processing-jobs/{id}/return/receive", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(returnReceiveBody(jobId, 9001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("CONFIRMED"))
                .andExpect(jsonPath("data.outputs[0].materialId").isNumber());

        assertThat(balances.findByMaterialIdAndOwnerId(materialId, brandId).orElseThrow().getQuantity())
                .isEqualTo(700);
        assertThat(locks.findAll().getFirst().getStatus()).isEqualTo(MaterialLockStatus.CONSUMED);
        var output = outputs.findAllByProcessingJobIdOrderById(jobId).getFirst();
        assertThat(output.getOutputMaterialId()).isNotNull();
        assertThat(balances.findByMaterialIdAndOwnerId(output.getOutputMaterialId(), brandId).orElseThrow().getQuantity())
                .isEqualTo(200);
        assertThat(lineages.findAllByChildMaterialId(output.getOutputMaterialId())).singleElement()
                .satisfies(link -> assertThat(link.getParentMaterialId()).isEqualTo(materialId));
        assertThat(jdbc.queryForList(
                "select type from user_notification where recipient_id=? order by id",
                String.class,
                processorId))
                .containsExactly(
                        "PROCESSING_REQUEST_RECEIVED",
                        "PROCESSING_PAYMENT_COMPLETED",
                        "PROCESSING_MATERIAL_SHIPPED",
                        "PROCESSING_RESULT_APPROVED");
        assertThat(jdbc.queryForList(
                "select type from user_notification where recipient_id=? order by id",
                String.class,
                brandId))
                .containsExactly(
                        "PROCESSING_JOB_ACCEPTED",
                        "PROCESSING_MATERIAL_RECEIVED",
                        "PROCESSING_STARTED",
                        "PROCESSING_RESULT_SUBMITTED");
    }

    @Test
    void unpaidJobExpiresAfterDeadlineAndReleasesItsLock() throws Exception {
        linkWallets("0x" + "3".repeat(40), "0x" + "4".repeat(40));
        long jobId = createJob(250);
        jdbc.update("update processing_job set payment_expires_at=? where id=?", java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusSeconds(1)), jobId);
        jdbc.update("update material_lock set expires_at=? where reference_type='PROCESSING_JOB' and reference_id=?",
                java.sql.Timestamp.valueOf(LocalDateTime.now(clock).minusSeconds(1)), jobId);

        jobService.expire(jobId);

        assertThat(jdbc.queryForObject("select status from processing_job where id=?",String.class,jobId)).isEqualTo("EXPIRED");
        assertThat(locks.findAll().getFirst().getStatus()).isEqualTo(MaterialLockStatus.EXPIRED);
    }
    @Test
    void confirmedProcessingResultCreatesChildMaterialAndLineageAtomically() throws Exception {
        long jobId = startProcessingJob(300);

        mvc.perform(post("/processing-jobs/{id}/result", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outputs": [
                                    {"materialType":"COTTON","name":"재생 면사","grade":"A","gradeReason":"오염과 손상이 없음","quantity":200},
                                    {"materialType":"DENIM","name":"재생 데님 조각","grade":"B","gradeReason":"표면에 경미한 오염이 남아 있음","quantity":50}
                                  ],
                                  "evidenceHash":"0x%s",
                                  "manifestUrl":"https://example.com/processing/manifest.json"
                                }
                                """.formatted("a".repeat(64))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("RESULT_SUBMITTED"))
                .andExpect(jsonPath("data.lossQuantity").value(50))
                .andExpect(jsonPath("data.processorSubmittedAt").isNotEmpty());

        assertThat(jdbc.queryForObject(
                "select count(*) from material_lineage", Long.class)).isZero();

        mvc.perform(patch("/processing-jobs/{id}/return/ship", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(returnShipBody(jobId)))
                .andExpect(status().isOk());
        mvc.perform(post("/processing-jobs/{id}/return/receive", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(returnReceiveBody(jobId, 9101, 9102)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("CONFIRMED"))
                .andExpect(jsonPath("data.brandReviewedAt").isNotEmpty())
                .andExpect(jsonPath("data.completedAt").isNotEmpty());

        List<Long> childIds = jdbc.queryForList(
                "select output_material_id from processing_output where processing_job_id=? order by id",
                Long.class, jobId);
        assertThat(childIds).hasSize(2).doesNotContainNull();
        assertThat(materials.findAllById(childIds))
                .allSatisfy(child -> {
                    assertThat(child.getGenerationType()).isEqualTo(MaterialGenerationType.PROCESSED);
                    assertThat(child.getRootOrigin()).isEqualTo(MaterialRootOrigin.WASTE);
                    assertThat(child.getRegistrantId()).isEqualTo(processorId);
                });
        assertThat(jdbc.queryForList(
                "select parent_material_id from material_lineage where reference_id=? order by id",
                Long.class, jobId)).containsExactly(materialId, materialId);
        assertThat(balances.findByMaterialIdAndOwnerId(materialId, brandId).orElseThrow().getQuantity())
                .isEqualTo(700);
        assertThat(childIds)
                .allSatisfy(childId -> assertThat(
                        balances.findByMaterialIdAndOwnerId(childId, brandId).orElseThrow().getQuantity())
                        .isPositive());
        assertThat(childIds)
                .allSatisfy(childId -> assertThat(verificationStates.resolve(childId))
                        .isEqualTo(MaterialVerificationState.PROCESSING_VERIFIED));
        assertThat(locks.findAll().getFirst().getStatus()).isEqualTo(MaterialLockStatus.CONSUMED);
    }

    @Test
    void processorBrowseReturnsEveryCompletelyRegisteredProcessorWithoutFilters() throws Exception {
        // 승인받지 않은 업체는 목록에 없다 — company_profile 이 없으므로 조인에서 빠진다.
        Long incomplete = users.save(User.register("incomplete@test.com", "hash", "incomplete", "010", UserRole.PROCESSOR)).getId();
        jdbc.update("insert into processor_profile(user_id,region) values (?,'Busan')", incomplete);
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,daily_capacity,price_per_kg) values (?,'DENIM','CLEANING',1000,500)", incomplete);

        mvc.perform(get("/api/v1/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("page", "0").param("size", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.total").value(1))
                .andExpect(jsonPath("data.items[0].processorId").value(processorId))
                .andExpect(jsonPath("data.items[0].dailyCapacity").value(1000))
                .andExpect(jsonPath("data.items[0].pricePerKg").value(1000))
                // 수량을 주지 않았으니 예상 금액도 기간도 없다. 0 으로 채우면 "공짜" 가 된다.
                .andExpect(jsonPath("data.items[0].estimatedPrice").doesNotExist())
                .andExpect(jsonPath("data.items[0].estimatedDays").doesNotExist())
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes").isEmpty());
    }

    @Test
    void invalidOrUnapprovedResultNeverCreatesLineage() throws Exception {
        long jobId = startProcessingJob(300);

        mvc.perform(post("/processing-jobs/{id}/result", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outputs":[
                                  {"materialType":"DENIM","name":"과다 산출","grade":"A","gradeReason":"오염과 손상이 없음","quantity":301}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PROCESSING_OUTPUT_INVALID"));

        assertThat(jdbc.queryForObject(
                "select status from processing_job where id=?", String.class, jobId))
                .isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject(
                "select count(*) from processing_output where processing_job_id=?", Long.class, jobId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from material_lineage", Long.class)).isZero();

        mvc.perform(post("/processing-jobs/{id}/return/receive", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(returnReceiveBody(jobId, 9991)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PROCESSING_RESULT_INVALID"));
        assertThat(jdbc.queryForObject("select count(*) from material_lineage", Long.class)).isZero();
    }

    @Test
    void resultDraftCalculatesLossFromInputAndOutputs() throws Exception {
        long jobId = startProcessingJob(300);

        mvc.perform(post("/processing-jobs/{id}/results", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outputs":[
                                  {"materialType":"COTTON","name":"재생 면사","grade":"A","gradeReason":"오염과 손상이 없음","quantity":200},
                                  {"materialType":"DENIM","name":"재생 데님 조각","grade":"B","gradeReason":"표면에 경미한 오염이 남아 있음","quantity":50}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.lossQuantity").value(50))
                .andExpect(jsonPath("data.resultRevision").value(1))
                .andExpect(jsonPath("data.outputs[1].gradeReason")
                        .value("표면에 경미한 오염이 남아 있음"));

        mvc.perform(post("/processing-jobs/{id}/results", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outputs":[
                                  {"materialType":"DENIM","name":"사유 없는 산출물","grade":"A","gradeReason":"","quantity":200}
                                ]}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "select loss_quantity from processing_job where id=?", Long.class, jobId))
                .isEqualTo(50L);

        mvc.perform(post("/processing-jobs/{id}/results", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lossQuantity":999,"outputs":[
                                  {"materialType":"COTTON","name":"재생 면사","grade":"A","gradeReason":"오염과 손상이 없음","quantity":240}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.lossQuantity").value(60))
                .andExpect(jsonPath("data.resultRevision").value(2));

        mvc.perform(post("/processing-jobs/{id}/results", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lossQuantity":999,"outputs":[
                                  {"materialType":"DENIM","name":"과다 산출","grade":"A","gradeReason":"오염과 손상이 없음","quantity":301}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("PROCESSING_RESULT_INVALID"));

        assertThat(jdbc.queryForObject(
                "select loss_quantity from processing_job where id=?", Long.class, jobId))
                .isEqualTo(60L);
    }

    @Test
    void rejectedJobCanReturnLockedInputToBrandAndClose() throws Exception {
        long jobId = startProcessingJob(300);
        jdbc.update("update processing_job set status='REJECTED' where id=?", jobId);
        long evidenceId = evidenceFiles.save(EvidenceFile.record(EvidenceReferenceType.PROCESSING_JOB, jobId,
                "processing/rejected-return.jpg", "0x" + "b".repeat(64), EvidenceType.INVOICE,
                processorId)).getId();

        mvc.perform(post("/processing-jobs/{id}/return/rejected", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ\",\"trackingNumber\":\"RETURN\",\"evidenceFileId\":"
                                + evidenceId + ",\"txHash\":\"" + chainHash(jobId, 6) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("CANCELED"));

        ProcessingInput input = inputs.findByProcessingJobId(jobId).orElseThrow();
        assertThat(locks.findById(input.getProcessorMaterialLockId()).orElseThrow().getStatus())
                .isEqualTo(MaterialLockStatus.CONSUMED);
        assertThat(balances.findByMaterialIdAndOwnerId(materialId, processorId).orElseThrow().getQuantity())
                .isZero();
        assertThat(balances.findByMaterialIdAndOwnerId(materialId, brandId).orElseThrow().getQuantity())
                .isEqualTo(1000);
    }

    @Test
    void processorSeesShippedInputAsCustodyWithoutTokenOwnershipTransfer() throws Exception {
        long jobId = createPaidProcessingJob(300);

        mvc.perform(get("/processing-jobs/custody-materials")
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data").isEmpty());
        mvc.perform(get("/processing-jobs/custody-materials")
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/processing-jobs/{id}/ship", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ\",\"trackingNumber\":\"1234\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/processing-jobs/custody-materials")
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data[0].jobId").value(jobId))
                .andExpect(jsonPath("data[0].materialId").value(materialId))
                .andExpect(jsonPath("data[0].materialCode").value("M1"))
                .andExpect(jsonPath("data[0].materialName").value("denim"))
                .andExpect(jsonPath("data[0].materialType").value("DENIM"))
                .andExpect(jsonPath("data[0].quantity").value(300))
                .andExpect(jsonPath("data[0].status").value("SHIPPED"));

        assertThat(balances.findByMaterialIdAndOwnerId(materialId, brandId).orElseThrow().getQuantity())
                .isEqualTo(1000);
        assertThat(balances.findByMaterialIdAndOwnerId(materialId, processorId)).isEmpty();
    }

    private long startProcessingJob(long amount) throws Exception {
        long jobId = createPaidProcessingJob(amount);
        mvc.perform(post("/processing-jobs/{id}/shipment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("SHIPPED"));
        mvc.perform(patch("/processing-jobs/{id}/receive", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(receiveBody(jobId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("RECEIVED"));
        mvc.perform(post("/processing-jobs/{id}/start", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("PROCESSING"));
        return jobId;
    }

    /**
     * 요청 → 결제 → 가공사 수락까지. 견적 왕복이 사라져 세 걸음이 됐다.
     *
     * <p>수락까지 와야 발송이 열린다 — 결제만으로 보내면 가공사가 받지도 않은 일에
     * 소재가 먼저 도착한다.
     */
    private long createPaidProcessingJob(long amount) throws Exception {
        linkWallets("0x" + "5".repeat(40), "0x" + "6".repeat(40));
        long jobId = createJob(amount);
        payJob(jobId);
        // 수락은 온체인 등록을 전제로 한다 — 먼저 체인에 올린 뒤에야 받을 수 있다.
        linkChainJob(jobId);
        acceptJob(jobId);
        return jobId;
    }

    /** 요청이 곧 작업을 만든다. 응답에서 jobId 를 바로 받는다. */
    private long createJob(long amount) throws Exception {
        var created = mvc.perform(request("/processing-requests", brandId, "BRAND", body("" + amount)))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("jobId").asLong();
    }

    private void payJob(long jobId) throws Exception {
        mvc.perform(post("/processing-jobs/{id}/payment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk());
        mvc.perform(post("/processing-jobs/{id}/payment/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgToken\":\"mock-token\"}"))
                .andExpect(status().isOk());
    }

    private void acceptJob(long jobId) throws Exception {
        mvc.perform(patch("/processing-jobs/{id}/accept", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_PROCESSOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("ACCEPTED"));
    }

    /**
     * 이 작업의 각 단계에서 체인이 무엇을 답할지 미리 정해 둔다.
     *
     * <p>서버가 해시를 체인에서 확인한 뒤에만 기록하는데, 가공은 <b>단계마다 서명자가
     * 바뀐다</b> — 작업 생성·반송 수령은 브랜드, 수령·반송 발송은 가공사다. 그래서 해시별로
     * 답을 달리 둔다 ({@code chainHash(jobId, step)} 가 단계마다 다른 해시를 만든다).
     *
     * <p><b>지갑은 그때그때 DB 에서 읽는다.</b> 테스트마다 {@code linkWallets} 에 다른 값을
     * 넘기므로(고정 문자열·{@code KEY_PAIR} 서명 지갑 등) 하나로 고정하면 대부분의 테스트가
     * "서명자 불일치" 로 거절된다 — 실제로 5건이 이렇게 깨졌었다.
     */
    private void chainReceiptsFor(long jobId) {
        String brand = wallets.findByUserIdAndActiveTrue(brandId).orElseThrow().getWalletAddress();
        String processor = wallets.findByUserIdAndActiveTrue(processorId).orElseThrow().getWalletAddress();
        chain.expectFor(chainHash(jobId, 1), PROCESSING_REGISTRY, brand);
        chain.expectFor(chainHash(jobId, 2), PROCESSING_REGISTRY, processor);
        chain.expectFor(chainHash(jobId, 3), PROCESSING_REGISTRY, processor);
        chain.expectFor(chainHash(jobId, 4), PROCESSING_REGISTRY, brand);
        chain.expectFor(chainHash(jobId, 5), PROCESSING_REGISTRY, brand);
        chain.expectFor(chainHash(jobId, 6), PROCESSING_REGISTRY, processor);
    }

    private void linkChainJob(long jobId) throws Exception {
        chainReceiptsFor(jobId);
        mvc.perform(post("/processing-jobs/{id}/chain", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onChainId\":" + jobId + ",\"chainLockId\":" + (1000 + jobId)
                                + ",\"txHash\":\"" + chainHash(jobId, 1) + "\",\"blockNumber\":1}"))
                .andExpect(status().isOk());
    }

    private String receiveBody(long jobId) {
        return "{\"txHash\":\"" + chainHash(jobId, 2) + "\"}";
    }

    private String returnShipBody(long jobId) {
        long evidenceId = evidenceFiles.save(EvidenceFile.record(EvidenceReferenceType.PROCESSING_JOB, jobId,
                "processing/return-" + jobId + ".jpg", "0x" + "a".repeat(64),
                EvidenceType.INVOICE, processorId)).getId();
        return "{\"carrier\":\"CJ\",\"trackingNumber\":\"RETURN-" + jobId
                + "\",\"evidenceFileId\":" + evidenceId
                + ",\"txHash\":\"" + chainHash(jobId, 3) + "\"}";
    }

    private String returnReceiveBody(long jobId, long... tokenIds) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("txHash", chainHash(jobId, 4)));
    }

    private String chainHash(long jobId, int step) {
        return "0x" + String.format("%064x", jobId * 10 + step);
    }

    private void assertNotification(
            String type, Long recipientId, String referenceType, Long referenceId, String targetPath) {
        assertThat(jdbc.queryForMap(
                "select recipient_id, type, reference_type, reference_id, target_path "
                        + "from user_notification where type=? and reference_id=?",
                type,
                referenceId))
                .containsEntry("recipient_id", recipientId)
                .containsEntry("type", type)
                .containsEntry("reference_type", referenceType)
                .containsEntry("reference_id", referenceId)
                .containsEntry("target_path", targetPath);
    }
}
