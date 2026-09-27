package com.ssafy.upcycle.agent.tool;

import com.ssafy.upcycle.agent.entity.AgentConversation;

/**
 * tool 이 실행되는 맥락.
 *
 * <p>{@code userId} 를 모델이 준 인자가 아니라 여기서만 읽는다. 모델이 인자로 사용자 id 를
 * 넣게 두면 다른 사람의 id 를 지어내는 순간 인가가 뚫린다.
 *
 * @param userId 인증된 호출자. 항상 대화의 소유자다
 * @param conversation 진행 중인 대화. tool 이 단계를 바꾸거나 요구사항을 갱신할 때 쓴다
 */
public record AgentToolContext(Long userId, AgentConversation conversation) {}
