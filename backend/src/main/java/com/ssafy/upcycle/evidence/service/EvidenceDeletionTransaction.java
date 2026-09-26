package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import com.ssafy.upcycle.material.entity.MaterialEventReferenceType;
import com.ssafy.upcycle.material.entity.MaterialEventType;
import com.ssafy.upcycle.material.service.MaterialEventRecorder;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 증빙 소프트 삭제와 Material 사건 기록을 하나의 DB 트랜잭션으로 묶는다. */
@Service
@RequiredArgsConstructor
public class EvidenceDeletionTransaction {

    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceAccessPolicy accessPolicy;
    private final EvidenceAnchorPolicy anchorPolicy;
    private final MaterialEventRecorder eventRecorder;
    private final Clock clock;

    @Transactional
    public EvidenceFile markDeleted(Long requesterId, Long evidenceFileId) {
        EvidenceFile evidence = evidenceFileRepository
                .findByIdAndDeletedAtIsNull(evidenceFileId)
                .orElseThrow(() -> new BusinessException(EvidenceErrorCode.EVIDENCE_NOT_FOUND));
        accessPolicy.requireDeletable(requesterId, evidence);
        anchorPolicy.requireNotAnchored(evidence);
        evidence.markDeleted(LocalDateTime.now(clock), requesterId);
        EvidenceFile deleted = evidenceFileRepository.saveAndFlush(evidence);
        if (deleted.getReferenceType() == EvidenceReferenceType.MATERIAL) {
            eventRecorder.record(
                    deleted.getReferenceId(), MaterialEventType.EVIDENCE_DELETED, requesterId,
                    MaterialEventReferenceType.EVIDENCE_FILE, deleted.getId(), null, null,
                    "소재 증빙 삭제",
                    MaterialEventRecorder.key(
                            MaterialEventType.EVIDENCE_DELETED,
                            deleted.getReferenceId(), deleted.getId()));
        }
        return deleted;
    }
}
