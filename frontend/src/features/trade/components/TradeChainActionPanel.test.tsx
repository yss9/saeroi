import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Trade, TradeStatus } from '../types/trade.types.ts'
import { TradeChainActionPanel } from './TradeChainActionPanel.tsx'

/**
 * <b>이 패널은 평소에 나오면 안 된다.</b>
 *
 * 발송·수령의 체인 서명은 각자의 패널이 서버 성공 직후에 이어서 띄운다. 예전에는 이
 * 패널이 조건만 맞으면 항상 그려져서, 같은 동작의 버튼이 화면에 둘 있었다 — "수령 쪽에
 * 온체인 확인 버튼이 남아 있다" 는 제보가 그것이다.
 *
 * 그렇다고 지울 수도 없다. 서버 쓰기와 체인 서명은 별개 트랜잭션이라 한쪽만 성공할 수
 * 있고, 실제로 주문 #1 이 오프체인만 진행된 채 체인은 CREATED 에 멈춰 있다. 다시 서명할
 * 곳이 없으면 두 기록은 영영 어긋난다.
 *
 * 그래서 <b>체인이 서버보다 뒤처졌을 때만</b> 나와야 한다. 이 테스트가 그 경계를 고정한다.
 */
const mocks = vi.hoisted(() => ({
  chainStatus: 'COMPLETED' as string | null | undefined,
  ship: vi.fn(),
  receive: vi.fn(),
  cancel: vi.fn(),
}))

vi.mock('../../wallet/hooks/useWallet.ts', () => ({
  useWallet: () => ({ address: '0xc62aF11C2f7F5fbB39253e6ef7bDdd65BAda06Dd' }),
}))

vi.mock('../hooks/useChainOrderStatus.ts', () => ({
  useChainOrderStatus: () => ({ data: { status: mocks.chainStatus } }),
}))

vi.mock('../hooks/useTradeChainAction.ts', () => ({
  useTradeChainAction: () => ({
    ship: mocks.ship,
    receive: mocks.receive,
    cancel: mocks.cancel,
    status: 'IDLE',
    hash: null,
    error: null,
    isBusy: false,
    isRejected: false,
  }),
}))

function trade(status: TradeStatus, chainTradeId: number | null = 1): Trade {
  return { tradeId: 1, chainTradeId, status } as Trade
}

beforeEach(() => {
  mocks.chainStatus = 'COMPLETED'
})

afterEach(cleanup)

describe('TradeChainActionPanel', () => {
  it('체인이 서버를 따라잡았으면 아무것도 그리지 않는다', () => {
    // 정상 경로. ReceiptPanel 이 이미 서명을 이어서 끝냈다.
    mocks.chainStatus = 'COMPLETED'
    const { container } = render(
      <TradeChainActionPanel trade={trade('COMPLETED')} viewer="BUYER" />,
    )

    expect(container).toBeEmptyDOMElement()
  })

  it('체인 상태를 모르면 아무 주장도 하지 않는다', () => {
    // RPC 가 실패했을 수 있다. "밀렸다" 도 "끝났다" 도 거짓일 수 있으므로 침묵한다 —
    // 모르는 채로 서명시키면 사용자는 revert 메시지만 보게 된다.
    mocks.chainStatus = undefined
    const { container } = render(<TradeChainActionPanel trade={trade('SHIPPED')} viewer="SELLER" />)

    expect(container).toBeEmptyDOMElement()
  })

  it('발송이 체인에 안 남았으면 판매자에게 마저 서명할 버튼을 준다', () => {
    mocks.chainStatus = 'CREATED'
    render(<TradeChainActionPanel trade={trade('SHIPPED')} viewer="SELLER" />)

    expect(screen.getByRole('button', { name: /온체인 발송 마저 기록/ })).toBeInTheDocument()
  })

  it('발송이 체인에 안 남은 동안 구매자에게 수령 서명을 시키지 않는다', () => {
    // 컨트랙트가 순서를 검사한다 — confirmReceipt 는 주문이 SHIPPED 여야 통과한다
    // (Marketplace.sol:256). 버튼을 주면 사용자는 revert 만 보게 된다.
    mocks.chainStatus = 'CREATED'
    render(<TradeChainActionPanel trade={trade('RECEIVED')} viewer="BUYER" />)

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    expect(screen.getByText(/판매자의 온체인 발송이 먼저 기록돼야/)).toBeInTheDocument()
  })

  it('발송은 남았는데 수령이 안 남았으면 구매자가 마저 서명한다', () => {
    mocks.chainStatus = 'SHIPPED'
    render(<TradeChainActionPanel trade={trade('RECEIVED')} viewer="BUYER" />)

    expect(screen.getByRole('button', { name: /온체인 수령 마저 기록/ })).toBeInTheDocument()
  })

  it('취소된 거래인데 체인의 주문이 남아 있으면 정리할 자리를 준다', () => {
    // 서버는 취소하면서 판매 글에 수량을 되돌린다. 체인은 cancelOrder 를 서명해야만
    // 되돌아오므로, 놓치면 체인의 잔량만 작게 남아 다음 구매자가 전량 구매에서 막힌다.
    mocks.chainStatus = 'CREATED'
    render(<TradeChainActionPanel trade={trade('CANCELED')} viewer="BUYER" />)

    expect(screen.getByRole('button', { name: '온체인 주문 취소' })).toBeInTheDocument()
  })

  it('취소가 체인에도 반영돼 있으면 그리지 않는다', () => {
    mocks.chainStatus = 'CANCELED'
    const { container } = render(<TradeChainActionPanel trade={trade('CANCELED')} viewer="BUYER" />)

    expect(container).toBeEmptyDOMElement()
  })

  it('취소 정리는 판매자도 서명할 수 있다', () => {
    // 컨트랙트가 구매자·판매자 모두에게 열어 두었다(Marketplace.sol:279).
    // 먼저 보는 쪽이 정리하면 된다.
    mocks.chainStatus = 'CREATED'
    render(<TradeChainActionPanel trade={trade('CANCELED')} viewer="SELLER" />)

    expect(screen.getByRole('button', { name: '온체인 주문 취소' })).toBeInTheDocument()
  })

  it('온체인 주문 자체가 없으면 그리지 않는다', () => {
    const { container } = render(
      <TradeChainActionPanel trade={trade('SHIPPED', null)} viewer="SELLER" />,
    )

    expect(container).toBeEmptyDOMElement()
  })
})
