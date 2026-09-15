// 브랜드 주문·발송 관리.
//
// **소비자 주문이 여기서 멈춰 있었다.** 결제까지 끝난 주문을 보낼 화면이 없어 PAID 에
// 머물렀고, 그러면 소비자의 수령 확인도 열리지 않는다.
//
// 틀리면 안 되는 지점 다섯.
//
//   1) **송장에 쓰는 이름은 수령인이다.** 주문자와 다를 수 있다(선물·가족 집) — 주문자
//      이름을 적으면 물건이 엉뚱한 사람 앞으로 간다.
//   2) **개체 코드와 일련번호를 보여준다.** 같은 모델이라도 개체마다 다른 물건이라(QR 이
//      다르다) 제품 이름만 보고 집으면 다른 제품이 나간다.
//   3) **기본 탭이 발송 대기다.** 거르지 않으면 배송 완료된 주문까지 섞여 할 일이 묻힌다.
//   4) **운송장만 적고 택배사를 비울 수 없다.** 조회할 방법이 없는 조합이라 서버가 400 을
//      준다 — 송장을 다 입력하고 거절당하면 그 입력이 헛수고다.
//   5) **발송은 되돌릴 수 없다.** 그 뒤로는 소비자도 취소할 수 없다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { BrandOrder } from '../types/brandOrder.types.ts'
import { BrandOrderList } from './BrandOrderList.tsx'

const api = vi.hoisted(() => ({ getBrandOrders: vi.fn(), shipBrandOrder: vi.fn() }))
vi.mock('../api/brandOrderApi.ts', () => api)

function order(overrides: Partial<BrandOrder> = {}): BrandOrder {
  return {
    orderId: 12,
    orderCode: 'ORD-00000012',
    status: 'PAID',
    // 주문자와 수령인이 다른 주문. 선물로 보내는 경우다.
    buyerName: '홍길동',
    totalPrice: 79_000,
    orderedAt: '2026-09-15T10:00:00',
    paidAt: '2026-09-15T10:02:00',
    shippedAt: null,
    deliveredAt: null,
    items: [
      { itemCode: 'ITEM-1', serialNumber: 1, productName: '업사이클 토트백', price: 79_000 },
    ],
    shipping: {
      receiverName: '홍수령',
      receiverPhone: '010-9999-0000',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 212',
      addressDetail: '5층',
      carrier: null,
      trackingNumber: null,
      shippedAt: null,
    },
    ...overrides,
  }
}

function page(content: BrandOrder[]) {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: 1,
    hasNext: false,
  }
}

