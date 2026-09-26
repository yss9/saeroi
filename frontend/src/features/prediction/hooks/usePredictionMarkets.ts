import { useQuery } from '@tanstack/react-query'
import { getPredictionMarkets } from '../api/predictionApi.ts'

export const predictionKeys = { all: ['prediction-markets'] as const }

export function usePredictionMarkets() {
  return useQuery({ queryKey: predictionKeys.all, queryFn: getPredictionMarkets, refetchInterval: 30_000 })
}

