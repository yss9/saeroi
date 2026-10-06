package com.ssafy.upcycle.blockchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.upcycle.blockchain.entity.*;
import com.ssafy.upcycle.blockchain.repository.BlockchainOperationRepository;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;

/**
 * 브라우저 콜백과 WebSocket 이벤트는 빠른 경로일 뿐 정합성의 정본이 아니다.
 * 이 워커는 체인을 직접 다시 읽어 누락된 tx hash와 영수증을 복구하고 DB 투영을 멱등 수행한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainReconciliationWorker {
    static final String OPERATION_RECORDED_TOPIC =
            Hash.sha3String("OperationRecorded(bytes32,bytes4,address)").toLowerCase();
    private static final long LOOKBACK_BLOCKS = 100_000L;
    private static final List<BlockchainOperationStatus> RECEIPT_PENDING =
            List.of(BlockchainOperationStatus.SUBMITTED, BlockchainOperationStatus.DELAYED);

    private final BlockchainOperationRepository operations;
    private final BlockchainTransactionRepository transactions;
    private final BlockchainJsonRpcClient rpc;
    private final Clock clock;

    @Scheduled(
            initialDelayString = "${app.blockchain.reconciliation-initial-delay-ms:10000}",
            fixedDelayString = "${app.blockchain.reconciliation-poll-ms:15000}")
    public void reconcile() {
        recoverMissingHashes();
        LocalDateTime now = LocalDateTime.now(clock);
        operations.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(RECEIPT_PENDING, now)
                .forEach(value -> safely(() -> reconcileReceipt(value.getOperationId())));
        operations.findTop100ByStatusOrderByCreatedAtAsc(BlockchainOperationStatus.CHAIN_CONFIRMED)
                .forEach(value -> safely(() -> project(value.getOperationId())));
    }

    private void recoverMissingHashes() {
        for (BlockchainOperation operation : operations.findTop100ByStatusOrderByCreatedAtAsc(
                BlockchainOperationStatus.CREATED)) {
            safely(() -> {
                long latest = rpc.latestBlockNumber();
                long from = Math.max(0, latest - LOOKBACK_BLOCKS);
                JsonNode logs = rpc.logs(operation.getContractAddress(),
                        List.of(OPERATION_RECORDED_TOPIC, operation.getOperationId()), from, latest);
                if (logs.size() == 0) return;
                JsonNode logNode = logs.get(logs.size() - 1);
                attachRecoveredHash(operation.getOperationId(), logNode.path("transactionHash").asText());
            });
        }
    }

    @Transactional
    public void attachRecoveredHash(String operationId, String txHash) {
        BlockchainOperation operation = operations.findById(operationId).orElse(null);
        if (operation == null || operation.getStatus() != BlockchainOperationStatus.CREATED) return;
        operation.submitted(txHash, LocalDateTime.now(clock));
    }

    @Transactional
    public void reconcileReceipt(String operationId) {
        BlockchainOperation operation = operations.findById(operationId).orElse(null);
        if (operation == null || operation.getTxHash() == null) return;
        LocalDateTime now = LocalDateTime.now(clock);
        JsonNode receipt;
        try { receipt = rpc.transactionReceiptOrNull(operation.getTxHash()); }
        catch (RuntimeException unavailable) {
            operation.delayed(now, nextAttempt(now, operation.getAttemptCount()));
            return;
        }
        if (receipt == null) {
            operation.delayed(now, nextAttempt(now, operation.getAttemptCount()));
            return;
        }
        if (!"0x1".equals(receipt.path("status").asText())) {
            operation.failed("transaction reverted on chain", now);
            return;
        }
        if (!same(operation.getContractAddress(), receipt.path("to").asText())
                || !same(operation.getActorWallet(), receipt.path("from").asText())) {
            operation.inconsistent("receipt contract or signer does not match operation", now);
            return;
        }
        JsonNode event = findOperationEvent(receipt.path("logs"), operation);
        if (event == null) {
            operation.inconsistent("OperationRecorded event is missing", now);
            return;
        }
        operation.confirmed(hexLong(receipt.path("blockNumber").asText()),
                Math.toIntExact(hexLong(event.path("logIndex").asText())), now);
    }

    @Transactional
    public void project(String operationId) {
        BlockchainOperation operation = operations.findById(operationId).orElse(null);
        if (operation == null || operation.getStatus() != BlockchainOperationStatus.CHAIN_CONFIRMED) return;
        if (!transactions.existsByChainIdAndTxHashAndLogIndex(operation.getChainId(),
                operation.getTxHash(), operation.getLogIndex())) {
            transactions.save(BlockchainTransaction.confirmed(operation.getChainId(),
                    operation.getContractAddress(), operation.getTxHash(), operation.getReferenceType(),
                    operation.getReferenceId(), operation.getFunctionName(), operation.getActorWallet(),
                    operation.getBlockNumber(), operation.getLogIndex(), operation.getConfirmedAt()));
        }
        operation.projected(LocalDateTime.now(clock));
    }

    private JsonNode findOperationEvent(JsonNode logs, BlockchainOperation operation) {
        if (!logs.isArray()) return null;
        for (JsonNode value : logs) {
            JsonNode topics = value.path("topics");
            if (same(operation.getContractAddress(), value.path("address").asText())
                    && topics.isArray() && topics.size() >= 3
                    && OPERATION_RECORDED_TOPIC.equalsIgnoreCase(topics.get(0).asText())
                    && operation.getOperationId().equalsIgnoreCase(topics.get(1).asText())
                    && selectorMatches(operation.getExpectedSelector(), topics.get(2).asText())) return value;
        }
        return null;
    }
    private LocalDateTime nextAttempt(LocalDateTime now, int attempts) {
        long seconds = Math.min(300, 5L << Math.min(attempts, 6));
        return now.plusSeconds(seconds);
    }
    private long hexLong(String value) {
        if (value == null || !value.startsWith("0x")) throw new IllegalArgumentException("invalid hex");
        return new BigInteger(value.substring(2), 16).longValueExact();
    }
    private boolean same(String left, String right) { return left != null && right != null && left.equalsIgnoreCase(right); }
    private boolean selectorMatches(String selector, String topic) {
        return selector != null && topic != null && topic.length() >= 10
                && selector.equalsIgnoreCase(topic.substring(0, 10));
    }
    private void safely(Runnable task) {
        try { task.run(); } catch (RuntimeException failure) { log.warn("Blockchain reconciliation item failed", failure); }
    }
}
