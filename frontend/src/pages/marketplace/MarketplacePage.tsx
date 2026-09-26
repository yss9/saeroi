import { useState } from 'react'
import { ListingFilterBar } from '../../features/marketplace/components/ListingFilterBar.tsx'
import { ListingSortBar } from '../../features/marketplace/components/ListingSortBar.tsx'
import { ListingList } from '../../features/marketplace/components/ListingList.tsx'
import type { ListingFilters, ListingSort } from '../../features/marketplace/types/marketplace.types.ts'
import { AgentLauncher } from '../../features/agent/components/AgentLauncher.tsx'
import { useAuthStore } from '../../stores/authStore.ts'

/**
 * Brand Market — 소재를 사는 쪽.
 *
 * Page 는 조합만 한다. 필터 상태만 지역 상태로 들고, 조회·표시는 Feature 가 한다.
 */
export function MarketplacePage() {
  const [filters, setFilters] = useState<ListingFilters>({})
  const [sort, setSort] = useState<ListingSort>('LATEST')
  const [page, setPage] = useState(0)

  const role = useAuthStore((state) => state.user?.role)

  return (
    <main className="page">
      <h1 className="ui-visually-hidden">소재 검색</h1>

      {/* 소재를 사러 온 자리라 AI 어시스턴트 진입점이 여기 있다. 누르면 별도 창이 떠서
          이 화면을 보면서 대화할 수 있다 (BRAND 전용 — 다른 역할에는 보이지 않는다). */}
      {role === 'BRAND' && (
        <div className="marketplace-agent-entry">
          <AgentLauncher label="AI 어시스턴트로 소재 찾기" />
        </div>
      )}

      {/* 조건이 바뀌면 첫 페이지로 돌아간다. 3페이지를 보다가 필터를 걸면 결과가 한 페이지로
          줄어들 수 있고, 그러면 3페이지는 빈 화면이라 "결과가 없다" 로 읽힌다. */}
      <ListingFilterBar
        filters={filters}
        onFiltersChange={(next) => {
          setFilters(next)
          setPage(0)
        }}
        onReset={() => {
          setFilters({})
          setPage(0)
        }}
      />

      {/* 정렬은 조건 상자 밖, 목록 바로 위다 — 바꾸면 눈이 가야 할 곳이 목록이다. */}
      <ListingSortBar
        sort={sort}
        onSortChange={(next) => {
          setSort(next)
          setPage(0)
        }}
      />

      <ListingList params={{ ...filters, sort, page }} onPageChange={setPage} />
    </main>
  )
}
