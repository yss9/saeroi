// Material 도메인 타입.
//
// 문자열 값은 Backend Enum 과 **정확히** 일치시킨다 (backend/src/main/resources/schema.sql).
// 프론트에서 임의로 이름을 바꾸면 서버 응답을 그대로 화면 상태로 쓸 수 없게 된다.

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

export interface MaterialDetail extends MaterialSummary {
  description: string | null
  lengthMm: number | null
  widthMm: number | null
  registrantId: number
  createdAt: string
}

/** 목록 조회 필터. Query Key 에 그대로 들어가므로 직렬화 가능해야 한다. */
export interface MaterialListFilters {
  materialType?: string
  rootOrigin?: MaterialRootOrigin
  status?: MaterialStatus
  page?: number
  size?: number
}

export interface CreateMaterialRequest {
  materialCode: string
  materialType: MaterialType
  name: string
  rootOrigin: MaterialRootOrigin
  generationType: MaterialGenerationType
  totalQuantity: number
  description?: string
  grade?: MaterialGrade
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
