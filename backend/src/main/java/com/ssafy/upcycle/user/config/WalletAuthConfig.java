package com.ssafy.upcycle.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 이 프로젝트는 {@code @ConfigurationPropertiesScan} 을 쓰지 않는다
 * ({@link com.ssafy.upcycle.common.security.config.JwtSecurityConfig} 참고).
 * 새 프로퍼티 클래스는 이렇게 직접 등록해야 빈으로 잡힌다.
 */
@Configuration
@EnableConfigurationProperties(WalletAuthProperties.class)
public class WalletAuthConfig {
}
