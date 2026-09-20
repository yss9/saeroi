package com.ssafy.upcycle.material.service;

import com.ssafy.upcycle.blockchain.entity.BlockchainReferenceType;
import com.ssafy.upcycle.blockchain.entity.BlockchainTransactionStatus;
import com.ssafy.upcycle.blockchain.repository.BlockchainTransactionRepository;
import com.ssafy.upcycle.blockchain.service.ChainTransactionRecorder;
import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.material.MaterialErrorCode;
import com.ssafy.upcycle.material.dto.MaterialSourceVerificationRequest;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소재 출처 검증({@code verifySource}) 확정을 {@code blockchain_transaction} 에 기록한다.
 *
 * <p><b>여기서 {@code Material} 을 바꾸지 않는다.</b> {@code SOURCE_VERIFIED} 여부는
 * 저장하지 않고 {@link MaterialVerificationStateResolver} 가 이 테이블에서 조회 시점에
 * 계산한다({@code docs/superpowers/specs/2026-09-07-material-verification-state.md}).
 * 이 서비스의 일은 그 판정이 볼 사실 하나를 남기는 것뿐이다 — {@link MaterialChainLinkService}
 * 가 mint 결과만 남기고 material 상태 전이를 별도로 검증 판정에 맡기지 않는 것과 다른
 * 점이다(mint 는 {@code tokenId} 를 저장해야 한다, 출처 검증은 저장할 컬럼이 없다).
 */
@Service
@RequiredArgsConstructor
public class MaterialSourceVerificationService {

    private static final String VERIFY_SOURCE = "verifySource";

    private final MaterialRepository materialRepository;
    private final BlockchainVerification blockchainVerification;
    private final ChainTransactionRecorder recorder;
    private final BlockchainTransactionRepository transactionRepository;
    private final MaterialEventRecorder eventRecorder;

    /**
     * 발행 결과를 연결한다. <b>등록자 본인만 할 수 있다.</b>
     *
     * <p>남이 부를 수 있으면 임의의 소재를 임의의 지갑으로 "검증됐다" 고 만들 수 있다.
     * 등록자 본인의 지갑 서명이어야 "이 출처가 사실이다" 라는 주장에 의미가 생긴다.
     */
    @Transactional
    public void confirm(Long registrantId, Long materialId, MaterialSourceVerificationRequest request) {
        recorder.requireConfigured();

        Material material = materialRepository
                .findById(materialId)
                .orElseThrow(() -> new BusinessException(MaterialErrorCode.MATERIAL_NOT_FOUND));
        if (!material.getRegistrantId().equals(registrantId)) {
            // MaterialChainLinkService(mint 확정)와 같은 코드를 쓴다 — 두 확정 경로가
            // "등록자만" 이라는 같은 규칙을 지키므로 실패도 같은 코드여야 화면이 한
            // 분기로 두 경우를 처리할 수 있다.
            throw new BusinessException(MaterialErrorCode.MATERIAL_ROLE_NOT_ALLOWED);
        }
        if (material.getTokenId() == null) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_NOT_ON_CHAIN);
        }
        if (blockchainVerification.hasVerifiedSource(materialId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_SOURCE_ALREADY_VERIFIED);
        }
        // 실제 Tx 해시는 한 번의 실행에만 대응한다 — 같은 해시가 다른 소재의 verifySource
        // 로 이미 성공 기록되어 있으면 위조이거나 클라이언트 버그다. 같은 소재 재확정은
        // 위 "이미 검증됨" 검사가 이미 막으므로 여기서는 다른 소재(referenceIdNot)만 본다.
        // 저장 시 소문자로 정규화되므로(BlockchainTransaction.requireTxHash) 조회도
        // 소문자로 맞춘다 — 그렇지 않으면 대문자 hex 로 온 재사용을 놓친다.
        if (transactionRepository.existsByReferenceTypeAndFunctionNameAndTxHashAndStatusAndReferenceIdNot(
                BlockchainReferenceType.MATERIAL,
                VERIFY_SOURCE,
                request.txHash().toLowerCase(),
                BlockchainTransactionStatus.SUCCESS,
                materialId)) {
            throw new BusinessException(MaterialErrorCode.MATERIAL_SOURCE_VERIFICATION_TX_HASH_REUSED);
        }

        recorder.record(
                registrantId,
                ChainTransactionRecorder.Contract.MATERIAL_TOKEN,
                VERIFY_SOURCE,
                BlockchainReferenceType.MATERIAL,
                materialId,
                request.txHash());
        eventRecorder.record(
                materialId,
                MaterialEventType.SOURCE_VERIFIED,
                registrantId,
                MaterialEventReferenceType.MATERIAL,
                materialId,
                null,
                null,
                "소재 출처 온체인 검증 확정",
                MaterialEventRecorder.key(MaterialEventType.SOURCE_VERIFIED, materialId, materialId));
    }
}
