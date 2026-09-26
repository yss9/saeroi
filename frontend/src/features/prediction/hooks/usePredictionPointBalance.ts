import { useQuery } from '@tanstack/react-query'
import { loadPredictionPointBalance } from '../services/predictionContractService.ts'

export const predictionPointBalanceKey = (walletAddress: string | null) =>
  ['prediction-point-balance', walletAddress] as const

export function usePredictionPointBalance(walletAddress: string | null) {
  return useQuery({
    queryKey: predictionPointBalanceKey(walletAddress),
    queryFn: () => loadPredictionPointBalance(walletAddress!),
    enabled: Boolean(walletAddress),
    refetchInterval: 15_000,
  })
}
