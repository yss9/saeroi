// 주문 내역과 수령 확인.
//
// **받았다고 말하는 쪽은 구매자다.** 브랜드가 발송을 처리해도 주문은 배송 중에서 멈추고,
// 소비자가 여기서 확인해야 배송 완료가 된다 (소재 거래와 같은 2-Track, 다만 서명은 없다).
//
// 틀리면 안 되는 지점 넷.
//
//   1) **배송 중일 때만 누를 수 있다.** 아직 안 떠난 주문이나 이미 끝난 주문에 버튼이
//      있으면 눌러 보고 서버 거절로 알게 된다.
//   2) **되돌릴 수 없으니 확인을 거친다.** 어느 주문인지 문장에 넣는다 — 주문에 제품이
//      여럿이면 한 줄에서 눌러도 그 주문이 통째로 완료된다.
//   3) **주문 번호로 부른다.** 목록의 한 줄은 개체라, 개체 코드로 부르면 서버가 못 찾는다.
//   4) **배송 현황에 날짜가 남는다.** 상태 배지만으로는 "언제" 를 알 수 없다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ConsumerRecentOrder } from '../types/consumerMyPage.types.ts'
import { ConsumerOrderList } from './ConsumerOrderList.tsx'

const api = vi.hoisted(() => ({
  confirmProductOrderDelivery: vi.fn(),
  cancelProductOrder: vi.fn(),
}))
vi.mock('../api/confirmProductOrderDelivery.ts', () => ({
  confirmProductOrderDelivery: api.confirmProductOrderDelivery,
}))
vi.mock('../api/productOrderPaymentApi.ts', () => ({
  cancelProductOrder: api.cancelProductOrder,
}))

vi.mock('../hooks/useConsumerOrderThumbnail.ts', () => ({
  useConsumerOrderThumbnail: () => ({ url: null }),
}))

function order(overrides: Partial<ConsumerRecentOrder> = {}): ConsumerRecentOrder {
  return {
    id: 'PO-001:ITEM-001',
    orderId: 12,
    orderNumber: 'PO-001',
    orderedAt: '2026-09-12T10:30:00',
    productName: '데님 서클 백',
    brandName: 'ReWeave',
    thumbnailUrl: '/api/v1/product-models/10/thumbnail',
    thumbnailAlt: '데님 서클 백 대표 이미지',
    itemCode: 'ITEM-001',
    itemCount: 1,
    itemPrice: 129_000,
    totalAmount: 129_000,
    status: 'SHIPPING',
    journeyUrl: 'http://localhost:5173/qr/journey-token',
    paidAt: '2026-09-12T10:35:00',
    shippedAt: '2026-09-13T09:00:00',
    deliveredAt: null,
    carrier: 'CJ대한통운',
    trackingNumber: '123456789012',
    receiverName: '김소비',
    zipCode: '06236',
    address: '서울특별시 강남구 테헤란로 123',
    addressDetail: '101동 202호',
    ...overrides,
  }
}

