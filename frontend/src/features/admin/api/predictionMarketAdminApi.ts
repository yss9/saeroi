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
