package com.ssafy.upcycle.processing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static com.ssafy.upcycle.user.WalletTestSignatures.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.evidence.entity.*;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.processing.repository.ProcessingOutputRepository;
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
import org.web3j.crypto.Credentials;

@SpringBootTest(properties = {"app.processing.expiry.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:processing-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ProcessingRequestTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired UserWalletRepository wallets;
    @Autowired MaterialRepository materials;
    @Autowired MaterialBalanceRepository balances;
    @Autowired MaterialLockRepository locks;
    @Autowired MaterialEventRepository events;
    @Autowired MaterialLineageRepository lineages;
    @Autowired ProcessingOutputRepository outputs;
    @Autowired EvidenceFileRepository evidenceFiles;
    @Autowired com.ssafy.upcycle.processing.service.ProcessingRequestService service;
    @Autowired com.ssafy.upcycle.processing.service.ProcessingJobService jobService;
    @Autowired com.ssafy.upcycle.processing.repository.ProcessingRequestRepository requests;
    @Autowired java.time.Clock clock;
    @Autowired com.ssafy.upcycle.material.service.MaterialVerificationStateResolver verificationStates;
    Long brandId;
    Long processorId;
    Long materialId;

    @BeforeEach
    void fixture() {
        // Real persisted fixtures: missing reservation writes and transaction errors are observable.
        for (String table : List.of("processing_signature_challenge", "evidence_file", "material_lineage", "processing_output",
                "material_event", "processing_input", "processing_job", "processing_quote", "material_lock", "processing_request_type", "processing_request_input",
                "processing_request", "processor_capability", "processor_lead_time", "processor_profile", "material_balance", "material", "user_wallet", "users")) {
            jdbc.update("delete from " + table);
        }
        brandId = users.save(User.register("brand@test.com", "hash", "brand", "010", UserRole.BRAND)).getId();
        processorId = users.save(User.register("processor@test.com", "hash", "processor", "010", UserRole.PROCESSOR)).getId();
        materialId = materials.save(Material.register("M1", MaterialType.DENIM, MaterialRootOrigin.WASTE,
                "denim", null, "A", 1000, null, null, brandId)).getId();
        balances.save(MaterialBalance.of(materialId, brandId, "0x" + "a".repeat(40), 1000));
        jdbc.update("insert into processor_profile(user_id, region) values (?, 'Seoul')", processorId);
        capability("DENIM", "CLEANING", 1, 1000);
        capability("DENIM", "CUTTING", 1, 1000);
    }

    void capability(String material, String purpose, long min, long max) {
        jdbc.update("insert into processor_capability(processor_profile_id, material_type, process_type, min_quantity, max_quantity) values (?, ?, ?, ?, ?)",
                processorId, material, purpose, min, max);
    }

    void leadTime(Long id, String material, String process, long max, int days) {
        jdbc.update("insert into processor_lead_time(processor_profile_id, material_type, process_type, max_quantity, lead_days) values (?, ?, ?, ?, ?)",
                id, material, process, max, days);
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
    void createsRequestAndLockWithoutDecreasingBalanceAndExpiresAfterSevenDays() throws Exception {
        long id = create(700);
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, id)).isEqualTo("QUOTING");
        var dates = jdbc.queryForMap("select created_at, expires_at from processing_request where id=?", id);
        assertThat(((java.sql.Timestamp) dates.get("expires_at")).toLocalDateTime())
                .isEqualTo(((java.sql.Timestamp) dates.get("created_at")).toLocalDateTime().plusDays(7));
        assertThat(locks.findAll()).singleElement().satisfies(lock -> {
            assertThat(lock.getAmount()).isEqualTo(700);
            assertThat(lock.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
            assertThat(lock.getReferenceType().name()).isEqualTo("PROCESSING_REQUEST");
            assertThat(lock.getReferenceId()).isEqualTo(id);
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
    void requiresEveryPurposeAndMatchingMaterialAndInclusiveQuantityRange() throws Exception {
        jdbc.update("delete from processor_capability where process_type='CUTTING'");
        capability("COTTON", "CUTTING", 1, 1000);
        mvc.perform(request("/processing-requests", brandId, "BRAND", body("100")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSING_CAPABILITY_MISMATCH"));
        capability("DENIM", "CUTTING", 100, 200);
        for (String amount : List.of("99", "201")) {
            mvc.perform(request("/processing-requests", brandId, "BRAND", body(amount))).andExpect(status().isBadRequest());
        }
        create(100);
        create(200);
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
        jdbc.update("update material_lock set expires_at=? where reference_id=?", past, due);
        var worker = new com.ssafy.upcycle.processing.service.ProcessingRequestExpiryWorker(requests, service, clock);
        worker.expireDueRequests();
        worker.expireDueRequests();
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, due)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from material_lock where reference_id=?", String.class, due)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from processing_request where id=?", String.class, future)).isEqualTo("QUOTING");
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
    void attachingToJobKeepsTheSameReservationAndRemovesQuoteDeadline() throws Exception {
        long id = create(700);
        var lock = locks.findAll().getFirst();
        Long lockId = lock.getId();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lock.attachToProcessingJob(id + 1, 999L))
                .isInstanceOf(IllegalArgumentException.class);
        lock.attachToProcessingJob(id, 999L);
        locks.save(lock);
        var persisted = locks.findById(lockId).orElseThrow();
        assertThat(persisted.getAmount()).isEqualTo(700);
        assertThat(persisted.getStatus()).isEqualTo(MaterialLockStatus.ACTIVE);
        assertThat(persisted.getReferenceType()).isEqualTo(MaterialLockReferenceType.PROCESSING_JOB);
        assertThat(persisted.getReferenceId()).isEqualTo(999);
        assertThat(persisted.getExpiresAt()).isNull();
    }

    @Test
    void processorSearchMatchesEveryPurposeAndReturnsCombinedQuantityBounds() throws Exception {
        jdbc.update("update processor_capability set min_quantity=100,max_quantity=900 where process_type='CUTTING'");
        leadTime(processorId, "DENIM", "CLEANING", 1000, 5);
        leadTime(processorId, "DENIM", "CUTTING", 1000, 7);
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType", "DENIM").param("quantity", "500")
                        .param("processTypes", "CLEANING", "CUTTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.items[0].processorId").value(processorId))
                .andExpect(jsonPath("data.items[0].minQuantity").value(100))
                .andExpect(jsonPath("data.items[0].maxQuantity").value(900))
                .andExpect(jsonPath("data.items[0].region").value("Seoul"))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].processType").value("CLEANING"))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].leadDays").value(5))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].processType").value("CUTTING"))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].leadDays").value(7));
    }

    @Test
    void searchUsesTheSmallestCoveringWeightBandAndRejectsPartialMatches() throws Exception {
        leadTime(processorId, "DENIM", "CLEANING", 100, 2);
        leadTime(processorId, "DENIM", "CLEANING", 1000, 5);
        leadTime(processorId, "DENIM", "CUTTING", 100, 3);
        leadTime(processorId, "DENIM", "CUTTING", 1000, 6);
        search("100").andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].leadDays").value(2))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].leadDays").value(3));
        search("101").andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].leadDays").value(5))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].leadDays").value(6));
        search("1000").andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].leadDays").value(5));
        search("1001").andExpect(jsonPath("data.items").isEmpty());
        jdbc.update("delete from processor_capability where process_type='CUTTING'");
        search("100").andExpect(jsonPath("data.items").isEmpty()).andExpect(jsonPath("data.total").value(0));
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
        jdbc.update("insert into processor_profile(user_id,region) values (?,'Busan')",second);
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,min_quantity,max_quantity) values (?,'DENIM','CLEANING',1,1000),(?,'DENIM','CUTTING',1,1000)",second,second);
        for (Long id : List.of(processorId, second)) {
            leadTime(id, "DENIM", "CLEANING", 1000, id.equals(processorId) ? 3 : 4);
            leadTime(id, "DENIM", "CUTTING", 1000, id.equals(processorId) ? 5 : 6);
        }
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType","DENIM").param("quantity","100").param("processTypes","CLEANING,CUTTING")
                        .param("page","1").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(2))
                .andExpect(jsonPath("data.items.length()").value(1))
                .andExpect(jsonPath("data.items[0].processorId").value(second))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[0].leadDays").value(4))
                .andExpect(jsonPath("data.items[0].estimatedLeadTimes[1].leadDays").value(6));
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
                .andExpect(jsonPath("data.items[0].status").value("QUOTING"))
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
    void quoteSelectionPaymentAndPartyScopedDetailsKeepTheSameLock() throws Exception {
        wallets.save(UserWallet.link(brandId, "0x" + "1".repeat(40), 31337));
        wallets.save(UserWallet.link(processorId, "0x" + "2".repeat(40), 31337));
        long requestId = create(300);

        mvc.perform(post("/processing-requests/{id}/quotes", requestId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":120000,\"leadDays\":5,\"memo\":\"washing included\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.price").value(120000))
                .andExpect(jsonPath("data.status").value("SUBMITTED"));
        long quoteId = jdbc.queryForObject("select id from processing_quote where processing_request_id=?", Long.class, requestId);

        mvc.perform(get("/processing-requests/{id}", requestId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.quantity").value(300));
        Long stranger = users.save(User.register("stranger@test.com", "hash", "stranger", "010", UserRole.BRAND)).getId();
        mvc.perform(get("/processing-requests/{id}", requestId)
                        .with(jwt().jwt(j -> j.subject(stranger.toString())))).andExpect(status().isForbidden());

        mvc.perform(get("/processing-requests/{id}/quotes", requestId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data[0].quoteId").value(quoteId));

        var selection = mvc.perform(post("/quotes/{id}/select", quoteId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"MOCK\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("data.status").value("CREATED")).andReturn();
        long jobId = mapper.readTree(selection.getResponse().getContentAsString()).path("data").path("jobId").asLong();
        var timestamps = jdbc.queryForMap("select created_at,payment_expires_at from processing_job where id=?", jobId);
        assertThat(((java.sql.Timestamp) timestamps.get("payment_expires_at")).toLocalDateTime())
                .isEqualTo(((java.sql.Timestamp) timestamps.get("created_at")).toLocalDateTime().plusMinutes(15));
        MaterialLock selectedLock = locks.findAll().getFirst();
        assertThat(selectedLock.getReferenceType()).isEqualTo(MaterialLockReferenceType.PROCESSING_JOB);
        assertThat(selectedLock.getReferenceId()).isEqualTo(jobId);
        assertThat(selectedLock.getExpiresAt()).isNotNull();

        mvc.perform(get("/processing-jobs/{id}", jobId).with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.fee").value(120000));
        mvc.perform(get("/processing-jobs/{id}", jobId).with(jwt().jwt(j -> j.subject(stranger.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/processing-jobs/{id}/payment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.amount").value(120000))
                .andExpect(jsonPath("data.live").value(false));
        mvc.perform(post("/processing-jobs/{id}/payment/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"mock-token\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("PAID"));
        assertThat(locks.findAll().getFirst().getExpiresAt()).isNull();
        assertThat(events.findAllByMaterialIdOrderByOccurredAtAscIdAsc(
                        materialId, org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(event -> event.getEventType())
                .containsExactly(
                        MaterialEventType.PROCESSING_REQUESTED,
                        MaterialEventType.PROCESSING_QUOTE_SUBMITTED,
                        MaterialEventType.PROCESSING_QUOTE_SELECTED,
                        MaterialEventType.PROCESSING_JOB_PAID);
    }

    @Test
    void completesSignedWorkflowConsumesInputAndCreatesBrandOwnedChildMaterial() throws Exception {
        String brandWallet = Credentials.create(KEY_PAIR).getAddress();
        String processorWallet = Credentials.create(OTHER_KEY_PAIR).getAddress();
        wallets.save(UserWallet.link(brandId, brandWallet, 31337));
        wallets.save(UserWallet.link(processorId, processorWallet, 31337));
        long requestId = create(300);
        mvc.perform(post("/processing-requests/{id}/quotes", requestId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":10000,\"leadDays\":2}"))
                .andExpect(status().isCreated());
        long quoteId = jdbc.queryForObject("select id from processing_quote where processing_request_id=?",
                Long.class, requestId);
        var selected = mvc.perform(post("/quotes/{id}/select", quoteId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"MOCK\"}"))
                .andExpect(status().isCreated()).andReturn();
        long jobId = mapper.readTree(selected.getResponse().getContentAsString()).path("data").path("jobId").asLong();

        mvc.perform(post("/processing-jobs/{id}/payment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk());
        mvc.perform(post("/processing-jobs/{id}/payment/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"pgToken\":\"mock-token\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/processing-jobs/{id}/ship", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"CJ\",\"trackingNumber\":\"1234\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("SHIPPED"));
        mvc.perform(patch("/processing-jobs/{id}/receive", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("RECEIVED"));
        mvc.perform(patch("/processing-jobs/{id}/start", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("PROCESSING"));
        mvc.perform(post("/processing-jobs/{id}/results", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lossQuantity\":100,\"outputs\":[{\"materialType\":\"DENIM\",\"grade\":\"A\",\"name\":\"재생 데님\",\"quantity\":200}]}"))
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

        var approvalChallenge = mvc.perform(post("/processing-jobs/{id}/signature-challenge", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isOk()).andReturn();
        var approvalData = mapper.readTree(approvalChallenge.getResponse().getContentAsString()).path("data");
        mvc.perform(post("/processing-jobs/{id}/approve", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of(
                                "challengeId", approvalData.path("challengeId").asLong(),
                                "signature", sign(approvalData.path("message").asText(), KEY_PAIR)))))
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
    }

    @Test
    void unpaidJobExpiresAfterDeadlineAndReleasesItsLock() throws Exception {
        wallets.save(UserWallet.link(brandId, "0x" + "3".repeat(40), 31337));
        wallets.save(UserWallet.link(processorId, "0x" + "4".repeat(40), 31337));
        long requestId=create(250);
        mvc.perform(post("/processing-requests/{id}/quotes", requestId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":50000,\"leadDays\":3}"))
                .andExpect(status().isCreated());
        long quoteId=jdbc.queryForObject("select id from processing_quote where processing_request_id=?",Long.class,requestId);
        var selected=mvc.perform(post("/quotes/{id}/select",quoteId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"paymentMethod\":\"MOCK\"}"))
                .andExpect(status().isCreated()).andReturn();
        long jobId=mapper.readTree(selected.getResponse().getContentAsString()).path("data").path("jobId").asLong();
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
                                    {"materialType":"COTTON","name":"재생 면사","grade":"A","quantity":200},
                                    {"materialType":"DENIM","name":"재생 데님 조각","grade":"B","quantity":50}
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

        mvc.perform(post("/processing-jobs/{id}/result/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
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
    void invalidOrUnapprovedResultNeverCreatesLineage() throws Exception {
        long jobId = startProcessingJob(300);

        mvc.perform(post("/processing-jobs/{id}/result", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"outputs":[
                                  {"materialType":"DENIM","name":"과다 산출","grade":"A","quantity":301}
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

        mvc.perform(post("/processing-jobs/{id}/result/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("PROCESSING_INVALID_STATE"));
        assertThat(jdbc.queryForObject("select count(*) from material_lineage", Long.class)).isZero();
    }

    private long startProcessingJob(long amount) throws Exception {
        wallets.save(UserWallet.link(brandId, "0x" + "5".repeat(40), 31337));
        wallets.save(UserWallet.link(processorId, "0x" + "6".repeat(40), 31337));
        long requestId = create(amount);
        mvc.perform(post("/processing-requests/{id}/quotes", requestId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":50000,\"leadDays\":3}"))
                .andExpect(status().isCreated());
        long quoteId = jdbc.queryForObject(
                "select id from processing_quote where processing_request_id=?", Long.class, requestId);
        var selected = mvc.perform(post("/quotes/{id}/select", quoteId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"MOCK\"}"))
                .andExpect(status().isCreated()).andReturn();
        long jobId = mapper.readTree(selected.getResponse().getContentAsString())
                .path("data").path("jobId").asLong();
        mvc.perform(post("/processing-jobs/{id}/payment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk());
        mvc.perform(post("/processing-jobs/{id}/payment/confirm", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pgToken\":\"mock-token\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/processing-jobs/{id}/shipment", jobId)
                        .with(jwt().jwt(j -> j.subject(brandId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("SHIPPED"));
        mvc.perform(post("/processing-jobs/{id}/receipt", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("RECEIVED"));
        mvc.perform(post("/processing-jobs/{id}/start", jobId)
                        .with(jwt().jwt(j -> j.subject(processorId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.status").value("PROCESSING"));
        return jobId;
    }
}
