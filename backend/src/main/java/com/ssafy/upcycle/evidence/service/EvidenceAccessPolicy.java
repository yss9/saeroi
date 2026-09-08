package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.material.entity.Material;
import com.ssafy.upcycle.material.repository.MaterialBalanceRepository;
import com.ssafy.upcycle.material.repository.MaterialRepository;
import java.util.List;
import java.util.Optional;
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
 *
 * <h2>목록도 같은 판정을 쓴다</h2>
 *
 * <p>{@link #canReadFully} 가 {@code GET /api/evidence} 의 응답 범위를 정한다
 * (S15P21E104-286 §6-1). <b>판정 기준을 새로 만들지 않고 이 클래스에 넣은 것이 요점이다</b> —
 * 두 곳에 생기면 {@code /content} 는 되는데 목록은 안 되는(또는 그 반대) 상태가 조용히
 * 생기고, 어느 쪽이 맞는지 말해 주는 것이 없다.
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
        if (!isRelated(requesterId, requireCheckableMaterial(evidence))) {
            throw new BusinessException(EvidenceErrorCode.EVIDENCE_ACCESS_FORBIDDEN);
        }
    }

    /**
     * 목록을 <b>전체로</b> 보여줄 수 있는가. S15P21E104-286 §6-1.
     *
     * <p>{@link #requireReadable} 과 같은 세 부류를 본다. 다만 대상 단위로 판정한다 —
     * 목록은 파일 하나가 아니라 한 대상({@code referenceType} + {@code referenceId})의
     * 질문이다. 업로더는 넘겨받은 행들로 확인한다 (한 건이라도 자기가 올린 것이면 관계가 있다).
     *
     * <p><b>예외를 던지지 않고 {@code boolean} 을 준다.</b> 목록은 관계가 없어도 요약을
     * 돌려주므로 여기서 거절이 일어나지 않는다. 그리고 대상이 없거나 확인할 수 없는
     * 참조 타입이면 <b>{@code false}</b> 다 — {@link #requireCheckableMaterial} 처럼 던지면
     * "대상이 없으면 빈 목록" 이라는 이 엔드포인트의 계약이 404 로 바뀌고,
     * 그 404 는 <b>소재 id 의 존재 여부를 알려주는 신호</b>가 된다.
     * 확인 불가를 {@code true} 로 기울이지 않는 것은 {@link #requireCheckableMaterial} 과 같다.
     *
     * <p>업로더 판정이 넘겨받은 행에 달려 있으므로, <b>자기가 올린 증빙이 모두 지워진
     * 업로더</b>는 (등록자·보유자가 아니라면) 요약 응답을 받는다. 그 응답에 담길 목록이
     * 애초에 없으니 잃는 것이 없다 — 반대로 "과거에 올린 적이 있다" 를 별도 조회로 살려
     * 두면, 지워진 증빙의 존재를 계속 알려주는 경로가 하나 더 생긴다.
     */
    public boolean canReadFully(
            Long requesterId,
            EvidenceReferenceType referenceType,
            Long referenceId,
            List<EvidenceFile> evidence) {
        if (evidence.stream().anyMatch(file -> isUploader(requesterId, file))) {
            return true;
        }
        return findCheckableMaterial(referenceType, referenceId)
                .filter(material -> isRelated(requesterId, material))
                .isPresent();
    }

    /** 소재 등록자이거나 현재 보유자인가. {@code /content} 와 목록이 공유하는 판정이다. */
    private boolean isRelated(Long requesterId, Material material) {
        return isRegistrant(requesterId, material)
                || materialBalanceRepository
                        .findByMaterialIdAndOwnerId(material.getId(), requesterId)
                        .isPresent();
    }

    /**
     * 관계를 판정할 수 있는 대상 소재. 없으면 {@code Optional.empty()}.
     *
     * <p>{@link #requireCheckableMaterial} 과 달리 던지지 않는다. 두 메서드가 같은
     * {@code switch} 를 갖는 것은 중복이 아니라 <b>양쪽 모두 새 참조 타입이 추가되면
     * 컴파일이 깨지게</b> 하려는 것이다 — 그래야 그 도메인이 합류할 때 인가를 쓰지 않은 채로
     * 조회가 열리지 않는다.
     */
    private Optional<Material> findCheckableMaterial(
            EvidenceReferenceType referenceType, Long referenceId) {
        return switch (referenceType) {
            case MATERIAL -> materialRepository.findById(referenceId);
            case PROCESSING_JOB, PRODUCTION_BATCH, PRODUCTION_INPUT -> Optional.empty();
        };
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
