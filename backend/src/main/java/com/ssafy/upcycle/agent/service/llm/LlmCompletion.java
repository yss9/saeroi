package com.ssafy.upcycle.agent.service.llm;

import java.util.List;

/**
 * 모델의 한 번 응답.
 *
 * @param content 사용자에게 보여줄 말. tool 만 부르는 턴에서는 비어 있을 수 있다
 * @param toolCalls 이번 턴에 부르기로 한 tool 들. 비어 있으면 이 턴이 대화의 끝이다
 */
public record LlmCompletion(String content, List<ToolCall> toolCalls) {

    public LlmCompletion {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
