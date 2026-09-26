// 거래 상세의 출구.
//
// **판매자와 구매자가 같은 주소를 쓴다.** 그래서 "돌아가기" 가 가리킬 곳이 사람마다 다르고,
// 여기서 틀리면 방금 본 거래가 없는 목록이 열린다 — 또는 아예 못 들어가는 화면이 열린다.
//
//   1) **구매 내역(/trades)은 BRAND 전용이다.** 판매자를 그리로 보내면 /403 이다.
//   2) **거래를 읽은 뒤에는 역할이 아니라 그 거래에서의 위치로 고른다.** SUPPLIER 라도
//      남의 거래의 판매자가 아니면 구매자 자리이고, 재판매가 열리면 BRAND 도 판매자가 된다.
//   3) **읽기 전(로딩·실패)에는 역할이 유일한 단서다.** 그래도 못 들어가는 곳으로는
//      보내지 않는다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '../../stores/authStore.ts'
import type { UserRole } from '../../types/role.types.ts'
import type { Trade } from '../../features/trade/types/trade.types.ts'
import { TradeDetailPage } from './TradeDetailPage.tsx'

const api = vi.hoisted(() => ({ getTrade: vi.fn() }))
vi.mock('../../features/trade/api/getTrade.ts', () => ({ getTrade: api.getTrade }))

// 이 화면은 당사자별 패널을 여럿 거느린다. 출구만 보므로 전부 잘라 낸다 — 패널마다
// 지갑·결제·체인 모킹을 끌고 오면 무엇을 검증하는 테스트인지 알 수 없게 된다.
vi.mock('../../features/trade/components/PaymentPanel.tsx', () => ({ PaymentPanel: () => null }))
vi.mock('../../features/trade/components/ReceiptPanel.tsx', () => ({ ReceiptPanel: () => null }))
vi.mock('../../features/trade/components/ShipmentPanel.tsx', () => ({ ShipmentPanel: () => null }))
vi.mock('../../features/trade/components/TradeChainPanel.tsx', () => ({
  TradeChainPanel: () => null,
}))
vi.mock('../../features/trade/components/TradeTimeline.tsx', () => ({ TradeTimeline: () => null }))

const SELLER_ID = 2
const BUYER_ID = 5

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 7,
    tradeCode: 'TRD-000007',
    chainTradeId: null,
    listingId: 3,
    materialId: 1,
    materialCode: 'MAT-000001',
    materialName: '세척된 폐데님',
    sellerId: SELLER_ID,
    sellerName: '수거업체 A',
    buyerId: BUYER_ID,
    buyerName: '브랜드 B',
    sellerWallet: '0x1111111111111111111111111111111111111111',
    buyerWallet: '0x2222222222222222222222222222222222222222',
    quantity: 12_000,
    pricePerKg: 4_200,
    totalPrice: 50_400,
    paymentMethod: 'MOCK',
    paymentReference: 'MOCK-TID-7',
    paymentAmount: 50_400,
    status: 'PAID',
    createdAt: '2026-09-01T10:00:00',
    paidAt: '2026-09-02T10:00:00',
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

