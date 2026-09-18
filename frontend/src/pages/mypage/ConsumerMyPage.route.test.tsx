import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, useRoutes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { routes } from '../../app/router/routes.tsx'
import { apiClient } from '../../services/apiClient.ts'
import { queryClient } from '../../services/queryClient.ts'
import { useAuthStore } from '../../stores/authStore.ts'

vi.mock('../../features/user/hooks/useConsumerOrderThumbnail.ts', () => ({
  useConsumerOrderThumbnail: () => ({ url: null }),
}))

const orderPage = {
  content: [
    {
      orderId: 1,
      orderCode: 'PO-001',
      brandName: 'ReWeave',
      totalPrice: 129000,
      status: 'DELIVERED',
      orderedAt: '2026-09-12T10:30:00',
      paidAt: '2026-09-12T10:35:00',
      shippedAt: '2026-09-13T09:00:00',
      deliveredAt: '2026-09-14T15:00:00',
      items: [
        {
          itemCode: 'ITEM-001',
          serialNumber: 1,
          productName: '데님 서클 백',
          category: 'BAG',
          price: 129000,
          imageUrl: '/api/v1/product-models/10/thumbnail',
          journeyUrl: 'http://localhost:5173/qr/journey-token',
        },
      ],
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  hasNext: false,
}

/**
 * 이 화면은 요청 셋으로 그려진다 — 주문 목록·주문 요약·배송지 목록.
 *
 * URL 로 갈라 각각 다른 모양을 돌려준다. 하나로 뭉뚱그리면 배송지 자리에 주문 페이지가
 * 실려 `addresses.find is not a function` 으로 화면이 통째로 죽는다.
 */
function mockMyPageApi() {
  return vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
    if (url === '/v1/product-orders/me/summary') {
      return { data: { created: 0, paid: 0, shipped: 0, delivered: 1 } } as AxiosResponse
    }
    // 배송지가 0건인 계정. 주소를 받기 전에 가입한 사람이고, 빈 배열이 정상이다.
    if (url === '/v1/consumers/me/addresses') {
      return { data: [] } as AxiosResponse
    }
    return { data: orderPage } as AxiosResponse
  })
}

function AppRoutes() {
  return useRoutes(routes)
}

function renderRoute(path: string) {
  return render(
    <AppProviders>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AppProviders>,
  )
}

beforeEach(() => {
  localStorage.clear()
  queryClient.clear()
  useAuthStore.setState({
    user: null,
    accessToken: null,
    isAuthenticated: false,
  })
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  queryClient.clear()
  useAuthStore.setState({
    user: null,
    accessToken: null,
    isAuthenticated: false,
  })
  vi.restoreAllMocks()
})

