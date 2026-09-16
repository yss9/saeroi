import { CaretDown } from '@phosphor-icons/react'
import { useEffect, useRef, useState } from 'react'
import { LISTING_SORT_LABEL, type ListingSort } from '../types/marketplace.types.ts'

export interface ListingSortBarProps {
  sort: ListingSort
  onSortChange: (sort: ListingSort) => void
}

const SORT_OPTIONS = Object.keys(LISTING_SORT_LABEL) as ListingSort[]

/**
 * 목록 정렬.
 *
 * **필터 상자 밖, 목록 바로 위에 선다.** 정렬은 결과를 *거르는* 조건이 아니라 *보는
 * 방식*이라 조건 칸들과 나란히 두면 다섯 번째 조건처럼 읽힌다. 바꾸는 순간 눈이 가야 할
 * 곳도 조건 상자가 아니라 목록이다.
 *
 * "정렬" 이라는 이름을 적지 않는다 — 지금 값이 "최신순" 이면 그 자체로 정렬임을 안다.
 * 대신 그 이름을 버튼의 `aria-label` 에 남긴다. 화면에서 지웠다고 스크린리더에서까지
 * 사라지면 이 버튼이 무엇을 바꾸는지 알 길이 없다.
 */
export function ListingSortBar({ sort, onSortChange }: ListingSortBarProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)

  // 바깥을 누르면 닫는다. 열어 둔 채 목록을 누르면 메뉴가 그 위에 남는다.
  useEffect(() => {
    if (!open) return

    function handlePointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }

    document.addEventListener('mousedown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [open])

  return (
    <div className="listing-sort" ref={rootRef}>
      <button
        type="button"
        className="listing-sort__trigger"
        aria-label={`정렬: ${LISTING_SORT_LABEL[sort]}`}
        aria-expanded={open}
        aria-haspopup="listbox"
        onClick={() => setOpen((value) => !value)}
      >
        {LISTING_SORT_LABEL[sort]}
        <CaretDown aria-hidden="true" data-open={open} />
      </button>

      {open && (
        <ul className="listing-sort__menu" role="listbox" aria-label="정렬 기준">
          {SORT_OPTIONS.map((value) => (
            <li key={value}>
              <button
                type="button"
                role="option"
                aria-selected={value === sort}
                className={value === sort ? 'is-selected' : undefined}
                onClick={() => {
                  onSortChange(value)
                  setOpen(false)
                }}
              >
                {LISTING_SORT_LABEL[value]}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
