// QR Passport 라우트. 이 테스트가 고정하는 것은 하나 — 로그인하지 않아도 들어간다.
// ProtectedRoute 안으로 옮겨지면 미인증 사용자가 /login 으로 튕기고 여기서 깨진다.

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { routes } from '../../app/router/routes.tsx'
import { useAuthStore } from '../../stores/authStore.ts'
import type { ProductPassport } from '../../features/traceability/types/traceability.types.ts'

const api = vi.hoisted(() => ({ getPassport: vi.fn() }))
vi.mock('../../features/traceability/api/getPassport.ts', () => ({
  getPassport: api.getPassport,
  getPassportThumbnailUrl: (qrToken: string) => `/api/v1/qr/${qrToken}/thumbnail`,
}))

function passport(): ProductPassport {
  return {
    itemCode: 'ITEM-0001',
    serialNumber: 1,
    itemStatus: 'SOLD',
    issuedAt: '2026-09-08T10:00:00',
    product: { name: 'RE:BAG', category: 'BAG', description: '폐데님으로 만든 가방입니다.' },
    traceabilityAvailable: false,
  }
}

function renderRoute(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  return render(
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  api.getPassport.mockResolvedValue(passport())
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})
afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

describe('passport route', () => {
  it('로그인하지 않아도 /qr/:qrToken 에 들어간다', async () => {
    renderRoute('/qr/deadbeef00000000000000000000000')

    expect(await screen.findByRole('heading', { name: 'RE:BAG' })).toBeInTheDocument()
    expect(api.getPassport).toHaveBeenCalledWith('deadbeef00000000000000000000000')
    // /login 으로 튕기지 않았다.
    expect(screen.queryByLabelText('이메일')).not.toBeInTheDocument()
  })
})
