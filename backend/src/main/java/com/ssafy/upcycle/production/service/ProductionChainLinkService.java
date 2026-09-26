package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionChainBatchRequest;
import com.ssafy.upcycle.production.dto.ProductionChainLinkRequest;
import com.ssafy.upcycle.production.dto.ProductionItemsChainRequest;
import com.ssafy.upcycle.material.entity.MaterialLock;
import com.ssafy.upcycle.material.repository.MaterialLockRepository;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.entity.ProductionInput;
import com.ssafy.upcycle.production.repository.ProductionInputRepository;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
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
 * 생산 배치의 온체인 결과를 DB 에 연결한다.
 *
 * <p>서버가 트랜잭션을 보내지 않는 이유는 소재·거래와 같다 — {@code ProductionRegistry} 를
 * 부르는 서명은 브랜드 지갑의 것이고 서버에 개인키가 없다. 화면이 지갑으로 보내고, 확정된
 * 영수증을 여기로 알려 준다.
 *
 * <h2>이 연결이 없으면 무엇이 끊기는가</h2>
 *
 * <p>소재 발행·판매·거래는 이미 온체인에 남는데 <b>생산만 오프체인 DB 에만 있었다.</b>
 * 그러면 QR Passport 가 증명하는 범위가 소재와 거래에서 멈추고, "이 제품이 그 소재로
 * 만들어졌다" 는 마지막 한 칸은 서버 말을 믿어야 한다. 추적성 플랫폼에서 그 칸이 가장
 * 중요하다.
 *
 * <h2>온체인 번호를 받지 않는다</h2>
 *
 * <p>소재는 {@code token_id}, 판매글은 {@code chain_listing_id}, 주문은
 * {@code chain_trade_id} 를 갖는데 <b>{@code production_batch} 에는 그런 컬럼이 없다.</b>
 * 그래서 번호를 받아도 넣을 자리가 없고, 억지로 받으면 그 숫자가 무엇인지 아무도 모르는
 * 값이 된다. 대신 배치가 이미 가진 {@code evidence_hash} · {@code manifest_url} 을 채운다 —
 * 체인에 올린 manifest 를 나중에 대조할 수 있게 하는 값이다.
 *
 * <p>번호가 필요해지면 그때 컬럼을 추가하는 편이 낫다. 스키마 변경은 팀 전체에 영향이 간다.
 */
@Service
@RequiredArgsConstructor
public class ProductionChainLinkService {

    /**
     * {@code ProductionRegistry.submitPlanAndLock}. <b>evidenceHash 를 인자로 받는 함수가
     * 이것뿐이다</b> — 이 엔드포인트가 받는 값이 그 해시이므로 짝이 맞는다.
     *
     * <p>온체인 순서는 {@code createBatch} → {@code submitPlanAndLock} →
     * {@code confirmProduction} → {@code linkProductItems} 다. 그중 소재를 잠그고 계획
     * 해시를 체인에 남기는 단계가 여기다.
     *
     * <p>검증 판정이 이 문자열을 보므로 상수로만 넘긴다. 컨트랙트에 없는 이름을 적으면
     * 기록은 남는데 실제 트랜잭션과 대조할 때 전부 불일치가 된다.
     */
    private static final String FUNCTION_NAME = "submitPlanAndLock";

    /** {@code ProductionRegistry.createBatch}. 온체인 배치 번호를 채번하는 첫 단계다. */
    private static final String CREATE_BATCH = "createBatch";

    /** {@code ProductionRegistry.linkProductItems}. 개체를 배치에 잇는 마지막 단계다. */
    private static final String LINK_PRODUCT_ITEMS = "linkProductItems";

    private final ProductionBatchRepository batchRepository;
    private final ChainTransactionRecorder recorder;
    private final BlockchainProperties properties;
    private final ProductionManifestService manifestService;
    private final ProductionInputRepository inputRepository;
    private final MaterialLockRepository lockRepository;

    /**
     * {@code createBatch} 결과를 연결한다. <b>온체인 4단계 중 첫 번째다.</b>
     *
     * <p>뒤의 세 단계({@code submitPlanAndLock} · {@code confirmProduction} ·
     * {@code linkProductItems})가 모두 이 번호를 인자로 받는다. 화면이 붙들고 있으면
     * 새로고침 한 번에 잃어버리고, 그러면 이미 체인에 만들어진 배치를 다시 가리킬 수 없다.
     */
    @Transactional
    public void linkChainBatch(
            Long producerId, Long batchId, ProductionChainBatchRequest request) {
        requireConfigured();
        ProductionBatch batch = ownedConfirmedBatch(producerId, batchId);
        if (batch.getChainBatchId() != null) {
            throw new BusinessException(
                    ProductionErrorCode.PRODUCTION_CHAIN_BATCH_ALREADY_LINKED);
        }

        recorder.record(
                producerId,
                ChainTransactionRecorder.Contract.PRODUCTION_REGISTRY,
                CREATE_BATCH,
                BlockchainReferenceType.PRODUCTION,
                batchId,
                request.txHash());
        batch.linkChainBatch(request.chainBatchId());
    }

