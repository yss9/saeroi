import { apiClient } from '../../../services/apiClient.ts'
import type { QuantityUnit } from '../../marketplace/types/marketplace.types.ts'
import type { TradeQuote } from '../types/trade.types.ts'

/**
 * 구매 전 금액과 구매 가능 여부 확인. **아무것도 바꾸지 않는다.**
 *
 * 매 입력마다 부를 필요는 없다 — 화면도 같은 계산을 한다. 구매 버튼을 누르기 직전에
 * 한 번 확인해 "그 사이에 팔렸는가" 를 잡는 용도다.
 */
export async function getTradeQuote(
  listingId: number,
  quantity: number,
  unit: QuantityUnit = 'G',
): Promise<TradeQuote> {
  const { data } = await apiClient.get<TradeQuote>('/v1/trades/quote', {
    params: { listingId, quantity, unit },
  })
  return data
}
