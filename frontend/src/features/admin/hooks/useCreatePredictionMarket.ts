import { useMutation, useQueryClient } from '@tanstack/react-query'
import { predictionKeys } from '../../prediction/hooks/usePredictionMarkets.ts'
import { createPredictionMarket } from '../api/predictionMarketAdminApi.ts'

export function useCreatePredictionMarket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createPredictionMarket,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: predictionKeys.all }),
  })
}
