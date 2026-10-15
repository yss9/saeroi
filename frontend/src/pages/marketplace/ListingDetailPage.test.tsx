// 판매 상세.
//
// 여기서 지키는 것은 **구매 요청을 여는 방법** 하나다. 폼을 늘 펼쳐 두면 소재를 보러 온
// 사람도 수량·배송지 여섯 칸을 지나쳐 스크롤해야 하고, 목록에서 "구매하기" 를 누른
// 사람은 반대로 한 번 더 눌러야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Listing } from '../../features/marketplace/types/marketplace.types.ts'
import { ListingDetailPage } from './ListingDetailPage.tsx'

const api = vi.hoisted(() => ({ getListing: vi.fn() }))
vi.mock('../../features/marketplace/api/getListing.ts', () => ({ getListing: api.getListing }))
// 구매 폼은 지갑·주소 검색까지 물고 있어 이 화면의 관심사가 아니다.
vi.mock('../../features/trade/components/PurchaseForm.tsx', () => ({
  PurchaseForm: () => <div data-testid="purchase-form" />,
}))
vi.mock('../../features/marketplace/components/ListingChainPanel.tsx', () => ({
  ListingChainPanel: () => null,
}))

function listing(overrides: Partial<Listing> = {}): Listing {
  return {
    listingId: 7,
    chainListingId: null,
    materialId: 3,
    materialCode: 'MAT-000003',
    materialName: '폐데님 원단',
    materialDescription: null,
    materialType: 'DENIM',
    rootOrigin: 'WASTE',
    generationType: 'INITIAL',
    grade: 'A',
    sellerId: 2,
    sellerName: '공급사',
    totalQuantity: 10_000,
    availableQuantity: 8_000,
    pricePerKg: 5_000,
    status: 'ON_SALE',
    createdAt: '2026-09-11T01:00:00',
    ...overrides,
  }
}

function renderPage(entry = '/marketplace/7') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/marketplace/:listingId" element={<ListingDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getListing.mockResolvedValue(listing())
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('ListingDetailPage', () => {
  it('제목 옆에 가공 이력을 보여준다', async () => {
    api.getListing.mockResolvedValue(listing({ completedProcessTypes: [] }))
    renderPage()

    expect(await screen.findByText('가공 전')).toBeInTheDocument()
  })

  it('구매하기를 누르기 전에는 구매 요청 폼을 열지 않는다', async () => {
    const user = renderPage()

    const open = await screen.findByRole('button', { name: '구매하기' })
    expect(screen.queryByTestId('purchase-form')).not.toBeVisible()

    await user.click(open)
    expect(screen.getByTestId('purchase-form')).toBeVisible()
  })

  // 목록에서 "구매하기" 를 누른 사람에게 한 번 더 누르게 하지 않는다.
  it('#purchase 로 들어오면 곧바로 열어 둔다', async () => {
    renderPage('/marketplace/7#purchase')

    expect(await screen.findByTestId('purchase-form')).toBeVisible()
  })

  // 눌러도 잠긴 폼이 나올 뿐이다 — 살 수 없는 판매글에는 길을 내지 않는다.
  it('판매 중이 아니면 구매하기를 내주지 않는다', async () => {
    api.getListing.mockResolvedValue(listing({ status: 'SOLD_OUT' }))
    renderPage()

    await screen.findByRole('heading', { name: '폐데님 원단' })
    expect(screen.queryByRole('button', { name: '구매하기' })).not.toBeInTheDocument()
  })
})
