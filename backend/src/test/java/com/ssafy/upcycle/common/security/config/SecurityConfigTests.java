package com.ssafy.upcycle.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.upcycle.common.response.ApiResponse;
import com.ssafy.upcycle.common.security.jwt.JwtTokenService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityConfigTests.SecurityTestController.class)
class SecurityConfigTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void publicAuthPathDoesNotRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("public"));
    }

    @Test
    void protectedPathReturnsCommonUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/test/security/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void validTokenCanAccessProtectedPath() throws Exception {
        String token = tokenService.issueAccessToken("user-1", List.of("USER")).value();

        mockMvc.perform(get("/test/security/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void insufficientRoleReturnsCommonForbiddenResponse() throws Exception {
        String token = tokenService.issueAccessToken("user-1", List.of("USER")).value();

        mockMvc.perform(get("/test/security/admin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void passwordEncoderUsesDelegatingBcrypt() {
        String encoded = passwordEncoder.encode("password");

        assertThat(encoded).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("password", encoded)).isTrue();
    }

    @Test
    void invalidTokenDoesNotLeakDetails() throws Exception {
        String invalidToken = "secret-invalid-token";

        String body = mockMvc.perform(get("/test/security/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(invalidToken, "JwtException", "Invalid");
    }

    @Test
    void signedTokenWithPrefixedRoleIsRejectedAsUnauthorized() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("upcycle-test")
                .subject("user-1")
                .issuedAt(now.minusSeconds(1))
                .expiresAt(now.plusSeconds(300))
                .claim("roles", List.of("ROLE_ADMIN"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mockMvc.perform(get("/test/security/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/api/v1/auth/test")
        ApiResponse<String> publicEndpoint() {
            return ApiResponse.success("public");
        }

        @GetMapping("/test/security/protected")
        ApiResponse<String> protectedEndpoint() {
            return ApiResponse.success("protected");
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/test/security/admin")
        ApiResponse<String> adminEndpoint() {
            return ApiResponse.success("admin");
        }
    }
}
