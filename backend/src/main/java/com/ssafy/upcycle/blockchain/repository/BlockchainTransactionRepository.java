package com.ssafy.upcycle.blockchain.repository;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainTransactionRepository
        extends JpaRepository<BlockchainTransaction, Long> {

    boolean existsByChainIdAndTxHashAndLogIndex(Long chainId, String txHash, Integer logIndex);

    /**
     * 한 대상에 기록된 모든 온체인 트랜잭션. 함수 이름을 가리지 않는다 —
     * mint 링크·거래 링크·검증 등 여러 함수가 같은 대상(reference_id)을 가리킬 수 있고,
     * Transaction 상태 조회 화면은 그 전부를 한 번에 보여준다.
     */
    List<BlockchainTransaction> findByReferenceTypeAndReferenceIdOrderByConfirmedAtDesc(
            BlockchainReferenceType referenceType, Long referenceId);

    /**
     * 특정 대상에 대해 특정 함수 호출이 어떤 상태로 기록되어 있는지.
     *
     * <p>네 조건이 <b>모두</b> 필요하다. 하나라도 빠지면 다른 대상이나 다른 함수의
     * 트랜잭션을 이 소재의 검증으로 읽는다 — {@code reference_id} 는 타입마다 따로
     * 매겨지므로 {@code reference_type} 없이 id 만 보면 같은 번호의 거래를 소재로 착각한다.
     */
    boolean existsByReferenceTypeAndReferenceIdAndFunctionNameAndStatus(
            BlockchainReferenceType referenceType,
            Long referenceId,
            String functionName,
            BlockchainTransactionStatus status);

    /** 판정용 {@code exists} 와 같은 조건으로 근거 행 자체를 읽는다 (Blockchain Proof 화면). */
    Optional<BlockchainTransaction> findFirstByReferenceTypeAndReferenceIdAndFunctionNameAndStatusOrderByConfirmedAtDesc(
            BlockchainReferenceType referenceType,
            Long referenceId,
            String functionName,
            BlockchainTransactionStatus status);

    /**
     * 같은 {@code txHash} 가 <b>다른</b> 대상(reference_id)에 이미 성공으로 기록됐는지.
     *
     * <p>실제 블록체인 트랜잭션 해시는 한 번의 실행에만 대응하므로, 같은 해시가 서로
     * 다른 대상에 대해 {@code SUCCESS} 로 두 번 나타나는 것은 항상 위조이거나 클라이언트
     * 버그다 — 정당하게 재사용될 시나리오가 없다. {@code referenceId} 를 제외 조건에 두는
     * 이유는 같은 대상을 다시 확정하는 것(멱등 재시도 등)까지 막으면 안 되기 때문이다 —
     * 그건 호출부의 "이미 검증됨" 검사가 따로 막는다.
     */
    boolean existsByReferenceTypeAndFunctionNameAndTxHashAndStatusAndReferenceIdNot(
            BlockchainReferenceType referenceType,
            String functionName,
            String txHash,
            BlockchainTransactionStatus status,
            Long referenceId);
}
