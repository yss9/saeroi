package com.ssafy.upcycle.agent.service.llm;

import java.util.List;

/**
 * 공급자 중립 대화 메시지.
 *
 * <p>OpenAI 형식을 그대로 쓰지 않고 한 겹 두는 이유는 GMS 의 실제 규격이 확정되지 않았기
 * 때문이다. 규격이 다르면 {@link GmsLlmClient} 안의 변환만 고치면 되고, 대화 저장·툴 루프는
 * 손대지 않는다.
 *
 * @param role assistant/user/tool 중 하나
 * @param content 본문. tool 호출만 하는 턴에서는 null 일 수 있다
 * @param toolCalls 이 턴에서 모델이 부르기로 한 tool 들. 없으면 빈 목록
 * @param toolCallId TOOL 메시지일 때, 어느 호출에 대한 결과인지
 */
public record LlmMessage(
        LlmRole role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public LlmMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(LlmRole.SYSTEM, content, List.of(), null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(LlmRole.USER, content, List.of(), null);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(LlmRole.ASSISTANT, content, List.of(), null);
    }

    public static LlmMessage assistantToolCalls(String content, List<ToolCall> toolCalls) {
        return new LlmMessage(LlmRole.ASSISTANT, content, toolCalls, null);
    }

    public static LlmMessage toolResult(String toolCallId, String resultJson) {
        return new LlmMessage(LlmRole.TOOL, resultJson, List.of(), toolCallId);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
