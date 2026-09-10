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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * <h2>온체인 잠금 번호가 여기서 채워진다</h2>
 *
 * <p>{@code material_lock.chain_lock_id} 는 지금까지 아무도 채우지 않았다. DB 잠금은 서버가
 * 만들고 온체인 잠금은 화면이 만들어, 둘을 잇는 코드가 없었기 때문이다. 소각 함수
 * ({@code consumeLockedMaterial})가 온체인 {@code lockId} 를 인자로 받으므로,
 * <b>소각을 보고하는 이 순간이 두 번호가 한자리에 모이는 유일한 지점</b>이다.
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

        List<MaterialLock> locks = matchedLocks(batchId, request);

        // ── 여기까지 검증. 아래에서 처음으로 무엇인가를 바꾼다 ──

        for (int i = 0; i < locks.size(); i++) {
            locks.get(i).linkChainLock(request.consumedLocks().get(i).chainLockId());
        }
        recorder.record(
                producerId,
                // 소각은 MaterialToken 이 하지만 브랜드가 서명하는 컨트랙트는 레지스트리다.
                ChainTransactionRecorder.Contract.PRODUCTION_REGISTRY,
                FUNCTION_NAME,
                BlockchainReferenceType.MATERIAL,
                batchId,
                request.txHash(),
                request.blockNumber());
    }

    /**
     * 보고된 소각이 배치의 투입과 정확히 짝이 맞는가.
     *
     * <p><b>전부여야 한다.</b> 확정은 투입 전체를 한꺼번에 소비하므로, 일부만 소각하고
     * 보고하면 DB 는 다 썼다고 하는데 체인에는 남아 있는 소재가 생긴다 — 그 소재로 또
     * 만들었다고 주장할 수 있다.
     *
     * <p>잠금 id 를 요청에서 받지 않고 투입 id 로 받는 이유는, 잠금을 직접 지목하게 하면
     * <b>남의 잠금을 이 배치의 것으로 보고</b>할 수 있기 때문이다. 투입 행에서 꺼내면 그
     * 요청이 만들어지지 않는다.
     *
     * @return 요청의 순서와 <b>같은 순서로</b> 정렬된 잠금들
     */
    private List<MaterialLock> matchedLocks(
            Long batchId, ProductionConsumeChainRequest request) {
        Map<Long, ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId)
                .stream()
                .collect(Collectors.toMap(ProductionInput::getId, Function.identity()));
        if (inputs.size() != request.consumedLocks().size()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_CONSUME_MISMATCH);
        }

        List<MaterialLock> locks = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ProductionConsumeChainRequest.ConsumedLock consumed : request.consumedLocks()) {
            ProductionInput input = inputs.get(consumed.productionInputId());
            if (input == null || !seen.add(consumed.productionInputId())) {
                // 이 배치의 투입이 아니거나, 같은 투입을 두 번 보고했다.
                throw new BusinessException(ProductionErrorCode.PRODUCTION_CONSUME_MISMATCH);
            }
            MaterialLock lock = lockRepository.findById(input.getMaterialLockId())
                    .orElseThrow(() -> new BusinessException(
                            ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE));
            if (lock.getStatus() != MaterialLockStatus.CONSUMED) {
                // 확정이 CONSUMED 로 바꿔 두었어야 한다. 아니라면 DB 쪽 소비가 실제로
                // 일어나지 않은 것이고, 그 상태에서 소각을 기록하면 두 쪽이 더 어긋난다.
                throw new BusinessException(ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE);
            }
            if (lock.getChainLockId() != null) {
                throw new BusinessException(
                        ProductionErrorCode.PRODUCTION_CONSUME_ALREADY_RECORDED);
            }
            locks.add(lock);
        }
        return locks;
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
