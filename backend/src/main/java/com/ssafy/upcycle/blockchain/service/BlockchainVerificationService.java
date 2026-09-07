package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.material.service.BlockchainVerification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * material 이 선언한 {@link BlockchainVerification} 창구를 blockchain 쪽에서 구현한다.
 *
 * <p>판정은 {@code blockchain_transaction} 한 테이블에서 끝난다. 온체인에서 실제로 확정된
 * 트랜잭션만 이 테이블에 {@code SUCCESS} 로 들어오므로, 여기서 나온 값은 <b>"온체인에
 * 서명이 있다" 와 동의어</b>다.
 *
 * <h2>과소 보고 위험을 알고 있다</h2>
 *
 * <p>이 테이블에 행을 쓰는 주체가 둘인데 둘 다 아직 없다 — Tx 전송 경로(S15P21E104-153,
 * 컨트랙트 주소 S15P21E104-374 대기)와 이벤트 리스너(S15P21E104-205, 다른 담당자).
 * 리스너가 없으면 사용자가 지갑으로 컨트랙트를 직접 호출한 경우를 놓친다.
 *
 * <p><b>방향은 과소 보고다</b> — 검증되지 않은 것을 검증됐다고는 하지 않는다.
 * 추적성에서는 이 방향이 맞다. 반대 방향은 되돌릴 수 없다.
 *
 * <p>오늘 기준으로는 결과가 정확하다. {@code material.token_id} 가 전부 {@code NULL} 이라
 * (mint 결과를 기록하는 confirm 엔드포인트가 없다) 온체인에 없는 소재는 검증 대상 자체가
 * 될 수 없다.
 */
@Service
@RequiredArgsConstructor
public class BlockchainVerificationService implements BlockchainVerification {

    /**
     * {@code MaterialToken.verifySource}. 컬럼이 {@code VARCHAR(100)} 이라 문자열 비교인데,
     * 오타가 나면 <b>조용히 항상 false</b> 가 되므로 상수로 한 곳에만 둔다.
     */
    private static final String VERIFY_SOURCE = "verifySource";

    private final BlockchainTransactionRepository transactionRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasVerifiedSource(Long materialId) {
        if (materialId == null) {
            return false;
        }
        return transactionRepository
                .existsByReferenceTypeAndReferenceIdAndFunctionNameAndStatus(
                        BlockchainReferenceType.MATERIAL,
                        materialId,
                        VERIFY_SOURCE,
                        BlockchainTransactionStatus.SUCCESS);
    }
}
