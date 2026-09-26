import type { PageResponse } from '../../types/api.types.ts'
import { Button } from './Button.tsx'

export interface PaginationProps {
  page: PageResponse<unknown>
  /** 0-based 페이지 번호를 받는다 — 서버 기준을 그대로 쓴다 */
  onChange: (page: number) => void
}

/**
 * 이전·다음 페이지 이동.
 *
 * marketplace 에 있던 것을 공통으로 옮겼다 — 제품 목록이 두 번째로 필요해졌고,
 * `PageResponse` 와 콜백만 받아 도메인을 알지 못한다 (ARCHITECTURE §7 의 승격 조건).
 *
 * 페이지 번호 버튼을 나열하지 않는다. 목록이 자주 바뀌는 화면이라(다른 사람이 사면 순서가
 * 밀린다) 3페이지를 정확히 짚어 가는 것이 큰 의미가 없고, 번호 나열은 총 페이지 수가
 * 커질수록 생략 규칙이 필요해져 복잡해진다. 필요해지면 그때 넣는다.
 *
 * **사람에게는 1-based 로 보여준다.** 내부 값은 0-based 그대로 두고 표시할 때만 더한다 —
 * 반대로 하면 어딘가에서 한 번은 반드시 빼먹는다.
 */
export function Pagination({ page, onChange }: PaginationProps) {
  // 한 페이지에 다 들어가면 이동할 곳이 없다.
  if (page.totalPages <= 1) return null

  const isFirst = page.page <= 0

  return (
    <nav className="pagination" aria-label="페이지 이동">
      <Button
        variant="secondary"
        size="sm"
        disabled={isFirst}
        onClick={() => onChange(page.page - 1)}
      >
        이전
      </Button>

      {/* aria-live 로 알리는 이유 — 버튼을 눌러도 포커스가 그대로라 스크린리더가
          페이지가 바뀐 것을 알 방법이 없다. */}
      <span className="pagination__status" aria-live="polite">
        {page.page + 1} / {page.totalPages} 페이지
        <span className="pagination__total"> · 총 {page.totalElements.toLocaleString()}건</span>
      </span>

      <Button
        variant="secondary"
        size="sm"
        disabled={!page.hasNext}
        onClick={() => onChange(page.page + 1)}
      >
        다음
      </Button>
    </nav>
  )
}
