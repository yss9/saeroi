import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
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
  return render(
    <MemoryRouter initialEntries={['/company/account']}>
      <CompanyDashboardLayout><h1>회사 정보 본문</h1></CompanyDashboardLayout>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  cleanup()
  localStorage.clear()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
})

describe('CompanyDashboardLayout', () => {
  it('renders role-specific navigation without duplicating other roles', () => {
    setBusinessUser('SUPPLIER')
    renderLayout()

    expect(screen.getByRole('link', { name: '회사 정보' })).toBeInTheDocument()
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

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().user).toBeNull()
  })
})
