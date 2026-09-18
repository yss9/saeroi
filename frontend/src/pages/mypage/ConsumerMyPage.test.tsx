import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConsumerMyPageView } from '../../features/user/components/ConsumerMyPageView.tsx'
import type { ConsumerMyPageData } from '../../features/user/types/consumerMyPage.types.ts'

vi.mock('../landing/components/Header.tsx', () => ({ Header: () => <header /> }))
vi.mock('../landing/components/Footer.tsx', () => ({ Footer: () => <footer /> }))

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
      carrier: 'CJ대한통운',
      trackingNumber: '123456789012',
      receiverName: '새로이 고객',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 123',
      addressDetail: null,
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
      carrier: null,
      trackingNumber: null,
      receiverName: '새로이 고객',
      zipCode: '06236',
      address: '서울특별시 강남구 테헤란로 123',
      addressDetail: null,
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
  // 비밀번호 확인 없이 바로 보이는 화면이라 값은 가려서 보여준다(2026-09-18) —
  // 전체 값은 /mypage/profile 에서 비밀번호를 확인한 뒤에만 보인다.
  it('이름·이메일·연락처·기본 배송지를 가린 채 프로필로 묶어 보여준다', () => {
    renderView({ status: 'ready', data })

    const profile = screen.getByRole('heading', { name: '내 프로필' }).closest('section')
    expect(profile).not.toBeNull()

    const card = within(profile as HTMLElement)
    expect(card.getByText('새****객')).toBeInTheDocument()
    expect(card.getByText('김*령')).toBeInTheDocument()
    expect(card.getByText('co******@saeroi.test')).toBeInTheDocument()
    expect(card.getByText('010-****-5678')).toBeInTheDocument()
    expect(card.getByText('서울특별시 강남구 ********', { exact: false })).toBeInTheDocument()

    // 원본 값은 화면에 그대로 노출되지 않아야 한다.
    expect(card.queryByText('새로이 고객')).not.toBeInTheDocument()
    expect(card.queryByText('consumer@saeroi.test')).not.toBeInTheDocument()
    expect(card.queryByText('010-1234-5678')).not.toBeInTheDocument()
  })

  // 주소를 받기 전에 가입한 계정이다. 배송지 등록 안내 문구만 보여준다 — 배송지 관리는
  // 사이드바로 간다(2026-09-17, 이 카드에서 바로 가는 링크는 없앴다).
  it('배송지가 없으면 등록 안내 문구를 보여준다', () => {
    renderView({ status: 'ready', data: { ...data, defaultAddress: null } })

    expect(screen.getByText('배송지를 등록해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('등록된 연락처가 없습니다')).toBeInTheDocument()
  })

  // 주문이 늘어날수록 내 정보와 배송지처럼 짧고 자주 보는 것이 목록 아래로 밀려 내려간다.
  it('주문 목록과 주문 현황을 홈에 두지 않는다', () => {
    renderView({ status: 'ready', data })

    expect(screen.queryByRole('article', { name: /주문$/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '주문 현황' })).not.toBeInTheDocument()
    // 사이드바로 간다. 홈에서 따로 안내할 자리를 만들지 않는다.
    expect(screen.getByRole('link', { name: '주문목록/배송조회' })).toHaveAttribute(
      'href',
      '/mypage/orders',
    )
  })

  // 사이드바는 My쇼핑/My활동/My정보 세 분류다(2026-09-16 개편) — 홈(이 화면)은 목록에
  // 없다. breadcrumb 의 "마이페이지" 링크(다른 계정 화면에서)나 로고로 돌아온다.
  it('프로필 카드의 제목은 여전히 내 프로필이다', () => {
    renderView({ status: 'ready', data })

    expect(screen.getByRole('heading', { name: '내 프로필' })).toBeInTheDocument()
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

  it('uses the storefront header and footer', () => {
    renderView({ status: 'ready', data })
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
  })
})
