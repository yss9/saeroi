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
        for (String table : List.of("processing_input", "processing_job", "processing_quote", "material_lock",
                "processing_request_type", "processing_request_input", "processing_request",
                "processor_capability", "processor_lead_time", "processor_profile", "material_balance",
                "material", "user_wallet", "users")) {
            jdbc.update("delete from " + table);
        }
        processorId = users.save(User.register("processor-profile@test.com", "hash", "가공사", "010", UserRole.PROCESSOR)).getId();
        brandId = users.save(User.register("profile-brand@test.com", "hash", "브랜드", "010", UserRole.BRAND)).getId();
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
        mvc.perform(authenticated(get("/processors/me/lead-times"), processorId, "PROCESSOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
    }

    @Test
    void registersAndFullyReplacesProfileCapabilitiesAndLeadTimes() throws Exception {
        mvc.perform(authenticated(put("/api/v1/processors/me/profile"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울 성동구\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.processorId").value(processorId))
                .andExpect(jsonPath("data.region").value("서울 성동구"));

        String capabilities = """
                {"capabilities":[
                  {"materialType":"DENIM","processType":"CLEANING","minQuantity":100,"maxQuantity":5000},
                  {"materialType":"COTTON","processType":"CUTTING","minQuantity":50,"maxQuantity":2000}
                ]}
                """;
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content(capabilities))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(2));

        String leadTimes = """
                {"leadTimes":[
                  {"materialType":"DENIM","processType":"CLEANING","maxQuantity":5000,"leadDays":7},
                  {"materialType":"COTTON","processType":"CUTTING","maxQuantity":2000,"leadDays":3},
                  {"materialType":"DENIM","processType":"CLEANING","maxQuantity":1000,"leadDays":2}
                ]}
                """;
        mvc.perform(authenticated(put("/processors/me/lead-times"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content(leadTimes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data[0].materialType").value("COTTON"))
                .andExpect(jsonPath("data[0].processType").value("CUTTING"))
                .andExpect(jsonPath("data[0].maxQuantity").value(2000))
                .andExpect(jsonPath("data[1].materialType").value("DENIM"))
                .andExpect(jsonPath("data[1].maxQuantity").value(1000))
                .andExpect(jsonPath("data[2].maxQuantity").value(5000));

        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"LEATHER\",\"processType\":\"SORTING\",\"minQuantity\":1,\"maxQuantity\":100}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1));
        assertThat(jdbc.queryForObject("select count(*) from processor_capability where processor_profile_id=?", Long.class, processorId)).isEqualTo(1);
    }

    @Test
    void rejectsNonProcessorInvalidRangesDuplicatesAndUncoveredCapabilities() throws Exception {
        mvc.perform(authenticated(put("/processors/me/profile"), brandId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("PROCESSOR_PROFILE_FORBIDDEN"));

        mvc.perform(authenticated(put("/processors/me/profile"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"region\":\"서울\"}"))
                .andExpect(status().isOk());
        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"minQuantity\":10,\"maxQuantity\":1}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSOR_CAPABILITIES_INVALID"));
        mvc.perform(authenticated(put("/processors/me/lead-times"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadTimes\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"maxQuantity\":1000,\"leadDays\":3},{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"maxQuantity\":1000,\"leadDays\":5}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSOR_LEAD_TIMES_INVALID"));

        mvc.perform(authenticated(put("/processors/me/capabilities"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capabilities\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"minQuantity\":1,\"maxQuantity\":5000}]}"))
                .andExpect(status().isOk());
        mvc.perform(authenticated(put("/processors/me/lead-times"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadTimes\":[{\"materialType\":\"DENIM\",\"processType\":\"CLEANING\",\"maxQuantity\":1000,\"leadDays\":3}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSOR_LEAD_TIMES_INVALID"));

        mvc.perform(authenticated(put("/processors/me/lead-times"), processorId, "PROCESSOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leadTimes\":[{\"materialType\":\"COTTON\",\"processType\":\"CLEANING\",\"maxQuantity\":5000,\"leadDays\":3}]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("PROCESSOR_LEAD_TIMES_INVALID"));
    }

    @Test
    void searchExposesOnlyCompleteRegistrations() throws Exception {
        jdbc.update("insert into processor_profile(user_id,region) values (?,?)", processorId, "서울");
        jdbc.update("insert into processor_capability(processor_profile_id,material_type,process_type,min_quantity,max_quantity) values (?,?,?,?,?)",
                processorId, "DENIM", "CLEANING", 1, 1000);
        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(0));

        jdbc.update("insert into processor_lead_time(processor_profile_id,material_type,process_type,max_quantity,lead_days) values (?,?,?,?,?)",
                processorId, "DENIM", "CLEANING", 1000, 3);
        mvc.perform(authenticated(get("/processors/search").param("materialType", "DENIM")
                        .param("quantity", "100").param("processTypes", "CLEANING"), brandId, "BRAND"))
                .andExpect(status().isOk()).andExpect(jsonPath("data.total").value(1));
    }
}
