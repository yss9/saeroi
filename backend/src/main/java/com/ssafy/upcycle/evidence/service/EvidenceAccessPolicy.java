package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 누가 증빙 파일을 볼 수 있는가. S15P21E104-162.
 *
 * <h2>왜 인가가 필요한가</h2>
 *
 * <p>증빙은 남의 소재 자료다. 계근표에는 거래 상대·중량·단가가 찍혀 있고, 사진에는 공장
 * 내부가 찍힌다. 로그인만 하면 아무 id 나 넣어 남의 증빙을 훑을 수 있으면 그건 유출이다.
 *
 * <p><b>이것이 presigned URL 을 쓰지 않은 이유다.</b> 서명 URL 은 만료 전까지 토큰 없이
 * 통하므로 이 판정을 붙일 자리가 없다 ({@code FileStorageService#read} 주석).
 *
 * <h2>볼 수 있는 사람 — 세 부류</h2>
 *
 * <ol>
 *   <li><b>올린 사람</b> — 자기가 올린 것이다
 *   <li><b>대상 소재의 등록자</b> ({@code material.registrant_id}) — 그 증빙은 자기 소재를
 *       설명하는 자료다
 *   <li><b>그 소재의 현재 보유자</b> ({@code material_balance}) — 받은 소재의 증빙을 확인
 *       못 하면 <b>추적성이라는 제품의 목적 자체가 성립하지 않는다.</b> 사기 전에는 못 보고
 *       사고 나면 볼 수 있다는 것이 이 규칙의 뜻이다
 * </ol>
 *
 * <p>역할({@code UserRole})로 판정하지 않는다. ADMIN 을 예외로 두면 그 뒤로 예외가 늘고,
 * 무엇보다 <b>역할은 관계를 대신하지 못한다</b> — 두 SUPPLIER 는 서로의 증빙과 무관하다.
 *
 * <h2>삭제는 이보다 좁다</h2>
 *
 * <p>{@link #requireDeletable} 은 보유자를 <b>제외</b>한다. 소재를 사 온 사람이 그 소재의
 * 과거 증빙을 지울 수 있으면 이력을 지우고 되파는 것이 가능해진다.
 */
@Component
@RequiredArgsConstructor
public class EvidenceAccessPolicy {

    private final MaterialRepository materialRepository;
    private final MaterialBalanceRepository materialBalanceRepository;

    /** 조회 권한. 통과하지 못하면 {@code 403}. */
    public void requireReadable(Long requesterId, EvidenceFile evidence) {
        if (isUploader(requesterId, evidence)) {
            return;
        }
        Material material = requireCheckableMaterial(evidence);
        boolean related = isRegistrant(requesterId, material)
                || materialBalanceRepository
                        .findByMaterialIdAndOwnerId(material.getId(), requesterId)
                        .isPresent();
        if (!related) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_ACCESS_FORBIDDEN);
        }
    }

    /**
     * 삭제 권한. 업로더와 소재 등록자만. 통과하지 못하면 {@code 403}.
     *
     * <p>보유자를 넣지 않은 것이 이 메서드의 요점이다 — 위 클래스 주석 참고.
     */
    public void requireDeletable(Long requesterId, EvidenceFile evidence) {
        if (isUploader(requesterId, evidence)) {
            return;
        }
        Material material = requireCheckableMaterial(evidence);
        if (!isRegistrant(requesterId, material)) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_DELETE_FORBIDDEN);
        }
    }

    /**
     * 대상 소재를 읽어 온다. 읽을 수 없으면 <b>거절한다.</b>
     *
     * <p>여기서 통과시키는 실수가 위험하다 — 대상을 확인할 수 없다는 것은 관계를 판정할 수
     * 없다는 뜻이고, 그때 "권한 있음" 으로 기울면 인가가 사라진다. 확인 불가는 거절이다.
     *
     * <p>{@code MATERIAL} 이 아닌 참조 타입은 {@code EvidenceReferenceValidator} 가 업로드에서
     * 막으므로 그런 행은 존재할 수 없다. 그래도 여기서 다시 막는 이유는, 나중에 그 도메인이
     * 합류할 때 <b>인가 규칙을 쓰지 않은 채로</b> 조회가 열리는 것을 막기 위해서다.
     */
    private Material requireCheckableMaterial(EvidenceFile evidence) {
        return switch (evidence.getReferenceType()) {
            case MATERIAL -> materialRepository
                    .findById(evidence.getReferenceId())
                    .orElseThrow(() -> new BusinessException(
                            EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_FOUND));
            case PROCESSING_JOB, PRODUCTION_BATCH, PRODUCTION_INPUT ->
                    throw new BusinessException(
                            EvidenceErrorCode.EVIDENCE_REFERENCE_NOT_SUPPORTED);
        };
    }

    private static boolean isUploader(Long requesterId, EvidenceFile evidence) {
        return evidence.getUploadedBy().equals(requesterId);
    }

    private static boolean isRegistrant(Long requesterId, Material material) {
        return material.getRegistrantId().equals(requesterId);
    }
}
