package com.ssafy.upcycle.blockchain.dto;

import java.time.Instant;
import java.util.List;

/** SSAFY Besu에서 새로 확인한 블록 한 개. SSE의 {@code block} 이벤트로 전달된다. */
public record LiveBlockEvent(
        long chainId,
        long number,
        String hash,
        String parentHash,
        Instant timestamp,
        List<LiveTransaction> transactions,
        int projectTransactionCount) {

    public record LiveTransaction(
            String hash,
            String from,
            String to,
            String value,
            String selector,
            String contractName,
            String functionName) {}
}
