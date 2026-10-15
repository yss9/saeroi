import { apiClient } from '../../../services/apiClient.ts'
import type {
  BrandPredictionInsight,
  PredictionMarket,
  PredictionPointGrant,
  PredictionSurveyRequest,
} from '../types/prediction.types.ts'

export async function getPredictionMarkets(): Promise<PredictionMarket[]> {
  const { data } = await apiClient.get<PredictionMarket[]>('/v1/prediction-markets')
  return data
}

export async function savePredictionSurvey(marketId: number, request: PredictionSurveyRequest) {
  const { data } = await apiClient.post(`/v1/prediction-markets/${marketId}/responses`, request)
  return data
}

export async function grantPredictionWelcomePoint(): Promise<PredictionPointGrant> {
  const { data } = await apiClient.post<PredictionPointGrant>('/v1/prediction-markets/points/welcome')
  return data
}

export async function getBrandPredictionInsights(): Promise<BrandPredictionInsight[]> {
  const { data } = await apiClient.get<BrandPredictionInsight[]>(
    '/v1/brand/prediction-markets/insights',
  )
  return data
}

