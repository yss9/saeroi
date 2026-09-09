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
 * Blockchain Proof 화면(누가·언제·어느 Tx)이 그리는 근거 (S15P21E104-182).
 *
 * `verification` 이 `null` 이면 근거가 아직 없다는 뜻이다 — 빈 객체를 내려주지 않는
 * 이유는 화면이 "값이 있는데 비어 있음" 과 "아직 없음" 을 구분해야 하기 때문이다
 * (`docs/superpowers/specs/2026-09-07-material-verification-state.md`).
 *
 * 지금은 `state`가 `SOURCE_VERIFIED` 일 때만 채워진다. 다른 단(`TRANSACTION_VERIFIED`
 * 등)은 근거가 온체인 Tx 가 아니라 거래 서명이라 이 모양(지갑·Tx 해시)이 맞지 않는다.
 */
export interface MaterialVerification {
  state: MaterialVerificationState
  signerWallet: string
  signedAt: string
  txHash: string
}

/**
 * `GET /api/materials/{materialId}` 응답.
 *
 * `verificationState` 는 조회 시점에 계산된 값이다 — 지금 판정되는 단계는
 * `REGISTERED` · `SOURCE_VERIFIED` · `TRANSACTION_VERIFIED` 셋이다. `verification` 은
 * `SOURCE_VERIFIED` 일 때만 근거를 담고, 그 외에는 `null` 이다(위 `MaterialVerification`
 * 주석 참고).
 *
 * `tokenId`·`chainId`·`contractAddress` 가 `null` 인 것은 정상이다 — mint 확정 전
 * 소재의 상태다. **`tokenId === null` 이 "등록됐지만 온체인에 없음" 의 유일한 신호다.**
 *
 * `evidenceHash`·`manifestUrl` 도 증빙 등록 경로가 없어 당분간 `null` 이다.
 */
export interface MaterialDetail extends MaterialSummary {
  verification: MaterialVerification | null
  /**
   * ⚠️ **상세 응답의 식별자는 `id` 가 아니라 `materialId` 다.**
   *
   * `MaterialSummary` 를 확장하면서 `id` 를 물려받지만 **서버는 그 이름으로 주지 않는다** —
   * `MaterialDetailResponse` 의 첫 필드가 `materialId` 다. 타입만 보고 `data.id` 를 쓰면
   * 컴파일은 통과하고 런타임에 `undefined` 가 되어 `/api/materials/undefined/chain` 같은
   * 요청이 나간다 (2026-09-08 온체인 발행에서 실제로 그렇게 500 이 났다).
   *
   * `id` 를 지우지 않는 이유는 목록(`getMaterials`)이 아직 목이고 그쪽이 `id` 를 쓰기
   * 때문이다. 목록 API 가 붙을 때 두 이름을 한쪽으로 맞춘다.
   */
  materialId: number
  description: string | null
  lengthMm: number | null
  widthMm: number | null
  registrantId: number
  createdAt: string
  owners: MaterialOwner[]
  evidenceHash: string | null
  manifestUrl: string | null
  originReason: MaterialOriginReason | null
  originReasonNote: string | null
  /** 정보가 없으면 **빈 배열**이다. 서버가 `null` 을 주지 않는다 */
  composition: MaterialCompositionItem[]
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
  /**
   * 왜 폐기물이 되었나. `rootOrigin: 'VIRGIN'` 이면 **보내면 안 된다** —
   * 서버가 "신소재에는 발생 사유가 붙을 수 없다" 로 400 을 낸다.
   *
   * 아직 필수는 아니다. 서버가 `WASTE` 에 대해 필수로 전환하는 시점은 이 입력칸이
   * 배포된 뒤다 (그 반대로 하면 등록이 전부 막힌다).
   */
  originReason?: MaterialOriginReason
  /** `originReason: 'ETC'` 일 때 필수 */
  originReasonNote?: string
  /** 혼방 비율. 비어 있으면 **보내지 않는다** — 빈 배열과 미입력을 서버가 같게 다룬다 */
  composition?: MaterialCompositionInput[]
}

/**
 * `material.origin_reason` — 왜 폐기물이 되었나.
 *
 * ⚠️ `generationType` 과 **다른 축**이다. 그쪽은 "어떻게 생겼나"(최초 등록 / 가공 산출 /
 * 생산 자투리)를 말한다. 그래서 이 목록에 `PRODUCTION_SCRAP` 이 **없다** — 같은 사실을
 * 두 곳에 적으면 어긋난다.
 */
