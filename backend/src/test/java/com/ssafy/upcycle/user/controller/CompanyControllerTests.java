package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * {@code GET/PUT /api/v1/companies/me}. -130/-133 (업체 정보 API + 테스트).
 *
 * <p>{@code @SpringBootTest} 는 롤백이 없어 커밋한 행이 같은 in-memory H2 에 남는다
 * ({@code backend.md} "테스트 작성 시 주의" 참고) — 그래서 매 테스트 전후로 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    private Long supplierId;

    @BeforeEach
    void setUp() {
        clean();
        User supplier = userRepository.save(
                User.register("company-supplier@test.com", "{noop}x", "김대표", "010-1111-2222", UserRole.SUPPLIER));
        supplierId = supplier.getId();
        companyProfileRepository.save(CompanyProfile.register(
                supplierId, "새로이 텍스타일", "123-45-67890", "06236", "서울특별시 강남구 테헤란로 212", "3층"));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        companyProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request, Long userId, String role) {
        return request.with(jwt().jwt(builder -> builder.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    // ── 인증 ─────────────────────────────────────────────────

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/companies/me")).andExpect(status().isUnauthorized());
    }

    // ── 조회 ─────────────────────────────────────────────────

    @Test
    void returnsMyCompanyProfile() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/companies/me"), supplierId, "SUPPLIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("새로이 텍스타일"))
                .andExpect(jsonPath("$.data.businessNumber").value("123-45-67890"))
                .andExpect(jsonPath("$.data.representativeName").value("김대표"))
                .andExpect(jsonPath("$.data.companyPhone").value("010-1111-2222"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 강남구 테헤란로 212"))
                .andExpect(jsonPath("$.data.role").value("SUPPLIER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rejectsAConsumerWithoutACompanyProfile() throws Exception {
        User consumer = userRepository.save(
                User.register("company-consumer@test.com", "{noop}x", "일반", "010-0000-0000", UserRole.CONSUMER));

        mockMvc.perform(authenticated(get("/api/v1/companies/me"), consumer.getId(), "CONSUMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_FORBIDDEN"));
    }

    @Test
    void returnsNotFoundWhenABusinessRoleHasNoProfileRow() throws Exception {
        // 정상 가입 흐름(SignupService.registerBusiness)에서는 항상 함께 생기지만,
        // 방어적으로 이 상태도 처리한다 — 데이터 정합성이 깨진 계정을 500 대신 명확히 알린다.
        User brandWithoutProfile = userRepository.save(
                User.register("company-brand-no-profile@test.com", "{noop}x", "브랜드", "010-3333-4444", UserRole.BRAND));

        mockMvc.perform(authenticated(get("/api/v1/companies/me"), brandWithoutProfile.getId(), "BRAND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_NOT_FOUND"));
    }

    @Test
    void onlySeesOwnProfileNeverAnotherCompanys() throws Exception {
        User otherSupplier = userRepository.save(
                User.register("company-other@test.com", "{noop}x", "박대표", "010-5555-6666", UserRole.SUPPLIER));
        companyProfileRepository.save(CompanyProfile.register(
                otherSupplier.getId(), "다른 업체", "999-88-77777", "12345", "부산광역시 해운대구", null));

        mockMvc.perform(authenticated(get("/api/v1/companies/me"), otherSupplier.getId(), "SUPPLIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("다른 업체"));

        // 원래 계정은 여전히 자기 데이터만 본다 — 위 조회가 다른 유저의 컨텍스트를 새지 않았는지 확인.
        mockMvc.perform(authenticated(get("/api/v1/companies/me"), supplierId, "SUPPLIER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("새로이 텍스타일"));
    }

    // ── 수정 ─────────────────────────────────────────────────

    @Test
    void updatesEditableFieldsAndPersists() throws Exception {
        String body = """
                {
                  "representativeName": "이신임",
                  "companyPhone": "02-9999-8888",
                  "zipCode": "04524",
                  "address": "서울특별시 중구 세종대로 110",
                  "addressDetail": "5층",
                  "introduction": "업사이클 원단 공급업체입니다.",
                  "logoUrl": "https://cdn.example.com/logo.png"
                }
                """;

        mockMvc.perform(authenticated(put("/api/v1/companies/me"), supplierId, "SUPPLIER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.representativeName").value("이신임"))
                .andExpect(jsonPath("$.data.companyPhone").value("02-9999-8888"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 중구 세종대로 110"))
                .andExpect(jsonPath("$.data.introduction").value("업사이클 원단 공급업체입니다."))
                // 수정 대상이 아니다 — 등록 당시 값 그대로 남아야 한다.
                .andExpect(jsonPath("$.data.companyName").value("새로이 텍스타일"))
                .andExpect(jsonPath("$.data.businessNumber").value("123-45-67890"));

        User reloaded = userRepository.findById(supplierId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("이신임");
        assertThat(reloaded.getPhone()).isEqualTo("02-9999-8888");
        CompanyProfile reloadedProfile = companyProfileRepository.findById(supplierId).orElseThrow();
        assertThat(reloadedProfile.getAddress()).isEqualTo("서울특별시 중구 세종대로 110");
        assertThat(reloadedProfile.getIntroduction()).isEqualTo("업사이클 원단 공급업체입니다.");
    }

    @Test
    void rejectsUpdateAttemptsToChangeImmutableFields() throws Exception {
        // 요청 DTO 자체에 companyName/businessNumber 필드가 없다 — 보내도 무시된다.
        String body = """
                {
                  "companyName": "몰래 바꾼 이름",
                  "businessNumber": "000-00-00000",
                  "representativeName": "이신임",
                  "companyPhone": "02-9999-8888",
                  "zipCode": "04524",
                  "address": "서울특별시 중구 세종대로 110",
                  "addressDetail": null
                }
                """;

        mockMvc.perform(authenticated(put("/api/v1/companies/me"), supplierId, "SUPPLIER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("새로이 텍스타일"))
                .andExpect(jsonPath("$.data.businessNumber").value("123-45-67890"));
    }

    @Test
    void rejectsMalformedUpdateInput() throws Exception {
        String body = """
                {
                  "representativeName": "",
                  "companyPhone": "not-a-phone",
                  "zipCode": "04524",
                  "address": "서울특별시 중구 세종대로 110"
                }
                """;

        mockMvc.perform(authenticated(put("/api/v1/companies/me"), supplierId, "SUPPLIER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsUpdateFromAConsumerAccount() throws Exception {
        User consumer = userRepository.save(
                User.register("company-update-consumer@test.com", "{noop}x", "일반", "010-0000-0000", UserRole.CONSUMER));
        String body = """
                {
                  "representativeName": "이신임",
                  "companyPhone": "02-9999-8888",
                  "zipCode": "04524",
                  "address": "서울특별시 중구 세종대로 110"
                }
                """;

        mockMvc.perform(authenticated(put("/api/v1/companies/me"), consumer.getId(), "CONSUMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPANY_PROFILE_FORBIDDEN"));
    }
}
