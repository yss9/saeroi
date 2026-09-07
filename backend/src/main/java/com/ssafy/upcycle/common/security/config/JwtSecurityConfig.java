package com.ssafy.upcycle.common.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
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

    // Clock 빈은 여기 있었다 (jwtClock). 이름과 위치가 "JWT 전용" 처럼 보여 systemUTC 가
    // 무해해 보였지만, 실제로는 12개 클래스가 주입받고 그중 11개는 LocalDateTime.now(clock) 을
    // 쓴다 — 시간대에 따라 값이 바뀌는 쪽이다. 시간 기준은 보안 설정의 세부사항이 아니라
    // 애플리케이션 전체의 계약이므로 common.config.ClockConfig 로 옮겼다.

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
