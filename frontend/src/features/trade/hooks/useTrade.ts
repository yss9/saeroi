import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import { getTrade } from '../api/getTrade.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { TradeDetail } from '../types/trade.types.ts'

export function useTrade(tradeId: number | null) {
  return useQuery<TradeDetail, AppError>({
    queryKey: tradeKeys.detail(tradeId ?? 0),
    queryFn: () => getTrade(tradeId as number),
    enabled: tradeId !== null,
  })
}
