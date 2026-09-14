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

function mockMyPageApi() {
  return vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
    const data = url === '/v1/product-orders/me/summary'
      ? { created: 0, paid: 0, shipped: 0, delivered: 1 }
      : orderPage
    return { data } as AxiosResponse
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
