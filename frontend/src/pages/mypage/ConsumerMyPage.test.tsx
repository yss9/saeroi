import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import type { ConsumerMyPageData } from '../../features/user/types/consumerMyPage.types.ts'

vi.mock('../../features/user/hooks/useConsumerOrderThumbnail.ts', () => ({
  useConsumerOrderThumbnail: () => ({ url: null }),
}))

const profile = {
  id: 5,
  name: '새로이 고객',
  email: 'consumer@saeroi.test',
}

const data: ConsumerMyPageData = {
  profile,
  orderStages: [
    { stage: 'PAYMENT_PENDING', count: 1 },
    { stage: 'PREPARING', count: 2 },
    { stage: 'SHIPPING', count: 1 },
    { stage: 'DELIVERED', count: 3 },
  ],
  recentOrders: [
    {
      id: 'PO-001:ITEM-001',
      orderNumber: 'PO-001',
      orderedAt: '2026-09-12T10:30:00',
      productName: '데님 서클 백',
      brandName: 'ReWeave',
      thumbnailUrl: '/api/v1/product-models/10/thumbnail',
      thumbnailAlt: '데님 서클 백 대표 이미지',
      itemCode: 'ITEM-001',
      itemCount: 1,
      itemPrice: 129000,
      totalAmount: 129000,
      status: 'DELIVERED',
      journeyUrl: 'http://localhost:5173/qr/journey-token',
      paidAt: '2026-09-12T10:35:00',
      shippedAt: '2026-09-13T09:00:00',
      deliveredAt: '2026-09-14T15:00:00',
    },
    {
      id: 'PO-002:ITEM-002',
      orderNumber: 'PO-002',
      orderedAt: '2026-09-13T11:30:00',
      productName: '업사이클 파우치',
      brandName: 'ReWeave',
      thumbnailUrl: '/api/v1/product-models/11/thumbnail',
      thumbnailAlt: '업사이클 파우치 대표 이미지',
      itemCode: 'ITEM-002',
      itemCount: 1,
      itemPrice: 49000,
      totalAmount: 49000,
      status: 'PAYMENT_PENDING',
      journeyUrl: null,
      paidAt: null,
      shippedAt: null,
      deliveredAt: null,
    },
  ],
  defaultAddress: {
    id: 1,
    recipientName: '새로이 고객',
    phone: '010-1234-5678',
    postalCode: '06220',
    addressLine1: '서울특별시 강남구 테헤란로 212',
    isDefault: true,
  },
}

function renderView(
  state: Parameters<typeof ConsumerMyPageView>[0]['state'],
  onSignOut?: () => void,
) {
  return render(
    <MemoryRouter>
      <ConsumerMyPageView state={state} onSignOut={onSignOut} />
    </MemoryRouter>,
  )
}

afterEach(cleanup)

describe('ConsumerMyPageView', () => {
  it('내 정보와 기본 배송지를 보여준다', () => {
    renderView({ status: 'ready', data })

    expect(screen.getByRole('heading', { name: '새로이 고객님, 반갑습니다.' })).toBeInTheDocument()
    expect(screen.getByText('서울특별시 강남구 테헤란로 212', { exact: false })).toBeInTheDocument()
    // 편집이 배송지 관리로 데려간다.
    expect(screen.getByRole('link', { name: /편집/ })).toHaveAttribute('href', '/mypage/addresses')
  })

  // 홈에 주문을 통째로 쌓아 두면 주문이 늘어날수록 배송지가 목록 아래로 밀려 내려간다.
  it('주문 목록은 홈에 두지 않고 주문 내역으로 보낸다', () => {
    renderView({ status: 'ready', data })

    expect(screen.queryByRole('article', { name: /주문$/ })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /전체 주문 보기/ })).toHaveAttribute(
      'href',
      '/mypage/orders',
    )
  })

  // 이 테스트가 이 파일의 존재 이유다 — 0 이 네 칸 늘어선 표는 처음 온 사람에게
  // 무엇을 세는 것인지조차 알려주지 않는다.
  it('주문이 한 건도 없으면 주문 현황을 그리지 않는다', () => {
    renderView({
      status: 'ready',
      data: {
        ...data,
        orderStages: data.orderStages.map((stage) => ({ ...stage, count: 0 })),
      },
    })

    expect(screen.queryByRole('heading', { name: '주문 현황' })).not.toBeInTheDocument()
    // 프로필과 배송지는 그대로 있다.
    expect(screen.getByRole('heading', { name: '기본 배송지' })).toBeInTheDocument()
  })

  it('주문이 있으면 현황을 바로 보여준다', () => {
    renderView({ status: 'ready', data })

    expect(screen.getByRole('heading', { name: '주문 현황' })).toBeInTheDocument()
  })

  it('renders loading, error, and empty states separately', () => {
    const { rerender } = renderView({ status: 'loading' })
    expect(screen.getByRole('status', { name: '마이페이지 정보를 불러오는 중' })).toBeInTheDocument()

    rerender(
      <MemoryRouter>
        <ConsumerMyPageView state={{ status: 'error', error: new Error('조회 실패') }} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('조회 실패')

    rerender(
      <MemoryRouter>
        <ConsumerMyPageView
          state={{
            status: 'ready',
            data: { ...data, recentOrders: [], defaultAddress: null },
          }}
        />
      </MemoryRouter>,
    )
    expect(screen.getByText('등록된 배송지가 없습니다')).toBeInTheDocument()
  })

  it('calls the sign-out action from the account summary', async () => {
    const user = userEvent.setup()
    const onSignOut = vi.fn()
    renderView({ status: 'ready', data }, onSignOut)

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(onSignOut).toHaveBeenCalledOnce()
  })

  it('shows pending and failed sign-out states', () => {
    const { rerender } = render(
      <MemoryRouter>
        <ConsumerMyPageView
          state={{ status: 'ready', data }}
          onSignOut={() => undefined}
          signOutPending
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('button', { name: '로그아웃 중…' })).toBeDisabled()

    rerender(
      <MemoryRouter>
        <ConsumerMyPageView
          state={{ status: 'ready', data }}
          onSignOut={() => undefined}
          signOutError="로그아웃에 실패했습니다."
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('alert')).toHaveTextContent('로그아웃에 실패했습니다.')
  })
})
