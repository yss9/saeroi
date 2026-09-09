import { cleanup, render, screen } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppProviders } from '../../app/providers/AppProviders.tsx'
import { routes } from '../../app/router/routes.tsx'
import { useAuthStore } from '../../stores/authStore.ts'
import { apiClient } from '../../services/apiClient.ts'

function renderRoute() {
  const router = createMemoryRouter(routes, { initialEntries: ['/company/account'] })
  return render(
    <AppProviders>
      <RouterProvider router={router} />
    </AppProviders>,
  )
}

beforeEach(() => {
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [] } as AxiosResponse)
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
})

describe('company account route', () => {
  it('allows an authenticated business member to enter the dashboard account page', () => {
    useAuthStore.setState({
      user: {
        id: 8,
        name: '새로이 브랜드',
        email: 'brand@saeroi.test',
        role: 'BRAND',
        walletAddress: null,
      },
      accessToken: 'test-token',
      isAuthenticated: true,
    })

    renderRoute()

    expect(screen.getByRole('heading', { name: '회사 정보', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })).toBeInTheDocument()
  })

})