function renderList(orders: ConsumerRecentOrder[] = [order()]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ConsumerOrderList orders={orders} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.confirmProductOrderDelivery.mockResolvedValue({ orderId: 12, orderCode: 'PO-001' })
  api.cancelProductOrder.mockResolvedValue(undefined)
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ConsumerOrderList', () => {
  // 이 테스트가 이 파일의 존재 이유다.
  it('배송 중인 주문을 수령 확인한다', async () => {
    const user = renderList()

    await user.click(screen.getByRole('button', { name: '배송 완료 확인' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText(/PO-001 주문을 수령 완료로 표시합니다/)).toBeInTheDocument()
    expect(api.confirmProductOrderDelivery).not.toHaveBeenCalled()

    await user.click(within(dialog).getByRole('button', { name: '수령 확인' }))

    // 목록의 한 줄은 개체다. 개체 코드로 부르면 서버가 못 찾는다.
    await waitFor(() => expect(api.confirmProductOrderDelivery).toHaveBeenCalled())
    expect(api.confirmProductOrderDelivery.mock.calls[0][0]).toBe(12)
  })

  it('아직 배송 중이 아니면 수령 확인을 두지 않는다', () => {
    renderList([order({ status: 'PREPARING', shippedAt: null })])

    expect(screen.queryByRole('button', { name: '배송 완료 확인' })).not.toBeInTheDocument()
  })

  it('이미 배송 완료된 주문에는 두지 않는다', async () => {
    const user = renderList([order({ status: 'DELIVERED', deliveredAt: '2026-09-14T15:00:00' })])

    expect(screen.queryByRole('button', { name: '배송 완료 확인' })).not.toBeInTheDocument()
    // 배송 현황의 마지막 칸에 날짜가 찍힌다. ("배송 완료" 는 배지와 단계 이름 양쪽에 있어
    // 그 문구로는 무엇을 본 것인지 드러나지 않는다.)
    await user.click(screen.getByRole('button', { name: '배송조회' }))
    const progress = within(screen.getByRole('dialog')).getByRole('list', { name: '배송 현황' })
    expect(within(progress).queryByText('대기')).not.toBeInTheDocument()
  })

  it('수령 확인이 실패하면 이유를 보여준다', async () => {
    api.confirmProductOrderDelivery.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        message: '배송 중인 주문만 수령 확인할 수 있습니다.',
        status: 409,
      }),
    )
    const user = renderList()

    await user.click(screen.getByRole('button', { name: '배송 완료 확인' }))
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '수령 확인' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('배송 중인 주문만')
  })

  // 배송 단계·운송장·받는 곳은 목록에 늘어놓지 않고 "배송조회" 모달로 모았다(2026-09-16
  // 개편) — 제품이 여럿인 주문에서 같은 정보가 제품 수만큼 반복되는 것을 막는다.
  describe('배송조회', () => {
    // 상태 배지는 "지금 어디쯤" 만 말하고 "언제" 를 말하지 않는다.
    it('배송 현황에 지나온 단계를 적는다', async () => {
      const user = renderList()
      await user.click(screen.getByRole('button', { name: '배송조회' }))

      const dialog = screen.getByRole('dialog')
      const progress = within(dialog).getByRole('list', { name: '배송 현황' })
      const steps = within(progress).getAllByRole('listitem')
      expect(steps).toHaveLength(4)
      // 아직 오지 않은 단계도 지우지 않는다 — 안 보이면 지금이 마지막인지 알 수 없다.
      expect(within(progress).getByText('대기')).toBeInTheDocument()
    })

    // 이 블록의 존재 이유 — "배송 중" 배지만 남으면 어디쯤 왔는지 브랜드에 물어봐야 한다.
    it('브랜드가 적은 택배사와 운송장을 그대로 보여준다', async () => {
      const user = renderList()
      await user.click(screen.getByRole('button', { name: '배송조회' }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByText('CJ대한통운')).toBeInTheDocument()
      expect(within(dialog).getByText('123456789012')).toBeInTheDocument()
    })

    // 직접 전달·퀵처럼 송장 없이 보내는 방식이 정상 경로다. "운송장 없음" 이라고 적으면
    // 빠뜨린 것처럼 읽힌다.
    it('송장이 없으면 그 줄을 그리지 않는다', async () => {
      const user = renderList([order({ carrier: null, trackingNumber: null })])
      await user.click(screen.getByRole('button', { name: '배송조회' }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).queryByText('운송장')).not.toBeInTheDocument()
    })

    // 서버는 번호만 있는 조합을 막지만 그 반대는 허용한다.
    it('택배사만 있어도 보여준다', async () => {
      const user = renderList([order({ trackingNumber: null })])
      await user.click(screen.getByRole('button', { name: '배송조회' }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByText('CJ대한통운')).toBeInTheDocument()
    })

    // 받는 곳은 주문 시점에 확정되므로 배송 전(결제 대기)이어도 항상 보여줄 수 있다.
    it('수령인과 주소를 보여준다', async () => {
      const user = renderList([
        order({
          status: 'PAYMENT_PENDING',
          shippedAt: null,
          carrier: null,
          trackingNumber: null,
          receiverName: '김소비',
          zipCode: '06236',
          address: '서울특별시 강남구 테헤란로 123',
          addressDetail: '101동 202호',
        }),
      ])
      await user.click(screen.getByRole('button', { name: '배송조회' }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByText('김소비')).toBeInTheDocument()
      expect(within(dialog).getByText(/테헤란로 123/)).toBeInTheDocument()
      expect(within(dialog).getByText(/101동 202호/)).toBeInTheDocument()
    })
  })

  // 큰 박스(주문) 안에 작은 박스(제품)를 두는 2단 구조(2026-09-16 개편).
  describe('주문 묶기', () => {
    it('같은 주문의 제품은 한 큰 박스 안에 들어간다', () => {
      renderList([
        order({ id: 'PO-001:ITEM-001', itemCode: 'ITEM-001', productName: '데님 서클 백' }),
        order({ id: 'PO-001:ITEM-002', itemCode: 'ITEM-002', productName: '업사이클 파우치' }),
      ])

      const group = screen.getByRole('article', { name: 'PO-001 주문' })
      expect(within(group).getByText('데님 서클 백')).toBeInTheDocument()
      expect(within(group).getByText('업사이클 파우치')).toBeInTheDocument()
      // 주문 단위 동작이라 제품이 둘이어도 버튼은 큰 박스에 하나만 있다.
      expect(within(group).getAllByRole('button', { name: '배송 완료 확인' })).toHaveLength(1)
    })

    it('"주문 상세보기" 는 이 주문의 상세 페이지로 간다', () => {
      renderList()

      expect(screen.getByRole('link', { name: /주문 상세보기/ })).toHaveAttribute(
        'href',
        '/mypage/orders/12',
      )
    })
  })

  describe('주문 취소', () => {
    // 이 테스트가 이 블록의 존재 이유다 — 무엇이 없어지는지 미리 말해야 한다.
    it('결제한 주문을 취소하면 환불과 목록에서 사라짐을 미리 알린다', async () => {
      const user = renderList([order({ status: 'PREPARING', shippedAt: null })])

      await user.click(screen.getByRole('button', { name: '주문 취소' }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByText(/결제하신 금액은 환불됩니다/)).toBeInTheDocument()
      expect(within(dialog).getByText(/목록에서 사라지고/)).toBeInTheDocument()
      expect(api.cancelProductOrder).not.toHaveBeenCalled()

      await user.click(within(dialog).getByRole('button', { name: '주문 취소' }))

      await waitFor(() => expect(api.cancelProductOrder).toHaveBeenCalled())
      expect(api.cancelProductOrder.mock.calls[0][0]).toBe(12)
    })

    // 아직 결제 전이라 돌려줄 돈이 없다. 없는 환불을 말하면 사용자가 기다린다.
    it('결제 전 주문에는 환불을 말하지 않는다', async () => {
      const user = renderList([order({ status: 'PAYMENT_PENDING', paidAt: null, shippedAt: null })])

      await user.click(screen.getByRole('button', { name: '주문 취소' }))

      const dialog = screen.getByRole('dialog')
      expect(within(dialog).queryByText(/환불/)).not.toBeInTheDocument()
    })

    // 물건이 나갔는데 주문만 없애면 그 개체가 어디에도 속하지 않게 된다.
    it('발송된 뒤에는 취소를 두지 않는다', () => {
      renderList([order({ status: 'SHIPPING' })])

      expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: '배송 완료 확인' })).toBeInTheDocument()
    })

    it('배송 완료된 주문에도 두지 않는다', () => {
      renderList([order({ status: 'DELIVERED', deliveredAt: '2026-09-14T15:00:00' })])

      expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument()
    })

    it('취소가 실패하면 이유를 보여준다', async () => {
      api.cancelProductOrder.mockRejectedValue(
        createAppError(ERROR_KIND.API_ERROR, {
          message: '발송된 주문은 취소할 수 없습니다.',
          status: 409,
        }),
      )
      const user = renderList([order({ status: 'PREPARING', shippedAt: null })])

      await user.click(screen.getByRole('button', { name: '주문 취소' }))
      await user.click(
        within(screen.getByRole('dialog')).getByRole('button', { name: '주문 취소' }),
      )

      expect(await screen.findByRole('alert')).toHaveTextContent('발송된 주문은 취소할 수 없습니다')
    })
  })

  it('주문이 없으면 둘러보기로 데려간다', () => {
    renderList([])

    expect(screen.getByText('주문 내역이 없습니다')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '상품 둘러보기' })).toHaveAttribute('href', '/shop')
  })
})
