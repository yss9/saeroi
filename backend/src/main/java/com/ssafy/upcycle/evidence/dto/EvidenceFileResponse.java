package com.ssafy.upcycle.evidence.dto;

import com.ssafy.upcycle.evidence.entity.EvidenceFile;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import java.time.LocalDateTime;

/**
 * 업로드된 증빙 한 건.
 *
 * <p>{@code storageKey} 를 그대로 내려준다. <b>공개 URL 을 만들어 주지 않는다</b> —
 * 지금은 파일을 되돌려주는 엔드포인트가 없고(다음 티켓), 없는 URL 을 내려보내면
 * 화면이 깨진 이미지를 그린다. 키만 주면 화면은 "아직 미리보기 불가" 를 정직하게 말할 수 있다.
 *
 * <p>{@code fileHash} 는 온체인에 기록되는 값이라 그대로 노출한다. 사용자가 자기 증빙이
 * 체인에 올라간 그것인지 대조할 수 있어야 한다.
 */
public record EvidenceFileResponse(
        Long evidenceFileId,
        String storageKey,
        String fileHash,
        EvidenceType evidenceType,
        Long uploadedBy,
        LocalDateTime uploadedAt) {

    public static EvidenceFileResponse from(EvidenceFile evidence) {
        return new EvidenceFileResponse(
                evidence.getId(),
                evidence.getFileUrl(),
                evidence.getFileHash(),
                evidence.getEvidenceType(),
                evidence.getUploadedBy(),
                evidence.getUploadedAt());
    }
}
