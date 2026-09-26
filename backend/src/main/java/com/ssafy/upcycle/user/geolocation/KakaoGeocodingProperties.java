package com.ssafy.upcycle.user.geolocation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 카카오맵 주소 검색 REST API 설정. */
@ConfigurationProperties(prefix = "app.geocoding.kakao")
public record KakaoGeocodingProperties(
        String baseUrl,
        String restApiKey,
        boolean backfillEnabled) {

    private static final String DEFAULT_BASE_URL = "https://dapi.kakao.com";

    public KakaoGeocodingProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
    }

    public boolean isConfigured() {
        return restApiKey != null && !restApiKey.isBlank();
    }
}