export type MaterialOriginReason =
  | 'DEADSTOCK'
  | 'DEFECT'
  | 'OFFCUT'
  | 'END_OF_LIFE'
  | 'COLLECTED'
  | 'ETC'

export const ORIGIN_REASON_LABEL: Record<MaterialOriginReason, string> = {
  DEADSTOCK: '재고 원단',
  DEFECT: '불량·B급',
  OFFCUT: '재단 자투리',
  END_OF_LIFE: '사용 종료 제품',
  COLLECTED: '수거·회수',
  ETC: '기타',
}

/** `ETC` 만 설명을 요구한다. 나머지는 값 자체가 뜻을 담는다. */
export function originReasonRequiresNote(reason: MaterialOriginReason): boolean {
  return reason === 'ETC'
}

/**
 * `material_composition.fiber_type`.
 *
 * ⚠️ 서버 컬럼은 `VARCHAR(50)` 이지만 **허용 목록을 서버 코드가 강제**한다
 * (`MaterialFiberType`). 목록 밖의 값을 보내면 400 이다. 이 배열이 그 목록과
 * 어긋나면 사용자가 고를 수 없거나 고른 값이 거절되므로 순서까지 맞춰 둔다.
 */
export const MATERIAL_FIBER_TYPES = [
  'COTTON',
  'POLYESTER',
  'NYLON',
  'WOOL',
  'LINEN',
  'RAYON',
  'ACRYLIC',
  'SPANDEX',
  'LYOCELL',
  'LEATHER',
  'DENIM',
  'OTHER',
] as const

export type MaterialFiberType = (typeof MATERIAL_FIBER_TYPES)[number]

export const FIBER_TYPE_LABEL: Record<MaterialFiberType, string> = {
  COTTON: '면',
  POLYESTER: '폴리에스터',
  NYLON: '나일론',
  WOOL: '울',
  LINEN: '린넨',
  RAYON: '레이온',
  ACRYLIC: '아크릴',
  SPANDEX: '스판덱스',
  LYOCELL: '리요셀',
  LEATHER: '가죽',
  DENIM: '데님',
  OTHER: '기타',
}

/** 구성 비율 합계의 상한. **100 을 요구하지 않는다** — 아래 주석 참고 */
export const COMPOSITION_MAX_TOTAL = 100

/** 전송용 구성 한 줄 */
export interface MaterialCompositionInput {
  fiberType: MaterialFiberType
  percentage: number
}

/**
 * 조회 응답의 구성 한 줄.
 *
 * 서버가 **비율 내림차순**으로 정렬해 준다. 클라이언트에서 다시 정렬하지 않는다 —
 * 양쪽에서 정렬하면 같은 소재의 줄 순서가 화면마다 달라 보인다.
 */
export interface MaterialCompositionItem {
  fiberType: string
  percentage: number
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

/**
 * 소재 종류 라벨.
 *
 * ⚠️ 이 표가 없어서 `ListingFilterBar` 와 `MaterialForm` 이 각자 같은 목록을 들고
 * 있었다. 종류가 하나 늘 때 두 곳을 고쳐야 하고, 빠뜨리면 한쪽 화면에만 나타난다.
 */
export const MATERIAL_TYPE_LABEL: Record<MaterialType, string> = {
  DENIM: '데님',
  COTTON: '면',
  LEATHER: '가죽',
  LINING: '안감',
  METAL: '금속',
  MIXED: '혼방',
  ETC: '기타',
}

/**
 * 생성 유형 라벨. **출처와 함께 두 축을 이룬다** (화면 설계서 B03 · S05).
 *
 * "폐기물 · 최초 등록" 은 원물이고 "폐기물 · 가공 산출물" 은 이미 한 번 손이 간 소재다.
 */
export const GENERATION_TYPE_LABEL: Record<MaterialGenerationType, string> = {
  INITIAL: '최초 등록',
  PROCESSED: '가공 산출물',
  PRODUCTION_SCRAP: '생산 부산물',
}

export const GRADE_LABEL: Record<MaterialGrade, string> = {
  A: 'A · 오염·손상 없음',
  B: 'B · 경미한 오염·손상',
  C: 'C · 파쇄·재생 원료화 대상',
}
