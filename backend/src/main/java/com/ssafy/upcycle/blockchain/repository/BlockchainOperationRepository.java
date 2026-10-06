package com.ssafy.upcycle.blockchain.repository;

import com.ssafy.upcycle.blockchain.entity.BlockchainOperation;
import com.ssafy.upcycle.blockchain.entity.BlockchainOperationStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainOperationRepository extends JpaRepository<BlockchainOperation, String> {
    Optional<BlockchainOperation> findByOperationIdAndUserId(String operationId, Long userId);
    Optional<BlockchainOperation> findByTxHash(String txHash);
    List<BlockchainOperation> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<BlockchainOperationStatus> statuses, LocalDateTime now);
    List<BlockchainOperation> findTop100ByStatusOrderByCreatedAtAsc(BlockchainOperationStatus status);
}
