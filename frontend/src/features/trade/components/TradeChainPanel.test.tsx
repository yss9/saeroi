// 구매 온체인 기록 칸을 언제 그리는가.
//
// **평소에 나오면 안 된다.** 구매 신청(`PurchaseForm`)이 거래를 만들면서 곧바로
// `createOrder` 까지 서명하므로, 상세에 닿는 거래는 대부분 이미 번호를 갖고 있다.
// 그때 남는 것은 제목·설명·번호뿐이라 — 버튼은 `!done` 일 때만 그려진다 — 결제하러 온
// 사람에게는 읽을 이유가 없는 칸이 결제 수단 위를 차지한다.
//
// **그렇다고 없앨 수는 없다.** 구매 때 서명이 거절·실패하면 `chainTradeId` 가 비고,
// 이 칸이 그것을 되살리는 유일한 수단이다. 되살리지 못한 거래는 발송·수령도 체인에
// 남지 않아 판매자 화면의 「이력 기록」이 영영 '미기록' 이 된다.
//
// 상세 페이지와 목록 팝업이 같은 `TradeDetail` 을 쓰므로 이 컴포넌트 한 곳이 둘 다 정한다.

import { render, screen, cleanup } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Trade } from '../types/trade.types.ts'
import { TradeChainPanel } from './TradeChainPanel.tsx'

// 지갑·판매글·체인 훅을 직접 자른다. 이 테스트가 보는 것은 "칸을 그리는가" 하나이고,
// 실제 훅을 끌고 오면 MetaMask 와 조회 캐시까지 준비해야 해 무엇을 검증하는지 흐려진다.
const wallet = vi.hoisted(() => ({ address: '0xabc' as string | null }))
vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useWallet: () => wallet,
  useSwitchChainNotice: () => ({ message: null, report: () => {} }),
}))

const listing = vi.hoisted(() => ({
  data: { chainListingId: 4 } as { chainListingId: number | null } | undefined,
}))
vi.mock('../../marketplace/hooks/useListing.ts', () => ({ useListing: () => listing }))

const chain = vi.hoisted(() => ({
  status: 'IDLE' as string,
  hash: null as string | null,
  error: null,
  isBusy: false,
  isRejected: false,
  order: vi.fn(),
}))
vi.mock('../hooks/useTradeOnChain.ts', () => ({ useTradeOnChain: () => chain }))

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 11,
    tradeCode: 'TRD-000011',
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
    paymentReference: 'MOCK-TID-11',
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

beforeEach(() => {
  wallet.address = '0xabc'
  listing.data = { chainListingId: 4 }
  chain.hash = null
  chain.isBusy = false
  chain.isRejected = false
})

afterEach(cleanup)

describe('구매 온체인 기록', () => {
  it('이미 기록된 거래에는 칸을 만들지 않는다', () => {
    const { container } = render(<TradeChainPanel trade={trade({ chainTradeId: 9 })} />)

    // 결제하러 온 사람에게는 누를 것도 할 일도 없는 칸이다.
    expect(container).toBeEmptyDOMElement()
  })

  it('기록이 빠진 거래에는 되살릴 수단을 남긴다', () => {
    render(<TradeChainPanel trade={trade({ chainTradeId: null })} />)

    // 여기가 사라지면 이 거래는 영영 체인에 오르지 못한다 — 발송·수령도 함께 잃는다.
    expect(screen.getByRole('button', { name: '온체인 주문' })).toBeEnabled()
  })

  it('방금 서명해 해시가 떠 있으면 결과를 남긴다', () => {
    chain.hash = '0x'.padEnd(66, 'a')

    render(<TradeChainPanel trade={trade({ chainTradeId: 9 })} />)

    // 앞 테스트와 다른 것은 해시뿐이므로(둘 다 chainTradeId 는 9), 이 쌍이 해시 조건만
    // 따로 못박는다. 서명이 끝나자마자 칸이 사라지면 성공했는지 알 수 없다.
    expect(screen.getByRole('heading', { name: '구매 온체인 기록' })).toBeInTheDocument()
  })
})
