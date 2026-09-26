package com.ssafy.upcycle.agent.tool;

import com.ssafy.upcycle.agent.entity.PendingActionType;

/** 쓰기 tool 이 사용자에게 요구하는 확인의 종류. */
public enum PendingActionKind {

    /** MetaMask 서명. */
    WALLET_SIGN(PendingActionType.WALLET_SIGN),

    /** KakaoPay 결제. */
    PAYMENT(PendingActionType.PAYMENT),

    /** 단순 확인. 되돌리기 어려운 쓰기라 사람이 한 번 봐야 한다. */
    CONFIRM(PendingActionType.CONFIRM);

    private final PendingActionType actionType;

    PendingActionKind(PendingActionType actionType) {
        this.actionType = actionType;
    }

    public PendingActionType actionType() {
        return actionType;
    }
}
