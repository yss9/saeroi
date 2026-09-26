import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { predictionKeys } from '../../prediction/hooks/usePredictionMarkets.ts'
import {
  cancelPredictionMarket,
  createPredictionMarket,
  getPredictionMarketEligibleBatches,
} from '../api/predictionMarketAdminApi.ts'

export const predictionMarketAdminKeys = {
  eligibleBatches: ['admin', 'prediction-markets', 'eligible-batches'] as const,
}

export function usePredictionMarketEligibleBatches() {
  return useQuery({
    queryKey: predictionMarketAdminKeys.eligibleBatches,
    queryFn: getPredictionMarketEligibleBatches,
  })
}

export function useCreatePredictionMarket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createPredictionMarket,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: predictionKeys.all }),
        queryClient.invalidateQueries({ queryKey: predictionMarketAdminKeys.eligibleBatches }),
      ])
    },
  })
}

/**
 * 시장 무효화.
 *
 * 개설 가능 배치 목록은 건드리지 않는다 — 무효가 된 시장의 생산 배치는 여전히
 * `existsByProductionBatchId` 에 걸려 다시 열 수 없기 때문이다.
 */
export function useCancelPredictionMarket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: cancelPredictionMarket,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: predictionKeys.all })
    },
  })
}
