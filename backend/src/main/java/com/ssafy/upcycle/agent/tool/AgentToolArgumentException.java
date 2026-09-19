package com.ssafy.upcycle.agent.tool;

/**
 * 모델이 넘긴 인자가 쓸 수 없는 형태다.
 *
 * <p>장애가 아니다. 모델에게 무엇이 잘못됐는지 돌려주면 대개 다음 호출에서 고쳐 온다.
 * {@code AgentToolExecutor} 가 이 예외를 잡아 오류 JSON 으로 바꾼다.
 */
public class AgentToolArgumentException extends RuntimeException {

    public AgentToolArgumentException(String message) {
        super(message);
    }
}
