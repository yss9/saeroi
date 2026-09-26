// 온체인 예약 해제 버튼.
//
// 구매 신청은 두 곳의 수량을 깎는다 — 서버와 체인. 서버 취소는 결제 마감 배치가 자동으로
// 하지만 체인은 당사자 서명이 있어야 풀린다. 풀지 않으면 판매자가 그 수량을 다시 팔 수
// 없고, 다음 구매자는 화면에서는 살 수 있는데 서명에서 막힌다.
//
// **언제 안 보여야 하는가가 더 중요하다.** 진행 중인 거래에 이 버튼이 뜨면 사용자가
// 눌러서 살아 있는 주문의 예약을 풀어 버린다 — 그 뒤 발송·수령 확인이 체인에서 막힌다.

import { render, screen, cleanup } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Trade, TradeStatus } from '../types/trade.types.ts'
import { TradeChainReleaseButton } from './TradeChainReleaseButton.tsx'

// 체인 조회·서명을 자른다. 이 테스트가 보는 것은 "버튼을 그리는가" 하나다.
const order = vi.hoisted(() => ({
  data: { status: 'CREATED' as string | null } as { status: string | null } | undefined,
}))
vi.mock('../hooks/useOnChainOrder.ts', () => ({ useOnChainOrder: () => order }))

const chain = vi.hoisted(() => ({
  isBusy: false,
  isRejected: false,
  error: null,
  cancel: vi.fn(),
}))
vi.mock('../hooks/useTradeChainAction.ts', () => ({ useTradeChainAction: () => chain }))

function trade(overrides: Partial<Trade> = {}): Trade {
  return {
    tradeId: 11,
    tradeCode: 'TRD-000011',
    chainTradeId: 9,
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
    paymentReference: null,
    paymentAmount: 0,
    status: 'CANCELED',
    createdAt: '2026-09-18T10:00:00',
    paidAt: null,
    shippedAt: null,
    receivedAt: null,
    buyerSignedAt: null,
    completedAt: null,
    canceledAt: '2026-09-18T10:30:00',
    cancelReason: '결제 기한이 지나 자동 취소되었습니다.',
    shipping: null,
    ...overrides,
  }
}

const release = () => screen.queryByRole('button', { name: '예약 수량 되돌리기' })

beforeEach(() => {
  order.data = { status: 'CREATED' }
  chain.isBusy = false
  chain.isRejected = false
  chain.cancel.mockReset()
})

afterEach(cleanup)

describe('나와야 할 때', () => {
  it('취소된 거래에 체인 예약이 남아 있으면 해제를 낸다', () => {
    render(<TradeChainReleaseButton trade={trade()} />)

    expect(release()).toBeEnabled()
  })

  it('누르면 그 주문 번호로 해제를 부른다', () => {
    render(<TradeChainReleaseButton trade={trade({ chainTradeId: 9 })} />)
    release()?.click()

    expect(chain.cancel).toHaveBeenCalledWith(9)
  })
})

/**
 * 안 나와야 할 때.
 *
 * <b>진행 중인 거래에 이 버튼이 뜨는 것이 가장 위험하다.</b> 살아 있는 주문의 예약을
 * 풀면 그 뒤 발송·수령 확인이 체인에서 막힌다 — 둘 다 그 주문 번호를 인자로 받는다.
 */
describe('안 나와야 할 때', () => {
  it.each<TradeStatus>(['CREATED', 'PAID', 'SHIPPED', 'RECEIVED', 'COMPLETED'])(
    '%s 인 거래에는 내지 않는다',
    (status) => {
      render(<TradeChainReleaseButton trade={trade({ status })} />)

      expect(release()).not.toBeInTheDocument()
    },
  )

  it('온체인 주문 자체가 없으면 풀 것도 없다', () => {
    render(<TradeChainReleaseButton trade={trade({ chainTradeId: null })} />)

    expect(release()).not.toBeInTheDocument()
  })

  it('체인에서 이미 취소됐으면 내지 않는다', () => {
    // 컨트랙트는 CREATED 일 때만 취소를 받는다. 이미 푼 사람에게 다시 풀라고 하면
    // 눌러도 order is not cancellable 로 되돌려진다.
    order.data = { status: 'CANCELED' }
    render(<TradeChainReleaseButton trade={trade()} />)

    expect(release()).not.toBeInTheDocument()
  })

  it('체인 상태를 모르면 아무 주장도 하지 않는다', () => {
    // RPC 실패. "남아 있다" 도 "정리됐다" 도 거짓일 수 있다.
    order.data = undefined
    render(<TradeChainReleaseButton trade={trade()} />)

    expect(release()).not.toBeInTheDocument()
  })
})
