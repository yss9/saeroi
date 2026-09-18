// 구매하기.
//
// **누르면 사이트를 떠난다.** 주문을 만든 뒤 카카오페이 결제창으로 이동한다. 그래서 이
// 컴포넌트가 지는 책임은 "떠나기 전" 까지다.
//
//   1) **받을 곳을 먼저 보여준다.** 주문하고 나서야 옛 주소였다는 것을 알게 되는 것이
//      가장 나쁘다.
//   2) **기본 배송지가 없으면 누르지 못한다.** 서버가 받을 곳을 요구한다.
//   3) **못 사는 이유를 적는다.** 버튼만 잠그면 사용자는 자기가 뭘 잘못했다고 생각한다.
//   4) **주문을 만든 뒤 반드시 결제 준비까지 간다.** 주문만 만들고 멈추면 개체가 묶인 채
//      남고, 제품 개체는 한 점씩이라 그 제품이 즉시 품절로 보인다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createAppError, ERROR_KIND } from '../../../utils/errors.ts'
import type { ConsumerAddress } from '../types/consumerAddress.types.ts'
import { ProductPurchasePanel } from './ProductPurchasePanel.tsx'

const api = vi.hoisted(() => ({
  getConsumerAddresses: vi.fn(),
  createProductOrder: vi.fn(),
  readyProductOrderPayment: vi.fn(),
  approveProductOrderPayment: vi.fn(),
  cancelProductOrder: vi.fn(),
}))
vi.mock('../api/consumerAddressApi.ts', () => ({
  getConsumerAddresses: api.getConsumerAddresses,
}))
vi.mock('../api/createProductOrder.ts', () => ({ createProductOrder: api.createProductOrder }))
vi.mock('../api/productOrderPaymentApi.ts', () => ({
  readyProductOrderPayment: api.readyProductOrderPayment,
  approveProductOrderPayment: api.approveProductOrderPayment,
  cancelProductOrder: api.cancelProductOrder,
}))

// 결제창으로 떠나는 것은 브라우저가 하는 일이다. 여기서 볼 것은 "어디로 보냈는가" 다.
const location = vi.hoisted(() => ({ href: '' }))
vi.stubGlobal('location', location)

function address(overrides: Partial<ConsumerAddress> = {}): ConsumerAddress {
  return {
    addressId: 9,
    alias: '집',
    receiverName: '김수령',
    receiverPhone: '010-1234-5678',
    zipCode: '06236',
    address: '서울특별시 강남구 테헤란로 212',
    addressDetail: '101동 1004호',
    isDefault: true,
    ...overrides,
  }
}

function renderPanel(props: Partial<Parameters<typeof ProductPurchasePanel>[0]> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ProductPurchasePanel productModelId={31} purchasable {...props} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return userEvent.setup()
}