    /**
     * 온체인 기록을 연결한다. <b>확정된 배치만, 생산자 본인만</b> 할 수 있다.
     *
     * <p>확정 전 배치를 막는 이유는 투입이 더 바뀔 수 있어서다 — 바뀐 뒤에도 체인에는 옛
     * 내용의 해시가 남고, 그 어긋남은 대조할 때만 드러난다.
     *
     * <p>두 번 기록하는 것도 막는다. 두 해시 중 어느 것이 정본인지 알 수 없게 된다.
     */
    @Transactional
    public void link(Long producerId, Long batchId, ProductionChainLinkRequest request) {
        requireConfigured();
        ProductionBatch batch = ownedConfirmedBatch(producerId, batchId);
        if (batch.getChainBatchId() == null) {
            // createBatch 없이는 submitPlanAndLock 을 부를 인자가 없다. 순서를 강제하지
            // 않으면 화면이 존재하지 않는 번호로 서명을 시도하고, 사용자는 지갑 팝업까지
            // 간 뒤에 revert 를 본다.
            throw new BusinessException(ProductionErrorCode.PRODUCTION_CHAIN_BATCH_NOT_LINKED);
        }
        if (batch.getEvidenceHash() != null) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_ALREADY_ON_CHAIN);
        }
        // 화면이 서버가 모르는 내용을 체인에 올리면 그 해시는 나중에 재현할 수 없다.
        manifestService.requireMatchingHash(producerId, batchId, request.evidenceHash());
        List<MaterialLock> locks = matchedLocks(batchId, request);

        // 기록이 먼저다. 트랜잭션 해시 형식이 틀려 거절될 요청에 배치만 먼저 바꿔 두면,
        // 롤백은 되지만 "해시는 붙었는데 기록은 없는" 순간이 코드 안에 존재하게 된다.
        recorder.record(
                producerId,
                ChainTransactionRecorder.Contract.PRODUCTION_REGISTRY,
                FUNCTION_NAME,
                BlockchainReferenceType.PRODUCTION,
                batchId,
                request.txHash());

        for (int i = 0; i < locks.size(); i++) {
            locks.get(i).linkChainLock(request.lockedMaterials().get(i).chainLockId());
        }
        batch.linkManifest(request.evidenceHash(), request.manifestUrl());
    }

    /**
     * 보고된 잠금이 배치의 투입과 정확히 짝이 맞는가.
     *
     * <p><b>전부여야 한다.</b> {@code submitPlanAndLock} 은 투입 전체를 한 번에 잠그므로,
     * 일부만 보고하면 나머지는 온체인 번호를 영영 갖지 못하고 3단계에서 그 잠금이 무엇인지
     * 확인할 수 없다.
     *
     * @return 요청의 순서와 <b>같은 순서로</b> 정렬된 잠금들
     */
    private List<MaterialLock> matchedLocks(Long batchId, ProductionChainLinkRequest request) {
        Map<Long, ProductionInput> inputs = inputRepository.findAllByProductionBatchId(batchId)
                .stream()
                .collect(Collectors.toMap(ProductionInput::getId, Function.identity()));
        if (inputs.size() != request.lockedMaterials().size()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_CONSUME_MISMATCH);
        }

        List<MaterialLock> locks = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (ProductionChainLinkRequest.LockedMaterial locked : request.lockedMaterials()) {
            ProductionInput input = inputs.get(locked.productionInputId());
            if (input == null || !seen.add(locked.productionInputId())) {
                throw new BusinessException(ProductionErrorCode.PRODUCTION_CONSUME_MISMATCH);
            }
            locks.add(lockRepository.findById(input.getMaterialLockId())
                    .orElseThrow(() -> new BusinessException(
                            ProductionErrorCode.PRODUCTION_LOCK_NOT_ACTIVE)));
        }
        return locks;
    }

    /**
     * {@code linkProductItems} 결과를 연결한다. <b>온체인 4단계 중 마지막이다.</b>
     *
     * <p>기록만 남긴다. 개체 번호는 우리 {@code product_item.id} 를 그대로 쓰므로 DB 에
     * 새로 채울 값이 없다 — 온체인에서 따로 채번하지 않는다.
     *
     * <p>소각까지 보고된 뒤에만 받는다. 컨트랙트도 {@code CONFIRMED} 상태를 요구하고, 그
     * 상태는 {@code confirmProduction} 이 만든다.
     */
    @Transactional
    public void linkProductItems(
            Long producerId, Long batchId, ProductionItemsChainRequest request) {
        requireConfigured();
        ProductionBatch batch = ownedConfirmedBatch(producerId, batchId);
        if (batch.getChainBatchId() == null) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_CHAIN_BATCH_NOT_LINKED);
        }

        recorder.record(
                producerId,
                ChainTransactionRecorder.Contract.PRODUCTION_REGISTRY,
                LINK_PRODUCT_ITEMS,
                BlockchainReferenceType.PRODUCTION,
                batchId,
                request.txHash());
    }

    /**
     * 소재·마켓 주소와 별개로 레지스트리 주소가 필요하다. 없는데 기록을 받으면
     * {@code contract_address} 가 빈 행이 쌓이고, 나중에 어느 배포분이었는지 알 수 없다.
     */
    private void requireConfigured() {
        recorder.requireConfigured();
        if (!properties.hasProductionRegistry()) {
            throw new BusinessException(BlockchainErrorCode.BLOCKCHAIN_NOT_CONFIGURED);
        }
    }

    private ProductionBatch ownedConfirmedBatch(Long producerId, Long batchId) {
        ProductionBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(
                        () -> new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_FOUND));
        if (!batch.getProducerId().equals(producerId)) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_FORBIDDEN);
        }
        if (!batch.isConfirmed()) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_NOT_CONFIRMED);
        }
        return batch;
    }
}
