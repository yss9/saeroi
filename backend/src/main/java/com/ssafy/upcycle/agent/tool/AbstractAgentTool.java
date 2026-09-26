package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * tool 구현이 매번 반복하게 되는 것들 — 결과 직렬화와 인자 읽기.
 *
 * <p>인자 접근자를 여기 모아 둔 이유는 <b>모델이 만든 인자가 신뢰할 수 없는 입력</b>이기
 * 때문이다. 타입이 다르거나, 키가 없거나, 숫자 자리에 문자열이 오는 일이 흔하다. 각 tool 이
 * 제각각 방어하면 빠뜨리는 곳이 생기므로 한 곳에서 처리한다.
 */
@RequiredArgsConstructor
public abstract class AbstractAgentTool implements AgentTool {

    protected static final String NO_ARGUMENTS_SCHEMA = """
            {"type":"object","properties":{},"additionalProperties":false}""";

    protected final ObjectMapper objectMapper;

    /**
     * JSON 스키마의 {@code "enum"} 목록을 도메인 enum 에서 만든다.
     *
     * <p><b>손으로 적으면 안 된다.</b> 도메인이 값을 지워도 스키마에 적힌 문자열은 그대로
     * 남고 컴파일도 통과한다. 실제로 {@code MaterialType} 에서 네 값이 빠졌을 때 tool 세
     * 곳의 스키마만 남아, 모델이 없는 값을 계속 제안하고 그 값이 DB 에 남으면 조회가
     * 통째로 500 이 됐다 (2026-09-19 팀 공지).
     *
     * <p>여기서 만들면 값이 사라질 때 스키마도 같이 사라진다.
     */
    protected static String enumValues(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants())
                .map(constant -> '"' + constant.name() + '"')
                .collect(Collectors.joining(","));
    }

    /**
     * DTO 를 필드 맵으로 바꾼다. <b>모델이 읽을 문장 필드를 옆에 붙이려고</b> 쓴다.
     *
     * <p>수량은 gram 정수라 모델이 읽으려면 환산해야 하는데, 그러다 94,000g 을 "94g" 이라고
     * 말했다. 그래서 도구 결과에 "94kg" 같은 문장을 함께 싣는다. DTO 에 필드를 더하지 않는
     * 이유는 그 DTO 를 화면과 다른 기능도 함께 쓰기 때문이다 — 에이전트 때문에 바꾸지 않는다.
     */
    protected Map<String, Object> fields(Object dto) {
        return objectMapper.convertValue(dto, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            // 우리가 만든 DTO 를 직렬화하는 것이라 여기서 깨지면 코드 버그다.
            throw new IllegalStateException("Failed to serialize tool result for " + name(), failure);
        }
    }

    /**
     * 모델이 넘긴 문자열을 enum 으로 읽는다. 없으면 {@code null}(필터 안 걸기).
     *
     * <p><b>거절할 때 다음 수를 함께 준다.</b> JSON Schema 의 {@code enum} 은 강제가 아니라
     * 지시여서 모델이 없는 값을 낼 수 있다 — 실제로 소재 종류에서 {@code LEATHER} 를 냈다
     * (지금 분류에는 없고 {@code ETC} 로 옮겨진 값이다). 그때 "알 수 없는 값" 만 돌려주면
     * 모델이 또 다른 없는 값을 지어내 왕복이 늘고, 왕복은 그대로 크레딧이다.
     *
     * <p>가능한 값 목록과 "애매하면 이 필터를 비우고 keyword 로 찾으라" 를 함께 주면 대개
     * 다음 호출에서 고쳐 온다. 경량 모델에서 차이가 특히 크다.
     */
    protected <E extends Enum<E>> E enumValue(
            JsonNode arguments, String field, Class<E> type) {
        String raw = optionalText(arguments, field);
        if (raw == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new AgentToolArgumentException(
                    "알 수 없는 " + field + " 값입니다: " + raw
                            + ". 가능한 값은 [" + enumValues(type) + "] 입니다. "
                            + "여기에 없는 개념이라면 이 필터를 비우고 keyword 로 찾으세요.");
        }
    }

    /** 없거나 비면 {@code null}. 빈 문자열을 값으로 취급하지 않는다. */
    protected String optionalText(JsonNode arguments, String field) {
        JsonNode node = arguments.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText("").strip();
        return value.isEmpty() ? null : value;
    }

    protected String requiredText(JsonNode arguments, String field) {
        String value = optionalText(arguments, field);
        if (value == null) {
            throw new AgentToolArgumentException(field + " 은(는) 필수입니다.");
        }
        return value;
    }

    /**
     * 정수를 읽는다. 모델이 {@code "1200"} 처럼 문자열로 넣는 일이 잦아 함께 받아준다.
     */
    protected Long optionalLong(JsonNode arguments, String field) {
        JsonNode node = arguments.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.valueOf(node.asText("").strip());
        } catch (NumberFormatException notANumber) {
            throw new AgentToolArgumentException(field + " 은(는) 숫자여야 합니다.");
        }
    }

    protected long requiredLong(JsonNode arguments, String field) {
        Long value = optionalLong(arguments, field);
        if (value == null) {
            throw new AgentToolArgumentException(field + " 은(는) 필수입니다.");
        }
        return value;
    }

    protected long requiredPositiveLong(JsonNode arguments, String field) {
        long value = requiredLong(arguments, field);
        if (value <= 0) {
            throw new AgentToolArgumentException(field + " 은(는) 0보다 커야 합니다.");
        }
        return value;
    }
}
