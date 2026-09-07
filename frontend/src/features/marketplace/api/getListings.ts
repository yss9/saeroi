import { apiClient } from '../../../services/apiClient.ts'
import type { ListingFilters, ListingSort, ListingSummary } from '../types/marketplace.types.ts'
import { allListings } from './mock/marketplaceMockStore.ts'

export interface GetListingsParams extends ListingFilters {
  sort?: ListingSort
}

/**
 * 판매 목록 조회 (Brand Market).
 *
 * ⚠️ Backend `GET /listings` 가 아직 없어 Mock 을 쓴다.
 * TODO(3번): 아래 두 줄로 교체한다. 훅·컴포넌트는 그대로 둔다.
 *   const { data } = await apiClient.get<ListingSummary[]>('/listings', { params })
 *   return data
 */
export async function getListings(params: GetListingsParams = {}): Promise<ListingSummary[]> {
  void apiClient
  await delay()

  let rows = allListings()

  // 기본값은 판매 중인 것만 본다. 품절·취소된 글이 섞이면 목록의 의미가 흐려진다.
  rows = rows.filter((row) => row.status === (params.status ?? 'ON_SALE'))

  if (params.materialType) rows = rows.filter((r) => r.materialType === params.materialType)
  if (params.rootOrigin) rows = rows.filter((r) => r.rootOrigin === params.rootOrigin)
  if (params.grade) rows = rows.filter((r) => r.grade === params.grade)
  if (params.minPricePerKg !== undefined) rows = rows.filter((r) => r.pricePerKg >= params.minPricePerKg!)
  if (params.maxPricePerKg !== undefined) rows = rows.filter((r) => r.pricePerKg <= params.maxPricePerKg!)
  if (params.minAvailableQuantity !== undefined) {
    rows = rows.filter((r) => r.availableQuantity >= params.minAvailableQuantity!)
  }

  return sortRows(rows, params.sort ?? 'LATEST')
}

function sortRows(rows: ListingSummary[], sort: ListingSort): ListingSummary[] {
  const sorted = [...rows]
  switch (sort) {
    case 'PRICE_ASC':
      return sorted.sort((a, b) => a.pricePerKg - b.pricePerKg)
    case 'PRICE_DESC':
      return sorted.sort((a, b) => b.pricePerKg - a.pricePerKg)
    case 'QUANTITY_DESC':
      return sorted.sort((a, b) => b.availableQuantity - a.availableQuantity)
    default:
      return sorted.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }
}

/** 로딩 UI 를 확인할 수 있게 지연을 준다. Backend 연결 시 사라진다. */
function delay(ms = 250): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
