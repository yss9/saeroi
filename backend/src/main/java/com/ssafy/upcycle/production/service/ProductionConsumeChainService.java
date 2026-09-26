package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.entity.MaterialLockStatus;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionConsumeChainRequest;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 소각의 온체인 결과를 DB 에 맞춘다.
 *
 * <h2>왜 필요한가</h2>
 *
 * <p>생산 확정은 소재를 <b>DB 에서만</b> 줄인다. 온체인 토큰은 그대로 남아 있다.
 *
 * <pre>
 *   DB    폐데님 100kg → 70kg   줄어듦
 *   체인  폐데님 100kg → 100kg  그대로
 * </pre>
 *
 * <p>이 프로젝트는 "체인에 적힌 것이 진짜다" 를 파는 서비스다. 그런데 정작 그 소재가
 * <b>쓰였다는 사실이 체인에 없으면</b>, 브랜드는 같은 100kg 으로 또 무언가를 만들었다고
 * 주장할 수 있다 — 같은 소재로 두 번 업사이클한 것이 된다. 추적성에서 이건 치명적이다.
 *
 * <h2>잠금 번호는 2단계에서 이미 받았다</h2>
 *
 * <p>온체인 {@code lockId} 는 {@code submitPlanAndLock} 영수증에서만 나온다. 3단계까지
 * 화면이 들고 오게 하면 그 사이 새로고침 한 번에 잃어버리므로,
 * {@code POST /{id}/chain} 이 받아 {@code material_lock.chain_lock_id} 에 채워 둔다.
 * 여기서는 <b>그 값이 채워져 있는지만</b> 확인한다.
 *
 * <h2>서버는 체인을 읽지 않는다</h2>
 *
 * <p>이벤트 리스너가 아니다. 이 프로젝트는 노드 RPC 클라이언트를 의존성에 넣지 않았고
 * ({@code web3j:crypto} 만 쓴다), 소재 발행·판매·거래·생산 기록이 모두 같은 방식이다 —
 * 화면이 지갑으로 보내고 확정된 결과를 서버가 받아 적는다.
 */
@Service
@RequiredArgsConstructor
public class ProductionConsumeChainService {

    /**
     * {@code ProductionRegistry.confirmProduction}. <b>브랜드가 서명하는 함수는 이것이다.</b>
     *
     * <p>실제로 소각하는 것은 {@code MaterialToken.consumeLockedMaterial} 이지만, 그것은
     * 레지스트리가 내부에서 부르는 호출이라 <b>{@code msg.sender} 가 브랜드가 아니라
     * 레지스트리</b>다. 그 이름으로 기록하면 브랜드가 서명하지 않은 함수를 브랜드가 서명한
     * 것처럼 남기게 되고, 실제 트랜잭션과 대조하면 불일치가 된다.
     *
     * <p>{@code confirmProduction} 한 번이 잠금 소각 · 자투리 발행 · 비율 계산을 모두 한다
     * (컨트랙트가 {@code MaterialConsumed} · {@code ProductionScrapCreated} ·
     * {@code ProductionConfirmed} 를 함께 낸다).
     */
    private static final String FUNCTION_NAME = "confirmProduction";

    private final ProductionBatchRepository batchRepository;
    private final ProductionInputRepository inputRepository;
    private final MaterialLockRepository lockRepository;
    private final BlockchainTransactionRepository transactionRepository;
    private final ChainTransactionRecorder recorder;
    private final BlockchainProperties properties;

    /**
     * 소각 결과를 기록한다. <b>확정된 배치만, 생산자 본인만, 한 번만.</b>
     *
     * <p>확정 전 배치를 막는 이유는 소비 자체가 아직 일어나지 않았기 때문이다 — 소각할
     * 잠금이 {@code CONSUMED} 도 아니다.
     *
     * <p>검증을 모두 끝낸 뒤에 처음으로 무엇인가를 바꾼다. 절반만 연결된 상태 —
     * 잠금 일부에만 온체인 번호가 붙고 기록은 없는 — 를 만들지 않기 위해서다.
     */
    @Transactional
    public void linkConsume(
            Long producerId, Long batchId, ProductionConsumeChainRequest request) {
        recorder.requireConfigured();
        if (!properties.hasProductionRegistry()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }

        ProductionBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        if (!batch.getProducerId().equals(producerId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_FORBIDDEN);
        }
        if (!batch.isConfirmed()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_CONFIRMED);
        }
        requireNotRecorded(batchId);
        requireChainLocksLinked(batchId);

        recorder.record(
                producerId,
                // 소각은 MaterialToken 이 하지만 브랜드가 서명하는 컨트랙트는 레지스트리다.
                ChainTransactionRecorder.Contract.PRODUCTION_REGISTRY,
                FUNCTION_NAME,
                BlockchainReferenceType.MATERIAL,
                batchId,
                request.txHash());
    }

    /**
     * 2단계에서 잠금 번호가 채워졌는가.
     *
     * <p>그 값 없이 소각을 기록하면 <b>어느 온체인 잠금이 소각됐는지</b> 확인할 방법이 없다.
     * 번호는 {@code submitPlanAndLock} 영수증에서만 나오고, 그것을 받는 자리가 2단계 보고다.
     *
     * <p>잠금 상태도 함께 본다. 확정이 {@code CONSUMED} 로 바꿔 두었어야 하고, 아니라면 DB 쪽
     * 소비가 실제로 일어나지 않은 것이다 — 그 상태에서 소각을 기록하면 두 쪽이 더 어긋난다.
     */
    private void requireChainLocksLinked(Long batchId) {
        List<ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId);
        if (inputs.isEmpty()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_NO_INPUT);
        }
        for (ProductionInput input : inputs) {
            MaterialLock lock = lockRepository.findById(input.getMaterialLockId())
                    .orElseThrow(() -> new BusinessException(
                            ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE));
            if (lock.getStatus() != MaterialLockStatus.CONSUMED) {
                throw new BusinessException(ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE);
            }
            if (lock.getChainLockId() == null) {
                throw new BusinessException(
                        ProductionErrorCode.PRODUCTION_CHAIN_LOCKS_NOT_LINKED);
            }
        }
    }

    /**
     * 이미 소각이 기록된 배치인가.
     *
     * <p>{@code blockchain_transaction} 을 본다 — 잠금의 {@code chain_lock_id} 로도 알 수
     * 있지만, 투입이 없는 배치(있을 수 없지만)에서 그 판정이 조용히 통과한다.
     */
    private void requireNotRecorded(Long batchId) {
        boolean recorded = transactionRepository
                .existsByReferenceTypeAndReferenceIdAndFunctionNameAndStatus(
                        BlockchainReferenceType.MATERIAL,
                        batchId,
                        FUNCTION_NAME,
                        BlockchainTransactionStatus.SUCCESS);
        if (recorded) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_CONSUME_ALREADY_RECORDED);
        }
    }
}
