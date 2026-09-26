// 제품 주문 결제 API.
//
// 카카오페이는 2단계다. 소재 거래(`trade/api/paymentApi.ts`)와 같은 흐름이고, 서버도 같은
// 에러 코드를 쓴다.
//
//   1) ready    서버가 PG 에 준비를 요청해 tid 와 결제창 주소를 받는다   (돈 안 움직임)
//   2) 결제창으로 이동 → 사용자가 인증 → pg_token 을 들고 복귀 URL 로 돌아온다
//   3) approve  서버가 그 토큰으로 승인을 마친다                        (실제 청구)
//
// 프론트가 PG 를 직접 부르지 않는다. 시크릿이 필요해 서버만 호출할 수 있고, 프론트가
// 부르면 그 값이 브라우저에 노출된다.

import { apiClient } from '../../../services/apiClient.ts'
import type {
  ConsumerOrderResponse,
  ProductOrderPaymentReady,
} from '../types/consumerOrder.types.ts'

/**
 * 결제 준비 — `POST /v1/product-orders/{orderId}/payment/ready`
 *
 * **아직 청구되지 않는다.** 응답의 `redirectUrl` 로 사용자를 보내면 결제창이 열린다.
 *
 * `live` 가 false 면 서버에 카카오페이 키가 없는 환경이다. 그때는 `redirectUrl` 이 비어
 * 있으므로 이동하지 않고 바로 승인으로 간다 — 화면은 그 사실을 사용자에게 알려야 한다.
 *
 * 결제창을 닫고 다시 여는 것은 허용된다. 이 요청을 다시 부르면 된다.
 */
export async function readyProductOrderPayment(
  orderId: number,
): Promise<ProductOrderPaymentReady> {
  const { data } = await apiClient.post<ProductOrderPaymentReady>(
    `/v1/product-orders/${orderId}/payment/ready`,
  )
  return data
}

/**
 * 결제 승인 — `POST /v1/product-orders/{orderId}/payment/approve`
 *
 * **이 시점에 실제로 청구된다.** 성공하면 주문이 `PAID` 로 넘어가고 개체의 `journeyUrl` 이
 * 채워진다 — 그 전까지는 null 이라 제품 여정을 열 수 없다.
 *
 * 금액과 tid 를 보내지 않는다. 금액은 서버가 PG 응답에서 읽어 주문 금액과 대조하고, tid 는
 * 준비 단계에서 서버가 주문에 저장해 두었다. 둘 다 클라이언트가 말하게 두면 조작 경로가 된다.
 *
 * 준비를 건너뛰고 부르면 `PAYMENT_NOT_PREPARED`, 이미 결제된 주문이면
 * `PAYMENT_STATE_INVALID` 다 — 결제창에서 뒤로 가기를 누르면 복귀 URL 이 실제로 두 번 열린다.
 */
export async function approveProductOrderPayment(
  orderId: number,
  pgToken: string,
): Promise<ConsumerOrderResponse> {
  const { data } = await apiClient.post<ConsumerOrderResponse>(
    `/v1/product-orders/${orderId}/payment/approve`,
    { pgToken },
  )
  return data
}

/**
 * 주문 취소 — `POST /v1/product-orders/{orderId}/cancel`
 *
 * ⚠️ **결제창을 떠난 사용자를 반드시 이 경로로 보내야 한다.** 주문을 만드는 순간 개체가
 * `RESERVED` 로 잡히는데, 제품 개체는 한 점씩이라 **한 건만 묶여도 그 제품이 즉시 품절로
 * 보인다.** 소재 거래는 수량 일부가 묶이는 것이라 티가 덜 나지만 여기서는 바로 드러난다.
 *
 * 서버에 30분 마감 배치가 있지만 그때까지는 품절로 보인다 — 화면이 부르는 쪽이 훨씬 빠르다.
 *
 * 응답 본문이 없다. **취소된 주문은 행 자체가 사라진다** — 개체를 다시 팔기 위한 설계다.
 * 그래서 취소 뒤에는 그 주문을 다시 조회할 수 없고, 목록을 새로 읽어야 한다.
 *
 * 사유는 선택이다. 값이 없으면 바디를 보내지 않는다 — 사유 없는 취소(결제창 이탈)가 정상
 * 경로라는 것을 요청 모양이 그대로 말하게 둔다.
 */
export async function cancelProductOrder(orderId: number, reason?: string): Promise<void> {
  const trimmed = reason?.trim()
  await apiClient.post(
    `/v1/product-orders/${orderId}/cancel`,
    trimmed ? { reason: trimmed } : undefined,
  )
}
