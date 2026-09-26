package com.ssafy.upcycle.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.agent.service.llm.GmsLlmClient;
import com.ssafy.upcycle.agent.service.llm.LlmClient;
import com.ssafy.upcycle.agent.service.llm.OpenAiLlmClient;
import com.ssafy.upcycle.agent.service.llm.UnavailableLlmClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 에이전트용 LLM 클라이언트 설정.
 *
 * <p>{@code @ConfigurationPropertiesScan} 을 쓰지 않는 이 저장소의 관례대로, 프로퍼티를
 * 여기서 직접 등록한다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AgentProperties.class)
@RequiredArgsConstructor
public class AgentConfig {

    private final AgentProperties properties;

    /**
     * 키 유무를 기동 로그에 남긴다.
     *
     * <p>{@code deploy/scripts/health-check.sh} 는 컨테이너가 살아 있는지만 보므로, 키가 비어도
     * 배포는 성공한다. 그러면 사용자가 대화를 시도할 때까지 아무도 모른다 — 기동 로그 한 줄이
     * 그 간극을 메운다. {@code blockchain/config/BlockchainConfig} 가 같은 일을 한다.
     */
    @PostConstruct
    void logConfiguration() {
        if (properties.isConfigured()) {
            log.info(
                    "Agent LLM configured: provider={} baseUrl={} model={} thinking={} maxToolIterations={}",
                    properties.provider(),
                    properties.baseUrl(),
                    properties.model(),
                    properties.thinking(),
                    properties.maxToolIterations());
        } else {
            log.warn("Agent LLM NOT configured (app.agent.api-key is empty) — 대화형 에이전트는 503 을 낸다.");
        }
    }

    @Bean
    LlmClient llmClient(RestClient.Builder builder, ObjectMapper objectMapper) {
        if (!properties.isConfigured()) {
            return new UnavailableLlmClient();
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        // 인증 헤더가 공급자마다 다르다. Anthropic 은 x-api-key + 버전 헤더,
        // OpenAI 는 Authorization: Bearer 다. GMS 는 그대로 패스스루한다.
        RestClient.Builder configured = builder.requestFactory(requestFactory)
                .baseUrl(properties.baseUrl());

        if (properties.isAnthropic()) {
            return new GmsLlmClient(
                    properties,
                    configured
                            .defaultHeader("x-api-key", properties.apiKey())
                            .defaultHeader("anthropic-version", "2023-06-01")
                            .build(),
                    objectMapper);
        }
        return new OpenAiLlmClient(
                properties,
                configured.defaultHeader("Authorization", "Bearer " + properties.apiKey()).build(),
                objectMapper);
    }
}
