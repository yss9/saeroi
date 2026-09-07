import { apiClient } from '../../../services/apiClient.ts'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ListingDetail } from '../types/marketplace.types.ts'
import { findListing } from './mock/marketplaceMockStore.ts'

/**
 * 판매 상세 조회.
 *
 * TODO(3번): `apiClient.get<ListingDetail>('/listings/' + listingId)` 로 교체.
 */
export async function getListing(listingId: number): Promise<ListingDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 250))

  const listing = findListing(listingId)
  if (!listing) {
    // 서버가 404 를 줬을 때와 같은 형태로 던진다. 화면이 분기를 다르게 짜지 않도록.
    throw createAppError(ERROR_KIND.API_ERROR, {
      message: '판매글을 찾을 수 없습니다.',
      code: 'RESOURCE_NOT_FOUND',
      status: 404,
    })
  }
  return listing
}
