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
  | 'WEIGHT_PHOTO'
  | 'INVOICE'
  | 'PROCESS_PHOTO'
  | 'ETC'

export const EVIDENCE_TYPE_LABEL: Record<EvidenceType, string> = {
  BEFORE_PHOTO: '작업 전 사진',
  AFTER_PHOTO: '작업 후 사진',
  WEIGHT_SLIP: '계근표',
  WEIGHT_PHOTO: '저울 측정 사진',
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
  'WEIGHT_PHOTO',
  'INVOICE',
  'ETC',
]

/**
 * 가공 작업에 올리는 증빙 — 작업 전 사진과 작업 후 사진 두 가지뿐이다.
 *
 * 받는 쪽이 확인하는 것은 "무엇이 들어와 무엇이 나갔는가" 다. 작업 전 사진은 받은 그대로의
 * 상태라 소재를 수령할 때, 작업 후 사진은 결과를 쓸 때 올린다. 서버도 이 둘 외에는 받지 않고
 * (EvidenceReferenceValidator), 수령과 결과 제출이 각각 그 사진을 요구한다.
 */
export const PROCESSING_EVIDENCE_TYPES: readonly EvidenceType[] = [
  'BEFORE_PHOTO',
  'AFTER_PHOTO',
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
 * `GET /api/evidence/thumbnails` 의 항목 하나 — 대상 하나의 대표 사진.
 *
 * 사진류 증빙만 나온다 (`BEFORE_PHOTO`/`AFTER_PHOTO`/`WEIGHT_PHOTO`/`PROCESS_PHOTO`).
 * 계근표·거래명세서는 대표 사진 후보에 들지 않는다 — 관계 없는 마켓플레이스 구매자도
 * 이 사진은 볼 수 있지만(`EvidenceAccessPolicy` 의 사진류 예외), 문서는 여전히 못 본다.
 *
 * 대상에 사진이 없으면 응답 맵에 그 id가 **없다** — `EvidenceListResponse` 와 같은
 * 원칙으로, "사진 없음" 과 "조회 실패" 를 구분한다.
 */
export interface EvidenceThumbnail {
  referenceId: number
  evidenceFileId: number
  contentPath: string
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

/**
 * 서버에 올라간 증빙을 **어떻게 보여줄 수 있는가.**
 *
 * 허용 목록에 `.pdf` 가 있으므로 모든 증빙을 `<img>` 로 그릴 수는 없다. pdf 를
 * `<img src>` 에 넣으면 깨진 이미지 아이콘이 뜨고, 사용자는 파일이 잘못됐다고 생각한다.
 */
export type EvidenceContentKind = 'IMAGE' | 'PDF'

/**
 * 저장 키의 확장자로 표시 방법을 정한다.
 *
 * <b>`storageKey` 를 보는 것이 맞다.</b> 서버가 만든 값이고(`{...}/{uuid}.{ext}`) 그
 * 확장자는 **업로드 때 실제 내용과 대조해 검증된 것**이다 (정책 문서: 확장자와 미디어
 * 타입이 일치해야 통과한다). 사용자가 준 원본 파일명은 저장 경로에 들어가지 않으므로
 * 여기에 끼어들 수 없다.
 *
 * 알 수 없는 확장자는 `null` 이다 — 추측해서 `<img>` 로 그리면 깨진 이미지가 된다.
 * 화이트리스트가 좁아 실제로는 나올 수 없지만, 정책이 늘어날 때 조용히 깨지지 않게 한다.
 */
export function evidenceContentKind(storageKey: string): EvidenceContentKind | null {
  const dot = storageKey.lastIndexOf('.')
  if (dot < 0) return null
  const extension = storageKey.slice(dot + 1).toLowerCase()
  if (extension === 'pdf') return 'PDF'
  if (['jpg', 'jpeg', 'png', 'webp'].includes(extension)) return 'IMAGE'
  return null
}
