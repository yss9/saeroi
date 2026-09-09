import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Trade, TradeStatus } from '../types/trade.types.ts'
import { TradeChainPanel } from './TradeChainPanel.tsx'

/**
 * <b>늦게라도 온체인 주문을 만들 수 있어야 한다.</b>
 *
 * 예전에는 결제 직후(PAID)까지만 허용했다. 그때는 체인이 뒤처지면 따라잡을 방법이 없어,
 * 늦게 만든 주문이 배송도 수령도 없는 CREATED 로 영구히 남았기 때문이다.
 *
 * 지금은 {@code TradeChainActionPanel} 이 밀린 발송·수령 서명을 마저 받는다. 그래서 늦게
 * 만든 주문도 COMPLETED 까지 갈 수 있다. 이 테스트가 그 완화를 고정한다 — 다시 조이면
 * 이미 진행된 거래는 영영 오프체인에만 남는다.
 *
 * 취소된 거래만은 계속 막아야 한다. 일어나지 않은 거래를 체인에 남기는 것이고,
 * {@code createOrder} 가 판매 글 수량을 잠그므로 아무도 풀 수 없는 잠금이 생긴다.
 */
const mocks = vi.hoisted(() => ({
  chainListingId: 5 as number | null,
  order: vi.fn(),
}))

vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useWallet: () => ({ address: '0xc62aF11C2f7F5fbB39253e6ef7bDdd65BAda06Dd' }),
}))

vi.mock('../../marketplace/hooks/useListing.ts', () => ({
  useListing: () => ({ data: { chainListingId: mocks.chainListingId } }),
}))

vi.mock('../hooks/useTradeOnChain.ts', () => ({
  useTradeOnChain: () => ({
    order: mocks.order,
    status: 'IDLE',
    hash: null,
    error: null,
    isBusy: false,
    isRejected: false,
  }),
}))

vi.mock('../../../config/env.ts', () => ({
  env: { besuChainIdHex: '0x79f5', besuExplorerUrl: null },
}))

function trade(status: TradeStatus): Trade {
  return { tradeId: 1, listingId: 2, quantity: 5000, chainTradeId: null, status } as Trade
}

beforeEach(() => {
  mocks.chainListingId = 5
})

afterEach(cleanup)

describe('TradeChainPanel', () => {
  it('결제 직후에는 온체인 주문을 만들 수 있다', () => {
    render(<TradeChainPanel trade={trade('PAID')} />)

    expect(screen.getByRole('button', { name: '온체인 주문' })).toBeEnabled()
  })

  it('이미 수령까지 끝난 거래도 뒤늦게 온체인 주문을 만들 수 있다', () => {
    render(<TradeChainPanel trade={trade('RECEIVED')} />)

    expect(screen.getByRole('button', { name: '온체인 주문' })).toBeEnabled()
  })

  it('뒤늦게 만드는 것이라는 사실을 숨기지 않는다', () => {
    // 체인에 남는 시각은 실제 구매 시각이 아니라 지금이다. 그 차이를 말하지 않으면
    // 나중에 체인 기록을 읽는 사람이 구매 시점을 잘못 읽는다.
    render(<TradeChainPanel trade={trade('RECEIVED')} />)

    expect(screen.getByText(/실제 구매 시각이 아니라 지금/)).toBeInTheDocument()
  })

  it('취소된 거래는 체인에 남기지 않는다', () => {
    render(<TradeChainPanel trade={trade('CANCELED')} />)

    expect(screen.getByRole('button', { name: '온체인 주문' })).toBeDisabled()
    expect(screen.getByText(/취소된 거래는 온체인에 기록하지 않습니다/)).toBeInTheDocument()
  })

  it('판매자가 판매 글을 온체인에 올리지 않았으면 이유를 말한다', () => {
    // 구매자 혼자서는 시작할 수 없는 단계다. 버튼만 잠그면 자기가 뭘 잘못했다고 읽는다.
    mocks.chainListingId = null
    render(<TradeChainPanel trade={trade('PAID')} />)

    expect(screen.getByRole('button', { name: '온체인 주문' })).toBeDisabled()
    expect(screen.getByText(/판매자가 이 판매 글을 온체인에 등록하지 않았습니다/)).toBeInTheDocument()
  })
})
