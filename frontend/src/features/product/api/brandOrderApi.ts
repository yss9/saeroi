// 브랜드가 보는 제품 주문 API.
//
// 소비자가 산 물건을 브랜드가 보내는 흐름이다.
//
//   CREATED ─결제─▶ PAID ─브랜드 발송─▶ SHIPPED ─소비자 수령 확인─▶ DELIVERED
//
// 발송 이후는 소비자도 취소할 수 없다. 물건이 나갔는데 주문만 없애면 그 개체가 어디에도
// 속하지 않게 되기 때문이다.

import { apiClient } from '../../../services/apiClient.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import type {
  BrandOrder,
  BrandOrderFilters,
  BrandOrderShipRequest,
  BrandOrderShipResult,
} from '../types/brandOrder.types.ts'

/**
 * 내게 들어온 주문 — `GET /v1/product-orders/brand/me`
 *
 * **배송지가 함께 온다.** 남의 집 주소라 범위는 언제나 JWT 의 사용자다 — 경로나 조건으로
 * 브랜드를 지정하는 방법이 없다.
 *
 * 발송할 건만 보려면 `status: 'PAID'` 로 거른다. 거르지 않으면 배송 완료된 주문까지 같이
 * 나온다.
 */
export async function getBrandOrders(
  filters: BrandOrderFilters = {},
): Promise<PageResponse<BrandOrder>> {
  const { data } = await apiClient.get<PageResponse<BrandOrder>>('/v1/product-orders/brand/me', {
    params: filters,
  })
  return data
}

/**
 * 발송 처리 — `PATCH /v1/product-orders/{orderId}/ship`
 *
 * **되돌릴 수 없다.** 발송한 순간 소비자의 취소도 막힌다.
 *
 * 택배사와 운송장은 둘 다 선택이라 빈 요청도 받는다(직접 전달·퀵). 다만 운송장만 보내고
 * 택배사를 비우면 `PRODUCT_ORDER_TRACKING_WITHOUT_CARRIER`(400) 다 — 조회할 방법이 없는
 * 조합이라 화면이 먼저 막는다.
 *
 * 거절 조건: 결제되지 않은 주문(`PRODUCT_ORDER_NOT_SHIPPABLE`), 이미 발송됨
 * (`PRODUCT_ORDER_ALREADY_SHIPPED` — 중복 클릭), 남의 주문(`PRODUCT_ORDER_NOT_SELLER`).
 */
export async function shipBrandOrder(
  orderId: number,
  request: BrandOrderShipRequest,
): Promise<BrandOrderShipResult> {
  const { data } = await apiClient.patch<BrandOrderShipResult>(
    `/v1/product-orders/${orderId}/ship`,
    normalize(request),
  )
  return data
}

/**
 * 빈 값을 빼고 보낸다.
 *
 * 빈 문자열을 그대로 보내면 서버는 "택배사를 적었다" 로 읽는다 — 운송장만 있고 택배사가
 * 빈 문자열인 조합은 400 이 아니라 조회 불가능한 송장으로 저장될 여지가 있다.
 */
function normalize(request: BrandOrderShipRequest): BrandOrderShipRequest {
  const carrier = request.carrier?.trim()
  const trackingNumber = request.trackingNumber?.trim()
  return {
    ...(carrier ? { carrier } : {}),
    ...(trackingNumber ? { trackingNumber } : {}),
  }
}
