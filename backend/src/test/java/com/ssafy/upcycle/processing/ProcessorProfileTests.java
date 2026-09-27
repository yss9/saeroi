package com.ssafy.upcycle.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "app.processing.expiry.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:processor-profile-tests;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class ProcessorProfileTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    Long processorId;
    Long brandId;

    @BeforeEach
    void fixture() {
        for (String table : List.of("processing_input", "processing_job", "material_lock",
                "processing_request_type", "processing_request_input", "processing_request",
                "processor_capability", "processor_profile", "material_balance",
                "material", "user_wallet", "company_profile", "users")) {
            jdbc.update("delete from " + table);
        }
        processorId = users.save(User.register("processor-profile@test.com", "hash", "가공사", "010", UserRole.PROCESSOR)).getId();
        brandId = users.save(User.register("profile-brand@test.com", "hash", "브랜드", "010", UserRole.BRAND)).getId();
        approveCompany(processorId, "가공사", "processor-business");
        approveCompany(brandId, "브랜드", "brand-business");
    }

    private void approveCompany(Long userId, String companyName, String businessNumber) {
        jdbc.update("insert into company_profile(user_id,company_name,business_number,zip_code,address,role_approval_status) values (?,?,?,?,?,'APPROVED')",
                userId, companyName, businessNumber, "00000", "서울");
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request, Long userId, String role) {
        return request.with(jwt().jwt(jwt -> jwt.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void missingProfileIsNotFoundWhileListsAreEmpty() throws Exception {
        mvc.perform(authenticated(get("/processors/me/profile"), processorId, "PROCESSOR"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("PROCESSOR_PROFILE_NOT_FOUND"));
        mvc.perform(authenticated(get("/processors/me/capabilities"), processorId, "PROCESSOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
    }

    @Test
    void registersAndFullyReplacesProfileAndCapabilities() throws Exception {
        mvc.perform(authenticated(put("/api/v1/processors/me/profile"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울 성동구\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.processorId").value(processorId))
                .andExpect(jsonPath("data.region").value("서울 성동구"));

        // 가공사가 적는 것은 이제 둘뿐이다 — 하루 처리량과 kg 당 단가.
        String capabilities = """
                {"capabilities":[
                  {"materialType":"DENIM","processType":"CLEANING","dailyCapacity":5000,"pricePerKg":1200},
                  {"materialType":"COTTON","processType":"CUTTING","dailyCapacity":2000,"pricePerKg":900}
                ]}
                """;
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content(capabilities))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(2))
                .andExpect(jsonPath("data[0].materialType").value("COTTON"))
                .andExpect(jsonPath("data[0].dailyCapacity").value(2000))
                .andExpect(jsonPath("data[0].pricePerKg").value(900))
                .andExpect(jsonPath("data[1].materialType").value("DENIM"))
                .andExpect(jsonPath("data[1].dailyCapacity").value(5000))
                .andExpect(jsonPath("data[1].pricePerKg").value(1200));

        // 부분 수정이 아니라 전체 교체다. 남아 있던 두 줄이 사라진다.
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"POLYESTER\",\"processType\":\"SORTING\",\"dailyCapacity\":100,\"pricePerKg\":0}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from processor_capability where processor_profile_id=?", Long.class, processorId)).isEqualTo(1);
    }

    @Test
    void rejectsNonProcessorsZeroCapacityAndDuplicateCombinations() throws Exception {
        mvc.perform(authenticated(put("/processors/me/profile"), brandId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PROCESSOR_PROFILE_FORBIDDEN"));

        mvc.perform(authenticated(put("/processors/me/profile"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울\"}"))
                .andExpect(status().isOk());
        // 하루 처리량 0 은 소요일의 분모가 0 이라는 뜻이다. 계산이 성립하지 않는다.
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"dailyCapacity\":0,\"pricePerKg\":1000}]}"))
                .andExpect(status().isBadRequest());

        // 같은 (소재, 공정) 조합이 두 번 오면 어느 쪽이 맞는지 알 수 없다.
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"dailyCapacity\":1000,\"pricePerKg\":1000},{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"dailyCapacity\":2000,\"pricePerKg\":900}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSOR_CAPABILITIES_INVALID"));

        // 단가 0 은 허용한다 — 무상 가공이 실수인지 의도인지 서버가 판단할 수 없다.
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"dailyCapacity\":5000,\"pricePerKg\":0}]}"))
                .andExpect(status().isOk());
    }

    @Test
    void searchExposesOnlySchedulableRegistrations() throws Exception {
        // 소요일 표가 사라져 "완전한 등록" 의 기준이 하나 줄었다. 남은 조건은 하루
        // 처리량이 있느냐다 — 0 이면 며칠 걸리는지 답할 수 없어 목록에 올리지 않는다.
        jdbc.update("insert into processor_profile(user_id,region) values (?,?)", processorId, "서울");
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,daily_capacity,price_per_kg) values (?,?,?,?,?)",
                processorId, "DENIM", "CLEANING", 0, 1000);
        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(0));

        jdbc.update("update processor_capability set daily_capacity=1000 where processor_profile_id=?", processorId);
        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(1));
    }

    @Test
    void searchHidesACompleteProcessorUntilCompanyRoleIsApproved() throws Exception {
        jdbc.update("insert into processor_profile(user_id,region) values (?,?)", processorId, "서울");
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,daily_capacity,price_per_kg) values (?,?,?,?,?)",
                processorId, "DENIM", "CLEANING", 1000, 1000);
        jdbc.update("update company_profile set role_approval_status='PENDING' where user_id=?", processorId);

        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(0));

        jdbc.update("update company_profile set role_approval_status='APPROVED' where user_id=?", processorId);
        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(1));
    }

    @Test
    void searchShowsTheCompanyNameNotTheRepresentativeName() throws Exception {
        // 가입자 이름(users.name)은 대표자 이름이다. 브랜드가 고르는 것은 업체라 업체명을 보여야 한다.
        jdbc.update("update company_profile set company_name=? where user_id=?", "새로이 가공소", processorId);
        jdbc.update("insert into processor_profile(user_id,region) values (?,?)", processorId, "서울");
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,daily_capacity,price_per_kg) values (?,?,?,?,?)",
                processorId, "DENIM", "CLEANING", 1000, 1000);

        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.items[0].name").value("새로이 가공소"));
        // 조건 없이 둘러볼 때도 같다.
        mvc.perform(authenticated(get("/processors/search"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.items[0].name").value("새로이 가공소"));
    }
}
