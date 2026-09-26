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
 * **돈이 움직이지 않는다.** 주문만 `CREATED` 로 만들어지고, 결제는 카카오페이 2단계로
 * 이어진다 (`productOrderPaymentApi.ts`). 그래서 이 응답의 `paidAt` 과 `items[].journeyUrl`
 * 은 아직 null 이다 — 승인이 끝나야 채워진다.
 *
 * ⚠️ **이 순간 개체가 `RESERVED` 로 잡힌다.** 제품 개체는 한 점씩이라 한 건만 묶여도 그
 * 제품이 즉시 품절로 보인다. 결제를 포기하면 반드시 `cancelProductOrder` 를 불러야 한다.
 *
 * 거절 조건: 품절(`PRODUCT_ORDER_OUT_OF_STOCK`), 판매 중이 아닌 제품
 * (`PRODUCT_ORDER_MODEL_NOT_ON_SALE`), 없거나 남의 배송지(`PRODUCT_ORDER_ADDRESS_NOT_FOUND`).
 */
export async function createProductOrder(
  request: CreateProductOrderRequest,
): Promise<ConsumerOrderResponse> {
  const { data } = await apiClient.post<ConsumerOrderResponse>('/v1/product-orders', request)
  return data
}
