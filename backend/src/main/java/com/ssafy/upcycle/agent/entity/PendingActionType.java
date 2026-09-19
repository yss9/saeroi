package com.ssafy.upcycle.agent.entity;

/**
 * 서버가 대신 할 수 없어 사용자에게 넘기는 일의 종류.
 *
 * <p>프론트가 이 값으로 어떤 카드를 그릴지 고른다.
 */
public enum PendingActionType {

    /** MetaMask 서명이 필요하다. 개인키는 브라우저에만 있으므로 서버가 대신할 수 없다. */
    WALLET_SIGN,

    /** KakaoPay 결제가 필요하다. 사용자가 PG 화면에서 직접 승인해야 한다. */
    PAYMENT,

    /** 서명·결제는 없지만 되돌리기 어려운 쓰기라 사람이 한 번 확인해야 한다. */
    CONFIRM
}
