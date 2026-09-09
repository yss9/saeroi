import { apiClient } from '../../../services/apiClient.ts'
import type { ProductionBatch } from '../types/production.types.ts'

/**
 * 투입 취소 — `DELETE /v1/production-batches/{batchId}/inputs/{inputId}`
 *
 * **잠금이 함께 풀려 수량이 돌아온다.** 투입을 남겨 두면 그 소재는 다른 판매·가공·생산에
 * 쓸 수 없다 — 잘못 넣은 투입을 되돌릴 수 있어야 하는 이유다.
 *
 * `DRAFT` 배치에서만 가능하다. 확정된 배치는 소재를 이미 소비했으므로 서버가
 * `PRODUCTION_BATCH_NOT_EDITABLE` 로 거절한다.
 *
 * 응답은 배치 전체다 — 남은 투입 목록을 화면이 다시 그리므로 한 번에 준다.
 */
export async function removeProductionInput(
  batchId: number,
  inputId: number,
): Promise<ProductionBatch> {
  const { data } = await apiClient.delete<ProductionBatch>(
    `/v1/production-batches/${batchId}/inputs/${inputId}`,
  )
  return data
}
