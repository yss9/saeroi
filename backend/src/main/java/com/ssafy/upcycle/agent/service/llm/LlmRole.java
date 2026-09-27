package com.ssafy.upcycle.agent.service.llm;

/** LLM 대화에서의 화자. 저장용 {@code AgentMessageRole} 과 달리 SYSTEM 을 포함한다. */
public enum LlmRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
