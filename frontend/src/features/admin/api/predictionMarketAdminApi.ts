import { apiClient } from '../../../services/apiClient.ts'
import type { PredictionMarket } from '../../prediction/types/prediction.types.ts'

export interface PredictionMarketCreateRequest {
  productionBatchId: number
  bettingEndsAt: string
  salesStartsAt: string
  salesEndsAt: string
  bucketUpperBounds: number[]
}

export async function createPredictionMarket(
  request: PredictionMarketCreateRequest,
): Promise<PredictionMarket> {
  const { data } = await apiClient.post<PredictionMarket>('/v1/admin/prediction-markets', request)
  return data
}
