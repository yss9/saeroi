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
    expect(screen.queryByRole('link', { name: '가공 요청' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '회사 정보 본문' })).toBeInTheDocument()
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
