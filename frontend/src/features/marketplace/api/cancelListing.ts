import { apiClient } from '../../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ListingDetail } from '../types/marketplace.types.ts'
import { updateListingStatus } from './mock/marketplaceMockStore.ts'

/**
 * 판매 취소.
 *
 * 남은 수량의 MARKETPLACE Lock 이 해제되어 다시 쓸 수 있게 된다.
 * 이미 거래가 진행 중인 수량은 되돌리지 않는다 — 그건 거래 취소(정책 07.3) 영역이다.
 *
 * TODO(3번): `apiClient.patch<ListingDetail>('/listings/' + id + '/cancel', { reason })` 로 교체.
 */
export async function cancelListing(listingId: number, reason?: string): Promise<ListingDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300))

  const updated = updateListingStatus(listingId, 'CANCELED', reason)
  if (!updated) {
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '판매글을 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }
  return updated
}
