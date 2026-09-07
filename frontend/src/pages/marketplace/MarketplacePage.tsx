import { useState } from 'react'
import { ListingFilterBar } from '../../features/marketplace/components/ListingFilterBar.tsx'
import { ListingList } from '../../features/marketplace/components/ListingList.tsx'
import type { ListingFilters, ListingSort } from '../../features/marketplace/types/marketplace.types.ts'

/**
 * Brand Market — 소재를 사는 쪽.
 *
 * Page 는 조합만 한다. 필터 상태만 지역 상태로 들고, 조회·표시는 Feature 가 한다.
 */
export function MarketplacePage() {
  const [filters, setFilters] = useState<ListingFilters>({})
  const [sort, setSort] = useState<ListingSort>('LATEST')

  return (
    <main className="page">
      <header className="page__header">
        <h1>소재 마켓플레이스</h1>
        <p className="page__lead">
          검증된 폐소재와 가공 소재를 구매할 수 있습니다. 필요한 만큼만 부분 구매도 가능합니다.
        </p>
      </header>

      <ListingFilterBar
        filters={filters}
        sort={sort}
        onFiltersChange={setFilters}
        onSortChange={setSort}
      />

      <ListingList params={{ ...filters, sort }} />
    </main>
  )
}
