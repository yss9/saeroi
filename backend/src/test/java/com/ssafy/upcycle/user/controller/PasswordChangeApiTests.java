package com.ssafy.upcycle.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.user.entity.User;
import com.ssafy.upcycle.user.entity.UserRole;
import com.ssafy.upcycle.user.repository.RefreshTokenRepository;
import com.ssafy.upcycle.user.repository.UserRepository;
import com.ssafy.upcycle.user.service.IssuedTokenPair;
import com.ssafy.upcycle.user.service.RefreshTokenCookieWriter;
import com.ssafy.upcycle.user.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code PUT /api/v1/users/me/password} — 비밀번호 변경.
 *
 * <p>여기서 지키는 것은 넷이다. <b>현재 비밀번호를 맞혀야 바뀌고</b>, <b>바뀐 비밀번호로
 * 실제로 로그인되며</b>, <b>실패한 요청은 아무것도 바꾸지 않고</b>, <b>성공하면 그 계정의
 * 세션이 전부 끊긴다</b>. 둘째는 저장 형식(인코딩)이 맞는지까지 확인하는 유일한 방법이라
 * 로그인 API 를 실제로 부른다 — DB 의 해시 문자열을 비교하는 것으로는 그 계정으로 로그인이
 * 되는지 알 수 없다.
 *
 * <p>넷째가 이 기능의 핵심이다. 비밀번호를 바꾸는 이유 중 하나가 계정 탈취 의심인데, 침입자의
 * 세션이 살아 있으면 바꾼 의미가 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordChangeApiTests {

    private static final String BASE = "/api/v1/users/me/password";
    private static final String EMAIL = "password-consumer@example.com";
    private static final String CURRENT_PASSWORD = "Password!1";
    private static final String NEW_PASSWORD = "NewPassword!2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private User consumer;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        consumer = userRepository.save(User.register(
                EMAIL,
                passwordEncoder.encode(CURRENT_PASSWORD),
                "홍길동",
                "010-1111-2222",
                UserRole.CONSUMER));
    }

    // ── 변경 ────────────────────────────────────────────────

    @Test
    void theNewPasswordActuallyWorksForLoggingIn() throws Exception {
        mockMvc.perform(changeRequest(consumer.getId(), CURRENT_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());

        login(NEW_PASSWORD).andExpect(status().isOk());
        // 옛 비밀번호로 계속 로그인되면 바뀐 것이 아니다.
        login(CURRENT_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void theStoredValueIsAHashAndNotTheTypedPassword() throws Exception {
        mockMvc.perform(changeRequest(consumer.getId(), CURRENT_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());

        String stored = userRepository.findById(consumer.getId()).orElseThrow().getPassword();
        assertThat(stored).doesNotContain(NEW_PASSWORD).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, stored)).isTrue();
    }

    @Test
    void businessAccountsChangeTheirPasswordThroughTheSamePath() throws Exception {
        // 역할과 무관한 계정 기능이다. 사업자에게만 없으면 같은 것을 한 벌 더 만들게 된다.
        User brand = userRepository.save(User.register(
                "password-brand@example.com",
                passwordEncoder.encode(CURRENT_PASSWORD),
                "김대표",
                "02-1234-5678",
                UserRole.BRAND));

        mockMvc.perform(put(BASE)
                        .with(jwt()
                                .jwt(token -> token.subject(String.valueOf(brand.getId())))
                                .authorities(new SimpleGrantedAuthority("ROLE_BRAND")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                body(CURRENT_PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isOk());

        assertThat(passwordEncoder.matches(
                NEW_PASSWORD,
                userRepository.findById(brand.getId()).orElseThrow().getPassword())).isTrue();
    }

    // ── 거절 ────────────────────────────────────────────────

    @Test
    void aWrongCurrentPasswordIsRejectedAsInputNotAsAnExpiredSession() throws Exception {
        // 401 이면 프론트 공통 인터셉터가 세션 만료로 해석해 로그인 화면으로 보낸다.
        mockMvc.perform(changeRequest(consumer.getId(), "WrongPassword!1", NEW_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_MISMATCH"));

        assertThat(passwordEncoder.matches(
                CURRENT_PASSWORD,
                userRepository.findById(consumer.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void reusingTheCurrentPasswordIsRefused() throws Exception {
        // 막지 않으면 "변경되었습니다" 를 받고도 바뀐 것 없이 다시 로그인만 하게 된다.
        mockMvc.perform(changeRequest(consumer.getId(), CURRENT_PASSWORD, CURRENT_PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_SAME_AS_CURRENT"));
    }

    @Test
    void aWeakNewPasswordIsRefusedWithTheSameRuleAsSignup() throws Exception {
        for (String weak : new String[] {"short!1", "nodigits!!", "nospecial12"}) {
            mockMvc.perform(changeRequest(consumer.getId(), CURRENT_PASSWORD, weak))
                    .andExpect(status().isBadRequest());
        }

        assertThat(passwordEncoder.matches(
                CURRENT_PASSWORD,
                userRepository.findById(consumer.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void changingAPasswordNeedsALogin() throws Exception {
        mockMvc.perform(put(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                body(CURRENT_PASSWORD, NEW_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ── 세션 ────────────────────────────────────────────────

    /**
     * 비밀번호를 바꾸기 전에 받은 Refresh Token 은 더 이상 쓸 수 없어야 한다.
     *
     * <p>이것이 안 되면 계정이 털린 것 같아 비밀번호를 바꾼 사용자의 화면에서, 침입자의
     * 세션이 2주(Refresh Token 수명) 동안 계속 갱신된다.
     */
    @Test
    void everySessionOpenedBeforeTheChangeIsCutOff() throws Exception {
        IssuedTokenPair otherDevice = refreshTokenService.issueFor(consumer);
        IssuedTokenPair thisDevice = refreshTokenService.issueFor(consumer);

        mockMvc.perform(changeRequest(consumer.getId(), CURRENT_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk());

        for (IssuedTokenPair session : new IssuedTokenPair[] {otherDevice, thisDevice}) {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .cookie(new Cookie(
                                    RefreshTokenCookieWriter.COOKIE_NAME, session.refreshToken())))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        }
    }

    @Test
    void theDeadCookieIsClearedSoTheBrowserDoesNotKeepUsingIt() throws Exception {
        // 죽은 토큰을 브라우저에 남겨 두면 다음 재발급이 INVALID_REFRESH_TOKEN 으로 튕기고,
        // 화면은 그 이유를 설명할 수 없다. 로그아웃과 같은 방식으로 지운다.
        var result = mockMvc.perform(
                        changeRequest(consumer.getId(), CURRENT_PASSWORD, NEW_PASSWORD))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .startsWith(RefreshTokenCookieWriter.COOKIE_NAME + "=")
                .contains("Max-Age=0");
    }

    @Test
    void aFailedChangeDoesNotCutAnySession() throws Exception {
        // 폐기를 먼저 하면 현재 비밀번호를 틀린 요청도 남의 세션을 끊을 수 있다.
        IssuedTokenPair session = refreshTokenService.issueFor(consumer);

        mockMvc.perform(changeRequest(consumer.getId(), "WrongPassword!1", NEW_PASSWORD))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(
                                RefreshTokenCookieWriter.COOKIE_NAME, session.refreshToken())))
                .andExpect(status().isOk());
    }

    // ── helpers ─────────────────────────────────────────────

    private MockHttpServletRequestBuilder changeRequest(
            Long userId, String currentPassword, String newPassword) throws Exception {
        return put(BASE)
                .with(jwt()
                        .jwt(token -> token.subject(String.valueOf(userId)))
                        .authorities(new SimpleGrantedAuthority("ROLE_CONSUMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body(currentPassword, newPassword)));
    }

    private org.springframework.test.web.servlet.ResultActions login(String password)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("email", EMAIL);
        request.put("password", password);
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private Map<String, Object> body(String currentPassword, String newPassword) {
        Map<String, Object> request = new HashMap<>();
        request.put("currentPassword", currentPassword);
        request.put("newPassword", newPassword);
        return request;
    }
}
