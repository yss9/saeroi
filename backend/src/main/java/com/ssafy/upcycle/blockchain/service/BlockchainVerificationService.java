package com.ssafy.upcycle.blockchain.service;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransaction;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.material.service.BlockchainVerification;
import java.util.Optional;
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
 * <h2>과소 보고 위험은 남아 있다</h2>
 *
 * <p>이 테이블에 행을 쓰는 경로가 이제 하나 생겼다 — {@code MaterialSourceVerificationService}
 * (등록자가 화면에서 지갑 서명을 확정한 뒤 알려주는 경로). 이벤트 리스너(S15P21E104-205,
 * 다른 담당자, 아직 없음)는 여전히 없어서, 사용자가 우리 화면을 거치지 않고 지갑으로
 * 컨트랙트를 직접 호출한 경우는 놓친다.
 *
 * <p><b>방향은 과소 보고다</b> — 검증되지 않은 것을 검증됐다고는 하지 않는다.
 * 추적성에서는 이 방향이 맞다. 반대 방향은 되돌릴 수 없다.
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

    /**
     * 등록자당 소재 하나에 {@code verifySource} 를 두 번 확정할 수 없으므로
     * ({@code MaterialSourceVerificationService} 가 재확정을 막는다) 행은 많아야 하나다.
     * 그래도 "가장 최근" 을 명시해 향후 재검증 경로가 생겨도 이 메서드가 조용히 옛 근거를
     * 돌려주지 않게 한다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<SourceVerificationEvidence> findSourceVerification(Long materialId) {
        if (materialId == null) {
            return Optional.empty();
        }
        return transactionRepository
                .findFirstByReferenceTypeAndReferenceIdAndFunctionNameAndStatusOrderByConfirmedAtDesc(
                        BlockchainReferenceType.MATERIAL,
                        materialId,
                        VERIFY_SOURCE,
                        BlockchainTransactionStatus.SUCCESS)
                .map(BlockchainVerificationService::toEvidence);
    }

    private static SourceVerificationEvidence toEvidence(BlockchainTransaction transaction) {
        return new SourceVerificationEvidence(
                transaction.getFromWallet(), transaction.getTxHash(), transaction.getConfirmedAt());
    }
}
