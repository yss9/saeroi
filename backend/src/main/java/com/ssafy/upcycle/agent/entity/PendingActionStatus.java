package com.ssafy.upcycle.agent.entity;

/** 제안된 액션이 어떻게 끝났는가. */
public enum PendingActionStatus {

    /** 사용자 응답을 기다리는 중. 대화당 여러 개가 동시에 떠 있을 수 있다. */
    PENDING,

    /** 사용자가 수행했고 결과를 받아 실제 도메인 서비스까지 호출했다. */
    COMPLETED,

    /** 사용자가 거절했다. 오류가 아니라 정상적인 선택이다. */
    REJECTED,

    /** 기한이 지났다. 오래된 제안이 나중에 되살아나는 것을 막는다. */
    EXPIRED
}
