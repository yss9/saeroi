import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { predictionKeys } from '../../prediction/hooks/usePredictionMarkets.ts'
import {
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
