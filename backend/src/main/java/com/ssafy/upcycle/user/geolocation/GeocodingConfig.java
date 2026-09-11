package com.ssafy.upcycle.user.geolocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 카카오 REST API 키 유무에 따라 실제 주소 변환기를 연결한다. */
@Configuration
@EnableConfigurationProperties(KakaoGeocodingProperties.class)
public class GeocodingConfig {

    private static final Logger log = LoggerFactory.getLogger(GeocodingConfig.class);

    @Bean
    public AddressGeocoder addressGeocoder(
            KakaoGeocodingProperties properties, RestClient.Builder builder) {
        if (!properties.isConfigured()) {
            log.warn("Kakao geocoding REST API key is not set. "
                    + "Business address registration and updates will be rejected.");
            return new UnavailableAddressGeocoder();
        }
        log.info("Kakao geocoding enabled (baseUrl={})", properties.baseUrl());
        return new KakaoAddressGeocoder(properties, builder);
    }
}
