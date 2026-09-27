package com.ssafy.upcycle.agent.entity;

/** 대화 세션의 수명. */
public enum ConversationStatus {

    /** 진행 중. 메시지를 더 받을 수 있다. */
    ACTIVE,

    /** 여정을 끝까지 마쳤다. */
    COMPLETED,

    /** 사용자가 중단했다. 이력은 남기되 더 이상 진행하지 않는다. */
    ABANDONED
}