beforeEach(() => {
  location.href = ''
  api.getConsumerAddresses.mockResolvedValue([address()])
  api.createProductOrder.mockResolvedValue({ orderId: 12, orderCode: 'PO-010' })
  api.readyProductOrderPayment.mockResolvedValue({
    tid: 'T1234',
    redirectUrl: 'https://kakao.example/pay/12',
    totalPrice: 79_000,
    live: true,
  })
  api.cancelProductOrder.mockResolvedValue(undefined)
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductPurchasePanel', () => {
  it('받을 곳을 먼저 보여준다 — 숫자만 가려서', async () => {
    renderPanel()

    await screen.findByRole('button', { name: '배송지 변경' })
    expect(screen.getByText('김*령')).toBeInTheDocument()
    expect(
      screen.getByText('[06236] 서울특별시 강남구 테헤란로 *** ***동 ****호'),
    ).toBeInTheDocument()
    expect(screen.getByText('010-****-5678')).toBeInTheDocument()
  })

  // 예전에는 "배송지 변경"이 /mypage/addresses 로 보내 구매 흐름이 끊겼다. 지금은 그
  // 자리에서 골라 이어서 구매할 수 있어야 한다.
  it('배송지 변경을 누르면 화면을 옮기지 않고 다른 배송지를 고를 수 있다', async () => {
    const home = address({ addressId: 9, alias: '집', isDefault: true })
    const office = address({
      addressId: 10,
      alias: '회사',
      receiverName: '박회사',
      address: '서울특별시 서초구 서초대로 1',
      addressDetail: null,
      isDefault: false,
    })
    api.getConsumerAddresses.mockResolvedValue([home, office])
    const user = renderPanel()
    await screen.findByRole('button', { name: '배송지 변경' })

    await user.click(screen.getByRole('button', { name: '배송지 변경' }))
    await user.click(screen.getByRole('radio', { name: /박회사/ }))
    await user.click(screen.getByRole('button', { name: '선택 완료' }))

    // 선택 완료 뒤에는 평소처럼 가려서 보여준다.
    expect(screen.getByText('박*사')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '선택 완료' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    await waitFor(() => expect(api.createProductOrder).toHaveBeenCalled())
    expect(api.createProductOrder.mock.calls[0][0]).toEqual({
      productModelId: 31,
      addressId: 10,
    })
  })

  // 이 테스트가 이 파일의 존재 이유다 — 주문만 만들고 멈추면 개체가 묶인 채 남는다.
  it('구매하면 주문을 만들고 결제창으로 보낸다', async () => {
    const user = renderPanel()
    await screen.findByRole('button', { name: '배송지 변경' })

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    await waitFor(() => expect(api.createProductOrder).toHaveBeenCalled())
    expect(api.createProductOrder.mock.calls[0][0]).toEqual({
      productModelId: 31,
      addressId: 9,
    })
    await waitFor(() => expect(api.readyProductOrderPayment).toHaveBeenCalledWith(12))
    await waitFor(() => expect(location.href).toBe('https://kakao.example/pay/12'))
  })

  // 주문은 만들어졌는데 준비가 실패하면 개체가 묶인 채 남는다. 제품 개체는 한 점씩이라
  // 그 제품이 즉시 품절로 보인다.
  it('결제 준비가 실패하면 주문을 되돌린다', async () => {
    api.readyProductOrderPayment.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: 'PG 호출 실패', status: 502 }),
    )
    const user = renderPanel()
    await screen.findByRole('button', { name: '배송지 변경' })

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('PG 호출 실패')
    await waitFor(() => expect(api.cancelProductOrder).toHaveBeenCalledWith(12))
    expect(location.href).toBe('')
  })

  // 결제 키가 없는 환경에서는 결제창이 열리지 않는다. 안 알리면 "눌렀는데 아무 일도 안
  // 난다" 로 읽힌다.
  it('결제 키가 없으면 그 사실을 알리고 승인만 진행한다', async () => {
    api.readyProductOrderPayment.mockResolvedValue({
      tid: 'T1234',
      redirectUrl: '',
      totalPrice: 79_000,
      live: false,
    })
    api.approveProductOrderPayment.mockResolvedValue({ orderId: 12, orderCode: 'PO-010' })
    const user = renderPanel()
    await screen.findByRole('button', { name: '배송지 변경' })

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    expect(await screen.findByText(/결제 키가 없어 결제창이 열리지 않습니다/)).toBeInTheDocument()
    expect(location.href).toBe('')

    await user.click(screen.getByRole('button', { name: '결제 승인 (시연)' }))

    await waitFor(() => expect(api.approveProductOrderPayment).toHaveBeenCalled())
    expect(api.approveProductOrderPayment.mock.calls[0][0]).toBe(12)
  })

  it('기본 배송지가 없으면 주문할 수 없다고 알린다', async () => {
    api.getConsumerAddresses.mockResolvedValue([])
    renderPanel()

    expect(await screen.findByText(/기본 배송지가 없어 주문할 수 없습니다/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매하기' })).toBeDisabled()
  })

  // 등록만 된 제품의 재고 0 은 "다 팔렸다" 가 아니라 "아직 만들지 않았다" 다.
  it('못 사는 이유를 적는다', async () => {
    renderPanel({ purchasable: false, blockedReason: '아직 판매를 시작하지 않은 제품입니다.' })

    expect(await screen.findByText('아직 판매를 시작하지 않은 제품입니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '구매하기' })).toBeDisabled()
  })

  it('누르면 결제창으로 간다는 것을 미리 적는다', async () => {
    renderPanel()

    expect(await screen.findByText(/카카오페이 결제창으로 이동합니다/)).toBeInTheDocument()
  })

  it('주문이 실패하면 떠나지 않고 이유를 보여준다', async () => {
    api.createProductOrder.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '재고가 없습니다.', status: 409 }),
    )
    const user = renderPanel()
    await screen.findByRole('button', { name: '배송지 변경' })

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('재고가 없습니다')
    expect(location.href).toBe('')
    // 주문 자체가 안 만들어졌으므로 되돌릴 것도 없다.
    expect(api.cancelProductOrder).not.toHaveBeenCalled()
  })
})
