// 구매자 수령 확인.
//
// 이 버튼이 소유권을 옮긴다. 그래서 지켜야 하는 것은 둘이다.
//
//   1) **발송 전에는 누를 수 없다** — 서버도 거절하지만(TRADE_NOT_RECEIVABLE),
//      받지도 않은 물건을 받았다고 기록할 통로가 화면에 있으면 안 된다.
//   2) **확인 없이 호출되지 않는다** — 되돌릴 수 없고, 발송된 거래는 취소도 막혀 있다.
//
// 성공 후 이전 수량을 보여주는 것도 함께 본다. 그게 이 요청의 결과이고, 보이지 않으면
// 사용자는 보유 소재 화면을 따로 열어 확인해야 한다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Trade, TradeReceiptResult } from '../types/trade.types.ts'
import { ReceiptPanel } from './ReceiptPanel.tsx'

const api = vi.hoisted(() => ({ confirmTradeReceipt: vi.fn() }))
vi.mock('../api/confirmTradeReceipt.ts', () => api)

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 7,
    tradeCode: 'TRD-000007',
    chainTradeId: null,
    listingId: 3,
    materialId: 1,
    materialCode: 'MAT-000001',
    materialName: '세척된 폐데님',
    sellerId: 2,
    sellerName: '수거업체 A',
    buyerId: 5,
    buyerName: '브랜드 B',
    sellerWallet: '0x1111111111111111111111111111111111111111',
    buyerWallet: '0x2222222222222222222222222222222222222222',
    quantity: 12_000,
    pricePerKg: 4_200,
    totalPrice: 50_400,
    paymentMethod: 'MOCK',
    paymentReference: 'MOCK-TID-7',
    paymentAmount: 50_400,
    status: 'SHIPPED',
    createdAt: '2026-09-01T10:00:00',
    paidAt: '2026-09-02T10:00:00',
    shippedAt: '2026-09-03T09:00:00',
    receivedAt: null,
    buyerSignedAt: null,
    completedAt: null,
    // 배송지는 구매 시점에 확정된다. 이 값이 null 인 거래는 배송지 입력이 적용되기 전에
    // 만들어진 건이고, 서버에서도 발송이 막힌다.
    shipping: null,
    ...overrides,
  }
}

const RECEIPT: TradeReceiptResult = {
  tradeId: 7,
  tradeCode: 'TRD-000007',
  status: 'COMPLETED',
  receivedAt: '2026-09-05T14:00:00',
  buyerSignedAt: '2026-09-05T14:00:00',
  completedAt: '2026-09-05T14:00:00',
  transferredQuantity: 12_000,
  buyerQuantity: 32_000,
}

function renderPanel(value: Trade = trade()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ReceiptPanel trade={value} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

// beforeEach 가 함수를 반환하면 vitest 는 그것을 teardown 으로 실행한다.
// mockResolvedValue 는 mock 자신을 돌려주므로 블록으로 감싸야 한다.
beforeEach(() => {
  api.confirmTradeReceipt.mockResolvedValue(RECEIPT)
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ReceiptPanel', () => {
  it('발송 전에는 수령을 확인할 수 없다', () => {
    renderPanel(trade({ status: 'PAID', shippedAt: null }))

    expect(screen.queryByRole('button', { name: '수령 확인' })).not.toBeInTheDocument()
    expect(screen.getByText(/판매자의 발송을 기다리는 중/)).toBeInTheDocument()
  })

  it('되돌릴 수 없다는 것을 먼저 알린다', () => {
    renderPanel()

    expect(screen.getByText(/소유권이 이전되고 되돌릴 수 없습니다/)).toBeInTheDocument()
  })

  // 이 테스트가 이 파일의 존재 이유다.
  it('확인 단계를 거친 뒤에만 수령을 확정하고 이전 결과를 보여준다', async () => {
    const user = renderPanel()

    await user.click(screen.getByRole('button', { name: '수령 확인' }))
    expect(api.confirmTradeReceipt).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '수령 확정' }))

    expect(api.confirmTradeReceipt).toHaveBeenCalledWith(7)
    // 얼마가 옮겨졌고 내 보유량이 얼마가 됐는지가 이 요청의 결과다.
    expect(await screen.findByText('12 kg')).toBeInTheDocument()
    expect(screen.getByText('32 kg')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수령 확인' })).not.toBeInTheDocument()
  })

  it('확인 모달을 닫으면 수령을 확정하지 않는다', async () => {
    const user = renderPanel()

    await user.click(screen.getByRole('button', { name: '수령 확인' }))
    await user.click(screen.getByRole('button', { name: '취소' }))

    expect(api.confirmTradeReceipt).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: '수령 확인' })).toBeInTheDocument()
  })

  it('이미 완료된 거래는 확인 버튼 없이 시각만 보여준다', () => {
    renderPanel(
      trade({
        status: 'COMPLETED',
        receivedAt: '2026-09-05T14:00:00',
        buyerSignedAt: '2026-09-05T14:00:00',
        completedAt: '2026-09-05T14:00:00',
      }),
    )

    expect(screen.queryByRole('button', { name: '수령 확인' })).not.toBeInTheDocument()
    expect(screen.getByText('거래 완료')).toBeInTheDocument()
    expect(screen.getByText('수령 확인 시각')).toBeInTheDocument()
  })
})
