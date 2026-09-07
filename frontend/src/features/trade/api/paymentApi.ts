// 소재 거래 결제 API.
//
// 정책 08절: 카카오페이 PG 직접 결제다. 플랫폼이 대금을 예치하지 않고(Escrow 폐지),
// **블록체인은 결제를 검증하지 않는다** — `Marketplace.sol` 의 `Order` 구조체에 금액
// 필드가 없고 `createListing` 에 가격 인자가 없다. 온체인에는 수량과 소유권만 남는다.
//
// ⚠️ **PG 연동 전이다.** 지금은 서버가 카카오페이를 부르지 않고, 클라이언트가 승인
// 결과를 알려주는 구조다. 그래서 서버가 승인 금액을 거래 금액과 반드시 대조한다.
//
// 실제 카카오페이는 2단계다 — `ready` 로 결제창 주소와 tid 를 받고, 사용자가 인증한 뒤
// `approve` 로 승인한다. 붙일 때 바뀌는 것은 **서버 안쪽과 이 파일의 두 함수뿐**이고,
// 호출하는 훅(usePayment)과 화면의 상태 흐름은 그대로다.
//
// 프론트가 PG 를 직접 부르지 않는다. CID·시크릿이 필요해서 서버만 호출할 수 있고,
// 프론트가 부르면 그 값이 브라우저에 노출된다.

import { apiClient } from '../../../services/apiClient.ts'
import type { PaymentConfirmRequest, Trade } from '../types/trade.types.ts'

/**
 * 결제 승인 — `POST /trades/{tradeId}/payment`
 *
 * 성공하면 거래가 PAID 로 넘어간다. 이때 Material 은 아직 이동하지 않는다 —
 * 소유권 이전은 Buyer 수령 확인 시점이다 (정책 07.2).
 *
 * `CREATED` 가 아닌 거래에 부르면 서버가 `PAYMENT_STATE_INVALID` 로 거절한다.
 * 중복 결제를 막는 지점이다.
 */
export async function confirmPayment(
  tradeId: number,
  payload: PaymentConfirmRequest,
): Promise<Trade> {
  const { data } = await apiClient.post<Trade>(`/trades/${tradeId}/payment`, payload)
  return data
}

/**
 * 거래 취소 — `POST /trades/{tradeId}/cancel`
 *
 * 당사자 양쪽 모두 부를 수 있고 **발송 전까지만** 가능하다. 배송이 시작된 뒤의 되돌리기는
 * 분쟁 절차(정책 09)다.
 *
 * 서버가 판매글의 잔여 수량을 함께 복구한다. **결제창을 닫은 사용자를 반드시 이 경로로
 * 보내야 한다** — 안 보내면 그 수량이 아무도 살 수 없는 채로 남는다.
 *
 * `DELETE` 가 아니라 `POST /cancel` 인 것은 거래 행을 지우지 않기 때문이다. 취소된 거래도
 * 이력이고, 같은 판매글에서 몇 건이 취소됐는지가 분쟁 때 근거가 된다.
 *
 * 취소 사유는 서버가 받지 않는다 — `material_trade` 에 사유 컬럼이 없다.
 */
export async function cancelTrade(tradeId: number): Promise<Trade> {
  const { data } = await apiClient.post<Trade>(`/trades/${tradeId}/cancel`)
  return data
}
