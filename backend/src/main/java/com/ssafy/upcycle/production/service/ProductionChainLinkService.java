package com.ssafy.upcycle.production.service;

import com.ssafy.upcycle.blockchain.BlockchainErrorCode;
import com.ssafy.upcycle.blockchain.config.BlockchainProperties;
import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.production.ProductionErrorCode;
import com.ssafy.upcycle.production.dto.ProductionChainLinkRequest;
import com.ssafy.upcycle.production.entity.ProductionBatch;
import com.ssafy.upcycle.production.repository.ProductionBatchRepository;
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

    /** 컨트랙트 함수 이름. 검증 판정이 이 값을 보므로 상수로만 넘긴다. */
    private static final String FUNCTION_NAME = "registerProduction";

    private final ProductionBatchRepository batchRepository;
    private final ChainTransactionRecorder recorder;
    private final BlockchainProperties properties;

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
        // 소재·마켓 주소와 별개로 레지스트리 주소가 필요하다. 없는데 기록을 받으면
        // contract_address 가 빈 행이 쌓이고, 나중에 어느 배포분이었는지 알 수 없다.
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
        if (batch.getEvidenceHash() != null) {
            throw new BusinessException(ProductionErrorCode.PRODUCTION_BATCH_ALREADY_ON_CHAIN);
        }

        // 기록이 먼저다. 트랜잭션 해시 형식이 틀려 거절될 요청에 배치만 먼저 바꿔 두면,
        // 롤백은 되지만 "해시는 붙었는데 기록은 없는" 순간이 코드 안에 존재하게 된다.
        recorder.record(
                producerId,
                ChainTransactionRecorder.Contract.PRODUCTION_REGISTRY,
                FUNCTION_NAME,
                BlockchainReferenceType.PRODUCTION,
                batchId,
                request.txHash(),
                request.blockNumber());

        batch.linkManifest(request.evidenceHash(), request.manifestUrl());
    }
}
