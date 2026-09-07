// Material 도메인 타입.
//
// 문자열 값은 Backend Enum 과 **정확히** 일치시킨다 (backend/src/main/resources/schema.sql).
// 프론트에서 임의로 이름을 바꾸면 서버 응답을 그대로 화면 상태로 쓸 수 없게 된다.

import type { MaterialQuantityUnit } from './materialQuantity.ts'

/** `material.root_origin` — 원소재의 최초 출처 */
/**
 * `material.root_origin` — 원소재의 최초 출처. Processing 으로 변하지 않는다.
 *
 * ⚠️ 정책 v3에서 `RECOVERED`(판매 완제품 회수)는 **MVP 범위에서 제외**됐다.
 * 회수 소재의 부모는 Material 이 아니라 ProductItem 이라 Lineage 구조 변경이 선행돼야 한다.
 */
export type MaterialRootOrigin = 'VIRGIN' | 'WASTE'

/** `material.generation_type` — 이 소재가 만들어진 경위 */
export type MaterialGenerationType = 'INITIAL' | 'PROCESSED' | 'PRODUCTION_SCRAP'

/**
 * `material.verification_state` — 이력 검증 단계.
 * 단계가 올라갈수록 더 많은 이력이 확인된 소재다.
 */
export type MaterialVerificationState =
  | 'REGISTERED'
  | 'SOURCE_VERIFIED'
  | 'TRANSACTION_VERIFIED'
  | 'PROCESSING_VERIFIED'
  | 'PRODUCT_LINKED'

/**
 * `material.status`.
 *
 * 참고: 설계 프롬프트의 예시에는 'AVAILABLE' | 'RESERVED' | 'PROCESSING' | 'CONSUMED'
 * 로 되어 있었으나, 실제 Backend 스키마는 ACTIVE/DEPLETED 두 가지이고
 * 예약·가공 중 여부는 `material_lock` 테이블이 따로 관리한다.
 * "Backend Enum 이름을 따른다" 는 규칙을 우선해 스키마 쪽에 맞췄다.
 */
export type MaterialStatus = 'ACTIVE' | 'DEPLETED'

/** 목록 카드에 필요한 만큼만. 상세는 MaterialDetail 을 쓴다. */
export interface MaterialSummary {
  id: number
  materialCode: string
  materialType: MaterialType
  name: string
  rootOrigin: MaterialRootOrigin
  generationType: MaterialGenerationType
  verificationState: MaterialVerificationState
  status: MaterialStatus
  totalQuantity: number
  grade: MaterialGrade | null
}

/**
 * `GET /api/materials/{materialId}` 응답.
 *
 * ⚠️ **`verificationState` 를 신뢰하지 말 것.** 서버가 항상 `'REGISTERED'` 를 준다.
 * `material` 테이블에 그 컬럼이 없고 파생 판정 근거(`signed_at`)도 스키마에 없어서다.
 * 소재의 사실이 아니라 구현의 한계이며, 이 값으로 검증 배지를 그리면
 * **실제로 검증된 소재도 "등록만 됨" 으로 보인다.**
 *
 * `tokenId`·`chainId`·`contractAddress` 가 `null` 인 것은 정상이다 — mint 결과를
 * 기록하는 confirm 엔드포인트가 아직 없다. **`tokenId === null` 이 "등록됐지만
 * 온체인에 없음" 의 유일한 신호다.**
 *
 * `evidenceHash`·`manifestUrl` 도 증빙 등록 경로가 없어 당분간 `null` 이다.
 */
export interface MaterialDetail extends MaterialSummary {
  description: string | null
  lengthMm: number | null
  widthMm: number | null
  registrantId: number
  createdAt: string
  owners: MaterialOwner[]
  evidenceHash: string | null
  manifestUrl: string | null
  tokenId: number | null
  chainId: number | null
  contractAddress: string | null
}

/** 목록 조회 필터. Query Key 에 그대로 들어가므로 직렬화 가능해야 한다. */
export interface MaterialListFilters {
  materialType?: string
  rootOrigin?: MaterialRootOrigin
  status?: MaterialStatus
  page?: number
  size?: number
}

/**
 * `POST /api/materials` 요청 본문. **Backend 계약(MR !99)과 정확히 일치한다.**
 *
 * 빠진 필드가 셋 있고 전부 의도된 것이다.
 *
 * - `materialCode` — 서버가 저장 후 `id` 에서 파생한다(`MAT-%06d`). 보내도 무시된다
 * - `generationType` — 등록은 항상 `INITIAL` 이다. 요청이 지정할 수 있게 하면
 *   가공을 거치지 않은 소재를 "가공됨" 으로 올릴 수 있고, 그러면 신뢰 모델의
 *   전제가 무너진다. 서버가 고정한다
 * - `registrantId` — JWT `sub` 에서 얻는다. 바디로 받으면 남의 계정 이름으로 등록된다
 *
 * `totalQuantity` 가 아니라 **`quantity` + `unit`** 인 것에 주의한다.
 * 저장·응답은 `totalQuantity`(gram 정수)라 이름이 다르다.
 */
export interface CreateMaterialRequest {
  materialType: MaterialType
  rootOrigin: MaterialRootOrigin
  name: string
  /** gram 정수. 소수를 보내면 서버가 400 을 낸다 */
  quantity: number
  unit: MaterialQuantityUnit
  description?: string
  grade?: MaterialGrade
  lengthMm?: number
  widthMm?: number
}

/** `POST /api/materials` 응답. 전체 필드는 상세 조회가 책임진다. */
export interface CreateMaterialResponse {
  materialId: number
  materialCode: string
}

/** 소재 보유자 한 명. `material_balance` 기준이며 등록자와 다르다. */
export interface MaterialOwner {
  ownerId: number
  walletAddress: string
  /** 보유량 (gram). ACTIVE 잠금을 빼지 않은 원값이다 */
  quantity: number
  /** 이 값이 언제 기준인지. 온체인 잔액 Projection 이 아직 동기화되지 않아 중요하다 */
  syncedAt: string
}

export const ROOT_ORIGIN_LABEL: Record<MaterialRootOrigin, string> = {
  VIRGIN: '신소재',
  WASTE: '폐기물',
}

/** `material.material_type` — 정책 v3 부록에서 7종으로 확정 */
export type MaterialType =
  | 'DENIM'
  | 'COTTON'
  | 'LEATHER'
  | 'LINING'
  | 'METAL'
  | 'MIXED'
  | 'ETC'

/**
 * `material.grade` — 정책 2.4절에서 3단계로 확정.
 *
 * 등급은 **Processor 가 매긴다.** Supplier 의 최초 등록 등급은 자가 신고이므로
 * null 을 허용하며 검증된 값으로 취급하지 않는다.
 */
export type MaterialGrade = 'A' | 'B' | 'C'

export const GRADE_LABEL: Record<MaterialGrade, string> = {
  A: 'A · 오염·손상 없음',
  B: 'B · 경미한 오염·손상',
  C: 'C · 파쇄·재생 원료화 대상',
}
