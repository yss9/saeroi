import { ArrowLeft, ArrowRight } from '@phosphor-icons/react'
import { paginationWindow } from './catalog.ts'

interface Props {
  page: number
  totalPages: number
  hasNext: boolean
  onPageChange: (page: number) => void
}

export function CatalogPagination({ page, totalPages, hasNext, onPageChange }: Props) {
  if (totalPages <= 1 || page >= totalPages) return null
  return (
    <nav className="shop-catalog__pagination" aria-label="상품 페이지">
      <button type="button" aria-label="이전 페이지" disabled={page === 0} onClick={() => onPageChange(page - 1)}><ArrowLeft aria-hidden="true" /></button>
      {paginationWindow(page, totalPages).map((number) => <button key={number} type="button" aria-label={`${number + 1}페이지`} aria-current={number === page ? 'page' : undefined} onClick={() => onPageChange(number)}>{number + 1}</button>)}
      <button type="button" aria-label="다음 페이지" disabled={!hasNext} onClick={() => onPageChange(page + 1)}><ArrowRight aria-hidden="true" /></button>
    </nav>
  )
}
