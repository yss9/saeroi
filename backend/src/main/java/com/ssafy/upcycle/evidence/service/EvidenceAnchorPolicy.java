package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 온체인에 고정된 증빙은 지울 수 없다. S15P21E104-162.
 *
 * <h2>왜 막아야 하는가</h2>
 *
 * <p>{@code MaterialToken.verifyEvidence} 는 체인에 기록된 해시와 지금 파일의 해시를
 * 비교한다. 파일 바이트가 사라지면 그 비교를 <b>영구히</b> 할 수 없다 — 체인 쪽은 지울 수
 * 없으므로 "검증 대기" 가 아니라 "검증 불가" 로 굳는다. 그 소재를 사려는 사람이 이력을
 * 확인할 방법이 없어지고, 되돌릴 방법도 없다.
 *
 * <h2>판정이 파일 단위가 아니라 대상 단위인 이유</h2>
 *
 * <p>체인에는 파일마다 해시를 올리지 않는다. 파일 목록을 담은 manifest 의 해시 하나만
 * 올라간다 ({@code ERD.md} Evidence Manifest). 그래서 <b>어느 파일이 그 manifest 안에
 * 들어갔는지 행만 보고는 알 수 없다.</b>
 *
 * <p>모르는 상태에서 하나라도 지우면 manifest 재계산이 불가능해지므로, manifest 가 만들어진
 * 대상의 증빙은 <b>전부</b> 잠근다. 파일 해시와 {@code evidence_hash} 를 직접 비교하는
 * 방식은 틀린다 — 그 둘은 애초에 같은 값이 아니다 (하나는 파일, 하나는 목록의 해시).
 *
 * <h2>고정으로 보는 두 신호</h2>
 *
 * <ul>
 *   <li>{@code material.evidence_hash} 가 있다 — manifest 해시가 계산됐다
 *   <li>{@code material.token_id} 가 있다 — 토큰이 발행됐다. 발행 트랜잭션에 증빙이
 *       함께 들어가므로, manifest 컬럼이 비어 있어도 체인에는 남아 있을 수 있다
 * </ul>
 *
 * <p>둘 중 하나라도 있으면 막는다. <b>모호할 때 막는 쪽으로 기운다</b> — 잘못 막으면
 * 사용자가 문의하면 되지만, 잘못 지우면 복구가 없다.
 */
@Component
@RequiredArgsConstructor
public class EvidenceAnchorPolicy {

    private final MaterialRepository materialRepository;

    /** 온체인에 고정됐으면 {@code 409} 로 거절한다. */
    public void requireNotAnchored(EvidenceFile evidence) {
        switch (evidence.getReferenceType()) {
            case MATERIAL -> requireMaterialNotAnchored(evidence.getReferenceId());
            // 아직 이 대상들에는 증빙을 붙일 수 없어 행이 존재하지 않는다. 그 도메인이
            // 합류할 때 여기 한 줄을 얹기 전까지는 지우지 못하게 둔다 — 고정 여부를 모르는
            // 증빙을 지우는 것이 이 클래스가 막으려는 바로 그 일이다.
            case PROCESSING_JOB, PRODUCTION_BATCH, PRODUCTION_INPUT ->
                    throw new BusinessException(
                            EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_SUPPORTED);
        }
    }

    private void requireMaterialNotAnchored(Long materialId) {
        Material material = materialRepository
                .findById(materialId)
                .orElseThrow(() ->
                        new BusinessException(EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_FOUND));
        if (material.getEvidenceHash() != null || material.getTokenId() != null) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_ANCHORED_ON_CHAIN);
        }
    }
}
