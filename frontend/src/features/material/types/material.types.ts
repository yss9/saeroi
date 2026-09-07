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

/** `material_lineage.relation_type` — 자식 소재가 만들어진 경위 */
export type MaterialLineageRelationType = 'PROCESSING' | 'PRODUCTION_SCRAP'

export const LINEAGE_RELATION_LABEL: Record<MaterialLineageRelationType, string> = {
  PROCESSING: '가공',
  PRODUCTION_SCRAP: '생산 자투리',
}

/** 혈통에 등장하는 소재 하나. 같은 소재는 응답에 한 번만 담긴다. */
export interface MaterialLineageNode {
  materialId: number
  materialCode: string
  materialType: MaterialType
  rootOrigin: MaterialRootOrigin
  generationType: MaterialGenerationType
  name: string
  status: MaterialStatus
  /** 조회 대상은 0, 조상은 음수, 자손은 양수 */
  depth: number
}

/** 부모 → 자식 간선 하나. */
export interface MaterialLineageEdge {
  parentMaterialId: number
  childMaterialId: number
  relationType: MaterialLineageRelationType
  referenceId: number
}

/**
 * `GET /api/materials/{materialId}/lineage` 응답.
 *
 * ⚠️ **`truncated` 를 반드시 화면에 반영할 것.** 서버는 재귀 상한(깊이 5 · 노드 200)에
 * 걸리면 일부만 담고 `message` 에 안내 문구를 준다. 그걸 표시하지 않으면 잘린 이력이
 * 전체로 보이는데, 추적성 기능에서 그건 가장 나쁜 종류의 거짓이다.
 *
 * 트리가 아니라 노드 + 간선인 이유는 혈통이 트리가 아니기 때문이다 — 1:N 해체,
 * 자투리 재투입으로 같은 소재가 두 경로에 나타난다. 중첩 트리면 복제되어 화면이
 * 같은 것을 다르게 보여준다.
 */
export interface MaterialLineage {
  materialId: number
  materialCode: string
  nodes: MaterialLineageNode[]
  edges: MaterialLineageEdge[]
  truncated: boolean
  message: string | null
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

/**
 * `GET /api/materials/{materialId}/balance` 의 항목.
 *
 * **{@link MaterialOwner} 와 합치지 않는다.** 상세 조회(`-178`)의 `owners[]` 는
 * `available` 을 주지 않는다 — 그 계산은 별도 티켓(`-378`) 소관이라 상세 응답에
 * 섞지 않았다. 한 타입으로 합치면 상세 응답에서 `available` 이 `undefined` 가 되고
 * 화면이 `0` 으로 렌더해 **"판매 가능량 0" 이라는 거짓**을 보여준다.
 *
 * `available` 은 **서버가 계산해 준다.** 클라이언트에서 다시 계산하지 않는다 —
 * 어떤 잠금 상태를 빼는지(ACTIVE 만)가 서버 규칙이고, 양쪽에서 계산하면 값이 갈라진다.
 *
 * 음수가 올 수 있다. 잠금 합계가 보유량을 넘었다는 데이터 파손 신호이므로
 * **0 으로 깎지 말고 그대로 보여준다.**
 */
export interface MaterialBalance {
  ownerId: number
  walletAddress: string
  /** 보유량 (gram) */
  quantity: number
  /** ACTIVE 잠금 합계 (gram) */
  lockedQuantity: number
  /** quantity − lockedQuantity (gram). 음수 가능 */
  available: number
  /** 이 값이 언제 기준인지. 온체인 잔액 Projection 동기화 전에는 특히 중요하다 */
  syncedAt: string
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
