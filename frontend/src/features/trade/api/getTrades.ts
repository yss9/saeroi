import { apiClient } from '../../../services/apiClient.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import type { Trade, TradeFilters } from '../types/trade.types.ts'

/**
 * 내 거래 목록.
 *
 * 사용자 id 를 보내지 않는다 — 서버가 JWT 에서 강제한다. 파라미터로 받으면 남의 거래
 * 목록을 볼 수 있고, 거래에는 금액과 양쪽 지갑 주소가 들어 있다.
 *
 * 기본은 `side=BUY`(내가 산 것)다. 판매자는 `SELL` 로 발송 대기 건을 본다.
 */
export async function getTrades(params: TradeFilters = {}): Promise<PageResponse<Trade>> {
  const { data } = await apiClient.get<PageResponse<Trade>>('/v1/trades', { params })
  return data
}
