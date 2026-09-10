package com.ssafy.upcycle.blockchain.dto;

import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import java.time.LocalDateTime;

/**
 * 한 대상(Material·Trade·Processing·Production)에 기록된 온체인 트랜잭션 한 건.
 *
 * <p>이벤트 리스너가 없어 {@code SUCCESS} 만 기록되므로({@code 06-blockchain.md} 참고),
 * {@code status} 는 사실상 항상 {@link BlockchainTransactionStatus#SUCCESS}다. 그래도
 * 필드를 그대로 내보낸다 — 엔티티가 이미 세 상태를 구분하고, 화면이 나중에 PENDING·FAILED
 * 를 표시할 경로가 생겨도 응답 모양을 바꾸지 않아도 된다.
 */
public record BlockchainTransactionResponse(
        String functionName,
        String txHash,
        Long blockNumber,
        String fromWallet,
        BlockchainTransactionStatus status,
        LocalDateTime confirmedAt) {

    public static BlockchainTransactionResponse from(BlockchainTransaction transaction) {
        return new BlockchainTransactionResponse(
                transaction.getFunctionName(),
                transaction.getTxHash(),
                transaction.getBlockNumber(),
                transaction.getFromWallet(),
                transaction.getStatus(),
                transaction.getConfirmedAt());
    }
}
