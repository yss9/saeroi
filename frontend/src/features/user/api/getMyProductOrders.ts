import { apiClient } from '../../../services/apiClient.ts'
import type {
  ConsumerOrderFilters,
  ConsumerOrderPage,
} from '../types/consumerOrder.types.ts'

/** 로그인한 소비자의 제품 주문 목록. 구매자 id는 JWT에서 결정된다. */
export async function getMyProductOrders(
  filters: ConsumerOrderFilters = {},
): Promise<ConsumerOrderPage> {
  const { data } = await apiClient.get<ConsumerOrderPage>('/v1/product-orders/me', {
    params: filters,
  })
  return data
}
