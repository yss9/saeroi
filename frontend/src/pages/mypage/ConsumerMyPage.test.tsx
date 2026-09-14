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
  it('renders real order summaries and each product journey action', () => {
    renderView({ status: 'ready', data })

    expect(screen.getByRole('heading', { name: '새로이 고객님, 반갑습니다.' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '주문 현황' })).toBeInTheDocument()
    expect(screen.getAllByRole('article', { name: /주문$/ })).toHaveLength(2)
    expect(screen.getByText('ITEM-001')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '제품 여정 보기' })).toHaveAttribute(
      'href',
      'http://localhost:5173/qr/journey-token',
    )
    expect(screen.getByText('결제 완료 후 제품 여정 확인 가능')).toBeInTheDocument()
    expect(screen.getByText('서울특별시 강남구 테헤란로 212', { exact: false })).toBeInTheDocument()
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
    expect(screen.getByText('최근 주문이 없습니다')).toBeInTheDocument()
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
