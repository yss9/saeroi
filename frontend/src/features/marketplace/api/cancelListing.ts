import { apiClient } from '../../../services/apiClient.ts'
import type { Listing } from '../types/marketplace.types.ts'

/**
 * 판매 취소.
 *
 * **남은 수량만 회수된다.** 이미 팔린 몫은 각자 거래로 살아 있고, 진행 중인 거래가
 * 하나라도 있으면 서버가 `LISTING_HAS_ONGOING_TRADE` 로 거절한다 — 판매글을 내리며
 * 잠금을 풀면 판매자가 배송해야 할 물건을 다른 곳에 또 팔 수 있기 때문이다.
 *
 * `DELETE` 지만 행이 지워지지 않는다. 이미 성사된 거래들이 이 판매글을 참조하고 있어
 * 지울 수 없고, 지워서도 안 된다 — 거래 이력이 어디서 시작됐는지가 추적의 일부다.
 *
 * 취소 사유는 서버가 받지 않는다. `material_listing` 에 사유 컬럼이 없다.
 */
export async function cancelListing(listingId: number): Promise<Listing> {
  const { data } = await apiClient.delete<Listing>(`/v1/listings/${listingId}`)
  return data
}
