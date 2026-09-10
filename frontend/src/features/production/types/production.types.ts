// Production 도메인 타입. Backend `production_batch` 에 대응한다.

/** `production_batch.status` — 블록체인 기록 상태를 함께 표현한다 */
export type ProductionBatchStatus = 'DRAFT' | 'SIGNING' | 'CONFIRMED' | 'FAILED' | 'CANCELED'

/**
 * 생산 배치 한 건과 그 투입 목록.
 *
 * Backend 원본: `production/dto/ProductionBatchResponse.java`.
 *
 * **투입이 응답에 함께 담긴다.** 화면이 언제나 둘을 같이 보여주기 때문이고, 나눠 부르면
 * 그 사이에 투입이 바뀌어 합계와 목록이 어긋난 화면이 그려진다.
 *
 * `upcycleRatio` 는 **확정 전에는 0** 이다. 계산은 확정 시점에 한 번 하고 스냅샷으로
 * 굳힌다 — 매번 재계산하면 소재 데이터가 바뀔 때 이미 팔린 제품의 비율이 소급해 변한다.
 */
export interface ProductionBatch {
  productionBatchId: number
  batchCode: string
  batchName: string
  productModelId: number
  producedQuantity: number
  producerId: number
  /** 배치를 연 시점의 활성 지갑. 온체인 기록에 그대로 남는다 */
  producerWallet: string
  status: ProductionBatchStatus
  /** 투입 합계 (gram) */
  totalUsedQuantity: number
  virginUsedQuantity: number
  upcycledUsedQuantity: number
  /** 확정 전에는 0. 서버가 문자열로 줄 수도 있어(BigDecimal) 표시 전에 Number 로 좁힌다 */
  upcycleRatio: number
  createdAt: string
  confirmedAt: string | null
  inputs: ProductionInput[]
}

/**
 * 투입 한 건.
 *
 * `materialLockId` 가 함께 온다 — 투입을 취소하면 이 잠금이 풀린다는 것을 화면이 설명할 수
 * 있어야 하고, 잠금이 풀리지 않은 채 남는 사고를 눈으로 확인할 수 있다.
 */
export interface ProductionInput {
  productionInputId: number
  materialId: number
  /** gram */
  inputQuantity: number
  productUsedQuantity: number
  reusableScrapQuantity: number
  wasteQuantity: number
  wasteReason: string | null
  materialLockId: number
}

/**
 * 투입 한 건의 요청 값.
 *
 * **투입량을 셋으로 나눠 보낸다.**
 *
 * ```text
 *   inputQuantity = productUsedQuantity   제품에 실제로 들어간 양
 *                 + reusableScrapQuantity 자투리 (새 소재로 되살아난다)
 *                 + wasteQuantity         폐기
 * ```
 *
 * 합계만 보내고 서버가 쪼개는 방식은 불가능하다 — 어디까지가 제품이고 어디부터 자투리인지는
 * 실제로 재단해 본 브랜드만 안다. 대신 **식이 맞는지는 서버가 행마다 검사한다**
 * (`PRODUCTION_INPUT_QUANTITY_MISMATCH`). 배치 합계로만 보면 한 소재를 과다 입력하고 다른
 * 소재를 과소 입력해도 통과하기 때문이다.
 *
 * 단위는 **gram** 이다. `materialLockId` 는 보내지 않는다 — 잠금은 서버가 이 요청을
 * 처리하면서 직접 걸고, 클라이언트가 고르게 하면 남이 잡아 둔 잠금을 자기 투입에 붙일 수 있다.
 */
export interface ProductionInputRequest {
  materialId: number
  inputQuantity: number
  productUsedQuantity: number
  reusableScrapQuantity: number
  wasteQuantity: number
  /** 200자 이하. 폐기가 없으면 비워 둔다 */
  wasteReason?: string
}

/**
 * 배치 열기 요청 (`POST /v1/production-batches`).
 *
 * 요청이 정할 수 없는 값이 셋 있다 — `producerId`(JWT), `producerWallet`(등록된 활성 지갑),
 * `status`(언제나 `DRAFT`). 수량 세 컬럼과 업사이클 비율도 받지 않는다: 확정 시점에 실제
 * 투입으로 계산한다. 요청이 정할 수 있으면 실제와 다른 비율을 적어 넣을 수 있고, 그 값이
 * 그대로 제품에 붙는다.
 */
export interface OpenProductionBatchRequest {
  /** 필수, 200자 이하 */
  batchName: string
  /** 내 제품이어야 한다 */
  productModelId: number
  /** 1개 이상 */
  producedQuantity: number
}

/**
 * 한 요청의 투입 상한 (`ProductionInputsRequest.MAX_INPUTS`).
 *
 * 상한이 없으면 한 번의 요청으로 소재 수천 건을 잠글 수 있다. 더 필요하면 나눠 부른다.
 */
export const PRODUCTION_INPUTS_MAX = 20

/**
 * 이 소재를 생산에 투입할 수 있는가.
 *
 * **폐기물에서 바로 온 소재(WASTE + INITIAL)는 투입할 수 없다.** 가공을 최소 한 번 거쳐야
 * 한다 — 막지 않으면 가공 이력이 없는 제품이 업사이클로 팔린다. 서버도 같은 규칙으로
 * 거절한다 (`PRODUCTION_MATERIAL_NOT_ELIGIBLE`).
 *
 * 화면이 같은 판단을 하는 이유는, 고를 수 없는 소재를 왜 못 고르는지 그 자리에서 말해야
 * 하기 때문이다. 서버에만 맡기면 사용자는 수량까지 입력하고 저장을 눌러야 이유를 안다.
 */
export function isProductionEligible(material: {
  rootOrigin: string
  generationType: string
}): boolean {
  return !(material.rootOrigin === 'WASTE' && material.generationType === 'INITIAL')
}

// TODO(5번/Production 담당): 산출물(production_output) 타입을 추가한다.
