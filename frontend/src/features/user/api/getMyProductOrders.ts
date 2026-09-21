import { apiClient } from '../../../services/apiClient.ts'
import type {
  ConsumerOrderFilters,
  ConsumerOrderPage,
  ConsumerOrderResponse,
} from '../types/consumerOrder.types.ts'

/** 로그인한 소비자의 제품 주문 한 건. 본인 주문이 아니거나 없으면 서버가 404를 반환한다. */
export async function getMyProductOrder(orderId: number): Promise<ConsumerOrderResponse> {
  const { data } = await apiClient.get<ConsumerOrderResponse>(`/v1/product-orders/${orderId}`)
  return data
}

/** 로그인한 소비자의 제품 주문 목록. 구매자 id는 JWT에서 결정된다. */
export async function getMyProductOrders(
  filters: ConsumerOrderFilters = {},
): Promise<ConsumerOrderPage> {
  const { data } = await apiClient.get<ConsumerOrderPage>('/v1/product-orders/me', {
    params: filters,
  })
  return data
}
