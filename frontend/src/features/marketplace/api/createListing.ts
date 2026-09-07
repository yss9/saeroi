import { apiClient } from '../../../services/apiClient.ts'
import type { CreateListingRequest, ListingDetail } from '../types/marketplace.types.ts'
import { insertListing } from './mock/marketplaceMockStore.ts'

/**
 * 판매 등록 (Supplier Market).
 *
 * 서버는 이 요청을 받아 Material 의 가용 수량을 확인하고 MARKETPLACE Lock 을 만든다
 * (정책 06.3). 즉 등록에 성공하면 그 수량은 즉시 다른 용도로 쓸 수 없다.
 *
 * TODO(3번): `apiClient.post<ListingDetail>('/listings', payload)` 로 교체.
 */
export async function createListing(payload: CreateListingRequest): Promise<ListingDetail> {
  void apiClient
  await new Promise((resolve) => setTimeout(resolve, 300))
  return insertListing(payload)
}
