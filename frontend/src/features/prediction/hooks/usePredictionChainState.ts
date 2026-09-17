import { useQuery } from '@tanstack/react-query'
import { loadPredictionChainState } from '../services/predictionContractService.ts'
import type { PredictionMarket } from '../types/prediction.types.ts'

export function usePredictionChainState(market: PredictionMarket, walletAddress: string | null) {
  return useQuery({
    queryKey: ['prediction-chain', market.chainMarketId, walletAddress],
    queryFn: () => loadPredictionChainState(market, walletAddress!),
    enabled: Boolean(walletAddress),
    refetchInterval: 15_000,
  })
}
