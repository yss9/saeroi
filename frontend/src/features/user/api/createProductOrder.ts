import { apiClient } from '../../../services/apiClient.ts'
import type { ConsumerOrderResponse } from '../types/consumerOrder.types.ts'

/**
 * 주문할 제품과 받을 곳.
 *
 * **수량이 없다.** 제품 개체(`product_item`)는 한 점씩 만들어지는 물건이라 같은 개체를
 * 두 개 살 수 없다. 서버가 재고에서 개체 하나를 골라 주문에 묶는다 — 화면이 개체 번호를
 * 고르면 그 사이에 남이 사 간 개체를 지목하는 일이 생긴다.
 */
export interface CreateProductOrderRequest {
  productModelId: number
  /** 받을 곳. 배송지 주소록(`/v1/consumers/me/addresses`)의 id */
  addressId: number
}

/**
 * 제품 주문 — `POST /v1/product-orders`
 *
 * ⚠️ **아직 서버에 없는 경로다.** 결제 수단이 정해지지 않아 결제를 건너뛰고 주문만
 * 만들기로 했고, 그 엔드포인트를 요청해 둔 상태다. 반영 전에는 404 가 돌아온다.
 *
 * **결제를 건너뛰므로 주문은 곧바로 결제 완료 상태로 들어온다고 가정한다** — 그래야
 * 마이페이지의 주문 현황과 제품 여정(결제 전에는 `journeyUrl` 이 null 이다)이 바로
 * 보인다. 서버가 `CREATED` 로 만들면 화면은 "결제 대기" 로 보여주고, 그것도 맞는 동작이다.
 */
export async function createProductOrder(
  request: CreateProductOrderRequest,
): Promise<ConsumerOrderResponse> {
  const { data } = await apiClient.post<ConsumerOrderResponse>('/v1/product-orders', request)
  return data
}
