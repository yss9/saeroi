package com.ssafy.upcycle.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대화형 에이전트 설정.
 *
 * <p>{@code apiKey} 가 비어 있으면 기동은 하되 에이전트만 사용 불가가 된다
 * ({@code UnavailableLlmClient}). 필수로 만들지 않는 이유는 이 키가 없다고 나머지 도메인까지
 * 못 뜨게 할 이유가 없어서다.
 *
 * @param provider {@code anthropic} 또는 {@code openai}. GMS 는 통합 규격이 아니라 각 공급자의
 *     원래 API 를 그대로 패스스루하므로, 어느 규격으로 말할지 골라야 한다.
 *     기본값은 모델 이름에서 추론한다 — {@code claude-*} 면 anthropic, 아니면 openai
 * @param baseUrl GMS 엔드포인트. 공급자마다 호스트 구간이 다르다
 *     ({@code /gmsapi/api.anthropic.com} vs {@code /gmsapi/api.openai.com})
 * @param apiKey {@code x-api-key} 자격증명. 비면 에이전트 비활성
 * @param model 모델 식별자
 * @param maxOutputTokens Anthropic Messages API 의 {@code max_tokens} (필수 파라미터다)
 * @param thinking adaptive thinking 사용 여부. Haiku 4.5 는 거부하므로 기본 false
 * @param promptCache 시스템 프롬프트와 tool 명세를 캐싱할지. tool 루프가 한 턴에 같은 접두사를
 *     여러 번 재전송하므로 효과가 크다. GMS 가 {@code cache_control} 을 통과시키지 않으면
 *     400 이 날 수 있어 끌 수 있게 두었다
 * @param maxToolIterations 한 턴에서 허용할 tool 왕복 횟수 상한
 * @param actionTtl 제안된 액션의 유효 기간
 */
@ConfigurationProperties(prefix = "app.agent")
public record AgentProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int maxOutputTokens,
        boolean thinking,
        boolean promptCache,
        int maxToolIterations,
        Duration connectTimeout,
        Duration readTimeout,
        Duration actionTtl) {

    public AgentProperties {
        model = model == null || model.isBlank() ? "claude-haiku-4-5-20251001" : model;

        // 모델 이름으로 규격을 추론한다. 둘을 따로 설정하게 하면 어긋난 채로 배포되고,
        // 그때 나는 오류(400)는 원인이 드러나지 않는다.
        provider = provider == null || provider.isBlank()
                ? (model.startsWith("claude") ? "anthropic" : "openai")
                : provider.trim().toLowerCase();

        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://gms.ssafy.io/gmsapi/api." + provider + ".com"
                : stripTrailingSlash(baseUrl);
        apiKey = apiKey == null ? "" : apiKey.trim();

        // Anthropic Messages API 는 max_tokens 가 필수다. 0 이면 요청이 거부된다.
        maxOutputTokens = maxOutputTokens <= 0 ? 2048 : maxOutputTokens;

        // 상한이 0 이하면 tool 을 한 번도 못 부르고, 크면 한 번의 사용자 발화가 LLM 호출
        // 수십 번으로 번진다(크레딧이 토큰당 차감이라 그대로 비용이다). 4 는 "보유 확인 →
        // 판매글 검색 → 상세" 정도의 연쇄를 한 턴에 소화할 수 있는 크기다.
        maxToolIterations = maxToolIterations <= 0 ? 4 : Math.min(maxToolIterations, 20);

        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        // OCR(30초)보다 길게 잡는다. tool 루프가 아니라 LLM 한 번의 생성 시간이고, 긴 시스템
        // 프롬프트 + 대화 이력이면 수십 초가 걸리는 일이 흔하다.
        readTimeout = readTimeout == null ? Duration.ofSeconds(60) : readTimeout;
        actionTtl = actionTtl == null ? Duration.ofHours(6) : actionTtl;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public boolean isAnthropic() {
        return "anthropic".equals(provider);
    }

    /** 경로를 이어 붙일 때 {@code //} 가 생기지 않게 한다. */
    private static String stripTrailingSlash(String value) {
        String trimmed = value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
