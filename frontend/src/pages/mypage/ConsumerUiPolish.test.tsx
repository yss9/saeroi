import type { ReactNode } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsumerRecentlyViewedPage } from './ConsumerRecentlyViewedPage.tsx'
import { ConsumerAskInquiryPage } from './ConsumerAskInquiryPage.tsx'

vi.mock('../../features/auth/hooks/useAuth.ts', () => ({ useAuth: () => ({ user: null }) }))
vi.mock('../../features/auth/hooks/useLogout.ts', () => ({ useLogout: () => ({ mutate: vi.fn() }) }))
vi.mock('../../features/user/components/ConsumerAccountLayout.tsx', () => ({
  ConsumerAccountLayout: ({ children }: { children: ReactNode }) => <main>{children}</main>,
}))
vi.mock('../../features/product/hooks/usePublicProductModels.ts', () => ({
  usePublicProductModels: () => ({ data: { content: [{ productModelId: 31, name: '데님 백',
    brandId: 5, brandName: 'REWEAVE', price: 99000, stockQuantity: 10, thumbnailUrl: '', status: 'ON_SALE' }] } }),
}))
vi.mock('../../features/wishlist/WishlistButton.tsx', () => ({ WishlistButton: () => <button>찜하기</button> }))
vi.mock('../../features/cart/CartButton.tsx', () => ({ CartButton: () => <button>장바구니 담기</button> }))

afterEach(cleanup)

describe('consumer UI polish', () => {
  it('최근 본 상품은 공통 상품 카드와 상세 링크를 쓰고 장바구니 버튼은 생략한다', () => {
    render(<MemoryRouter><ConsumerRecentlyViewedPage /></MemoryRouter>)
    expect(screen.getByRole('list', { name: '최근 본 상품 목록' })).toHaveClass('shop-catalog__grid')
    expect(screen.getByRole('link', { name: '데님 백 상세 보기' })).toHaveAttribute('href', '/products/31')
    expect(screen.queryByRole('button', { name: '장바구니 담기' })).not.toBeInTheDocument()
  })

  it('문의 완료 안내와 주요 버튼을 표시하고 새 문의 작성으로 폼을 초기화한다', () => {
    render(<MemoryRouter><ConsumerAskInquiryPage /></MemoryRouter>)
    fireEvent.change(screen.getByLabelText('문의 유형'), { target: { value: 'ETC' } })
    fireEvent.change(screen.getByLabelText('문의 내용'), { target: { value: '문의 내용 예시' } })
    fireEvent.click(screen.getByRole('button', { name: '문의 등록' }))
    expect(screen.getByRole('heading', { name: '문의가 접수되었습니다.' })).toBeInTheDocument()
    const restart = screen.getByRole('button', { name: '새 문의 작성' })
    expect(restart).toHaveClass('ui-button--primary')
    fireEvent.click(restart)
    expect(screen.getByLabelText('문의 내용')).toHaveValue('')
    expect(screen.getByRole('button', { name: '문의 등록' })).toBeDisabled()
  })
})
