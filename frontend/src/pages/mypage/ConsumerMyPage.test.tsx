import { cleanup, render, screen, within } from '@testing-library/react'
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
      orderId: 1,
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
      orderId: 2,
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
    // 계정 이름과 일부러 다르게 둔다 — 받는 사람이 본인이 아닐 수 있고, 화면이 두 값을
    // 섞어 쓰고 있지 않은지가 그래야 드러난다.
    recipientName: '김수령',
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
  // 배송지만 덩그러니 있으면 이 화면이 무엇을 보여주는 곳인지 읽히지 않는다.
  it('이름·이메일·연락처·기본 배송지를 프로필로 묶어 보여준다', () => {
    renderView({ status: 'ready', data })

    expect(screen.getByRole('heading', { name: '새로이 고객님, 반갑습니다.' })).toBeInTheDocument()
    const profile = screen.getByRole('heading', { name: '내 프로필' }).closest('section')
    expect(profile).not.toBeNull()

    const card = within(profile as HTMLElement)
    expect(card.getByText('새로이 고객')).toBeInTheDocument()
    expect(card.getByText('김수령')).toBeInTheDocument()
    expect(card.getByText('consumer@saeroi.test')).toBeInTheDocument()
    expect(card.getByText('010-1234-5678')).toBeInTheDocument()
    expect(card.getByText('서울특별시 강남구 테헤란로 212', { exact: false })).toBeInTheDocument()
    expect(card.getByRole('link', { name: /배송지 편집/ })).toHaveAttribute(
      'href',
      '/mypage/addresses',
    )
  })

  // 주소를 받기 전에 가입한 계정이다. 등록으로 데려간다.
  it('배송지가 없으면 등록으로 데려간다', () => {
    renderView({ status: 'ready', data: { ...data, defaultAddress: null } })

    expect(screen.getByText('주문한 제품을 받을 곳을 등록해 주세요.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /배송지 등록/ })).toHaveAttribute(
      'href',
      '/mypage/addresses',
    )
  })

  // 주문이 늘어날수록 내 정보와 배송지처럼 짧고 자주 보는 것이 목록 아래로 밀려 내려간다.
  it('주문 목록과 주문 현황을 홈에 두지 않는다', () => {
    renderView({ status: 'ready', data })

    expect(screen.queryByRole('article', { name: /주문$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '주문 현황' })).not.toBeInTheDocument()
    // 사이드바로 간다. 홈에서 따로 안내할 자리를 만들지 않는다.
    expect(screen.getByRole('link', { name: '주문 내역' })).toHaveAttribute(
      'href',
      '/mypage/orders',
    )
  })

  // 사이드바 첫 항목이 이 화면을 가리킨다. "마이페이지 홈" 은 무엇이 있는지 말하지 않는다.
  it('사이드바에서 이 화면을 내 프로필이라고 부른다', () => {
    renderView({ status: 'ready', data })

    expect(screen.getByRole('link', { name: '내 프로필' })).toHaveAttribute('href', '/mypage')
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
    expect(screen.getByRole('heading', { name: '내 프로필' })).toBeInTheDocument()
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
