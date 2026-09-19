package com.ssafy.upcycle.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception failure) {
            // 우리가 만든 DTO 를 직렬화하는 것이라 여기서 깨지면 코드 버그다.
            throw new IllegalStateException("Failed to serialize tool result for " + name(), failure);
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
