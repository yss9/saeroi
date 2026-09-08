package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailAuthControllerTests {

    private static final String EMAIL = "consumer@example.com";
    private static final String PASSWORD = "Password!1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.register(
                EMAIL,
                passwordEncoder.encode(PASSWORD),
                "소비자",
                "010-1234-5678",
                UserRole.CONSUMER));
    }

    @Test
    void loginIsPublicAndReturnsATokenAndProfileWithoutThePassword() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.id").value(user.getId()))
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.name").value("소비자"))
                .andExpect(jsonPath("$.data.user.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.data.user.role").value("CONSUMER"))
                .andExpect(jsonPath("$.data.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.user.password").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(PASSWORD, user.getPassword());
    }

    @Test
    void issuedTokenCarriesTheUserIdAndRole() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(response).path("data").path("accessToken").asText();

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(user.getId()));
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("CONSUMER"));
    }

    @Test
    void emailLookupIsCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("CONSUMER@EXAMPLE.COM", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL));
    }

    @Test
    void wrongPasswordAndUnknownEmailReturnTheSameError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, "WrongPassword!1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("unknown@example.com", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void suspendedUserIsRejectedAfterProvidingTheCorrectPassword() throws Exception {
        user.suspend();
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_SUSPENDED"));
    }

    @Test
    void malformedRequestReturnsValidationErrorsWithoutEchoingThePassword() throws Exception {
        String password = "secret-that-must-not-leak";

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("not-an-email", password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data[0].field").value("email"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(password);
    }

    private String loginBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("email", email, "password", password));
    }
}
