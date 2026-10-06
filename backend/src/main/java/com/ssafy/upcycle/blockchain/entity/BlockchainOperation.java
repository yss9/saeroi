package com.ssafy.upcycle.blockchain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "blockchain_operation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlockchainOperation {
    @Id
    @Column(name = "operation_id", columnDefinition = "CHAR(66)")
    private String operationId;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "chain_id", nullable = false) private Long chainId;
    @Enumerated(EnumType.STRING) @Column(name = "contract_name", nullable = false, length = 40)
    private BlockchainContract contract;
    @Column(name = "contract_address", nullable = false, length = 42) private String contractAddress;
    @Column(name = "function_name", nullable = false, length = 100) private String functionName;
    @Column(name = "expected_selector", nullable = false, length = 10) private String expectedSelector;
    @Enumerated(EnumType.STRING) @Column(name = "reference_type", nullable = false)
    private BlockchainReferenceType referenceType;
    @Column(name = "reference_id", nullable = false) private Long referenceId;
    @Column(name = "actor_wallet", nullable = false, length = 42) private String actorWallet;
    @Column(name = "tx_hash", columnDefinition = "CHAR(66)") private String txHash;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30)
    private BlockchainOperationStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "next_attempt_at") private LocalDateTime nextAttemptAt;
    @Column(name = "block_number") private Long blockNumber;
    @Column(name = "log_index") private Integer logIndex;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "projected_at") private LocalDateTime projectedAt;

    public static BlockchainOperation create(String id, Long userId, Long chainId,
            BlockchainContract contract, String address, String functionName, String expectedSelector,
            BlockchainReferenceType referenceType, Long referenceId, String wallet,
            LocalDateTime now) {
        BlockchainOperation value = new BlockchainOperation();
        value.operationId = id.toLowerCase(); value.userId = userId; value.chainId = chainId;
        value.contract = contract; value.contractAddress = address.toLowerCase();
        value.functionName = functionName; value.expectedSelector = expectedSelector; value.referenceType = referenceType;
        value.referenceId = referenceId; value.actorWallet = wallet.toLowerCase();
        value.status = BlockchainOperationStatus.CREATED; value.createdAt = now; value.updatedAt = now;
        return value;
    }

    public void submitted(String hash, LocalDateTime now) {
        if (txHash != null && !txHash.equalsIgnoreCase(hash)) throw new IllegalStateException("operation already has another tx");
        txHash = hash.toLowerCase(); status = BlockchainOperationStatus.SUBMITTED;
        nextAttemptAt = now; updatedAt = now; errorMessage = null;
    }
    public void delayed(LocalDateTime now, LocalDateTime next) {
        attemptCount++; status = BlockchainOperationStatus.DELAYED; updatedAt = now; nextAttemptAt = next;
        errorMessage = "transaction receipt is not available yet";
    }
    public void confirmed(long block, int log, LocalDateTime now) {
        blockNumber = block; logIndex = log; status = BlockchainOperationStatus.CHAIN_CONFIRMED;
        confirmedAt = now; updatedAt = now; nextAttemptAt = null; errorMessage = null;
    }
    public void failed(String reason, LocalDateTime now) {
        status = BlockchainOperationStatus.CHAIN_FAILED; errorMessage = reason; updatedAt = now; nextAttemptAt = null;
    }
    public void inconsistent(String reason, LocalDateTime now) {
        status = BlockchainOperationStatus.INCONSISTENT; errorMessage = reason; updatedAt = now; nextAttemptAt = null;
    }
    public void projected(LocalDateTime now) {
        status = BlockchainOperationStatus.DB_PROJECTED; projectedAt = now; updatedAt = now; errorMessage = null;
    }
}
