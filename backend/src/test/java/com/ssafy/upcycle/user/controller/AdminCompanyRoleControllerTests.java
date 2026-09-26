package com.ssafy.upcycle.user.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.blockchain.service.OnChainRoleVerificationGateway;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.entity.UserWallet;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.repository.UserWalletRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCompanyRoleControllerTests {
    private static final String WALLET = "0x1111111111111111111111111111111111111111";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyProfileRepository companyProfileRepository;
    @Autowired UserWalletRepository userWalletRepository;
    @MockitoBean OnChainRoleVerificationGateway roleVerificationGateway;

    private Long adminId;
    private Long brandId;

    @BeforeEach
    void setUp() {
        clean();
        User admin = userRepository.save(User.register(
                "role-admin@test.com", "{noop}x", "관리자", "010-0000-0000", UserRole.ADMIN));
        User brand = userRepository.save(User.register(
                "role-brand@test.com", "{noop}x", "대표", "010-1111-1111", UserRole.BRAND));
        adminId = admin.getId();
        brandId = brand.getId();
        companyProfileRepository.save(CompanyProfile.register(
                brandId, "순환 브랜드", "123-45-67890", "06236", "서울 강남구", null,
                new GeoCoordinates(new BigDecimal("37.5"), new BigDecimal("127.0"))));
    }

    @AfterEach void tearDown() { clean(); }

    @Test
    void listsAnApplicationWaitingForAVerifiedWallet() throws Exception {
        mockMvc.perform(admin(get("/api/v1/admin/company-role-applications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(brandId))
                .andExpect(jsonPath("$.data[0].status").value("WAITING_WALLET"));
    }

    @Test
    void removesApplicationFromListAfterApproval() throws Exception {
        UserWallet wallet = userWalletRepository.save(UserWallet.link(brandId, WALLET, 31221));
        wallet.markVerified(LocalDateTime.now());
        userWalletRepository.saveAndFlush(wallet);
        String tx1 = "0x" + "a".repeat(64);
        String tx2 = "0x" + "b".repeat(64);
        String tx3 = "0x" + "c".repeat(64);

        mockMvc.perform(admin(post("/api/v1/admin/company-role-applications/{id}/approve", brandId))
                        .contentType("application/json")
                        .content("{\"transactionHashes\":[\"" + tx1 + "\",\"" + tx2 + "\",\"" + tx3 + "\"]}"))
                .andExpect(status().isOk());

        mockMvc.perform(admin(get("/api/v1/admin/company-role-applications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void grantsEveryRequiredOnChainRoleBeforeMarkingApproved() throws Exception {
        UserWallet wallet = userWalletRepository.save(UserWallet.link(brandId, WALLET, 31221));
        wallet.markVerified(LocalDateTime.now());
        userWalletRepository.saveAndFlush(wallet);
        String tx1 = "0x" + "a".repeat(64);
        String tx2 = "0x" + "b".repeat(64);
        String tx3 = "0x" + "c".repeat(64);

        mockMvc.perform(admin(post("/api/v1/admin/company-role-applications/{id}/approve", brandId))
                        .contentType("application/json")
                        .content("{\"transactionHashes\":[\"" + tx1 + "\",\"" + tx2 + "\",\"" + tx3 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedWallet").value(WALLET))
                .andExpect(jsonPath("$.data.transactionHashes.length()").value(3));

        verify(roleVerificationGateway).verifyGranted(
                eq(UserRole.BRAND), eq(WALLET), eq(List.of(tx1, tx2, tx3)));
    }

    @Test
    void refusesApprovalWithoutAVerifiedWallet() throws Exception {
        mockMvc.perform(admin(post("/api/v1/admin/company-role-applications/{id}/approve", brandId))
                        .contentType("application/json")
                        .content("{\"transactionHashes\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMPANY_WALLET_REQUIRED"));
    }

    @Test
    void forbidsNonAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/admin/company-role-applications")
                        .with(jwt().jwt(builder -> builder.subject(brandId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_BRAND"))))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.with(jwt().jwt(builder -> builder.subject(adminId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private void clean() {
        userWalletRepository.deleteAll();
        companyProfileRepository.deleteAll();
        userRepository.deleteAll();
    }
}
