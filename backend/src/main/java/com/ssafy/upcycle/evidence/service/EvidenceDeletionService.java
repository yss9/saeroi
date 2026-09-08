package com.ssafy.upcycle.evidence.service;

import com.ssafy.upcycle.common.exception.BusinessException;
import com.ssafy.upcycle.common.storage.FileStorageService;
import com.ssafy.upcycle.evidence.EvidenceErrorCode;
import com.ssafy.upcycle.evidence.dto.EvidenceFileResponse;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.repository.EvidenceFileRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 증빙 삭제. S15P21E104-162.
 *
 * <h2>정책 — 무엇을 언제 막는가</h2>
 *
 * <table border="1">
 *   <caption>거절 조건</caption>
 *   <tr><th>조건</th><th>코드</th><th>HTTP</th></tr>
 *   <tr><td>행이 없거나 이미 삭제됨</td><td>{@code EVIDENCE_NOT_FOUND}</td><td>404</td></tr>
 *   <tr><td>업로더도 소재 등록자도 아님</td>
 *       <td>{@code EVIDENCE_DELETE_FORBIDDEN}</td><td>403</td></tr>
 *   <tr><td>대상이 온체인에 고정됨</td>
 *       <td>{@code EVIDENCE_ANCHORED_ON_CHAIN}</td><td>409</td></tr>
 *   <tr><td>확인할 수 없는 참조 타입</td>
 *       <td>{@code EVIDENCE_REFERENCE_NOT_SUPPORTED}</td><td>400</td></tr>
 * </table>
 *
 * <p>판정 근거는 {@link EvidenceAccessPolicy} 와 {@link EvidenceAnchorPolicy} 주석에 있다.
 * 핵심은 <b>온체인에 고정된 증빙은 지울 수 없다</b>는 것이다 — 지우면
 * {@code MaterialToken.verifyEvidence} 가 영구히 검증 불가가 된다.
 *
 * <h2>소프트 삭제 — 행은 남고 바이트만 사라진다</h2>
 *
 * <p>하드 삭제는 {@code file_hash} 를 함께 잃는다. 그 값이 나중에 온체인 manifest 에서
 * 발견되면 <b>무엇의 해시인지 알 수 없는 상태</b>가 되고, 되돌릴 수 없다.
 *
 * <p>사용자가 원하는 것은 파일이 없어지는 것이고, 우리가 잃어선 안 되는 것은 그 해시가
 * 존재했다는 사실이다. 그래서 행은 {@code deleted_at} 으로 표시하고 저장소의 바이트는
 * 실제로 지운다. 남는 것은 행 하나뿐이라 비용도 그만큼이다.
 *
 * <h2>순서 — 행을 먼저 커밋하고 바이트를 지운다</h2>
 *
 * <p>반대로 하면 <b>바이트를 지운 뒤 행 저장이 실패</b>했을 때 살아 있는 행이 없는 파일을
 * 가리킨다. 그 증빙을 여는 모든 화면이 깨지고, 파일은 복구할 수 없다.
 *
 * <p>이 순서에서 사고가 나면 <b>고아 객체</b>가 남는다. 디스크만 쓰고 아무도 그것을 가리키지
 * 않으며 접두사({@code MATERIAL/{id}/})로 찾을 수 있다. 업로드 정책 문서가 고른 것과 같은
 * 방향이다 — 고아 객체는 비용이고, 없는 파일을 가리키는 행은 고장이다.
 *
 * <p><b>그래서 이 메서드에 {@code @Transactional} 을 붙이지 않았다.</b> 붙이면 저장소 삭제가
 * 트랜잭션 안으로 들어와 위 순서가 무너진다 (커밋 전에 바이트가 사라진다). 행 변경은
 * 한 건이라 {@code saveAndFlush} 자체의 트랜잭션으로 충분하다.
 */
@Service
@RequiredArgsConstructor
public class EvidenceDeletionService {

    private final EvidenceFileRepository evidenceFileRepository;
    private final EvidenceAccessPolicy accessPolicy;
    private final EvidenceAnchorPolicy anchorPolicy;
    private final FileStorageService fileStorageService;
    private final Clock clock;

    /**
     * 증빙 한 건을 지운다.
     *
     * @param requesterId JWT {@code sub}. 요청 바디로 받지 않는다 — 받으면 남의 이름으로
     *     증빙을 지울 수 있고, 그 기록이 감사에서 유일한 단서다
     * @return 삭제 표시가 반영된 증빙. {@code deletedAt} 이 채워져 있다
     */
    public EvidenceFileResponse delete(Long requesterId, Long evidenceFileId) {
        EvidenceFile evidence = evidenceFileRepository
                .findByIdAndDeletedAtIsNull(evidenceFileId)
                .orElseThrow(() -> new BusinessException(EvidenceErrorCode.EVIDENCE_NOT_FOUND));

        // 권한을 먼저 본다. 순서를 뒤집으면 권한 없는 요청자가 응답 코드로 그 소재가
        // 온체인에 올라갔는지 알아낼 수 있다 (403 대신 409 가 오는 것으로).
        accessPolicy.requireDeletable(requesterId, evidence);
        anchorPolicy.requireNotAnchored(evidence);

        String storageKey = evidence.getFileUrl();
        evidence.markDeleted(LocalDateTime.now(clock), requesterId);
        EvidenceFile deleted = evidenceFileRepository.saveAndFlush(evidence);

        // 실패해도 예외를 던지지 않는 계약이다 (FileStorageService#delete). 여기서 실패하면
        // 고아 객체가 남을 뿐이고, 행은 이미 삭제로 표시돼 아무도 그 파일을 열지 못한다.
        fileStorageService.delete(storageKey);
        return EvidenceFileResponse.from(deleted);
    }
}
