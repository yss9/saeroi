package com.ssafy.upcycle.agent.service.llm;

/**
 * 모델이 "이 tool 을 이 인자로 불러 달라" 고 요청한 것.
 *
 * @param id 이 호출의 식별자. 결과를 되돌려줄 때 짝을 맞추는 데 쓴다
 * @param name tool 이름
 * @param argumentsJson 인자. 모델이 만든 JSON 문자열이라 파싱 실패를 항상 염두에 둔다
 */
public record ToolCall(String id, String name, String argumentsJson) {}