function signInAs(role: UserRole, id: number) {
  useAuthStore.setState({
    user: { id, name: '테스트 계정', email: 'user@saeroi.test', role, walletAddress: null },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
}

function renderDetail(path = '/trades/7') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/trades/:tradeId" element={<TradeDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** 첫 번째 링크가 출구다 (BackLink). */
function exitLink(): HTMLElement {
  return screen.getAllByRole('link')[0]
}

beforeEach(() => {
  api.getTrade.mockResolvedValue(trade())
})
afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('TradeDetailPage 출구', () => {
  it('구매자는 구매 내역으로 돌아간다', async () => {
    signInAs('BRAND', BUYER_ID)
    renderDetail()

    const link = await screen.findByRole('link', { name: /구매 내역/ })
    expect(link).toHaveAttribute('href', '/trades')
  })

  // 판매자를 /trades 로 보내면 방금 본 거래가 없는 목록이 열린다 — 게다가 BRAND 전용이라
  // 아예 못 들어간다.
  it('판매자는 판매 거래로 돌아간다', async () => {
    signInAs('SUPPLIER', SELLER_ID)
    renderDetail()

    const link = await screen.findByRole('link', { name: /판매 거래/ })
    expect(link).toHaveAttribute('href', '/supplier/trades')
  })

  // 이 테스트가 이 파일의 존재 이유다 — 구매 내역은 BRAND 전용이다.
  it('읽기 전에도 판매자를 BRAND 전용 목록으로 보내지 않는다', () => {
    signInAs('SUPPLIER', SELLER_ID)
    // 응답이 오지 않는 상태. 로딩 화면의 출구를 본다.
    api.getTrade.mockReturnValue(new Promise(() => {}))
    renderDetail()

    expect(exitLink()).toHaveAttribute('href', '/supplier/trades')
  })

  it('주소가 잘못돼도 역할에 맞는 출구를 준다', () => {
    signInAs('SUPPLIER', SELLER_ID)
    renderDetail('/trades/abc')

    expect(exitLink()).toHaveAttribute('href', '/supplier/trades')
    expect(screen.getByRole('alert')).toHaveTextContent('잘못된 주소입니다')
  })

  // 역할이 아니라 그 거래에서의 위치로 고른다. 정책 07.1 로 재판매가 열리면 BRAND 도
  // 판매자 자리에 선다 — 그때 역할로 갈랐다면 이 화면이 조용히 틀린다.
  it('BRAND 가 판매자인 거래에서는 판매 거래로 돌아간다', async () => {
    signInAs('BRAND', SELLER_ID)
    renderDetail()

    const link = await screen.findByRole('link', { name: /판매 거래/ })
    expect(link).toHaveAttribute('href', '/supplier/trades')
  })
})

/**
 * 거래 요약.
 *
 * 이 화면은 <b>돈 이야기</b>인데 총 금액이 없었다. 수량과 단가만 있어서 사용자가 직접
 * 곱해야 했다 — `totalPrice` 는 응답에 처음부터 있던 값이다.
 *
 * 그리고 판매자·구매자를 둘 다 적었는데, 그중 하나는 언제나 이 화면을 보는 본인이다.
 * 내 화면에 내 회사 이름이 적혀 있는 셈이었다.
 */
describe('거래 요약', () => {
  it('총 거래 금액을 보여준다', async () => {
    signInAs('SUPPLIER', SELLER_ID)
    renderDetail()

    expect(await screen.findByText('총 거래 금액')).toBeInTheDocument()
    expect(screen.getByText('50,400원')).toBeInTheDocument()
  })

  it('판매자에게는 구매자만 보여준다', async () => {
    signInAs('SUPPLIER', SELLER_ID)
    renderDetail()

    expect(await screen.findByText('브랜드 B')).toBeInTheDocument()
    // 내 회사 이름은 내 화면에 적을 이유가 없다.
    expect(screen.queryByText('수거업체 A')).not.toBeInTheDocument()
  })
})

/**
 * 어느 본문이 떴는가.
 *
 * <b>판매자와 구매자가 서로 다른 본문을 본다.</b> 판매자는 `SellerTradeDetail`(2단,
 * 발송), 구매자는 `TradeDetail`(팝업에도 들어가는 한 줄)이다. 겉으로 그것을 가장 잘
 * 드러내는 값이 "발송 필요" 칩이다 — 판매자 본문에만 있다.
 *
 * <b>구매자 쪽은 새어 나오지 않는지만 본다.</b> 구매자 본문의 생김새는 그 컴포넌트의
 * 테스트가 지킨다. 여기서 단정하면 남의 화면을 이 파일이 붙잡게 된다.
 */
describe('본문 갈래', () => {
  it('결제된 거래는 판매자에게 발송 필요로 보인다', async () => {
    signInAs('SUPPLIER', SELLER_ID)
    renderDetail()

    expect(await screen.findByText('발송 필요')).toBeInTheDocument()
  })

  it('같은 거래가 구매자에게는 할 일로 보이지 않는다', async () => {
    // 구매자는 기다리는 구간이다. "발송 필요" 가 보이면 자기가 보내야 하는 줄 안다.
    // 두 본문이 갈린 뒤로는 이 단정이 경계를 지킨다 — 판매자 본문이 구매자에게 뜨면
    // 발송 패널까지 함께 뜬다.
    signInAs('BRAND', BUYER_ID)
    renderDetail()

    // 소재 이름은 제목과 요약 카드 양쪽에 있다. 화면이 떴다는 확인은 제목으로 한다.
    expect(await screen.findByRole('heading', { name: '세척된 폐데님' })).toBeInTheDocument()
    expect(screen.queryByText('발송 필요')).not.toBeInTheDocument()
  })

  // 결제를 마치고 발송을 기다리는 구간에는 구매자가 할 수 있는 일이 없다. 그때 온체인
  // 기록과 수령 확인이 서 있으면 지금 눌러야 하는 것으로 읽힌다.
  it('결제 완료 구간의 구매자에게는 온체인 기록과 수령 확인을 두지 않는다', async () => {
    signInAs('BRAND', BUYER_ID)
    renderDetail()

    await screen.findByRole('heading', { name: '세척된 폐데님' })
    expect(screen.queryByText('거래 이력 기록')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '수령 확인' })).not.toBeInTheDocument()
  })
})
