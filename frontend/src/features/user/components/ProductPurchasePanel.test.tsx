// 구매하기.
//
// **결제 단계가 없다.** 결제 수단이 정해지지 않아 주문만 만들고 마이페이지에서 확인하게
// 한다. 그래서 틀리면 안 되는 지점이 보통의 결제 화면과 다르다.
//
//   1) **받을 곳을 먼저 보여준다.** 주문하고 나서야 옛 주소였다는 것을 알게 되는 것이
//      가장 나쁘다.
//   2) **기본 배송지가 없으면 누르지 못한다.** 서버가 받을 곳을 요구한다.
//   3) **못 사는 이유를 적는다.** 버튼만 잠그면 사용자는 자기가 뭘 잘못했다고 생각한다.
//   4) **성공하면 주문 내역으로 보낸다.** 결제창이 뜨지 않아, 같은 화면에 남으면 눌렀는데
//      아무 일도 없는 것처럼 보인다.

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
}))
vi.mock('../api/consumerAddressApi.ts', () => ({
  getConsumerAddresses: api.getConsumerAddresses,
}))
vi.mock('../api/createProductOrder.ts', () => ({ createProductOrder: api.createProductOrder }))

const navigate = vi.hoisted(() => vi.fn())
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => navigate }
})

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
  api.getConsumerAddresses.mockResolvedValue([address()])
  api.createProductOrder.mockResolvedValue({ orderCode: 'PO-010' })
})
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  vi.resetAllMocks()
})

describe('ProductPurchasePanel', () => {
  it('받을 곳을 먼저 보여준다', async () => {
    renderPanel()

    expect(await screen.findByText('김수령')).toBeInTheDocument()
    expect(
      screen.getByText('[06236] 서울특별시 강남구 테헤란로 212 101동 1004호'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '배송지 변경' })).toHaveAttribute(
      'href',
      '/mypage/addresses',
    )
  })

  // 이 테스트가 이 파일의 존재 이유다 — 결제창이 뜨지 않아, 같은 화면에 남으면 눌렀는데
  // 아무 일도 없는 것처럼 보인다.
  it('구매하면 기본 배송지로 주문을 만들고 주문 내역으로 보낸다', async () => {
    const user = renderPanel()
    await screen.findByText('김수령')

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    await waitFor(() => expect(api.createProductOrder).toHaveBeenCalled())
    expect(api.createProductOrder.mock.calls[0][0]).toEqual({
      productModelId: 31,
      addressId: 9,
    })
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/mypage/orders'))
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

  it('결제가 아직 없다는 것을 적는다', async () => {
    renderPanel()

    expect(await screen.findByText(/결제 단계는 아직 준비 중입니다/)).toBeInTheDocument()
  })

  it('주문이 실패하면 이동하지 않고 이유를 보여준다', async () => {
    api.createProductOrder.mockRejectedValue(
      createAppError(ERROR_KIND.API_ERROR, { message: '재고가 없습니다.', status: 409 }),
    )
    const user = renderPanel()
    await screen.findByText('김수령')

    await user.click(screen.getByRole('button', { name: '구매하기' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('재고가 없습니다')
    expect(navigate).not.toHaveBeenCalled()
  })
})
