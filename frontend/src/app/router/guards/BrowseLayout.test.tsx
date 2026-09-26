import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import type { AxiosResponse } from 'axios'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from '../../../services/apiClient.ts'
import { companyKeys } from '../../../features/company/api/companyKeys.ts'
import { useAuthStore } from '../../../stores/authStore.ts'
import { BrowseLayout } from './BrowseLayout.tsx'

/**
 * <b>공개 조회 화면에서 메뉴가 사라지면 안 된다.</b>
 *
 * 소재 목록·마켓플레이스는 비회원도 봐야 해서 {@code ProtectedRoute} 밖에 있는데, 그
 * 대가로 로그인한 사용자가 들어가면 사이드바가 없어졌다. 브랜드가 소재를 사러 들어간
 * 순간 판매 관리·거래 내역으로 갈 길이 브라우저 뒤로 가기밖에 없었다.
 *
 * 반대로 비회원에게 사업자 대시보드를 씌우면 안 된다 — 있지도 않은 메뉴 자리가 생기고,
 * 로그인 안 한 사람에게 회사 정보 같은 항목을 보여주게 된다.
 */
function renderBrowse() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  queryClient.setQueryData(companyKeys.me(), { companyName: '새로이', roleApprovalStatus: 'APPROVED' })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/marketplace']}>
        <Routes>
          <Route element={<BrowseLayout />}>
            <Route path="/marketplace" element={<h1>마켓플레이스</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  vi.spyOn(apiClient, 'get').mockImplementation(async (url) => ({
    data: url === '/v1/companies/me'
      ? { companyName: '새로이', roleApprovalStatus: 'APPROVED' }
      : [],
  } as AxiosResponse))
})

afterEach(() => {
  cleanup()
  useAuthStore.setState({ user: null, accessToken: null, isAuthenticated: false })
  vi.restoreAllMocks()
})

/** 로고가 대시보드 안으로 돌아가는지. `/` 로 가면 셸을 잃는다. */
function renderShell(role: 'SUPPLIER' | 'BRAND' | 'CONSUMER') {
  useAuthStore.setState({
    user: { id: 1, name: '새로이', email: 'a@saeroi.test', role, walletAddress: null },
    accessToken: 'token',
    isAuthenticated: true,
  })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
  queryClient.setQueryData(companyKeys.me(), { companyName: '새로이', roleApprovalStatus: 'APPROVED' })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/marketplace']}>
        <Routes>
          <Route element={<BrowseLayout />}>
            <Route path="/marketplace" element={<h1>마켓플레이스</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('대시보드 셸의 로고', () => {
  // 로고가 하는 일은 어디서든 하나다 — 서비스의 첫 화면으로 간다. 역할이나 화면에 따라
  // 다른 곳으로 보내면 누르고 나서 자기가 어디로 갔는지 되짚어야 한다.
  it('누구에게나 쇼핑몰 랜딩으로 간다', () => {
    renderShell('SUPPLIER')

    expect(screen.getByRole('link', { name: 'SAEROI 홈' })).toHaveAttribute('href', '/')
  })

  it('역할이 달라도 같은 곳으로 간다', () => {
    renderShell('CONSUMER')

    expect(screen.getByRole('link', { name: 'SAEROI 홈' })).toHaveAttribute('href', '/')
  })

  it('쇼핑몰로 나가는 길은 따로 남아 있다', () => {
    // 로고를 대시보드로 돌린 대신, 의도한 이탈은 이 버튼이 맡는다.
    renderShell('SUPPLIER')

    expect(screen.getByRole('link', { name: /쇼핑몰/ })).toHaveAttribute('href', '/')
  })
})

describe('BrowseLayout', () => {
  it('로그인한 사용자에게는 대시보드 메뉴를 씌운다', () => {
    useAuthStore.setState({
      user: { id: 1, name: '새로이', email: 'a@saeroi.test', role: 'BRAND', walletAddress: null },
      accessToken: 'token',
      isAuthenticated: true,
    })

    renderBrowse()

    expect(screen.getByRole('heading', { name: '마켓플레이스' })).toBeInTheDocument()
    // 셸이 씌워졌다는 증거. 이게 없으면 여기서 나갈 방법이 뒤로 가기뿐이다.
    expect(screen.getByRole('navigation', { name: '사업자 대시보드 메뉴' })).toBeInTheDocument()
  })

  it('비회원에게는 화면만 보여준다', () => {
    renderBrowse()

    expect(screen.getByRole('heading', { name: '마켓플레이스' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: '사업자 대시보드 메뉴' })).not.toBeInTheDocument()
  })
})
