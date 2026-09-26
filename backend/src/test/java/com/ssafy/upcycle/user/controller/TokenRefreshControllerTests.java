package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.RefreshTokenRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.service.IssuedTokenPair;
import com.ssafy.upcycle.user.service.RefreshTokenCookieWriter;
import com.ssafy.upcycle.user.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenRefreshControllerTests {

    @Autowired
    private MockMvc mockMvc;

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
    void refreshIsPublicAndReturnsAccessTokenWhileRotatingTheCookie() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(issued.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie)
                .startsWith(RefreshTokenCookieWriter.COOKIE_NAME + "=")
                .doesNotContain(issued.refreshToken())
                .contains("HttpOnly", "Secure", "SameSite=Strict");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(issued.refreshToken());
    }

    @Test
    void invalidRefreshTokenReturnsTheDomainErrorWithoutEchoingTheToken() throws Exception {
        String rawToken = "unknown-secret-refresh-token";

        String response = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(rawToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response).doesNotContain(rawToken);
    }

    @Test
    void missingRefreshTokenCookieIsRejectedAsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private Cookie refreshCookie(String refreshToken) {
        return new Cookie(RefreshTokenCookieWriter.COOKIE_NAME, refreshToken);
    }
}
