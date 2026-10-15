// 소재 거래 결제 API.
//
// 정책 08절: 카카오페이 PG 직접 결제다. 플랫폼이 대금을 예치하지 않고(Escrow 폐지),
// **블록체인은 결제를 검증하지 않는다** — `Marketplace.sol` 의 `Order` 구조체에 금액
// 필드가 없고 `createListing` 에 가격 인자가 없다. 온체인에는 수량과 소유권만 남는다.
//
// 카카오페이는 2단계다.
//
//   1) ready    서버가 PG 에 준비를 요청해 tid 와 결제창 주소를 받는다   (돈 안 움직임)
//   2) 결제창으로 이동 → 사용자가 인증 → pg_token 을 들고 복귀 URL 로 돌아온다
//   3) approve  서버가 그 토큰으로 승인을 마친다                        (실제 청구)
//
// 프론트가 PG 를 직접 부르지 않는다. 시크릿이 필요해서 서버만 호출할 수 있고,
// 프론트가 부르면 그 값이 브라우저에 노출된다.

import { apiClient } from '../../../services/apiClient.ts'
import type { PaymentReady, Trade } from '../types/trade.types.ts'

/**
 * 결제 준비 — `POST /v1/trades/{tradeId}/payment/ready`
 *
 * **아직 청구되지 않는다.** 응답의 `redirectUrl` 로 사용자를 보내면 결제창이 열린다.
 *
 * `live` 가 false 면 서버에 카카오페이 키가 없어 Mock 게이트웨이가 붙은 것이다.
 * 그때는 `redirectUrl` 이 비어 있고, 화면은 이동하지 않고 바로 승인으로 갈 수 있다.
 */
export async function readyPayment(tradeId: number): Promise<PaymentReady> {
  const { data } = await apiClient.post<PaymentReady>(`/v1/trades/${tradeId}/payment/ready`)
  return data
}

/**
 * 결제 승인 — `POST /v1/trades/{tradeId}/payment/approve`
 *
 * **이 시점에 실제로 청구된다.** 성공하면 거래가 PAID 로 넘어간다. 이때 Material 은 아직
 * 이동하지 않는다 — 소유권 이전은 Buyer 수령 확인 시점이다 (정책 07.2).
 *
 * 금액과 tid 를 보내지 않는다. 금액은 서버가 PG 응답에서 읽어 거래 금액과 대조하고,
 * tid 는 준비 단계에서 서버가 거래에 저장해 두었다. 둘 다 클라이언트가 말하게 두면
 * 조작 경로가 된다.
 *
 * 준비를 건너뛰고 부르면 `PAYMENT_NOT_PREPARED`, 이미 결제된 거래면
 * `PAYMENT_STATE_INVALID` 로 거절된다 (뒤로 가기로 복귀 URL 을 다시 열면 실제로 두 번 들어온다).
 */
export async function approvePayment(tradeId: number, pgToken: string): Promise<Trade> {
  const { data } = await apiClient.post<Trade>(`/v1/trades/${tradeId}/payment/approve`, {
    pgToken,
  })
  return data
}

/**
 * 거래 취소 — `POST /v1/trades/{tradeId}/cancel`
 *
 * 당사자 양쪽 모두 부를 수 있고 **발송 전까지만** 가능하다. 배송이 시작된 뒤의 되돌리기는
 * 분쟁 절차(정책 09)다.
 *
 * 결제까지 끝난 거래면 서버가 **환불을 먼저 요청하고** 상태를 바꾼다. 환불이 실패하면
 * 거래는 PAID 로 남는다 — 돈은 그대로인데 거래만 취소된 상태를 만들지 않기 위해서다.
 *
 * 판매글의 잔여 수량도 함께 복구된다. **결제창을 닫은 사용자를 반드시 이 경로로 보내야
 * 한다** — 안 보내면 그 수량이 아무도 살 수 없는 채로 남는다.
 *
 * 사유는 선택이다. 값이 없으면 **바디를 아예 보내지 않는다** — 서버가 빈 문자열을 null 로
 * 정규화하므로 보내도 결과는 같지만, 사유 없는 취소가 정상 경로라는 것을 요청 모양이
 * 그대로 말하게 둔다. 기존 호출부(`usePayment.cancel`)는 인자를 늘리지 않아도 그대로 동작한다.
 *
 * ⚠️ 취소는 되돌릴 수 없다. 같은 수량을 다시 사려면 판매글에서 새로 구매해야 한다.
 */
export async function cancelTrade(tradeId: number, reason?: string): Promise<Trade> {
  const trimmed = reason?.trim()
  const { data } = await apiClient.post<Trade>(
    `/v1/trades/${tradeId}/cancel`,
    trimmed ? { reason: trimmed } : undefined,
  )
  return data
}
