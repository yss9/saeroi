package com.ssafy.upcycle.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.upcycle.common.security.config.JwtProperties;
import com.ssafy.upcycle.common.security.config.JwtSecurityConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtTokenServiceTests {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final JwtProperties PROPERTIES =
            new JwtProperties(SECRET, "upcycle-test", Duration.ofMinutes(30));

    private final JwtSecurityConfig securityConfig = new JwtSecurityConfig();
    private final JwtEncoder encoder = securityConfig.jwtEncoder(PROPERTIES);
    private final JwtDecoder decoder = securityConfig.jwtDecoder(PROPERTIES);

    @Test
    void issuesAndDecodesAccessTokenWithExactClaims() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        AccessToken token = tokenService(Clock.fixed(now, ZoneOffset.UTC))
                .issueAccessToken("user-1", List.of("USER", "ADMIN", "USER"));

        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("upcycle-test");
        assertThat(jwt.getSubject()).isEqualTo("user-1");
        assertThat(jwt.getIssuedAt()).isEqualTo(now);
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN", "USER");
        assertThat(token.expiresAt()).isEqualTo(jwt.getExpiresAt());
        assertThat(token.toString()).doesNotContain(token.value());
    }

    @Test
    void rejectsInvalidTokenInputs() {
        JwtTokenService service = tokenService(Clock.systemUTC());

        assertThatThrownBy(() -> service.issueAccessToken(" ", List.of("USER")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueAccessToken("user-1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueAccessToken("user-1", List.of("")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.issueAccessToken("user-1", List.of("ROLE_USER")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decoderRejectsWrongIssuerExpiredAndForeignSignature() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String wrongIssuer = encode(encoder, "other", now.minusSeconds(1), now.plusSeconds(300));
        String expired = encode(encoder, "upcycle-test", now.minusSeconds(600), now.minusSeconds(300));
        String foreign = encode(foreignEncoder(), "upcycle-test", now.minusSeconds(1), now.plusSeconds(300));

        assertThatThrownBy(() -> decoder.decode(wrongIssuer))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(expired))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void decoderRejectsTokenWithoutExpiration() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("upcycle-test")
                .subject("user-1")
                .issuedAt(now.minusSeconds(1))
                .claim("roles", List.of("USER"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    private JwtTokenService tokenService(Clock clock) {
        return new JwtTokenService(encoder, PROPERTIES, clock);
    }

    private String encode(JwtEncoder source, String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("user-1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("USER"))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return source.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private JwtEncoder foreignEncoder() {
        byte[] key = new byte[32];
        key[0] = 1;
        JwtProperties foreign = new JwtProperties(
                Base64.getEncoder().encodeToString(key), "upcycle-test", Duration.ofMinutes(30));
        return securityConfig.jwtEncoder(foreign);
    }
}
