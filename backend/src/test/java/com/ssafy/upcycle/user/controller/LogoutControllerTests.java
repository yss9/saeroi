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
import com.ssafy.upcycle.user.service.RefreshTokenCodec;
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
class LogoutControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenCodec codec;

    private IssuedTokenPair issued;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        User user = userRepository.save(User.register(
                "consumer@example.com", "{noop}x", "소비자", "010-0000-0000", UserRole.CONSUMER));
        issued = refreshTokenService.issueFor(user);
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsItsCookie() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie(issued.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn();

        assertThat(refreshTokenRepository.findByTokenHash(codec.hash(issued.refreshToken())))
                .get()
                .satisfies(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .startsWith(RefreshTokenCookieWriter.COOKIE_NAME + "=;")
                .contains("Path=/api/v1/auth", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie(issued.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutWithoutACookieIsIdempotentAndStillClearsTheBrowserCookie() throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .startsWith(RefreshTokenCookieWriter.COOKIE_NAME + "=;")
                .contains("Max-Age=0");
    }

    private Cookie refreshCookie(String refreshToken) {
        return new Cookie(RefreshTokenCookieWriter.COOKIE_NAME, refreshToken);
    }
}
