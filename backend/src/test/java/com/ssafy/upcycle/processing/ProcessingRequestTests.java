package com.ssafy.upcycle.processing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.material.entity.*;
import com.ssafy.upcycle.material.repository.*;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
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

@SpringBootTest(properties = {"app.processing.expiry.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:processing-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ProcessingRequestTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired MaterialRepository materials;
    @Autowired MaterialBalanceRepository balances;
    @Autowired MaterialLockRepository locks;
    @Autowired com.ssafy.upcycle.processing.service.ProcessingRequestService service;
    @Autowired com.ssafy.upcycle.processing.repository.ProcessingRequestRepository requests;
    @Autowired java.time.Clock clock;
    Long brandId;
    Long processorId;
    Long materialId;

    @BeforeEach
    void fixture() {
        // Real persisted fixtures: missing reservation writes and transaction errors are observable.
        for (String table : List.of("material_lock", "processing_request_type", "processing_request_input",
                "processing_request", "processor_capability", "processor_lead_time", "processor_profile", "material_balance", "material", "users")) {
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

    String body(String quantity) {
        return "{\"materialId\":" + materialId + ",\"processorId\":" + processorId
                + ",\"quantity\":" + quantity + ",\"processTypes\":[\"CLEANING\",\"CUTTING\"]}";
    }

    MockHttpServletRequestBuilder request(String path, Long user, String role, String body) {
        return post(path).with(jwt().jwt(j -> j.subject(user.toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                .contentType(MediaType.APPLICATION_JSON).content(body);
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
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType", "DENIM").param("quantity", "500")
                        .param("processTypes", "CLEANING", "CUTTING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.items[0].processorId").value(processorId))
                .andExpect(jsonPath("data.items[0].minQuantity").value(100))
                .andExpect(jsonPath("data.items[0].maxQuantity").value(900))
                .andExpect(jsonPath("data.items[0].region").value("Seoul"));
    }

    @Test
    void searchUsesTheSmallestCoveringWeightBandAndRejectsPartialMatches() throws Exception {
        jdbc.update("insert into processor_lead_time(processor_profile_id,max_quantity,lead_days) values (?,100,2),(?,1000,5)", processorId, processorId);
        search("100").andExpect(jsonPath("data.items[0].estimatedLeadDays").value(2));
        search("101").andExpect(jsonPath("data.items[0].estimatedLeadDays").value(5));
        search("1000").andExpect(jsonPath("data.items[0].estimatedLeadDays").value(5));
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
    void searchPaginationIsStableAndMissingLeadTimeIsNotInvented() throws Exception {
        Long second=users.save(User.register("second@test.com","hash","second","010",UserRole.PROCESSOR)).getId();
        jdbc.update("insert into processor_profile(user_id,region) values (?,'Busan')",second);
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,min_quantity,max_quantity) values (?,'DENIM','CLEANING',1,1000),(?,'DENIM','CUTTING',1,1000)",second,second);
        mvc.perform(get("/processors/search").with(jwt().jwt(j -> j.subject(brandId.toString())))
                        .param("materialType","DENIM").param("quantity","100").param("processTypes","CLEANING,CUTTING")
                        .param("page","1").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(2))
                .andExpect(jsonPath("data.items.length()").value(1))
                .andExpect(jsonPath("data.items[0].processorId").value(second))
                .andExpect(jsonPath("data.items[0].estimatedLeadDays").value(org.hamcrest.Matchers.nullValue()));
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
}
