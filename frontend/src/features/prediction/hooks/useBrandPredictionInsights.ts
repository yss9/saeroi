import { useQuery } from '@tanstack/react-query'
import { getBrandPredictionInsights } from '../api/predictionApi.ts'

export const brandPredictionInsightKeys = {
  all: ['brand-prediction-insights'] as const,
}

export function useBrandPredictionInsights() {
  return useQuery({
    queryKey: brandPredictionInsightKeys.all,
    queryFn: getBrandPredictionInsights,
    refetchInterval: 30_000,
  })
}
