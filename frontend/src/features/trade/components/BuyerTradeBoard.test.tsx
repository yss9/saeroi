// 구매 내역 상태 필터.
//
// 목록은 서버가 쪽수로 잘라 준다. 그래서 필터는 **서버로 나가야** 한다 — 화면에서 걸러
// 봐야 지금 쪽에 있는 것만 걸러지고, 다음 쪽에 있는 결제 대기 건은 "없다" 가 된다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Trade } from '../types/trade.types.ts'
import { BuyerTradeBoard } from './BuyerTradeBoard.tsx'

const api = vi.hoisted(() => ({ getTrades: vi.fn() }))
vi.mock('../api/getTrades.ts', () => ({ getTrades: api.getTrades }))

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 1,
    tradeCode: 'TRD-000001',
    chainTradeId: null,
    listingId: 10,
    materialId: 20,
    materialCode: 'MTR-000020',
    materialName: '폐데님 원단',
    sellerId: 2,
    sellerName: '공급업체',
    buyerId: 3,
    buyerName: '브랜드',
    sellerWallet: '0x1111111111111111111111111111111111111111',
    buyerWallet: '0x2222222222222222222222222222222222222222',
    quantity: 20_000,
    pricePerKg: 5_000,
    totalPrice: 100_000,
    paymentMethod: 'MOCK',
    paymentTid: null,
    status: 'CREATED',
    createdAt: '2026-09-10T10:00:00',
    ...overrides,
  } as Trade
}

function renderBoard() {
  api.getTrades.mockResolvedValue({
    content: [trade()],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <BuyerTradeBoard />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('BuyerTradeBoard', () => {
  it('처음에는 상태를 걸지 않고 전체를 불러온다', async () => {
    renderBoard()

    await waitFor(() => expect(api.getTrades).toHaveBeenCalled())
    expect(api.getTrades.mock.calls[0][0]).toEqual({ status: undefined })
  })

  // 이 테스트가 이 파일의 존재 이유다 — 필터가 서버로 나가지 않으면 결과가 거짓이 된다.
  it('상태를 고르면 서버 조회 조건에 실어 보낸다', async () => {
    const user = renderBoard()
    await waitFor(() => expect(api.getTrades).toHaveBeenCalled())

    await user.click(screen.getByRole('button', { name: '결제 완료' }))

    // TanStack Query 가 두 번째 인자로 컨텍스트를 넘기므로 첫 인자만 본다.
    await waitFor(() =>
      expect(api.getTrades.mock.calls.at(-1)?.[0]).toEqual({ status: 'PAID' }),
    )
  })

  // side 를 보내면 안 된다. 서버 기본값이 BUY 이고, 잘못 보내면 남의 판매 목록이 섞인다.
  it('구매 관점을 서버 기본값에 맡긴다', async () => {
    renderBoard()

    await waitFor(() => expect(api.getTrades).toHaveBeenCalled())
    expect(api.getTrades.mock.calls[0][0]).not.toHaveProperty('side')
  })
})
