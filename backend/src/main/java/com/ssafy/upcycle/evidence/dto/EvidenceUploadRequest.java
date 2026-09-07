package com.ssafy.upcycle.evidence.dto;

import com.ssafy.upcycle.evidence.entity.EvidenceReferenceType;
import com.ssafy.upcycle.evidence.entity.EvidenceType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 증빙이 무엇에 딸리고 어떤 종류인가.
 *
 * <p>multipart 요청의 <b>폼 필드</b>로 온다 (JSON 본문이 아니다). 파일과 메타데이터를
 * 한 요청에 담아야 하기 때문이다 — 두 요청으로 나누면 파일은 올라갔는데 메타데이터가
 * 실패한 상태가 생기고, 그 파일은 아무도 가리키지 않는 고아가 된다.
 *
 * <p>{@code uploadedBy} 는 여기 없다. JWT {@code sub} 에서 얻는다 — 요청으로 받으면
 * 남의 이름으로 증빙을 올릴 수 있고, 증빙은 온체인에 해시가 남는 기록이다.
 */
public record EvidenceUploadRequest(
        @NotNull(message = "증빙 대상 종류는 필수입니다.")
        EvidenceReferenceType referenceType,

        @NotNull(message = "증빙 대상 id는 필수입니다.")
        @Positive(message = "증빙 대상 id는 양수여야 합니다.")
        Long referenceId,

        @NotNull(message = "증빙 종류는 필수입니다.")
        EvidenceType evidenceType) {
}
