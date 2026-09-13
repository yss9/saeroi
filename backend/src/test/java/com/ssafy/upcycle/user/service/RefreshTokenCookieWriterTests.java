package com.ssafy.upcycle.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.upcycle.user.config.RefreshTokenProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshTokenCookieWriterTests {

    @Test
    void writesAHardenedRefreshTokenCookie() {
        RefreshTokenCookieWriter writer = new RefreshTokenCookieWriter(
                new RefreshTokenProperties(Duration.ofDays(14), true, "Strict"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, "opaque-refresh-token");

        assertThat(response.getHeader("Set-Cookie"))
                .startsWith("refresh_token=opaque-refresh-token;")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=1209600")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }
}
