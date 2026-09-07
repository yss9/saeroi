import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getTrades, type TradeFilters } from '../api/getTrades.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { TradeSummary } from '../types/trade.types.ts'

export function useTrades(filters: TradeFilters = {}) {
  return useQuery<TradeSummary[], AppError>({
    queryKey: tradeKeys.list(filters),
    queryFn: () => getTrades(filters),
  })
}
