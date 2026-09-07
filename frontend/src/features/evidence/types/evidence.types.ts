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
 * `POST /api/evidence` · `GET /api/evidence` 응답 항목.
 *
 * ⚠️ `storageKey` 는 **URL 이 아니다.** 저장소 루트 기준 상대 경로이고, 파일을
 * 되돌려주는 엔드포인트는 아직 없다. `<img src>` 에 넣으면 깨진 이미지가 된다.
 *
 * `fileHash` 는 온체인에 기록되는 값이라 그대로 보여준다 — 사용자가 자기 증빙이
 * 체인에 올라간 그것인지 대조할 수 있어야 한다.
 */
export interface EvidenceFile {
  evidenceFileId: number
  storageKey: string
  fileHash: string
  evidenceType: EvidenceType
  uploadedBy: number
  uploadedAt: string
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
