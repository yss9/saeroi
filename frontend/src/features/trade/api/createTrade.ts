import { apiClient } from '../../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import { findListing, insertTrade } from '../../marketplace/api/mock/marketplaceMockStore.ts'
import type { CreateTradeRequest, TradeDetail } from '../types/trade.types.ts'

/**
 * 구매 요청 (Brand Market).
 *
 * 성공하면 그 수량은 **즉시 예약된다** — 다른 구매자가 같은 수량을 살 수 없다
 * (정책 07.2). 아직 결제 전이며 상태는 CREATED 다.
 *
 * TODO(3번): `apiClient.post<TradeDetail>('/trades', payload)` 로 교체.
 */
export async function createTrade(payload: CreateTradeRequest): Promise<TradeDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300))

  const listing = findListing(payload.listingId)
  if (!listing) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '판매글을 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }

  // 서버도 반드시 이 검사를 한다. 클라이언트 검증만 믿으면 수량을 초과해 살 수 있다.
  if (payload.quantity > listing.availableQuantity) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '판매 가능 수량을 초과했습니다.',
      code: 'QUANTITY_EXCEEDED',
      status: 400,
    })
  }
  if (payload.quantity <= 0) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '구매 수량을 입력해 주세요.',
      code: 'VALIDATION_ERROR',
      status: 400,
    })
  }

  return insertTrade(payload.listingId, payload.quantity)
}
