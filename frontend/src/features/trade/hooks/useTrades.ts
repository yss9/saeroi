import { useQuery } from '@tanstack/react-query'
import type { AppError } from '../../../utils/errors.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import { getTrades } from '../api/getTrades.ts'
import { tradeKeys } from '../api/tradeKeys.ts'
import type { Trade, TradeFilters } from '../types/trade.types.ts'

/** 내 거래 목록. 사용자 id 를 보내지 않는다 — 서버가 JWT 에서 강제한다. */
export function useTrades(filters: TradeFilters = {}) {
  return useQuery<PageResponse<Trade>, AppError>({
    queryKey: tradeKeys.list(filters),
    queryFn: () => getTrades(filters),
  })
}
