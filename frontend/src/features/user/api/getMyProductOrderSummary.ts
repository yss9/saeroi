import { apiClient } from '../../../services/apiClient.ts'
import type { ConsumerOrderStatusSummaryResponse } from '../types/consumerOrder.types.ts'

/** 로그인한 소비자의 주문 상태별 전체 건수. */
export async function getMyProductOrderSummary(): Promise<ConsumerOrderStatusSummaryResponse> {
  const { data } = await apiClient.get<ConsumerOrderStatusSummaryResponse>(
    '/v1/product-orders/me/summary',
  )
  return data
}
