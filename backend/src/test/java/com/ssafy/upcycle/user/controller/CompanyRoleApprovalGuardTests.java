package com.ssafy.upcycle.user.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:company-role-guard;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.company-role-approval-guard.enabled=true"
})
@AutoConfigureMockMvc
class CompanyRoleApprovalGuardTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired CompanyProfileRepository companyProfiles;

    private Long brandId;

    @BeforeEach
    void fixture() {
        companyProfiles.deleteAll();
        users.deleteAll();
        brandId = users.save(User.register(
                "pending-brand@test.com", "{noop}x", "대기 브랜드", "010", UserRole.BRAND)).getId();
        companyProfiles.save(CompanyProfile.register(
                brandId,
                "대기 브랜드",
                "pending-brand",
                "00000",
                "서울",
                null,
                new GeoCoordinates(BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test
    void pendingBusinessCanManageOnboardingButCannotUseRoleFeatures() throws Exception {
        mvc.perform(get("/api/v1/companies/me").with(businessJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.roleApprovalStatus").value("PENDING"));

        mvc.perform(get("/api/materials/me").with(businessJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("code").value("COMPANY_ROLE_APPROVAL_REQUIRED"));
    }

    @Test
    void approvedBusinessCanUseRoleFeaturesImmediately() throws Exception {
        CompanyProfile profile = companyProfiles.findById(brandId).orElseThrow();
        profile.approveRole(
                "0x" + "a".repeat(40), "0xapproval", 999L, LocalDateTime.now());
        companyProfiles.save(profile);

        mvc.perform(get("/api/materials/me").with(businessJwt()))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor businessJwt() {
        return jwt().jwt(builder -> builder
                .subject(brandId.toString())
                .claim("roles", List.of("BRAND")));
    }
}
