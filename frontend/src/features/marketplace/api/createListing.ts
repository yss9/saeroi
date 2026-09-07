import { apiClient } from '../../../services/apiClient.ts'
import type { CreateListingRequest, Listing } from '../types/marketplace.types.ts'

/**
 * 판매 등록 (Supplier Market).
 *
 * 서버가 소재의 **가용 수량**(보유량 − 잠금 합계)을 확인하고 MARKETPLACE 잠금을 만든다.
 * 즉 등록에 성공하면 그 수량은 즉시 다른 판매글이나 가공에 쓸 수 없다.
 * 보유량만 보는 것이 아니라는 점이 중요하다 — 그랬다면 같은 30kg 을 여러 번 팔 수 있다.
 */
export async function createListing(payload: CreateListingRequest): Promise<Listing> {
  const { data } = await apiClient.post<Listing>('/listings', payload)
  return data
}
