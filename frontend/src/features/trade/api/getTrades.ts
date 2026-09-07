import { apiClient } from '../../../services/apiClient.ts'
import { allTrades } from '../../marketplace/api/mock/marketplaceMockStore.ts'
import type { TradeStatus, TradeSummary } from '../types/trade.types.ts'

export interface TradeFilters {
  status?: TradeStatus
  /** 내가 산 것 / 내가 판 것 */
  side?: 'BUY' | 'SELL'
}

/**
 * 내 거래 목록.
 *
 * TODO(3번): `apiClient.get<TradeSummary[]>('/trades', { params })` 로 교체.
 */
export async function getTrades(params: TradeFilters = {}): Promise<TradeSummary[]> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 250))

  let rows = allTrades()
  if (params.status) rows = rows.filter((row) => row.status === params.status)
  return rows.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}
