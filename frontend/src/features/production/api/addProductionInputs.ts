import { apiClient } from '../../../services/apiClient.ts'
import type { ProductionBatch, ProductionInputRequest } from '../types/production.types.ts'

/**
 * 배치에 소재를 연결한다 — `POST /v1/production-batches/{batchId}/inputs`
 *
 * **연결과 동시에 그 수량이 잠긴다.** 잠금을 푸는 것은 투입 취소
 * (`DELETE /{batchId}/inputs/{inputId}`)뿐이다.
 *
 * ⚠️ **목록으로 보낸다** (`{ inputs: [...] }`). 한 건씩 보내면 중간에 실패했을 때 앞의
 * 것들이 잠긴 채 남고, 화면이 어디까지 성공했는지 추적해 되돌려야 한다. 그 되돌리기가
 * 실패하면 아무도 쓰지 않는 잠금이 남는다. 목록이면 **전부 성공하거나 전부 실패한다.**
 *
 * 한 번에 최대 20건이다 (`PRODUCTION_INPUTS_MAX`). 단건도 원소 하나짜리 목록으로 보낸다.
 *
 * 응답은 배치 전체다 — 화면이 투입 목록과 합계를 함께 다시 그리므로 한 번에 준다.
 */
export async function addProductionInputs(
  batchId: number,
  inputs: ProductionInputRequest[],
): Promise<ProductionBatch> {
  const { data } = await apiClient.post<ProductionBatch>(
    `/v1/production-batches/${batchId}/inputs`,
    { inputs },
  )
  return data
}
