package com.ssafy.upcycle.vision.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 내부 AI 서비스 주소와 HTTP 타임아웃. */
@ConfigurationProperties(prefix = "app.ai")
public record VisionProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout) {

    public VisionProperties {
        baseUrl = baseUrl == null ? "http://localhost:8000" : baseUrl;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }
}
