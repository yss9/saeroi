package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import com.ssafy.upcycle.processing.entity.ProcessingJobStatus;
import com.ssafy.upcycle.processing.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 증빙을 붙일 대상이 실제로 있는지 확인한다.
 *
 * <p><b>DB 가 막아 주지 않는다.</b> {@code evidence_file.reference_id} 에는 FK 가 없다 —
 * 대상 테이블이 넷이라 걸 수 없다. 확인하지 않으면 <b>없는 소재를 가리키는 증빙 행</b>이
 * 남고, 그 파일의 해시는 온체인에 올라갈 수 있다. 그 뒤에는 무엇의 증빙인지 알 방법이 없다.
 *
 * <h2>지금은 MATERIAL 만 받는다</h2>
 *
 * <p>나머지 셋은 엔티티가 없다 — processing 은 견적 요청까지만, production 은 패키지만
 * 들어와 있다. 확인할 수 없는 대상을 통과시키면 위 문제가 그대로 생기므로 거절한다.
 *
 * <p><b>{@code NOT_FOUND} 가 아니라 별도 코드로 거절하는 것이 중요하다.</b> "없는 대상" 과
 * "서버가 아직 못 하는 일" 은 사용자에게 다른 뜻이다. 같은 코드로 묶으면 맞는 요청을
 * 보낸 사용자가 자기 입력을 계속 고친다.
 *
 * <p>각 도메인이 합류하면 자기 확인을 여기에 한 줄씩 얹는다. {@code TradeVerification} 처럼
 * 포트로 뒤집지 않은 이유는, 이건 "존재하는가" 하나만 묻는 질문이고 리포지토리에 이미
 * 그 메서드가 있어 창구를 새로 만들 이득이 없기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class EvidenceReferenceValidator {

    private final MaterialRepository materialRepository;
    private final ProcessingJobRepository processingJobRepository;

    public void requireExists(EvidenceReferenceType referenceType, Long referenceId) {
        boolean exists = switch (referenceType) {
            case MATERIAL -> materialRepository.existsById(referenceId);
            case PROCESSING_JOB -> processingJobRepository.existsById(referenceId);
            case PRODUCTION_BATCH, PRODUCTION_INPUT ->
                    throw new BusinessException(
                            EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_SUPPORTED);
        };
        if (!exists) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_FOUND);
        }
    }

    public void requireUploadable(Long actorId, EvidenceReferenceType referenceType, Long referenceId) {
        requireExists(referenceType, referenceId);
        if (referenceType != EvidenceReferenceType.PROCESSING_JOB) return;
        boolean allowed = processingJobRepository.findById(referenceId)
                .filter(job -> job.getProcessorId().equals(actorId))
                .filter(job -> job.getStatus() == ProcessingJobStatus.PROCESSING
                        || job.getStatus() == ProcessingJobStatus.REJECTED)
                .isPresent();
        if (!allowed) throw new BusinessException(EvidenceErrorCode.EVIDENCE_ACCESS_FORBIDDEN);
    }
}
