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

    expect(await screen.findByRole('heading', { name: '새로이 고객님, 반갑습니다.' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '데님 서클 백' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '제품 여정 보기' })).toHaveAttribute(
      'href',
      'http://localhost:5173/qr/journey-token',
    )
    expect(get).toHaveBeenCalledWith('/v1/product-orders/me', {
      params: { page: 0, size: 20 },
    })
    expect(get).toHaveBeenCalledWith('/v1/product-orders/me/summary')
  })

  /** 소비자로 로그인시킨다. 셸 검사에서 여러 번 쓴다. */
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

  // 이 테스트가 이 파일의 존재 이유다 — 소비자 계정 화면은 로고·쇼핑 메뉴·푸터를 스스로
  // 갖고 있어서, 사업자 대시보드 셸 안에 들어가면 화면 안에 화면이 또 있는 모양이 된다.
  it('사업자 대시보드 셸을 씌우지 않는다', async () => {
    mockMyPageApi()
    signInAsConsumer()

    renderRoute('/mypage')
    await screen.findByRole('heading', { name: '새로이 고객님, 반갑습니다.' })

    expect(screen.queryByRole('navigation', { name: '사업자 대시보드 메뉴' })).not.toBeInTheDocument()
    // 자기 껍데기는 그대로 있다. 셸만 빠진 것이지 머리말까지 사라지면 나갈 길이 없다.
    expect(screen.getByRole('navigation', { name: '쇼핑 메뉴' })).toBeInTheDocument()
  })

  // 소비자는 소재를 보유하지 않는다. 언제나 비어 있는 화면으로 데려가지 않는다.
  it('보유 소재로는 들어가지 못한다', async () => {
    mockMyPageApi()
    signInAsConsumer()

    renderRoute('/inventory')

    expect(await screen.findByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
  })

  it('revokes the server session before clearing the consumer session', async () => {
    mockMyPageApi()
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null } as AxiosResponse)
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

    const heroHeading = await screen.findByRole('heading', { name: '새로이 고객님, 반갑습니다.' })
    const hero = heroHeading.closest('section')
    expect(hero).not.toBeNull()

    await user.click(within(hero as HTMLElement).getByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(false))
    expect(post).toHaveBeenCalledWith('/v1/auth/logout', undefined, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })
})
