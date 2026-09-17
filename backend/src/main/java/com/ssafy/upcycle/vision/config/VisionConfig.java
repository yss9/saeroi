package com.ssafy.upcycle.vision.config;

import com.ssafy.upcycle.vision.service.WeightVisionClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 저울 사진 인식 전용 HTTP 클라이언트 설정. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VisionProperties.class)
public class VisionConfig {

    @Bean
    WeightVisionClient weightVisionClient(
            VisionProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return new WeightVisionClient(properties, builder.requestFactory(requestFactory));
    }
}
