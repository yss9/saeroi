import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import type { UserRole } from '../../../types/role.types.ts'
import { CompanyDashboardLayout } from './CompanyDashboardLayout.tsx'

function setBusinessUser(role: UserRole) {
  useAuthStore.setState({
    user: {
      id: 7,
      name: '새로이 기업',
      email: 'company@saeroi.test',
      role,
      walletAddress: null,
    },
    accessToken: 'test-token',
    isAuthenticated: true,
  })
}

function renderLayout() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/company/account']}>
        <CompanyDashboardLayout><h1>회사 정보 본문</h1></CompanyDashboardLayout>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  localStorage.clear()
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: [] } as AxiosResponse)
  vi.spyOn(apiClient, 'post').mockResolvedValue({ data: null } as AxiosResponse)
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
})

describe('CompanyDashboardLayout', () => {
  it('renders role-specific navigation without duplicating other roles', () => {
    setBusinessUser('SUPPLIER')
    renderLayout()

    expect(screen.getByRole('link', { name: '회사 정보' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '회사 대시보드' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '소재 등록' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '판매 관리' })).toBeInTheDocument()
    // 라우트(/supplier/trades)는 있는데 예전 사이드바에는 빠져 있던 항목.
    // 이제 메뉴가 routes.tsx 정의에서 파생되므로 자동으로 따라온다.
    expect(screen.getByRole('link', { name: '판매 거래' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '가공 요청' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '제품 등록' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '회사 정보 본문' })).toBeInTheDocument()
  })

  it('PROCESSOR 에게는 가공 작업 메뉴가 보이고 공급자 메뉴가 보이지 않는다', () => {
    setBusinessUser('PROCESSOR')
    renderLayout()

    expect(screen.getByRole('link', { name: '가공 작업' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '소재 등록' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '판매 관리' })).not.toBeInTheDocument()
  })

  // 역할이 아직 없을 때 공통 메뉴만 그려 두면 역할이 도착하는 순간 메뉴가 번쩍 늘어난다.
  it('역할 판정 중에는 메뉴 링크를 그리지 않고 aria-busy 로 알린다', () => {
    useAuthStore.setState({ user: null, accessToken: 'test-token', isAuthenticated: true })
    renderLayout()

    const nav = screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })
    expect(nav).toHaveAttribute('aria-busy', 'true')
    expect(nav.querySelectorAll('a')).toHaveLength(0)
  })

  it('signs out and clears the authenticated session', async () => {
    const user = userEvent.setup()
    setBusinessUser('BRAND')
    renderLayout()

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await waitFor(() => expect(useAuthStore.getState().isAuthenticated).toBe(false))
    expect(useAuthStore.getState().user).toBeNull()
    expect(apiClient.post).toHaveBeenCalledWith('/v1/auth/logout', undefined, {
      skipAuth: true,
      skipTokenRefresh: true,
    })
  })

  it('shows the persistent wallet status beside the business account shortcut', async () => {
    const user = userEvent.setup()
    setBusinessUser('BRAND')
    renderLayout()

    expect(screen.getByRole('link', { name: '마이페이지' })).toHaveAttribute('href', '/company/account')
    const walletButton = await screen.findByRole('button', { name: '지갑 연결 안 됨' })

    await user.click(walletButton)

    expect(screen.getByRole('region', { name: '지갑 연결 상태' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '연결하러 가기' })).toHaveAttribute(
      'href',
      '/company/account#company-wallet',
    )
  })

  it('does not expose the wallet address in the registered header popover', async () => {
    const address = '0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045'
    vi.mocked(apiClient.get).mockResolvedValue({
      data: [{
        id: 1,
        walletAddress: address,
        chainId: 1337,
        active: true,
        verified: true,
        verifiedAt: null,
        createdAt: '2026-09-09T00:00:00',
        deactivatedAt: null,
      }],
    } as AxiosResponse)
    const user = userEvent.setup()
    setBusinessUser('SUPPLIER')
    renderLayout()

    await user.click(await screen.findByRole('button', { name: '지갑 연결됨' }))

    expect(screen.getByRole('link', { name: '지갑 관리' })).toBeInTheDocument()
    expect(screen.queryByText(address)).not.toBeInTheDocument()
    expect(screen.queryByText(/0xd8dA/)).not.toBeInTheDocument()
  })

  it('Brand 메뉴에서 가공 신청과 요청 내역을 구분해 이동한다', () => {
    setBusinessUser('BRAND')
    renderLayout()

    expect(screen.getByRole('link', { name: '가공 신청' })).toHaveAttribute('href', '/brand/processing')
    expect(screen.getByRole('link', { name: '요청 내역' })).toHaveAttribute('href', '/brand/processing/requests')
  })

  it('소비자에게는 사업자 알림 센터를 표시하지 않는다', () => {
    setBusinessUser('CONSUMER')
    renderLayout()

    expect(screen.queryByRole('button', { name: /알림/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '새 알림 없음' })).not.toBeInTheDocument()
  })
})
