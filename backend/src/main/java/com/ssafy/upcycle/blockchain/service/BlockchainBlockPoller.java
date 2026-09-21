package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.dto.LiveBlockEvent;
import com.ssafy.upcycle.blockchain.dto.LiveBlockEvent.LiveTransaction;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** HTTP JSON-RPC로 새 블록을 확인하고 SSE 허브에 전달한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BlockchainBlockPoller {

    private static final int MAX_CATCH_UP_BLOCKS = 10;
    private final BesuLiveRpcClient rpc;
    private final BlockchainProperties properties;
    private final BlockchainLiveEventHub eventHub;
    private long lastProcessedBlock = -1;

    @Scheduled(
            initialDelayString = "${app.blockchain.live-initial-delay-ms:2000}",
            fixedDelayString = "${app.blockchain.live-poll-ms:2500}")
    public synchronized void poll() {
        if (!properties.hasRpcUrl()) return;
        try {
            long latest = rpc.latestBlockNumber();
            long first = firstBlockToRead(latest);
            for (long number = first; number <= latest; number++) {
                eventHub.publish(toEvent(rpc.block(number, true)));
                lastProcessedBlock = number;
            }
            eventHub.heartbeat(latest);
        } catch (RuntimeException exception) {
            log.warn("Besu live block polling failed; the next cycle will retry", exception);
        }
    }

    long firstBlockToRead(long latest) {
        if (lastProcessedBlock < 0 || latest < lastProcessedBlock) return latest;
        if (latest <= lastProcessedBlock) return latest + 1;
        long expected = lastProcessedBlock + 1;
        long limited = Math.max(expected, latest - MAX_CATCH_UP_BLOCKS + 1);
        if (limited > expected) {
            log.warn("Skipping {} old Besu blocks while catching up", limited - expected);
        }
        return limited;
    }

    LiveBlockEvent toEvent(JsonNode block) {
        List<LiveTransaction> transactions = new ArrayList<>();
        int projectCount = 0;
        for (JsonNode transaction : block.path("transactions")) {
            LiveTransaction mapped = toTransaction(transaction);
            transactions.add(mapped);
            if (mapped.contractName() != null) projectCount++;
        }
        return new LiveBlockEvent(
                properties.chainId(),
                BesuLiveRpcClient.hexLong(block.path("number")),
                block.path("hash").asText(),
                block.path("parentHash").asText(),
                Instant.ofEpochSecond(BesuLiveRpcClient.hexLong(block.path("timestamp"))),
                List.copyOf(transactions),
                projectCount);
    }

    private LiveTransaction toTransaction(JsonNode transaction) {
        String to = nullableText(transaction.path("to"));
        String input = transaction.path("input").asText("0x");
        String contractName = contractName(to);
        return new LiveTransaction(
                transaction.path("hash").asText(),
                transaction.path("from").asText(),
                to,
                hexQuantity(transaction.path("value").asText("0x0")),
                input.length() >= 10 ? input.substring(0, 10) : null,
                contractName,
                null);
    }

    private String contractName(String address) {
        if (same(address, properties.materialTokenAddress())) return "MaterialToken";
        if (same(address, properties.marketplaceAddress())) return "Marketplace";
        if (same(address, properties.processingRegistryAddress())) return "ProcessingRegistry";
        if (same(address, properties.productionRegistryAddress())) return "ProductionRegistry";
        return null;
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static String nullableText(JsonNode value) {
        return value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private static String hexQuantity(String value) {
        if (!value.toLowerCase(Locale.ROOT).matches("^0x[0-9a-f]+$")) return "0";
        return new BigInteger(value.substring(2), 16).toString();
    }
}
