// 소재 거래 결제 API.
//
// 정책 08절: 카카오페이 PG 직접 결제다. 플랫폼이 대금을 예치하지 않고(Escrow 폐지),
// **블록체인은 결제를 검증하지 않는다** — 온체인에는 소유권 이전 결과만 남는다.
//
// 카카오페이는 2단계다.
//
//   1) ready   서버가 PG 에 결제 준비를 요청하고 `tid` 와 결제창 주소를 받는다
//   2) approve 사용자가 결제창에서 인증하면 `pg_token` 을 들고 돌아오고,
//              서버가 그 토큰으로 승인을 마친다
//
// 프론트가 PG 를 직접 부르지 않는다. CID·시크릿이 필요해서 서버만 호출할 수 있고,
// 프론트가 부르면 그 값이 브라우저에 노출된다.

import { apiClient } from '../../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import { findTrade, restoreListingQuantity, updateTrade } from '../../marketplace/api/mock/marketplaceMockStore.ts'
import type {
  PaymentConfirmRequest,
  PaymentReadyResponse,
  TradeDetail,
} from '../types/trade.types.ts'

/**
 * 결제 준비 — `POST /trades/{tradeId}/payment`
 *
 * TODO(3번): Backend 준비되면 아래로 교체.
 *   const { data } = await apiClient.post<PaymentReadyResponse>(`/trades/${tradeId}/payment`)
 *   return data
 *
 * Mock 은 결제창으로 보내지 않는다. 실제 카카오페이 창을 띄우려면 CID 가 필요하고,
 * 그건 Backend 몫이다. 대신 가짜 tid 를 돌려주고 화면 흐름만 확인한다.
 */
export async function preparePayment(tradeId: number): Promise<PaymentReadyResponse> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 400))

  const trade = findTrade(tradeId)
  if (!trade) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '거래를 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }
  if (trade.status !== 'CREATED') {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '결제할 수 있는 상태가 아닙니다.',
      code: 'INVALID_TRADE_STATUS',
      status: 400,
    })
  }

  return {
    tid: `MOCK-TID-${tradeId}-${Date.now()}`,
    // 실제로는 카카오페이 결제창 주소다. Mock 은 이동하지 않는다.
    redirectUrl: '',
  }
}

/**
 * 결제 승인 — `POST /trades/{tradeId}/payment/confirm`
 *
 * 승인에 성공하면 거래가 PAID 로 넘어간다. 이때 Material 은 아직 이동하지 않는다 —
 * 소유권 이전은 Buyer 수령 확인 시점이다 (정책 07.2).
 *
 * TODO(3번): `apiClient.post<TradeDetail>(`/trades/${tradeId}/payment/confirm`, payload)` 로 교체.
 */
export async function confirmPayment(
  tradeId: number,
  payload: PaymentConfirmRequest,
): Promise<TradeDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 500))

  const updated = updateTrade(tradeId, {
    status: 'PAID',
    paymentMethod: 'KAKAOPAY',
    paymentReference: payload.tid,
    paidAt: new Date().toISOString(),
  })
  if (!updated) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '거래를 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }
  return updated
}

/**
 * 거래 취소 — `PATCH /trades/{tradeId}/cancel`
 *
 * 정책 07.3 순서를 그대로 따른다.
 *   1) 결제가 완료된 건이면 PG 취소 호출 → cancelReference 기록
 *   2) Listing 잔여 판매 수량 복구
 *   3) status → CANCELED
 *
 * 배송이 시작된 뒤의 취소는 여기가 아니라 분쟁 절차(정책 09)로 간다.
 *
 * TODO(3번): `apiClient.patch<TradeDetail>(`/trades/${tradeId}/cancel`, { reason })` 로 교체.
 */
export async function cancelTrade(tradeId: number, reason?: string): Promise<TradeDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 400))

  const trade = findTrade(tradeId)
  if (!trade) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '거래를 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }
  if (trade.status === 'SHIPPED' || trade.status === 'RECEIVED' || trade.status === 'COMPLETED') {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '배송이 시작된 거래는 취소할 수 없습니다. 분쟁 신청을 이용해 주세요.',
      code: 'INVALID_TRADE_STATUS',
      status: 400,
    })
  }

  // 결제가 완료된 건이면 PG 취소 참조를 남긴다.
  const wasPaid = trade.status === 'PAID'
  restoreListingQuantity(trade.listingId, trade.quantity)

  return updateTrade(tradeId, {
    status: 'CANCELED',
    cancelReason: reason ?? null,
    canceledAt: new Date().toISOString(),
    cancelReference: wasPaid ? `MOCK-CANCEL-${tradeId}` : null,
  })!
}
