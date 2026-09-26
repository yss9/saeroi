// 거래 목록 (구매·판매 공용). 표 한 줄에 코드·소재명·수량·금액·상태·발송 안내까지
// 늘어놓아 옆으로 너무 길었던 것을 카드로 바꿨다 — InventoryList 와 같은 이유.
//
// 모킹 경계는 API 모듈 하나다. vitest 에 globals 가 없어 cleanup 을 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { Trade } from '../types/trade.types.ts'
import { TradeList } from './TradeList.tsx'

const api = vi.hoisted(() => ({ getTrades: vi.fn() }))
vi.mock('../api/getTrades.ts', () => ({ getTrades: api.getTrades }))

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 1,
    tradeCode: 'TRD-000001',
    chainTradeId: null,
    listingId: 1,
    materialId: 1,
    materialCode: 'MAT-000001',
    materialName: '폐데님 원단',
    sellerId: 1,
    sellerName: '공급자',
    buyerId: 2,
    buyerName: '브랜드',
    sellerWallet: '0xseller',
    buyerWallet: '0xbuyer',
    quantity: 20_000,
    pricePerKg: 5_000,
    totalPrice: 100_000,
    paymentMethod: 'KAKAO_PAY',
    paymentReference: 'tid-1',
    paymentAmount: 100_000,
    status: 'PAID',
    createdAt: '2026-09-01T09:00:00',
    paidAt: '2026-09-01T09:10:00',
    shippedAt: null,
    receivedAt: null,
    buyerSignedAt: null,
    completedAt: null,
    canceledAt: null,
    cancelReason: null,
    shipping: null,
    ...overrides,
  }
}

function renderList(filters?: { side?: 'BUY' | 'SELL' }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <TradeList filters={filters} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

// mockReset() 이 아니라 mockClear() 다 — 이 조합(빈 Promise 로 응답을 영원히 미루는
// 로딩 테스트 + TanStack Query)에서 mockReset() 을 쓰면 테스트가 걸린다(원인 미상,
// vitest/msw 상호작용으로 추정). 각 테스트가 자기 응답을 매번 명시적으로 지정하므로
// 호출 이력만 지우는 mockClear() 로 충분하다.
beforeEach(() => api.getTrades.mockClear())
afterEach(cleanup)

describe('거래 카드', () => {
  it('소재명 · 수량 · 금액 · 상태를 카드로 보여준다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderList()

    expect(await screen.findByText('폐데님 원단')).toBeInTheDocument()
    // 거래 번호는 카드에 없다. 고르는 기준은 소재 이름이고, 번호는 상세에 있다.
    expect(screen.queryByText('TRD-000001')).not.toBeInTheDocument()
    expect(screen.getByText('20 kg')).toBeInTheDocument()
    expect(screen.getByText('100,000원')).toBeInTheDocument()
    expect(screen.getByText('결제 완료')).toBeInTheDocument()
  })

  it('상세로 이동하는 링크를 준다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade()],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderList()

    const link = await screen.findByRole('link', { name: /폐데님 원단/ })
    expect(link).toHaveAttribute('href', '/trades/1')
  })

  it('판매자 화면에서는 결제 완료 건에 발송 필요를 붙인다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade({ status: 'PAID' })],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderList({ side: 'SELL' })

    expect(await screen.findByText('발송 필요')).toBeInTheDocument()
  })

  // 물건이 오는 동안 구매자가 할 수 있는 일은 없다 — 배송 중마다 안내가 붙으면 정작
  // 결제가 남은 건이 묻힌다.
  it('구매자 화면에서 배송 중 건에는 안내를 붙이지 않는다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade({ status: 'SHIPPED' })],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderList({ side: 'BUY' })

    await screen.findByText('배송 중')
    expect(screen.queryByText('확인 필요')).not.toBeInTheDocument()
    expect(screen.queryByText('결제 필요')).not.toBeInTheDocument()
  })

  // 아직 결제하지 않은 건은 목록에서 바로 짚여야 한다.
  it('구매자 화면에서는 결제 전 건에 결제 필요를 붙인다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade({ status: 'CREATED' })],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderList({ side: 'BUY' })

    expect(await screen.findByText('결제 필요')).toBeInTheDocument()
  })

  it('완료된 거래에는 안내를 붙이지 않는다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade({ status: 'COMPLETED' })],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    })
    renderList({ side: 'SELL' })

    await screen.findByText('거래 완료')
    expect(screen.queryByText('발송 필요')).not.toBeInTheDocument()
    expect(screen.queryByText('확인 필요')).not.toBeInTheDocument()
  })
})

describe('세 가지 상태', () => {
  it('조회 중에는 로딩을 보여준다', async () => {
    // 영원히 응답하지 않는 Promise 를 쓰지 않는다 — 이 조합(TanStack Query + 여러
    // 테스트가 도는 파일)에서 절대 안 풀리는 Promise 는 다음 테스트까지 멈추게
    // 만든다. 로딩 확인 뒤 직접 풀어 정리한다.
    let resolvePending: (value: unknown) => void = () => {}
    api.getTrades.mockReturnValue(new Promise((resolve) => { resolvePending = resolve }))
    renderList()

    expect(screen.getByRole('status', { name: '거래 내역을 불러오는 중' })).toBeInTheDocument()

    resolvePending({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    await screen.findByText('거래 내역이 없습니다')
  })

  it('거래가 없으면 EmptyState 다 — 에러가 아니다', async () => {
    api.getTrades.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    renderList()

    expect(await screen.findByText('거래 내역이 없습니다')).toBeInTheDocument()
  })

  it('실패하면 ErrorMessage 다', async () => {
    // mockRejectedValue(고정값) 이 아니라 Once 다 — TanStack Query 가 이 쿼리를
    // 조용히 한 번 더 부르는 경우가 있는데(retry:false 인데도), 그 두 번째 호출까지
    // 계속 reject 하면 아무도 못 잡는 시점에 거부되어 "처리되지 않은 Promise 거부"로
    // 이 테스트가 아니라 엉뚱하게 실패로 잡힌다. 두 번째부터는 빈 목록으로 흘려보낸다.
    api.getTrades.mockRejectedValueOnce(
      createAppError(ERROR_KIND.API_ERROR, { message: '거래 내역을 불러오지 못했습니다.' }),
    )
    api.getTrades.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    renderList()

    expect(await screen.findByText('거래 내역을 불러오지 못했습니다.')).toBeInTheDocument()
  })

  it('여러 건이면 모두 카드로 보여준다', async () => {
    api.getTrades.mockResolvedValue({
      content: [trade({ tradeId: 1, tradeCode: 'TRD-000001' }), trade({ tradeId: 2, tradeCode: 'TRD-000002' })],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    renderList()

    await waitFor(() => expect(screen.getAllByRole('listitem')).toHaveLength(2))
  })
})
