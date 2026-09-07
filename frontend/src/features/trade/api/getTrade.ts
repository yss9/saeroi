import { apiClient } from '../../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import { findTrade } from '../../marketplace/api/mock/marketplaceMockStore.ts'
import type { TradeDetail } from '../types/trade.types.ts'

/** TODO(3번): `apiClient.get<TradeDetail>('/trades/' + tradeId)` 로 교체. */
export async function getTrade(tradeId: number): Promise<TradeDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 250))

  const trade = findTrade(tradeId)
  if (!trade) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '거래를 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }
  return trade
}
