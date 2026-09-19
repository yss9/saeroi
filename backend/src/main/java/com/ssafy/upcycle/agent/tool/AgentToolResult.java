package com.ssafy.upcycle.agent.tool;

/**
 * tool 실행 결과. 이 값은 그대로 모델에게 되돌아간다.
 *
 * <p>실패를 예외가 아니라 값으로 돌려주는 경우가 많다. 조회 결과가 0건인 것은 장애가 아니라
 * 모델이 알아야 할 사실이고, 예외로 던지면 대화 전체가 503 으로 끊긴다. 대신 모델이 다음
 * 수를 두게 한다("조건을 넓혀 다시 찾을까요?").
 *
 * @param payloadJson 모델에게 줄 JSON
 * @param userVisibleSummary 화면 카드에 쓸 한 줄 요약. 읽기 tool 은 null
 */
public record AgentToolResult(String payloadJson, String userVisibleSummary) {

    public static AgentToolResult of(String payloadJson) {
        return new AgentToolResult(payloadJson, null);
    }

    public static AgentToolResult of(String payloadJson, String userVisibleSummary) {
        return new AgentToolResult(payloadJson, userVisibleSummary);
    }
}
