// 판매자 발송 처리.
//
// 이 화면에서 사고가 나면 안 되는 지점은 셋이다.
//
//   1) **결제 전 발송** — 결제되지 않은 건을 보내면 판매자가 물건을 잃는다.
//   2) **택배사 없는 운송장** — 서버가 거절하는 조합이라 제출 후에야 알게 되면 헛수고다.
//   3) **확인 없는 발송** — 발송하면 취소가 막힌다. 되돌릴 수 없다.
//
// 모킹 경계는 API 모듈 하나다 (InventoryTable.test 와 같은 방식).
// vitest 에 globals 가 없어 cleanup 을 직접 부른다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { Trade, TradeShipResult } from '../types/trade.types.ts'
import { ShipmentPanel } from './ShipmentPanel.tsx'

const api = vi.hoisted(() => ({ shipTrade: vi.fn(), cancelTrade: vi.fn() }))
vi.mock('../api/shipTrade.ts', () => ({ shipTrade: api.shipTrade }))
// 취소는 결제 흐름과 같은 API 모듈을 쓴다 (usePayment.cancel 과 한 함수를 공유한다).
vi.mock('../api/paymentApi.ts', () => ({ cancelTrade: api.cancelTrade }))

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
    status: 'PAID',
    createdAt: '2026-09-01T10:00:00',
    paidAt: '2026-09-02T10:00:00',
    shippedAt: null,
    receivedAt: null,
    buyerSignedAt: null,
    completedAt: null,
    canceledAt: null,
    cancelReason: null,
    // 배송지는 구매 시점에 확정된다. 이 값이 null 인 거래는 배송지 입력이 적용되기 전에
    // 만들어진 건이고, 서버에서도 발송이 막힌다.
    shipping: null,
    ...overrides,
  }
}

const SHIPPED: TradeShipResult = {
  tradeId: 7,
  tradeCode: 'TRD-000007',
  status: 'SHIPPED',
  shippedAt: '2026-09-03T09:00:00',
  carrier: 'CJ대한통운',
  trackingNumber: '123456789012',
}

function renderPanel(value: Trade = trade()) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <ShipmentPanel trade={value} />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

