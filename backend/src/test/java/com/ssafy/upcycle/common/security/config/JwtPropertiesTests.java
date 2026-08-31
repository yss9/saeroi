package com.ssafy.upcycle.common.security.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtPropertiesTests {

    private static final String VALID_SECRET =
            Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void acceptsAValidConfigurationWithoutExposingRawSecret() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, "upcycle", Duration.ofMinutes(30));

        assertThat(properties.secretKey().getEncoded()).hasSize(32);
        assertThat(properties.issuer()).isEqualTo("upcycle");
        assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.toString()).doesNotContain(VALID_SECRET);
    }

    @Test
    void rejectsMissingMalformedAndShortSecrets() {
        assertThatThrownBy(() -> new JwtProperties(null, "upcycle", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("not-base64", "upcycle", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);
        assertThatThrownBy(() -> new JwtProperties(shortSecret, "upcycle", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankIssuerAndNonPositiveTtl() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, " ", Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "upcycle", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, "upcycle", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
