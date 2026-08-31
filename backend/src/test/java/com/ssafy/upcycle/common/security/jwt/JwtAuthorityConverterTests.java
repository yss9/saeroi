package com.ssafy.upcycle.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthorityConverterTests {

    private final JwtAuthorityConverter converter = new JwtAuthorityConverter();

    @Test
    void convertsRolesWithPrefixDeduplicationAndStableOrdering() {
        Jwt jwt = jwtWithRoles(List.of("USER", "ADMIN", "USER"));

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("user-1");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void missingRolesProducesNoAuthorities() {
        assertThat(converter.convert(jwtWithRoles(null)).getAuthorities()).isEmpty();
    }

    private Jwt jwtWithRoles(List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("user-1")
                .issuedAt(Instant.parse("2026-08-31T00:00:00Z"))
                .expiresAt(Instant.parse("2026-08-31T00:30:00Z"));
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }
}
