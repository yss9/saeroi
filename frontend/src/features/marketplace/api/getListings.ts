import { apiClient } from '../../../services/apiClient.ts'
import type { PageResponse } from '../../../types/api.types.ts'
import type { Listing, ListingFilters, ListingSort } from '../types/marketplace.types.ts'

export interface GetListingsParams extends ListingFilters {
  sort?: ListingSort
}

/**
 * 판매 목록 조회.
 *
 * Brand Market(전체)과 Supplier Market(내 판매글)이 **같은 엔드포인트**를 쓴다.
 * 차이는 `sellerId` 조건 하나뿐이고 필터·정렬·페이지는 완전히 같다.
 *
 * 서버가 기본으로 `ON_SALE` 만 돌려준다. 품절·취소된 글까지 보려면 `status` 를 준다.
 */
export async function getListings(
  params: GetListingsParams = {},
): Promise<PageResponse<Listing>> {
  const { data } = await apiClient.get<PageResponse<Listing>>('/v1/listings', { params })
  return data
}
