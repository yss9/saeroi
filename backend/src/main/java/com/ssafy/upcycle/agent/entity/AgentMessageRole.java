package com.ssafy.upcycle.agent.entity;

/**
 * 메시지를 누가 썼는가.
 *
 * <p>SYSTEM 이 없는 것은 의도적이다. 시스템 프롬프트는 대화마다 달라지지 않고 코드에서
 * 매번 조립하므로 저장하지 않는다 — 저장해 두면 프롬프트를 고쳤을 때 예전 대화만 옛 규칙으로
 * 돌아 재현이 안 되는 상태가 된다.
 */
public enum AgentMessageRole {

    /** 사용자 발화. */
    USER,

    /** 모델의 답변. tool 을 부르기로 한 턴이면 content 가 비고 tool_payload_json 만 찬다. */
    ASSISTANT,

    /** tool 실행 결과. 다음 호출에서 모델에게 그대로 되돌려준다. */
    TOOL
}
