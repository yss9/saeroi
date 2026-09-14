package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.user.CompanyErrorCode;
import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.CompanyProfileRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.geolocation.AddressGeocoder;
import com.ssafy.upcycle.user.geolocation.GeoCoordinates;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SignupControllerTests {

    private static final String PASSWORD = "Password!1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AddressGeocoder addressGeocoder;

    @BeforeEach
    void setUpGeocoding() {
        when(addressGeocoder.geocode(anyString())).thenReturn(new GeoCoordinates(
                new BigDecimal("37.5012345"), new BigDecimal("127.0123456")));
    }

    @Test
    void consumerSignupIsPublicAndPersistsAnEncodedConsumerAccount() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consumerBody("NEW@EXAMPLE.COM"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.role").value("CONSUMER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        User saved = userRepository.findByEmail("new@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(UserRole.CONSUMER);
        assertThat(saved.getPassword()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();
        assertThat(response).doesNotContain(PASSWORD, saved.getPassword());
    }

    @Test
    void newlyRegisteredConsumerCanLoginWithTheExistingEmailLoginApi() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consumerBody("login@example.com"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "login@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("login@example.com"))
                .andExpect(jsonPath("$.data.user.role").value("CONSUMER"));
    }

    @Test
    void businessSignupPersistsTheAccountAndCompanyProfileTogether() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(businessBody(UserRole.SUPPLIER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("company@example.com"))
                .andExpect(jsonPath("$.data.name").value("김대표"))
                .andExpect(jsonPath("$.data.phone").value("02-1234-5678"))
                .andExpect(jsonPath("$.data.role").value("SUPPLIER"));

        User saved = userRepository.findByEmail("company@example.com").orElseThrow();
        CompanyProfile profile = companyProfileRepository.findById(saved.getId()).orElseThrow();

        assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword())).isTrue();
        assertThat(profile.getCompanyName()).isEqualTo("새로이 소재");
        assertThat(profile.getBusinessNumber()).isEqualTo("123-45-67890");
        assertThat(profile.getZipCode()).isEqualTo("06236");
        assertThat(profile.getAddress()).isEqualTo("서울특별시 강남구 테헤란로 1");
        assertThat(profile.getAddressDetail()).isEqualTo("2층");
        assertThat(profile.getLatitude()).isEqualByComparingTo("37.5012345");
        assertThat(profile.getLongitude()).isEqualByComparingTo("127.0123456");
    }

    @Test
    void rejectsBusinessSignupWhenTheAddressCannotBeLocatedWithoutSavingTheAccount() throws Exception {
        when(addressGeocoder.geocode(anyString()))
                .thenThrow(new BusinessException(CompanyErrorCode.COMPANY_ADDRESS_NOT_FOUND));

        mockMvc.perform(post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(businessBody(UserRole.SUPPLIER))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMPANY_ADDRESS_NOT_FOUND"));

        assertThat(userRepository.findByEmail("company@example.com")).isEmpty();
        assertThat(companyProfileRepository.count()).isZero();
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consumerBody("duplicate@example.com"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(consumerBody("DUPLICATE@EXAMPLE.COM"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void consumerAndAdminRolesCannotUseBusinessSignup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(businessBody(UserRole.CONSUMER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_ROLE"));

        mockMvc.perform(post("/api/v1/auth/signup/business")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(businessBody(UserRole.ADMIN))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BUSINESS_ROLE"));
    }

    @Test
    void malformedSignupReturnsValidationErrorsWithoutEchoingThePassword() throws Exception {
        Map<String, Object> body = consumerBody("not-an-email");
        body.put("password", "secret-that-must-not-leak");

        String response = mockMvc.perform(post("/api/v1/auth/signup/consumer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain("secret-that-must-not-leak");
    }

    private Map<String, Object> consumerBody(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "홍길동");
        body.put("email", email);
        body.put("phone", "010-1234-5678");
        body.put("password", PASSWORD);
        body.put("zipCode", "06236");
        body.put("address", "서울특별시 강남구 테헤란로 212");
        body.put("addressDetail", "5층");
        return body;
    }

    private Map<String, Object> businessBody(UserRole role) {
        Map<String, Object> body = new HashMap<>();
        body.put("role", role);
        body.put("companyName", "새로이 소재");
        body.put("businessNumber", "123-45-67890");
        body.put("representativeName", "김대표");
        body.put("companyPhone", "02-1234-5678");
        body.put("zipCode", "06236");
        body.put("address", "서울특별시 강남구 테헤란로 1");
        body.put("addressDetail", "2층");
        body.put("email", "company@example.com");
        body.put("password", PASSWORD);
        return body;
    }
}
