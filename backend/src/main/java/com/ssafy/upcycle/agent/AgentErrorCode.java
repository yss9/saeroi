package com.ssafy.upcycle.agent;

import com.ssafy.upcycle.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 대화형 에이전트의 공개 오류 계약. */
public enum AgentErrorCode implements ErrorCode {
    AGENT_CONVERSATION_NOT_FOUND(
            HttpStatus.NOT_FOUND, "AGENT_CONVERSATION_NOT_FOUND", "대화를 찾을 수 없습니다."),

    AGENT_CONVERSATION_NOT_ACTIVE(
            HttpStatus.CONFLICT, "AGENT_CONVERSATION_NOT_ACTIVE", "이미 종료된 대화입니다."),

    AGENT_FORBIDDEN(
            HttpStatus.FORBIDDEN, "AGENT_FORBIDDEN", "브랜드 계정만 사용할 수 있는 기능입니다."),

    AGENT_ACTION_NOT_FOUND(
            HttpStatus.NOT_FOUND, "AGENT_ACTION_NOT_FOUND", "요청한 작업을 찾을 수 없습니다."),

    AGENT_ACTION_NOT_PENDING(
            HttpStatus.CONFLICT,
            "AGENT_ACTION_NOT_PENDING",
            "이미 처리되었거나 기한이 지난 작업입니다. 대화에서 다시 요청해주세요."),

    AGENT_TOOL_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "AGENT_TOOL_NOT_AVAILABLE",
            "지금 단계에서 수행할 수 없는 작업입니다."),

    // 루프 상한에 걸린 것은 장애가 아니다. 모델이 스스로 결론을 못 낸 것이므로 사용자에게
    // 되물어 다음 입력을 받는 편이 낫다. 그래서 5xx 가 아니라 422 다.
    AGENT_TOOL_LIMIT_EXCEEDED(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "AGENT_TOOL_LIMIT_EXCEEDED",
            "요청을 정리하지 못했습니다. 조금 더 구체적으로 말씀해주세요."),

    AGENT_LLM_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "AGENT_LLM_UNAVAILABLE",
            "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    AgentErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
