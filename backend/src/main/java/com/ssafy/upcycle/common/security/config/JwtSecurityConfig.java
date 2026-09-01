package com.ssafy.upcycle.common.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.time.Clock;
import java.util.Objects;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtSecurityConfig {

    @Bean
    public Clock jwtClock() {
        return Clock.systemUTC();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.secretKey()).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<>("roles", JwtSecurityConfig::doesNotContainPrefixedRole));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private static boolean doesNotContainPrefixedRole(Object rolesClaim) {
        if (rolesClaim instanceof String role) {
            return !role.startsWith("ROLE_");
        }
        if (rolesClaim instanceof Iterable<?> roles) {
            for (Object role : roles) {
                if (role instanceof String value && value.startsWith("ROLE_")) {
                    return false;
                }
            }
        }
        return true;
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        ImmutableSecret<SecurityContext> source = new ImmutableSecret<>(properties.secretKey());
        return new NimbusJwtEncoder(source);
    }
}
