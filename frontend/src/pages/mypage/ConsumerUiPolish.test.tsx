import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

const inquiryApi = vi.hoisted(() => ({ createConsumerInquiry: vi.fn() }))
vi.mock('../../features/user/api/consumerInquiryApi.ts', () => inquiryApi)
vi.mock('../../features/user/api/getMyProductOrders.ts', () => ({
  getMyProductOrders: vi.fn().mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, hasNext: false }),
}))

function renderWithQueryClient(ui: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>)
}

afterEach(cleanup)

describe('consumer UI polish', () => {
  it('최근 본 상품은 공통 상품 카드와 상세 링크를 쓰고 장바구니 버튼은 생략한다', () => {
    renderWithQueryClient(<ConsumerRecentlyViewedPage />)
    expect(screen.getByRole('list', { name: '최근 본 상품 목록' })).toHaveClass('shop-catalog__grid')
    expect(screen.getByRole('link', { name: '데님 백 상세 보기' })).toHaveAttribute('href', '/products/31')
    expect(screen.queryByRole('button', { name: '장바구니 담기' })).not.toBeInTheDocument()
  })

  describe('문의하기', () => {
    beforeEach(() => vi.clearAllMocks())

    it('문의 완료 안내와 주요 버튼을 표시하고 새 문의 작성으로 폼을 초기화한다', async () => {
      inquiryApi.createConsumerInquiry.mockResolvedValue({
        inquiryId: 1,
        type: 'ETC',
        productOrderId: null,
        targetLabel: null,
        content: '문의 내용 예시',
        answered: false,
        answer: null,
        answeredAt: null,
        createdAt: '2026-09-18T00:00:00',
      })

      renderWithQueryClient(<ConsumerAskInquiryPage />)
      fireEvent.change(screen.getByLabelText('문의 유형'), { target: { value: 'ETC' } })
      fireEvent.change(screen.getByLabelText('문의 내용'), { target: { value: '문의 내용 예시' } })
      fireEvent.click(screen.getByRole('button', { name: '문의 등록' }))

      expect(await screen.findByRole('heading', { name: '문의가 접수되었습니다.' })).toBeInTheDocument()
      const restart = screen.getByRole('button', { name: '새 문의 작성' })
      expect(restart).toHaveClass('ui-button--primary')
      fireEvent.click(restart)
      expect(screen.getByLabelText('문의 내용')).toHaveValue('')
      expect(screen.getByRole('button', { name: '문의 등록' })).toBeDisabled()
    })

    it('등록 실패 시 안내를 보여주고 폼을 유지한다', async () => {
      inquiryApi.createConsumerInquiry.mockRejectedValue(new Error('서버 오류'))

      renderWithQueryClient(<ConsumerAskInquiryPage />)
      fireEvent.change(screen.getByLabelText('문의 유형'), { target: { value: 'ETC' } })
      fireEvent.change(screen.getByLabelText('문의 내용'), { target: { value: '문의 내용 예시' } })
      fireEvent.click(screen.getByRole('button', { name: '문의 등록' }))

      await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument())
      expect(screen.queryByRole('heading', { name: '문의가 접수되었습니다.' })).not.toBeInTheDocument()
    })
  })
})
