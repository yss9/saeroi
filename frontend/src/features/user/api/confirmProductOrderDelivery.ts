import { apiClient } from '../../../services/apiClient.ts'
import type { ConsumerOrderResponse } from '../types/consumerOrder.types.ts'

/**
 * 수령 확인 — `POST /v1/product-orders/{orderId}/receive/confirm`
 *
 * **배송이 끝났다고 말하는 쪽은 구매자다.** 소재 거래와 같은 2-Track 이다 — 브랜드가
 * 발송을 처리해도 주문은 `SHIPPED` 에서 멈추고, 소비자가 받았다고 해야 `DELIVERED` 가 된다.
 * 다만 소비자는 지갑을 쓰지 않으므로 서명도 온체인 기록도 없다.
 *
 * `SHIPPED` 인 주문만 받는다. 아직 배송 전이거나 이미 완료된 주문은 서버가 거절한다.
 *
 * ⚠️ **아직 서버에 없는 경로다.** 엔티티에 `confirmDelivery` 는 있는데 그것을 부르는
 * 컨트롤러가 없어, 추가를 요청해 둔 상태다. 반영 전에는 404 가 돌아온다.
 */
export async function confirmProductOrderDelivery(
  orderId: number,
): Promise<ConsumerOrderResponse> {
  const { data } = await apiClient.post<ConsumerOrderResponse>(
    `/v1/product-orders/${orderId}/receive/confirm`,
  )
  return data
}
