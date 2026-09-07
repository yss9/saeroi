import { apiClient } from '../../../services/apiClient.ts'
import type { Listing } from '../types/marketplace.types.ts'

/** 판매글 상세. 소재 정보가 함께 담겨 오므로 소재를 따로 조회하지 않는다. */
export async function getListing(listingId: number): Promise<Listing> {
  const { data } = await apiClient.get<Listing>(`/listings/${listingId}`)
  return data
}
