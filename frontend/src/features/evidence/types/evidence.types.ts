/**
 * 증빙 파일. Backend `evidence_file` 테이블과 `POST /api/evidence` 계약.
 */

/**
 * `evidence_file.reference_type` — 증빙이 무엇에 딸려 있는가.
 *
 * ⚠️ **지금 서버가 받는 것은 `MATERIAL` 하나뿐이다.** 나머지 셋은 대상 존재를 확인할
 * 엔티티가 없어 `EVIDENCE_REFERENCE_NOT_SUPPORTED` 로 거절된다. 타입에는 남겨 두고
 * (스키마 ENUM 이 그렇다) 화면은 소재만 쓴다.
 */
export type EvidenceReferenceType =
  | 'MATERIAL'
  | 'PROCESSING_JOB'
  | 'PRODUCTION_BATCH'
  | 'PRODUCTION_INPUT'

/** `evidence_file.evidence_type` */
export type EvidenceType =
  | 'BEFORE_PHOTO'
  | 'AFTER_PHOTO'
  | 'WEIGHT_SLIP'
  | 'INVOICE'
  | 'PROCESS_PHOTO'
  | 'ETC'

export const EVIDENCE_TYPE_LABEL: Record<EvidenceType, string> = {
  BEFORE_PHOTO: '작업 전 사진',
  AFTER_PHOTO: '작업 후 사진',
  WEIGHT_SLIP: '계근표',
  INVOICE: '거래명세서',
  PROCESS_PHOTO: '공정 사진',
  ETC: '기타',
}

/**
 * 소재 등록 단계에서 고를 수 있는 종류.
 *
 * `PROCESS_PHOTO` 는 가공 작업에 딸리는 것이라 소재 화면에 두지 않는다 —
 * 목록에 있으면 사용자가 고르고, 그러면 소재 증빙이 공정 사진으로 분류된다.
 */
export const MATERIAL_EVIDENCE_TYPES: readonly EvidenceType[] = [
  'BEFORE_PHOTO',
  'AFTER_PHOTO',
  'WEIGHT_SLIP',
  'INVOICE',
  'ETC',
]

/**
 * `POST /api/evidence` 응답 항목 · `GET /api/evidence` 의 `files[]` 항목.
 *
 * ⚠️ `storageKey` 는 **URL 이 아니다.** 저장소 루트 기준 상대 경로다.
 * `<img src>` 에 넣으면 깨진 이미지가 된다. 파일 바이트는 `contentPath` 로 받는다.
 *
 * `fileHash` 는 온체인에 기록되는 값이라 그대로 보여준다 — 사용자가 자기 증빙이
 * 체인에 올라간 그것인지 대조할 수 있어야 한다.
 */
export interface EvidenceFile {
  evidenceFileId: number
  storageKey: string
  /**
   * 파일 바이트를 받는 경로 (`GET /api/evidence/{id}/content`).
   *
   * **서버가 만들어 준 값을 그대로 쓴다.** 프론트가 id 로 경로를 조립하면 조립 규칙이
   * 두 곳에 생기고, 서버가 경로를 고칠 때 한쪽이 남는다.
   *
   * 절대 URL 이 아니라 경로인 것은 의도다 — 오리진이 같아서 `/api` 를 그대로 부른다.
   * 이 경로도 **인증과 관계 판정을 거친다** (업로더·소재 등록자·현재 보유자).
   */
  contentPath: string
  fileHash: string
  evidenceType: EvidenceType
  uploadedBy: number
  uploadedAt: string
  /**
   * 삭제 시각. **목록에서는 항상 `null` 이다** — 목록은 살아 있는 증빙만 준다.
   * `DELETE /api/evidence/{id}` 응답에서만 값이 있고, 값이 있다는 것 자체가
   * 삭제가 반영됐다는 증거다.
   */
  deletedAt: string | null
}

/**
 * `GET /api/evidence` 를 **어느 범위까지 보여준 응답인가.** S15P21E104-286 §6-1.
 *
 * 서버가 이 값을 응답에 싣는 이유는 **"증빙이 없는 것" 과 "가려진 것" 이 다르기**
 * 때문이다. 민감 필드를 조용히 `null` 로 내려주면, 계근표가 아직 안 올라온 소재와
 * 남의 소재라 파일명을 못 보는 상태가 화면에서 같은 모양이 된다. 앞의 것은
 * "올려 주세요" 이고 뒤의 것은 "권한이 없습니다" 다.
 */
export type EvidenceListVisibility =
  /** 전체. 업로더 · 소재 등록자 · 소재 현재 보유자 */
  | 'FULL'
  /**
   * 요약만. 그 외 인증 사용자.
   *
   * 파일명(`storageKey`) · `contentPath` · `fileHash` · 업로더 · `evidenceFileId` 가
   * **전부 없다.** `계근표_OO상사.pdf` 라는 이름 하나로 거래 상대가 드러나고 계근표에는
   * 중량·단가가 찍혀 있다. 종류별 개수까지가 추적성이고 그 뒤는 거래 정보다.
   */
  | 'SUMMARY'

/** 증빙 종류 하나의 건수. */
export interface EvidenceTypeCount {
  evidenceType: EvidenceType
  count: number
}

/**
 * `GET /api/evidence` 응답. **배열이 아니라 객체다** (S15P21E104-286 §6-1, BE `!192`).
 *
 * `summary` 는 `FULL` 에도 온다. 전체를 보는 화면도 같은 요약 줄을 쓰라는 뜻이므로
 * **`files` 를 세어 만들지 않는다** — 그러면 요약 규칙이 두 곳에 생기고, 서버가
 * 나중에 목록을 페이징하면 개수가 조용히 어긋난다.
 *
 * `summary` 순서는 서버가 `EvidenceType` 열거 순서로 정해 준다. 클라이언트가 다시
 * 정렬하지 않는다. **0건인 종류는 들어 있지 않다** — 증빙이 하나도 없으면 빈 배열이다.
 *
 * `files` 는 `FULL` 일 때만 있고 `SUMMARY` 면 **키 자체가 없다.** 빈 배열이 아니다 —
 * 빈 배열은 "증빙이 없다" 는 뜻이고 그건 사실이 아니다. 그래서 화면은 `files` 유무가
 * 아니라 `visibility` 로 갈라야 한다.
 */
export interface EvidenceListResponse {
  visibility: EvidenceListVisibility
  files?: EvidenceFile[]
  summary: EvidenceTypeCount[]
}

/**
 * 서버가 받는 파일 형식. `svg` 는 **의도적으로 없다** — 사용자에게는 이미지지만
 * 브라우저에는 스크립트 컨테이너라, 우리 오리진에서 서빙하면 저장형 XSS 가 된다.
 *
 * `accept` 속성에 그대로 쓴다. 이 목록이 서버와 어긋나면 사용자가 고른 파일이
 * 업로드 단계에서 거절된다.
 */
export const EVIDENCE_ACCEPT = '.jpg,.jpeg,.png,.webp,.pdf'

/** 파일 하나 10MB. 정책 문서의 값이고 서버도 같은 값으로 막는다. */
export const EVIDENCE_MAX_FILE_SIZE = 10 * 1024 * 1024

/** 한 요청에 10개. 더 올리려면 여러 번 나눠 올린다 (진행 상황도 눈에 보인다). */
export const EVIDENCE_MAX_FILES = 10
