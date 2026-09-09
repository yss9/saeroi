package com.ssafy.upcycle.blockchain.repository;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockchainTransactionRepository
        extends JpaRepository<BlockchainTransaction, Long> {

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
}
