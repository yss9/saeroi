import { apiClient } from '../../../services/apiClient.ts'
import type { Trade } from '../types/trade.types.ts'

/** 거래 상세. 당사자(판매자·구매자)만 볼 수 있다 — 남이 부르면 403 이다. */
export async function getTrade(tradeId: number): Promise<Trade> {
  const { data } = await apiClient.get<Trade>(`/v1/trades/${tradeId}`)
  return data
}
