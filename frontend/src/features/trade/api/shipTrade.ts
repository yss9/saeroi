import { apiClient } from '../../../services/apiClient.ts'
import type { ShipTradeRequest, TradeShipResult } from '../types/trade.types.ts'

/**
 * 판매자 발송 처리 — `PATCH /v1/trades/{tradeId}/ship`
 *
 * `PAID` 인 거래만 가능하고, 판매자 본인만 부를 수 있다 (서버가 `seller_id` 와 대조한다 —
 * `SUPPLIER` 역할만으로는 통과하지 못한다).
 *
 * **잠금도 보유량도 여기서 바뀌지 않는다.** 소유권은 구매자 수령 확인 시점에 넘어가고,
 * 배송 중에도 MARKETPLACE 잠금이 유지되어 재판매를 막는다 (정책 07.2). 그래서 이 요청의
 * 결과는 "상태와 송장" 뿐이다.
 *
 * ⚠️ 되돌릴 수 없다. 발송 뒤에는 `/cancel` 도 막히고 (취소는 발송 전까지만), 서버 엔티티가
 * 재발송을 거부한다. 화면이 확인 단계를 두는 이유다.
 */
export async function shipTrade(
  tradeId: number,
  payload: ShipTradeRequest,
): Promise<TradeShipResult> {
  const { data } = await apiClient.patch<TradeShipResult>(
    `/v1/trades/${tradeId}/ship`,
    // 빈 값은 필드를 뺀다. 서버도 blank 를 null 로 정규화하므로 보내도 결과는 같지만,
    // **앞뒤 공백을 지우는 것은 서버가 하지 않는다** — 공백이 섞인 택배사·운송장이
    // 그대로 저장되면 조회에 실패한다. 경계에서 한 번 정리해 호출부가 신경 쓰지 않게 한다.
    omitBlank(payload),
  )
  return data
}

function omitBlank(payload: ShipTradeRequest): ShipTradeRequest {
  const carrier = payload.carrier?.trim()
  const trackingNumber = payload.trackingNumber?.trim()

  return {
    ...(carrier ? { carrier } : {}),
    ...(trackingNumber ? { trackingNumber } : {}),
  }
}
