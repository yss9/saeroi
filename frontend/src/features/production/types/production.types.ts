// Production 도메인 타입. Backend `production_batch` 에 대응한다.

/** `production_batch.status` — 블록체인 기록 상태를 함께 표현한다 */
export type ProductionBatchStatus = 'DRAFT' | 'SIGNING' | 'CONFIRMED' | 'FAILED' | 'CANCELED'

export interface ProductionBatchSummary {
  id: number
  batchCode: string
  productModelId: number
  status: ProductionBatchStatus
  createdAt: string
}

// TODO(5번/Production 담당): 투입 소재(production_input) / 산출물(production_output) 타입을 추가한다.
