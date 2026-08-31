package com.ssafy.upcycle.common.security.jwt;

import com.ssafy.upcycle.common.security.config.JwtProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(String subject, Collection<String> roles) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT subject must not be blank");
        }
        if (roles == null) {
            throw new IllegalArgumentException("JWT roles must not be null");
        }

        TreeSet<String> normalizedRoles = new TreeSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("JWT role must not be blank");
            }
            if (role.startsWith("ROLE_")) {
                throw new IllegalArgumentException("JWT role must not have ROLE_ prefix");
            }
            normalizedRoles.add(role);
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.copyOf(normalizedRoles))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, expiresAt);
    }
}
