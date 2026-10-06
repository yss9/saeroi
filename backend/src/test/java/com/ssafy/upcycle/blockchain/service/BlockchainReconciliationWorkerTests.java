package com.ssafy.upcycle.blockchain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.upcycle.blockchain.entity.*;
import com.ssafy.upcycle.blockchain.repository.BlockchainOperationRepository;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlockchainReconciliationWorkerTests {
    private static final String OP = "0x" + "01".repeat(32);
    private static final String TX = "0x" + "02".repeat(32);
    private static final String CONTRACT = "0x" + "03".repeat(20);
    private static final String WALLET = "0x" + "04".repeat(20);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);
    private BlockchainOperationRepository operations;
    private BlockchainTransactionRepository transactions;
    private BlockchainJsonRpcClient rpc;
    private BlockchainReconciliationWorker worker;
    private BlockchainOperation operation;

    @BeforeEach
    void setUp() {
        operations = mock(BlockchainOperationRepository.class);
        transactions = mock(BlockchainTransactionRepository.class);
        rpc = mock(BlockchainJsonRpcClient.class);
        worker = new BlockchainReconciliationWorker(operations, transactions, rpc, clock);
        operation = BlockchainOperation.create(OP, 1L, 31221L, BlockchainContract.MATERIAL_TOKEN,
                CONTRACT, "mintMaterial", BlockchainOperationCatalog.selector(BlockchainContract.MATERIAL_TOKEN, "mintMaterial"), BlockchainReferenceType.MATERIAL, 7L, WALLET,
                LocalDateTime.now(clock));
        operation.submitted(TX, LocalDateTime.now(clock));
        when(operations.findById(OP)).thenReturn(Optional.of(operation));
    }

    @Test
    void nullReceiptIsDelayNotFailure() {
        when(rpc.transactionReceiptOrNull(TX)).thenReturn(null);

        worker.reconcileReceipt(OP);

        assertThat(operation.getStatus()).isEqualTo(BlockchainOperationStatus.DELAYED);
        assertThat(operation.getAttemptCount()).isEqualTo(1);
        assertThat(operation.getNextAttemptAt()).isAfter(LocalDateTime.now(clock));
    }

    @Test
    void successfulOperationEventIsConfirmedAndProjectedIdempotently() throws Exception {
        String topic = BlockchainReconciliationWorker.OPERATION_RECORDED_TOPIC;
        JsonNode receipt = mapper.readTree("""
                {"status":"0x1","to":"%s","from":"%s","blockNumber":"0x64","logs":[
                  {"address":"%s","logIndex":"0x5","topics":["%s","%s","%s"]}
                ]}
                """.formatted(CONTRACT, WALLET, CONTRACT, topic, OP,
                        BlockchainOperationCatalog.selector(BlockchainContract.MATERIAL_TOKEN, "mintMaterial") + "0".repeat(56)));
        when(rpc.transactionReceiptOrNull(TX)).thenReturn(receipt);

        worker.reconcileReceipt(OP);

        assertThat(operation.getStatus()).isEqualTo(BlockchainOperationStatus.CHAIN_CONFIRMED);
        assertThat(operation.getBlockNumber()).isEqualTo(100L);
        assertThat(operation.getLogIndex()).isEqualTo(5);

        when(transactions.existsByChainIdAndTxHashAndLogIndex(31221L, TX, 5)).thenReturn(false);
        worker.project(OP);
        assertThat(operation.getStatus()).isEqualTo(BlockchainOperationStatus.DB_PROJECTED);
        verify(transactions).save(any(BlockchainTransaction.class));
    }

    @Test
    void revertedReceiptIsFinalFailure() throws Exception {
        when(rpc.transactionReceiptOrNull(TX)).thenReturn(mapper.readTree("{\"status\":\"0x0\"}"));
        worker.reconcileReceipt(OP);
        assertThat(operation.getStatus()).isEqualTo(BlockchainOperationStatus.CHAIN_FAILED);
    }
}