// 화살표 함수로 바로 돌려주면 mock 함수 자체가 반환값이 되고, vitest 는 그것을
// teardown 으로 보고 매 테스트 뒤에 호출한다 — 다음 테스트에 호출 1건이 남는다.
beforeEach(() => {
  api.shipTrade.mockResolvedValue(SHIPPED)
  api.cancelTrade.mockResolvedValue(trade({ status: 'CANCELED' }))
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('ShipmentPanel', () => {
  it('결제 전에는 발송할 수 없고 이유를 알린다', () => {
    renderPanel(trade({ status: 'CREATED', paidAt: null }))

    expect(screen.queryByRole('button', { name: '발송 처리' })).not.toBeInTheDocument()
    expect(screen.getByText(/결제가 완료되면 발송 처리를 할 수 있습니다/)).toBeInTheDocument()
  })

  // 서버도 같은 조합을 거절한다(TRADE_CARRIER_REQUIRED). 여기서 막는 이유는
  // 제출 후에야 알게 되면 사용자가 헛수고하기 때문이다.
  it('택배사 없이 운송장만 입력하면 막는다', async () => {
    const user = renderPanel()

    await user.type(screen.getByLabelText('운송장 번호'), '123456789012')
    await user.click(screen.getByRole('button', { name: '발송 처리' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('택배사도 입력')
    expect(api.shipTrade).not.toHaveBeenCalled()
  })

  // 이 테스트가 이 파일의 존재 이유다 — 발송은 되돌릴 수 없다.
  it('확인 단계를 거친 뒤에만 발송 요청을 보낸다', async () => {
    const user = renderPanel()

    await user.type(screen.getByLabelText('택배사'), 'CJ대한통운')
    await user.type(screen.getByLabelText('운송장 번호'), '123456789012')
    await user.click(screen.getByRole('button', { name: '발송 처리' }))

    expect(api.shipTrade).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '발송 확정' }))

    expect(api.shipTrade).toHaveBeenCalledWith(7, {
      carrier: 'CJ대한통운',
      trackingNumber: '123456789012',
    })
    // 발송 후에는 입력 폼이 사라지고, 방금 입력한 송장이 결과로 남는다.
    expect(await screen.findByText('CJ대한통운 · 123456789012')).toBeInTheDocument()
    expect(screen.queryByLabelText('택배사')).not.toBeInTheDocument()
  })

  it('확인 모달을 닫으면 발송하지 않는다', async () => {
    const user = renderPanel()

    await user.click(screen.getByRole('button', { name: '발송 처리' }))
    await user.click(screen.getByRole('button', { name: '취소' }))

    expect(api.shipTrade).not.toHaveBeenCalled()
    expect(screen.getByLabelText('택배사')).toBeInTheDocument()
  })

  it('송장 없이도 발송할 수 있다', async () => {
    const user = renderPanel()

    await user.click(screen.getByRole('button', { name: '발송 처리' }))
    // 직접 전달이라 송장이 없는 경우다. 확인 문구가 그 사실을 말해야 한다.
    expect(screen.getByText(/송장 없이 전달 으로 발송 처리합니다/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '발송 확정' }))

    expect(api.shipTrade).toHaveBeenCalledWith(7, { carrier: '', trackingNumber: '' })
  })

  it('이미 발송된 거래는 폼 대신 송장과 발송 시각을 보여준다', () => {
    renderPanel(
      trade({
        status: 'SHIPPED',
        shippedAt: '2026-09-03T09:00:00',
        shipping: {
          receiverName: '브랜드 B 자재팀',
          receiverPhone: '010-0000-0000',
          zipCode: '06236',
          address: '서울 강남구 테헤란로 1',
          addressDetail: '3층',
          carrier: 'CJ대한통운',
          trackingNumber: '123456789012',
          shippedAt: '2026-09-03T09:00:00',
          deliveredAt: null,
        },
      }),
    )

    expect(screen.queryByRole('button', { name: '발송 처리' })).not.toBeInTheDocument()
    expect(screen.getByText('CJ대한통운 · 123456789012')).toBeInTheDocument()
    expect(screen.getByText('서울 강남구 테헤란로 1', { exact: false })).toBeInTheDocument()
    // 발송이 끝이 아니라는 것을 알려야 한다 — 소유권은 수령 확인 시점에 넘어간다.
    expect(screen.getByText(/구매자의 수령 확인을 기다리는 중/)).toBeInTheDocument()
  })

  describe('거래 취소', () => {
    it('발송 전에는 취소할 수 있다', async () => {
      const user = renderPanel()

      await user.click(screen.getByRole('button', { name: '거래 취소' }))

      // 확인 단계 없이 요청이 나가면 안 된다 — 취소는 되돌릴 수 없다.
      expect(api.cancelTrade).not.toHaveBeenCalled()
      expect(screen.getByLabelText(/취소 사유/)).toBeInTheDocument()
    })

    // 사유를 강제하면 급한 취소가 늦어지고 의미 없는 문자열이 쌓인다.
    it('사유를 적지 않아도 취소된다', async () => {
      const user = renderPanel()

      await user.click(screen.getByRole('button', { name: '거래 취소' }))
      // 모달 안의 확인 버튼. 여는 버튼과 이름이 같아 마지막 것을 고른다.
      const [, confirm] = screen.getAllByRole('button', { name: '거래 취소' })
      await user.click(confirm)

      expect(api.cancelTrade).toHaveBeenCalledWith(7, '')
    })

    it('적은 사유를 함께 보낸다', async () => {
      const user = renderPanel()

      await user.click(screen.getByRole('button', { name: '거래 취소' }))
      await user.type(screen.getByLabelText(/취소 사유/), '재고 부족')
      const [, confirm] = screen.getAllByRole('button', { name: '거래 취소' })
      await user.click(confirm)

      expect(api.cancelTrade).toHaveBeenCalledWith(7, '재고 부족')
    })

    // 서버도 255자로 거절한다(VALIDATION_ERROR). 제출 후에야 알게 되면 적은 글이 날아간다.
    it('사유가 255자를 넘으면 막는다', async () => {
      const user = renderPanel()

      await user.click(screen.getByRole('button', { name: '거래 취소' }))
      // type() 이 아니라 paste() 다. 256자를 한 자씩 입력하면 키 입력마다 리렌더가 일어나
      // 2.7초가 걸리고, 전체 스위트를 돌릴 때 5초 제한을 넘어 간헐적으로 실패했다.
      // 검증 대상은 "길이가 넘으면 막는다" 이고 붙여넣기도 같은 경로를 지난다.
      await user.click(screen.getByLabelText(/취소 사유/))
      await user.paste('x'.repeat(256))

      expect(screen.getByRole('alert')).toHaveTextContent('255자를 넘을 수 없습니다')
      const [, confirm] = screen.getAllByRole('button', { name: '거래 취소' })
      expect(confirm).toBeDisabled()
      expect(api.cancelTrade).not.toHaveBeenCalled()
    })

    it('결제된 거래는 환불이 함께 일어난다는 것을 알린다', async () => {
      const user = renderPanel()

      await user.click(screen.getByRole('button', { name: '거래 취소' }))

      expect(screen.getByText(/환불이 함께 요청됩니다/)).toBeInTheDocument()
    })

    // 발송한 뒤에는 서버가 PAYMENT_STATE_INVALID 로 거절한다. 버튼이 남아 있으면
    // 판매자는 취소가 가능하다고 읽는다.
    it('발송된 거래는 취소 버튼이 없다', () => {
      renderPanel(trade({ status: 'SHIPPED', shippedAt: '2026-09-03T09:00:00' }))

      expect(screen.queryByRole('button', { name: '거래 취소' })).not.toBeInTheDocument()
    })

    it('취소된 거래는 시각과 사유를 보여준다', () => {
      renderPanel(
        trade({
          status: 'CANCELED',
          canceledAt: '2026-09-02T15:00:00',
          cancelReason: '재고 부족으로 발송이 어렵습니다',
        }),
      )

      expect(screen.getByText('재고 부족으로 발송이 어렵습니다')).toBeInTheDocument()
      // 되돌린 수량은 판매글에 남아 있다 — 잠금이 풀린 것이 아니다.
      expect(screen.getByText(/판매글에 다시 올라가 있습니다/)).toBeInTheDocument()
    })

    it('사유 없이 취소된 거래는 사유 자리를 만들지 않는다', () => {
      renderPanel(trade({ status: 'CANCELED', canceledAt: '2026-09-02T15:00:00' }))

      expect(screen.queryByText('취소 사유')).not.toBeInTheDocument()
    })
  })

  // 배송지가 없는 것은 판매자가 해결할 수 없는 서버 쪽 문제다. 서버 문구를 그대로
  // 보여주면 판매자가 자기 입력을 고치려 든다.
  it('배송지 정보가 없다는 거절은 판매자가 할 수 있는 일로 안내한다', async () => {
    api.shipTrade.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        code: 'TRADE_SHIPPING_NOT_FOUND',
        message: '배송지 정보가 없는 거래입니다.',
        status: 404,
      }),
    )
    const user = renderPanel()

    await user.click(screen.getByRole('button', { name: '발송 처리' }))
    await user.click(screen.getByRole('button', { name: '발송 확정' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('운영자에게 알려 주세요')
  })
})