describe('consumer mypage route', () => {
  /** 소비자로 로그인시킨다. 여러 테스트가 쓴다. */
  function signInAsConsumer() {
    useAuthStore.setState({
      user: {
        id: 5,
        name: '새로이 고객',
        email: 'consumer@saeroi.test',
        role: 'CONSUMER',
        walletAddress: null,
      },
      accessToken: 'test-token',
      isAuthenticated: true,
    })
  }

  it('loads authenticated consumer orders and exposes the product journey', async () => {
    const get = mockMyPageApi()
    useAuthStore.setState({
      user: {
        id: 5,
        name: '새로이 고객',
        email: 'consumer@saeroi.test',
        role: 'CONSUMER',
        walletAddress: null,
      },
      accessToken: 'test-token',
      isAuthenticated: true,
    })

    renderRoute('/mypage')

    expect(await screen.findByRole('heading', { name: '내 프로필' })).toBeInTheDocument()
    // 주문 목록도 주문 현황도 홈이 아니라 /mypage/orders 에 있다. 홈은 프로필만 본다.
    expect(screen.queryByRole('heading', { name: '데님 서클 백' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '주문 현황' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '내 프로필' })).toBeInTheDocument()
    expect(get).not.toHaveBeenCalledWith('/v1/product-orders/me', expect.anything())
    expect(get).not.toHaveBeenCalledWith('/v1/product-orders/me/summary')
  })

  // 주문 내역은 별도 화면이다. 배송 현황·제품 여정은 "배송조회" 모달로 옮겼다(2026-09-16).
  it('주문 내역에서 주문과 배송 현황, 제품 여정을 보여준다', async () => {
    mockMyPageApi()
    signInAsConsumer()
    const user = userEvent.setup()

    renderRoute('/mypage/orders')

    expect(await screen.findByRole('heading', { name: '데님 서클 백' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '제품 여정 확인' })).toHaveAttribute(
      'href',
      'http://localhost:5173/qr/journey-token',
    )
    // 상태 배지만으로는 "언제" 를 알 수 없다. 지나온 단계에 날짜가 적힌다.
    await user.click(screen.getByRole('button', { name: '배송조회' }))
    const progress = within(screen.getByRole('dialog')).getByRole('list', { name: '배송 현황' })
    expect(within(progress).getAllByRole('listitem')).toHaveLength(4)
  })

  it('사이드바로 주문 내역과 배송지 관리를 오간다', async () => {
    mockMyPageApi()
    signInAsConsumer()

    renderRoute('/mypage')
    await screen.findByRole('heading', { name: '내 프로필' })

    // 사이드바는 My쇼핑/My활동/My정보 세 분류다(2026-09-16 개편) — 홈은 목록에 없고
    // 로고·breadcrumb 로 돌아온다.
    const menu = screen.getByRole('navigation', { name: '마이페이지 메뉴' })
    expect(within(menu).getByRole('link', { name: '주문목록/배송조회' })).toHaveAttribute(
      'href',
      '/mypage/orders',
    )
    expect(within(menu).getByRole('link', { name: '배송지 관리' })).toHaveAttribute(
      'href',
      '/mypage/addresses',
    )
  })

  // 이 테스트가 이 파일의 존재 이유다 — 소비자 계정 화면은 로고·쇼핑 메뉴·푸터를 스스로
  // 갖고 있어서, 사업자 대시보드 셸 안에 들어가면 화면 안에 화면이 또 있는 모양이 된다.
  it('사업자 대시보드 셸을 씌우지 않는다', async () => {
    mockMyPageApi()
    signInAsConsumer()

    renderRoute('/mypage')
    await screen.findByRole('heading', { name: '내 프로필' })

    expect(screen.queryByRole('navigation', { name: '사업자 대시보드 메뉴' })).not.toBeInTheDocument()
    // 자기 껍데기는 그대로 있다. 셸만 빠진 것이지 머리말까지 사라지면 나갈 길이 없다.
    expect(screen.getByRole('navigation', { name: '주요 메뉴' })).toBeInTheDocument()
  })

  // 소비자는 소재를 보유하지 않는다. 언제나 비어 있는 화면으로 데려가지 않는다.
  it('보유 소재로는 들어가지 못한다', async () => {
    mockMyPageApi()
    signInAsConsumer()

    renderRoute('/inventory')

    expect(await screen.findByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
  })

  it('shows delivery and payment details together and keeps receipt printing', async () => {
    mockMyPageApi()
    signInAsConsumer()
    const print = vi.spyOn(window, 'print').mockImplementation(() => undefined)
    renderRoute('/mypage/orders/1')
    const heading = await screen.findByText('결제 정보')
    const details = within(heading.parentElement as HTMLElement)
    for (const label of ['상품 수', '총 결제금액', '받는사람', '주소']) {
      expect(details.getByText(label)).toBeInTheDocument()
    }
    expect(screen.queryByText('받는 곳')).not.toBeInTheDocument()
    const receipt = screen.getByRole('button', { name: '영수증 출력' })
    expect(receipt.querySelector('svg')).toBeNull()
    await userEvent.setup().click(receipt)
    expect(print).toHaveBeenCalledOnce()
  })

  it('links the common storefront menus to their pages', async () => {
    mockMyPageApi()
    signInAsConsumer()
    renderRoute('/mypage')
    await screen.findByRole('heading', { name: '내 프로필' })
    const menu = screen.getByRole('navigation', { name: '주요 메뉴' })
    for (const [name, href] of [['SHOP', '/shop'], ['BRANDS', '/brands'], ['ABOUT', '/about']]) {
      expect(within(menu).getByRole('link', { name })).toHaveAttribute('href', href)
    }
    expect(screen.queryByRole('link', { name: '쇼핑 계속하기' })).not.toBeInTheDocument()
    expect(screen.getAllByText('MY ACCOUNT')).toHaveLength(1)
    for (const label of ['My 정보', 'My 쇼핑', 'My 활동']) {
      expect(screen.getByText(label)).toBeInTheDocument()
    }
    expect(screen.getAllByRole('banner')).toHaveLength(1)
    expect(screen.getAllByRole('contentinfo')).toHaveLength(1)
  })

  it('keeps the profile and real default address available without order requests', async () => {
    const get = vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
      if (url.startsWith('/v1/product-orders/')) throw new Error('order service unavailable')
      if (url === '/v1/consumers/me/addresses') return { data: [{ addressId: 7, receiverName: '수령인', receiverPhone: '010-1234-5678', zipCode: '12345', address: '서울 주소 45', addressDetail: '101호', isDefault: true }] } as AxiosResponse
      return { data: url.endsWith('/product-ids') ? [] : { items: [] } } as AxiosResponse
    })
    signInAsConsumer()
    renderRoute('/mypage')
    await screen.findByRole('heading', { name: '내 프로필' })
    // 비밀번호 확인 없이 바로 보이는 화면이라 배송지·연락처는 숫자만 가려서 보여준다(2026-09-18).
    expect(screen.getByText('[12345] 서울 주소 **')).toBeInTheDocument()
    expect(screen.getByText('***호')).toBeInTheDocument()
    expect(screen.getByText('010-****-5678')).toBeInTheDocument()
    expect(get.mock.calls.some(([url]) => url.startsWith('/v1/product-orders/'))).toBe(false)
  })

  it('shows common-header logout failure without clearing the session', async () => {
    mockMyPageApi()
    signInAsConsumer()
    vi.spyOn(apiClient, 'post').mockRejectedValue(new Error('로그아웃 실패'))
    const user = userEvent.setup()
    renderRoute('/mypage')
    await screen.findByRole('heading', { name: '내 프로필' })
    await user.click(screen.getByRole('button', { name: '계정 메뉴' }))
    await user.click(screen.getByRole('button', { name: '로그아웃' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('로그아웃 실패')
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('revokes the server session before clearing the consumer session', async () => {
    mockMyPageApi()
    let finishLogout!: (response: AxiosResponse) => void
    const pending = new Promise<AxiosResponse>((resolve) => { finishLogout = resolve })
    const post = vi.spyOn(apiClient, 'post').mockReturnValue(pending)
    useAuthStore.setState({
      user: {
        id: 5,
        name: '새로이 고객',
        email: 'consumer@saeroi.test',
        role: 'CONSUMER',
        walletAddress: null,
      },
      accessToken: 'test-token',
      isAuthenticated: true,
    })
    const user = userEvent.setup()
    renderRoute('/mypage')

    await screen.findByRole('heading', { name: '내 프로필' })

    await user.click(screen.getByRole('button', { name: '계정 메뉴' }))
    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    expect(screen.getByRole('button', { name: '로그아웃 중…' })).toBeDisabled()
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    finishLogout({ data: null } as AxiosResponse)
    await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(false))
    expect(post).toHaveBeenCalledWith('/v1/auth/logout', undefined, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })
})
