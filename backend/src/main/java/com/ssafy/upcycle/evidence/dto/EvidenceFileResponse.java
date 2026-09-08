package com.ssafy.upcycle.evidence.dto;

import com.ssafy.upcycle.evidence.EvidenceApiPaths;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import java.time.LocalDateTime;

/**
 * 업로드된 증빙 한 건.
 *
 * <p>{@code storageKey} 를 그대로 내려준다. <b>저장소 URL 이 아니다</b> — 클라이언트는 그
 * 값으로 파일을 받을 수 없다. 그 값을 노출하는 이유는 온체인 대조 시 어떤 객체를 가리키는지
 * 알아야 하고, 지원 문의에서 같은 이름을 쓰기 위해서다.
 *
 * <p>파일은 {@code contentPath} 로 받는다 ({@code GET /api/evidence/{id}/content},
 * S15P21E104-162). <b>프론트가 경로를 조립하지 않게 하려고 서버가 만들어 준다</b> —
 * 조립 규칙이 두 곳에 있으면 경로를 고칠 때 한쪽이 남는다.
 *
 * <p>절대 URL 이 아니라 경로인 것은 의도적이다. 호스트를 우리가 박아 넣으면 로컬·개발·운영이
 * 다 다르고, 프록시 뒤에서는 서버가 자기 외부 주소를 정확히 모른다. 프론트는 이미
 * 같은 오리진으로 {@code /api} 를 부르고 있다.
 *
 * <p>{@code fileHash} 는 온체인에 기록되는 값이라 그대로 노출한다. 사용자가 자기 증빙이
 * 체인에 올라간 그것인지 대조할 수 있어야 한다.
 *
 * <p>{@code deletedAt} 은 목록에서는 항상 {@code null} 이다 (목록은 살아 있는 것만 보여준다).
 * 삭제 응답에서만 값이 있고, <b>그 값이 있다는 것이 삭제가 반영됐다는 증거</b>다.
 */
public record EvidenceFileResponse(
        Long evidenceFileId,
        String storageKey,
        String contentPath,
        String fileHash,
        EvidenceType evidenceType,
        Long uploadedBy,
        LocalDateTime uploadedAt,
        LocalDateTime deletedAt) {

    public static EvidenceFileResponse from(EvidenceFile evidence) {
        return new EvidenceFileResponse(
                evidence.getId(),
                evidence.getFileUrl(),
                EvidenceApiPaths.content(evidence.getId()),
                evidence.getFileHash(),
                evidence.getEvidenceType(),
                evidence.getUploadedBy(),
                evidence.getUploadedAt(),
                evidence.getDeletedAt());
    }
}
