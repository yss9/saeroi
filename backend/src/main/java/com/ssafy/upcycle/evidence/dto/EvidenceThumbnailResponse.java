package com.ssafy.upcycle.evidence.dto;

import com.ssafy.upcycle.evidence.EvidenceApiPaths;
import com.ssafy.upcycle.evidence.entity.EvidenceFile;

/**
 * 대상 하나의 대표 사진. {@code GET /api/evidence/thumbnails} 응답 배열의 한 줄.
 *
 * <p>사진류 증빙만 나온다 ({@code EvidenceType#isPubliclyViewablePhoto}) — 계근표·거래명세서는
 * 대표 사진 후보에서 아예 빠진다. 대상에 사진이 하나도 없으면 그 {@code referenceId} 는
 * 배열에 나타나지 않는다 (빈 자리를 {@code null} 로 채우지 않는다 — {@code EvidenceListResponse}
 * 의 "값이 없다와 가려졌다를 구분한다" 원칙과 같다).
 */
public record EvidenceThumbnailResponse(Long referenceId, Long evidenceFileId, String contentPath) {

    public static EvidenceThumbnailResponse from(EvidenceFile evidence) {
        return new EvidenceThumbnailResponse(
                evidence.getReferenceId(),
                evidence.getId(),
                EvidenceApiPaths.content(evidence.getId()));
    }
}
