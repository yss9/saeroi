package com.ssafy.upcycle.agent.service.llm;

import com.ssafy.upcycle.agent.AgentErrorCode;
import com.ssafy.upcycle.common.exception.BusinessException;
import java.util.List;

/**
 * GMS 자격증명이 없을 때 쓰는 대체 구현.
 *
 * <p>기동을 막지 않고 호출 시점에 503 을 낸다. {@code PaymentGatewayConfig} 가
 * KakaoPay 시크릿이 비었을 때 mock 으로 대체하는 것과 같은 방식이다 — 에이전트는 이 앱의
 * 한 기능일 뿐이라, 키가 없다고 해서 소재 등록이나 결제까지 못 뜨게 만들 이유가 없다.
 */
public class UnavailableLlmClient implements LlmClient {

    @Override
    public LlmCompletion complete(List<LlmMessage> messages, List<ToolSpec> tools) {
        throw new BusinessException(AgentErrorCode.AGENT_LLM_UNAVAILABLE);
    }
}
