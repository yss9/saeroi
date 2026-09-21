package com.ssafy.upcycle.blockchain.dto;

import java.time.Instant;

/** 대시보드가 txHash를 기준으로 Besu에서 다시 읽은 온체인 원본. */
public record ChainEvidenceResponse(
        String hash,
        String chainId,
        long blockNumber,
        String blockHash,
        long latestBlock,
        long confirmations,
        long timestamp,
        String from,
        String to,
        int status,
        String gasUsed,
        String value,
        String input,
        int logCount,
        Instant queriedAt,
        long latencyMs) {}
