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
  const [page, setPage] = useState(0)

  return (
    <main className="page">
      <header className="page__header">
        <h1>소재 검색</h1>
      </header>

      {/* 조건이 바뀌면 첫 페이지로 돌아간다. 3페이지를 보다가 필터를 걸면 결과가 한 페이지로
          줄어들 수 있고, 그러면 3페이지는 빈 화면이라 "결과가 없다" 로 읽힌다. */}
      <ListingFilterBar
        filters={filters}
        sort={sort}
        onFiltersChange={(next) => {
          setFilters(next)
          setPage(0)
        }}
        onSortChange={(next) => {
          setSort(next)
          setPage(0)
        }}
      />

      <ListingList params={{ ...filters, sort, page }} onPageChange={setPage} />
    </main>
  )
}
