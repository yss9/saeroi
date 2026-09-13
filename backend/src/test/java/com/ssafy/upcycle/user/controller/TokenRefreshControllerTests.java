package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.RefreshTokenRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.service.IssuedTokenPair;
import com.ssafy.upcycle.user.service.RefreshTokenService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenRefreshControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private IssuedTokenPair issued;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        User user = userRepository.save(User.register(
                "consumer@example.com", "{noop}x", "소비자", "010-0000-0000", UserRole.CONSUMER));
        issued = refreshTokenService.issueFor(user);
    }

    @Test
    void refreshIsPublicAndReturnsARotatedTokenPair() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(issued.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(response).path("data").path("refreshToken").asText())
                .isNotEqualTo(issued.refreshToken());
        assertThat(response).doesNotContain(issued.refreshToken());
    }

    @Test
    void invalidRefreshTokenReturnsTheDomainErrorWithoutEchoingTheToken() throws Exception {
        String rawToken = "unknown-secret-refresh-token";

        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rawToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(rawToken);
    }

    @Test
    void blankRefreshTokenIsRejectedByValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String body(String refreshToken) throws Exception {
        return objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken));
    }
}
