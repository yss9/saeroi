package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.dto.ChainEvidenceResponse;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 저장 DB를 거치지 않고 Besu RPC에서 트랜잭션 증거를 다시 조회한다. */
@Service
@RequiredArgsConstructor
public class BlockchainLiveQueryService {

    private final BesuLiveRpcClient rpc;
    private final BlockchainProperties properties;

    public ChainEvidenceResponse findEvidence(String txHash) {
        Instant started = Instant.now();
        JsonNode transaction = rpc.transaction(txHash);
        JsonNode receipt = rpc.receipt(txHash);
        long latest = rpc.latestBlockNumber();
        long blockNumber = BesuLiveRpcClient.hexLong(receipt.path("blockNumber"));
        JsonNode block = rpc.block(blockNumber, false);
        Instant queriedAt = Instant.now();

        return new ChainEvidenceResponse(
                receipt.path("transactionHash").asText(txHash),
                "0x" + Long.toHexString(properties.chainId()),
                blockNumber,
                receipt.path("blockHash").asText(),
                latest,
                Math.max(0, latest - blockNumber + 1),
                BesuLiveRpcClient.hexLong(block.path("timestamp")),
                receipt.path("from").asText(),
                nullableText(receipt.path("to")),
                (int) BesuLiveRpcClient.hexLong(receipt.path("status")),
                hexQuantity(receipt.path("gasUsed").asText("0x0")),
                hexQuantity(transaction.path("value").asText("0x0")),
                transaction.path("input").asText("0x"),
                receipt.path("logs").size(),
                queriedAt,
                Duration.between(started, queriedAt).toMillis());
    }

    private static String nullableText(JsonNode value) {
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static String hexQuantity(String value) {
        if (value == null || !value.matches("^0x[0-9a-fA-F]+$")) return "0";
        return new BigInteger(value.substring(2), 16).toString();
    }
}
