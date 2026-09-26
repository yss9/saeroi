import { apiClient } from '../../../services/apiClient.ts'
import type { PredictionMarket } from '../../prediction/types/prediction.types.ts'

export interface PredictionMarketCreateRequest {
  productionBatchId: number
  bettingEndsAt: string
  salesStartsAt: string
  salesEndsAt: string
  bucketUpperBounds: number[]
}

export interface PredictionMarketEligibleBatch {
  productionBatchId: number
  chainBatchId: number
  batchCode: string
  batchName: string
  brandName: string
  productName: string
  productionQuantity: number
}

export async function getPredictionMarketEligibleBatches(): Promise<PredictionMarketEligibleBatch[]> {
  const { data } = await apiClient.get<PredictionMarketEligibleBatch[]>(
    '/v1/admin/prediction-markets/eligible-batches',
  )
  return data
}

export async function createPredictionMarket(
  request: PredictionMarketCreateRequest,
): Promise<PredictionMarket> {
  const { data } = await apiClient.post<PredictionMarket>('/v1/admin/prediction-markets', request)
  return data
}

/**
 * 시장을 무효로 돌린다. 되돌릴 수 없고 참여자 전원이 환불 대상이 된다.
 *
 * 온체인 `cancelMarket` 을 서버가 대신 서명하므로 지갑도 가스도 필요 없다. 체인 호출이
 * 실패하면 서버가 DB 를 되돌리므로, 성공 응답이 온 시장만 무효가 된 것이다.
 */
export async function cancelPredictionMarket(marketId: number): Promise<PredictionMarket> {
  const { data } = await apiClient.post<PredictionMarket>(
    `/v1/admin/prediction-markets/${marketId}/cancel`,
  )
  return data
}