function renderList() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <BrandOrderList />
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  api.getBrandOrders.mockResolvedValue(page([order()]))
  api.shipBrandOrder.mockResolvedValue({
    orderId: 12,
    orderCode: 'ORD-00000012',
    status: 'SHIPPED',
    shippedAt: '2026-09-15T14:00:00',
    carrier: 'CJ대한통운',
    trackingNumber: '123456789012',
  })
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('BrandOrderList', () => {
  // 거르지 않으면 배송 완료된 주문까지 섞여 나와 오늘 할 일이 묻힌다.
  it('발송 대기만 먼저 보여준다', async () => {
    renderList()

    await waitFor(() => expect(api.getBrandOrders).toHaveBeenCalled())
    expect(api.getBrandOrders.mock.calls[0][0]).toMatchObject({ status: 'PAID', page: 0 })
  })

  // 이 테스트가 이 파일의 존재 이유다 — 주문자 이름을 송장에 적으면 물건이 엉뚱한 곳으로 간다.
  it('받는 사람과 주문자를 구분해 보여준다', async () => {
    renderList()

    expect(await screen.findByText('홍수령')).toBeInTheDocument()
    expect(screen.getByText('010-9999-0000')).toBeInTheDocument()
    expect(screen.getByText('주문자 홍길동')).toBeInTheDocument()
  })

  // 같은 모델이라도 개체마다 다른 물건이다.
  it('개체 코드와 일련번호를 보여준다', async () => {
    renderList()

    expect(await screen.findByText('ITEM-1')).toBeInTheDocument()
    expect(screen.getByText('#1')).toBeInTheDocument()
  })

  it('송장을 입력해 발송 처리한다', async () => {
    const user = renderList()
    await screen.findByText('홍수령')

    await user.click(screen.getByRole('button', { name: '발송 처리' }))

    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('택배사'), 'CJ대한통운')
    await user.type(within(dialog).getByLabelText('운송장 번호'), '123456789012')
    await user.click(within(dialog).getByRole('button', { name: '발송 처리' }))

    await waitFor(() => expect(api.shipBrandOrder).toHaveBeenCalled())
    expect(api.shipBrandOrder.mock.calls[0][0]).toBe(12)
    expect(api.shipBrandOrder.mock.calls[0][1]).toEqual({
      carrier: 'CJ대한통운',
      trackingNumber: '123456789012',
    })
  })

  // 직접 전달·퀵처럼 송장이 없는 방식이 있다.
  it('송장 없이도 발송할 수 있다', async () => {
    const user = renderList()
    await screen.findByText('홍수령')

    await user.click(screen.getByRole('button', { name: '발송 처리' }))
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '발송 처리' }),
    )

    // 빈 값을 서버로 보내지 않는 것은 API 계층이 한다(brandOrderApi.test). 여기서는
    // "송장이 없어도 제출된다" 까지만 본다.
    await waitFor(() => expect(api.shipBrandOrder).toHaveBeenCalled())
    expect(api.shipBrandOrder.mock.calls[0][1]).toEqual({ carrier: '', trackingNumber: '' })
  })

  // 서버가 400 으로 거절하는 조합이다. 여기서 막지 않으면 제출 뒤에야 알게 된다.
  it('택배사 없이 운송장만 적으면 보내지 않는다', async () => {
    const user = renderList()
    await screen.findByText('홍수령')

    await user.click(screen.getByRole('button', { name: '발송 처리' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('운송장 번호'), '123456789012')
    await user.click(within(dialog).getByRole('button', { name: '발송 처리' }))

    expect(await screen.findByText('택배사를 함께 입력해 주세요.')).toBeInTheDocument()
    expect(api.shipBrandOrder).not.toHaveBeenCalled()
  })

  it('발송된 주문에는 발송 버튼을 두지 않고 송장을 보여준다', async () => {
    api.getBrandOrders.mockResolvedValue(
      page([
        order({
          status: 'SHIPPED',
          shippedAt: '2026-09-15T14:00:00',
          shipping: {
            ...order().shipping,
            carrier: 'CJ대한통운',
            trackingNumber: '123456789012',
            shippedAt: '2026-09-15T14:00:00',
          },
        }),
      ]),
    )
    renderList()

    expect(await screen.findByText('CJ대한통운 123456789012')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '발송 처리' })).not.toBeInTheDocument()
    // 브랜드가 대신 완료시킬 수 없다는 것을 알린다.
    expect(screen.getByText(/소비자가 수령을 확인하면 완료됩니다/)).toBeInTheDocument()
  })

  it('보낼 주문이 없으면 그렇게 말한다', async () => {
    api.getBrandOrders.mockResolvedValue(page([]))
    renderList()

    expect(await screen.findByText('발송할 주문이 없습니다')).toBeInTheDocument()
  })

  it('발송이 실패하면 이유를 폼 안에서 보여준다', async () => {
    api.shipBrandOrder.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, {
        message: '이미 발송된 주문입니다.',
        code: 'PRODUCT_ORDER_ALREADY_SHIPPED',
        status: 409,
      }),
    )
    const user = renderList()
    await screen.findByText('홍수령')

    await user.click(screen.getByRole('button', { name: '발송 처리' }))
    await user.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '발송 처리' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 발송된 주문입니다')
    // 모달을 닫지 않는다 — 닫으면 무엇이 실패했는지 못 본 채 목록으로 돌아간다.
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('조회가 실패하면 다시 시도할 수 있게 한다', async () => {
    api.getBrandOrders.mockRejectedValue(
      createAppError(ERROR_KIND.NETWORK_ERROR, { message: '서버에 연결할 수 없습니다.' }),
    )
    renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
