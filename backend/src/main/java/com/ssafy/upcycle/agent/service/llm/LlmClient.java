package com.ssafy.upcycle.agent.service.llm;

import java.util.List;

/**
 * LLM 공급자 교체 지점.
 *
 * <p>구현체는 실패를 {@code AgentErrorCode.AGENT_LLM_UNAVAILABLE} 로 정규화해서 던진다 —
 * 공급자별 예외가 이 경계를 넘어가면 호출부가 공급자를 알아야 하게 된다.
 * ({@code WeightVisionClient} 가 같은 방식으로 실패를 번역한다.)
 */
public interface LlmClient {

    /**
     * @param messages 시스템 프롬프트를 포함한 전체 대화
     * @param tools 이번 호출에서 쓸 수 있는 tool. 빈 목록이면 tool 없이 답만 받는다
     */
    LlmCompletion complete(List<LlmMessage> messages, List<ToolSpec> tools);
}
