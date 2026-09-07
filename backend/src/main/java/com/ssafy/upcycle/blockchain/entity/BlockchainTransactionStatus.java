package com.ssafy.upcycle.blockchain.entity;

/**
 * {@code blockchain_transaction.status}.
 *
 * <p><b>{@link #SUCCESS} 만 "일어난 일" 이다.</b> {@link #PENDING} 은 아직 확정되지
 * 않았고 {@link #FAILED} 는 일어나지 않았다. 검증 단계 판정에서 둘 중 하나라도 인정하면
 * 일어나지 않은 검증을 화면에 표시하게 된다.
 */
public enum BlockchainTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED
}
