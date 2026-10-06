package com.ssafy.upcycle.blockchain.dto;

import com.ssafy.upcycle.blockchain.entity.BlockchainContract;
import com.ssafy.upcycle.blockchain.entity.BlockchainOperation;
import com.ssafy.upcycle.blockchain.entity.BlockchainOperationStatus;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import java.time.LocalDateTime;

public record BlockchainOperationResponse(
        String operationId, BlockchainOperationStatus status, BlockchainContract contract,
        String contractAddress, String functionName, BlockchainReferenceType referenceType,
        Long referenceId, String txHash, Long blockNumber, Integer logIndex,
        int attemptCount, String errorMessage, LocalDateTime createdAt,
        LocalDateTime confirmedAt, LocalDateTime projectedAt) {
    public static BlockchainOperationResponse from(BlockchainOperation value) {
        return new BlockchainOperationResponse(value.getOperationId(), value.getStatus(),
                value.getContract(), value.getContractAddress(), value.getFunctionName(),
                value.getReferenceType(), value.getReferenceId(), value.getTxHash(),
                value.getBlockNumber(), value.getLogIndex(), value.getAttemptCount(),
                value.getErrorMessage(), value.getCreatedAt(), value.getConfirmedAt(), value.getProjectedAt());
    }
}
